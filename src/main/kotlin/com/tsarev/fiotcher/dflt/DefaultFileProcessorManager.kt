package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FileProcessorManager
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.trackers.FileSystemTracker
import com.tsarev.fiotcher.internal.Processor
import com.tsarev.fiotcher.internal.typedKey
import org.w3c.dom.Document
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.CompletionStage
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory

class DefaultFileProcessorManager(
    override val processor: Processor<File>
) : FileProcessorManager {

    val saxParser = SAXParserFactory.newInstance().newSAXParser()

    val domParser = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    override fun startTracking(path: File, key: String, recursively: Boolean): CompletionStage<out Stoppable> {
        val fileSystemTracker = FileSystemTracker(recursive = recursively)
        return processor.startTracker(path, fileSystemTracker, key)
    }

    override fun stopTracking(path: File, key: String, force: Boolean): CompletionStage<*> {
        return processor.stopTracker(path, key, force)
    }

    override fun handleLines(key: String, linedListener: (String) -> Unit): Stoppable {
        val listener = with(processor) {
            createCommonListener(listener = linedListener)
                .syncSplitFrom<InputStream, String> { stream ->
                    mutableListOf<String>().also { list ->
                        with(Scanner(stream)) {
                            while (hasNextLine()) list += nextLine()
                        }
                    }
                }
                .syncChainFrom<File, InputStream> { getStreamOrNull(it) }
                .asyncDelegateFrom<Collection<File>, File> { bunch, publisher -> bunch.forEach { publisher(it) } }
        }
        processor.registerListener(listener, key.typedKey())
        return listener
    }

    override fun handleFiles(key: String, fileListener: (File) -> Unit): Stoppable {
        val listener = with(processor) {
            createCommonListener(listener = fileListener)
                .asyncDelegateFrom<Collection<File>, File> { bunch, publisher -> bunch.forEach { publisher(it) } }
        }
        processor.registerListener(listener, key.typedKey())
        return listener
    }

    override fun handleSax(key: String, saxListener: DefaultHandler): Stoppable {
        val listener = with(processor) {
            createCommonListener<InputStream> { saxParser.parse(it, saxListener) }
                .syncChainFrom<File, InputStream> { getStreamOrNull(it) }
                .asyncDelegateFrom<Collection<File>, File> { bunch, publisher -> bunch.forEach { publisher(it) } }
        }
        processor.registerListener(listener, key.typedKey())
        return listener
    }

    override fun handleDom(key: String, domListener: (Document) -> Unit): Stoppable {
        val listener = with(processor) {
            createCommonListener<InputStream> { val document = domParser.parse(it); domListener(document) }
                .syncChainFrom<File, InputStream> { getStreamOrNull(it) }
                .asyncDelegateFrom<Collection<File>, File> { bunch, publisher -> bunch.forEach { publisher(it) } }
        }
        processor.registerListener(listener, key.typedKey())
        return listener
    }

    private fun getStreamOrNull(file: File) = try {
        FileInputStream(file)
    } catch (cause: IOException) {
        null
    }

}