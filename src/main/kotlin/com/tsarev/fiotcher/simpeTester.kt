package com.tsarev.fiotcher

import com.tsarev.fiotcher.flows.CommonListener
import com.tsarev.fiotcher.simple.FileSystemTracker
import com.tsarev.fiotcher.simple.SimpleTrackerPool
import com.tsarev.fiotcher.tracker.TrackerEvent
import com.tsarev.fiotcher.tracker.TrackerEventBunch
import java.nio.file.Paths
import java.util.concurrent.ForkJoinPool

fun main() {
    val tracker = FileSystemTracker()
    val tracker2 = FileSystemTracker()
    val trackerPool = SimpleTrackerPool()

    val listener = object : CommonListener<TrackerEvent>() {
        override fun doOnNext(item: TrackerEvent) {
            println("key1 - $item")
        }
    }

    val split = listener.splitFrom<TrackerEventBunch>(ForkJoinPool.commonPool()) { it.events }

    trackerPool.registerListener(split, "key1")

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