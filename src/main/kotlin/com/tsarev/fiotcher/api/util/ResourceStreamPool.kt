package com.tsarev.fiotcher.api.util

import java.io.InputStream

/**
 * Raises, when an error occurred while opening stream.
 */
class CannotOpenStream(resource: Any, cause: Throwable) :
    RuntimeException("Cannot open stream for $resource", cause)

/**
 * Interface that hides [InputStream] creation.
 */
interface ResourceStreamPool<ResourceT : Any> {

    /**
     * Get input stream for selected resource.
     *
     * @throws CannotOpenStream when stream open failed
     */
    fun getInputStream(resource: ResourceT): InputStream?
}