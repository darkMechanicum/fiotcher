package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FileProcessorManager
import com.tsarev.fiotcher.api.Processor
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.common.EventType
import com.tsarev.fiotcher.flows.CommonListener
import com.tsarev.fiotcher.tracker.TrackerEventBunch
import org.w3c.dom.Document
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.nio.file.Path
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

    override fun startTracking(path: String, key: String, recursively: Boolean) =
        startTracking(File(path), key, recursively)

    override fun startTracking(path: Path, key: String, recursively: Boolean) =
        startTracking(File(path.toAbsolutePath().toUri()), key, recursively)

    override fun stopTracking(path: File, key: String?, force: Boolean): Future<*> {
        return processor.trackerPool
            .deRegisterTracker(path, key, force)
    }

    override fun stopTracking(path: String, key: String?, force: Boolean) =
        stopTracking(File(path), key, force)

    override fun stopTracking(path: Path, key: String?, force: Boolean) =
        stopTracking(File(path.toAbsolutePath().toUri()), key, force)

    override fun handleLines(key: String?, linedListener: (String) -> Unit): Stoppable {
        val topListener = object : CommonListener<String>() {
            override fun doOnNext(item: String) {
                linedListener(item)
            }
        }
        val listener = with(processor.wayStation) {
            topListener
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

    override fun handleFiles(key: String?, linedListener: (File) -> Unit): Stoppable {
        val topListener = object : CommonListener<File>() {
            override fun doOnNext(item: File) {
                linedListener(item)
            }
        }
        val listener = with(processor.wayStation) {
            topListener.asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

    override fun handleSax(key: String?, saxListener: DefaultHandler): Stoppable {
        val topListener = object : CommonListener<InputStream>() {
            override fun doOnNext(item: InputStream) {
                saxParser.parse(item, saxListener)
            }
        }
        val listener = with(processor.wayStation) {
            topListener
                .syncChainFrom<File, InputStream> {
                    naiveFileStreamPool.getInputStream(it)
                }
                .asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

    override fun handleDom(key: String?, domListener: (Document) -> Unit): Stoppable {
        val topListener = object : CommonListener<InputStream>() {
            override fun doOnNext(item: InputStream) {
                val document = domParser.parse(item)
                domListener(document)
            }
        }
        val listener = with(processor.wayStation) {
            topListener
                .syncChainFrom<File, InputStream> {
                    naiveFileStreamPool.getInputStream(it)
                }
                .asyncDelegateFrom<TrackerEventBunch<File>, File> { bunch, publisher ->
                    val changedFiles = bunch.events.filter { it.type == EventType.CHANGED }.flatMap { it.event }
                    changedFiles.forEach { publisher(it) }
                }
        }
        processor.trackerListenerRegistry.registerListener(listener, key)
        return listener
    }

}