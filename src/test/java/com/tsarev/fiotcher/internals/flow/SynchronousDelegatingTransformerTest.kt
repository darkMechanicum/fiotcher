package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.api.ListenerIsStopped
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingTransformer
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.SubmissionPublisher

/**
 * Testing [DelegatingTransformer].
 */
class SynchronousDelegatingTransformerTest {

    private val testSync = SyncTestEvents()

    @Test
    fun `send two events`() {
        // Prepare.
        val chained = CommonListener<String>(
            { testSync.sendEvent("chained $it") },
            { testSync.sendEvent("chained subscribed") }
        )
        val publisher = SubmissionPublisher<String>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingTransformer<String, String, CommonListener<String>>(
            callerThreadTestExecutor,
            10,
            chained,
            callerThreadTestExecutor,
            { testSync.sendEvent("subscribed") },
            { it, publish -> testSync.sendEvent(it); publish(it) }
        )
        testSync.assertEvent("chained subscribed")
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        publisher.submit("one")
        testSync.assertEvent("one")
        testSync.assertEvent("chained one")
        publisher.submit("two")
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
        val publisher = SubmissionPublisher<String>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingTransformer<String, String, CommonListener<String>>(
            callerThreadTestExecutor,
            10,
            chained,
            callerThreadTestExecutor,
            { testSync.sendEvent("subscribed") },
            { it, publish -> testSync.sendEvent(it); publish(it) }
        )
        testSync.assertEvent("chained subscribed")
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        publisher.submit("one")
        testSync.assertEvent("one")
        testSync.assertEvent("chained one")
        listener.stop(true)
        publisher.submit("two")
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop before submit`() {
        // Prepare.
        val chained = CommonListener<String>(
            { testSync.sendEvent("chained $it") },
            { testSync.sendEvent("chained subscribed") }
        )
        val publisher = SubmissionPublisher<String>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingTransformer<String, String, CommonListener<String>>(
            callerThreadTestExecutor,
            10,
            chained,
            callerThreadTestExecutor,
            { testSync.sendEvent("subscribed") },
            { it, publish -> testSync.sendEvent(it); publish(it) }
        )
        testSync.assertEvent("chained subscribed")
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        listener.stop(true)
        publisher.submit("one")
        publisher.submit("two")
        testSync.assertNoEvent()
    }

    @Test
    fun `synchronous force stop before subscribe`() {
        // Prepare.
        val chained = CommonListener<String>(
            onNextHandler = {  },
            onErrorHandler = { testSync.sendEvent(it) }
        )
        val publisher = SubmissionPublisher<String>(callerThreadTestExecutor, 10)

        // Test.
        val listener = DelegatingTransformer<String, String, CommonListener<String>>(
            callerThreadTestExecutor,
            10,
            chained,
            callerThreadTestExecutor,
            { testSync.sendEvent("subscribed") },
            { it, publish -> testSync.sendEvent(it); publish(it) }
        )
        listener.stop()
        publisher.subscribe(listener)
        testSync.assertEvent<ListenerIsStopped> {
            Assertions.assertEquals("Cannot subscribe when stopped.", it.message)
        }
    }

}