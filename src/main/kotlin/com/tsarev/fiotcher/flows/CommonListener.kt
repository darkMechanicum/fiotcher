package com.tsarev.fiotcher.flows

import java.util.concurrent.Flow

/**
 * Common resource listener.
 */
abstract class CommonListener<ResourceT: Any>
    : SingleSubscriptionSubscriber<ResourceT>(), Flow.Subscriber<ResourceT>, ChainingListener<ResourceT> {

    /**
     * Call super and guaranteed method.
     */
    override fun onNext(item: ResourceT) {
        super.onNext(item)
        doOnNext(item)
    }

    override fun askNext() {
        subscription?.request(1)
    }

    /**
     * Actual onNext handling.
     */
    abstract fun doOnNext(item: ResourceT)
}