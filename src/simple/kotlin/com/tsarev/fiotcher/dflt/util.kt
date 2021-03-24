package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FiotcherException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

enum class BrakeState(
    val isForced: Boolean,
) {
    PUSHED(false),
    PUSHED_FORCIBLY(true);
}

typealias Brake = AtomicReference<BrakeState>

/**
 * Check, if this brake was once pushed.
 */
val Brake.isPushed get() = this.get() != null

/**
 * Check, if this brake was once pushed.
 */
val Brake.isForced get() = this.get()?.isForced ?: false

/**
 * Push the brake once with [CompletableFuture], if it was not pushed.
 */
inline fun Brake.push(
    force: Boolean = false,
    block: () -> Unit = {}
) {
    val success = compareAndSet(null, if (force) BrakeState.PUSHED_FORCIBLY else BrakeState.PUSHED)
    if (success) block()
}

/**
 * Try to handle exceptions in the provided block,
 * wrapping them at need and sending further.
 */
fun <T : Any, S : Any> handleErrors(
    handleErrors: ((Throwable) -> Throwable?)? = null,
    send: (EventWithException<T>) -> Unit = {},
    item: EventWithException<S>,
    block: (S) -> Unit
) {
    item.ifSuccess { event ->
        try {
            block(event)
        } catch (fiotcherCommon: FiotcherException) {
            throw fiotcherCommon
        } catch (common: Throwable) {
            tryTransform(handleErrors, common, send)
        }
    }
    item.ifFailed { exception ->
        tryTransform(handleErrors, exception, send)
    }
}

/**
 * Try to transform caught exception and resend it, if handler is set.
 */
fun <T : Any> tryTransform(
    handleErrors: ((Throwable) -> Throwable?)?,
    common: Throwable,
    send: (EventWithException<T>) -> Unit
) {
    if (handleErrors != null) {
        try {
            // Handle or resend.
            val transformedException = handleErrors.invoke(common)
            if (transformedException != null) {
                send(transformedException.asFailure())
            }
        } catch (innerCause: Throwable) {
            // Throw if transforming failed.
            throw FiotcherException("Exception while transforming another: [$common]", innerCause)
        }
    } else throw common
}

/**
 * Check if OS is windows.
 */
val isWindows = System.getProperty("os.name", "null").startsWith("Windows")