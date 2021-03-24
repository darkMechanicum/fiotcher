package com.tsarev.fiotcher.api

/**
 * Base class for library exceptions.
 */
open class FiotcherException : RuntimeException {
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(message: String) : super(message)
}
