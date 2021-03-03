package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.Processor
import com.tsarev.fiotcher.internal.pool.ListenerPool
import com.tsarev.fiotcher.internal.pool.TrackerPool
import java.util.concurrent.*

/**
 * Default processor implementation, using
 * [DefaultWayStation] and [DefaultTrackerPool].
 */
class DefaultProcessor<WatchT : Any> private constructor(
    /**
     * Executor service, used for processing.
     */
    private val executorService: ExecutorService = Executors.newCachedThreadPool(),

    /**
     * Marker field to avoid constructor cycle.
     */
    @Suppress("unused") private val marker: Any,

    private val publisherPool: DefaultPublisherPool<EventWithException<InitialEventsBunch<WatchT>>> = DefaultPublisherPool(
        executorService,
        256
    ),

    private val trackerPool: DefaultTrackerPool<WatchT> = DefaultTrackerPool(executorService, publisherPool),

    private val listenerPool: DefaultListenerPool<InitialEventsBunch<WatchT>> = DefaultListenerPool(publisherPool),

    ) : Processor<WatchT>, TrackerPool<WatchT> by trackerPool, ListenerPool<InitialEventsBunch<WatchT>> by listenerPool,
    Stoppable, StoppableBrakeMixin<Unit> {

    // Allowed public constructor.
    constructor(executorService: ExecutorService) : this(executorService, Any())

    /**
     * Main processor brake.
     */
    override val stopBrake = Brake<Unit>()

    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push {
        if (force) {
            listenerPool.stop(true)
            trackerPool.stop(true)
            publisherPool.clear()
            executorService.shutdownNow()
            complete(Unit)
        } else {
            // Order is important to guarantee graceful stopping.
            trackerPool.stop(false)
                .thenCompose { listenerPool.stop(false).thenAccept { } }
                .thenAccept {
                    publisherPool.clear()
                    // Stopping executor must be shot down lastly.
                    executorService.shutdown()
                    complete(Unit)
                }
        }
    }

}