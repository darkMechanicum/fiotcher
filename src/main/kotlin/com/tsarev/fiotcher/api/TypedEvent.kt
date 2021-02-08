package com.tsarev.fiotcher.api

/**
 * Resource, wrapped with event type.
 */
data class TypedEvent<T>(
    val event: T,
    val type: EventType
)

/**
 * What happened to resource.
 */
enum class EventType {
    /**
     * Resource is created.
     */
    CREATED,

    /**
     * Resource content is changed in any way.
     */
    CHANGED,

    /**
     * Resource is deleted.
     */
    DELETED
}
