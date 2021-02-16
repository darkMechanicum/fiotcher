package com.tsarev.fiotcher.internals.pool

import com.tsarev.fiotcher.api.typedKey
import com.tsarev.fiotcher.dflt.DefaultAggregatorPool
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.Flow

class DefaultAggregatorPoolTest {

    private val asyncTest = AsyncTestEvents()

    @Test
    fun `synchronous get same aggregator two times`() {
        // --- Prepare ---
        val pool = DefaultAggregatorPool(100, callerThreadTestExecutor, callerThreadTestExecutor)
        val first = pool.getAggregator("key".typedKey(Int::class))
        val second = pool.getAggregator("key".typedKey(Int::class))

        // --- Test ---
        Assertions.assertTrue(first === second)
    }

    @Test
    fun `synchronous get different by key aggregators two times`() {
        // --- Prepare ---
        val pool = DefaultAggregatorPool(100, callerThreadTestExecutor, callerThreadTestExecutor)
        val first = pool.getAggregator("first".typedKey(Int::class))
        val second = pool.getAggregator("second".typedKey(Int::class))

        // --- Test ---
        Assertions.assertTrue(first !== second)
    }

    @Test
    fun `synchronous get different by type aggregators two times`() {
        // --- Prepare ---
        val pool = DefaultAggregatorPool(100, callerThreadTestExecutor, callerThreadTestExecutor)
        val first = pool.getAggregator("key".typedKey(Int::class))
        val second = pool.getAggregator("key".typedKey(Long::class))

        // --- Test ---
        Assertions.assertTrue(first !== second)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `synchronous stop aggregator pool test`(force: Boolean) {
        // --- Prepare ---
        val pool = DefaultAggregatorPool(100, callerThreadTestExecutor, callerThreadTestExecutor)
        val first = pool.getAggregator("key".typedKey(Int::class))
        val second = pool.getAggregator("key".typedKey(Int::class))

        // --- Test ---
        val handle = pool.stop(force)
        Assertions.assertTrue(first.isStopped)
        Assertions.assertTrue(second.isStopped)
        Assertions.assertTrue(handle.isDone)
    }

    @Test
    fun `asynchronous force stop aggregator pool test`() {
        // --- Prepare ---
        val subscription = object : Flow.Subscription {
            override fun request(n: Long) = Unit
            override fun cancel() = asyncTest.sendEvent("cancel")
        }
        val executor = acquireExecutor(
            beforeStart = { asyncTest.sendEvent("start") },
            afterCompletion = { asyncTest.sendEvent("end") },
        )
        val pool = DefaultAggregatorPool(100, callerThreadTestExecutor, executor)
        val first = pool.getAggregator("key".typedKey(Int::class))
        val second = pool.getAggregator("key".typedKey(Int::class))
        first.onSubscribe(subscription)

        // --- Test ---
        val handle = pool.stop(true)

        Assertions.assertTrue(first.isStopped)
        Assertions.assertTrue(second.isStopped)
        Assertions.assertTrue(handle.isDone)

        executor.activate {
            asyncTest.assertEvent("start")
            asyncTest.assertEvent("cancel")
            asyncTest.assertEvent("end")
        }
    }

    @Test
    fun `asynchronous graceful stop aggregator pool test`() {
        // --- Prepare ---
        val subscription = object : Flow.Subscription {
            override fun request(n: Long) = Unit
            override fun cancel() = asyncTest.sendEvent("cancel")
        }
        val executor = acquireExecutor(
            beforeStart = { asyncTest.sendEvent("start") },
            afterCompletion = { asyncTest.sendEvent("end") },
        )
        val pool = DefaultAggregatorPool(100, callerThreadTestExecutor, executor)
        val first = pool.getAggregator("key".typedKey(Int::class))
        val second = pool.getAggregator("key".typedKey(Int::class))
        first.onSubscribe(subscription)

        // --- Test ---
        val handle = pool.stop(false)

        Assertions.assertTrue(first.isStopped)
        Assertions.assertTrue(second.isStopped)
        Assertions.assertTrue(!handle.isDone)

        executor.activate {
            asyncTest.assertEvent("start")
            asyncTest.assertEvent("cancel")
            asyncTest.assertEvent("end")
        }

        Assertions.assertTrue(handle.isDone)

        asyncTest.assertNoEvent()
    }

}