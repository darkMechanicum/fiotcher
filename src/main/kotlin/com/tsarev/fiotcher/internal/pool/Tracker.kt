package com.tsarev.fiotcher.internal.pool

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.StoppableBrakeMixin
import com.tsarev.fiotcher.dflt.push
import com.tsarev.fiotcher.internal.EventWithException
import java.util.concurrent.Executor


/**
 * Resource tracker that is responsible for
 * tracking resource sets at specified location and
 * watching if resource set is changed, or
 * resources content is changed.
 */
abstract class Tracker<WatchT : Any> : Runnable, Stoppable, StoppableBrakeMixin<Unit> {

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
        executor: Executor,
        sendEvent: (EventWithException<InitialEventsBunch<WatchT>>) -> Unit
    ) {
        this@Tracker.resourceBundle = resourceBundle
        doInit(executor, sendEvent)
    }

    /**
     * Part of initialization that can be altered by descendants.
     */
    abstract fun doInit(
        executor: Executor,
        sendEvent: (EventWithException<InitialEventsBunch<WatchT>>) -> Unit
    )

}