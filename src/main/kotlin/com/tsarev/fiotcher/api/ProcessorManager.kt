package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.internal.Processor
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import kotlin.reflect.KClass

/**
 * This is high level entry point for using fiotcher library.
 */
interface ProcessorManager<InitialEventT : Any> {

    /**
     * Fiotcher inner API.
     */
    val processor: Processor<InitialEventT>

    /**
     * Stop tracking specified [resource] with specified type ([key]).
     *
     * This is non cancellable operation.
     *
     * Note: If tracker that is not fully initialized is stopped than no it
     * will not produce any event.
     *
     * @param resource resource to stop tracking
     * @param key type of this tracked path, to bind to handlers
     * @param force if set to `false` then attempt to process all discovered elements and then stop
     * @throws PoolIsStopped if underlying tracker pool is stopping
     * @return a future handle, that will complete when stopping completes.
     */
    fun stopTracking(resource: InitialEventT, key: String, force: Boolean = false): CompletionStage<*>

    /**
     * Listen directly for tracker produced events.
     *
     * @param key type of this tracked path, to bind to handlers
     * @throws ListenerAlreadyRegistered if there is already registered listener for this key
     */
    fun listenForInitial(key: String): ListenerBuilder<InitialEventsBunch<InitialEventT>>

    /**
     * Stop listening to tracker events by key.
     *
     * @param key type of this tracked path, to bind to handlers
     * @param force force if set to `false` then attempt to process all passed events and then stop
     * @return a asynchronous handle to stopping process
     */
    fun stopListeningInitial(key: String, force: Boolean = false): CompletionStage<*>

    /**
     * Marker interface to distinguish between initial event and user made custom one.
     */
    interface EventMarker

    /**
     * Listen for custom made event.
     *
     * @param key type of listened events, to bind to handlers
     * @throws ListenerAlreadyRegistered if there is already registered listener for this key and type
     * @param type event type to listen
     */
    fun <EventT : EventMarker> listenForKey(key: String, type: KClass<EventT>): ListenerBuilder<EventT>

    /**
     * Stop listening to custom made event.
     *
     * @param key type of listened events, to bind to handlers
     * @param force force if set to `false` then attempt to process all passed events and then stop
     * @return a asynchronous handle to stopping process
     */
    fun <EventT : EventMarker> stopListening(
        key: String, type: KClass<EventT>, force: Boolean = false
    ): CompletionStage<*>

    /**
     * Builder interface to ease listener registration process.
     */
    interface ListenerBuilder<EventT : Any> {

        /**
         * Aggregate events into new custom event type.
         *
         * @param key new event type
         * @param handleErrors error handling function to process errors or to pass them down the chain.
         *        If exception is thrown inside the handler, than listener chain will stop.
         */
        fun doAggregate(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            key: KClassTypedKey<EventT>,
        )

        /**
         * Chain from listener, that will transform its events to this listener ones synchronously.
         *
         * @param transformer event transforming logic
         * @param handleErrors error handling function to process errors or to pass them down the chain.
         *        If exception is thrown inside the handler, than listener chain will stop.
         * @param async if event should be processed in new queue asynchronously
         * @param executor executor to use for async processing. only make sense when [async] is true
         */
        fun <NextT : Any> chain(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            async: Boolean = false,
            executor: Executor? = null,
            transformer: (EventT) -> NextT?,
        ): ListenerBuilder<NextT>

        /**
         * Chain from listener, that will transform its events to this listener grouped ones synchronously.
         *
         * @param transformer event to collection transforming logic
         * @param handleErrors error handling function to process errors or to pass them down the chain.
         *        If exception is thrown inside the handler, than listener chain will stop.
         * @param async if event should be processed in new queue asynchronously
         * @param executor executor to use for async processing. only make sense when [async] is true
         */
        fun <NextT : Any> split(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            async: Boolean = false,
            executor: Executor? = null,
            transformer: (EventT) -> Collection<NextT?>?,
        ): ListenerBuilder<NextT>

        /**
         * Create proxy listener, that will handle split events in same queue and thread.
         *
         * @transformer a function that accepts [EventT] event and function to publish it further,
         * thus allowing to make a number of publishing on its desire.
         * @param handleErrors error handling function to process errors or to pass them down the chain.
         *        If exception is thrown inside the handler, than listener chain will stop.
         * @param async if event should be processed in new queue asynchronously
         * @param executor executor to use for async processing. only make sense when [async] is true
         */
        fun <NextT : Any> delegate(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            async: Boolean = false,
            executor: Executor? = null,
            transformer: (EventT, (NextT) -> Unit) -> Unit,
        ): ListenerBuilder<NextT>

        /**
         * Actual listening start.
         *
         * @param handleErrors error handling function to process errors or to pass them down the chain.
         *        If exception is thrown inside the handler, than listener chain will stop.
         * @param listener event processing logic.
         */
        fun startListening(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            listener: (EventT) -> Unit
        ): Stoppable
    }

}