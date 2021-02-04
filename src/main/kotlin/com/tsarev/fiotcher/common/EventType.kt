package com.tsarev.fiotcher.common

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