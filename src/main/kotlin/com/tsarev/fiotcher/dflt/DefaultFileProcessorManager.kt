package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.EventType
import com.tsarev.fiotcher.api.FileProcessorManager
import com.tsarev.fiotcher.api.Processor
import com.tsarev.fiotcher.api.tracker.TrackerEventBunch
import com.tsarev.fiotcher.api.util.Stoppable
import com.tsarev.fiotcher.dflt.streams.NaiveFileStreamPool
import com.tsarev.fiotcher.dflt.trackers.FileSystemTracker
import org.w3c.dom.Document
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.Future
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory

class DefaultFileProcessorManager(
    override val processor: Processor<File>
) : FileProcessorManager {

    private val naiveFileStreamPool = NaiveFileStreamPool()

    val saxParser = SAXParserFactory.newInstance().newSAXParser()

    val domParser = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    override fun startTracking(path: File, key: String, recursively: Boolean): Stoppable {
        val fileSystemTracker = FileSystemTracker(recursive = recursively)
        processor.trackerPool
            .registerTracker(path, fileSystemTracker, key)
        return fileSystemTracker
    }

    override fun stopTracking(path: File, key: String, force: Boolean): Future<*> {
        return processor.trackerPool
            .deRegisterTracker(path, key, force)
    }

    override fun handleLines(key: String, linedListener: (String) -> Unit): Stoppable {
        val listener = with(processor.wayStation) {
            createCommonListener(linedListener)
                .syncSplitFrom<InputStream, String> { stream ->
                    mutableListOf<String>().also { list ->
                        with(Scanner(stream)) {
                            while (hasNextLine()) list += nextLine()
                        }
                    }
                }
                .syncChainFrom<File, InputStream> { naiveFileStreamPool.getInputStream(it) }
                .asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

    override fun handleFiles(key: String, fileListener: (File) -> Unit): Stoppable {
        val listener = with(processor.wayStation) {
            createCommonListener(fileListener)
                .asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

    override fun handleSax(key: String, saxListener: DefaultHandler): Stoppable {
        val listener = with(processor.wayStation) {
            createCommonListener<InputStream> { saxParser.parse(it, saxListener) }
                .syncChainFrom<File, InputStream> { naiveFileStreamPool.getInputStream(it) }
                .asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

    override fun handleDom(key: String, domListener: (Document) -> Unit): Stoppable {
        val listener = with(processor.wayStation) {
            createCommonListener<InputStream> { val document = domParser.parse(it); domListener(document) }
                .syncChainFrom<File, InputStream> { naiveFileStreamPool.getInputStream(it) }
                .asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

}