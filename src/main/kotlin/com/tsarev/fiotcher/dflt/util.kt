package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FiotcherException
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asFailure
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

data class BrakeInner<T>(
    val stopFuture: CompletableFuture<T>,
    var forcibly: Boolean
)

typealias Brake<T> = AtomicReference<BrakeInner<T>>

/**
 * Check, if this brake was once pushed.
 */
val <T> Brake<T>.isPushed get() = this.get() != null

/**
 * Check, if this brake was once pushed.
 */
val <T> Brake<T>.isForced get() = this.get()?.forcibly ?: false

/**
 * Push the brake once with [CompletableFuture], if it was not pushed.
 */
inline fun <T> Brake<T>.push(
    force: Boolean = false,
    block: CompletableFuture<T>.() -> Unit = {}
): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val success = compareAndSet(null, BrakeInner(future, force))
    if (success) block(future)
    return get()?.stopFuture!!
}

/**
 * Push the brake once with [CompletableFuture], if it was not pushed, and complete
 * the future after [block] completion with [completeValue].
 */
inline fun <T> Brake<T>.pushCompleted(
    completeValue: T,
    force: Boolean = false,
    block: (CompletableFuture<*>) -> Unit
): CompletableFuture<*> {
    val future = CompletableFuture<T>()
    val success = compareAndSet(null, BrakeInner(future, force))
    if (success) {
        block(future)
        future.complete(completeValue)
    }
    return get()?.stopFuture!!
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
            throw FiotcherException("Unexpected exception while transform processing", fiotcherCommon)
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
            // Handle or rethrow.
            val transformedException = handleErrors.invoke(common)
            if (transformedException != null) {
                send(transformedException.asFailure())
            }
        } catch (innerCause: Throwable) {
            throw FiotcherException("Exception while transforming another: [$common]", innerCause)
        }
    } else throw common
}

fun <T> runAsync(executor: Executor, block: () -> T) = CompletableFuture.supplyAsync(block, executor)

/**
 * Check if OS is windows.
 */
val isWindows = System.getProperty("os.name", "null").startsWith("Windows")