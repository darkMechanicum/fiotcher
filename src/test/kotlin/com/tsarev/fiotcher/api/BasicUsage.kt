package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.dflt.DefaultProcessor
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class BasicUsage {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `basic single directory usage with defaults`(async: Boolean) {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager(DefaultProcessor())
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)
            .toCompletableFuture().get()

        // Create simple listener.
        manager.listenForInitial(key)
            .split(async = async) { it }
            // Send file name as event.
            .startListening { testAsync.sendEvent(it.name) }

        // --- Test ---

        // Create first file.
        tempDir.createFile("newFile.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile.txt")

        // Create second file.
        tempDir.createFile("newFile2.txt") { "content" }

        // Check that event was passed.
        testAsync.assertEvent("newFile2.txt")
    }

    @Test
    fun `parse single directory files with defaults`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager(DefaultProcessor())
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)
            .toCompletableFuture().get()

        // Create simple listener.
        // Send xml tag local name as event (when parser detects closing tag).
        manager.handleSax(key) { testAsync.sendEvent(it.element) }

        // --- Test ---
        // Create first file.
        tempDir.createFile("newFile.txt") {
            """
                <?xml version="1.0"?>
                <first></first>
            """.trimIndent()
        }
        // Check that first file has processed.
        testAsync.assertEvent("first")

        // Create second file.
        tempDir.createFile("newFile2.txt") {
            """
                <?xml version="1.0"?>
                <second>
                <third>
                </third>
                </second>
            """.trimIndent()
        }

        // Check that second file has processed.
        testAsync.assertEvent("third")
        testAsync.assertEvent("second")

        // Check that no more events are left.
        testAsync.assertNoEvent()
    }

    @Test
    fun `parse inner directory files with defaults`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager(DefaultProcessor())
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, true)
            .toCompletableFuture().get()

        // Create simple listener.
        // Send xml tag local name as event (when parser detects closing tag).
        manager.handleSax(key) { testAsync.sendEvent(it.element) }

        // --- Test ---
        // Create inner directory.
        val innedDirectory = tempDir.createDirectory("inner")
        // Small pause to give filesystem some time.
        Thread.sleep(fileSystemPause)
        // Create first file.
        innedDirectory.createFile("newFile.txt") {
            """
                <?xml version="1.0"?>
                <first></first>
            """.trimIndent()
        }

        // Check that first inner file has processed.
        testAsync.assertEvent("first")

        // Create second inner file.
        innedDirectory.createFile("newFile2.txt") {
            """
                <?xml version="1.0"?>
                <second>
                <third>
                </third>
                </second>
            """.trimIndent()
        }

        // Check that second inner file has processed.
        testAsync.assertEvent("third")
        testAsync.assertEvent("second")

        // Check that no more events are left.
        testAsync.assertNoEvent()
    }

}