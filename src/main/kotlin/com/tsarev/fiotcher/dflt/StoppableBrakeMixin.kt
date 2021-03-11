package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

/**
 * Mixin to implement [Stoppable] with [Brake].
 */
interface StoppableBrakeMixin<T> : DoStop<T>, Stoppable {

    override fun stop(force: Boolean) = doStop(force)

    override val isStopping get() = stopBrake.isPushed && stopBrake.get()?.stopFuture?.isDone?.let { !it } ?: true

    override val isStopped get() = stopBrake.get()?.stopFuture?.isDone ?: false

    override val stoppedException: Throwable?
        get() {
            return try {
                stopBrake.get()?.stopFuture?.get()
                null
            } catch (wrapped: ExecutionException) {
                wrapped.cause
            }
        }
}

/**
 * Interface to handle stopping with [Brake]. Divided from [StoppableBrakeMixin]
 * for correct delegation.
 */
interface DoStop<T> {

    val stopBrake: Brake<T>

    fun doStop(force: Boolean = false, exception: Throwable? = null): CompletionStage<*>

}