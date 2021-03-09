package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.api.PoolIsStopped
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
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
        // --- Prepare ---
        val listener = CommonListener<String> { testSync.sendEvent(it) }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)
        publisher.subscribe(listener)

        // --- Test ---
        publisher.submit("one".asSuccess())
        testSync.assertEvent("one")
        publisher.submit("two".asSuccess())
        testSync.assertEvent("two")
        testSync.assertNoEvent()
    }

    @Test
    fun `stop after one submit`() {
        // --- Prepare ---
        val listener = CommonListener<String> { testSync.sendEvent(it) }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)
        publisher.subscribe(listener)

        // --- Test ---
        publisher.submit("one".asSuccess())
        listener.stop()
        publisher.submit("two".asSuccess())
        testSync.assertEvent("one")
        testSync.assertNoEvent()
    }

    @Test
    fun `stop before submit`() {
        // --- Prepare ---
        val listener = CommonListener<String> { testSync.sendEvent(it) }
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)
        publisher.subscribe(listener)

        // --- Test ---
        listener.stop()
        publisher.submit("one".asSuccess())
        publisher.submit("two".asSuccess())
        testSync.assertNoEvent()
    }

    @Test
    fun `stop before subscribe`() {
        // --- Prepare ---
        val listener = CommonListener<String>(
            handleErrors = { testSync.sendEvent(it); null },
            listener = { }
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 100)

        // --- Test ---
        listener.stop()
        publisher.subscribe(listener)
        publisher.submit("one".asSuccess())
        testSync.assertNoEvent()
    }

}