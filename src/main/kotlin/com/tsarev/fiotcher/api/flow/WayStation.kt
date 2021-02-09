package com.tsarev.fiotcher.api.flow

/**
 * This is an intermediate point, where we may split, group and make asynchronous (or may not)
 * processing of resources.
 */
interface WayStation {

    /**
     * Create common listener.
     *
     * @param listener actual listening logic
     */
    fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit): ChainingListener<ResourceT>

    /**
     * Chain from listener, that will transform its events to this listener ones synchronously.
     *
     * @param transformer event transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(
        transformer: (FromT) -> ToT?
    ): ChainingListener<FromT>

    /**
     * Chain from listener, that will transform its events to this listener grouped ones synchronously.
     *
     * @param transformer event to collection transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(
        transformer: (FromT) -> Collection<ToT?>?
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in same queue and thread.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncDelegateFrom(
        transformer: (FromT, (ToT) -> Unit) -> Unit
    ): ChainingListener<FromT>

    /**
     * Chain from listener, that will transform its events to this listener ones
     * asynchronously in the separate event queue.
     *
     * @param transformer event transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(
        transformer: (FromT) -> ToT?
    ): ChainingListener<FromT>

    /**
     * Chain from listener, that will transform its events to this listener grouped ones
     * asynchronously in the separate event queue.
     *
     * @param transformer event to collection transforming logic
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