package com.tsarev.fiotcher.util

import org.junit.jupiter.api.Assertions
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit

/**
 * Caller thread executor, that turns test effectively synchronous.
 */
val callerThreadTestExecutor = Executor { it.run() }

/**
 * Caller thread executor, that turns test effectively synchronous.
 */
val callerThreadTestExecutorService = object : AbstractExecutorService() {
    override fun shutdown() = Unit
    override fun shutdownNow() = listOf<Runnable>()
    override fun isShutdown() = false
    override fun isTerminated() = false
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    override fun execute(command: Runnable) {
        command.run()
    }
}

// --- Utilities for testing async code in sync - like way ---
/**
 * Synchronous non thread safe queue.
 */
typealias SyncTestEvents = LinkedList<Any>

/**
 * Send event to the queue without blocking.
 */
fun SyncTestEvents.sendEvent(event: Any) {
    addFirst(event)
}

/**
 * Get first event from the queue and assert it equality with specified [event].
 */
fun SyncTestEvents.assertEvent(event: Any) {
    val gotEvent = this.pollLast()
    Assertions.assertEquals(event, gotEvent) { "Received not expected event" }
}

/**
 * Get first event of [T] from the queue and assert it with specified block.
 */
inline fun <reified T> SyncTestEvents.assertEvent(assertion: (T) -> Unit) {
    when (val gotEvent = pollLast()) {
        is T -> assertion(gotEvent)
        null -> Assertions.fail { "No event found" }
        else -> Assertions.fail { "Expected event has type [${gotEvent::class}] instead of expected [${T::class}]" }
    }
}

/**
 * Assert there are no events in the queue.
 */
fun SyncTestEvents.assertNoEvent() {
    Assertions.assertTrue(size == 0) { "No event was expected" }
}

// --- Utilities for testing async code in sync - like way ---
/**
 * Asynchronous thread safe single element queue.
 */
typealias AsyncTestEvents = SynchronousQueue<Any>

const val defaultTestAsyncAssertTimeoutMs = 500L

/**
 * Send event to the queue and await for the assertion on that event.
 *
 * @param event event ot send
 * @param timeoutMs time allocated for sending
 */
fun AsyncTestEvents.sendEvent(event: Any, timeoutMs: Long = defaultTestAsyncAssertTimeoutMs) {
    if (!offer(event, timeoutMs, TimeUnit.MILLISECONDS))
        Assertions.fail<Unit>("Failed to send event [$event]")
}

/**
 * Get first event of [T] from the queue and assert it with specified block.
 *
 * @param assertion an assertion to apply to found typed eevent
 * @param timeoutMs time allocated for receiving
 */
inline fun <reified T> AsyncTestEvents.assertEvent(
    timeoutMs: Long = defaultTestAsyncAssertTimeoutMs,
    assertion: (T) -> Unit
) {
    when (val polled = poll(timeoutMs, TimeUnit.MILLISECONDS)) {
        is T -> assertion(polled)
        null -> Assertions.fail<Unit>("No event received")
        else -> Assertions.fail { "Expected event [$polled] has type [${polled::class}] instead of expected [${T::class}]" }
    }
}

/**
 * Get event from the queue and assert it equality with specified [event].
 *
 * @param event event to compare with
 * @param timeoutMs time allocated for receiving
 */
fun AsyncTestEvents.assertEvent(event: Any, timeoutMs: Long = defaultTestAsyncAssertTimeoutMs) {
    val polled = poll(timeoutMs, TimeUnit.MILLISECONDS)
    if (polled == null) Assertions.fail<Unit>("No event [$event] received")
    else Assertions.assertEquals(event, polled) { "Received not expected event" }
}

/**
 * Assert there are no events in the queue.
 *
 * @param timeoutMs time allocated for waiting extra events.
 */
fun AsyncTestEvents.assertNoEvent(timeoutMs: Long = defaultTestAsyncAssertTimeoutMs) {
    val polled = poll(timeoutMs, TimeUnit.MILLISECONDS)
    if (polled != null)
        Assertions.fail<Unit>("Event [$polled] received")
}

// --- Utilities for creating files and directories ---
/**
 * Create empty file in [this] directory.
 */
fun File.createFile(name: String, content: () -> String = { "" }): File {
    if (!exists()) throw IllegalArgumentException("No such directory ${this.absolutePath}")
    if (!isDirectory) throw IllegalArgumentException("File ${this.absolutePath} is not a directory")
    return Paths.get(this.absolutePath, name).toFile().apply {
        if (!exists()) {
            PrintWriter(this).use { it.append(content()) }
        }
    }
}

/**
 * Create empty file in [this] directory.
 */
fun File.createDirectory(name: String): File {
    if (!exists()) throw IllegalArgumentException("No such directory ${this.absolutePath}")
    if (!isDirectory) throw IllegalArgumentException("File ${this.absolutePath} is not a directory")
    return Paths.get(this.absolutePath, name).toFile().apply {
        if (!exists()) mkdirs()
    }
}