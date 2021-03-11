package com.tsarev.fiotcher.internals.pool

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.TrackerAlreadyRegistered
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.DefaultPublisherPool
import com.tsarev.fiotcher.dflt.DefaultTrackerPool
import com.tsarev.fiotcher.dflt.push
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.pool.Tracker
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
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
        val defaultPublisherPool =
            DefaultPublisherPool<EventWithException<InitialEventsBunch<String>>>(callerThreadTestExecutor, 256)
        val pool = DefaultTrackerPool(callerThreadTestExecutorService, defaultPublisherPool)
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            override val stopBrake = Brake<Unit>()
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
                stopBrake.push { testSync.sendEvent("tracker stopped"); complete(Unit) }

            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<String>>) -> Unit
            ) = testSync.sendEvent("tracker initialized")
        }

        // --- Test ---
        // Check that tracker start is successful.
        pool.startTracker("some", testTracker, "key")
        testSync.assertEvent("tracker initialized")
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
        val defaultPublisherPool =
            DefaultPublisherPool<EventWithException<InitialEventsBunch<String>>>(callerThreadTestExecutor, 256)
        val pool = DefaultTrackerPool(
            callerThreadTestExecutorService,
            defaultPublisherPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            override val stopBrake = Brake<Unit>()
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
                stopBrake.push { testSync.sendEvent("tracker stopped"); complete(Unit) }

            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<String>>) -> Unit
            ) = testSync.sendEvent("tracker initialized")
        }

        // --- Test ---
        // Check that tracker can't be registered twice.
        pool.startTracker("some", testTracker, "key")
        try {
            pool.startTracker("some", testTracker, "key").get()
            Assertions.fail()
        } catch (cause: ExecutionException) {
            Assertions.assertTrue(cause.cause is TrackerAlreadyRegistered)
        }
    }

    @Test
    fun `synchronous tracker handle stop`() {
        // --- Prepare ---
        val defaultPublisherPool =
            DefaultPublisherPool<EventWithException<InitialEventsBunch<String>>>(callerThreadTestExecutor, 256)
        val pool = DefaultTrackerPool(
            callerThreadTestExecutorService,
            defaultPublisherPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            override val stopBrake = Brake<Unit>()
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
                stopBrake.push { testSync.sendEvent("tracker stopped"); complete(Unit) }

            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<String>>) -> Unit
            ) = testSync.sendEvent("tracker initialized")
        }

        // --- Test ---
        // Check that tracker start is successful.
        val handle = pool.startTracker("some", testTracker, "key")
        testSync.assertEvent("tracker initialized")
        testSync.assertEvent("tracker started")

        // Check that tracker stop is successful.
        handle.get().stop()
        testSync.assertEvent("tracker stopped")

        // Check that tracker can be started again.
        pool.startTracker("some", testTracker, "key")
    }

    @Test
    fun `asynchronous register and listen tracker`() {
        // --- Prepare ---
        val publisherExecutor = acquireExecutor("aggregate", testAsync::sendEvent)
        val trackerExecutor = acquireExecutor("tracker", testAsync::sendEvent)
        val defaultPublisherPool =
            DefaultPublisherPool<EventWithException<InitialEventsBunch<String>>>(publisherExecutor, 256)
        val pool = DefaultTrackerPool(
            trackerExecutor,
            defaultPublisherPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testAsync.sendEvent("tracker started")
            override val stopBrake = Brake<Unit>()
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
                stopBrake.push { thread(start = true) { testAsync.sendEvent("tracker stopped") }; complete(Unit) }

            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<String>>) -> Unit
            ) = testAsync.sendEvent("tracker initialized")
        }
        val key = "key"

        // --- Test ---
        // Check that tracker start is successful.
        pool.startTracker("some", testTracker, key)
        trackerExecutor.activate {
            // Check that tracker is initialized.
            testAsync.assertEvent("tracker start")
            testAsync.assertEvent("tracker initialized")
            testAsync.assertEvent("tracker finished")

            // Check that tracker is launched.
            testAsync.assertEvent("tracker start")
            testAsync.assertEvent("tracker started")
            testAsync.assertEvent("tracker finished")
        }

        // Check that tracker stop is successful.
        pool.stopTracker("some", key).get()
        testAsync.assertEvent("tracker stopped")

        // Check that tracker can be started again.
        pool.startTracker("some", testTracker, key)
        trackerExecutor.activate {
            // Check that tracker is initialized.
            testAsync.assertEvent("tracker start")
            testAsync.assertEvent("tracker initialized")
            testAsync.assertEvent("tracker finished")

            // Check that tracker is launched.
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
        val trackerExecutor = acquireExecutor("tracker", testAsync::sendEvent)
        val defaultPublisherPool =
            DefaultPublisherPool<EventWithException<InitialEventsBunch<String>>>(aggregatorExecutor, 256)
        val pool = DefaultTrackerPool(
            trackerExecutor,
            defaultPublisherPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testSync.sendEvent("tracker started")
            override val stopBrake = Brake<Unit>()
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
                stopBrake.push { testSync.sendEvent("tracker stopped"); complete(Unit) }

            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<String>>) -> Unit
            ) = testSync.sendEvent("tracker initialized")
        }
        val key = "key"

        // --- Test ---
        // Check that tracker start is successful.
        val startHandle = pool.startTracker("some", testTracker, key)
        Assertions.assertFalse(startHandle.isDone)
        startHandle.cancel(true)
        Assertions.assertTrue(startHandle.isDone)

        activateAll {
            // Check that interrupt occurred.
            testAsync.assertEvents(
                "tracker start" to true,
                "tracker finished" to true
            )

            // Test that there are no more events.
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `asynchronous middle cancel tracker start`() {
        // --- Prepare ---
        val publisherExecutor = acquireExecutor("publisher", testAsync::sendEvent)
        val trackerExecutor = acquireExecutor("tracker", testAsync::sendEvent)
        val defaultPublisherPool =
            DefaultPublisherPool<EventWithException<InitialEventsBunch<String>>>(publisherExecutor, 256)
        val pool = DefaultTrackerPool(
            trackerExecutor,
            defaultPublisherPool
        )
        val testTracker = object : Tracker<String>() {
            override fun run() = testAsync.sendEvent("tracker started")
            override val stopBrake = Brake<Unit>()
            override fun doStop(force: Boolean, exception: Throwable?): CompletionStage<*> =
                stopBrake.push { thread(start = true) { testAsync.sendEvent("tracker stopped") }; complete(Unit) }

            override fun doInit(
                executor: Executor,
                sendEvent: (EventWithException<InitialEventsBunch<String>>) -> Unit
            ) = testAsync.sendEvent("tracker initialized")
        }
        val key = "key"

        // --- Test ---
        // Check that tracker partial start is successful.
        val startHandle = pool.startTracker("some", testTracker, key)
        trackerExecutor.activate {
            testAsync.assertEvent("tracker start")
        }

        // Cancel the handle.
        startHandle.cancel(true)

        // Aggregation subscription is interrupted, so no event will be passed to cancel it.
        trackerExecutor.activate {
            // Test that inner tracker registration was interrupted.
            testAsync.assertEvents(
                // Tracker can jump to initialization, but not further.
                "tracker initialized" to false,
                "tracker finished" to false,
                // Tracker stop in essential.
                "tracker stopped" to true
            )
        }

        // Check that all there are no other events.
        activateAll {
            // Allow to finish registration handle.
            testAsync.assertEvents(
                "tracker finished" to false,
            )
            testAsync.assertNoEvent()
        }
    }

}