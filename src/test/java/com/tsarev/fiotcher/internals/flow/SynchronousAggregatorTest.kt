package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.api.EventWithException
import com.tsarev.fiotcher.api.asSuccess
import com.tsarev.fiotcher.dflt.flows.Aggregator
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher

/**
 * Testing [Aggregator].
 */
class SynchronousAggregatorTest {

    private val testSync = SyncTestEvents()

    @Test
    fun `synchronous send two events`() {
        // --- Prepare ---
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)
        val aggregator = Aggregator<String>(
            callerThreadTestExecutor,
            10,
            { testSync.sendEvent("aggregator subscribed") }
        )
        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testSync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testSync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }
        val secondSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testSync.sendEvent("second subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testSync.sendEvent("second ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }

        // --- Test ---
        // Test subscribing.
        publisher.subscribe(aggregator)
        testSync.assertEvent("aggregator subscribed")
        aggregator.subscribe(firstSubscriber)
        aggregator.subscribe(secondSubscriber)
        testSync.assertEvent("first subscribed")
        testSync.assertEvent("second subscribed")

        // Test event processing.
        publisher.submit("item".asSuccess())
        testSync.assertEvent("first item")
        testSync.assertEvent("second item")
    }

    @Test
    fun `synchronous stop after event`() {
        // --- Prepare ---
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)
        val aggregator = Aggregator<String>(
            callerThreadTestExecutor,
            10,
            { testSync.sendEvent("aggregator subscribed") }
        )
        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testSync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testSync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }

        // --- Test ---
        // Test subscribing.
        publisher.subscribe(aggregator)
        testSync.assertEvent("aggregator subscribed")
        aggregator.subscribe(firstSubscriber)
        testSync.assertEvent("first subscribed")

        // Test event processing after stop.
        aggregator.stop()
        publisher.submit("item".asSuccess())
        testSync.assertNoEvent()
        Assertions.assertEquals(0, publisher.numberOfSubscribers)
    }

    @Test
    fun `synchronous stop before subscribe`() {
        // --- Prepare ---
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)
        val aggregator = Aggregator<String>(
            callerThreadTestExecutor,
            10,
            { testSync.sendEvent("aggregator subscribed") }
        )
        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testSync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testSync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }

        // --- Test ---
        // Test subscribing.
        publisher.subscribe(aggregator)
        testSync.assertEvent("aggregator subscribed")

        // Test subscribing after stop.
        aggregator.stop()
        aggregator.subscribe(firstSubscriber)
        testSync.assertEvent("first subscribed")
    }

    @Test
    fun `synchronous NPE on subscribing on null`() {
        // --- Prepare ---
        val aggregator = Aggregator<String>(
            callerThreadTestExecutor,
            10,
            { testSync.sendEvent("aggregator subscribed") }
        )

        // --- Test ---
        Assertions.assertThrows(NullPointerException::class.java) {
            aggregator.subscribe(null)
        }
    }

    @Test
    fun `re enable after subscribed on`() {
        // --- Prepare ---
        val publisher = SubmissionPublisher<EventWithException<String>>(callerThreadTestExecutor, 10)
        val aggregator = Aggregator<String>(
            callerThreadTestExecutor,
            10,
            { testSync.sendEvent("aggregator subscribed") }
        )
        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testSync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testSync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }
        aggregator.stop()

        // --- Test ---
        aggregator.subscribe(firstSubscriber)
        testSync.assertEvent("first subscribed")

        aggregator.onNext("item".asSuccess())
        testSync.assertNoEvent()

        publisher.subscribe(aggregator)
        testSync.assertEvent("aggregator subscribed")

        aggregator.onNext("item".asSuccess())
        testSync.assertEvent("first item")

        testSync.assertNoEvent()
    }

}