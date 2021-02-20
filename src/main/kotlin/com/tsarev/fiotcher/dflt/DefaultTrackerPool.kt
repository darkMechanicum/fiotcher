package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.pool.AggregatorPool
import com.tsarev.fiotcher.internal.pool.Tracker
import com.tsarev.fiotcher.internal.pool.TrackerPool
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Default tracker pool implementation, which also serves as
 * [WatchT] notifier.
 */
class DefaultTrackerPool<WatchT : Any>(
    /**
     * Executor, used to run trackers.
     */
    private val trackerExecutor: ExecutorService,

    /**
     * Executor, used to perform queue submission and processing by aggregators and trackers.
     */
    private val trackerExecutorService: Executor,

    /**
     * Executor, used to perform trackers registration process.
     */
    private val registrationExecutorService: ExecutorService,

    /**
     * [AggregatorPool] for aggregator synchronous access.
     */
    private val aggregatorPool: AggregatorPool
) : TrackerPool<WatchT> {

    /**
     * Mock no-op tracker.
     */
    private val mockTracker = object : Tracker<WatchT>() {
        override val isStopped = true
        override fun doStop(force: Boolean) = CompletableFuture.completedFuture(Unit)
        override fun run() = run { }
        override fun doInit(executor: Executor) =
            object : Flow.Publisher<EventWithException<InitialEventsBunch<WatchT>>> {
                override fun subscribe(subscriber: Flow.Subscriber<in EventWithException<InitialEventsBunch<WatchT>>>?) =
                    run { }
            }
    }

    /**
     * Utility class to hold tracker related info.
     */
    internal data class TrackerInfo<WatchT : Any>(
        val tracker: Tracker<WatchT>,
        val submissionFuture: CompletableFuture<Tracker<WatchT>>,
        val trackerTaskHandle: Future<*>?
    )

    /**
     * Registerer trackers, by resource and key.
     */
    private val registeredTrackers = ConcurrentHashMap<Pair<WatchT, String>, TrackerInfo<WatchT>>()

    /**
     * Stopping brake.
     */
    private val brake = Brake<Unit>()

    override fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): CompletableFuture<Tracker<WatchT>> {
        val trackerKey = resourceBundle to key
        val resultFuture = CompletableFuture<Tracker<WatchT>>()
        val trackerInfo = TrackerInfo(tracker, resultFuture, null)

        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            // Stop with exception if pool is stopped.
            validateIsStopping { PoolIsStopped() }
            // Try to register tracker.
            if (registeredTrackers.putIfAbsent(trackerKey, trackerInfo) != null)
                throw TrackerAlreadyRegistered(resourceBundle, key)
        }

        // We shall call submit here, since tracker [init] method is potentially time consuming.
        // It can, for example, include recursive directory scanning, or remote repository initial fetching.
        val submissionHandle = registrationExecutorService.submit {
            // Stop silently if pool is stopped. Tracker will be stopped by [stop] method, since it is in the map already.
            if (this@DefaultTrackerPool.isStopped) return@submit

            val wrappedTracker: Tracker<WatchT>?
            val currentThread = Thread.currentThread()
            try {
                // We don't need additional type info, since there can only be one tracker by string key.
                val asTyped = key.typedKey<InitialEventsBunch<WatchT>>()
                // Try to init tracker and subscribe to its publisher.
                // Can throw exception due to pool stopping, but we will handle it later.
                val targetAggregator = aggregatorPool.getAggregator(asTyped)
                val trackerPublisher = tracker.init(resourceBundle, trackerExecutorService)
                trackerPublisher.subscribe(targetAggregator)

                // Start tracker. From now he is treated like started.
                if (!currentThread.isInterrupted) {
                    val handle = trackerExecutor.submit(tracker)
                    wrappedTracker = createTrackerWrapper(resourceBundle, key, tracker)
                    val newTracker = registeredTrackers.computeIfPresent(trackerKey) { _, old ->
                        // Check if we are replacing ourselves.
                        if (tracker === old.tracker) trackerInfo.copy(trackerTaskHandle = handle) else old
                    }

                    // If some other tracker accidentally was in the map, so cancel tracker work.
                    if (newTracker?.tracker !== tracker) handle.cancel(true)
                    resultFuture.complete(wrappedTracker)
                }

                // If submission handle had interrupted us, so stop tracker.
                if (currentThread.isInterrupted) {
                    doStopTracker(resourceBundle, key, true, tracker)
                }
            } catch (interrupt: InterruptedException) {
                // Force shutdown if initialization was interrupted.
                try {
                    doStopTracker(resourceBundle, key, true, tracker)
                } finally {
                    currentThread.interrupt()
                }
            } catch (cause: Throwable) {
                try {
                    // Force shutdown if initialization threw an exception.
                    doStopTracker(resourceBundle, key, true, tracker)
                } finally {
                    resultFuture.completeExceptionally(cause)
                    throw cause
                }
            }
        }

        // If someone cancels result future, then he must also cancel registration future as well.
        resultFuture.exceptionally { cause ->
            if (cause is CancellationException) {
                // No need to check, whether we are running, since two interrupts cannot hurt more.
                submissionHandle.cancel(true)
                mockTracker
            } else {
                throw cause
            }
        }

        return resultFuture
    }

    override fun stopTracker(resourceBundle: WatchT, key: String, force: Boolean): CompletableFuture<*> {
        validateIsStopping { PoolIsStopped() }
        val trackerKey = resourceBundle to key
        val foundTracker = registeredTrackers[trackerKey]?.tracker
        return if (foundTracker != null) {
            doStopTracker(resourceBundle, key, force, foundTracker)
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }


    override val isStopped get() = brake.get() != null

    override fun stop(force: Boolean) = brake.push { brk ->
        val trackersCopy = HashMap(registeredTrackers)
        val allTrackersStopFuture = trackersCopy
            .map { (key, value) -> doStopTracker(key.first, key.second, force, value.tracker) }
            .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }

        // Combine all futures in one and clear resources after their completion.
        // Order matters here.
        allTrackersStopFuture.thenAccept {
            registeredTrackers.clear()
            brk.complete(Unit)
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
        return object : Tracker<WatchT>() {
            override val isStopped: Boolean
                get() = this@DefaultTrackerPool.isStopped || tracker.isStopped

            override fun doStop(force: Boolean) =
                if (!this@DefaultTrackerPool.isStopped) {
                    doStopTracker(resourceBundle, key, false, tracker)
                } else {
                    CompletableFuture.completedFuture(Unit)
                }

            override fun doInit(executor: Executor) = tracker.doInit(executor)
            override fun run() = tracker.run()
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
    ): CompletableFuture<*> {
        val trackerKey = resourceBundle to key
        val foundInfo = registeredTrackers[trackerKey]
        // Ensure first, that we are stopping the right tracker.
        return if (foundInfo != null && foundInfo.tracker === tracker) {
            // Add handler to stop tracker and remove it from registered.
            val submissionHandle = foundInfo.submissionFuture
            // Separate future as workaround of no `thenCompose` on abnormal completion.
            val resultHandle = CompletableFuture<Unit>()
            submissionHandle.whenComplete { _, _ ->
                try {
                    // Additional flag to distinguish between no registered tracker and other registered.
                    val wasPresent = AtomicBoolean(false)
                    // Remove only that tracker, that we are stopping.
                    val present = registeredTrackers.computeIfPresent(trackerKey) { _, old ->
                        if (tracker === old.tracker) {
                            wasPresent.set(true); null
                        } else old
                    }
                    if (wasPresent.get() && present == null) { // Only stop once, on first removal.
                        if (force) foundInfo.trackerTaskHandle?.cancel(force)
                        tracker.stop(force).thenAccept { resultHandle.complete(Unit) }
                    } else resultHandle.complete(Unit)
                } catch (cause: Throwable) {
                    resultHandle.completeExceptionally(cause)
                }
            }
            // Cancel the handle in case it is not finished yet, if we are forcing.
            if (force) {
                submissionHandle.cancel(true)
            }
            resultHandle
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Throw exception if pool is stopping.
     */
    private fun validateIsStopping(toThrow: () -> Throwable) {
        if (isStopped) {
            throw toThrow()
        }
    }
}