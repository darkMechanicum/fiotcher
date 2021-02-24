package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import com.tsarev.fiotcher.dflt.flows.SingleSubscriptionSubscriber
import com.tsarev.fiotcher.internal.*
import com.tsarev.fiotcher.internal.flow.ChainingListener
import com.tsarev.fiotcher.internal.pool.AggregatorPool
import com.tsarev.fiotcher.internal.pool.ListenerPool
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashMap

/**
 * Default [ListenerPool] implementation.
 */
class DefaultListenerPool(
    /**
     * Aggregators, to listen to.
     */
    private val aggregatorPool: AggregatorPool
) : ListenerPool, Stoppable {

    private val brake = Brake<Unit>()

    /**
     * Registered listeners, by key.
     */
    private val registeredListeners = ConcurrentHashMap<KClassTypedKey<*>, MutableSet<SingleSubscriptionSubscriber<*>>>()

    override fun <EventT : Any> registerListener(
        listener: ChainingListener<EventT>,
        key: KClassTypedKey<EventT>
    ): ChainingListener<EventT> {
        if (listener !is SingleSubscriptionSubscriber<*>)
            throw FiotcherException("Can't use listeners, other that are created by [DefaultWayStation]")
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            validateIsStopping { PoolIsStopped() }
            registeredListeners.computeIfAbsent(key) { Collections.synchronizedSet(HashSet()) } += listener
            // If listener is [SingleSubscriptionSubscriber<*>] and [ChainingListener<EventT>]
            // so definitely it is also a [SingleSubscriptionSubscriber<EventT>]
            aggregatorPool.getAggregator(key).subscribe(listener as SingleSubscriptionSubscriber<EventT>)
        }
        return createListenerWrapper(key, listener as ChainingListener<EventT>)
    }


    override fun deRegisterListener(key: KClassTypedKey<*>, force: Boolean): CompletionStage<*> {
        validateIsStopping { PoolIsStopped() }
        // Check if we need to de register anything.
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null) doDeRegisterListeners(
            key, force, deRegistered.toSet()
        ) else CompletableFuture.completedFuture(Unit)
    }

    override val isStopped get() = brake.get() != null

    override fun stop(force: Boolean) = brake.push { brk ->
        val listenersCopy = HashMap(registeredListeners)
        val allListenersStopFuture =
            if (listenersCopy.isEmpty()) CompletableFuture.completedFuture(Unit)
            else listenersCopy
                .map { doDeRegisterListeners(it.key, force, it.value) }
                .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }
        allListenersStopFuture.thenAccept {
            registeredListeners.clear()
            brk.complete(Unit)
        }
    }

    /**
     * Perform actual listeners de registration.
     */
    private fun <EventT : Any> doDeRegisterListeners(
        key: KClassTypedKey<EventT>,
        force: Boolean,
        listeners: Collection<ChainingListener<*>>
    ): CompletionStage<*> {
        var initial: CompletionStage<*> = CompletableFuture.completedFuture(Unit)
        for (listener in listeners) {
            initial = initial.thenAcceptBoth(doDeRegisterListener(key, force, listener)) { _, _ -> }
        }
        return initial
    }

    /**
     * Perform actual listener de registration.
     */
    private fun <EventT : Any> doDeRegisterListener(
        key: KClassTypedKey<EventT>,
        force: Boolean,
        listener: ChainingListener<*>
    ): CompletionStage<*> {
        val deRegisteredBucket = registeredListeners[key]
        return if (deRegisteredBucket != null && deRegisteredBucket.contains(listener)) {
            // Remove only that listener, that we are de registering.
            listener.stop(force).thenAccept {
                deRegisteredBucket.remove(listener)
            } ?: CompletableFuture.completedFuture(Unit)
        } else {
            CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Create a handle, that stops the tracker and de registers it.
     */
    private fun <EventT : Any> createListenerWrapper(
        key: KClassTypedKey<EventT>,
        listener: ChainingListener<EventT>
    ): ChainingListener<EventT> {
        return object : ChainingListener<EventT> by listener {
            override val isStopped: Boolean
                get() = this@DefaultListenerPool.isStopped || listener.isStopped

            override fun stop(force: Boolean) =
                if (!this@DefaultListenerPool.isStopped) doDeRegisterListener(key, force, listener)
                else CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Throw exception if pool is stopping.
     */
    private fun validateIsStopping(toThrow: () -> Throwable) = if (isStopped) throw toThrow() else Unit

}