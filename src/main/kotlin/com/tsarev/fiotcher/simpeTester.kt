package com.tsarev.fiotcher

import com.tsarev.fiotcher.common.TypedEvent
import com.tsarev.fiotcher.flows.CommonListener
import com.tsarev.fiotcher.simple.FileSystemTracker
import com.tsarev.fiotcher.simple.NaiveResourceStreamPool
import com.tsarev.fiotcher.simple.RollingResourceStreamPool
import com.tsarev.fiotcher.simple.SimpleTrackerPool
import com.tsarev.fiotcher.tracker.TrackerEvent
import com.tsarev.fiotcher.tracker.TrackerEventBunch
import java.io.InputStream
import java.io.Reader
import java.net.URI
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.collections.ArrayList

fun main() {
    val tracker = FileSystemTracker()
    val tracker2 = FileSystemTracker()
    val trackerPool = SimpleTrackerPool()

    val listener = object : CommonListener<String>() {
        override fun doOnNext(item: String) {
            println("key1 - $item")
        }
    }

    val split1 = listener.splitFrom<TypedEvent<InputStream>>(ForkJoinPool.commonPool()) {
        val scanner = Scanner(it.event)
        val result = ArrayList<String>()
        while(scanner.hasNextLine()) {
            result += scanner.nextLine()
        }
        result
    }

    val streamPool = RollingResourceStreamPool()

    val split2 = split1.chainFrom<TypedEvent<URI>>(ForkJoinPool.commonPool()) {
        try {
            val inputStream = streamPool.getInputStream(it.event)
            TypedEvent(inputStream, it.type)
        } catch (cause: CannotOpenStream) {
            null
        }
    }

    val split3 = split2.splitFrom<TrackerEventBunch>(ForkJoinPool.commonPool()) {
        it.events.flatMap { (event, type) -> event.map { TypedEvent(it, type) } }
    }

    trackerPool.registerListener(split3, "key1")

    val uriPath1 = Paths.get("/home/alex/projects/fiotcher/src/main/resources").toUri()
    val uriPath2 = Paths.get("/home/alex/projects/fiotcher/src/main/resources/inner").toUri()

    trackerPool.registerTracker(uriPath1, tracker, "key1")
    trackerPool.registerTracker(uriPath2, tracker2, "key2")

    println()
}