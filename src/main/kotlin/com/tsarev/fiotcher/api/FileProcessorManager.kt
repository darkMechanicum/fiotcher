package com.tsarev.fiotcher.api

import java.io.File
import java.util.concurrent.Future

/**
 * This is high level entry point for using fiotcher library.
 */
interface FileProcessorManager : ProcessorManager<File> {

    /**
     * Start tracking specified [path] with specified type ([key]). Specified [path] must be a directory.
     *
     * @param path directory to track
     * @param key type of this tracked path, to bind to handlers
     * @param recursively whether to track specified directory recursively
     * @return a future handle, that will return [Stoppable] - a handle to registered tracker, when registration completes.
     * Invoking that [Stoppable] is equivalent of [stopTracking] with same parameters as passed to [startTrackingFile].
     * If pool is stopped, then future will be completed exceptionally with [PoolIsStopped] exception.
     * If other tracker is already registered with passed pair, then handle will be completed
     * exceptionally with [TrackerAlreadyRegistered] exception.
     */
    fun startTrackingFile(path: File, key: String, recursively: Boolean = true): Future<out Stoppable>

}