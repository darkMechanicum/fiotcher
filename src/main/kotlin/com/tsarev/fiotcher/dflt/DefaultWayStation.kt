package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingTransformer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Flow
import java.util.concurrent.ForkJoinPool

/**
 * Default implementation of [WayStation] that uses [DelegatingTransformer]
 * for asynchronous event transforming and anonymous objects for
 * synchronous transforming.
 */
class DefaultWayStation(
    /**
     * Max queue capacity, used for transformers.
     */
    private val maxTransformerCapacity: Int,

    /**
     * Executor service, used for asynchronous transformers.
     */
    private val transformerExecutorService: ExecutorService,

    /**
     * Executor, used at stopping.
     */
    private val stoppingExecutorService: ExecutorService,
) : WayStation {

    override fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit) = CommonListener(listener)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(transformer: (FromT) -> ToT?) =
        object : ChainingListener<FromT> {
            override fun onNext(item: FromT) = transformer(item)?.let { this@syncChainFrom.onNext(it) } ?: askNext()
            override fun onSubscribe(subscription: Flow.Subscription?) = this@syncChainFrom.onSubscribe(subscription)
            override fun onError(throwable: Throwable?) = this@syncChainFrom.onError(throwable)
            override fun onComplete() = this@syncChainFrom.onComplete()
            override fun stop(force: Boolean) = this@syncChainFrom.stop(force)
            override fun askNext() = this@syncChainFrom.askNext()
            override val isStopped get() = this@syncChainFrom.isStopped
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        object : ChainingListener<FromT> {
            override fun onNext(item: FromT) =
                transformer(item)?.filterNotNull()?.forEach { this@syncSplitFrom.onNext(it) } ?: askNext()

            override fun onSubscribe(subscription: Flow.Subscription?) = this@syncSplitFrom.onSubscribe(subscription)
            override fun onError(throwable: Throwable?) = this@syncSplitFrom.onError(throwable)
            override fun onComplete() = this@syncSplitFrom.onComplete()
            override fun stop(force: Boolean) = this@syncSplitFrom.stop(force)
            override fun askNext() = this@syncSplitFrom.askNext()
            override val isStopped get() = this@syncSplitFrom.isStopped
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(transformer: (FromT) -> ToT?) =
        asyncSplitFrom<FromT, ToT> { listOf(transformer(it)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        asyncDelegateFrom<FromT, ToT> { event, publisher ->
            val split = transformer(event)
            split?.filterNotNull()?.forEach { publisher(it) }
        }


    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(
        transformer: (FromT, (ToT) -> Unit) -> Unit
    ) = DelegatingTransformer(transformerExecutorService, maxTransformerCapacity, stoppingExecutorService, transformer, this)

}