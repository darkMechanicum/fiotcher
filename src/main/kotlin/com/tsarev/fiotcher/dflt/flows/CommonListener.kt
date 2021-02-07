package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.Flow

/**
 * Common resource listener.
 */
open class CommonListener<ResourceT: Any>(
    private val onNext: ((ResourceT) -> Unit)? = null
) : SingleSubscriptionSubscriber<ResourceT>(), Flow.Subscriber<ResourceT>, ChainingListener<ResourceT> {

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
    open fun doOnNext(item: ResourceT) {
        if (onNext != null) onNext(item)
    }
}