package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingTransformer
import java.util.concurrent.Flow
import java.util.concurrent.ForkJoinPool

class DefaultWayStation : WayStation {

    private val threadPool = ForkJoinPool.commonPool()
    override fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit) =
        object : CommonListener<ResourceT>(listener) {}

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(transformer: (FromT) -> ToT?) =
        object : ChainingListener<FromT> {
            override fun onNext(item: FromT) = transformer(item)?.let { this@syncChainFrom.onNext(it) } ?: askNext()
            override fun onSubscribe(subscription: Flow.Subscription?) = this@syncChainFrom.onSubscribe(subscription)
            override fun onError(throwable: Throwable?) = this@syncChainFrom.onError(throwable)
            override fun onComplete() = this@syncChainFrom.onComplete()
            override fun stop(force: Boolean) = this@syncChainFrom.stop(force)
            override fun askNext() = this@syncChainFrom.askNext()
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
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(transformer: (FromT) -> ToT?) =
        asyncSplitFrom<FromT, ToT> { listOf(transformer(it)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        asyncDelegateFrom<FromT, ToT> { event, publisher ->
            val split = transformer(event)
            split?.filterNotNull()?.forEach { publisher(it) }
        }


    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(transformer: (FromT, (ToT) -> Unit) -> Unit) =
        DelegatingTransformer(threadPool, transformer, this)

}