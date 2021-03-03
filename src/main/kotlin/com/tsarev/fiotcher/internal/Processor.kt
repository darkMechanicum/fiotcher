package com.tsarev.fiotcher.internal

import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.internal.pool.ListenerPool
import com.tsarev.fiotcher.internal.pool.TrackerPool

/**
 * Low level entry point API for registering/de registering listeners and trackers and
 * also for controlling processor lifecycle.
 */
interface Processor<WatchT : Any> : TrackerPool<WatchT>, ListenerPool<InitialEventsBunch<WatchT>>, Stoppable