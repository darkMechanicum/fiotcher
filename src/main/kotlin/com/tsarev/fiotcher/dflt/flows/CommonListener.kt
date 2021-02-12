package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.internal.EventWithException
import java.util.concurrent.Flow

/**
 * Common resource listener.
 */
class CommonListener<ResourceT : Any>(
    private val onNextHandler: (ResourceT) -> Unit,
    private val onSubscribeHandler: (Flow.Subscription) -> Unit = {},
    private val onErrorHandler: (Throwable) -> Unit = { throw it },
    private val onCompleteHandler: () -> Unit = { },
) : SingleSubscriptionSubscriber<ResourceT>() {

    /**
     * Actual onNext handling.
     */
    override fun doOnNext(item: EventWithException<ResourceT>) {
        handleErrors<Unit, ResourceT>(
            handleErrors = { onErrorHandler(it); null },
            item = item,
            send = {}
        ) {
            onNextHandler(it)
        }
    }

    override fun doOnSubscribe(subscription: Flow.Subscription) {
        onSubscribeHandler(subscription)
    }

    override fun doOnError(throwable: Throwable) {
        onErrorHandler(throwable)
    }

    override fun doOnComplete() {
        onCompleteHandler()
    }
}