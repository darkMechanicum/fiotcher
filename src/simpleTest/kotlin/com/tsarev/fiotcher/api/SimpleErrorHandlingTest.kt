package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class SimpleErrorHandlingTest {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `handle error with single sync listener`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager()
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)

        // Create listener.
        manager.startListening(
            key,
            // Send file content as event, or IOException message, if present.
            handleErrors = {
                if (it is IOException) testAsync.sendEvent(it).let { null } else it
            }) { files ->
            // Try read from file.
            files.forEach { file -> throw FileNotFoundException(file.absolutePath) }
        }

        // --- Test ---
        // Create and delete first file, so event will be sent on non existing file, while executor is suspended.
        val file = tempDir.createFile("newFile.txt") { "first" }
        // Small pause to give filesystem some time.
        Thread.sleep(fileSystemPause)
        file.delete()

        // Check that event was passed.
        testAsync.assertEvent<FileNotFoundException> {
            Assertions.assertTrue(it.message?.contains(file.absolutePath) ?: false)
        }

        // Tear down.
        manager.stop(false)
    }

    @Test
    fun `interrupt chain while handling errors`() {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager()
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)

        // Special test only exceptions.
        class StopException : RuntimeException()
        class ContinueException : RuntimeException()

        // Create listener.
        manager.startListening(
            key,
            // Break the chain if [StopException] occurred.
            handleErrors = {
                if (it is StopException)
                    throw it
                else if (it is ContinueException) {
                    testAsync.sendEvent("continue")
                    null
                } else it
            }
        ) {
            for (file in it) {
                // Imitate unrecoverable error.
                if (file.name.contains("stop")) throw StopException()
                // Imitate recoverable error, that can be handled.
                if (file.name.contains("continue")) throw ContinueException()
                testAsync.sendEvent(file.name)
            }
        }

        // --- Test ---
        // Create and delete first file, so event will be sent on non existing file, while executor is supended.
        val file = tempDir.createFile("newFile.txt") { "first" }
        // Check that first event is sent.
        testAsync.assertEvent(file.name)

        // Imitate error prone file.
        tempDir.createFile("continue.txt") { "first" }
        // Check that error is processed.
        testAsync.assertEvent("continue")

        // Imitate error prone file.
        tempDir.createFile("stop.txt") { "first" }

        // Check that no more events are sent.
        tempDir.createFile("stop.txt") { "first" }
        testAsync.assertNoEvent()

        // Tear down.
        manager.stop(false)
    }

}