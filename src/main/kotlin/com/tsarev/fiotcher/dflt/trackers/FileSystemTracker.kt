package com.tsarev.fiotcher.dflt.trackers

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.isForced
import com.tsarev.fiotcher.dflt.isWindows
import com.tsarev.fiotcher.dflt.push
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.internal.pool.Tracker
import java.io.File
import java.nio.file.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Tracker, that monitors file changes on local file system.
 *
 * This tracker ignores directories - it does not send events for them.
 *
 * See parameters as class fields.
 */
class FileSystemTracker(
    /**
     * Graceful stop checking period.
     */
    private val checkForStopTimeoutMs: Long = 100,

    /**
     * Timeout to debounce time close changed files.
     *
     * Turns off debounce if less or equal zero.
     */
    private val debounceTimeoutMs: Long = if (isWindows) 200 else 20,

    /**
     * If should track directories recursively.
     */
    private val recursive: Boolean = true,

    /**
     * If should track changes.
     */
    private val trackChanges: Boolean = true,

    /**
     * If should track creations.
     */
    private val trackCreations: Boolean = isWindows,

    /**
     * If should track deletions.
     */
    private val trackDeletions: Boolean = false,

    ) : Tracker<File>() {

    /**
     * If debounce is enabled.
     */
    private val debounceEnabled = debounceTimeoutMs > 0

    /**
     * If this tracker is running.
     */
    override val stopBrake = Brake<Unit>()

    /**
     * Used to watch file system.
     */
    private val watchedPathFileSystem = FileSystems.getDefault()

    /**
     * Watch service to handle watch events.
     */
    private val watchService = watchedPathFileSystem.newWatchService()

    /**
     * Registered watch keys.
     */
    private var registeredWatches: MutableMap<Path, WatchKey> = HashMap()

    /**
     * Send event closure.
     */
    private lateinit var sendEvent: (EventWithException<InitialEventsBunch<File>>) -> Unit

    /**
     * Initialize existing directories.
     */
    override fun doInit(
        executor: Executor,
        sendEvent: (EventWithException<InitialEventsBunch<File>>) -> Unit
    ) {
        if (!resourceBundle.isDirectory) throw IllegalArgumentException("$resourceBundle is not a directory!")
        registerRecursively(resourceBundle)
        this.sendEvent = sendEvent
    }

    /**
     * Main tracker loop.
     */
    override fun run() {
        var key: WatchKey? = null
        try {
            while (!isStopping) {
                try {
                    // Try fetch any change.
                    key = watchService.poll(checkForStopTimeoutMs, TimeUnit.MILLISECONDS)
                    if (key != null) {
                        // If we succeed, than process this one and try to debounce close changes.
                        // Try to preserve order of events. It can be (or can be not) important.
                        val allEntries = LinkedHashSet<InnerEvent>()
                        allEntries.addNewEntries(processDirectoryEvent(key))
                        val currentTimeMillis = System.currentTimeMillis()
                        val endTime = currentTimeMillis + debounceTimeoutMs
                        while (debounceEnabled && !stopBrake.isForced) {
                            try {
                                // Reset previous key recklessly - finally at the bottom will do the job at failure.
                                key?.reset()
                                key = watchService.poll(debounceTimeoutMs, TimeUnit.MILLISECONDS)
                                if (stopBrake.isForced || endTime < System.currentTimeMillis()) break
                                if (key == null) break
                                allEntries.addNewEntries(processDirectoryEvent(key))
                            } catch (interrupted: InterruptedException) {
                                stop(false) // Set to stop, but continue debounce.
                            }
                        }
                        // Send event, if not empty.
                        if (allEntries.isNotEmpty()) {
                            // Watch only changed and created entities.
                            val events = allEntries
                                .filter {
                                    trackCreations && it.type == EventType.CREATED ||
                                            trackChanges && it.type == EventType.CHANGED ||
                                            trackDeletions && it.type == EventType.DELETED
                                }
                                .map { it.resource }
                                .toSet()

                            if (events.isNotEmpty()) {
                                sendEvent(InitialEventsBunch(events).asSuccess())
                            }
                        }
                    }
                } catch (interrupted: InterruptedException) {
                    doStopBrake()
                    Thread.currentThread().interrupt()
                } catch (cause: Throwable) {
                    doStopBrake()
                    throw RuntimeException("Something failed while watching $resourceBundle", cause)
                } finally {
                    key?.reset()
                }
            }
        } finally {
            doStopBrake()
        }
    }

    /**
     * Remove events that cancel each other.
     */
    private fun MutableCollection<InnerEvent>.addNewEntries(new: Collection<InnerEvent>) {
        new.forEach {
            val deletedCopy = it.copy(type = EventType.DELETED)
            val createdCopy = it.copy(type = EventType.CREATED)
            val changedCopy = it.copy(type = EventType.CHANGED)
            when (it.type) {
                EventType.CHANGED -> if (!this.contains(deletedCopy)) this += it
                EventType.CREATED -> {
                    this -= deletedCopy
                    this += createdCopy
                }
                EventType.DELETED -> {
                    this -= createdCopy
                    this -= changedCopy
                    this += deletedCopy
                }
            }
        }
    }

    /**
     * Private inner event to simplify groupong.
     */
    private data class InnerEvent(
        val type: EventType,
        val resource: File
    )

    /**
     * Perform directories recursive scan, registering them.
     */
    private fun registerRecursively(from: File) {
        while (!isStopping) {
            // Break links loops.
            if (registeredWatches.containsKey(from.toPath()))
                return
            registerDirectory(from.toPath())
            from.listFiles()?.filterNotNull()?.forEach {
                if (it.isDirectory) registerRecursively(it)
            }
        }
    }

    /**
     * Register directory watcher.
     */
    private fun registerDirectory(directory: Path) {
        registeredWatches.computeIfAbsent(directory) {
            directory.toAbsolutePath().register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
        }
    }

    /**
     * Process single change directory content event.
     */
    private fun processDirectoryEvent(key: WatchKey): Collection<InnerEvent> {
        val events = key.pollEvents()
        val currentEventBunch = LinkedHashSet<Pair<Path, EventType>>()
        val basePath = key.watchable() as Path // Always safe, since no other watchables are used here.
        events.forEach { event ->
            when (event.kind()) {
                StandardWatchEventKinds.ENTRY_CREATE -> {
                    // If entry was created in watched directory, then update it in discovered entries.
                    // Also, collect it for event if it is not a directory.
                    // Also, if it is a directory - register it for watching.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_CREATE)
                    val path = basePath.resolve(rawPath)
                    val asFile = path.toFile()
                    if (this.recursive && asFile.isDirectory) {
                        registerRecursively(asFile)
                    } else {
                        currentEventBunch.add(path to EventType.CREATED)
                    }
                }
                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    // If entry was modified in watched directory, then update it in discovered entries.
                    // Also, collect it for event if it is not a directory.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_MODIFY)
                    val path = basePath.resolve(rawPath)
                    if (!path.toFile().isDirectory) {
                        // Watch only non directory entries.
                        currentEventBunch.add(path to EventType.CHANGED)
                    }
                }
                StandardWatchEventKinds.ENTRY_DELETE -> {
                    // If entry was modified in watched directory, then delete it from discovered entries.
                    // Also, collect it for event if it is not a directory.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_DELETE)
                    val path = basePath.resolve(rawPath)
                    val wasDirectory = registeredWatches[path]?.cancel()?.let { true } ?: false
                    if (!wasDirectory) {
                        // Watch only non directory entries.
                        currentEventBunch.add(path to EventType.DELETED)
                    }
                }
            }
        }
        return currentEventBunch.map { InnerEvent(it.second, it.first.toAbsolutePath().toFile()) }
    }

    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push(force) {
        if (exception != null) completeExceptionally(exception)
    }

    /**
     * Create brake synchronously.
     */
    private fun doStopBrake() = stopBrake.push().apply {
        watchService.close()
        registeredWatches.clear()
        complete(Unit)
    }

    /**
     * Check, whether this [Tracker] is stopping.
     */
    override val isStopping get() = stopBrake.get() != null || Thread.currentThread().isInterrupted

    /**
     * Extension to hide casting.
     */
    private fun <T> WatchEvent<*>.typedContext(
        kind: WatchEvent.Kind<T>
    ) = kind.type().cast(this.context())

    /**
     * What happened to the file.
     */
    enum class EventType {
        /**
         * Resource is created.
         */
        CREATED,

        /**
         * Resource content is changed in any way.
         */
        CHANGED,

        /**
         * Resource is deleted.
         */
        DELETED
    }

}