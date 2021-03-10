package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.internal.pool.PublisherPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.SubmissionPublisher

/**
 * Simple [SubmissionPublisher] grouping class.
 */
class DefaultPublisherPool<EventT : Any>(
    private val executor: Executor,
    private val maxCapacity: Int
) : PublisherPool<EventT> {

    private val publishers = ConcurrentHashMap<String, SubmissionPublisher<EventT>>()

    override fun getPublisher(key: String): SubmissionPublisher<EventT> {
        return publishers.computeIfAbsent(key) { SubmissionPublisher<EventT>(executor, maxCapacity) }
    }

    override fun clear() {
        val oldPublishers = publishers.toMap()
        publishers.clear()
        oldPublishers.values.forEach { it.close() }
    }
}