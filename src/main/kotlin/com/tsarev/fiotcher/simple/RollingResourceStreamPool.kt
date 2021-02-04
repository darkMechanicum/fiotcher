package com.tsarev.fiotcher.simple

import com.tsarev.fiotcher.CannotOpenStream
import com.tsarev.fiotcher.ResourceStreamPool
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Caching implementation, that tries to remember last position.
 */
class RollingResourceStreamPool : ResourceStreamPool {

    /**
     * Captured read counters.
     */
    private val positions = ConcurrentHashMap<URI, Long>()

    override fun getInputStream(resource: URI): InputStream {
        try {
            val skip = positions.computeIfAbsent(resource) { 0 }
            val inner = resource.toURL().openStream()
            inner.skip(skip)
            return object : InputStream() {
                override fun read(): Int {
                    val result = inner.read()
                    // Skip EOF.
                    if (result != -1) positions.computeIfPresent(resource) { _, j -> j + 1 }
                    return result
                }
            }
        } catch (cause: IOException) {
            throw CannotOpenStream(resource, cause)
        }
    }
}