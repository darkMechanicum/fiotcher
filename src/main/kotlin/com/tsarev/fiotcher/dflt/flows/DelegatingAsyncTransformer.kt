package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.FiotcherException
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.push
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.flow.ChainingListener
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Resource transformer, that delegates to passed function how to split and publish.
 */
class DelegatingAsyncTransformer<FromT : Any, ToT : Any, ListenerT>(
    executor: Executor = ForkJoinPool.commonPool(),
    maxCapacity: Int = Flow.defaultBufferSize(),
    private val chained: ListenerT,
    private val stoppingExecutor: Executor,
    private val onSubscribeHandler: (Flow.Subscription) -> Unit = {},
    private val transform: (FromT, (ToT) -> Unit) -> Unit,
    private val handleErrors: ((Throwable) -> Throwable?)?,
) : SingleSubscriptionSubscriber<FromT>(),
    Flow.Processor<EventWithException<FromT>, EventWithException<ToT>>
        where ListenerT : ChainingListener<ToT>,
              ListenerT : Flow.Subscriber<EventWithException<ToT>> {

    private val destination = SubmissionPublisher<EventWithException<ToT>>(executor, maxCapacity)

    /**
     * Number of unprocessed entries by this transformer.
     */
    private val unprocessed = AtomicLong(0)

    init {
        destination.subscribe(chained)
    }

    override fun subscribe(subscriber: Flow.Subscriber<in EventWithException<ToT>>?) {
        if (!isStopped) destination.subscribe(subscriber)
    }

    /**
     * Special wrapper execution.
     */
    class WrapperException(cause: Throwable) : FiotcherException(cause)

    override fun doOnNext(item: EventWithException<FromT>) {
        unprocessed.incrementAndGet()
        try {
            handleErrors<ToT, FromT>(
                handleErrors, item, { destination.submit(it) }
            ) { event ->
                try {
                    transform(event) {
                        try {
                            destination.submit(EventWithException(it, null))
                        } catch (cause: Throwable) {
                            // Wrap inner submit error.
                            throw WrapperException(cause)
                        }
                    }
                } catch (wrapped: WrapperException) {
                    val exception = FiotcherException(wrapped.cause!!)
                    exception.addSuppressed(wrapped)
                    throw exception
                }
            }
        } finally {
            subscription?.request(1)
            unprocessed.decrementAndGet()
        }
    }

    override fun doOnError(throwable: Throwable) {
        // Stop all chain from non recoverable error.
        chained.stop(true)
        chained.onError(throwable)
    }

    private val additionalBrake = Brake<Unit>()

    override fun stop(force: Boolean) = additionalBrake.push { brk ->
        super.stop(force)
            .runAfterBoth(loopForEventsCompletion(force)) {
                destination.close()
                chained.stop(force)
                brk.complete(Unit)
            }
    }

    override fun doOnSubscribe(subscription: Flow.Subscription) {
        onSubscribeHandler(subscription)
    }


    /**
     * Spin loop that all events are processed.
     */
    // TODO What can we do with this spin loop?
    // TODO Can't remove it simply within current Publish/Subscribe implementation, see TODO.md.
    private fun loopForEventsCompletion(force: Boolean): CompletableFuture<*> =
        if (force)
            CompletableFuture.completedFuture(Unit)
        else
            CompletableFuture.runAsync(
                {
                    while (unprocessed.get() != 0L) Thread.sleep(100)
                    while (destination.estimateMaximumLag() != 0) Thread.sleep(100)
                }, stoppingExecutor
            )

    override fun doOnComplete() {
        stop(false)
    }
}