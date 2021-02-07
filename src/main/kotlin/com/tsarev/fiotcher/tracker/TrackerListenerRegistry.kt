package com.tsarev.fiotcher.tracker

import com.tsarev.fiotcher.flows.ChainingListener
import com.tsarev.fiotcher.flows.CommonListener
import java.net.URI

/**
 * Exception to signal, that tracker listener has been already registered for some key.
 */
class TrackerListenerAlreadyRegistered(key: String)
    : RuntimeException("Tracker listener for key: $key has been already registered.")

/**
 * Interface to separate [TrackerEvent] listening process
 * from actually generating these events.
 */
interface TrackerListenerRegistry<WatchT : Any> {

    /**
     * Register listener.
     */
    fun registerListener(
        listener: ChainingListener<TrackerEventBunch<WatchT>>,
        key: String? = null
    )

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String? = null,
        force: Boolean = false
    )

}