package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.util.AsyncTestEvents
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JUnitXmlLiveUsage {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `basic single directory usage with defaults`() {

    }

}