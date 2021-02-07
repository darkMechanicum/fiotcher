package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.CannotOpenStream
import com.tsarev.fiotcher.ResourceStreamPool
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