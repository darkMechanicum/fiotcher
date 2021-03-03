package com.tsarev.fiotcher.api

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * Convenient interface to control shutdown process more
 * precisely.
 */
interface Stoppable {

    /**
     * Is stopping flag.
     */
    val isStopping: Boolean

    /**
     * Is stopped flag.
     */
    val isStopped: Boolean

    /**
     * Is stopped exceptionally flag.
     */
    val isStoppedExceptionally: Boolean

    /**
     * Exception, that cause stopping.
     */
    val stoppedException: Throwable?

    /**
     * Stop this [Stoppable] asynchronously.
     *
     * @param force try to force stoppable resource shutdown
     */
    fun stop(
        force: Boolean = false
    ): CompletionStage<*>

    /**
     * Stop this [Stoppable] synchronously.
     *
     * @param force try to force stoppable resource shutdown
     */
    fun stopAndWait(force: Boolean = false) {
        stop(force).toCompletableFuture().get()
    }

    /**
     * Stop this [Stoppable] synchronously.
     *
     * @param timeout amount of time to wait
     * @param unit time unit to use
     * @param force try to force stoppable resource shutdown
     */
    fun stopAndWait(timeout: Long, unit: TimeUnit, force: Boolean = false) {
        stop(force).toCompletableFuture().get(timeout, unit)
    }

}