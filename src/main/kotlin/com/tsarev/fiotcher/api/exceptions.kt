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
 * Exception to signal, that listener is stopped.
 */
class ListenerIsStopped(message: String) : FiotcherException(message)

/**
 * Exception to signal, that tracker listener has been already registered for some key.
 */
class ListenerAlreadyRegistered(key: KClassTypedKey<*>) : FiotcherException("Listener for key: $key has been already registered.")

/**
 * Exception to signal, that listener registry is stopping and can't register anything.
 */
class ListenerRegistryIsStopping
    : FiotcherException("Listener registry is stopping and can't register anything")

/**
 * Exception to signal, that tracker has been already registered for some URI.
 */
class TrackerAlreadyRegistered(resource: Any, key: String) :
    FiotcherException("Tracker for resource: $resource and key: $key has been already registered.")

/**
 * Exception to signal, that tracker pool is stopping and can't register anything.
 */
class PoolIsStopping
    : FiotcherException("Tracker pool is stopping and can't register anything")