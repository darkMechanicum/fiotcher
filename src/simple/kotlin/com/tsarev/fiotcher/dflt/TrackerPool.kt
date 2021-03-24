package com.tsarev.fiotcher.dflt


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
     * @return `true` if success
     */
    fun startTracker(
        resourceBundle: WatchT,
        tracker: Tracker<WatchT>,
        key: String
    ): Boolean

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
    )

}