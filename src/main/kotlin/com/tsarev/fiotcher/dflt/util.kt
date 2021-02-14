package com.tsarev.fiotcher.dflt

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

typealias Brake<T> = AtomicReference<CompletableFuture<T>?>

/**
 * Check, if this brake was once pushed.
 */
val <T> Brake<T>.isPushed get() = this.get() != null

/**
 * Push the brake once with [CompletableFuture], if it was not pushed.
 */
inline fun <T> Brake<T>.push(block: (CompletableFuture<T>) -> Unit = {}): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val success = compareAndSet(null, future)
    if (success) block(future)
    return get()!!
}

/**
 * Push the brake once with [CompletableFuture], if it was not pushed, and complete
 * the future after [block] completion with [completeValue].
 */
inline fun <T> Brake<T>.pushCompleted(completeValue: T, block: (CompletableFuture<*>) -> Unit): CompletableFuture<*> {
    val future = CompletableFuture<T>()
    val success = compareAndSet(null, future)
    if (success) {
        block(future)
        future.complete(completeValue)
    }
    return get()!!
}