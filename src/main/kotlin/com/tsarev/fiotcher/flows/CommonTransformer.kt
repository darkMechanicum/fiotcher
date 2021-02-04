package com.tsarev.fiotcher.flows

import java.util.concurrent.Flow
import java.util.concurrent.Future
import java.util.concurrent.SubmissionPublisher

/**
 * Common resource transformer.
 */
abstract class CommonTransformer<FromT: Any, ToT: Any>(
    private val destination: SubmissionPublisher<ToT>,
    private val transform: (FromT) -> ToT
) : CommonListener<FromT>(), Flow.Processor<FromT, ToT> {

    override fun onSubscribe(subscription: Flow.Subscription) {
        subscription.request(Long.MAX_VALUE) // Take all.
    }

    override fun subscribe(subscriber: Flow.Subscriber<in ToT>?) {
        destination.subscribe(subscriber)
    }

    override fun doOnNext(item: FromT) {
        super.onNext(item)
        val transformed = transform(item)
        destination.submit(transformed)
    }

    override fun stop(force: Boolean): Future<*> {
        destination.close()
        return super.stop(force)
    }
}