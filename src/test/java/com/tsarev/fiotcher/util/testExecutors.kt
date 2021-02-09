package com.tsarev.fiotcher.util

import com.tsarev.fiotcher.util.TestExecutorRegistry.TestExecutor
import com.tsarev.fiotcher.util.TestExecutorRegistry.suspendAllBut
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
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
    fun acquireExecutor(name: String = "default", beforeStart: () -> Unit, afterCompletion: () -> Unit): TestExecutor {
        val result = DefaultTestExecutor(name, beforeStart, afterCompletion)
        executors += result
        return result
    }

    /**
     * Stop all executors and clear registry.
     */
    fun clear() {
        executors.forEach { it.interrupt() }
        executors.clear()
    }

    /**
     * Stop all other executors but one, that is activated.
     */
    fun suspendAllBut(executor: TestExecutor) {
        if (executor !is DefaultTestExecutor) throw IllegalArgumentException()
        synchronized(this) {
            executors.forEach { it.suspended = true }
            executor.suspended = false
        }
    }

    /**
     * Test executor, that can be `activated` on a block of code.
     */
    interface TestExecutor : Executor, AutoCloseable {
        fun activate(block: () -> Unit)
    }

    /**
     * Async single thread executor service with suspending capabilities.
     */
    private class DefaultTestExecutor(
        private val name: String,
        private val beforeStart: () -> Unit,
        private val afterCompletion: () -> Unit
    ) : TestExecutor {
        override fun activate(block: () -> Unit) {
            try {
                suspendAllBut(this)
                block()
            } finally {
                suspended = true
            }
        }

        fun interrupt() = singleTestThread.interrupt()

        @Volatile
        var suspended = true
        private val counter = AtomicLong(0)
        private val testThreadTasks = LinkedBlockingDeque<Runnable>()
        private val singleTestThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
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
                    // no-op
                } catch (ignored: Throwable) {
                    ignored.printStackTrace()
                }
            }
        }.apply { isDaemon = true; start() }

        override fun execute(command: Runnable) {
            val value = counter.incrementAndGet()
            System.err.println("[REQUEST $name $value]: $command")
            testThreadTasks.add { System.err.println("[EXECUTE $name $value]: before"); beforeStart() }
            testThreadTasks.add { System.err.println("[EXECUTE $name $value]: $command"); command.run() }
            testThreadTasks.add { System.err.println("[EXECUTE $name $value]: after"); afterCompletion() }
        }

        override fun close() {
            singleTestThread.interrupt()
        }
    }
}

/**
 * Convenient function to use without [TestExecutorRegistry].
 */
fun acquireExecutor(
    name: String = "default",
    beforeStart: () -> Unit = { },
    afterCompletion: () -> Unit = { },
) = TestExecutorRegistry.acquireExecutor(name, beforeStart, afterCompletion)