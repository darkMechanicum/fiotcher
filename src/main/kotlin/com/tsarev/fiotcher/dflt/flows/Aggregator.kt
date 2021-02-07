package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.util.Stoppable
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
class Aggregator<ResourceT : Any>(
    executorService: ExecutorService = ForkJoinPool.commonPool(),
    maxCapacity: Int = Flow.defaultBufferSize(),
    initialListening: Boolean = false
) : Flow.Processor<ResourceT, ResourceT>, Stoppable {

    /**
     * Aggregator publisher.
     */
    private val destination = SubmissionPublisher<ResourceT>(executorService, maxCapacity)

    /**
     * Registered subscriptions.
     */
    private val subscriptions = ArrayList<Flow.Subscription>()

    /**
     * Listening flag.
     */
    @Volatile
    private var isListening = initialListening

    override fun subscribe(subscriber: Flow.Subscriber<in ResourceT>?) {
        if (subscriber == null) throw NullPointerException()
        destination.subscribe(subscriber)
    }

    override fun onNext(item: ResourceT) {
        if (isListening) destination.submit(item)
    }

    override fun stop(force: Boolean): CompletableFuture<*> {
        val copy: ArrayList<Flow.Subscription>
        synchronized(this) {
            copy = ArrayList(subscriptions)
            isListening = false
            subscriptions.clear()
        }
        return if (force) {
            CompletableFuture.runAsync { copy.forEach { it.cancel() } }
        } else {
            copy.forEach { it.cancel() }
            CompletableFuture.completedFuture(Unit)
        }
    }

    override fun onSubscribe(subscription: Flow.Subscription) {
        if (!destination.isClosed) {
            // What can we do with this in the current Flow API?
            synchronized(this) {
                isListening = true
                subscriptions += subscription
            }
            subscription.request(Long.MAX_VALUE)
        } else {
            throw IllegalStateException("Aggregator is closed.")
        }
    }

    override fun onError(throwable: Throwable?) {
        throwable?.printStackTrace()
        stop(true)
    }

    override fun onComplete() {
        // no-op
    }
}