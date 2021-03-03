package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.*
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.flow.ChainingListener
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Last stop listener implementation of [Flow.Subscriber].
 */
open class CommonListener<ResourceT : Any>(
    private val handleErrors: ((Throwable) -> Throwable?)? = { null },
    private val listener: (ResourceT) -> Unit,
) : Flow.Subscriber<EventWithException<ResourceT>>,
    Stoppable,
    StoppableBrakeMixin<Unit>,
    ChainingListener<ResourceT> {

    override val stopBrake = Brake<Unit>()

    @Volatile
    private var subscription: Flow.Subscription? = null

    override fun onSubscribe(subscription: Flow.Subscription?) {
        subscription ?: throw IllegalArgumentException("subscription must not be null")
        this.subscription = subscription
        subscription.request(Long.MAX_VALUE)
    }

    override fun onNext(item: EventWithException<ResourceT>) {
        if (stopBrake.isPushed) return
        handleErrors<Unit, ResourceT>(item = item, handleErrors = handleErrors) {
            listener(it)
        }
    }

    override fun onError(throwable: Throwable) {
        doStop(exception = throwable)
    }

    override fun onComplete() {
        doStop()
    }

    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push(force) {
        subscription?.cancel()
        subscription = null
        if (exception != null) completeExceptionally(exception) else complete(Unit)
    }

    override fun <FromT : Any> asyncDelegateFrom(
        executor: Executor,
        maxCapacity: Int,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?
    ) = DelegatingAsyncChainListener(executor, maxCapacity, this, transformer, handleErrors)

}