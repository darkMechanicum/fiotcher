package com.tsarev.fiotcher.internals.pool

import com.tsarev.fiotcher.dflt.DefaultAggregatorPool
import com.tsarev.fiotcher.dflt.DefaultWayStation
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncTransformer
import com.tsarev.fiotcher.dflt.flows.DelegatingSyncTransformer
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DefaultWayStationTest {

    private val testSync = SyncTestEvents()
    private val testAsync = AsyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `create common listener`() {
        // --- Prepare ---
        val aggregatorPool = DefaultAggregatorPool(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor
        )
        val wayStation = DefaultWayStation(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor,
            aggregatorPool
        )

        // --- Test ---
        val listener = with(wayStation) {
            createCommonListener<String> { testSync.sendEvent(it) }
        }
        Assertions.assertTrue(listener is CommonListener<*>)
        listener.onNext("event".asSuccess())
        testSync.assertEvent("event")
    }

    @Test
    fun `create simple sync chain`() {
        // --- Prepare ---
        val aggregatorPool = DefaultAggregatorPool(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor
        )
        val wayStation = DefaultWayStation(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor,
            aggregatorPool
        )

        // --- Test ---
        val listener = with(wayStation) {
            createCommonListener<String> { testSync.sendEvent(it) }
                .syncDelegateFrom<String, String> { it, publish ->
                    testSync.sendEvent("transformer")
                    publish(it)
                }
        }
        Assertions.assertTrue(listener is DelegatingSyncTransformer<*, *>)
        val castedListener = listener as DelegatingSyncTransformer<String, String>
        castedListener.onNext("event".asSuccess())
        testSync.assertEvent("transformer")
        testSync.assertEvent("event")
    }

    @Test
    fun `synchronous create simple async chain`() {
        // --- Prepare ---
        val aggregatorPool = DefaultAggregatorPool(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor
        )
        val wayStation = DefaultWayStation(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor,
            aggregatorPool
        )

        // --- Test ---
        val listener = with(wayStation) {
            createCommonListener<String> { testSync.sendEvent(it) }
                .asyncDelegateFrom<String, String> { it, publish ->
                    testSync.sendEvent("transformer")
                    publish(it)
                }
        }
        Assertions.assertTrue(listener is DelegatingAsyncTransformer<*, *, *>)
        val castedListener = listener as DelegatingAsyncTransformer<String, String, *>
        castedListener.onNext("event".asSuccess())
        testSync.assertEvent("transformer")
        testSync.assertEvent("event")
    }

    @Test
    fun `asynchronous create simple async chain`() {
        // --- Prepare ---
        val queueExecutor = acquireExecutor("queue", testAsync::sendEvent, testAsync::sendEvent)
        val aggregatorExecutor = acquireExecutor { }
        val stoppingExecutor = acquireExecutor { }
        val aggregatorPool = DefaultAggregatorPool(
            100,
            aggregatorExecutor,
            stoppingExecutor
        )
        val wayStation = DefaultWayStation(
            100,
            queueExecutor,
            stoppingExecutor,
            aggregatorPool
        )

        // --- Test ---
        val listener = with(wayStation) {
            createCommonListener<String> { testAsync.sendEvent(it) }
                .asyncDelegateFrom<String, String> { it, publish ->
                    publish(it)
                }
        }
        Assertions.assertTrue(listener is DelegatingAsyncTransformer<*, *, *>)
        val castedListener = listener as DelegatingAsyncTransformer<String, String, *>
        castedListener.onNext("event".asSuccess())

        // Check that flow is asynchronous.
        queueExecutor.activate {
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("event")
            testAsync.assertEvent("queue finished")
        }

        activateAll {
            testAsync.assertNoEvent()
        }
    }

}