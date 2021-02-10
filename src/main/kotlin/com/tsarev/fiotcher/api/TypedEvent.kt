package com.tsarev.fiotcher.api

/**
 * Resource, wrapped with event type.
 */
data class TypedEvent<T>(
    val event: T,
    val type: EventType
)

/**
 * Simple alias for collection of typed events.
 */
typealias TypedEvents<WatchT> = Collection<TypedEvent<WatchT>>

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

/**
 * Create typed event from type.
 */
infix fun <T> T.withType(type: EventType) = TypedEvent(this, type)
