package com.tsarev.fiotcher.internal.pool

import java.util.concurrent.SubmissionPublisher

/**
 * Pool, used to synchronize aggregator access.
 */
interface PublisherPool<EventT : Any> {

    /**
     * Get or create new publisher.
     */
    fun getPublisher(key: String): SubmissionPublisher<EventT>

    /**
     * Clear publishers and stop them.
     */
    fun clear()

}