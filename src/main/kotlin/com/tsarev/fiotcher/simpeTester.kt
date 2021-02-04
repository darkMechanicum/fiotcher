package com.tsarev.fiotcher

import com.tsarev.fiotcher.tracker.FileSystemTracker
import com.tsarev.fiotcher.tracker.SimpleTrackerPool
import com.tsarev.fiotcher.tracker.TrackerListener
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Paths
import java.util.*

fun main() {
    val tracker = FileSystemTracker()
    val tracker2 = FileSystemTracker()
    val trackerPool = SimpleTrackerPool()
    trackerPool.registerListener(object : TrackerListener {
        override fun onChanged(resource: URI) {
            try {
                Scanner(File(resource)).use {
                    var counter = 5;
                    while (counter-- > 0 && it.hasNextLine()) {
                        println("yeah")
                        println(it.nextLine())
                    }
                    println("YEAH ---")
                }
            } catch (cause: Throwable) {
                cause.printStackTrace()
            }
        }
    }, "key1")

    trackerPool.registerListener(object : TrackerListener {
        override fun onChanged(resource: URI) {
            println("key2 - $resource")
        }
    }, "key2")
    val uriPath1 = Paths.get("/home/alex/projects/fiotcher/src/main/resources/dir1").toUri()
    val uriPath2 = Paths.get("/home/alex/projects/fiotcher/src/main/resources/dir2").toUri()
    trackerPool.registerTracker(uriPath1, tracker, "key1")
    trackerPool.registerTracker(uriPath2, tracker2, "key2")
}