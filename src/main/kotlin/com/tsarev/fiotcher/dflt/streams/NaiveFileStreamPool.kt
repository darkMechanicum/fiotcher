package com.tsarev.fiotcher.dflt.streams

import com.tsarev.fiotcher.api.util.CannotOpenStream
import com.tsarev.fiotcher.api.util.ResourceStreamPool
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Simple implementation, that just opens a connection for
 * each resource, without caching or seek position remembering.
 */
class NaiveFileStreamPool : ResourceStreamPool<File> {
    override fun getInputStream(resource: File): InputStream? {
        return try {
            FileInputStream(resource)
        } catch (cause: IOException) {
            null
        }
    }
}