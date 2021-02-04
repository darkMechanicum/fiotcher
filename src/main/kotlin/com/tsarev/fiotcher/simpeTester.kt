package com.tsarev.fiotcher

import com.tsarev.fiotcher.flows.CommonListener
import com.tsarev.fiotcher.simple.FileSystemTracker
import com.tsarev.fiotcher.simple.SimpleTrackerPool
import com.tsarev.fiotcher.tracker.TrackerEventBunch
import java.nio.file.Paths

fun main() {
    val tracker = FileSystemTracker()
    val tracker2 = FileSystemTracker()
    val trackerPool = SimpleTrackerPool()
    trackerPool.registerListener(object : CommonListener<TrackerEventBunch>() {
        override fun doOnNext(item: TrackerEventBunch) {
            println("key1 - $item")
        }
    }, "key1")

    trackerPool.registerListener(object : CommonListener<TrackerEventBunch>() {
        override fun doOnNext(item: TrackerEventBunch) {
            println("key2 - $item")
        }
    }, "key2")

    val uriPath1 = Paths.get("/home/alex/projects/fiotcher/src/main/resources").toUri()
    val uriPath2 = Paths.get("/home/alex/projects/fiotcher/src/main/resources/inner").toUri()

    trackerPool.registerTracker(uriPath1, tracker, "key1")
    trackerPool.registerTracker(uriPath2, tracker2, "key2")
}