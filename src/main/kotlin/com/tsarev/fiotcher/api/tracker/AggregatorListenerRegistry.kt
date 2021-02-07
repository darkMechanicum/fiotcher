package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.flow.ChainingListener

/**
 * Exception to signal, that tracker listener has been already registered for some key.
 */
class TrackerListenerAlreadyRegistered(key: String)
    : RuntimeException("Tracker listener for key: $key has been already registered.")

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
    )

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String,
        force: Boolean = false
    )

}