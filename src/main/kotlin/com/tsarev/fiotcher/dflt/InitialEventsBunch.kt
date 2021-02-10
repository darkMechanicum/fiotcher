package com.tsarev.fiotcher.dflt

/**
 * Special class to generate unique [KClassTypedKey]
 * for initial tracker events.
 */
class InitialEventsBunch<T : Any>(delegate: Collection<T>) : Collection<T> by delegate