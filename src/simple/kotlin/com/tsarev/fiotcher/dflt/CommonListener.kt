package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.Flow

/**
 * Last stop listener implementation of [Flow.Subscriber].
 */
open class CommonListener<ResourceT : Any>(
    private val key: String,
    private val handleErrors: ((Throwable) -> Throwable?)? = { null },
    private val listener: (ResourceT) -> Unit,
) : Flow.Subscriber<Pair<String, EventWithException<ResourceT>>>, Stoppable {

    private val stopBrake = Brake()

    @Volatile
    private var subscription: Flow.Subscription? = null

    override fun onSubscribe(subscription: Flow.Subscription?) {
        if (stopBrake.isPushed) return
        subscription ?: throw IllegalArgumentException("subscription must not be null")
        this.subscription = subscription
        subscription.request(Long.MAX_VALUE)
    }

    override fun onNext(itemWithKey: Pair<String, EventWithException<ResourceT>>) {
        if (stopBrake.isForced) return
        if (itemWithKey.first != key) return
        val item = itemWithKey.second
        handleErrors<Unit, ResourceT>(item = item, handleErrors = handleErrors) {
            listener(it)
        }
    }

    override fun onError(throwable: Throwable) {
        stop(true)
    }

    override fun onComplete() {
        stop(true)
    }

    override fun stop(force: Boolean) = stopBrake.push(force) {
        subscription?.cancel()
        subscription = null
    }

}