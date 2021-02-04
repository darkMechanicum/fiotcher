package com.tsarev.fiotcher.common

/**
 * Altered in any way resource, wrapped with event type.
 */
data class TypedEvent<T>(
    val event: T,
    val type: EventType
)

