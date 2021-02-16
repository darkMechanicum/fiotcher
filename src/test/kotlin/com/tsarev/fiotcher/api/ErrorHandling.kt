package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.dflt.DefaultProcessor
import com.tsarev.fiotcher.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException

class ErrorHandling {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `handle error with single sync listener`(async: Boolean) {
        // --- Prepare ---
        val testExecutor = acquireExecutor()
        val manager = DefaultFileProcessorManager(
            DefaultProcessor(
                aggregatorExecutorService = testExecutor
            )
        )
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)
            .toCompletableFuture().get()

        // Create listener.
        manager.listenForInitial(key)
            .split(async = async) { it }
            // Send file content as event, or IOException message, if present.
            .startListening(handleErrors = {
                if (it is IOException) testAsync.sendEvent(it).let { null } else it
            }) { file ->
                // Try read from file.
                FileReader(file).use { it.read() }
            }

        // --- Test ---
        // Create and delete first file, so event will be sent on non existing file, while executor is suspended.
        val file = tempDir.createFile("newFile.txt") { "first" }
        // Small pause to give filesystem some time.
        Thread.sleep(200)
        file.delete()

        // Activate executor.
        testExecutor.activate {
            // Check that event was passed.
            testAsync.assertEvent<FileNotFoundException> {
                Assertions.assertEquals("${file.absolutePath} (No such file or directory)", it.message)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `interrupt chain while handling errors`(async: Boolean) {
        // --- Prepare ---
        val manager = DefaultFileProcessorManager(DefaultProcessor())
        val key = "key"
        // Start tracking file.
        manager.startTrackingFile(tempDir, key, false)
            .toCompletableFuture().get()

        // Special test only exceptions.
        class StopException : RuntimeException()
        class ContinueException : RuntimeException()

        // Create listener.
        manager.listenForInitial(key)
            .delegate<File>(
                async = async,
                // Break the chain if [StopException] occurred.
                handleErrors = { if (it is StopException) throw it else it }
            ) { it, publish ->
                for (file in it) {
                    // Imitate unrecoverable error.
                    if (file.name.contains("stop")) throw StopException()
                    // Imitate recoverable error, that can be handled.
                    if (file.name.contains("continue")) throw ContinueException()
                    publish(file)
                }
            }
            .startListening(handleErrors = {
                // Imitate recoverable error handling.
                it.takeIf { it is ContinueException }?.let { testAsync.sendEvent("continue"); null }
            }) { file ->
                testAsync.sendEvent(file.name)
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
    }

}