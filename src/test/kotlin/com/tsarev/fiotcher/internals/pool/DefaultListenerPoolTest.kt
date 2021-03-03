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

        // --- Test ---
        val listenerHandle = pool.registerListener(listener, key)
        testSync.assertEvent("subscribed")

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
        testSync.assertEvent("first subscribed")
        val secondListenerHandle = pool.registerListener(secondListener, key)
        testSync.assertEvent("second subscribed")
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
        val chained = CommonListener<String> { testSync.sendEvent(it) }
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
            testAsync.assertEvent("chained subscribed")
            testAsync.assertEvent("listener executor finished")
        }

        // Check listener subscription is ok.
        pool.registerListener(listener, key)
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("subscribed")
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
        listenerExecutor.activate {
            testAsync.assertNoEvent()
        }
        aggregatorExecutor.activate {
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous listener stop gracefully`() {
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
        val listenerStoppingExecutor = acquireExecutor(
            name = "listener stopping executor",
            { testAsync.sendEvent("listener stopping executor start") },
            { testAsync.sendEvent("listener stopping executor finished") }
        )
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(aggregatorExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val chained = CommonListener<String> { testSync.sendEvent(it) }
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
            testAsync.assertEvent("chained subscribed")
            testAsync.assertEvent("listener executor finished")
        }

        // Check listener subscription is ok.
        pool.registerListener(listener, key)
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("subscribed")
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
        listenerStoppingExecutor.activate {
            testAsync.assertEvent("listener stopping executor start")
        }

        // Check that event is processed in the listener.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("chained item")
            testAsync.assertEvent("listener executor finished")
        }

        // Check that listener is stopped.
        listenerStoppingExecutor.activate {
            testAsync.assertEvent("listener stopping executor finished")
        }

        // Check that listener is unsubscribed from aggregator.
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Test that listener executor is being closed.
        listenerExecutor.activate {
            testAsync.assertEvent("listener executor start")
            testAsync.assertEvent("listener executor finished")
        }

        // Test that no more events appear at listener executor.
        listenerExecutor.activate {
            testAsync.assertNoEvent()
        }

        // Test that no more events appear at listener stopping executor.
        listenerStoppingExecutor.activate {
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
        val listenerStoppingExecutor = acquireExecutor(
            name = "listener stopping executor",
            { testAsync.sendEvent("listener stopping executor start") },
            { testAsync.sendEvent("listener stopping executor finished") }
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
            testAsync.assertEvent("chained subscribed")
            testAsync.assertEvent("listener executor finished")
        }

        // Check listener subscription is ok.
        pool.registerListener(listener, key)
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor start")
            testAsync.assertEvent("subscribed")
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

        // Check that no thread is used to await for unprocessed items.
        listenerStoppingExecutor.activate {
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
    fun `synchronous listener handle stopping`() {
        // --- Prepare ---
        val key = "key"
        val defaultPublisherPool = DefaultPublisherPool<EventWithException<String>>(callerThreadTestExecutor, 256)
        val pool = DefaultListenerPool(defaultPublisherPool)
        val firstListener = CommonListener<String> { }
        val secondListener = CommonListener<String> { }

        // --- Test ---
        val handle = pool.registerListener(firstListener, key)
        handle.stop()
        testSync.assertEvent("canceled")
        pool.registerListener(secondListener, key)
        testSync.assertEvent("subscribed")
    }

}