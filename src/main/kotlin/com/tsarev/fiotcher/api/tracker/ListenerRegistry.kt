package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.TypedEvents
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
        listener: ChainingListener<TypedEvents<WatchT>>,
        key: String
    ): ChainingListener<TypedEvents<WatchT>>

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}