package com.tsarev.fiotcher.internals.pool

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.TrackerAlreadyRegistered
import com.tsarev.fiotcher.api.typedKey
import com.tsarev.fiotcher.dflt.*
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.internal.pool.Tracker
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import kotlin.concurrent.thread

class DefaultTrackerPoolTest {

    private val testSync = SyncTestEvents()

    private val testAsync = AsyncTestEvents()

    @AfterEach
    fun `clear test executors`() {
        TestExecutorRegistry.clear()
    }

    @Test
    fun `synchronous register tracker`() {
        // --- Prepare ---
        val aggregatorPool = DefaultAggregatorPool(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor
        )
        val pool = DefaultTrackerPool<String>(
            callerThreadTestExecutorService,
            callerThreadTestExecutor,
            callerThreadTestExecutorService,
            aggregatorPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            private val brake = Brake<Unit>()
            override val isStopped = brake.isPushed
            override fun doStop(force: Boolean) = brake.push { testSync.sendEvent("tracker stopped"); it.complete(Unit) }
            override fun doInit(executor: Executor): Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                testSync.sendEvent("tracker initialized")
                return Flow.Publisher<EventWithException<InitialEventsBunch<String>>> { testSync.sendEvent("aggregator subscribed") }
            }
        }

        // --- Test ---
        // Check that tracker start is successful.
        pool.startTracker("some", testTracker, "key")
        testSync.assertEvent("tracker initialized")
        testSync.assertEvent("aggregator subscribed")
        testSync.assertEvent("tracker started")

        // Check that tracker stop is successful.
        pool.stopTracker("some", "key")
        testSync.assertEvent("tracker stopped")

        // Check that tracker can be started again.
        pool.startTracker("some", testTracker, "key")
    }

    @Test
    fun `synchronous register tracker twice`() {
        // --- Prepare ---
        val aggregatorPool = DefaultAggregatorPool(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor
        )
        val pool = DefaultTrackerPool<String>(
            callerThreadTestExecutorService,
            callerThreadTestExecutor,
            callerThreadTestExecutorService,
            aggregatorPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            private val brake = Brake<Unit>()
            override val isStopped = brake.isPushed
            override fun doStop(force: Boolean) = brake.push { testSync.sendEvent("tracker stopped"); it.complete(Unit) }
            override fun doInit(executor: Executor): Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                testSync.sendEvent("tracker initialized")
                return Flow.Publisher<EventWithException<InitialEventsBunch<String>>> { testSync.sendEvent("aggregator subscribed") }
            }
        }

        // --- Test ---
        // Check that tracker can't be registered twice.
        pool.startTracker("some", testTracker, "key")
        Assertions.assertThrows(TrackerAlreadyRegistered::class.java) {
            pool.startTracker("some", testTracker, "key")
        }
    }

    @Test
    fun `synchronous tracker handle stop`() {
        // --- Prepare ---
        val aggregatorPool = DefaultAggregatorPool(
            100,
            callerThreadTestExecutor,
            callerThreadTestExecutor
        )
        val pool = DefaultTrackerPool<String>(
            callerThreadTestExecutorService,
            callerThreadTestExecutor,
            callerThreadTestExecutorService,
            aggregatorPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            private val brake = Brake<Unit>()
            override val isStopped = brake.isPushed
            override fun doStop(force: Boolean) = brake.push { testSync.sendEvent("tracker stopped"); it.complete(Unit) }
            override fun doInit(executor: Executor): Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                testSync.sendEvent("tracker initialized")
                return Flow.Publisher<EventWithException<InitialEventsBunch<String>>> { testSync.sendEvent("aggregator subscribed") }
            }
        }

        // --- Test ---
        // Check that tracker start is successful.
        val handle = pool.startTracker("some", testTracker, "key")
        testSync.assertEvent("tracker initialized")
        testSync.assertEvent("aggregator subscribed")
        testSync.assertEvent("tracker started")

        // Check that tracker stop is successful.
        handle.thenAccept { it.stop() }
        testSync.assertEvent("tracker stopped")

        // Check that tracker can be started again.
        pool.startTracker("some", testTracker, "key")
    }

    @Test
    fun `asynchronous register and listen tracker`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor("aggregate", testAsync::sendEvent)
        val queueExecutor = acquireExecutor("queue", testAsync::sendEvent)
        val registrationExecutor = acquireExecutor("registration", testAsync::sendEvent)
        val trackerExecutor = acquireExecutor("tracker", testAsync::sendEvent)
        val stoppingExecutor = acquireExecutor("stopping", testAsync::sendEvent)
        val listener = CommonListener<InitialEventsBunch<String>>(
            onNextHandler = { testAsync.sendEvent(it.first()) }
        )
        val aggregatorPool = DefaultAggregatorPool(
            100,
            aggregatorExecutor,
            stoppingExecutor
        )
        val pool = DefaultTrackerPool<String>(
            trackerExecutor,
            queueExecutor,
            registrationExecutor,
            aggregatorPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testAsync.sendEvent("tracker started")
            private val brake = Brake<Unit>()
            override val isStopped = brake.isPushed
            lateinit var subscriber: Flow.Subscriber<in EventWithException<InitialEventsBunch<String>>>
            override fun doStop(force: Boolean) = brake.push {
                thread(start = true) { testAsync.sendEvent("tracker stopped") }; it.complete(Unit)
            }

            override fun doInit(executor: Executor): Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                testAsync.sendEvent("tracker initialized")
                return Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                    testAsync.sendEvent("aggregator subscribed")
                    subscriber = it
                    subscriber.onSubscribe(object : Flow.Subscription {
                        override fun request(n: Long) = run {}
                        override fun cancel() = run {}
                    })
                }
            }

            fun sendEvent(event: String) = subscriber.onNext(InitialEventsBunch(listOf(event)).asSuccess())
        }
        val key = "key"
        val keyTyped = "key".typedKey<InitialEventsBunch<String>>()
        aggregatorPool.getAggregator(keyTyped).subscribe(listener)
        // Allow listener to be linked to aggregator.
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregate start")
            testAsync.assertEvent("aggregate finished")
        }

        // --- Test ---
        // Check that tracker start is successful.
        pool.startTracker("some", testTracker, key)
        registrationExecutor.activate {
            testAsync.assertEvent("registration start")
            testAsync.assertEvent("tracker initialized")
            testAsync.assertEvent("aggregator subscribed")
            testAsync.assertEvent("registration finished")
        }

        // Check that tracker is launched.
        trackerExecutor.activate {
            testAsync.assertEvent("tracker start")
            testAsync.assertEvent("tracker started")
            testAsync.assertEvent("tracker finished")
        }

        // Check that events are passed.
        testTracker.sendEvent("event")
        aggregatorExecutor.activate {
            testAsync.assertEvent("aggregate start")
            testAsync.assertEvent("event")
            testAsync.assertEvent("aggregate finished")
        }

        // Check that tracker stop is successful.
        pool.stopTracker("some", key)
        testAsync.assertEvent("tracker stopped")

        // Check that tracker can be started again.
        pool.startTracker("some", testTracker, key)
        registrationExecutor.activate {
            testAsync.assertEvent("registration start")
            testAsync.assertEvent("tracker initialized")
            testAsync.assertEvent("aggregator subscribed")
            testAsync.assertEvent("registration finished")
        }

        // Check that tracker is launched.
        trackerExecutor.activate {
            testAsync.assertEvent("tracker start")
            testAsync.assertEvent("tracker started")
            testAsync.assertEvent("tracker finished")
        }

        testAsync.assertNoEvent()
    }

    @Test
    fun `asynchronous early cancel tracker start`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor("aggregate", testAsync::sendEvent)
        val queueExecutor = acquireExecutor("queue", testAsync::sendEvent)
        val registrationExecutor = acquireExecutor("registration", testAsync::sendEvent)
        val trackerExecutor = acquireExecutor("tracker", testAsync::sendEvent)
        val stoppingExecutor = acquireExecutor("stopping", testAsync::sendEvent)
        val aggregatorPool = DefaultAggregatorPool(
            100,
            aggregatorExecutor,
            stoppingExecutor
        )
        val pool = DefaultTrackerPool<String>(
            trackerExecutor,
            queueExecutor,
            registrationExecutor,
            aggregatorPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testAsync.sendEvent("tracker started")
            private val brake = Brake<Unit>()
            override val isStopped = brake.isPushed
            override fun doStop(force: Boolean) = brake.push {
                thread(start = true) { testAsync.sendEvent("tracker stopped") }; it.complete(Unit)
            }

            override fun doInit(executor: Executor): Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                testAsync.sendEvent("tracker initialized")
                return Flow.Publisher<EventWithException<InitialEventsBunch<String>>> { testAsync.sendEvent("aggregator subscribed") }
            }
        }
        val key = "key"

        // --- Test ---
        // Check that tracker start is successful.
        val startHandle = pool.startTracker("some", testTracker, key)
        Assertions.assertFalse(startHandle.isDone)
        startHandle.cancel(true)
        Assertions.assertTrue(startHandle.isDone)

        // Check that interrupt occurred.
        registrationExecutor.activate {
            testAsync.assertEvent("registration start")
            testAsync.assertEvent("registration finished")
        }

        // Test that there are no more events.
        activateAll {
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous middle cancel tracker start`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor("aggregate", testAsync::sendEvent)
        val queueExecutor = acquireExecutor("queue", testAsync::sendEvent)
        val registrationExecutor = acquireExecutor("registration", testAsync::sendEvent)
        val trackerExecutor = acquireExecutor("tracker", testAsync::sendEvent)
        val stoppingExecutor = acquireExecutor("stopping", testAsync::sendEvent)
        val aggregatorPool = DefaultAggregatorPool(
            100,
            aggregatorExecutor,
            stoppingExecutor
        )
        val pool = DefaultTrackerPool<String>(
            trackerExecutor,
            queueExecutor,
            registrationExecutor,
            aggregatorPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = if (!isStopped) testAsync.sendEvent("tracker started") else Unit
            private val brake = Brake<Unit>()
            override val isStopped = brake.isPushed
            override fun doStop(force: Boolean) = brake.push {
                it.complete(Unit); testAsync.sendEvent("tracker stopped")
            }

            override fun doInit(executor: Executor): Flow.Publisher<EventWithException<InitialEventsBunch<String>>> {
                testAsync.sendEvent("tracker initialized")
                return Flow.Publisher<EventWithException<InitialEventsBunch<String>>> { testAsync.sendEvent("aggregator subscribed") }
            }
        }
        val key = "key"

        // --- Test ---
        // Check that tracker partial start is successful.
        val startHandle = pool.startTracker("some", testTracker, key)
        registrationExecutor.activate {
            testAsync.assertEvent("registration start")
            // Increase registration executor activation time to pass "tracker initialized" event.
            Thread.sleep(defaultTestAsyncAssertTimeoutMs / 2)
        }
        // Do not pass to subscription by syncing on event outside of executor active block.
        testAsync.assertEvent("tracker initialized")

        // Cancel the handle.
        startHandle.cancel(true)

        // Aggregation subscription is interrupted, so no event will be passed to cancel it.
        registrationExecutor.activate {
            testAsync.assertEvents(
                "aggregator subscribed" required false, // Subscription event can or cannot be in time.
                "tracker stopped" required true // Stop event must occur.
            )
        }

        // Check that all there are no other events.
        activateAll {
            testAsync.assertNoEvent()
        }
    }

}