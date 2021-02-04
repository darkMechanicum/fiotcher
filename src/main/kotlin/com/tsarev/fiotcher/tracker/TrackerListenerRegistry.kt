package com.tsarev.fiotcher.tracker

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
interface TrackerListenerRegistry {

    /**
     * Register listener.
     */
    fun registerListener(
        listener: TrackerListener,
        key: String? = null
    )

}

/**
 * Interface to listen for resource events from managed [Tracker]s.
 */
interface TrackerListener {

    /**
     * Called when some monitored resource is changed.
     */
    fun onChanged(resource: URI) {}

    /**
     * Called when some monitored resource has been deleted.
     */
    fun onDeleted(resource: URI) {}

    /**
     * Called when some monitored resource has been created.
     */
    fun onCreated(resource: URI) {}

}