package com.tsarev.fiotcher

import java.io.InputStream
import java.net.URI

/**
 * Raises, when an error occurred while opening stream.
 */
class CannotOpenStream(resource: URI, cause: Throwable) :
    RuntimeException("Cannot open stream for $resource", cause)

/**
 * Interface that hides [InputStream] creation.
 */
interface ResourceStreamPool {

    /**
     * Get input stream for selected resource.
     *
     * @throws CannotOpenStream when stream open failed
     */
    fun getInputStream(resource: URI): InputStream
}