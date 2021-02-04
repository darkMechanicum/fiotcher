package com.tsarev.fiotcher.flows

import com.tsarev.fiotcher.common.Stoppable
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Listener, that can chain events.
 */
interface ChainingListener<ResourceT : Any> : Flow.Subscriber<ResourceT>, Stoppable {

    /**
     * Create proxy listener, that will handle pre converted events.
     */
    fun <ToT : Any> chainFrom(
        transformer: (ToT) -> ResourceT
    ): ChainingListener<ToT> = object : ChainingListener<ToT> {
        override fun onNext(item: ToT) = this@ChainingListener.onNext(transformer(item))
        override fun onSubscribe(subscription: Flow.Subscription?) = this@ChainingListener.onSubscribe(subscription)
        override fun onError(throwable: Throwable?) = this@ChainingListener.onError(throwable)
        override fun onComplete() = this@ChainingListener.onComplete()
        override fun stop(force: Boolean) = this@ChainingListener.stop(force)
    }

    /**
     * Create proxy listener, that will handle pre converted events in new queue.
     */
    fun <ToT : Any> chainFrom(
        executor: Executor,
        transformer: (ToT) -> ResourceT
    ): ChainingListener<ToT> = splitFrom(executor) { listOf(transformer(it)) }

    /**
     * Create proxy listener, that will handle split events in new queue.
     */
    fun <ToT : Any> splitFrom(
        executor: Executor,
        transformer: (ToT) -> Collection<ResourceT>
    ): ChainingListener<ToT> = SplittingTransformer(executor, transformer, this)

}