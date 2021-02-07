package com.tsarev.fiotcher

import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.dflt.DefaultProcessor
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File

fun main() {

    val manager = DefaultFileProcessorManager(DefaultProcessor())

    manager.startTracking(File("/home/alex/projects/fiotcher/src/main/resources"), "key1")
    manager.startTracking(File("/home/alex/projects/fiotcher/src/main/resources/inner"), "key2")

    manager.handleSax("key1", object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            println("Hello $qName!")
            println("Attrs are: $attributes")
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            println("By $qName!")
        }
    })

}