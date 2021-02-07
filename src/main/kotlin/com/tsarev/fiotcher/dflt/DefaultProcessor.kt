package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Processor

class DefaultProcessor<WatchT : Any> : Processor<WatchT> {

    override val wayStation = DefaultWayStation()

    private val _defaultTrackerPool = DefaultTrackerPool<WatchT>()

    override val trackerPool get() = _defaultTrackerPool

    override val trackerListenerRegistry get() = _defaultTrackerPool

}