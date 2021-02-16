package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.internal.Processor
import com.tsarev.fiotcher.internal.flow.WayStation
import com.tsarev.fiotcher.internal.pool.ListenerPool
import com.tsarev.fiotcher.internal.pool.TrackerPool
import java.util.concurrent.*

/**
 * Default processor implementation, using
 * [DefaultWayStation] and [DefaultTrackerPool].
 */
class DefaultProcessor<WatchT : Any>(
    /**
     * Max queue capacity, used for transformers.
     */
    maxTransformerCapacity: Int = Flow.defaultBufferSize(),

    /**
     * Executor service, used for asynchronous transformers.
     */
    // Separate pool due to possible blocking operations while reading changes.
    private val transformerExecutorService: ExecutorService = Executors.newCachedThreadPool(),

    /**
     * Executor, used to run trackers.
     */
    //Separate pool due to possible blocking operations while tracking.
    private val trackerExecutor: ExecutorService = Executors.newCachedThreadPool(),

    /**
     * Executor, used to perform tracker event publishing to aggregators.
     */
    private val trackerExecutorService: ExecutorService = ForkJoinPool.commonPool(),

    /**
     * Executor, used to perform queue aggregation.
     */
    private val aggregatorExecutorService: ExecutorService = ForkJoinPool.commonPool(),

    /**
     * Executor, used to perform trackers registration process.
     */
    private val registrationExecutorService: ExecutorService = ForkJoinPool.commonPool(),

    /**
     * Executor, used at stopping.
     */
    // Separate pool for possible-blocking shutdown.
    private val stoppingExecutorService: ExecutorService = Executors.newCachedThreadPool(),

    /**
     * Maximum capacity for aggregator queues.
     */
    aggregatorMaxCapacity: Int = Flow.defaultBufferSize() shl 1,

    private val aggregatorPool: DefaultAggregatorPool = DefaultAggregatorPool(
        aggregatorMaxCapacity,
        aggregatorExecutorService,
        stoppingExecutorService
    ),

    private val wayStation: WayStation = DefaultWayStation(
        maxTransformerCapacity,
        transformerExecutorService,
        stoppingExecutorService,
        aggregatorPool
    ),

    private val trackerPool: TrackerPool<WatchT> = DefaultTrackerPool(
        trackerExecutor,
        trackerExecutorService,
        registrationExecutorService,
        aggregatorPool
    ),

    private val listenerPool: ListenerPool = DefaultListenerPool(
        aggregatorPool
    ),

    ) : Processor<WatchT>, Stoppable, TrackerPool<WatchT> by trackerPool, ListenerPool by listenerPool,
    WayStation by wayStation {

    /**
     * Main processor brake.
     */
    private val brake = Brake<Unit>()

    override val isStopped get() = brake.isPushed

    override fun stop(force: Boolean) = brake.push { brk ->
        if (force) {
            val aggregatorHandle = aggregatorPool.stop(force)
            val listenerHandle = listenerPool.stop(force).thenAccept { }
            val executorsHandle = CompletableFuture.runAsync({
                trackerExecutor.shutdownNow()
                aggregatorExecutorService.shutdownNow()
                transformerExecutorService.shutdownNow()
                trackerExecutorService.shutdownNow()
                registrationExecutorService.shutdownNow()
            }, stoppingExecutorService)
            trackerPool.stop(force)
                .thenCompose { aggregatorHandle }
                .thenCompose { listenerHandle }
                .thenCompose { executorsHandle }
                .thenAccept {
                    // Stopping executor must be shot down lastly.
                    stoppingExecutorService.shutdown()
                    brk.complete(Unit)
                }
        } else {
            // Order is important to guarantee graceful stopping order.
            trackerPool.stop(force)
                .thenCompose { aggregatorPool.stop(force) }
                .thenCompose { listenerPool.stop(force).thenAccept { } }
                .thenAccept {
                    trackerExecutor.shutdown()
                    aggregatorExecutorService.shutdown()
                    transformerExecutorService.shutdown()
                    trackerExecutorService.shutdown()
                    registrationExecutorService.shutdown()
                    stoppingExecutorService.shutdown()
                    brk.complete(Unit)
                }
        }
    }

    override fun stopAndWait(force: Boolean) {
        stop(force).toCompletableFuture().get()
    }

    override fun stopAndWait(timeout: Long, unit: TimeUnit, force: Boolean) {
        stop(force).toCompletableFuture().get(timeout, unit)
    }

}