package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.dflt.DefaultProcessor
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LifecycleUsage {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `test forcible stop`() {
        // --- Prepare ---
        val executor = acquireExecutor("queue", testAsync::sendEvent, testAsync::sendEvent)
        val manager = DefaultFileProcessorManager(executorService = executor)
        val key = "key"

        // --- Test ---
        executor.activate {

            // Start tracking file.
            val trackerHandle = manager.startTrackingFile(tempDir, key, false)
                .toCompletableFuture()

            // Approve aggregator subscription.
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("queue finished")

            trackerHandle.get()

            // Create simple listener.
            manager.listenForKey(key)
                // Send file name as event.
                .startListening { it.forEach { testAsync.sendEvent(it.name) } }

            // Create first file.
            tempDir.createFile("newFile.txt") { "content" }

            // Check that event was passed.
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("newFile.txt")
            testAsync.assertEvent("queue finished")

            // Create second file, but do not allow to process it.
            tempDir.createFile("newFile2.txt") { "content" }
        }

        // forcibly stop with suspended processing.
        val handle = manager.stop(true)

        executor.activate {
            // Check for queues stopping.
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("queue finished")

            // Await for stopping.
            handle.toCompletableFuture().get()

            // Assert no processing events occurred.
            testAsync.assertNoEvent()
        }
    }

    @Test
    fun `test graceful stop`() {
        // --- Prepare ---
        val aggregatorExecutor = acquireExecutor("queue", testAsync::sendEvent, testAsync::sendEvent)
        val manager = DefaultFileProcessorManager()
        val key = "key"

        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)
            .toCompletableFuture().get()

        // Create simple listener.
        manager.listenForKey(key)
            // Send file name as event.
            .startListening { it.forEach { testAsync.sendEvent(it.name) } }

        // --- Test ---

        aggregatorExecutor.activate {

            // Approve aggregator subscription.
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("queue finished")

            // Create first file.
            tempDir.createFile("newFile.txt") { "content" }

            // Check that event was passed.
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("newFile.txt")
            testAsync.assertEvent("queue finished")

            // Create second file, but do not allow to process it.
            tempDir.createFile("newFile2.txt") { "content" }
            Thread.sleep(fileSystemPause) // Small pause for tracker to catch changes.
        }

        // gracefully stop with suspended processing.
        val handle = manager.stop(false)

        aggregatorExecutor.activate {
            // Check that event was passed.
            testAsync.assertEvent("queue start")
            testAsync.assertEvent("newFile2.txt")
            testAsync.assertEvent("queue finished")

            // Check for queues stopping.
            testAsync.assertEvent("queue start")
            // Artificial event can be discarded, since executor stop can shot
            // first (but not before queue stopping). It's discarding is allowed, but
            // no other event expected here.
            testAsync.assertEvent("queue finished", required = false)

            // Await for stopping.
            handle.toCompletableFuture().get()

            // Assert no events are available.
            testAsync.assertNoEvent()
        }
    }

}