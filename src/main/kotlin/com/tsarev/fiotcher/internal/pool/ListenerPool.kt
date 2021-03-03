package com.tsarev.fiotcher.internal.pool

import com.tsarev.fiotcher.internal.flow.ChainingListener
import java.util.concurrent.CompletionStage

/**
 * Listener registry used to synchronize [ChainingListener]
 * registration and de registration.
 */
interface ListenerPool<InitialT : Any> {

    /**
     * Register listener.
     */
    fun registerListener(
        listener: ChainingListener<InitialT>,
        key: String
    ): ChainingListener<InitialT>

    /**
     * De register listener.
     */
    fun deRegisterListener(
        key: String,
        force: Boolean = false
    ): CompletionStage<*>

}