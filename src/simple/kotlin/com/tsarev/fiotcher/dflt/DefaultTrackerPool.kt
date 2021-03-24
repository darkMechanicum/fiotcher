package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import java.util.concurrent.*

/**
 * Default tracker pool implementation, which also serves as
 * [WatchT] notifier.
 */
class DefaultTrackerPool<WatchT : Any>(
    /**
     * Executor service, used by this pool.
     */
    private val executorService: ExecutorService,

    /**
     * [SubmissionPublisher] to link to listeners.
     */
    private val publisher: SubmissionPublisher<Pair<String, EventWithException<InitialEventsBunch<WatchT>>>>
) : TrackerPool<WatchT>, Stoppable {

    /**
     * Registerer trackers, by resource and key.
     */
    private val registeredTrackers = ConcurrentHashMap<Pair<WatchT, String>, Tracker<WatchT>>()

    /**
     * Stopping brake.
     */
    private val stopBrake = Brake()

    override fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): Boolean {
        val trackerKey = resourceBundle to key

        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            // Stop with exception if pool is stopped.
            if (stopBrake.isPushed) return false
            // Try to register tracker.
            if (registeredTrackers.putIfAbsent(trackerKey, tracker) != null) return false
        }

        // We shall call submit here, since tracker [init] method is potentially time consuming.
        // It can, for example, include recursive directory scanning, or remote repository initial fetching.
        executorService.submit<Unit> {
            // Stop silently if pool is stopped. Tracker will be stopped by [stop] method, since it is in the map already.
            if (this@DefaultTrackerPool.stopBrake.isPushed) throw FiotcherException("Pool is stopped")

            val currentThread = Thread.currentThread()
            try {
                // Try to init tracker and subscribe to its publisher.
                // Can throw exception due to pool stopping, but we will handle it later.
                tracker.init(resourceBundle, executorService) { publisher.submit(key to it) }

                // Start tracker. From now he is treated like started.
                if (!currentThread.isInterrupted) {
                    executorService.submit(tracker)
                } else {
                    doStopTracker(resourceBundle to key, true, tracker)
                    throw InterruptedException("Interrupted before tracker was started")
                }
            } catch (interrupt: InterruptedException) {
                try {
                    // Force shutdown if initialization was interrupted.
                    doStopTracker(resourceBundle to key, true, tracker)
                } finally {
                    throw interrupt
                }
                // Exception for compiler satisfaction.
                @Suppress("UNREACHABLE_CODE", "ThrowableNotThrown")
                throw FiotcherException("Must not got here!")
            } catch (cause: Throwable) {
                try {
                    // Force shutdown if initialization threw an exception.
                    doStopTracker(resourceBundle to key, true, tracker)
                } finally {
                    throw cause
                }
                // Exception for compiler satisfaction.
                @Suppress("UNREACHABLE_CODE", "ThrowableNotThrown")
                throw FiotcherException("Must not got here!")
            }
        }

        return true
    }

    override fun stopTracker(resourceBundle: WatchT, key: String, force: Boolean) {
        val keyPair = resourceBundle to key
        doStopTracker(keyPair, force, registeredTrackers[keyPair])
    }

    override fun stop(force: Boolean) = stopBrake.push(force) {
        val trackersCopy = HashMap(registeredTrackers)
        trackersCopy.forEach { (key, _) -> stopTracker(key.first, key.second, force) }
        registeredTrackers.clear()
    }

    /**
     * Do stop tracker, either from initializing failure, or from manual stopping.
     *
     * This place must be the only one, that removes registered tracker from the map.
     */
    private fun doStopTracker(
        key: Pair<WatchT, String>,
        force: Boolean,
        tracker: Tracker<WatchT>?
    ) {
        registeredTrackers.computeIfPresent(key) { _, present ->
            if (present == tracker) {
                tracker.stop(force)
                null
            } else present
        }
    }
}