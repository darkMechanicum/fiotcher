package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Stoppable
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

interface StoppableBrakeMixin<T> : DoStopMixin<T>, Stoppable {

    override fun stop(force: Boolean) = doStop(force)

    override val isStopping get() = stopBrake.isPushed && stopBrake.get()?.stopFuture?.isDone?.let { !it } ?: true

    override val isStopped get() = stopBrake.get()?.stopFuture?.isDone ?: false

    override val isStoppedExceptionally get() = stopBrake.get()?.stopFuture?.isCompletedExceptionally ?: false

    override val stoppedException: Throwable?
        get() {
            return if (isStoppedExceptionally) {
                try {
                    stopBrake.get()?.stopFuture?.get()
                    null
                } catch (wrapped: ExecutionException) {
                    wrapped.cause
                }
            } else null
        }
}

interface DoStopMixin<T> {

    val stopBrake: Brake<T>

    fun doStop(force: Boolean = false, exception: Throwable? = null): CompletionStage<*>

}