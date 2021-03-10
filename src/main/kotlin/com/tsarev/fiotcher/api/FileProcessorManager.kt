package com.tsarev.fiotcher.api

import java.io.File
import java.util.concurrent.CompletionStage

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
     * @throws IllegalArgumentException when passed path is not a directory
     * @throws TrackerAlreadyRegistered if tracker is already registered on same [path] with the same [key]
     * @return a future handle, that will return [Stoppable] - a handle to registered tracker, when registration completes.
     * Invoking that [Stoppable] is equivalent of [stopTracking] with same parameters as passed to [startTrackingFile].
     * If pool is stopped, then future will be completed exceptionally with [PoolIsStopped] exception.
     */
    fun startTrackingFile(path: File, key: String, recursively: Boolean = true): CompletionStage<out Stoppable>

}