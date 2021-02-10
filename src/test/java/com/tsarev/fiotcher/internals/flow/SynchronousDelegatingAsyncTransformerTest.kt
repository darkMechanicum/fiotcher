package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.api.ListenerIsStopped
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingAsyncTransformer
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.SubmissionPublisher

/**
 * Testing [DelegatingAsyncTransformer].
 */
class SynchronousDelegatingAsyncTransformerTest {

    private val testSync = SyncTestEvents()

    @Test
    fun `send two events`() {
        // Prepare.
        val chained = CommonListener<String>(
            { testSync.sendEvent("chained $it") },
            { testSync.sendEvent("chained subscribed") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = callerThreadTestExecutor,
            onSubscribeHandler = { testSync.sendEvent("subscribed") },
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        testSync.assertEvent("chained subscribed")
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
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
        // Prepare.
        val chained = CommonListener<String>(
            { testSync.sendEvent("chained $it") },
            { testSync.sendEvent("chained subscribed") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = callerThreadTestExecutor,
            onSubscribeHandler = { testSync.sendEvent("subscribed") },
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        testSync.assertEvent("chained subscribed")
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        publisher.submit("one".asSuccess())
        testSync.assertEvent("one")
        testSync.assertEvent("chained one")
        listener.stop(true)
        publisher.submit("two".asSuccess())
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop before submit`() {
        // Prepare.
        val chained = CommonListener<String>(
            { testSync.sendEvent("chained $it") },
            { testSync.sendEvent("chained subscribed") }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = callerThreadTestExecutor,
            onSubscribeHandler = { testSync.sendEvent("subscribed") },
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        testSync.assertEvent("chained subscribed")
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        listener.stop(true)
        publisher.submit("one".asSuccess())
        publisher.submit("two".asSuccess())
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop before subscribe`() {
        // Prepare.
        val chained = CommonListener<String>(
            onNextHandler = { },
            onErrorHandler = { testSync.sendEvent(it) }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingAsyncTransformer<String, String, CommonListener<String>>(
            executor = callerThreadTestExecutor,
            maxCapacity = 10,
            chained = chained,
            stoppingExecutor = callerThreadTestExecutor,
            onSubscribeHandler = { testSync.sendEvent("subscribed") },
            transform = { it, publish -> testSync.sendEvent(it); publish(it) },
            handleErrors = null
        )
        listener.stop()
        publisher.subscribe(listener)
        testSync.assertEvent<ListenerIsStopped> {
            Assertions.assertEquals("Cannot subscribe when stopped.", it.message)
        }
    }

}