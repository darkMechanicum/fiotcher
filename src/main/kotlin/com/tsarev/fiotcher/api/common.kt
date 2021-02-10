package com.tsarev.fiotcher.api

import kotlin.reflect.KClass

data class EventWithException<EventT : Any>(
    val event: EventT?,
    val exception: Throwable?
) {
    init {
        if (event === null && exception === null) throw FiotcherException("Can't send empty event")
    }

    fun ifSuccess(block: (EventT) -> Unit) = if (event != null) block(event) else Unit
    fun ifFailed(block: (Throwable) -> Unit) = if (exception != null) block(exception) else Unit
}

fun <T : Any> T.asSuccess() = EventWithException(this, null)
fun <T : Any> Throwable.asFailure() = EventWithException<T>(null, this)

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
inline fun <reified T : Any> String.typedKey() = KClassTypedKey(this, T::class)