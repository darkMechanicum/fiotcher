package com.tsarev.fiotcher.dflt.trackers

import com.tsarev.fiotcher.api.EventType
import com.tsarev.fiotcher.api.TypedEvents
import com.tsarev.fiotcher.api.tracker.Tracker
import com.tsarev.fiotcher.api.withType
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.push
import java.io.File
import java.nio.file.*
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    private val debounceTimeoutMs: Long = 20,

    /**
     * If should track directories recursively.
     */
    private val recursive: Boolean = true,

    ) : Tracker<File>() {

    /**
     * If debounce is enabled.
     */
    private val debounceEnabled = debounceTimeoutMs > 0

    /**
     * If this tracker is running.
     */
    private val brake = Brake<Unit>()

    /**
     * If this tracker is being stopped forcibly.
     */
    @Volatile
    private var forced = AtomicBoolean(false)

    /**
     * Used to watch file system.
     */
    private val watchedPathFileSystem = FileSystems.getDefault()

    /**
     * Watch service to handle watch events.
     */
    private val watchService = watchedPathFileSystem.newWatchService()

    /**
     * Already discovered files with their timestamp.
     */
    private var discovered: MutableMap<Path, Pair<Instant, Boolean>> = HashMap()

    /**
     * Registered watch keys.
     */
    private var registeredWatches: MutableMap<Path, WatchKey> = HashMap()

    /**
     * Create brand new publisher.
     */
    private lateinit var publisher: SubmissionPublisher<TypedEvents<File>>

    override val isStopped get() = brake.get() != null

    /**
     * Initialize existing directories.
     */
    override fun doInit(
        executor: Executor
    ): Flow.Publisher<TypedEvents<File>> {
        if (!resourceBundle.isDirectory) throw IllegalArgumentException("$resourceBundle is not a directory!")
        publisher = SubmissionPublisher(executor, Flow.defaultBufferSize())
        registerRecursively(resourceBundle)
        return publisher
    }

    /**
     * Main tracker loop.
     */
    override fun run() {
        var key: WatchKey? = null
        try {
            while (!isStopping()) {
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
                        while (debounceEnabled && !forced.get()) {
                            try {
                                // Reset previous key recklessly - finally at the bottom will do the job at failure.
                                key?.reset()
                                key = watchService.poll(debounceTimeoutMs, TimeUnit.MILLISECONDS)
                                if (forced.get() || endTime < System.currentTimeMillis()) break
                                if (key == null) break
                                allEntries.addNewEntries(processDirectoryEvent(key))
                            } catch (interrupted: InterruptedException) {
                                stop(false) // Set to stop, but continue debounce.
                            }
                        }
                        // Send event, if not empty.
                        if (allEntries.isNotEmpty()) {
                            val events = allEntries.map { it.resource withType it.type }
                            publisher.submit(events)
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
            when (it.type) {
                EventType.CHANGED -> if (!this.contains(deletedCopy)) this += it
                EventType.CREATED -> if (this.contains(deletedCopy)) {
                    this -= deletedCopy
                    this += createdCopy
                } else {
                    this += createdCopy
                }
                EventType.DELETED -> if (this.contains(createdCopy)) {
                    this -= createdCopy
                    this += deletedCopy
                } else {
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
        while (!isStopping()) {
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
                    if (updatePath(path)) {
                        val asFile = path.toFile()
                        if (this.recursive && asFile.isDirectory) {
                            registerRecursively(asFile)
                        } else {
                            currentEventBunch.add(path to EventType.CREATED)
                        }
                    }
                }
                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    // If entry was modified in watched directory, then update it in discovered entries.
                    // Also, collect it for event if it is not a directory.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_MODIFY)
                    val path = basePath.resolve(rawPath)
                    if (updatePath(path)) also {
                        if (!path.toFile().isDirectory) {
                            // Watch only non directory entries.
                            currentEventBunch.add(path to EventType.CHANGED)
                        }
                    }
                }
                StandardWatchEventKinds.ENTRY_DELETE -> {
                    // If entry was modified in watched directory, then delete it from discovered entries.
                    // Also, collect it for event if it is not a directory.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_DELETE)
                    val path = basePath.resolve(rawPath)
                    val removed = discovered.remove(path)
                    if (removed != null && !removed.second && !path.toFile().isDirectory) {
                        // Watch only non directory entries.
                        currentEventBunch.add(path to EventType.DELETED)
                    } else if (removed != null) {
                        registeredWatches[path]?.cancel()
                    }
                }
            }
        }
        return currentEventBunch.map { InnerEvent(it.second, it.first.toAbsolutePath().toFile()) }
    }

    /**
     * Update timestamp for path.
     *
     * @return `true` if update was meaningful
     * (previous entry was not present or had older timestamp)
     */
    private fun updatePath(path: Path): Boolean {
        val oldTimeStamp = discovered[path]?.first
        val now = Instant.now()
        return if (oldTimeStamp == null || now.isAfter(oldTimeStamp)) {
            discovered[path] = Pair(now, path.toFile().isDirectory)
            true
        } else {
            false
        }
    }

    /**
     * Do stop.
     */
    override fun stop(force: Boolean) = this
        .forced.set(force)
        .let { brake.push () }

    /**
     * Create brake synchronously.
     */
    private fun doStopBrake() = brake.push().apply {
        watchService.close()
        registeredWatches.clear()
        discovered.clear()
        complete(Unit)
    }

    /**
     * Check, whether this [Tracker] is stopping.
     */
    private fun isStopping() = brake.get() != null || Thread.currentThread().isInterrupted

    /**
     * Extension to hide casting.
     */
    private fun <T> WatchEvent<*>.typedContext(
        kind: WatchEvent.Kind<T>
    ) = kind.type().cast(this.context())

}