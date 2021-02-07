package com.tsarev.fiotcher.api.flow

import java.util.concurrent.Flow

/**
 * This is an intermediate point, where we may split, group and make asynchronous (or may not)
 * processing of resources.
 */
interface WayStation {

    /**
     * Create common listener.
     */
    fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit): ChainingListener<ResourceT>

    /**
     * Create proxy listener, that will handle pre converted events in new queue.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(
        transformer: (FromT) -> ToT?
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle pre converted events in new queue.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(
        transformer: (FromT) -> Collection<ToT?>?
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle pre converted events in new queue.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(
        transformer: (FromT) -> ToT?
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in new queue.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(
        transformer: (FromT) -> Collection<ToT?>?
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in new queue.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(
        transformer: (FromT, (ToT) -> Unit) -> Unit
    ): ChainingListener<FromT>

}