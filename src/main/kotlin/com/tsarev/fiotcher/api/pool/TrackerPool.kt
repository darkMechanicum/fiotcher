package com.tsarev.fiotcher.api.pool

import com.tsarev.fiotcher.api.PoolIsStopping
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.TrackerAlreadyRegistered
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor


/**
 * Tracker pool that managers trackers execution.
 * Allows to create, start, stop and monitor trackers.
 *
 * Responsible for trackers asynchronous initialization and
 * processing.
 */
interface TrackerPool<WatchT : Any> : Stoppable {

    /**
     * Executor, that is used to launch trackers.
     */
    val trackerExecutor: Executor

    /**
     * Register and start tracker to scan passed [resourceBundle].
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param tracker tracker to register
     * @throws TrackerAlreadyRegistered if tacker is already registered within [resourceBundle] and [key]
     * @throws PoolIsStopping when the pool is stopping
     * @return a asynchronous handle to tracker shutdown hook
     */
    fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): CompletionStage<Tracker<WatchT>>

    /**
     * Stop tracker asynchronously based on [resourceBundle].
     *
     * May send additional out-of-order events to
     * reflect resources state at tracker shutdown.
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param force try to force tracker resource shutdown, thus ignoring pending resource events
     * @throws PoolIsStopping when the pool is stopping
     * @return a asynchronous handle to tracker stopping process
     */
    fun stopTracker(
        resourceBundle: WatchT,
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}