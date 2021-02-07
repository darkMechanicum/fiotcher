package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.flows.ChainingListener
import com.tsarev.fiotcher.intermediate.WayStation
import java.util.concurrent.ForkJoinPool

class DefaultWayStation : WayStation {

    private val threadPool = ForkJoinPool.commonPool()

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(transformer: (FromT) -> ToT?) =
        this.chainFrom(transformer)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        this.splitFrom(transformer)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(transformer: (FromT) -> ToT?) =
        this.chainFrom(threadPool, transformer)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        this.splitFrom(threadPool, transformer)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(transformer: (FromT, (ToT) -> Unit) -> Unit) =
        this.delegateFrom(threadPool, transformer)

}