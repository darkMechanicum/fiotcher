package com.tsarev.fiotcher.api

import kotlin.reflect.KClass

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
class TypedEvents<WatchT>(
    collection: Collection<TypedEvent<WatchT>>
) : Collection<TypedEvent<WatchT>> by collection

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

/**
 * Typed key to use in pools and registers.
 */
data class KClassTypedKey<TypeT : Any>(
    private val key: String,
    private val klass: KClass<TypeT>
)

/**
 * Create key/type pair from string and kclass.
 */
inline fun <reified T : Any> String.typedKey() = KClassTypedKey(this, T::class )