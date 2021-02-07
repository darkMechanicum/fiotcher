package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.TypedEvent

typealias TrackerEvent<WatchT> = TypedEvent<Collection<WatchT>>

/**
 * Bunch of grouped [TrackerEvent]
 */
data class TrackerEventBunch<WatchT : Any>(
    val events: List<TrackerEvent<WatchT>>
)