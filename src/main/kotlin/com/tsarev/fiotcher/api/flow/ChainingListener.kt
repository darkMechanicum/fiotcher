package com.tsarev.fiotcher.api.flow

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.Executor
import java.util.concurrent.Flow

/**
 * Marker interface, that can chain event processing within [WayStation] context.
 */
interface ChainingListener<ResourceT : Any> : Stoppable {

    /**
     * Event handler for this listener.
     */
    fun onNext(item: ResourceT)

    /**
     * Error handler for this listener.
     */
    fun onError(throwable: Throwable)

    /**
     * Create proxy listener, that will handle split events in new queue.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any> doAsyncDelegateFrom(
        executor: Executor,
        stoppingExecutor: Executor,
        maxCapacity: Int,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in same queue and thread.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any> doSyncDelegateFrom(
        transformer: (FromT, (ResourceT) -> Unit) -> Unit
    ): ChainingListener<FromT>

}