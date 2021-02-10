package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Processor
import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.api.pool.ListenerPool
import com.tsarev.fiotcher.api.pool.TrackerPool
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.ForkJoinPool

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
    transformerExecutorService: ExecutorService = Executors.newCachedThreadPool(), // Separate pool due to possible I/O operations while reading changes.

    /**
     * Executor, used to run trackers.
     */
    trackerExecutor: ExecutorService = Executors.newCachedThreadPool(), //Separate pool due to possible blocking I/O operations while tracking.

    /**
     * Executor, used to perform queue processing by aggregators.
     */
    queueExecutorService: ExecutorService = ForkJoinPool.commonPool(),

    /**
     * Executor, used to perform trackers registration process.
     */
    registrationExecutorService: ExecutorService = ForkJoinPool.commonPool(),

    /**
     * Executor, used at stopping.
     */
    stoppingExecutorService: ExecutorService = Executors.newCachedThreadPool(), // Separate pool for possible-blocking shutdown.

    /**
     * Maximum capacity for aggregator queues.
     */
    aggregatorMaxCapacity: Int = Flow.defaultBufferSize() shl 1,

    private val aggregatorPool: DefaultAggregatorPool = DefaultAggregatorPool(
        aggregatorMaxCapacity,
        queueExecutorService
    ),

    private val wayStation: WayStation = DefaultWayStation(
        maxTransformerCapacity,
        transformerExecutorService,
        stoppingExecutorService,
        aggregatorPool
    ),

    private val trackerPool: TrackerPool<WatchT> = DefaultTrackerPool<WatchT>(
        trackerExecutor,
        queueExecutorService,
        registrationExecutorService,
        stoppingExecutorService,
        aggregatorPool
    ),

    private val listenerPool: ListenerPool = DefaultListenerPool(
        aggregatorPool
    ),

    ) : Processor<WatchT>,
    TrackerPool<WatchT> by trackerPool,
    ListenerPool by listenerPool,
    WayStation by wayStation