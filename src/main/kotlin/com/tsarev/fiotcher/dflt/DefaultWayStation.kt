package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.KClassTypedKey
import com.tsarev.fiotcher.dflt.flows.Aggregator
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncTransformer
import com.tsarev.fiotcher.dflt.flows.SingleSubscriptionSubscriber
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.flow.ChainingListener
import com.tsarev.fiotcher.internal.flow.WayStation
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Default implementation of [WayStation] that uses [DelegatingAsyncTransformer]
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
    private val transformerExecutor: Executor,

    /**
     * Executor, used at stopping.
     */
    private val stoppingExecutor: Executor,

    /**
     * Aggregator pool.
     */
    private val aggregatorPool: DefaultAggregatorPool
) : WayStation {

    override fun <ResourceT : Any> createCommonListener(
        handleErrors: ((Throwable) -> Throwable?)?,
        listener: (ResourceT) -> Unit
    ) = CommonListener(onNextHandler = listener, onErrorHandler = {
        // Aggressive error handling on end listener.
        if (handleErrors != null) {
            val transformed = handleErrors(it)
            if (transformed != null) {
                throw transformed
            }
        } else {
            throw it
        }
    })

    override fun <ResourceT : Any> doAggregate(
        handleErrors: ((Throwable) -> Throwable?)?,
        key: KClassTypedKey<ResourceT>,
    ): ChainingListener<ResourceT> {
        val aggregator = aggregatorPool.getAggregator(key)
        val proxy = SubscriptionProxy(aggregator)
        aggregator.onSubscribe(proxy)
        return proxy
    }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(
        handleErrors: ((Throwable) -> Throwable?)?,
        transformer: (FromT) -> ToT?,
    ) = syncSplitFrom<FromT, ToT>(handleErrors) { event -> listOf(transformer(event)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(
        handleErrors: ((Throwable) -> Throwable?)?,
        transformer: (FromT) -> Collection<ToT?>?,
    ) = syncDelegateFrom<FromT, ToT>(handleErrors) { event, publisher ->
        val split = transformer(event)
        split?.filterNotNull()?.forEach { publisher(it) }
    }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncDelegateFrom(
        handleErrors: ((Throwable) -> Throwable?)?,
        transformer: (FromT, (ToT) -> Unit) -> Unit,
    ) = doSyncDelegateFrom(transformer, handleErrors)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(
        handleErrors: ((Throwable) -> Throwable?)?,
        transformer: (FromT) -> ToT?,
    ) = asyncSplitFrom<FromT, ToT>(handleErrors) { listOf(transformer(it)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(
        handleErrors: ((Throwable) -> Throwable?)?,
        transformer: (FromT) -> Collection<ToT?>?,
    ) = asyncDelegateFrom<FromT, ToT>(handleErrors) { event, publisher ->
        val split = transformer(event)
        split?.filterNotNull()?.forEach { publisher(it) }
    }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(
        handleErrors: ((Throwable) -> Throwable?)?,
        transformer: (FromT, (ToT) -> Unit) -> Unit,
    ) = doAsyncDelegateFrom(
        executor = transformerExecutor,
        stoppingExecutor = stoppingExecutor,
        maxCapacity = maxTransformerQueueCapacity,
        transformer = transformer,
        handleErrors = handleErrors
    )

    /**
     * Class that represents [Flow.Subscription] connection to aggregator.
     */
    class SubscriptionProxy<EventT : Any>(
        private var aggregator: Aggregator<EventT>?
    ) : SingleSubscriptionSubscriber<EventT>(), Flow.Subscription {
        private val requested = AtomicLong(0)
        private val canceled = AtomicBoolean(false)

        override fun doOnNext(item: EventWithException<EventT>) {
            if (!canceled.get()) {
                val lowered = requested.decrementAndGet()
                if (lowered >= 0) aggregator?.onNext(item)
                else requested.incrementAndGet()
            }
        }

        override fun request(n: Long) {
            while (true) {
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

        override fun doOnComplete() {
            canceled.set(true)
            // Free aggregator.
            aggregator = null
        }

        override fun doOnError(throwable: Throwable) = Unit
    }
}