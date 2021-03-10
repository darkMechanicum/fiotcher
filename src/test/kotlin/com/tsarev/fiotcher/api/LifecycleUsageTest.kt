package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LifecycleUsageTest {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `test forcible stop`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager()
        val key = "key"

        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false).get()

        // Create simple listener.
        manager.listenForKey(key)
            // Send file name as event.
            .startListening { it.forEach { testAsync.sendEvent(it.name) } }

        // --- Test ---
        // Create first file.
        tempDir.createFile("newFile.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile.txt")

        // Create second file, but do not allow to process it.
        tempDir.createFile("newFile2.txt") { "content" }

        // forcibly stop with suspended processing.
        manager.stop(true).toCompletableFuture().get()

        // Assert no processing events occurred.
        testAsync.assertNoEvent()

        // Tear down.
        manager.stop(false).toCompletableFuture().get()
    }

    @Test
    fun `test graceful stop`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager()
        val key = "key"

        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false).get()

        // Create simple listener.
        manager.listenForKey(key)
            // Send file name as event.
            .startListening { it.forEach { testAsync.sendEvent(it.name) } }

        // --- Test ---
        // Create first file.
        tempDir.createFile("newFile.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile.txt")

        // Create second file, but do not allow to process it.
        tempDir.createFile("newFile2.txt") { "content" }
        Thread.sleep(fileSystemPause) // Small pause for tracker to catch changes.

        // gracefully stop with suspended processing.
        val handle = manager.stop(false)

        // Check that event was passed.
        testAsync.assertEvent("newFile2.txt")

        // Await for stopping.
        handle.toCompletableFuture().get()

        // Assert no events are available.
        testAsync.assertNoEvent()

        // Tear down.
        manager.stop(false).toCompletableFuture().get()
    }

}