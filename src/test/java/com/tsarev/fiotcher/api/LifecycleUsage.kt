package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.util.AsyncTestEvents
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LifecycleUsage {

    @TempDir
    lateinit var tempDir: File

    private val testAsync = AsyncTestEvents()

    @Test
    fun `test forcible stop`() {

    }

    @Test
    fun `test graceful stop`() {

    }

}