package com.tsarev.fiotcher.simple

import com.tsarev.fiotcher.flows.Aggregator
import com.tsarev.fiotcher.flows.ChainingListener
import com.tsarev.fiotcher.flows.CommonListener
import com.tsarev.fiotcher.tracker.*
import java.net.URI
import java.util.concurrent.*

/**
 * Simple tracker pool implementation, which also serves as
 * [TrackerEventBunch] notifier.
 */
class SimpleTrackerPool : TrackerPool, TrackerListenerRegistry {

    companion object {
        const val keyPrefix = "other.key."
        const val nullKey = "null.key"
    }

    /**
     * Thread pool, used to launch trackers.
     */
    private val threadPool = Executors.newCachedThreadPool()

    /**
     * Registerer trackers, by resource and key.
     */
    private val registeredTrackers = ConcurrentHashMap<Pair<URI, String>, Pair<Tracker, Future<*>>>()

    /**
     * Registered listeners, by key.
     */
    private val registeredListeners = ConcurrentHashMap<String, ChainingListener<TrackerEventBunch>>()

    /**
     * Events, that are generated by trackers, aggregated by keys.
     */
    private val aggregators = ConcurrentHashMap<String, Aggregator<TrackerEventBunch>>()

    /**
     * If this pool is active.
     */
    @Volatile
    private var running = true

    override fun registerTracker(resourceBundle: URI, tracker: Tracker, key: String?): Future<*> {
        checkIsStopping()
        val wrapped = key.wrapKey
        val trackerKey = Pair(resourceBundle, wrapped)
        // Prepare mocked tracker and future, in case someone decide to stop, before registration is complete.
        val mockTaskFuture = CompletableFuture<Any>()
        val mockStopFuture = CompletableFuture<Boolean>()
        val mockTracker = createMockTracker(mockStopFuture)
        val mockPair = Pair(mockTracker, mockTaskFuture)
        if (registeredTrackers.putIfAbsent(trackerKey, mockPair) != null) throw TrackerAlreadyRegistered(
            resourceBundle,
            wrapped
        )
        val result = threadPool.submit {
            if (!this@SimpleTrackerPool.running) return@submit
            // First, we must replace mocker tracker with real one, to redirect stop requests.
            val existed = registeredTrackers.computeIfPresent(trackerKey) { _, _ -> Pair(tracker, mockTaskFuture) }
            // Here we must check - if someone stopped mocked tracker, than clear entry and return.
            if (existed == null || mockStopFuture.isDone) {
                return@submit
            }
            try {
                // Init tracker and subscribe to its publisher.
                val targetAggregator = getAggregator(wrapped)
                tracker.init(resourceBundle, threadPool).subscribe(targetAggregator)
                threadPool.submit(tracker)
            } catch (cause: Throwable) {
                registeredTrackers.remove(trackerKey)?.first?.stop(true) ?: return@submit
                throw cause
            }
        }
        registeredTrackers[trackerKey] = Pair(tracker, result)
        // If someone cancelled mock future, so we must do with real one and
        // return completed future, as we finished registration.
        // But we can't alter [registeredTrackers] since someone that stopped our
        // tracker had done it.
        return if (mockTaskFuture.isCancelled || mockStopFuture.isDone) {
            result.cancel(true)
            // Use force flag from outer caller.
            tracker.stop(mockStopFuture.get())
            return CompletableFuture.completedFuture(Unit)
        } else result
    }

    override fun deRegisterTracker(resourceBundle: URI, key: String?, force: Boolean): Future<*> {
        checkIsStopping()
        val wrapped = key.wrapKey
        val trackerKey = Pair(resourceBundle, wrapped)
        return threadPool.submit {
            if (!this@SimpleTrackerPool.running) return@submit
            val existed = registeredTrackers.remove(trackerKey)
            if (existed != null) {
                try {
                    // Try to stop graceful.
                    val stopFuture = existed.first.stop(force)
                    existed.second.cancel(force)
                    stopFuture.get()
                    return@submit
                } catch (outerCause: Throwable) {
                    try {
                        // Something went wrong - force it.
                        if (!force) {
                            val stopFuture = existed.first.stop(true)
                            existed.second.cancel(true)
                            stopFuture.get()
                            return@submit
                        }
                    } catch (innerCause: Throwable) {
                        // Retain all failure info.
                        throw RuntimeException(innerCause).apply { addSuppressed(outerCause) }
                    }
                }
            }
        }
    }

    override fun stop(force: Boolean): Future<Unit> {
        running = false
        // We are well assured that no one can add trackers here.
        // So we just iterate and stop them.
        return CompletableFuture.supplyAsync {
            registeredTrackers.map {
                deRegisterTracker(it.key.first, it.key.second, force).get()
            }
            aggregators.map {
                it.value.stop(force)
            }
            registeredListeners.clear()
            if (force) threadPool.shutdownNow() else threadPool.shutdown()
        }
    }

    override fun registerListener(listener: ChainingListener<TrackerEventBunch>, key: String?) {
        checkIsStopping()
        val wrapped = key.wrapKey
        if (registeredListeners.putIfAbsent(wrapped, listener) != null) throw TrackerListenerAlreadyRegistered(wrapped)
        getAggregator(wrapped).subscribe(listener)
    }

    override fun deRegisterListener(key: String?, force: Boolean) {
        checkIsStopping()
        val wrapped = key.wrapKey
        registeredListeners.computeIfPresent(wrapped) { _, old ->
            old.stop(true)
            null
        }
    }

    /**
     * Create new aggregator at need.
     */
    private fun getAggregator(key: String): Aggregator<TrackerEventBunch> {
        return aggregators.computeIfAbsent(key) {
            Aggregator(threadPool)
        }
    }

    /**
     * Throw exception if pool is stopping.
     */
    private fun checkIsStopping() {
        if (!running) {
            throw PoolIsStopping()
        }
    }

    /**
     * Create mock tracker, which can only hold simple signal state.
     */
    private fun createMockTracker(future: CompletableFuture<Boolean>) = object : Tracker() {
        override fun doInit(executor: Executor) = throw RuntimeException("Must be never invoked.")
        override fun run() = throw RuntimeException("Must be never invoked.")
        override fun stop(force: Boolean) = future.apply { complete(force) }
    }

    /**
     * Wrap null keys.
     */
    private val String?.wrapKey
        get() = this?.let { "$keyPrefix$it" } ?: nullKey
}