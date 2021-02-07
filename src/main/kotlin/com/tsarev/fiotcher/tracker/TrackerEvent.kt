package com.tsarev.fiotcher.tracker

import com.tsarev.fiotcher.common.TypedEvent

typealias TrackerEvent<WatchT> = TypedEvent<Collection<WatchT>>

/**
 * Bunch of grouped [TrackerEvent]
 */
data class TrackerEventBunch<WatchT : Any>(
    val events: List<TrackerEvent<WatchT>>
)