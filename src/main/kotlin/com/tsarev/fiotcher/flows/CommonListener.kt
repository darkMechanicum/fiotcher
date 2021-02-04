package com.tsarev.fiotcher.flows

import java.util.*
import java.util.concurrent.Flow

/**
 * Common resource listener.
 */
abstract class CommonListener<ResourceT: Any>
    : SubscriberAdapter<ResourceT>(), Flow.Subscriber<ResourceT> {

    /**
     * Call super and guaranteed method.
     */
    override fun onNext(item: ResourceT) {
        super.onNext(item)
        doOnNext(item)
    }

    /**
     * Actual onNext handling.
     */
    abstract fun doOnNext(item: ResourceT)
}