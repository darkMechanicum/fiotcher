package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.util.Stoppable
import java.util.concurrent.CompletionStage


/**
 * Exception to signal, that tracker has been already registered for some URI.
 */
class TrackerAlreadyRegistered(resource: Any, key: String)
    : RuntimeException("Tracker for resource: $resource and key: $key has been already registered.")

/**
 * Exception to signal, that tracker pool is stopping and can't register anything.
 */
class PoolIsStopping
    : RuntimeException("Tracker pool is stopping and can't register anything")

/**
 * Tracker pool that managers trackers execution.
 * Allows to create, start, stop and monitor trackers.
 *
 * Responsible for trackers asynchronous behaviour.
 */
interface TrackerPool<WatchT : Any> : Stoppable {

    /**
     * Register and start tracker to scan passed [resourceBundle].
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param tracker tracker to register
     */
    fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): CompletionStage<Stoppable>

    /**
     * Stop tracker asynchronously based on [resourceBundle].
     *
     * May send additional out-of-order [TrackerEventBunch] to
     * reflect resources state at tracker shutdown.
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param force try to force tracker resource shutdown
     *
     */
    fun stopTracker(
        resourceBundle: WatchT,
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}