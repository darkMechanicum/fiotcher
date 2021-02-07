package com.tsarev.fiotcher.api.flow

import com.tsarev.fiotcher.api.util.Stoppable
import java.util.concurrent.Flow

/**
 * Listener, that can chain events.
 */
interface ChainingListener<ResourceT : Any> : Flow.Subscriber<ResourceT>, Stoppable {

    /**
     * Ask next event.
     */
    fun askNext()

}