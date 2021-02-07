package com.tsarev.fiotcher.dflt.trackers

import com.tsarev.fiotcher.api.EventType
import com.tsarev.fiotcher.api.tracker.Tracker
import com.tsarev.fiotcher.api.tracker.TrackerEvent
import com.tsarev.fiotcher.api.tracker.TrackerEventBunch
import java.io.File
import java.nio.file.*
import java.time.Instant
import java.util.concurrent.*

/**
 * Tracker, that monitors file changes on local file system.
 *
 * This tracker ignored directories.
 */
class FileSystemTracker(
    /**
     * Timeout to wait until event.
     */
    private val checkForStopTimeoutMs: Long = 100,

    /**
     * Timeout to wait until event.
     */
    private val debounceTimeoutMs: Long = 10,

    /**
     * Timeout to wait until event.
     */
    private val debounceMax: Long = 10,

    /**
     * If should go down directories.
     */
    private val recursive: Boolean = true,

    ) : Tracker<File>() {

    /**
     * If this tracker is running.
     */
    @Volatile
    private var brake: CompletableFuture<Unit>? = null

    /**
     * If this tracker is running.
     */
    @Volatile
    private var forced = false

    /**
     * File system that residents at watched path.
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
    private lateinit var publisher: SubmissionPublisher<TrackerEventBunch<File>>

    /**
     * Initialize existing directories.
     */
    override fun doInit(
        executor: Executor
    ): Flow.Publisher<TrackerEventBunch<File>> {
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
                        var currentDebounce = 0
                        while (!forced && currentDebounce < debounceMax) {
                            try {
                                // Reset previous key recklessly - finally at the bottom will do the job at failure.
                                key?.reset()
                                key = watchService.poll(debounceTimeoutMs, TimeUnit.MILLISECONDS)
                                if (key == null) break
                                allEntries.addNewEntries(processDirectoryEvent(key))
                                currentDebounce++
                            } catch (interrupted: InterruptedException) {
                                doStopBrake() // Set to stop, but continue debounce.
                            }
                        }
                        // Send event, if not empty.
                        if (allEntries.isNotEmpty()) {
                            val groupedByType = allEntries.groupBy({ it.type }, { it.resource })
                            val trackerEvents = groupedByType.keys
                                .map { TrackerEvent(groupedByType[it]!!, it) }
                            publisher.submit(TrackerEventBunch(trackerEvents))
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
                }
                EventType.DELETED -> if (this.contains(createdCopy)) {
                    this -= createdCopy
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
                    updatePath(path)?.also {
                        if (this.recursive && File(path.toUri()).isDirectory) {
                            registerDirectory(path)
                        } else {
                            currentEventBunch.add(it to EventType.CREATED)
                        }
                    }
                }
                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    // If entry was modified in watched directory, then update it in discovered entries.
                    // Also, collect it for event if it is not a directory.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_MODIFY)
                    val path = basePath.resolve(rawPath)
                    updatePath(path)?.also {
                        if (!File(path.toUri()).isDirectory) {
                            // Watch only non directory entries.
                            currentEventBunch.add(it to EventType.CHANGED)
                        }
                    }
                }
                StandardWatchEventKinds.ENTRY_DELETE -> {
                    // If entry was modified in watched directory, then delete it from discovered entries.
                    // Also, collect it for event if it is not a directory.
                    val rawPath = event.typedContext(StandardWatchEventKinds.ENTRY_DELETE)
                    val path = basePath.resolve(rawPath)
                    val removed = discovered.remove(path)
                    if (removed != null && !removed.second) {
                        // Watch only non directory entries.
                        currentEventBunch.add(path to EventType.DELETED)
                    } else if (removed != null) {
                        registeredWatches[path]?.cancel()
                    }
                }
            }
        }
        return currentEventBunch.map { InnerEvent(it.second, File(it.first.toAbsolutePath().toUri())) }
    }

    /**
     * Update timestamp for path.
     *
     * @return `(path, now)` if update was meaningful
     * (previous entry was not present or has older timestamp)
     */
    private fun updatePath(path: Path): Path? {
        val oldTimeStamp = discovered[path]?.first
        val now = Instant.now()
        return if (oldTimeStamp == null || now.isAfter(oldTimeStamp)) {
            discovered[path] = Pair(now, File(path.toUri()).isDirectory)
            path
        } else {
            null
        }
    }

    /**
     * Do stop.
     */
    override fun stop(force: Boolean): Future<Unit> {
        this.forced = force
        return brake ?: doCreateBrake()
    }

    /**
     * Create brake with sync.
     */
    private fun doCreateBrake() = synchronized(this) {
        brake ?: CompletableFuture<Unit>()
            .also { this.brake = it }
            .apply {
                thenAccept {
                    this@FileSystemTracker.watchService.close()
                }
            }
    }

    /**
     * Create brake synchronously.
     */
    private fun doStopBrake() = synchronized(this) {
        brake?.also { it.complete(Unit) } ?: CompletableFuture.completedFuture(Unit).also { brake = it }
    }

    /**
     * Check, whether this [Tracker] is stopping.
     */
    private fun isStopping() = this.brake != null || Thread.currentThread().isInterrupted

    /**
     * Extension to hide casting.
     */
    private fun <T> WatchEvent<*>.typedContext(
        kind: WatchEvent.Kind<T>
    ) = kind.type().cast(this.context())

}