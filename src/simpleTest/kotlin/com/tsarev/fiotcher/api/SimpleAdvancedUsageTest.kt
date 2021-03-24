package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SimpleAdvancedUsageTest {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `two directories usage with defaults`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager()
        val key = "key"

        val firstTempDir = tempDir.createDirectory("first")
        val secondTempDir = tempDir.createDirectory("second")

        // Start tracking files.
        manager.startTrackingFile(firstTempDir, key, false)
        manager.startTrackingFile(secondTempDir, key, false)

        // Create simple listener.
        // Send file name as event.
        manager.startListening(key) { it.forEach { testAsync.sendEvent(it.name) } }

        // --- Test ---

        // Create first file.
        firstTempDir.createFile("newFile11.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile11.txt")

        // Create second file.
        secondTempDir.createFile("newFile21.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile21.txt")

        // Stop first tracker
        manager.stopTracking(firstTempDir, key, false)
        Thread.sleep(defaultTestAsyncAssertTimeoutMs)

        firstTempDir.createFile("newFile12.txt") { "content" }

        // Check that no events are passed.
        testAsync.assertNoEvent()

        // Stop first tracker with API method.
        manager.stopTracking(secondTempDir, key, false)
        firstTempDir.createFile("newFile21.txt") { "content" }

        // Check that no events are passed.
        testAsync.assertNoEvent()

        // Tear down.
        manager.stop(false)
    }

    @Test
    fun `two listeners usage with defaults`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager()
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)

        // Create listener.
        // Send file name as event.
        val firstHandle = manager.startListening(key) { it.forEach { testAsync.sendEvent(it.name) } }

        // --- Test ---

        // Create first file.
        tempDir.createFile("newFile.txt") { "content" }
        Thread.sleep(fileSystemPause)

        // Check that event was passed.
        testAsync.assertEvent("newFile.txt")

        // Stop first listener.
        firstHandle?.stop()

        // Create second file.
        tempDir.createFile("newFile2.txt") { "content" }

        // Check that there was no event.
        testAsync.assertNoEvent()

        // Create listener again.
        // Send file name as event.
        manager.startListening(key) { it.forEach { testAsync.sendEvent(it.name) } }

        // Create third file.
        tempDir.createFile("newFile3.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile3.txt")

        manager.stopListening(key)

        // Create fourth file.
        tempDir.createFile("newFile3.txt") { "content" }

        // Check that no event was passed.
        testAsync.assertNoEvent()

        // Tear down.
        manager.stop(false)
    }

}