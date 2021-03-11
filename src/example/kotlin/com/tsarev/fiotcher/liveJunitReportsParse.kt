package com.tsarev.fiotcher

import com.tsarev.fiotcher.api.handleFiles
import com.tsarev.fiotcher.api.handleLines
import com.tsarev.fiotcher.api.handleSax
import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import org.junit.platform.console.ConsoleLauncher
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess

val dummyPrintStream = PrintStream(object : OutputStream() {
    override fun write(b: Int) = Unit
})

// Stylish.
const val ANSI_RESET = "\u001B[0m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_WHITE = "\u001B[37m"

/**
 * Launch this library tests and parse their reports with it.
 */
fun main() {

    // Get parameters.
    val firstTmpReportsDir = Files.createTempDirectory("com_tsarev_fiotcher_example").toFile()
    val secondTmpReportsDir = Files.createTempDirectory("com_tsarev_fiotcher_example").toFile()
    println("Using temporary directory [${firstTmpReportsDir.absolutePath}] and [${secondTmpReportsDir.absolutePath}].\n")

    // Create processor manager.
    val manager = DefaultFileProcessorManager()

    // Start tracking junit report directory.
    manager.startTrackingFile(firstTmpReportsDir, "junit").get()
    manager.startTrackingFile(secondTmpReportsDir, "junit").get()

    // Start printing junit testcases.
    manager.handleSax("junit") {
        if (it.element == "testcase") {
            println(
                "${ANSI_WHITE}Test [${ANSI_BLUE}${it.attributes["name"]?.removeSuffix("()")}${ANSI_WHITE}] " +
                        "passed by [${ANSI_RESET}${it.attributes["time"]}${ANSI_WHITE}] seconds:\n" +
                        "\tFrom class [${ANSI_RESET}${it.attributes["classname"]}${ANSI_WHITE}].${ANSI_RESET}\n"
            )
        }
    }

    // Start counting junit testcases.
    val testCount = AtomicLong()
    manager.handleSax("junit") {
        if (it.element == "testcase") {
            testCount.incrementAndGet()
        }
    }

    // Start counting junit testcases.
    val linesCount = AtomicLong()
    manager.handleLines("junit") { linesCount.incrementAndGet() }

    // Start registering report files.
    val discoveredFiles = ConcurrentSkipListSet<String>()
    manager.handleFiles("junit") { discoveredFiles += it.absolutePath}

    // Start junit instance.
    startJUnit(firstTmpReportsDir, "com.tsarev.fiotcher.api").join()
    startJUnit(secondTmpReportsDir, "com.tsarev.fiotcher.internals").join()

    // Stop processor manager.
    manager.stopAndWait(false)

    println("\n${testCount.get()} test discovered.")
    println("\n${linesCount.get()} lines discovered.")
    println("Discovered reports:")
    discoveredFiles.forEach { println("\t$it") }

    // Exit in case some running threads remain.
    exitProcess(0)
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
        // no-op
    }
}