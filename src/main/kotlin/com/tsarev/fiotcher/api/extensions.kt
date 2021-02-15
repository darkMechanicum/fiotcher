package com.tsarev.fiotcher.api

import org.w3c.dom.Document
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import kotlin.collections.HashMap

fun FileProcessorManager.handleLines(key: String, linedListener: (String) -> Unit) = listenForInitial(key)
    .delegate<File>(async = true) { bunch, publisher -> bunch.forEach { publisher(it) } }
    .chain { getStreamOrNull(it) }
    .split(async = true) { with(Scanner(it)) { mutableListOf<String>().also { while (hasNextLine()) it += nextLine() } } }
    .startListening { linedListener(it) }

fun FileProcessorManager.handleFiles(key: String, fileListener: (File) -> Unit) = listenForInitial(key)
    .delegate<File>(async = true) { bunch, publisher -> bunch.forEach { publisher(it) } }
    .startListening { fileListener(it) }

val saxParser = SAXParserFactory.newInstance().newSAXParser()!!

data class SaxEvent(
    val element: String,
    val uri: String?,
    val localName: String?,
    val attributes: Map<String, String>,
)

fun FileProcessorManager.handleSax(
    key: String,
    parsingErrorHandler: ((SAXException) -> Unit)? = null,
    saxListener: (SaxEvent) -> Unit
) = listenForInitial(key)
    .delegate<File>(async = true) { bunch, publisher -> bunch.forEach { publisher(it) } }
    .chain { getStreamOrNull(it) }
    .delegate<SaxEvent>(async = true, handleErrors = {
        if (it is SAXException) parsingErrorHandler?.let { it1 -> it1(it) }
            .let { null } else it
    }) { stream, notifier ->
        val elements = HashMap<String, Deque<Map<String, String>>>()
        saxParser.parse(stream, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) =
                if (qName != null) elements.computeIfAbsent(qName) { LinkedList() }.addFirst(attributes.toMap())
                    .let {} else Unit

            override fun endElement(uri: String?, localName: String?, qName: String?) =
                qName?.let { elements.remove(it) }?.peekFirst()?.let { notifier(SaxEvent(qName, uri, localName, it)) }
                    ?: Unit
        })
        elements.clear()
    }
    .startListening { saxListener(it) }

private fun Attributes.toMap() = mutableMapOf<String, String>().apply {
    for (i in 0..length) {
        getLocalName(i)?.let { put(it, getValue(i)) }
    }
}

val domParser = DocumentBuilderFactory.newInstance().newDocumentBuilder()!!

fun FileProcessorManager.handleDom(key: String, domListener: (Document) -> Unit) = listenForInitial(key)
    .delegate<File>(async = true) { bunch, publisher -> bunch.forEach { publisher(it) } }
    .chain { getStreamOrNull(it) }
    .startListening { val document = domParser.parse(it); domListener(document) }

private fun getStreamOrNull(file: File) = try {
    FileInputStream(file)
} catch (cause: IOException) {
    null
}