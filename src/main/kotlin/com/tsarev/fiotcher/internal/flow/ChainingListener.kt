package com.tsarev.fiotcher.internal.flow

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.Executor

/**
 * Marker interface, that can chain event processing within [WayStation] context.
 */
interface ChainingListener<ResourceT : Any> : Stoppable {

    /**
     * Create proxy listener, that will handle split events in new queue.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any> WayStation.doAsyncDelegateFrom(
        executor: Executor,
        stoppingExecutor: Executor,
        maxCapacity: Int,
        transformer: (FromT, (ResourceT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?,
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in same queue and thread.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any> WayStation.doSyncDelegateFrom(
        transformer: (FromT, (ResourceT) -> Unit) -> Unit,
        handleErrors: ((Throwable) -> Throwable?)?,
    ): ChainingListener<FromT>

}