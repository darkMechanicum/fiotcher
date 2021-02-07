package com.tsarev.fiotcher.flows

import com.tsarev.fiotcher.api.Stoppable
import java.lang.NullPointerException
import java.util.concurrent.*

/**
 * This is [Flow.Processor] that subscribes to lots of
 * [Flow.Publisher]s and redirect their messages to other
 * [Flow.Subscriber]s via [SubmissionPublisher].
 *
 * This is useful in case, when we need to separate threads,
 * responsible for these events and to separate processing
 * from creating for events.
 */
class Aggregator<ResourceT: Any>(
    executorService: ExecutorService = ForkJoinPool.commonPool()
) : Flow.Processor<ResourceT, ResourceT>, Stoppable {

    /**
     * Aggregator publisher.
     */
    private val destination = SubmissionPublisher<ResourceT>(executorService, Flow.defaultBufferSize())

    /**
     * Registered subscriptions.
     */
    private val subscriptions = ArrayList<Flow.Subscription>()

    override fun subscribe(subscriber: Flow.Subscriber<in ResourceT>?) {
        if (subscriber == null) throw NullPointerException()
        subscriber.let { destination.subscribe(it) }
    }

    override fun onNext(item: ResourceT) {
        destination.submit(item)
    }

    override fun stop(force: Boolean): Future<*> {
        return if (force) {
            CompletableFuture.runAsync {
                subscriptions.forEach { it.cancel() }
                destination.close()
            }
        } else {
            subscriptions.forEach { it.cancel() }
            destination.close()
            CompletableFuture.completedFuture(Unit)
        }
    }

    override fun onSubscribe(subscription: Flow.Subscription) {
        subscription.request(Long.MAX_VALUE)
        subscriptions += subscription
    }

    override fun onError(throwable: Throwable?) {
        throwable?.printStackTrace()
    }

    override fun onComplete() {
        // no-op
    }
}