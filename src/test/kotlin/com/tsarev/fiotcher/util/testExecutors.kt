package com.tsarev.fiotcher.util

import com.tsarev.fiotcher.util.TestExecutorRegistry.TestExecutor
import com.tsarev.fiotcher.util.TestExecutorRegistry.suspendAllBut
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/**
 * A registry of [TestExecutor].
 *
 * It allows to create [Executor]s and only single of them can
 * be activated through [suspendAllBut] and [TestExecutor.activate] methods.
 *
 * This allows to control execution order precisely, even in presence of
 * executors thread blocking.
 */
object TestExecutorRegistry {

    /**
     * Registered executors.
     */
    private val executors = LinkedBlockingQueue<DefaultTestExecutor>()

    /**
     * Create single executor and add it to the queue.
     */
    fun acquireExecutor(
        name: String = "default",
        beforeStart: (String) -> Unit,
        afterCompletion: (String) -> Unit
    ): TestExecutor {
        val result = DefaultTestExecutor(name, beforeStart, afterCompletion)
        executors += result
        return result
    }

    /**
     * Stop all executors and clear registry.
     */
    fun clear() {
        executors.forEach { it.interrupt() }
        executors.forEach { it.close() }
        executors.forEach { it.suspended = true }
        executors.clear()
    }

    /**
     * Stop all other executors but one, that is activated.
     */
    fun suspendAllBut(executors: Iterable<TestExecutor>) {
        val castedExecutors = executors.map { it as? DefaultTestExecutor ?: throw IllegalArgumentException() }
        synchronized(this) {
            this.executors.forEach { it.suspended = true }
            castedExecutors.forEach { it.suspended = false }
        }
    }

    /**
     * Activate all executors.
     */
    fun activateAll() {
        this.executors.forEach { it.suspended = false }
    }

    /**
     * Test executor, that can be `activated` on a block of code.
     */
    interface TestExecutor : ExecutorService, AutoCloseable {
        fun activate(block: () -> Unit)
        fun suspend()
    }

    /**
     * Async single thread executor service with suspending capabilities.
     */
    private class DefaultTestExecutor(
        private val name: String,
        private val beforeStart: (String) -> Unit,
        private val afterCompletion: (String) -> Unit
    ) : AbstractExecutorService(), TestExecutor {
        override fun activate(block: () -> Unit) {
            try {
                suspendAllBut(listOf(this))
                block()
            } finally {
                suspended = true
            }
        }

        override fun suspend() {
            suspended = true
        }

        fun interrupt() = singleTestThread.interrupt()

        @Volatile
        var suspended = true
        private val loggingEnabled = System.getProperty("com.tsarev.fiotcher.tests.log", "false").toBoolean()
        private val counter = AtomicLong(0)
        private val testThreadTasks = LinkedBlockingDeque<Runnable>()
        private var isShutdown = false
        private val singleTestThread = Thread {
            mainLoop()
        }.apply { isDaemon = true; start() }

        private fun mainLoop() {
            while (!isShutdown && !Thread.currentThread().isInterrupted) {
                try {
                    if (suspended) {
                        // Loop while we are suspended.
                        Thread.sleep(10)
                        continue
                    }
                    val runnable = testThreadTasks.take()
                    // If we took task while suspended, than we must enqueue it again.
                    if (suspended) {
                        testThreadTasks.addFirst(runnable)
                    } else runnable.run()
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (ignored: Throwable) {
                    ignored.printStackTrace()
                }
            }
        }

        private fun mainLoopShutdown() {
            var runnable = testThreadTasks.poll()
            while (runnable != null) {
                runnable.run()
                runnable = testThreadTasks.poll()
            }
        }

        override fun execute(command: Runnable) {
            val value = counter.incrementAndGet()
            log { "[REQUEST $name $value]: $command" }
            testThreadTasks.add { log { "[EXECUTE $name $value]: before" }; beforeStart("$name start") }
            testThreadTasks.add { log { "[EXECUTE $name $value]: $command" }; command.run() }
            testThreadTasks.add { log { "[EXECUTE $name $value]: after" }; afterCompletion("$name finished") }
        }

        /**
         * Primitive no buffer logging.
         */
        private fun log(toLog: () -> String) {
            if (loggingEnabled) {
                System.err.println(toLog())
            }
        }

        override fun close() = singleTestThread.interrupt()
        override fun shutdown() = singleTestThread.interrupt().run { mainLoopShutdown() }
        override fun shutdownNow() = singleTestThread.interrupt().let { testThreadTasks.toList() }
        override fun isShutdown() = isShutdown
        override fun isTerminated() = testThreadTasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = throw UnsupportedOperationException()
    }
}

fun Iterable<TestExecutor>.activate(block: () -> Unit) {
    suspendAllBut(this)
    block()
    this.forEach { it.suspend() }
}

fun activateAll(block: () -> Unit) {
    TestExecutorRegistry.activateAll()
    block()
    suspendAllBut(listOf())
}

/**
 * Convenient function to use without [TestExecutorRegistry].
 */
fun acquireExecutor(
    name: String = "default",
    beforeStart: (String) -> Unit = { },
    afterCompletion: (String) -> Unit = { },
) = TestExecutorRegistry.acquireExecutor(name, beforeStart, afterCompletion)

/**
 * Convenient function to use without [TestExecutorRegistry].
 */
fun acquireExecutor(
    name: String = "default",
    wrapper: (String) -> Unit = { }
) = TestExecutorRegistry.acquireExecutor(name, wrapper, wrapper)