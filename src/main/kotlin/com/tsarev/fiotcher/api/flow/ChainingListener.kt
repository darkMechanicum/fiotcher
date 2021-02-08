package com.tsarev.fiotcher.api.flow

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.Flow

/**
 * Marker interface, that can chain event processing within [WayStation] context.
 */
interface ChainingListener<ResourceT : Any> : Flow.Subscriber<ResourceT>, Stoppable {

    /**
     * Ask next event from event source.
     */
    fun askNext()

}