package com.tsarev.fiotcher.internal.pool

import com.tsarev.fiotcher.api.PoolIsStopped
import com.tsarev.fiotcher.api.TrackerAlreadyRegistered
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future


/**
 * Tracker pool that managers trackers execution.
 * Allows to create, start, stop and monitor trackers.
 *
 * Responsible for trackers asynchronous initialization and
 * processing.
 */
interface TrackerPool<WatchT : Any> {

    /**
     * Register and start tracker to scan passed [resourceBundle].
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param tracker tracker to register
     * @return a asynchronous handle to tracker shutdown hook
     * If pool is stopped, then handle will be completed exceptionally with [PoolIsStopped] exception.
     * If other tracker is already registered with passed pair, then handle will be completed
     * exceptionally with [TrackerAlreadyRegistered] exception.
     */
    fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): Future<Tracker<WatchT>>

    /**
     * Stop tracker asynchronously based on [resourceBundle].
     *
     * May send additional out-of-order events to
     * reflect resources state at tracker shutdown.
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param force try to force tracker resource shutdown, thus ignoring pending resource events
     * @return a asynchronous handle to tracker stopping process
     */
    fun stopTracker(
        resourceBundle: WatchT,
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}