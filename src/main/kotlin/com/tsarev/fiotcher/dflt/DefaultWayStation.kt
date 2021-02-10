package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingTransformer
import java.util.concurrent.ExecutorService

/**
 * Default implementation of [WayStation] that uses [DelegatingTransformer]
 * for asynchronous event transforming and anonymous objects for
 * synchronous transforming.
 */
// TODO Do we ever need this? Only for grouping executors.
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
) : WayStation {

    override fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit) = CommonListener(listener)

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
}