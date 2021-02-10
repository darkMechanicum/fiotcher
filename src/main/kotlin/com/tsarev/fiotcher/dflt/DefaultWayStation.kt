package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.KClassTypedKey
import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.dflt.flows.Aggregator
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingTransformer
import com.tsarev.fiotcher.dflt.flows.SingleSubscriptionSubscriber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Default implementation of [WayStation] that uses [DelegatingTransformer]
 * for asynchronous event transforming and anonymous objects for
 * synchronous transforming.
 */
class DefaultWayStation(
    /**
     * Max queue capacity, used for transformers.
     */
    private val maxTransformerQueueCapacity: Int,

    /**
     * Executor service, used for asynchronous transformers.
     */
    private val transformerExecutorService: ExecutorService,

    /**
     * Executor, used at stopping.
     */
    private val stoppingExecutorService: ExecutorService,

    /**
     * Aggregator pool.
     */
    private val aggregatorPool: DefaultAggregatorPool
) : WayStation {

    override fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit) = CommonListener(listener)

    override fun <ResourceT : Any> doAggregate(key: KClassTypedKey<ResourceT>): ChainingListener<ResourceT> {
        val aggregator = aggregatorPool.getAggregator(key)
        val proxy = SubscriptionProxy(aggregator)
        aggregator.onSubscribe(proxy)
        return proxy
    }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(transformer: (FromT) -> ToT?) =
        syncSplitFrom<FromT, ToT> { event -> listOf(transformer(event)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        syncDelegateFrom<FromT, ToT> { event, publisher ->
            val split = transformer(event)
            split?.filterNotNull()?.forEach { publisher(it) }
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncDelegateFrom(transformer: (FromT, (ToT) -> Unit) -> Unit) =
        this.doSyncDelegateFrom(transformer)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(transformer: (FromT) -> ToT?) =
        asyncSplitFrom<FromT, ToT> { listOf(transformer(it)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        asyncDelegateFrom<FromT, ToT> { event, publisher ->
            val split = transformer(event)
            split?.filterNotNull()?.forEach { publisher(it) }
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(transformer: (FromT, (ToT) -> Unit) -> Unit) =
        doAsyncDelegateFrom(
            transformerExecutorService,
            stoppingExecutorService,
            maxTransformerQueueCapacity,
            transformer
        )

    /**
     * Class that represents [Flow.Subscription] connection to aggregator.
     */
    class SubscriptionProxy<EventT : Any>(
        private var aggregator: Aggregator<EventT>?
    ) : SingleSubscriptionSubscriber<EventT>(), Flow.Subscription {
        private val requested = AtomicLong(0)
        private val canceled = AtomicBoolean(false)

        override fun doOnNext(item: EventT) {
            if (!canceled.get()) {
                val lowered = requested.decrementAndGet()
                if (lowered >= 0) aggregator?.onNext(item)
                else requested.incrementAndGet()
            }
        }

        override fun request(n: Long) {
            while(true) {
                val expected = requested.get()
                // Check for overflow.
                val toSet = if (Long.MAX_VALUE - expected < n) Long.MAX_VALUE else expected + n
                if (requested.compareAndSet(expected, toSet)) break
            }
        }

        override fun cancel() {
            canceled.set(true)
            // Free aggregator.
            aggregator = null
        }
    }
}