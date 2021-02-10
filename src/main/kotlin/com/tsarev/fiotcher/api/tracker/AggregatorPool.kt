package com.tsarev.fiotcher.api.tracker

import com.tsarev.fiotcher.api.TypedEvent
import com.tsarev.fiotcher.dflt.flows.Aggregator

/**
 * Pool, used to synchronize aggregator access.
 */
interface AggregatorPool<WatchT : Any> {

    /**
     * Get or create new aggregator.
     */
    fun getAggregator(key: String): Aggregator<Collection<TypedEvent<WatchT>>>

}