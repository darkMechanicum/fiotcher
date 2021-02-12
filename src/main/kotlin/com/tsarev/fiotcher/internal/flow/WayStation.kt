package com.tsarev.fiotcher.internal.flow

import com.tsarev.fiotcher.api.KClassTypedKey

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
    fun <ResourceT : Any> createCommonListener(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        listener: (ResourceT) -> Unit,
    ): ChainingListener<ResourceT>

    /**
     * Aggregate events of new event type.
     *
     * @param key new event type
     */
    fun <ResourceT : Any> doAggregate(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        key: KClassTypedKey<ResourceT>,
    ): ChainingListener<ResourceT>

    /**
     * Chain from listener, that will transform its events to this listener ones synchronously.
     *
     * @param transformer event transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        transformer: (FromT) -> ToT?,
    ): ChainingListener<FromT>

    /**
     * Chain from listener, that will transform its events to this listener grouped ones synchronously.
     *
     * @param transformer event to collection transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        transformer: (FromT) -> Collection<ToT?>?,
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in same queue and thread.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncDelegateFrom(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        transformer: (FromT, (ToT) -> Unit) -> Unit,
    ): ChainingListener<FromT>

    /**
     * Chain from listener, that will transform its events to this listener ones
     * asynchronously in the separate event queue.
     *
     * @param transformer event transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        transformer: (FromT) -> ToT?,
    ): ChainingListener<FromT>

    /**
     * Chain from listener, that will transform its events to this listener grouped ones
     * asynchronously in the separate event queue.
     *
     * @param transformer event to collection transforming logic
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        transformer: (FromT) -> Collection<ToT?>?,
    ): ChainingListener<FromT>

    /**
     * Create proxy listener, that will handle split events in new queue.
     *
     * @transformer a function that accepts [FromT] event and function to publish it further,
     * thus allowing to make a number of publishing on its desire.
     */
    fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(
        handleErrors: ((Throwable) -> Throwable?)? = null,
        transformer: (FromT, (ToT) -> Unit) -> Unit,
    ): ChainingListener<FromT>

}