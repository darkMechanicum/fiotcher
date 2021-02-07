package com.tsarev.fiotcher.dflt.flows

/**
 * Common resource listener.
 */
class CommonListener<ResourceT: Any>(
    private val onNextHandler: (ResourceT) -> Unit
) : SingleSubscriptionSubscriber<ResourceT>() {

    /**
     * Actual onNext handling.
     */
    override fun doOnNext(item: ResourceT) {
        onNextHandler(item)
    }
}