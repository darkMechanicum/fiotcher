package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.*

/**
 * This is [Flow.Processor] that subscribes to lots of
 * [Flow.Publisher]s and redirect their messages to other
 * [Flow.Subscriber]s via [SubmissionPublisher].
 *
 * This is useful in case, when we need to separate threads,
 * responsible for these events and to separate processing
 * from creating for events.
 *
 * Note: When stopping this [Aggregator] it suspends its
 * work until any new subscription by it.
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
     * Listening brake flag.
     */
    @Volatile
    private var brake: CompletableFuture<*>? = if (initialListening) null else CompletableFuture.completedFuture(Unit)

    override val isStopped: Boolean get() = brake != null

    override fun subscribe(subscriber: Flow.Subscriber<in ResourceT>?) {
        if (subscriber == null) throw NullPointerException()
        destination.subscribe(subscriber)
    }

    override fun onNext(item: ResourceT) {
        if (!isStopped) destination.submit(item)
    }

    override fun stop(force: Boolean): CompletableFuture<*> {
        val brakeCopy = brake
        if (brakeCopy != null) return brakeCopy
        val copy: ArrayList<Flow.Subscription>
        synchronized(this) {
            val syncBrakeCopy = brake
            if (syncBrakeCopy != null) return syncBrakeCopy
            brake = CompletableFuture<Unit>()
            copy = ArrayList(subscriptions)
            subscriptions.clear()
        }
        copy.forEach { it.cancel() }
        return CompletableFuture.completedFuture(Unit)
    }

    override fun onSubscribe(subscription: Flow.Subscription) {
        if (!destination.isClosed) {
            // What can we do with this in the current Flow API?
            synchronized(this) {
                brake = null
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