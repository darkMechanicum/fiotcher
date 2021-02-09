package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.CompletionStage

/**
 * Interface to separate [TrackerEvent] listening process
 * from actually generating those events.
 */
interface AggregatorListenerRegistry<WatchT : Any> {

    /**
     * Register listener.
     */
    fun registerListener(
        listener: ChainingListener<TrackerEventBunch<WatchT>>,
        key: String
    ): Stoppable

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}