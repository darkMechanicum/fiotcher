package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.*

/**
 * Resource transformer, that delegates to passed function how to split and publish.
 */
class DelegatingTransformer<FromT : Any, ToT : Any>(
    executor: Executor = ForkJoinPool.commonPool(),
    maxCapacity: Int = Flow.defaultBufferSize(),
    private val transform: (FromT, (ToT) -> Unit) -> Unit,
    private val chained: ChainingListener<ToT>
) : SingleSubscriptionSubscriber<FromT>(), Flow.Processor<FromT, ToT> {

    private val destination = SubmissionPublisher<ToT>(executor, maxCapacity)

    init {
        destination.subscribe(chained)
    }

    override fun subscribe(subscriber: Flow.Subscriber<in ToT>?) {
        destination.subscribe(subscriber)
    }

    override fun doOnNext(item: FromT) {
        var pushed = false
        transform(item) {
            pushed = true
            destination.submit(it)
        }
        if (!pushed) {
            askNext()
        }
    }

    override fun stop(force: Boolean): CompletableFuture<*> {
        return super.stop(force)
            .runAfterBoth(loopForEventsCompletion(force)) {
                destination.close()
                chained.stop(force)
            }
    }

    /**
     * Spin loop that all events are processed.
     */
    private fun loopForEventsCompletion(force: Boolean): CompletableFuture<*> =
        if (force)
            CompletableFuture.completedFuture(Unit)
        else
            CompletableFuture.runAsync {
                while (destination.estimateMaximumLag() != 0) Thread.sleep(10);
            }
}