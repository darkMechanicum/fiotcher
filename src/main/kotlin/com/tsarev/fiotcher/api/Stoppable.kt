package com.tsarev.fiotcher.api

import java.util.concurrent.Future

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
    fun stop(force: Boolean = false): Future<*>

    /**
     * Stop this [Stoppable] synchronously.
     *
     * @param force try to force stoppable resource shutdown
     */
    fun stopAndWait(force: Boolean = false) {
        stop(force).get()
    }

}