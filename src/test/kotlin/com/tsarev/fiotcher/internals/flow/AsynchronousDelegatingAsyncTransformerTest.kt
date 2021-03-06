package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncChainListener
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Testing [DelegatingAsyncChainListener].
 */
class AsynchronousDelegatingAsyncChainListenerTest {

    private val testAsync = AsyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `asynchronous send two events`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val executor = acquireExecutor("executor", testAsync::sendEvent, testAsync::sendEvent)
        val publisher = SubmissionPublisher<EventWithException<String>>(executor, 10)
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = executor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null,
        )

        // --- Test ---
        executor.activate {
            // Chained subscription.
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")

            // Listener subscription.
            publisher.subscribe(listener)
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")

            // First submit.
            publisher.submit("one".asSuccess())
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("one")
            testAsync.assertEvent("executor finished")
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("chained one")
            testAsync.assertEvent("executor finished")

            // Second submit.
            publisher.submit("two".asSuccess())
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("two")
            testAsync.assertEvent("executor finished")
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("chained two")
            testAsync.assertEvent("executor finished")
        }
    }

    @Test
    fun `asynchronous force stop after submit`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val executor = acquireExecutor("executor", testAsync::sendEvent, testAsync::sendEvent)
        val publisher = SubmissionPublisher<EventWithException<String>>(executor, 10)

        // --- Test ---
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = executor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null,
        )

        executor.activate {
            // Chained subscription.
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")

            // Listener subscription.
            publisher.subscribe(listener)
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")

            // First submit.
            publisher.submit("one".asSuccess())
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("one")
            testAsync.assertEvent("executor finished")
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("chained one")
            testAsync.assertEvent("executor finished")

            // Stop and submit.
            publisher.submit("two".asSuccess())
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("two")
            testAsync.assertEvent("executor finished")

            // Stop execution.
            listener.stop(true)

            // On complete task.
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")

            testAsync.assertEvents(
                "executor start" to false,
                "executor finished" to false
            )

            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous graceful stop after submit`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val listenerExecutor = acquireExecutor(
            name = "listener executor",
            { testAsync.sendEvent("listener executor start") },
            { testAsync.sendEvent("listener executor finished") }
        )
        val publisherExecutor = acquireExecutor("publisher executor", testAsync::sendEvent, testAsync::sendEvent)
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)

        // --- Test ---
        // Start listener.
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = listenerExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null,
        )

        // Test chained subscription.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Test listener subscription.
        publisherExecutor.activate {
            publisher.subscribe(listener)
            testAsync.assertEvent("publisher executor start")
            testAsync.assertEvent("publisher executor finished")
        }

        // Test submit.
        // Allow only publisher executor, imitating `not processed event`.
        publisherExecutor.activate {
            publisher.submit("one".asSuccess())
            testAsync.assertEvent("publisher executor start")
            testAsync.assertEvent("one")
            testAsync.assertEvent("publisher executor finished")
        }

        // Test stop while having `non processed event`
        listener.stop(false)

        // Test `non processed event` processing.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained one")
            testAsync.assertEvent("listener executor finished")
        }

        // First stopping async block.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Second stopping async block.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        activateAll {
            // Skip listener inner publisher closing (can be done in previous
            // [listenerExecutor] block without separate execution submit).
            testAsync.assertEvents(
                "listener executor start" to false,
                "listener executor finished" to false,
                // Allow cancel processing by submission publisher.
                "publisher executor start" to false,
                "publisher executor finished" to false
            )
            testAsync.assertNoEvent()
        }
    }

    /**
     * This test is need for case, when event is not passed away
     * immediately in async transform function, but takes some time to process.
     */
    @Test
    fun `asynchronous graceful stop after submit with long event processing`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val listenerExecutor = acquireExecutor("listener executor", testAsync::sendEvent, testAsync::sendEvent)
        val publisherExecutor = acquireExecutor("publisher executor", testAsync::sendEvent, testAsync::sendEvent)
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = listenerExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish ->
                testAsync.sendEvent(it); Thread.sleep(defaultTestAsyncAssertTimeoutMs * 2); publish(
                it
            )
            },
            handleErrors = null,
        )

        // --- Test ---
        // Test chained subscription.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        publisherExecutor.activate {
            // Test listener subscription.
            publisher.subscribe(listener)
            testAsync.assertEvent("publisher executor start")
            testAsync.assertEvent("publisher executor finished")

            // Allow only publisher executor, imitating `not processed event`.
            publisher.submit("one".asSuccess())
            testAsync.assertEvent("publisher executor start")

            // Stop at event processing.
            testAsync.assertEvent("one")

            // Stop transformer listener.
            listener.stop(false)

            // Sleep to couple pause at event processing.
            Thread.sleep(defaultTestAsyncAssertTimeoutMs * 2)

            // This finished event also is responsible for cancelling publisher subscription.
            testAsync.assertEvent("publisher executor finished")
        }

        // Test `non processed event` processing by chained listener.
        listenerExecutor.activate {
            // Async stopping block.
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")

            // Process event.
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained one")
            testAsync.assertEvent("listener executor finished")

            // First stopping async block.
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")

            // Second stopping async block.
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        activateAll {
            testAsync.assertEvents(
                // Allow publisher cancel request processing.
                "publisher executor start" to false,
                "publisher executor finished" to false,
            )
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous double stop`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val innerExecutor = acquireExecutor(
            beforeStart = { testAsync.sendEvent("executor start") },
            afterCompletion = { testAsync.sendEvent("executor finished") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(innerExecutor, 10)

        // --- Test ---
        // Start listener.
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = innerExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        publisher.subscribe(listener)

        val firstStopHandle = AtomicReference<CompletableFuture<*>>()
        thread { firstStopHandle.set(listener.stop().toCompletableFuture()) }.join()

        val secondStopHandle = AtomicReference<CompletableFuture<*>>()
        thread { secondStopHandle.set(listener.stop().toCompletableFuture()) }.join()

        // Stop handles must be exactly the same.
        Assertions.assertNotNull(firstStopHandle.get())
        Assertions.assertNotNull(secondStopHandle.get())
        Assertions.assertTrue(firstStopHandle.get() === secondStopHandle.get())

        testAsync.assertNoEvent()
    }

}