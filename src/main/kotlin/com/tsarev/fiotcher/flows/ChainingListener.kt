package com.tsarev.fiotcher.flows

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Listener, that can chain events.
 */
interface ChainingListener<ResourceT : Any> : Flow.Subscriber<ResourceT>, Stoppable {

    /**
     * Ask next event.
     */
    fun askNext(): Unit

    /**
     * Create proxy listener, that will handle pre converted events.
     */
    fun <ToT : Any> chainFrom(
        transformer: (ToT) -> ResourceT?
    ): ChainingListener<ToT> = object : ChainingListener<ToT> {
        override fun onNext(item: ToT) = transformer(item)?.let { this@ChainingListener.onNext(it) } ?: askNext()
        override fun onSubscribe(subscription: Flow.Subscription?) = this@ChainingListener.onSubscribe(subscription)
        override fun onError(throwable: Throwable?) = this@ChainingListener.onError(throwable)
        override fun onComplete() = this@ChainingListener.onComplete()
        override fun stop(force: Boolean) = this@ChainingListener.stop(force)
        override fun askNext() = this@ChainingListener.askNext()
    }

    /**
     * Create proxy listener, that will handle pre converted events.
     */
    fun <ToT : Any> splitFrom(
        transformer: (ToT) -> Collection<ResourceT?>?
    ): ChainingListener<ToT> = object : ChainingListener<ToT> {
        override fun onNext(item: ToT) = transformer(item)?.filterNotNull()?.forEach { this@ChainingListener.onNext(it) } ?: askNext()
        override fun onSubscribe(subscription: Flow.Subscription?) = this@ChainingListener.onSubscribe(subscription)
        override fun onError(throwable: Throwable?) = this@ChainingListener.onError(throwable)
        override fun onComplete() = this@ChainingListener.onComplete()
        override fun stop(force: Boolean) = this@ChainingListener.stop(force)
        override fun askNext() = this@ChainingListener.askNext()
    }

    /**
     * Create proxy listener, that will handle pre converted events in new queue.
     */
    fun <FromT : Any> chainFrom(
        executor: Executor,
        transformer: (FromT) -> ResourceT?
    ): ChainingListener<FromT> = splitFrom(executor) { listOf(transformer(it)) }

    /**
     * Create proxy listener, that will handle split events in new queue.
     */
    fun <FromT : Any> splitFrom(
        executor: Executor,
        transformer: (FromT) -> Collection<ResourceT?>?
    ): ChainingListener<FromT> = delegateFrom(executor) { event, publisher ->
        val split = transformer(event)
        split?.filterNotNull()?.forEach { publisher(it) }
    }

    /**
     * Create proxy listener, that will handle split events in new queue.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any> delegateFrom(
        executor: Executor,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit
    ): ChainingListener<FromT> = DelegatingTransformer(executor, transformer, this)

}