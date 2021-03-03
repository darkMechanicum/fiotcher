package com.tsarev.fiotcher.api

/**
 * Special class to generate unique [KClassTypedKey]
 * for initial tracker events.
 */
class InitialEventsBunch<T : Any>(delegate: Collection<T>) : Collection<T> by delegate {
    constructor(value: T) : this(listOf(value))
}