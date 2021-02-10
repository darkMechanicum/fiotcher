package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.api.EventWithException
import com.tsarev.fiotcher.api.asSuccess
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncTransformer
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Testing [DelegatingAsyncTransformer].
 */
class AsynchronousDelegatingAsyncTransformerTest {

    private val testAsync = AsyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `asynchronous send two events`() {
        // Prepare.
        val chained = CommonListener<String>(
            { testAsync.sendEvent("chained $it") },
            { testAsync.sendEvent("chained subscribed") }
        )
        val executor = acquireExecutor(
            name = "executor",
            { testAsync.sendEvent("executor start") },
            { testAsync.sendEvent("executor finished") }
        )
        val publisher = SubmissionPublisher< EventWithException<String>>(executor, 10)

        // Test.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = executor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = executor,
            onSubscribeHandler = { testAsync.sendEvent("subscribed") },
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null,
        )

        executor.activate {
            // Chained subscription.
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("chained subscribed")
            testAsync.assertEvent("executor finished")

            // Listener subscription.
            publisher.subscribe(listener)
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("subscribed")
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
        // Prepare.
        val chained = CommonListener<String>(
            { testAsync.sendEvent("chained $it") },
            { testAsync.sendEvent("chained subscribed") }
        )
        val executor = acquireExecutor(
            name = "executor",
            { testAsync.sendEvent("executor start") },
            { testAsync.sendEvent("executor finished") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(executor, 10)

        // Test.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = executor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = executor,
            onSubscribeHandler = { testAsync.sendEvent("subscribed") },
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null,
        )

        executor.activate {
            // Chained subscription.
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("chained subscribed")
            testAsync.assertEvent("executor finished")

            // Listener subscription.
            publisher.subscribe(listener)
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("subscribed")
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
            listener.stop(true)
            // Stop execution.
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")
            // Listener execution (disabled).
            testAsync.assertEvent("executor start")
            testAsync.assertEvent("executor finished")
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous graceful stop after submit`() {
        // --- Prepare ---
        val chained = CommonListener<String>(
            { testAsync.sendEvent("chained $it") },
            { testAsync.sendEvent("chained subscribed") }
        )
        val listenerExecutor = acquireExecutor(
            name = "listener executor",
            { testAsync.sendEvent("listener executor start") },
            { testAsync.sendEvent("listener executor finished") }
        )
        val publisherExecutor = acquireExecutor(
            name = "publisher executor",
            { testAsync.sendEvent("publisher executor start") },
            { testAsync.sendEvent("publisher executor finished") }
        )
        val stoppingExecutor = acquireExecutor(
            name = "stopping executor",
            { testAsync.sendEvent("stopping executor start") },
            { testAsync.sendEvent("stopping executor finished") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)

        // --- Prepare ---
        // Start listener.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = listenerExecutor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = stoppingExecutor,
            onSubscribeHandler = { testAsync.sendEvent("subscribed") },
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null,
        )

        // Test chained subscription.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained subscribed")
            testAsync.assertEvent("listener executor finished")
        }

        // Test listener subscription.
        publisherExecutor.activate {
            publisher.subscribe(listener)
            testAsync.assertEvent("publisher executor start")
            testAsync.assertEvent("subscribed")
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
        stoppingExecutor.activate {
            listener.stop(false)
            testAsync.assertEvent("stopping executor start")
        }

        // Test `non processed event` processing.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained one")
            testAsync.assertEvent("listener executor finished")
        }

        // Test cancel subscription.
        publisherExecutor.activate {
            testAsync.assertEvent("publisher executor start")
            testAsync.assertEvent("publisher executor finished")
        }

        // Test listener inner publisher closing.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Test async waiter stopping.
        stoppingExecutor.activate {
            testAsync.assertEvent("stopping executor finished")
        }

        testAsync.assertNoEvent()
    }

    @Test
    fun `asynchronous double stop`() {
        // --- Prepare ---
        val chained = CommonListener<String>({ testAsync.sendEvent("chained $it") })
        val innerExecutor = acquireExecutor(
            beforeStart = { testAsync.sendEvent("executor start") },
            afterCompletion = { testAsync.sendEvent("executor finished") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(innerExecutor, 10)

        // --- Prepare ---
        // Start listener.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = innerExecutor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = innerExecutor,
            onSubscribeHandler = { testAsync.sendEvent("subscribed") },
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        publisher.subscribe(listener)

        val firstStopHandle = AtomicReference<CompletableFuture<*>>()
        thread { firstStopHandle.set(listener.stop()) }.join()

        val secondStopHandle = AtomicReference<CompletableFuture<*>>()
        thread { secondStopHandle.set(listener.stop()) }.join()

        // Stop handles must be exactly the same.
        Assertions.assertNotNull(firstStopHandle.get())
        Assertions.assertNotNull(secondStopHandle.get())
        Assertions.assertTrue(firstStopHandle.get() === secondStopHandle.get())

        testAsync.assertNoEvent()
    }

}