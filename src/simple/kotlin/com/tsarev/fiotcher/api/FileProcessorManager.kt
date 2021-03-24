package com.tsarev.fiotcher.api

import java.io.File

/**
 * Special class to generate events
 * for initial tracker events.
 */
class InitialEventsBunch<T : Any>(delegate: Collection<T>) : Collection<T> by delegate {
    constructor(value: T) : this(listOf(value))
}

/**
 * Convenient interface to control shutdown process more
 * precisely.
 */
interface Stoppable {

    /**
     * Stop this [Stoppable] asynchronously.
     *
     * @param force try to force
     */
    fun stop(force: Boolean = false)
}

/**
 * This is high level entry point for using fiotcher library.
 */
interface FileProcessorManager : Stoppable {

    /**
     * Start tracking specified [path] with specified type ([key]). Specified [path] must be a directory.
     *
     * @param path directory to track
     * @param key type of this tracked path, to bind to handlers
     * @param recursively whether to track specified directory recursively
     */
    fun startTrackingFile(path: File, key: String, recursively: Boolean = true): Boolean

    /**
     * Stop tracking specified [resource] with specified type ([key]).
     *
     * This is non cancellable operation.
     *
     * Note: If tracker that is not fully initialized is stopped than no it
     * will not produce any event.
     *
     * @param resource resource to stop tracking
     * @param key type of this tracked path, to bind to handlers
     * @param force if set to `false` then attempt to process all discovered elements and then stop
     */
    fun stopTracking(resource: File, key: String, force: Boolean = false)

    /**
     * Listen for tracker produced events.
     *
     * @param key a key to listen to
     * @param handleErrors error handling function to process errors or to pass them down the chain.
     *        If exception is thrown inside the handler, than listener chain will stop
     * @param listener event processing logic
     * @return a handle to stop listener
     */
    fun startListening(
        key: String,
        handleErrors: ((Throwable) -> Throwable?)? = null,
        listener: (InitialEventsBunch<File>) -> Unit
    ): Stoppable?

    /**
     * Stop listening to tracker events by key.
     *
     * @param key type of this tracked path, to bind to handlers
     * @param force force if set to `false` then attempt to process all passed events and then stop
     */
    fun stopListening(key: String, force: Boolean = false)

}