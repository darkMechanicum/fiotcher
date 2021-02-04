package com.tsarev.fiotcher.flows

import com.tsarev.fiotcher.common.Stoppable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.Future

/**
 * Common methods for [Flow.Subscriber] with only one [Flow.Subscription].
 */
abstract class SingleSubscriptionSubscriber<T> : Flow.Subscriber<T>, Stoppable {

    /**
     * Registered subscriptions.
     */
    protected val subscription: Flow.Subscription?
        get() = _subscription

    /**
     * Guard to restrict inaccurate mutating.
     */
    private var _subscription: Flow.Subscription? = null

    /**
     * Stop by cancelling subscription.
     */
    override fun stop(force: Boolean): Future<*> {
        return if (force) {
            CompletableFuture.runAsync {
                _subscription?.cancel()
                _subscription = null
            }
        } else {
            _subscription?.cancel()
            _subscription = null
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Request maximum of entries and store subscription.
     */
    override fun onSubscribe(subscription: Flow.Subscription) {
        if (_subscription != null) {
            throw RuntimeException("Cannot subscribe to multiple publishers!")
        }
        subscription.request(1) // Ask for single element.
        _subscription = subscription
    }

    override fun onNext(item: T) {
        _subscription?.request(1) // Ask for another element.
    }

    override fun onError(throwable: Throwable?) {
        throwable?.printStackTrace()
    }

    override fun onComplete() {
        // no-op
    }
}