package com.tsarev.fiotcher.internals.tracker

import com.tsarev.fiotcher.api.tracker.TrackerEventBunch
import com.tsarev.fiotcher.dflt.trackers.FileSystemTracker
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.Flow
import kotlin.concurrent.thread

/**
 * Testing [FileSystemTracker].
 */
class FileSystemTrackerTest {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `test two files altering without debounce`() {
        // --- Prepare ---
        val tracker = FileSystemTracker(debounceTimeoutMs = 0)
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<TrackerEventBunch<File>> {
            override fun onNext(item: TrackerEventBunch<File>?) {
                // extended pause because of [Thread.sleep(2000)] delays below.
                testAsync.sendEvent("files changed")
                item?.events
                    ?.flatMap { bunch -> bunch.event.map { bunch.type to it } }
                    ?.map { it.first to it.second.absolutePath }
                    ?.sortedBy { it.second }
                    ?.forEach { testAsync.sendEvent("file ${it.second} has been ${it.first}") }
            }

            override fun onError(throwable: Throwable?) = run { }
            override fun onComplete() = run { }
            override fun onSubscribe(subscription: Flow.Subscription?) = run { subscription?.request(Long.MAX_VALUE); Unit }
        }
        trackerPublisher.subscribe(subscriber)

        // --- Test ---
        val trackerThread = thread(start = true, isDaemon = true) { tracker.run() }

        // Test creation.
        val someFile = tempDir.createFile("someFile")
        Thread.sleep(100) // Small pause to allow filesystem watcher to give away events.
        val someFile2 = tempDir.createFile("someFile2")
        testAsync.assertEvent("files changed") // Higher timeout
        testAsync.assertEvent("file ${someFile.absolutePath} has been CREATED")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile2.absolutePath} has been CREATED")

        // Test changing.
        someFile.appendText("some text")
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been CHANGED")

        // Test deletion.
        someFile.delete()
        Thread.sleep(100) // Small pause to allow filesystem watcher to give away events.
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been DELETED")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test two files altering with debounce`() {
        // --- Prepare ---
        val tracker = FileSystemTracker(debounceTimeoutMs = 200)
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<TrackerEventBunch<File>> {
            override fun onNext(item: TrackerEventBunch<File>?) {
                // extended pause because of [Thread.sleep(2000)] delays below.
                testAsync.sendEvent("files changed")
                item?.events
                    ?.flatMap { bunch -> bunch.event.map { bunch.type to it } }
                    ?.map { it.first to it.second.absolutePath }
                    ?.sortedBy { it.second }
                    ?.forEach { testAsync.sendEvent("file ${it.second} has been ${it.first}") }
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
        Thread.sleep(100) // Small pause to allow filesystem watcher to give away events.
        val someFile2 = tempDir.createFile("someFile2")
        testAsync.assertEvent("files changed") // Higher timeout
        testAsync.assertEvent("file ${someFile.absolutePath} has been CREATED")
        testAsync.assertEvent("file ${someFile2.absolutePath} has been CREATED")

        // Test deletion debouncing.
        someFile.appendText("some text")
        someFile.delete()
        testAsync.assertEvent("files changed")
        testAsync.assertEvent("file ${someFile.absolutePath} has been CHANGED")
        testAsync.assertEvent("file ${someFile.absolutePath} has been DELETED")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test altering file in nested directory without debounce`() {
        // --- Prepare ---
        val tracker = FileSystemTracker(debounceTimeoutMs = 0, recursive = true)
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<TrackerEventBunch<File>> {
            override fun onNext(item: TrackerEventBunch<File>?) {
                // extended pause because of [Thread.sleep(2000)] delays below.
                testAsync.sendEvent("files changed")
                item?.events
                    ?.flatMap { bunch -> bunch.event.map { bunch.type to it } }
                    ?.map { it.first to it.second.absolutePath }
                    ?.sortedBy { it.second }
                    ?.forEach { testAsync.sendEvent("file ${it.second} has been ${it.first}") }
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
        // TODO This pause signalises that we also do manual registration of file changes and directory changes.
        Thread.sleep(100) // Small pause to allow filesystem watcher to give away events.
        val someInnerDir = someDir.createDirectory("someDir")
        Thread.sleep(100) // Small pause to allow filesystem watcher to give away events.
        val someFile = someInnerDir.createFile("someFile")
        testAsync.assertEvent("files changed") // Higher timeout
        testAsync.assertEvent("file ${someFile.absolutePath} has been CREATED")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test gracefully stop with debounce`() {
        // --- Prepare ---
        val tracker = FileSystemTracker(debounceTimeoutMs = 400, recursive = true)
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<TrackerEventBunch<File>> {
            override fun onNext(item: TrackerEventBunch<File>?) {
                // extended pause because of [Thread.sleep(2000)] delays below.
                testAsync.sendEvent("files changed")
                item?.events
                    ?.flatMap { bunch -> bunch.event.map { bunch.type to it } }
                    ?.map { it.first to it.second.absolutePath }
                    ?.sortedBy { it.second }
                    ?.forEach { testAsync.sendEvent("file ${it.second} has been ${it.first}") }
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
        Thread.sleep(100)
        tracker.stop(false)
        Thread.sleep(100)
        val someFile2 = tempDir.createFile("someFile2")
        val someFile3 = tempDir.createFile("someFile3")
        testAsync.assertEvent("files changed") // Higher timeout
        testAsync.assertEvent("file ${someFile.absolutePath} has been CREATED")
        testAsync.assertEvent("file ${someFile2.absolutePath} has been CREATED")
        testAsync.assertEvent("file ${someFile3.absolutePath} has been CREATED")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test forcibly stop with debounce`() {
        // --- Prepare ---
        val tracker = FileSystemTracker(debounceTimeoutMs = 400, recursive = true)
        val trackerPublisher = tracker.init(tempDir, callerThreadTestExecutor)
        val subscriber = object : Flow.Subscriber<TrackerEventBunch<File>> {
            override fun onNext(item: TrackerEventBunch<File>?) {
                // extended pause because of [Thread.sleep(2000)] delays below.
                testAsync.sendEvent("files changed")
                item?.events
                    ?.flatMap { bunch -> bunch.event.map { bunch.type to it } }
                    ?.map { it.first to it.second.absolutePath }
                    ?.sortedBy { it.second }
                    ?.forEach { testAsync.sendEvent("file ${it.second} has been ${it.first}") }
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
        tracker.stop(true)
        Thread.sleep(200) // Small pause to allow filesystem watcher to give away events.
        tempDir.createFile("someFile2")
        Thread.sleep(200) // Small pause to allow filesystem watcher to give away events.
        tempDir.createFile("someFile3")
        testAsync.assertEvent("files changed") // Higher timeout
        testAsync.assertEvent("file ${someFile.absolutePath} has been CREATED")

        testAsync.assertNoEvent()

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }
}