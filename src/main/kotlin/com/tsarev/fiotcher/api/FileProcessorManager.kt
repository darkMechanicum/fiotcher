package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.internal.Processor
import org.w3c.dom.Document
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.util.concurrent.CompletionStage

/**
 * This is high level entry point for using fiotcher library.
 */
interface FileProcessorManager {

    /**
     * Used processor to handle all operations.
     *
     * Can be used when more precise control is needed.
     */
    val processor: Processor<File>

    /**
     * Start tracking specified [path] with specified type ([key]). Specified [path] must be a directory.
     *
     * @param path directory to track
     * @param key type of this tracked path, to bind to handlers
     * @param recursively whether to track specified directory recursively
     * @throws IllegalArgumentException when passed path is not a directory
     * @throws TrackerAlreadyRegistered if tracker is already registered on same [path] with the same [key]
     * @throws PoolIsStopping if underlying tracker pool is stopping
     * @return a future handle, that will return [Stoppable] - a handle to registered tracker, when registration completes.
     * Invoking that [Stoppable] is equivalent of [stopTracking] with same parameters as passed to [startTracking]
     */
    fun startTracking(path: File, key: String, recursively: Boolean = true): CompletionStage<out Stoppable>

    /**
     * Stop tracking specified [path] with specified type ([key]).
     *
     * This is non cancellable operation.
     *
     * Note: If tracker that is not fully initialized is stopped than no it
     * will not produce any event.
     *
     * @param path directory to stop tracking
     * @param key type of this tracked path, to bind to handlers
     * @param force if set to `false` then attempt to process all discovered elements and then stop
     * @throws PoolIsStopping if underlying tracker pool is stopping
     * @return a future handle, that will complete when stopping completes.
     */
    fun stopTracking(path: File, key: String, force: Boolean = false): CompletionStage<*>

    /**
     * Handle lines async for each changed file, but synchronously within single file.
     *
     * @param key type of handler, to bind to tracker
     * @param linedListener listener logic
     * @throws ListenerRegistryIsStopping if underlying listener registry is stopping
     * @throws ListenerAlreadyRegistered if there is already registered handler for this key
     * @return a handle to created listener, which can stop listener
     */
    fun handleLines(key: String, linedListener: (String) -> Unit): Stoppable

    /**
     * Handle lines async for each changed file.
     *
     * @param key type of handler, to bind to tracker
     * @param fileListener listener logic
     * @throws ListenerRegistryIsStopping if underlying listener registry is stopping
     * @throws ListenerAlreadyRegistered if there is already registered handler for this key
     * @return a handle to created listener, which can stop listener
     */
    fun handleFiles(key: String, fileListener: (File) -> Unit): Stoppable

    /**
     * Handle files with sax parser async for each changed file, but synchronously within it.
     *
     * @param key type of handler, to bind to tracker
     * @param saxListener listener logic
     * @throws ListenerRegistryIsStopping if underlying listener registry is stopping
     * @throws ListenerAlreadyRegistered if there is already registered handler for this key
     * @return a handle to created listener, which can stop listener
     */
    fun handleSax(key: String, saxListener: DefaultHandler): Stoppable

    /**
     * Handle files with dom parser async for each changed file, but synchronously within it.
     *
     * @param key type of handler, to bind to tracker
     * @param domListener listener logic
     * @throws ListenerRegistryIsStopping if underlying listener registry is stopping
     * @throws ListenerAlreadyRegistered if there is already registered handler for this key
     * @return a handle to created listener, which can stop listener
     */
    fun handleDom(key: String, domListener: (Document) -> Unit): Stoppable

}