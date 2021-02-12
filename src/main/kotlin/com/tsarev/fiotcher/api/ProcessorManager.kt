package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.internal.Processor
import java.util.concurrent.CompletionStage
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
     * @throws PoolIsStopping if underlying tracker pool is stopping
     * @return a future handle, that will complete when stopping completes.
     */
    fun stopTracking(resource: InitialEventT, key: String, force: Boolean = false): CompletionStage<*>

    fun listenForInitial(key: String): ListenerBuilder<InitialEventsBunch<InitialEventT>>

    interface EventMarker

    fun <EventT : EventMarker> listenForKey(key: String, type: KClass<EventT>): ListenerBuilder<EventT>

    interface ListenerBuilder<EventT : Any> {

        /**
         * Aggregate events into new type new event type.
         *
         * @param key new event type
         */
        fun doAggregate(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            key: KClassTypedKey<EventT>,
        )

        /**
         * Chain from listener, that will transform its events to this listener ones synchronously.
         *
         * @param transformer event transforming logic
         */
        fun <NextT : Any> chain(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            async: Boolean = false,
            transformer: (EventT) -> NextT?,
        ): ListenerBuilder<NextT>

        /**
         * Chain from listener, that will transform its events to this listener grouped ones synchronously.
         *
         * @param transformer event to collection transforming logic
         */
        fun <NextT : Any> split(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            async: Boolean = false,
            transformer: (EventT) -> Collection<NextT?>?,
        ): ListenerBuilder<NextT>

        /**
         * Create proxy listener, that will handle split events in same queue and thread.
         *
         * @transformer a function that accepts [EventT] event and function to publish it further,
         * thus allowing to make a number of publishing on its desire.
         */
        fun <NextT : Any> delegate(
            handleErrors: ((Throwable) -> Throwable?)? = null,
            async: Boolean = false,
            transformer: (EventT, (NextT) -> Unit) -> Unit,
        ): ListenerBuilder<NextT>

        /**
         * Actual listening start.
         */
        fun startListening(
            async: Boolean = false,
            handleErrors: ((Throwable) -> Throwable?)? = null,
            listener: (EventT) -> Unit
        ): Stoppable
    }

}