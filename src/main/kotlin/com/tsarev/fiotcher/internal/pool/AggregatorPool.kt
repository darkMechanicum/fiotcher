package com.tsarev.fiotcher.internal.pool

import com.tsarev.fiotcher.dflt.flows.Aggregator
import com.tsarev.fiotcher.api.KClassTypedKey

/**
 * Pool, used to synchronize aggregator access.
 */
interface AggregatorPool {

    /**
     * Get or create new aggregator.
     */
    fun <EventT : Any> getAggregator(key: KClassTypedKey<EventT>): Aggregator<EventT>

}