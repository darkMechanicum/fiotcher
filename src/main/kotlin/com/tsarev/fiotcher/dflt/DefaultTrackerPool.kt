package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.tracker.*
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.flows.Aggregator
import java.util.concurrent.*

/**
 * Default tracker pool implementation, which also serves as
 * [TrackerEventBunch] notifier.
 */
class DefaultTrackerPool<WatchT : Any>(
    /**
     * Executor, used to run trackers.
     */
    override val trackerExecutor: ExecutorService,

    /**
     * Executor, used to perform queue submission and processing by aggregators and trackers.
     */
    private val queueExecutorService: ExecutorService,

    /**
     * Executor, used to perform trackers registration process.
     */
    private val registrationExecutorService: ExecutorService,

    /**
     * Executor, used at stopping.
     */
    private val stoppingExecutorService: ExecutorService,

    /**
     * Maximum capacity for aggregator queues.
     */
    private val aggregatorMaxCapacity: Int

) : TrackerPool<WatchT>, AggregatorListenerRegistry<WatchT> {

    companion object {
        val mockStoppable = object : Stoppable {
            override val isStopped = true
            override fun stop(force: Boolean) = CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Utility class to hold tracker related info.
     */
    internal data class TrackerInfo<WatchT : Any>(
        val tracker: Tracker<WatchT>,
        val submissionFuture: CompletableFuture<Stoppable>,
        val trackerTaskHandle: Future<*>?
    )

    /**
     * Registerer trackers, by resource and key.
     */
    private val registeredTrackers = ConcurrentHashMap<Pair<WatchT, String>, TrackerInfo<WatchT>>()

    /**
     * Registered listeners, by key.
     */
    private val registeredListeners = ConcurrentHashMap<String, ChainingListener<TrackerEventBunch<WatchT>>>()

    /**
     * Events, that are generated by trackers, aggregated by keys.
     */
    private val aggregators = ConcurrentHashMap<String, Aggregator<TrackerEventBunch<WatchT>>>()

    /**
     * Stopping brake.
     */
    @Volatile
    private var brake: CompletableFuture<Unit>? = null

    override fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): CompletableFuture<Stoppable> {
        val trackerKey = resourceBundle to key
        val resultFuture = CompletableFuture<Stoppable>()
        val trackerInfo = TrackerInfo(tracker, resultFuture, null)

        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            // Stop with exception if pool is stopped.
            checkIsStopping { PoolIsStopping() }
            // Try to register tracker.
            if (registeredTrackers.putIfAbsent(trackerKey, trackerInfo) != null)
                throw TrackerAlreadyRegistered(resourceBundle, key)
        }

        // We shall call submit here, since tracker [init] method is potentially time consuming.
        // It can, for example, include recursive directory scanning, or remote repository initial fetching.
        val submissionHandle = registrationExecutorService.submit {
            // Stop silently if pool is stopped. Tracker will be stopped by [stop] method, since it is in the map already.
            if (this@DefaultTrackerPool.isStopped) return@submit

            var stoppableWrapper: Stoppable? = null
            try {
                // Try to init tracker and subscribe to its publisher.
                // Can throw exception due to pool stopping, but we will handle it later.
                val targetAggregator = getAggregator(key)
                val trackerPublisher = tracker.init(resourceBundle, queueExecutorService)
                trackerPublisher.subscribe(targetAggregator)

                // Start tracker. From now he is treated like started.
                val handle = trackerExecutor.submit(tracker)
                stoppableWrapper = createTrackerStoppable(resourceBundle, key, tracker)
                val newTracker = registeredTrackers.computeIfPresent(trackerKey) { _, old ->
                    // Check if we are replacing ourselves.
                    if (tracker === old.tracker) trackerInfo.copy(trackerTaskHandle = handle) else old
                }

                // If some other tracker accidentally was in the map, so cancel tracker work.
                if (newTracker?.tracker !== tracker) handle.cancel(true)

                resultFuture.complete(stoppableWrapper)

                // If submission handle had interrupted us, so stop tracker.
                if (Thread.currentThread().isInterrupted) {
                    doStopTracker(resourceBundle, key, true, tracker)
                }
            } catch (interrupt: InterruptedException) {
                // Force shutdown if initialization was interrupted.
                try {
                    doStopTracker(resourceBundle, key, true, tracker)
                } finally {
                    Thread.currentThread().interrupt()
                }
            } catch (cause: Throwable) {
                try {
                    // Force shutdown if initialization threw an exception.
                    doStopTracker(resourceBundle, key, true, tracker)
                } finally {
                    resultFuture.completeExceptionally(cause)
                    throw cause
                }
            } finally {
                resultFuture.complete(stoppableWrapper ?: mockStoppable)
            }
        }

        // If someone cancels result future, then he must also cancel registration future as well.
        resultFuture.exceptionally { cause ->
            if (cause is CancellationException) {
                // No need to check, whether we are running, since two interrupts cannot hurt more.
                submissionHandle.cancel(true)
                mockStoppable
            } else {
                throw cause
            }
        }

        return resultFuture
    }

    override fun stopTracker(resourceBundle: WatchT, key: String, force: Boolean): CompletableFuture<*> {
        checkIsStopping { PoolIsStopping() }
        val trackerKey = resourceBundle to key
        val foundTracker = registeredTrackers[trackerKey]?.tracker
        return if (foundTracker != null) {
            doStopTracker(resourceBundle, key, force, foundTracker)
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    override fun registerListener(listener: ChainingListener<TrackerEventBunch<WatchT>>, key: String): Stoppable {
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            checkIsStopping { ListenerRegistryIsStopping() }
            if (registeredListeners.putIfAbsent(key, listener) != null) throw ListenerAlreadyRegistered(key)
        }
        // Can throw exception due to pool stopping, but user will handle it.
        getAggregator(key).subscribe(listener)
        return createListenerStoppable(key, listener)
    }

    override fun deRegisterListener(key: String, force: Boolean): CompletionStage<*> {
        checkIsStopping { ListenerRegistryIsStopping() }
        // Check if we need to de register anything.
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null) doDeRegisterListener(
            key, force, deRegistered
        ) else CompletableFuture.completedFuture(Unit)
    }

    override val isStopped get() = brake != null

    override fun stop(force: Boolean): CompletionStage<*> {
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            val brakeCopy = brake
            // From this moment no new trackers will be added.
            if (brakeCopy != null) return brakeCopy
            brake = CompletableFuture<Unit>()
        }

        val trackersCopy = HashMap(registeredTrackers)
        val allTrackersStopFuture = trackersCopy
            .map { (key, value) -> doStopTracker(key.first, key.second, force, value.tracker) }
            .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }

        val listenersCopy = HashMap(registeredListeners)
        val allListenersStopFuture = listenersCopy
            .map { deRegisterListener(it.key, force) }
            .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }

        val aggregatorsCopy = HashMap(aggregators)
        val allAggregatorsStopFuture = aggregatorsCopy.values
            .map { it.stop(force) }
            .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }

        val executorShutDownFuture: CompletionStage<*> = CompletableFuture.supplyAsync(
            {
                if (force) trackerExecutor.shutdownNow() else trackerExecutor.shutdown()
                if (force) queueExecutorService.shutdownNow() else queueExecutorService.shutdown()
                if (force) registrationExecutorService.shutdownNow() else registrationExecutorService.shutdown()
            }, stoppingExecutorService
        )

        // Combine all futures in one and clear resources after their completion.
        allTrackersStopFuture.thenAcceptBoth(allListenersStopFuture) { _, _ -> }
            .thenAcceptBoth(allAggregatorsStopFuture) { _, _ -> }
            .thenAcceptBoth(executorShutDownFuture) { _, _ ->
                registeredTrackers.clear()
                registeredListeners.clear()
                aggregators.clear()

                brake!!.complete(Unit)
            }

        // Brake is guaranteed to be not null here.
        return brake!!
    }

    /**
     * Perform actual listener de registration.
     */
    private fun doDeRegisterListener(
        key: String,
        force: Boolean,
        listener: ChainingListener<TrackerEventBunch<WatchT>>
    ): CompletionStage<*> {
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null && listener === deRegistered) {
            // Remove only that listener, that we are de registering.
            deRegistered.stop(force).thenAccept {
                registeredListeners.computeIfPresent(key) { _, old ->
                    if (old === listener) null else old
                }
            } ?: CompletableFuture.completedFuture(Unit)
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Create a handle, that stops the tracker and de registers it.
     */
    private fun createTrackerStoppable(resourceBundle: WatchT, key: String, tracker: Tracker<WatchT>): Stoppable {
        return object : Stoppable {
            override val isStopped: Boolean
                get() = this@DefaultTrackerPool.isStopped || tracker.isStopped

            override fun stop(force: Boolean) =
                if (!this@DefaultTrackerPool.isStopped)
                    doStopTracker(resourceBundle, key, false, tracker)
                else
                    CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Create a handle, that stops the tracker and de registers it.
     */
    private fun createListenerStoppable(key: String, listener: ChainingListener<TrackerEventBunch<WatchT>>): Stoppable {
        return object : Stoppable {
            override val isStopped: Boolean
                get() = this@DefaultTrackerPool.isStopped || listener.isStopped

            override fun stop(force: Boolean) = if (!this@DefaultTrackerPool.isStopped) doDeRegisterListener(
                key, force, listener
            ) else CompletableFuture.completedFuture(Unit)
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
            // Remove only that tracker, that we are stopping.
            val resultHandle = submissionHandle.whenComplete { _, _ ->
                registeredTrackers.computeIfPresent(trackerKey) { _, old -> if (tracker === old.tracker) null else old }
            }.thenCompose { stoppable ->
                if (force) foundInfo.trackerTaskHandle?.cancel(force)
                stoppable?.stop(force)?.thenAccept { }
            }
            // Cancel the handle in case it is not finished yet if we are forcing.
            submissionHandle.cancel(true)
            resultHandle
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Create new aggregator at need.
     */
    private fun getAggregator(key: String): Aggregator<TrackerEventBunch<WatchT>> {
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            checkIsStopping { PoolIsStopping() }
            return aggregators.computeIfAbsent(key) {
                Aggregator(queueExecutorService, aggregatorMaxCapacity)
            }
        }
    }

    /**
     * Throw exception if pool is stopping.
     */
    private fun checkIsStopping(toThrow: () -> Throwable) {
        if (isStopped) {
            throw toThrow()
        }
    }
}