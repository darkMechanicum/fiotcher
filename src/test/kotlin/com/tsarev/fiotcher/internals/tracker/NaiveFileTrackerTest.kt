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
        val tracker = NaiveFileTracker(iterationMinMillis = 40)
        tracker.init(tempDir, callerThreadTestExecutor) {}

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

        // There is no deletion test yet since test starts blinking.
        // It appears that sometimes [File.exists] returns true even if
        // file is already deleted. So it is much easier to
        // handle FileNotFound exception when processing event down the
        // processing chain.

        // Also, when changing file sometimes lastModifiedTime
        // is changed in two steps.

        // TODO Build workaround for two described above issues.

        // --- Clear ---
        trackerThread.interrupt()
        trackerThread.join()
    }

    @Test
    fun `test altering file in nested directory`() {
        // --- Prepare ---
        val tracker = NaiveFileTracker()
        tracker.init(tempDir, callerThreadTestExecutor){}

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
        tracker.init(tempDir, callerThreadTestExecutor){}

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
        tracker.init(tempDir, callerThreadTestExecutor){}

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