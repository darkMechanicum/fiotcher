package com.tsarev.fiotcher.internal.flow

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.internal.EventWithException
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Marker interface, that can chain event processing within [WayStation] context.
 */
interface ChainingListener<ResourceT : Any> : Flow.Subscriber<EventWithException<ResourceT>>, Stoppable {

    /**
     * Create proxy listener, that will handle split events in new queue.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any> asyncDelegateFrom(
        executor: Executor,
        maxCapacity: Int,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?,
    ): ChainingListener<FromT>

}