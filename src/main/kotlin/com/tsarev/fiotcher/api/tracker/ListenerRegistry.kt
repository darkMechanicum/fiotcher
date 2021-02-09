package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.CompletionStage

/**
 * Listener registry used to synchronize [ChainingListener]
 * registration and de registration.
 */
interface ListenerRegistry<WatchT : Any> {

    /**
     * Register listener.
     */
    fun registerListener(
        listener: ChainingListener<TrackerEventBunch<WatchT>>,
        key: String
    ): ChainingListener<TrackerEventBunch<WatchT>>

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}