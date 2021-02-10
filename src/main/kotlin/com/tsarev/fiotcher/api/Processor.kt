package com.tsarev.fiotcher.api

import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.api.pool.ListenerPool
import com.tsarev.fiotcher.api.pool.TrackerPool

/**
 * Low level entry point API for registering/de registering listeners and trackers and
 * also for controlling processor lifecycle.
 */
interface Processor<WatchT : Any> : TrackerPool<WatchT>, ListenerPool, WayStation