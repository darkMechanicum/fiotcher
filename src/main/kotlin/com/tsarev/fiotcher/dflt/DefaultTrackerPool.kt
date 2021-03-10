package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.pool.PublisherPool
import com.tsarev.fiotcher.internal.pool.Tracker
import com.tsarev.fiotcher.internal.pool.TrackerPool
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
     * [PublisherPool] to get publisher for key.
     */
    private val publisherPool: PublisherPool<EventWithException<InitialEventsBunch<WatchT>>>
) : TrackerPool<WatchT>, StoppableBrakeMixin<Unit> {

    /**
     * Mock no-op tracker.
     */
    private val mockTracker = object : Tracker<WatchT>(), StoppableBrakeMixin<Unit> {
        override val stopBrake = Brake<Unit>().apply { pushCompleted(Unit) {} }
        override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
            stopBrake.pushCompleted(Unit) {}

        override fun run() = run { }
        override fun doInit(executor: Executor, sendEvent: (EventWithException<InitialEventsBunch<WatchT>>) -> Unit) =
            run {}
    }

    /**
     * Registerer trackers, by resource and key.
     */
    private val registeredTrackers = ConcurrentHashMap<Pair<WatchT, String>, Tracker<WatchT>>()

    /**
     * Stopping brake.
     */
    override val stopBrake = Brake<Unit>()

    override fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): Future<Tracker<WatchT>> {
        val trackerKey = resourceBundle to key

        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            // Stop with exception if pool is stopped.
            if (stopBrake.isPushed) return CompletableFuture.failedFuture(PoolIsStopped())
            // Try to register tracker.
            if (registeredTrackers.putIfAbsent(trackerKey, tracker) != null)
                return CompletableFuture.failedFuture(TrackerAlreadyRegistered(resourceBundle, key))
        }

        // We shall call submit here, since tracker [init] method is potentially time consuming.
        // It can, for example, include recursive directory scanning, or remote repository initial fetching.
        return executorService.submit<Tracker<WatchT>> {
            // Stop silently if pool is stopped. Tracker will be stopped by [stop] method, since it is in the map already.
            if (this@DefaultTrackerPool.isStopped) throw PoolIsStopped()

            val wrappedTracker: Tracker<WatchT>?
            val currentThread = Thread.currentThread()
            try {
                // Try to init tracker and subscribe to its publisher.
                // Can throw exception due to pool stopping, but we will handle it later.
                val publisher = publisherPool.getPublisher(key)
                tracker.init(resourceBundle, executorService) { publisher.submit(it) }

                // Start tracker. From now he is treated like started.
                if (!currentThread.isInterrupted) {
                    executorService.submit(tracker)
                    wrappedTracker = createTrackerWrapper(resourceBundle, key, tracker)
                    // If submission handle had interrupted us, so stop tracker.
                    if (currentThread.isInterrupted) {
                        doStopTracker(resourceBundle, key, true, tracker)
                        return@submit mockTracker
                    } else {
                        return@submit wrappedTracker
                    }
                } else {
                    doStopTracker(resourceBundle, key, true, tracker)
                    return@submit mockTracker
                }
            } catch (interrupt: InterruptedException) {
                // Force shutdown if initialization was interrupted.
                doStopTracker(resourceBundle, key, true, tracker)
                throw interrupt
            } catch (cause: Throwable) {
                try {
                    // Force shutdown if initialization threw an exception.
                    doStopTracker(resourceBundle, key, true, tracker)
                } finally {
                    throw cause
                }
                // Exception for compiler.
                @Suppress("UNREACHABLE_CODE", "ThrowableNotThrown")
                throw FiotcherException("Must not got here")
            }
        }
    }

    override fun stopTracker(resourceBundle: WatchT, key: String, force: Boolean): CompletableFuture<*> {
        // Return stopping brake if requested to deregister something during stopping.
        val trackerKey = resourceBundle to key
        val foundTracker = registeredTrackers[trackerKey]
        return if (foundTracker != null) {
            doStopTracker(
                resourceBundle,
                key,
                force,
                foundTracker
            )
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push {
        val trackersCopy = HashMap(registeredTrackers)
        registeredTrackers.clear()
        val allTrackersStopFuture = trackersCopy
            .map { (key, _) -> stopTracker(key.first, key.second, force) }
            .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> }.thenApply { } }

        // Combine all futures in one and clear resources after their completion.
        // Order matters here.
        allTrackersStopFuture.thenAccept {
            if (exception != null) completeExceptionally(exception) else complete(Unit)
        }
    }

    /**
     * Create a handle, that stops the tracker and de registers it.
     */
    private fun createTrackerWrapper(
        resourceBundle: WatchT,
        key: String,
        tracker: Tracker<WatchT>
    ): Tracker<WatchT> {
        return object : Tracker<WatchT>(), DoStop<Unit> by tracker {
            // Also deregister tracker when stop is requested.
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<Unit> =
                doStopTracker(resourceBundle, key, false, tracker)

            // Only tracker pool is allowed to init tracker.
            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<WatchT>>) -> Unit
            ) = run {}

            // Only tracker pool is allowed to start tracker execution.
            override fun run() = run {}
        }
    }

    /**
     * Do stop tracker, either from initializing failure, or from manual stopping.
     *
     * This place must be the one, that removes registered tracker, except from the [stop] method
     * of the pool.
     */
    private fun doStopTracker(
        resourceBundle: WatchT,
        key: String,
        force: Boolean,
        tracker: Tracker<WatchT>
    ): CompletableFuture<Unit> {
        val resultHandle = CompletableFuture<Unit>()
        tracker.stop(force).whenComplete { _, exception ->
            registeredTrackers.computeIfPresent(resourceBundle to key) { _, old ->
                // In case of close [stopTracker] and [startTracker] calls.
                if (old == tracker) null else old
            }
            if (exception != null) resultHandle.completeExceptionally(exception)
            else resultHandle.complete(Unit)
        }
        return resultHandle
    }
}