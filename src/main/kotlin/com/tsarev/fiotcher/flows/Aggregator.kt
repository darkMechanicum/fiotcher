package com.tsarev.fiotcher.flows

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
    executorService: ExecutorService = ForkJoinPool.commonPool(),
    capacity: Int = Int.MAX_VALUE
) : SubscriberAdapter<ResourceT>(), Flow.Processor<ResourceT, ResourceT> {

    /**
     * Aggregator publisher.
     */
    private val destination = SubmissionPublisher<ResourceT>(executorService, capacity)

    override fun subscribe(subscriber: Flow.Subscriber<in ResourceT>?) {
        if (subscriber == null) throw NullPointerException()
        subscriber.let { destination.subscribe(it) }
    }

    override fun onNext(item: ResourceT) {
        super.onNext(item)
        destination.submit(item)
    }

    override fun stop(force: Boolean): Future<*> {
        destination.close()
        return super.stop(force)
    }
}