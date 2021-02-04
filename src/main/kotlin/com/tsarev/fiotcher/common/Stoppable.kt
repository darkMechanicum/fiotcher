package com.tsarev.fiotcher.common

import java.util.concurrent.Future

/**
 * Convenient interface to control shutdown process more
 * precisely.
 */
interface Stoppable {

    /**
     * Stop this [Stoppable].
     *
     * @param force try to force stoppable resource shutdown
     */
    fun stop(force: Boolean = false): Future<*>

}