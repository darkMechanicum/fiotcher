package com.tsarev.fiotcher.internals.flow

import com.tsarev.fiotcher.dflt.flows.Aggregator
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Testing [Aggregator].
 */
class AsynchronousAggregatorTest {

    private val testAsync = AsyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `asynchronous send two events`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor(
            beforeStart = { testAsync.sendEvent("aggregator executor started") },
            afterCompletion = { testAsync.sendEvent("aggregator executor finished") },
        )
        val publisherExecutor = acquireExecutor(
            name = "publisher executor",
            beforeStart = { testAsync.sendEvent("publisher executor started") },
            afterCompletion = { testAsync.sendEvent("publisher executor finished") },
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)
        val aggregator = Aggregator<String>(
            aggregatorExecutor,
            10,
            { testAsync.sendEvent("aggregator subscribed") }
        )

        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testAsync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testAsync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }
        val secondSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testAsync.sendEvent("second subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testAsync.sendEvent("second ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }

        // --- Test ---
        // Test subscribing on other.
        publisherExecutor.activate {
            publisher.subscribe(aggregator)
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("aggregator subscribed")
            testAsync.assertEvent("publisher executor finished")

        }

        // Test subscribing listeners.
        aggregatorExecutor.activate {
            aggregator.subscribe(firstSubscriber)
            aggregator.subscribe(secondSubscriber)
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("first subscribed")
            testAsync.assertEvent("aggregator executor finished")
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("second subscribed")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Test sending events.
        publisherExecutor.activate {
            publisher.submit("item".asSuccess())
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("publisher executor finished")
        }
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("first item")
            testAsync.assertEvent("aggregator executor finished")
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("second item")
            testAsync.assertEvent("aggregator executor finished")
        }

        testAsync.assertNoEvent()
    }

    @Test
    fun `asynchronous stop after event`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor(
            beforeStart = { testAsync.sendEvent("aggregator executor started") },
            afterCompletion = { testAsync.sendEvent("aggregator executor finished") },
        )
        val publisherExecutor = acquireExecutor(
            name = "publisher executor",
            beforeStart = { testAsync.sendEvent("publisher executor started") },
            afterCompletion = { testAsync.sendEvent("publisher executor finished") },
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)
        val aggregator = Aggregator<String>(
            aggregatorExecutor,
            10,
            { testAsync.sendEvent("aggregator subscribed") }
        )

        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testAsync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testAsync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }

        // --- Test ---
        // Test subscribing on other.
        publisherExecutor.activate {
            publisher.subscribe(aggregator)
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("aggregator subscribed")
            testAsync.assertEvent("publisher executor finished")
        }

        // Test subscribing listeners.
        aggregatorExecutor.activate {
            aggregator.subscribe(firstSubscriber)
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("first subscribed")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Test stop after non processed event.
        publisherExecutor.activate {
            publisher.submit("item".asSuccess())
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("publisher executor finished")
        }
        aggregator.stop()
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("first item")
            testAsync.assertEvent("aggregator executor finished")
        }
        testAsync.assertNoEvent()
        publisherExecutor.activate {
            // These are stopping task executions.
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("publisher executor finished")
        }
        Assertions.assertEquals(0, publisher.numberOfSubscribers)
    }

    @Test
    fun `asynchronous stop before event`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor(
            beforeStart = { testAsync.sendEvent("aggregator executor started") },
            afterCompletion = { testAsync.sendEvent("aggregator executor finished") },
        )
        val publisherExecutor = acquireExecutor(
            name = "publisher executor",
            beforeStart = { testAsync.sendEvent("publisher executor started") },
            afterCompletion = { testAsync.sendEvent("publisher executor finished") },
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)
        val aggregator = Aggregator<String>(
            aggregatorExecutor,
            10,
            { testAsync.sendEvent("aggregator subscribed") }
        )

        val firstSubscriber = object : Flow.Subscriber<EventWithException<String>> {
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(1); testAsync.sendEvent("first subscribed") }

            override fun onNext(item: EventWithException<String>) = run { testAsync.sendEvent("first ${item.event}") }
            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
        }

        // --- Test ---
        // Test subscribing on other.
        publisherExecutor.activate {
            publisher.subscribe(aggregator)
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("aggregator subscribed")
            testAsync.assertEvent("publisher executor finished")
        }

        // Test subscribing listeners.
        aggregatorExecutor.activate {
            aggregator.subscribe(firstSubscriber)
            testAsync.assertEvent("aggregator executor started")
            testAsync.assertEvent("first subscribed")
            testAsync.assertEvent("aggregator executor finished")
        }

        // Test stop after non processed event.
        aggregator.stop()
        publisherExecutor.activate {
            // These are stopping task executions.
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("publisher executor finished")
        }
        publisher.submit("item".asSuccess())
        publisherExecutor.activate {
            // no-op, just allowing publisher to end event to aggregator.
        }
        testAsync.assertNoEvent()
        Assertions.assertEquals(0, publisher.numberOfSubscribers)
    }

    @Test
    fun `asynchronous double stop`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor(
            beforeStart = { testAsync.sendEvent("aggregator executor started") },
            afterCompletion = { testAsync.sendEvent("aggregator executor finished") },
        )
        val publisherExecutor = acquireExecutor(
            name = "publisher executor",
            beforeStart = { testAsync.sendEvent("publisher executor started") },
            afterCompletion = { testAsync.sendEvent("publisher executor finished") },
        )
        val publisher = SubmissionPublisher<EventWithException<String>>(publisherExecutor, 10)
        val aggregator = Aggregator<String>(
            aggregatorExecutor,
            10,
            { testAsync.sendEvent("aggregator subscribed") }
        )

        // --- Test ---
        // Test subscribing on other.
        publisherExecutor.activate {
            publisher.subscribe(aggregator)
            testAsync.assertEvent("publisher executor started")
            testAsync.assertEvent("aggregator subscribed")
            testAsync.assertEvent("publisher executor finished")
        }

        // Test double stop.
        val firstStopHandle = AtomicReference<CompletableFuture<*>>()
        thread { firstStopHandle.set(aggregator.stop()) }.join()

        val secondStopHandle = AtomicReference<CompletableFuture<*>>()
        thread { secondStopHandle.set(aggregator.stop()) }.join()

        // Stop handles must be exactly the same.
        Assertions.assertNotNull(firstStopHandle.get())
        Assertions.assertNotNull(secondStopHandle.get())
        Assertions.assertTrue(firstStopHandle.get() === secondStopHandle.get())

        testAsync.assertNoEvent()
    }

}