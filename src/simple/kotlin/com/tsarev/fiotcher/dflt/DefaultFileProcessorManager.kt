package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FileProcessorManager
import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.Stoppable
import java.io.File
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashSet

/**
 * Default implementation of [FileProcessorManager].
 */
class DefaultFileProcessorManager(
    /**
     * Executor service, used for processing.
     */
    private val executorService: ExecutorService = Executors.newCachedThreadPool(),
) : FileProcessorManager {

    /**
     * Stop brake.
     */
    private val stopBrake = Brake()

    /**
     * Submission publisher to pass events from trackers to listeners.
     */
    private val publisher =
        SubmissionPublisher<Pair<String, EventWithException<InitialEventsBunch<File>>>>(executorService, 512)

    /**
     * Tracker pool to manage trackers.
     */
    private val trackerPool = DefaultTrackerPool(executorService, publisher)

    /**
     * Registered listeners, by key.
     */
    private val registeredListeners = ConcurrentHashMap<String, MutableSet<Stoppable>>()

    override fun startTrackingFile(path: File, key: String, recursively: Boolean): Boolean {
        if (stopBrake.isPushed) return false
        val fileSystemTracker = FileSystemTracker(recursive = recursively)
        return trackerPool.startTracker(path, fileSystemTracker, key)
    }

    override fun stopTracking(resource: File, key: String, force: Boolean) {
        trackerPool.stopTracker(resource, key, force)
    }

    override fun startListening(
        key: String,
        handleErrors: ((Throwable) -> Throwable?)?,
        listener: (InitialEventsBunch<File>) -> Unit
    ): Stoppable? {
        val result = CommonListener(key, handleErrors, listener)
        val wrapper = createListenerWrapper(key, result)
        listenersByKey(key) += wrapper
        return if (stopBrake.isPushed) {
            wrapper.stop(true)
            null
        } else {
            publisher.subscribe(result)
            wrapper
        }
    }

    override fun stopListening(key: String, force: Boolean) {
        val copy = listenersByKey(key).toSet()
        copy.forEach { it.stop(force) }
    }

    override fun stop(force: Boolean) = stopBrake.push(force) {
        trackerPool.stop(force)
        val copy = registeredListeners.values.toSet().flatten()
        registeredListeners.clear()
        copy.forEach { it.stop(force) }
        publisher.close()
        if (force) executorService.shutdownNow() else executorService.shutdown()
    }

    /**
     * Create a handle, that de registers listener.
     */
    private fun createListenerWrapper(
        key: String,
        listener: Stoppable
    ): Stoppable {
        return object : Stoppable {
            override fun stop(force: Boolean) = listener.stop(force).also { registeredListeners.remove(key) }
        }
    }

    /**
     * Utility to create listeners set.
     */
    private fun listenersByKey(key: String) =
        registeredListeners.computeIfAbsent(key) { Collections.synchronizedSet(HashSet()) }

}