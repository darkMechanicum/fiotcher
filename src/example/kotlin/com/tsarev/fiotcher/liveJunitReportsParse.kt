package com.tsarev.fiotcher

import com.tsarev.fiotcher.api.handleSax
import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import org.junit.platform.console.ConsoleLauncher
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

val dummyPrintStream = PrintStream(object : OutputStream() {
    override fun write(b: Int) = Unit
})

/**
 * Launch this library tests and parse their reports with it.
 */
fun main() {

    // Get parameters.
    val tmpReportsDir = Files.createTempDirectory("com_tsarev_fiotcher_example").toFile()
    println("Using temporary directory: ${tmpReportsDir.absolutePath}.\n")

    // Create processor manager.
    val manager = DefaultFileProcessorManager()

    // Start tracking junit report directory.
    manager.startTrackingFile(tmpReportsDir, "junit").toCompletableFuture().get()

    val testCount = AtomicLong()

    // Start parsing junit reports.
    manager.handleSax("junit") {
        if (it.element == "testcase") {
            testCount.incrementAndGet()
            println(
                "Test [${it.attributes["name"]?.removeSuffix("()")}]:\n" +
                        "\tFrom class [${it.attributes["classname"]}] " +
                        "passed by [${it.attributes["time"]}] seconds."
            )
        }
    }

    // Start junit instance.
    startJUnit(tmpReportsDir, "com.tsarev.fiotcher").join()

    // Stop processor manager.
    manager.stopAndWait(false)

    println("\n${testCount.get()} test discovered.")
}

/**
 * Start JUnit is separate thread.
 */
@Suppress("SameParameterValue")
private fun startJUnit(tmpReportsDir: File, packageName: String) = thread(start = true) {
    try {
        ConsoleLauncher.execute(
            dummyPrintStream,
            dummyPrintStream,
            "--reports-dir=${tmpReportsDir.absolutePath}",
            "-p", packageName
        )
    } catch (cause: Throwable) {
        cause.printStackTrace()
    }
}