package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.push
import java.util.concurrent.*

/**
 * Resource transformer, that delegates to passed function how to split and publish.
 */
class DelegatingTransformer<FromT : Any, ToT : Any, ListenerT>(
    executor: Executor = ForkJoinPool.commonPool(),
    maxCapacity: Int = Flow.defaultBufferSize(),
    private val chained: ListenerT,
    private val stoppingExecutor: Executor,
    private val onSubscribeHandler: (Flow.Subscription) -> Unit = {},
    private val transform: (FromT, (ToT) -> Unit) -> Unit,
) : SingleSubscriptionSubscriber<FromT>(), Flow.Processor<FromT, ToT>
        where ListenerT : ChainingListener<ToT>, ListenerT : Flow.Subscriber<ToT> {

    private val destination = SubmissionPublisher<ToT>(executor, maxCapacity)

    init {
        destination.subscribe(chained)
    }

    override fun subscribe(subscriber: Flow.Subscriber<in ToT>?) {
        if (!isStopped) destination.subscribe(subscriber)
    }

    override fun doOnNext(item: FromT) {
        transform(item) {
            destination.submit(it)
        }
        subscription?.request(1)
    }

    override fun doOnError(throwable: Throwable) {
        chained.onError(throwable)
    }

    private val additionalBrake = Brake<Unit>()

    override fun stop(force: Boolean) = additionalBrake.push {
        super.stop(force)
            .runAfterBoth(loopForEventsCompletion(force)) {
                destination.close()
                chained.stop(force)
                it.complete(Unit)
            }
    }

    override fun doOnSubscribe(subscription: Flow.Subscription) {
        onSubscribeHandler(subscription)
    }

    /**
     * Spin loop that all events are processed.
     */
    // TODO What can we do with this spin loop?
    private fun loopForEventsCompletion(force: Boolean): CompletableFuture<*> =
        if (force)
            CompletableFuture.completedFuture(Unit)
        else
            CompletableFuture.runAsync(
                { while (destination.estimateMaximumLag() != 0) Thread.sleep(10) }, stoppingExecutor
            )
}