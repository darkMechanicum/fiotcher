package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.api.tracker.AggregatorListenerRegistry
import com.tsarev.fiotcher.api.tracker.TrackerPool

/**
 * Low level entry point API for registering/de registering listeners and trackers and
 * also for controlling processor lifecycle.
 */
interface Processor<WatchT : Any> {

    /**
     * Tracker pool, used by this processor.
     */
    val trackerPool: TrackerPool<WatchT>

    /**
     * Tracker listeners, used by this processor.
     */
    val trackerListenerRegistry: AggregatorListenerRegistry<WatchT>

    /**
     * Grouping of intermediate processing.
     */
    val wayStation: WayStation

}