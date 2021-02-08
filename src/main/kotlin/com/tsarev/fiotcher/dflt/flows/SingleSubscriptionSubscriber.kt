package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow

/**
 * Common methods for [Flow.Subscriber] with only one [Flow.Subscription].
 */
abstract class SingleSubscriptionSubscriber<ResourceT : Any> : ChainingListener<ResourceT>, Stoppable {

    /**
     * Registered subscription.
     */
    protected val subscription: Flow.Subscription?
        get() = _subscription

    /**
     * Guard to restrict inaccurate mutating.
     */
    @Volatile
    private var _subscription: Flow.Subscription? = null

    /**
     * Stopped flag.
     */
    @Volatile
    private var brake: CompletableFuture<*>? = null

    override val isStopped get() = Thread.currentThread().isInterrupted || brake != null

    /**
     * Stop by cancelling subscription.
     */
    override fun stop(force: Boolean): CompletableFuture<*> {
        return brake ?: synchronized(this) { // Null check.
            brake?.let { return@stop it } // Null check.
            _subscription?.cancel()
            _subscription = null
            CompletableFuture.completedFuture(Unit).also { brake = it }
        }
    }

    /**
     * Request maximum of entries and store subscription.
     */
    override fun onSubscribe(subscription: Flow.Subscription) {
        if (isStopped) throw IllegalStateException("Cannot subscribe when stopped.")
        if (_subscription != null) throw IllegalStateException("Cannot subscribe to multiple publishers.")
        _subscription = subscription
        subscription.request(1) // Ask for single element.
    }

    override fun onNext(item: ResourceT) {
        if (!isStopped) {
            _subscription?.request(1) // Ask for another element.
            doOnNext(item)
        }
    }

    override fun askNext() {
        if (!isStopped) {
            _subscription?.request(1)
        }
    }

    /**
     * Actual onNext handling.
     */
    abstract fun doOnNext(item: ResourceT)

    override fun onError(throwable: Throwable?) {
        throwable?.printStackTrace()
    }

    override fun onComplete() {
        // no-op
    }
}