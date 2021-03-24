package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FiotcherException

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

internal fun <T : Any> T.asSuccess() = EventWithException(this, null)
internal fun <T : Any> Throwable.asFailure() = EventWithException<T>(null, this)