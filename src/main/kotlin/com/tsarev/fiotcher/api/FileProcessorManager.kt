package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.api.util.Stoppable
import org.w3c.dom.Document
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

/**
 * Convenient grouping of common listeners
 * creation process.
 */
interface FileProcessorManager {

    val processor: Processor<File>

    fun startTracking(path: File, key: String, recursively: Boolean = true): CompletionStage<Stoppable>

    fun stopTracking(path: File, key: String, force: Boolean = false): CompletionStage<*>

    fun handleLines(key: String, linedListener: (String) -> Unit): Stoppable
    fun handleFiles(key: String, fileListener: (File) -> Unit): Stoppable
    fun handleSax(key: String, saxListener: DefaultHandler): Stoppable
    fun handleDom(key: String, domListener: (Document) -> Unit): Stoppable

}