package com.tsarev.fiotcher.internal.pool

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.push
import com.tsarev.fiotcher.dflt.reset
import com.tsarev.fiotcher.internal.EventWithException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.Flow


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
     * Brake to handle concurrent [init] calls.
     */
    private val initBrake = Brake<Flow.Publisher<EventWithException<InitialEventsBunch<WatchT>>>>()

    /**
     * Possible time consuming initial registration.
     *
     * @param resourceBundle path to tracked resource bundle
     */
    fun init(
        resourceBundle: WatchT,
        executor: Executor
    ): Flow.Publisher<EventWithException<InitialEventsBunch<WatchT>>> = initBrake.push {
        this.resourceBundle = resourceBundle
        val publisher = doInit(executor)
        it.complete(publisher)
    }.get()

    /**
     * Part of initialization that can be altered by descendants.
     */
    abstract fun doInit(
        executor: Executor
    ): Flow.Publisher<EventWithException<InitialEventsBunch<WatchT>>>

    // Delegate to actual stopping logic and resting [initBrake].
    override fun stop(force: Boolean): CompletionStage<*> = doStop(force).whenComplete { _, _ -> initBrake.reset() }

    /**
     * Actual stopping logic.
     */
    abstract fun doStop(force: Boolean): CompletionStage<*>
}