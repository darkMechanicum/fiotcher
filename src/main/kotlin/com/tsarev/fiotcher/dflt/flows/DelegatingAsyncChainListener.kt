package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.FiotcherException
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.*
import com.tsarev.fiotcher.internal.ChainingListener
import com.tsarev.fiotcher.internal.EventWithException
import java.util.concurrent.*

/**
 * Async transformer implementation of [Flow.Processor] to handle possible
 * blocking transform operations (Like blocking read from file).
 */
class DelegatingAsyncChainListener<FromT : Any, ToT : Any, ListenerT>(
    executor: Executor,
    maxCapacity: Int,
    private val chained: ListenerT,
    private val transform: (FromT, (ToT) -> Unit) -> Unit,
    private val handleErrors: ((Throwable) -> Throwable?)?,
) : SubmissionPublisher<EventWithException<ToT>>(executor, maxCapacity),
    Flow.Processor<EventWithException<FromT>, EventWithException<ToT>>,
    Stoppable,
    StoppableBrakeMixin<Unit>,
    ChainingListener<FromT>
        where ListenerT : ChainingListener<ToT>,
              ListenerT : Flow.Subscriber<EventWithException<ToT>>,
              ListenerT : StoppableBrakeMixin<Unit> {

    override val stopBrake = Brake<Unit>()

    @Volatile
    private var subscription: Flow.Subscription? = null

    private val onNextBarrier = Phaser()

    init {
        // Actual chaining.
        subscribe(chained)
    }

    override fun onSubscribe(subscription: Flow.Subscription?) {
        if (stopBrake.isPushed) return
        subscription ?: throw IllegalArgumentException("subscription must not be null")
        this.subscription = subscription
        subscription.request(Long.MAX_VALUE)
    }

    /**
     * Special wrapper execution.
     */
    class WrapperException(cause: Throwable) : FiotcherException(cause) {
        override val cause: Throwable get() = super.cause!!
    }

    override fun onNext(item: EventWithException<FromT>) {
        if (stopBrake.isPushed) return
        try {
            onNextBarrier.register()
            handleErrors<ToT, FromT>(
                handleErrors = handleErrors,
                item = item,
                send = { submit(it) }
            ) { event ->
                try {
                    transform(event) {
                        try {
                            submit(EventWithException(it, null))
                        } catch (cause: Throwable) {
                            // Wrap inner submit error.
                            throw WrapperException(cause)
                        }
                    }
                } catch (wrapped: WrapperException) {
                    val exception = FiotcherException(wrapped.cause)
                    exception.addSuppressed(wrapped)
                    throw exception
                }
            }
        } finally {
            onNextBarrier.arriveAndDeregister()
        }
    }

    override fun onError(throwable: Throwable?) {
        doStop(force = true, exception = throwable)
    }

    override fun onComplete() {
        // no-op
    }

    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push {
        waitForOnNextCompletion(force)
            .thenCompose { loopForEventsCompletion(force) }
            .thenRun {
                this@DelegatingAsyncChainListener.subscription?.cancel()
                if (exception != null) {
                    // Can't rely solely on [onComplete] method of chained, since we need to pass exception and force flag.
                    chained.doStop(force, exception).whenComplete { _, _ ->
                        this@DelegatingAsyncChainListener.close()
                        completeExceptionally(exception)
                    }
                } else {
                    // Can't rely solely on [onComplete] method of chained, since we need to pass force flag.
                    chained.doStop(force).whenComplete { _, _ ->
                        this@DelegatingAsyncChainListener.close()
                        complete(Unit)
                    }
                }
            }
    }

    /**
     * Spin loop, checking all events are processed.
     */
    // Can't remove within SubmissionPublisher implementation, since it does not support waiting.
    // We need to know when we are stopped, so chained listener cannot be stopped wia [onComplete] method,
    // since [Flow.Subscriptions] do not offer any information of remaining elements.
    //
    // Workaround can be implemented with counting elements down to the chain, that are passed but not processed,
    // but such implementation will introduce additional cohesion between listeners.
    private fun loopForEventsCompletion(force: Boolean): CompletableFuture<Any> =
        if (force) CompletableFuture.completedFuture(Any())
        else runAsync(executor) {
            try {
                while (subscribers.size > 0 && estimateMaximumLag() != 0) Thread.sleep(100)
            } catch (ignored: InterruptedException) {
                // Somebody had interrupted this executor task (most likely stopped whole executor),
                // so no other events will be processed. Exit safely.
            }
        }

    /**
     * Wait until [onNext] logic is completed.
     */
    private fun waitForOnNextCompletion(force: Boolean): CompletableFuture<Any> =
        if (force) CompletableFuture.completedFuture(Any())
        else runAsync(executor) {
            // Just wait for on next barrier.
            try {
                // Register self to handle situation when no other is registered.
                onNextBarrier.register()
                onNextBarrier.arriveAndAwaitAdvance()
                onNextBarrier.arriveAndDeregister()
            } catch (ignored: InterruptedException) {
                // Somebody had interrupted this executor task (most likely stopped whole executor),
                // so no other events will be processed. Exit safely.
            }
        }

    override fun <NewT : Any> asyncDelegateFrom(
        executor: Executor,
        maxCapacity: Int,
        transformer: (NewT, (FromT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?
    ) = DelegatingAsyncChainListener(executor, maxCapacity, this, transformer, handleErrors)
}