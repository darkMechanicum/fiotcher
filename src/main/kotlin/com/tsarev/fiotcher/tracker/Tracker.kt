package com.tsarev.fiotcher.tracker

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import java.util.concurrent.ForkJoinPool

/**
 * Resource tracker that is responsible for
 * tracking resource sets at specified location and
 * watching if resource set is changed, or
 * resources content is changed.
 */
abstract class Tracker<WatchT : Any> : Runnable, Stoppable {

    /**
     * Path to tracked resource bundle.
     */
    protected lateinit var resourceBundle: WatchT

    /**
     * Possible time consuming initial registration.
     *
     * @param resourceBundle path to tracked resource bundle
     */
    fun init(
        resourceBundle: WatchT,
        executor: Executor = ForkJoinPool.commonPool()
    ): Flow.Publisher<TrackerEventBunch<WatchT>> {
        this.resourceBundle = resourceBundle
        return doInit(executor)
    }

    /**
     * Part of initialization that can be altered by descendants.
     */
    abstract fun doInit(
        executor: Executor
    ): Flow.Publisher<TrackerEventBunch<WatchT>>

}