package com.tsarev.fiotcher.api.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Convenient interface to control shutdown process more
 * precisely.
 */
interface Stoppable {

    /**
     * Stop this [Stoppable] asynchronously.
     *
     * @param force try to force stoppable resource shutdown
     */
    fun stop(
        force: Boolean = false
    ): CompletableFuture<*>

    /**
     * Stop this [Stoppable] synchronously.
     *
     * @param force try to force stoppable resource shutdown
     */
    fun stopAndWait(force: Boolean = false) {
        stop(force).get()
    }

    /**
     * Stop this [Stoppable] synchronously.
     *
     * @param timeout amount of time to wait
     * @param unit time unit to use
     * @param force try to force stoppable resource shutdown
     */
    fun stopAndWait(timeout: Long, unit: TimeUnit, force: Boolean = false) {
        stop(force).get(timeout, unit)
    }

}