package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.FiotcherException
import com.tsarev.fiotcher.api.ListenerIsStopped
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.pushCompleted
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asFailure
import com.tsarev.fiotcher.internal.flow.ChainingListener
import com.tsarev.fiotcher.internal.flow.WayStation
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Common methods for [Flow.Subscriber] with only one [Flow.Subscription].
 */
abstract class SingleSubscriptionSubscriber<ResourceT : Any> :
    Flow.Subscriber<EventWithException<ResourceT>>,
    ChainingListener<ResourceT>,
    Stoppable {

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

    override fun onNext(item: EventWithException<ResourceT>) {
        if (!isStopped) {
            _subscription?.request(1) // Ask for another element.
            doOnNext(item)
        }
    }

    /**
     * Actual onNext handling.
     */
    abstract fun doOnNext(item: EventWithException<ResourceT>)

    override fun onError(throwable: Throwable) {
        throwable.printStackTrace()
        doOnError(throwable)
    }

    /**
     * Try to handle exceptions in the provided block,
     * wrapping them at need and sending further.
     */
    protected fun <T : Any, S : Any> handleErrors(
        handleErrors: ((Throwable) -> Throwable?)?,
        item: EventWithException<S>,
        send: (EventWithException<T>) -> Unit,
        block: (S) -> Unit
    ) {
        item.ifSuccess { event ->
            try {
                block(event)
            } catch (fiotcherCommon: FiotcherException) {
                throw FiotcherException("Unexpected exception while transform processing", fiotcherCommon)
            } catch (common: Throwable) {
                tryTransform(handleErrors, common, send)
            }
        }
        item.ifFailed { exception ->
            tryTransform(handleErrors, exception, send)
        }
    }

    /**
     * Try to transform caught exception and resend it, if handler is set.
     */
    protected fun <T : Any> tryTransform(
        handleErrors: ((Throwable) -> Throwable?)?,
        common: Throwable,
        send: (EventWithException<T>) -> Unit
    ) {
        if (handleErrors != null) {
            try {
                // Handle or rethrow.
                val transformedException = handleErrors.invoke(common)
                if (transformedException != null) {
                    send(transformedException.asFailure())
                }
            } catch (innerCause: Throwable) {
                throw FiotcherException("Exception while transforming another: [$common]", innerCause)
            }
        } else throw common
    }

    /**
     * Additional on error logic.
     */
    open fun doOnError(throwable: Throwable) {

    }

    override fun onComplete() {
        // no-op
    }

    override fun <FromT : Any> WayStation.doSyncDelegateFrom(
        transformer: (FromT, (ResourceT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?,
    ) = DelegatingSyncTransformer(this@SingleSubscriptionSubscriber, transformer, handleErrors)

    override fun <FromT : Any> WayStation.doAsyncDelegateFrom(
        executor: Executor,
        stoppingExecutor: Executor,
        maxCapacity: Int,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?,
    ): ChainingListener<FromT> = DelegatingAsyncTransformer(
        executor = executor,
        chained = this@SingleSubscriptionSubscriber,
        maxCapacity = maxCapacity,
        stoppingExecutor = stoppingExecutor,
        transform = transformer,
        handleErrors = handleErrors
    )

}