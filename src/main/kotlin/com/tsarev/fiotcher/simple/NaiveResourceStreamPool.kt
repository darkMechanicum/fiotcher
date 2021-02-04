package com.tsarev.fiotcher.simple

import com.tsarev.fiotcher.CannotOpenStream
import com.tsarev.fiotcher.ResourceStreamPool
import java.io.InputStream
import java.net.URI

/**
 * Simple implementation, that just opens a connection for
 * each resource, without caching or seek position remembering.
 */
class NaiveResourceStreamPool : ResourceStreamPool {
    override fun getInputStream(resource: URI): InputStream {
        try {
            return resource.toURL().openStream()
        } catch (cause: Throwable) {
            throw CannotOpenStream(resource, cause)
        }
    }
}