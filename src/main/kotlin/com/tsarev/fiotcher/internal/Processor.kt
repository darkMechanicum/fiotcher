package com.tsarev.fiotcher.internal

import com.tsarev.fiotcher.internal.flow.WayStation
import com.tsarev.fiotcher.internal.pool.ListenerPool
import com.tsarev.fiotcher.internal.pool.TrackerPool

/**
 * Low level entry point API for registering/de registering listeners and trackers and
 * also for controlling processor lifecycle.
 */
interface Processor<WatchT : Any> : TrackerPool<WatchT>, ListenerPool, WayStation