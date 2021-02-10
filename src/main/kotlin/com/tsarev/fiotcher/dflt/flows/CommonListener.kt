package com.tsarev.fiotcher.dflt.flows

import java.util.concurrent.Flow

/**
 * Common resource listener.
 */
class CommonListener<ResourceT : Any>(
    private val onNextHandler: (ResourceT) -> Unit,
    private val onSubscribeHandler: (Flow.Subscription) -> Unit = {},
    private val onErrorHandler: (Throwable) -> Unit = {}
) : SingleSubscriptionSubscriber<ResourceT>() {

    /**
     * Actual onNext handling.
     */
    override fun doOnNext(item: ResourceT) {
        onNextHandler(item)
    }

    override fun doOnSubscribe(subscription: Flow.Subscription) {
        onSubscribeHandler(subscription)
    }

    override fun onError(throwable: Throwable) {
        onErrorHandler(throwable)
    }
}