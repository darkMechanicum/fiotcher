package com.tsarev.fiotcher.internals.tracker

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.dflt.trackers.NaiveFileTracker
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.Flow
import kotlin.concurrent.thread

/**
 * Testing [NaiveFileTracker].
 */
class NaiveFileTrackerTest {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `test two files altering`() {
        // --- Prepare ---
        val tracker = NaiveFileTracker()
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<EventWithException<InitialEventsBunch<File>>> {
            override fun onNext(item: EventWithException<InitialEventsBunch<File>>) {
                testAsync.sendEvent("files changed")
                item.event
                    ?.map { it.absolutePath }
                    ?.sorted()
                    ?.forEach { testAsync.sendEvent("file $it has been changed") }
            }

            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
            override fun onSubscribe(subscription: Flow.Subscription?) =
                run { subscription?.request(Long.MAX_VALUE); Unit }
        }
        trackerPublisher.subscribe(subscriber)

        // --- Test ---
        val trackerThread = thread(start = true, isDaemon = true) { tracker.run() }

        // Test creation.
        val someFile = tempDir.createFile("someFile")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been changed")

        val someFile2 = tempDir.createFile("someFile2")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile2.absolutePath} has been changed")

        // Test changing.
        someFile.appendText("some text")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been changed")

        // Test deletion.
        someFile.delete()
        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test altering file in nested directory`() {
        // --- Prepare ---
        val tracker = NaiveFileTracker()
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<EventWithException<InitialEventsBunch<File>>> {
            override fun onNext(item: EventWithException<InitialEventsBunch<File>>) {
                testAsync.sendEvent("files changed")
                item.event
                    ?.map { it.absolutePath }
                    ?.sorted()
                    ?.forEach { testAsync.sendEvent("file $it has been changed") }
            }

            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
            override fun onSubscribe(subscription: Flow.Subscription?) = run {
                subscription?.request(100); Unit
            }
        }
        trackerPublisher.subscribe(subscriber)

        // --- Test ---
        val trackerThread = thread(start = true, isDaemon = true) { tracker.run() }

        // Test file in the nested directories creation.
        val someDir = tempDir.createDirectory("someDir")
        val someInnerDir = someDir.createDirectory("someDir")
        val someFile = someInnerDir.createFile("someFile")
        testAsync.assertEvent("files changed") // Higher timeout
        testAsync.assertEvent("file ${someFile.absolutePath} has been changed")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test gracefully stop`() {
        // --- Prepare ---
        val tracker = NaiveFileTracker()
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<EventWithException<InitialEventsBunch<File>>> {
            override fun onNext(item: EventWithException<InitialEventsBunch<File>>) {
                testAsync.sendEvent("files changed")
                item.event
                    ?.map { it.absolutePath }
                    ?.sorted()
                    ?.forEach { testAsync.sendEvent("file $it has been changed") }
            }

            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
            override fun onSubscribe(subscription: Flow.Subscription?) = run {
                subscription?.request(100); Unit
            }
        }
        trackerPublisher.subscribe(subscriber)

        // --- Test ---
        val trackerThread = thread(start = true, isDaemon = true) { tracker.run() }

        // Test creation.
        val someFile = tempDir.createFile("someFile")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been changed")
        tracker.stop(false)
        val someFile2 = tempDir.createFile("someFile2")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile2.absolutePath} has been changed")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test forcibly stop`() {
        // --- Prepare ---
        val tracker = NaiveFileTracker()
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<EventWithException<InitialEventsBunch<File>>> {
            override fun onNext(item: EventWithException<InitialEventsBunch<File>>) {
                testAsync.sendEvent("files changed")
                item.event
                    ?.map { it.absolutePath }
                    ?.sorted()
                    ?.forEach { testAsync.sendEvent("file $it has been changed") }
            }

            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
            override fun onSubscribe(subscription: Flow.Subscription?) = run {
                subscription?.request(100); Unit
            }
        }
        trackerPublisher.subscribe(subscriber)

        // --- Test ---
        val trackerThread = thread(start = true, isDaemon = true) { tracker.run() }

        // Test creation.
        val someFile = tempDir.createFile("someFile")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been changed")

        // Check that no events are generated after forcible stop.
        tracker.stop(true)
        tempDir.createFile("someFile2")
        tempDir.createFile("someFile3")
        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }
}