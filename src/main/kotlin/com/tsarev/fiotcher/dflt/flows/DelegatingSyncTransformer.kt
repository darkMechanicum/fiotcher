package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.FiotcherException
import com.tsarev.fiotcher.internal.EventWithException
import java.util.concurrent.Flow

class DelegatingSyncTransformer<ToT : Any, FromT : Any>(
    private val delegate: SingleSubscriptionSubscriber<ToT>,
    private val transform: (FromT, (ToT) -> Unit) -> Unit,
    private val handleErrors: ((Throwable) -> Throwable?)?,
) : SingleSubscriptionSubscriber<FromT>() {

    override fun onSubscribe(subscription: Flow.Subscription) = delegate.onSubscribe(subscription)

    override val isStopped: Boolean get() = delegate.isStopped

    override fun stop(force: Boolean) = delegate.stop(force)

    override fun doOnNext(item: EventWithException<FromT>) {
        handleErrors<ToT, FromT>(
            handleErrors, item, { delegate.doOnNext(it) }
        ) { event ->
            try {
                transform(event) {
                    try {
                        delegate.onNext(EventWithException(it, null))
                    } catch (cause: Throwable) {
                        // Wrap inner submit error.
                        throw DelegatingAsyncTransformer.WrapperException(cause)
                    }
                }
            } catch (wrapped: DelegatingAsyncTransformer.WrapperException) {
                val exception = FiotcherException(wrapped.cause!!)
                exception.addSuppressed(wrapped)
                throw exception
            }
        }
    }

    override fun doOnError(throwable: Throwable) = delegate.onError(throwable)
    override fun doOnComplete() = stop(false).let {}
}