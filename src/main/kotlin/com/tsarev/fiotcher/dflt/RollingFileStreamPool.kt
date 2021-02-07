package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.ResourceStreamPool
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Caching implementation, that tries to remember last position.
 */
class RollingFileStreamPool : ResourceStreamPool<File> {

    /**
     * Captured read counters.
     */
    private val positions = ConcurrentHashMap<File, AtomicLong>()

    override fun getInputStream(resource: File): InputStream? {
        return try {
            val skip = positions.computeIfAbsent(resource) { AtomicLong(0) }
            val inner = FileInputStream(resource)
            inner.skip(skip.get())
            object : InputStream() {
                override fun read(): Int {
                    val result = inner.read()
                    // Skip EOF, it dos not count as 'read character'.
                    if (result != -1) positions.computeIfPresent(resource) { _, j -> j.incrementAndGet(); j }
                    return result
                }
            }
        } catch (cause: IOException) {
            null
        }
    }
}