package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.ListenerIsStopped
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.pushCompleted
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Common methods for [Flow.Subscriber] with only one [Flow.Subscription].
 */
abstract class SingleSubscriptionSubscriber<ResourceT : Any> :
    Flow.Subscriber<ResourceT>, ChainingListener<ResourceT>, Stoppable {

    /**
     * Registered subscription.
     */
    protected val subscription: Flow.Subscription?
        get() = _subscription

    /**
     * Guard to restrict inaccurate mutating.
     */
    @Volatile
    private var _subscription: Flow.Subscription? = null

    /**
     * Stopped flag.
     */
    private val brake = Brake<Unit>()

    override val isStopped get() = Thread.currentThread().isInterrupted || brake.get() != null

    /**
     * Stop by cancelling subscription.
     */
    override fun stop(force: Boolean) = brake.pushCompleted(Unit) {
        _subscription?.cancel()
        _subscription = null
    }

    /**
     * Request maximum of entries and store subscription.
     */
    override fun onSubscribe(subscription: Flow.Subscription) {
        if (isStopped) throw ListenerIsStopped("Cannot subscribe when stopped.")
        if (_subscription != null) throw ListenerIsStopped("Cannot subscribe to multiple publishers.")
        _subscription = subscription
        subscription.request(1) // Ask for single element.
        doOnSubscribe(subscription)
    }

    /**
     * Additional on subscribe logic.
     */
    open fun doOnSubscribe(subscription: Flow.Subscription) {

    }

    override fun onNext(item: ResourceT) {
        if (!isStopped) {
            _subscription?.request(1) // Ask for another element.
            doOnNext(item)
        }
    }

    /**
     * Actual onNext handling.
     */
    abstract fun doOnNext(item: ResourceT)

    override fun onError(throwable: Throwable) {
        throwable.printStackTrace()
        doOnError(throwable)
    }

    /**
     * Additional on error logic.
     */
    open fun doOnError(throwable: Throwable) {

    }

    override fun onComplete() {
        // no-op
    }

    override fun <FromT : Any> doSyncDelegateFrom(
        transformer: (FromT, (ResourceT) -> Unit) -> Unit
    ) = SyncDelegatedAdapter(this, transformer)

    override fun <FromT : Any> doAsyncDelegateFrom(
        executor: Executor,
        stoppingExecutor: Executor,
        maxCapacity: Int,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit
    ): ChainingListener<FromT> = DelegatingTransformer(
        executor = executor,
        chained = this,
        maxCapacity = maxCapacity,
        stoppingExecutor = stoppingExecutor,
        transform = transformer
    )

    class SyncDelegatedAdapter<ToT: Any, FromT : Any>(
        private val delegate: SingleSubscriptionSubscriber<ToT>,
        private val transformer: (FromT, (ToT) -> Unit) -> Unit
    ) : SingleSubscriptionSubscriber<FromT>() {
        override fun onError(throwable: Throwable) = delegate.onError(throwable)
        override fun doOnNext(item: FromT) = transformer(item) { delegate.doOnNext(it) }
    }
}