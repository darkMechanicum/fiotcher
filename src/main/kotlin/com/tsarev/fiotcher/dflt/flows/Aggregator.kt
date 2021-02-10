package com.tsarev.fiotcher.dflt.flows

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.Brake
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
    executor: Executor = ForkJoinPool.commonPool(),
    maxCapacity: Int = Flow.defaultBufferSize(),
    private val onSubscribeHandler: (Flow.Subscription) -> Unit = { }
) : Flow.Processor<ResourceT, ResourceT>, Stoppable {

    /**
     * Aggregator publisher.
     */
    private val destination = SubmissionPublisher<ResourceT>(executor, maxCapacity)

    /**
     * Registered subscriptions.
     */
    private val subscriptions = ArrayList<Flow.Subscription>()

    /**
     * Listening brake flag.
     */
    private val brake: Brake<Unit> = Brake()

    override val isStopped: Boolean get() = brake.get() != null

    override fun subscribe(subscriber: Flow.Subscriber<in ResourceT>?) {
        if (subscriber == null) throw NullPointerException()
        destination.subscribe(subscriber)
    }

    override fun onNext(item: ResourceT) {
        if (!isStopped) destination.submit(item)
    }

    override fun stop(force: Boolean): CompletableFuture<*> {
        brake.get()?.let { return it }
        val copy: List<Flow.Subscription>
        synchronized(this) {
            brake.compareAndExchange(null, CompletableFuture<Unit>())?.let { return it }
            copy = subscriptions.toList()
            subscriptions.clear()
        }
        copy.forEach { it.cancel() }
        return brake.get()!!
    }

    override fun onSubscribe(subscription: Flow.Subscription) {
        if (!destination.isClosed) {
            // What can we do with this in the current Flow API?
            synchronized(this) {
                brake.set(null)
                subscriptions += subscription
            }
            subscription.request(Long.MAX_VALUE)
            onSubscribeHandler(subscription)
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