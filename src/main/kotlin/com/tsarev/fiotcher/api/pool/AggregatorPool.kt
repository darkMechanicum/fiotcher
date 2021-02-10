package com.tsarev.fiotcher.api.pool

import com.tsarev.fiotcher.api.KClassTypedKey
import com.tsarev.fiotcher.dflt.flows.Aggregator

/**
 * Pool, used to synchronize aggregator access.
 */
interface AggregatorPool {

    /**
     * Get or create new aggregator.
     */
    fun <EventT : Any> getAggregator(key: KClassTypedKey<EventT>): Aggregator<EventT>

}