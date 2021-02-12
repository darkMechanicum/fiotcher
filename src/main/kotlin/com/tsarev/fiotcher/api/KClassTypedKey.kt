package com.tsarev.fiotcher.api

import kotlin.reflect.KClass

/**
 * Typed key to use in pools and registers.
 */
data class KClassTypedKey<TypeT : Any>(
    val key: String,
    val klass: KClass<TypeT>
)

/**
 * Create key/type pair from string and kclass.
 */
inline fun <reified T : Any> String.typedKey() = KClassTypedKey(this, T::class)

/**
 * Create key/type pair from string and kclass.
 */
fun <T : Any> String.typedKey(klass: KClass<T>) = KClassTypedKey(this, klass)