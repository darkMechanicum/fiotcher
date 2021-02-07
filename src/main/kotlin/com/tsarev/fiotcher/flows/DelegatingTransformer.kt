package com.tsarev.fiotcher.flows

import java.util.concurrent.*

/**
 * Resource transformer, that delegates to passed function how to split and publish.
 */
class DelegatingTransformer<FromT: Any, ToT: Any>(
    executor: Executor,
    private val transform: (FromT, (ToT) -> Unit) -> Unit,
    private val chained: ChainingListener<ToT>
) : CommonListener<FromT>(), Flow.Processor<FromT, ToT> {

    private val destination = SubmissionPublisher<ToT>(executor, Flow.defaultBufferSize())

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

    override fun stop(force: Boolean): Future<*> {
        return if (force) {
            CompletableFuture.runAsync {
                subscription?.cancel()
                chained.stop(force).get()
                destination.close()
            }
        } else {
            subscription?.cancel()
            chained.stop(force).get()
            destination.close()
            CompletableFuture.completedFuture(Unit)
        }
    }
}