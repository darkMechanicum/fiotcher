package com.tsarev.fiotcher

import com.tsarev.fiotcher.api.handleSax
import com.tsarev.fiotcher.dflt.DefaultFileProcessorManager
import com.tsarev.fiotcher.dflt.DefaultProcessor
import java.io.File

fun main() {

    val manager = DefaultFileProcessorManager(DefaultProcessor())
    manager.startTrackingFile(File("/home/alex/projects/fiotcher/src/main/resources"), "key1")
    manager.startTrackingFile(File("/home/alex/projects/fiotcher/src/main/resources/inner"), "key2")
    manager.handleSax("key1", { it.printStackTrace() }) {
        println("${it.element} with ${it.attributes.size} attributes (${it.attributes})")
    }

}