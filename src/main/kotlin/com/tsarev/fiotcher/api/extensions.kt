package com.tsarev.fiotcher.api

import org.w3c.dom.Document
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import kotlin.collections.HashMap

/**
 * Handle changed files lines asynchronously.
 * For details see [ProcessorManager.listenForKey]
 */
fun FileProcessorManager.handleLines(key: String, linedListener: (String) -> Unit) = listenForKey(key)
    .asyncTransform<File> { bunch, publisher -> bunch.forEach { publisher(it) } }
    .startListening {
        val stream = getStreamOrNull(it)
        if (stream != null) {
            val lines = with(Scanner(it)) { mutableListOf<String>().also { while (hasNextLine()) it += nextLine() } }
            lines.forEach(linedListener)
        }
    }

/**
 * Handle changed files asynchronously.
 * For details see [ProcessorManager.listenForKey]
 */
fun FileProcessorManager.handleFiles(key: String, fileListener: (File) -> Unit) = listenForKey(key)
    .asyncTransform<File> { bunch, publisher -> bunch.forEach { publisher(it) } }
    .startListening { fileListener(it) }

/**
 * Simplified SAX event, got when element closing tag is encountered.
 */
data class SaxEvent(
    val element: String,
    val uri: String?,
    val localName: String?,
    val attributes: Map<String, String>,
)

/**
 * Handle changed files asynchronously with SAX parser.
 * For details see [ProcessorManager.listenForKey].
 *
 * <i>Note</i>: when using this parser sax element event is
 * send only when closing tag is reached. To alter this
 * behaviour on could just copy/paste this extension.
 *
 * @param customSaxParser custom sax parser to use
 */
fun FileProcessorManager.handleSax(
    key: String,
    customSaxParser: SAXParser? = null,
    parsingErrorHandler: ((SAXException) -> Unit)? = null,
    saxListener: (SaxEvent) -> Unit
) {
    val defaultSaxParser = SAXParserFactory.newInstance().newSAXParser()!!
    listenForKey(key)
        .asyncTransform<File> { bunch, publisher -> bunch.forEach { publisher(it) } }
        .startListening(
            handleErrors = {
                if (it is SAXException) parsingErrorHandler?.let { handler -> handler(it) }
                    .let { null } else it
            }
        ) { file ->
            val elements = HashMap<String, Deque<Map<String, String>>>()
            (customSaxParser ?: defaultSaxParser).parse(file, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) =
                    if (qName != null) elements.computeIfAbsent(qName) { LinkedList() }.addFirst(attributes.toMap())
                        .let {} else Unit

                override fun endElement(uri: String?, localName: String?, qName: String?) =
                    qName?.let { elements.remove(it) }?.peekFirst()
                        ?.let { saxListener(SaxEvent(qName, uri, localName, it)) }
                        ?: Unit
            })
            elements.clear()
        }
}

private fun Attributes.toMap() = mutableMapOf<String, String>().apply {
    for (i in 0..length) {
        getLocalName(i)?.let { put(it, getValue(i)) }
    }
}

/**
 * Handle changed files asynchronously with DOM parser.
 * For details see [ProcessorManager.listenForKey]
 *
 * @param customDomParser custom dom parser to use
 */
fun FileProcessorManager.handleDom(
    key: String,
    customDomParser: DocumentBuilder? = null,
    domListener: (Document) -> Unit
) {
    val defaultDomParser = DocumentBuilderFactory.newInstance().newDocumentBuilder()!!
    listenForKey(key)
        .asyncTransform<File> { bunch, publisher -> bunch.forEach { publisher(it) } }
        .startListening { val document = (customDomParser ?: defaultDomParser).parse(it); domListener(document) }
}

/**
 * Get stream from file, or null if no file is found.
 */
private fun getStreamOrNull(file: File) = try {
    FileInputStream(file)
} catch (cause: IOException) {
    null
}