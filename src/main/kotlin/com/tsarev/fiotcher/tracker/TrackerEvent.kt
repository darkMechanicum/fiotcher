package com.tsarev.fiotcher.tracker

import com.tsarev.fiotcher.common.TypedEvent
import java.net.URI

typealias TrackerEvent = TypedEvent<Collection<URI>>

/**
 * Bunch of grouped [TrackerEvent]
 */
data class TrackerEventBunch(
    val events: Collection<TrackerEvent>
)