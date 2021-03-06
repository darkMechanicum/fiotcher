package com.tsarev.fiotcher.api

/**
 * Base class for library exceptions.
 */
open class FiotcherException : RuntimeException {
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String) : super(message)
}

/**
 * Exception to signal, that tracker has been already registered for some URI.
 */
class TrackerAlreadyRegistered(resource: Any, key: String) :
    FiotcherException("Tracker for resource: $resource and key: $key has been already registered.")

/**
 * Exception to signal, that the pool is stopping and can't register anything.
 */
class PoolIsStopped : FiotcherException("Tracker pool is stopped and can't register anything")
