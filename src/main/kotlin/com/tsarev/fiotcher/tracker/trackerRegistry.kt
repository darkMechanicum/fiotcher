package com.tsarev.fiotcher.tracker

import com.tsarev.fiotcher.common.Stoppable
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.Future


/**
 * Exception to signal, that tracker has been already registered for some URI.
 */
class TrackerAlreadyRegistered(resource: URI, key: String)
    : RuntimeException("Tracker for resource: $resource and key: $key has been already registered.")

/**
 * Exception to signal, that tracker pool is stopping and can't register anything.
 */
class PoolIsStopping
    : RuntimeException("Tracker pool is stopping and can't register anything")

/**
 * Resource tracker that is responsible for
 * tracking resource sets at specified location and
 * watching if resource set is changed, or
 * resources content is changed.
 */
abstract class Tracker : Runnable, Stoppable {

    /**
     * Path to tracked resource bundle.
     */
    protected lateinit var resourceBundle: URI

    /**
     * Possible time consuming initial registration.
     *
     * @param resourceBundle path to tracked resource bundle
     */
    fun init(
        resourceBundle: URI,
        executor: Executor = ForkJoinPool.commonPool()
    ): Flow.Publisher<TrackerEventBunch> {
        this.resourceBundle = resourceBundle
        return doInit(executor)
    }

    /**
     * Part of initialization that can be altered by descendants.
     */
    abstract fun doInit(
        executor: Executor
    ): Flow.Publisher<TrackerEventBunch>

}

/**
 * Tracker pool that managers trackers execution.
 * Allows to create, start, stop and monitor trackers.
 *
 * Responsible for trackers asynchronous behaviour.
 */
interface TrackerPool : Stoppable {

    /**
     * Register and start tracker to scan passed [resourceBundle].
     *
     * @param resourceBundle bundle, for which tracker was registered
     * @param key type, with which tracker was registered
     * @param tracker tracker to register
     */
    fun registerTracker(
        resourceBundle: URI,
        tracker: Tracker,
        key: String? = null
    ): Future<*>

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
    fun deRegisterTracker(
        resourceBundle: URI,
        key: String? = null,
        force: Boolean = false
    ): Future<*>

}