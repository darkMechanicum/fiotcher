package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.KClassTypedKey
import com.tsarev.fiotcher.api.flow.ChainingListener
import java.util.concurrent.CompletionStage

/**
 * Listener registry used to synchronize [ChainingListener]
 * registration and de registration.
 */
interface ListenerRegistry {

    /**
     * Register listener.
     */
    fun <EventT : Any> registerListener(
        listener: ChainingListener<EventT>,
        key: KClassTypedKey<EventT>
    ): ChainingListener<EventT>

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: KClassTypedKey<*>,
        force: Boolean = false
    ): CompletionStage<*>

}