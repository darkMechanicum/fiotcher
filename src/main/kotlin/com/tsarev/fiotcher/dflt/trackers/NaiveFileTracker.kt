package com.tsarev.fiotcher.dflt.trackers

import com.tsarev.fiotcher.api.FiotcherException
import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.dflt.Brake
import com.tsarev.fiotcher.dflt.isPushed
import com.tsarev.fiotcher.dflt.push
import com.tsarev.fiotcher.internal.EventWithException
import com.tsarev.fiotcher.internal.asSuccess
import com.tsarev.fiotcher.internal.pool.Tracker
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple plain file tracker implementation that iteratively checks
 * file change time.
 */
class NaiveFileTracker(

    /**
     * Minimum time of one watch iteration in milliseconds.
     */
    private val iterationMinMillis: Long = 40,

    /**
     * If nested directories should be processed.
     */
    private val recursive: Boolean = true,

    /**
     * An amount of time to believe, that file is changed.
     */
    private val doesChangeThresholdMillis: Long = 10,

    ) : Tracker<File>() {

    data class StampWithDirectoryFlag(val stamp: AtomicLong, val isDirectory: Boolean)

    /**
     * Already discovered files with their timestamp.
     */
    private var discovered: ConcurrentHashMap<File, StampWithDirectoryFlag> = ConcurrentHashMap()

    /**
     * Send event closure.
     */
    private lateinit var sendEvent: (EventWithException<InitialEventsBunch<File>>) -> Unit

    /**
     * Thread that runs this tracker.
     */
    private var trackerThread: Thread? = null

    /**
     * If this tracker is forced to stop.
     */
    @Volatile
    private var isForced = false

    /**
     * Stop brake.
     */
    override val stopBrake = Brake<Unit>()

    override fun doInit(
        executor: Executor,
        sendEvent: (EventWithException<InitialEventsBunch<File>>) -> Unit
    ) {
        if (!resourceBundle.exists() || !resourceBundle.isDirectory) throw FiotcherException("$resourceBundle is not a directory.")
        discovered[resourceBundle] = StampWithDirectoryFlag(AtomicLong(0), true)
        this.sendEvent = sendEvent
    }

    override fun run() {
        trackerThread = Thread.currentThread()
        outer@ while (true) {
            // Fix isStopped state to allow last graceful watch iteration.
            val isStoppedFixed = isStopped
            val discoveredCopy = discovered.entries.sortedBy { it.key.absolutePath }
            val iterationMinFinishTime = System.currentTimeMillis() + iterationMinMillis
            for ((file, stamp) in discoveredCopy) {
                // Check is forced state.
                if (isForced) break@outer
                // Watch all common files.
                checkFile(file)
                // Check is forced state.
                if (isForced) break@outer
                // Watch all directory contents.
                if (stamp.isDirectory) checkDirectory(file)
            }
            if (isStoppedFixed || trackerThread?.isInterrupted == true) break
            val remaining = iterationMinFinishTime - System.currentTimeMillis()
            try {
                if (remaining >= 0) Thread.sleep(remaining)
            } catch (cause: InterruptedException) {
                this.stop(true)
            }
        }
        // Complete brake handler.
        stopBrake.push().complete(Unit)
    }

    /**
     * Check if file is updated since last watch.
     * If file is removed, so it is removed from discovered.
     * If file is discovered at first time, so it is added to [discovered] map.
     */
    private fun checkFile(file: File) {
        if (!file.exists()) {
            discovered.remove(file)
            return
        } else {
            val dirStamp = discovered.computeIfAbsent(file) { StampWithDirectoryFlag(AtomicLong(0), file.isDirectory) }
            val lastModified = file.lastModified()
            val now = System.currentTimeMillis()
            val lastWatched = dirStamp.stamp.updateAndGet { previous ->
                if (previous + doesChangeThresholdMillis < lastModified) now else previous
            }
            if (lastWatched == now) {
                if (!dirStamp.isDirectory && !isForced) {
                    // Exists check must be the last, since file can be deleted and
                    // despite this have modified time.
                    if (file.exists()) {
                        sendEvent(InitialEventsBunch(listOf(file)).asSuccess())
                    }
                }
            }
        }
    }

    /**
     * Check for directory contents.
     */
    private fun checkDirectory(file: File) {
        if (!file.isDirectory) return
        else if (file !== resourceBundle && !recursive) return
        else {
            val innerFiles = file.listFiles() ?: emptyArray()
            for (innerFile in innerFiles) {
                // Check is forced state.
                if (isForced) return
                checkFile(innerFile)
            }
        }
    }

    /**
     * Stop processing.
     */
    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push {
        if (force) trackerThread?.interrupt()
        isForced = force
    }

    override val isStopped get() = stopBrake.isPushed
}