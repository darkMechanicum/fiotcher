package com.tsarev.fiotcher.api

import java.util.concurrent.CompletionStage

/**
 * This is high level entry point for using fiotcher library.
 */
interface ProcessorManager<InitialEventT : Any> {

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
     */
    fun listenForKey(key: String): ListenerBuilder<InitialEventsBunch<InitialEventT>>

    /**
     * Stop listening to tracker events by key.
     *
     * @param key type of this tracked path, to bind to handlers
     * @param force force if set to `false` then attempt to process all passed events and then stop
     * @return a asynchronous handle to stopping process
     */
    fun stopListening(key: String, force: Boolean = false): CompletionStage<*>

    /**
     * Builder interface to ease listener registration process.
     */
    interface ListenerBuilder<EventT : Any> {

        /**
         * Create proxy listener, that will handle split events in new queue and with new executor.
         *
         * @transformer a function that accepts [EventT] event and function to publish it further,
         * thus allowing to make a number of publishing on its desire.
         * @param handleErrors error handling function to process errors or to pass them down the chain.
         *        If exception is thrown inside the handler, than listener chain will stop.
         */
        fun <NextT : Any> delegateAsync(
            handleErrors: ((Throwable) -> Throwable?)? = null,
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