package com.tsarev.fiotcher.tracker

import java.net.URI

/**
 * Bunch of grouped [TrackerEvent]
 */
data class TrackerEventBunch(
    val events: Collection<TrackerEvent>
)

/**
 * Event that points to some changed resource.
 */
data class TrackerEvent(
    val resource: URI,
    val type: Type
) {
    enum class Type {
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
}