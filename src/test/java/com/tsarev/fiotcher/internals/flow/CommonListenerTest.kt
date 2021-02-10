package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.api.EventWithException
import com.tsarev.fiotcher.api.ListenerIsStopped
import com.tsarev.fiotcher.api.asSuccess
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.SubmissionPublisher

/**
 * Testing [CommonListener].
 */
class CommonListenerTest {

    private val testSync = SyncTestEvents()

    @Test
    fun `send two events`() {
        // Prepare.
        val listener = CommonListener<String>({ testSync.sendEvent(it) }, { testSync.sendEvent("subscribed") })
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)

        // Test.
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        publisher.submit("one".asSuccess())
        testSync.assertEvent("one")
        publisher.submit("two".asSuccess())
        testSync.assertEvent("two")
        testSync.assertNoEvent()
    }

    @Test
    fun `stop after one submit`() {
        // Prepare.
        val listener = CommonListener<String>({ testSync.sendEvent(it) }, { testSync.sendEvent("subscribed") })
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)

        // Test.
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        publisher.submit("one".asSuccess())
        listener.stop()
        publisher.submit("two".asSuccess())
        testSync.assertEvent("one")
        testSync.assertNoEvent()
    }

    @Test
    fun `stop before submit`() {
        // Prepare.
        val listener = CommonListener<String>({ testSync.sendEvent(it) }, { testSync.sendEvent("subscribed") })
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)

        // Test.
        publisher.subscribe(listener)
        testSync.assertEvent("subscribed")
        listener.stop()
        publisher.submit("one".asSuccess())
        publisher.submit("two".asSuccess())
        testSync.assertNoEvent()
    }

    @Test
    fun `stop before subscribe`() {
        // Prepare.
        val listener = CommonListener<String>(
            onNextHandler = { },
            onErrorHandler = { testSync.sendEvent(it) }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)

        // Test.
        listener.stop()
        publisher.subscribe(listener)
        testSync.assertEvent<ListenerIsStopped> {
            Assertions.assertEquals("Cannot subscribe when stopped.", it.message)
        }
        testSync.assertNoEvent()
    }

}