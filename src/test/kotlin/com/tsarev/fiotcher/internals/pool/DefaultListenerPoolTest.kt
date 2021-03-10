package com.tsarev.fiotcher.internals.pool

import com.tsarev.fiotcher.dflt.DefaultListenerPool
import com.tsarev.fiotcher.dflt.DefaultPublisherPool
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncChainListener
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DefaultListenerPoolTest {

    private val testSync = SyncTestEvents()
    private val testAsync = AsyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `synchronous send single event for registered listener`() {
        // --- Prepare ---
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(callerThreadTestExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val listener = CommonListener<String> { testSync.sendEvent(it) }
        val publisher = defaultPublisherPool.getPublisher(key)
        val listenerHandle = pool.registerListener(listener, key)

        // --- Test ---
        publisher.submit("item".asSuccess())
        testSync.assertEvent("item")

        pool.deRegisterListener(key)
        Assertions.assertTrue(listenerHandle.isStopped)
        Assertions.assertTrue(listener.isStopped)
    }

    @Test
    fun `synchronous send single event for two registered listeners`() {
        // --- Prepare ---
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(callerThreadTestExecutor, 256)
        val key = "key"
        val pool = DefaultListenerPool(defaultPublisherPool)
        val firstListener = CommonListener<String> { testSync.sendEvent("first $it") }
        val secondListener = CommonListener<String> { testSync.sendEvent("second $it") }
        val publisher = defaultPublisherPool.getPublisher(key)

        // --- Test ---
        val firstListenerHandle = pool.registerListener(firstListener, key)
        val secondListenerHandle = pool.registerListener(secondListener, key)
        testSync.assertNoEvent()

        publisher.submit("item".asSuccess())
        testSync.assertEvent("first item")
        testSync.assertEvent("second item")
        testSync.assertNoEvent()

        firstListenerHandle.stop()
        Assertions.assertTrue(firstListenerHandle.isStopped)
        Assertions.assertTrue(firstListener.isStopped)

        publisher.submit("item".asSuccess())
        testSync.assertEvent("second item")
        testSync.assertNoEvent()

        secondListenerHandle.stop()
        Assertions.assertTrue(secondListenerHandle.isStopped)
        Assertions.assertTrue(secondListener.isStopped)

        publisher.submit("item".asSuccess())
        testSync.assertNoEvent()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `synchronous listener double stop`(force: Boolean) {
        // --- Prepare ---
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(callerThreadTestExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)

        // --- Test ---
        val firstHandle = pool.stop(force)
        val secondHandle = pool.stop(force)

        Assertions.assertTrue(firstHandle === secondHandle)
    }

    @Test
    fun `asynchronous single listener register and single event`() {
        // --- Prepare ---
        val listenerExecutor = acquireExecutor("listener executor", testAsync::sendEvent, testAsync::sendEvent)
        val aggregatorExecutor = acquireExecutor("aggregator executor", testAsync::sendEvent, testAsync::sendEvent)
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(aggregatorExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = listenerExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        val publisher = defaultPublisherPool.getPublisher(key)

        // --- Test ---
        // Check chained subscription is ok.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Check listener subscription is ok.
        pool.registerListener(listener, key)
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Check message was received from aggregator.
        publisher.submit("item".asSuccess())
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("item")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Check message was received from listener to chained.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained item")
            testAsync.assertEvent("listener executor finished")
        }

        // Check no more events left.
        activateAll {
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous listener stop gracefully`() {
        // --- Prepare ---
        val listenerExecutor = acquireExecutor("listener executor", testAsync::sendEvent, testAsync::sendEvent)
        val aggregatorExecutor = acquireExecutor("aggregator executor", testAsync::sendEvent, testAsync::sendEvent)
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(aggregatorExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = listenerExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        val publisher = defaultPublisherPool.getPublisher(key)

        // --- Test ---
        // Check chained subscription is ok.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Check listener subscription is ok.
        pool.registerListener(listener, key)
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Check message was received from aggregator.
        publisher.submit("item".asSuccess())
        aggregatorExecutor.activate {
            // Message from aggregator is not processed by listener yet, but will be.
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("item")
            testAsync.assertEvent("aggregator executor finished")
        }

        pool.stop(false)

        // Check that event is processed in the listener.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained item")
            testAsync.assertEvent("listener executor finished")
        }

        // Test that listener executor is being closed.
        listenerExecutor.activate {
            testAsync.assertEvents(
                // First async closing block.
                "listener executor start" to true,
                "listener executor finished" to true,
                // Second async closing block.
                "listener executor start" to true,
                "listener executor finished" to true,
                // Closing publisher block.
                "listener executor start" to false,
                "listener executor finished" to false,
            )
        }

        // Test that no more events appear.
        activateAll {
            testAsync.assertEvents(
                // Allow publisher to process cancel request.
                "aggregator executor start" to false,
                "aggregator executor finished" to false,
            )
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous listener stop forcibly`() {
        // --- Prepare ---
        val listenerExecutor = acquireExecutor(
            name = "listener executor",
            { testAsync.sendEvent("listener executor start") },
            { testAsync.sendEvent("listener executor finished") }
        )
        val aggregatorExecutor = acquireExecutor(
            name = "aggregator executor",
            { testAsync.sendEvent("aggregator executor start") },
            { testAsync.sendEvent("aggregator executor finished") }
        )
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(aggregatorExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val chained = CommonListener<String> { testAsync.sendEvent("chained $it") }
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = listenerExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testAsync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        val publisher = defaultPublisherPool.getPublisher(key)

        // --- Test ---
        // Check chained subscription is ok.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Check listener subscription is ok.
        pool.registerListener(listener, key)
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Check message was received from aggregator.
        publisher.submit("item".asSuccess())
        aggregatorExecutor.activate {
            // Message from aggregator is not processed by listener yet, but will be.
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("item")
            testAsync.assertEvent("aggregator executor finished")
        }

        pool.stop(true)

        // Check that listener is being closed immediately.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
            testAsync.assertNoEvent()
        }

        // Check that listener is unsubscribed from aggregator.
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("aggregator executor finished")
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `synchronous handle register same key after previous listener stop`() {
        // --- Prepare ---
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(callerThreadTestExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val firstListener = CommonListener<String> { testSync.sendEvent(it) }
        val secondListener = CommonListener<String> { testSync.sendEvent(it) }

        // --- Test ---
        val handle = pool.registerListener(firstListener, key)
        handle.stop()

        // Test that first listener accepts no events when stopped.
        firstListener.onNext("item".asSuccess())
        testSync.assertNoEvent()

        // Test that no exception arise when second listener is registered on same key after stopping.
        pool.registerListener(secondListener, key)

        // Test that second listener accepts events after registration.
        secondListener.onNext("item".asSuccess())
        testSync.assertEvent("item")
    }

}