package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncChainListener
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.SubmissionPublisher

/**
 * Testing [DelegatingAsyncTransformer].
 */
class SynchronousDelegatingAsyncTransformerTest {

    private val testSync = SyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `send two events`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testSync.sendEvent("chained $it") }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // --- Test ---
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        publisher.subscribe(listener)
        publisher.submit("one".asSuccess())
        testSync.assertEvent("one")
        testSync.assertEvent("chained one")
        publisher.submit("two".asSuccess())
        testSync.assertEvent("two")
        testSync.assertEvent("chained two")
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop after one submit`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testSync.sendEvent("chained $it") }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // --- Test ---
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        publisher.subscribe(listener)
        publisher.submit("one".asSuccess())
        testSync.assertEvent("one")
        testSync.assertEvent("chained one")
        listener.stop(true)
        publisher.submit("two".asSuccess())
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop before submit`() {
        // --- Prepare ---
        val chained = CommonListener<String> { testSync.sendEvent("chained $it") }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // --- Test ---
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        publisher.subscribe(listener)
        listener.stop(true)
        publisher.submit("one".asSuccess())
        publisher.submit("two".asSuccess())
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop before subscribe`() {
        // --- Prepare ---
        val chained = CommonListener<String> { }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // --- Test ---
        val listener = DelegatingAsyncChainListener<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        listener.stop()
        publisher.subscribe(listener)
        publisher.submit("one".asSuccess())
        testSync.assertNoEvent()
    }

}