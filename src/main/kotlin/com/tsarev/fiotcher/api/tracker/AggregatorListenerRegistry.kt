package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.CompletionStage

/**
 * Exception to signal, that tracker listener has been already registered for some key.
 */
class ListenerAlreadyRegistered(key: String)
    : RuntimeException("Listener for key: $key has been already registered.")

/**
 * Exception to signal, that listener registry is stopping and can't register anything.
 */
class ListenerRegistryIsStopping
    : RuntimeException("Listener registry is stopping and can't register anything")

/**
 * Interface to separate [TrackerEvent] listening process
 * from actually generating those events.
 */
interface AggregatorListenerRegistry<WatchT : Any> {

    /**
     * Register listener.
     */
    fun registerListener(
        listener: ChainingListener<TrackerEventBunch<WatchT>>,
        key: String
    ): Stoppable

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}