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

/**
 * Holder for otherwise erased class info.
 */
abstract class KClassHolder<Type>

/**
 * Typed key to use in pools and registers.
 */
class KClassTypedKey<TypeT : Any>(
    private val key: String,
    private val klass: Lazy<KClass<out KClassHolder<TypeT>>>
) {
    override fun toString() = "[${key},${klass.value}]"
    // Override equals and hash code to use lazy value.
    override fun hashCode() = (key.hashCode() * 31) xor klass.value.hashCode()
    override fun equals(other: Any?) = this === other ||
            (other is KClassTypedKey<*> && other.key == key && other.klass.value == klass.value)
}

/**
 * Create key/type pair from string and kclass.
 */
inline fun <reified T : Any> String.typedKey() = KClassTypedKey(this, lazy {
    val value = object : KClassHolder<T>() {}
    value::class
})