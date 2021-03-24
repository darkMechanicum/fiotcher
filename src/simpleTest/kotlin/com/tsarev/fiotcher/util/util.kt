package com.tsarev.fiotcher.util

import com.tsarev.fiotcher.dflt.isWindows
import org.junit.jupiter.api.Assertions
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit

// --- Utilities for testing async code in sync - like way ---
/**
 * Asynchronous thread safe single element queue.
 */
typealias AsyncTestEvents = SynchronousQueue<Any>

val defaultTestAsyncAssertTimeoutMs = if (isWindows) 2000L else 500L

/**
 * Send event to the queue and await for the assertion on that event.
 *
 * @param event event ot send
 * @param timeoutMs time allocated for sending
 */
fun AsyncTestEvents.sendEvent(event: Any, required: Boolean = true, timeoutMs: Long = defaultTestAsyncAssertTimeoutMs) {
    try {
        if (!offer(event, timeoutMs, TimeUnit.MILLISECONDS) && required)
            Assertions.fail<Unit>("Failed to send event [$event]")
    } catch (cause: InterruptedException) {
        // Do not interrupt test messages sending.
        if (!offer(event, timeoutMs, TimeUnit.MILLISECONDS) && required)
            Assertions.fail<Unit>("Failed to send event [$event]")
        // But honor it and resend.
        throw cause
    }
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
        is T -> assertion(polled as T)
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
fun AsyncTestEvents.assertEvent(
    event: Any,
    required: Boolean = true,
    timeoutMs: Long = defaultTestAsyncAssertTimeoutMs
) {
    val polled = poll(timeoutMs, TimeUnit.MILLISECONDS)
    if (required && polled == null) Assertions.fail<Unit>("No event [$event] received")
    else if (required) Assertions.assertEquals(event, polled) { "Received not expected event" }
}

/**
 * Get event from the queue and assert it equality with specified [events].
 *
 * @param events events to compare with
 * @param timeoutMs time allocated for receiving
 */
fun AsyncTestEvents.assertEventsUnordered(
    vararg events: Any,
    timeoutMs: Long = defaultTestAsyncAssertTimeoutMs,
) {
    val set = Collections.synchronizedSet(events.toMutableSet())
    while (set.isNotEmpty()) {
        val polled = poll(timeoutMs, TimeUnit.MILLISECONDS)
        if (polled == null) Assertions.fail<Unit>("No events [$set] received")
        Assertions.assertTrue(set.remove(polled)) { "Received not expected event [${polled}]" }
    }
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

/**
 * Amout of time in ms to wait, to allow filesystem and watcher to catch changes.
 */
val fileSystemPause = if (isWindows) 500L else 200L