package com.tsarev.fiotcher.api

import org.w3c.dom.Document
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Future

/**
 * Convenient grouping of common listeners
 * creation process.
 */
interface FileProcessorManager {

    val processor: Processor<File>

    fun startTracking(path: String, key: String, recursively: Boolean = true): Stoppable
    fun startTracking(path: Path, key: String, recursively: Boolean = true): Stoppable
    fun startTracking(path: File, key: String, recursively: Boolean = true): Stoppable

    fun stopTracking(path: String, key: String? = null, force: Boolean = false): Future<*>
    fun stopTracking(path: Path, key: String? = null, force: Boolean = false): Future<*>
    fun stopTracking(path: File, key: String? = null, force: Boolean = false): Future<*>

    fun handleLines(key: String? = null, linedListener: (String) -> Unit): Stoppable
    fun handleFiles(key: String? = null, linedListener: (File) -> Unit): Stoppable
    fun handleSax(key: String? = null, saxListener: DefaultHandler): Stoppable
    fun handleDom(key: String? = null, domListener: (Document) -> Unit): Stoppable

}