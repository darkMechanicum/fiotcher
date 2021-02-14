package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import com.tsarev.fiotcher.dflt.flows.SingleSubscriptionSubscriber
import com.tsarev.fiotcher.internal.*
import com.tsarev.fiotcher.internal.flow.ChainingListener
import com.tsarev.fiotcher.internal.pool.AggregatorPool
import com.tsarev.fiotcher.internal.pool.ListenerPool
import java.util.concurrent.*

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
    private val registeredListeners = ConcurrentHashMap<KClassTypedKey<*>, SingleSubscriptionSubscriber<*>>()

    override fun <EventT : Any> registerListener(
        listener: ChainingListener<EventT>,
        key: KClassTypedKey<EventT>
    ): ChainingListener<EventT> {
        if (listener !is SingleSubscriptionSubscriber<*>)
            throw FiotcherException("Can't use listeners, other that are created by [DefaultWayStation]")
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            checkIsStopping { PoolIsStopped() }
            if (registeredListeners.putIfAbsent(key, listener) != null) throw ListenerAlreadyRegistered(
                key.key,
                key.klass
            )
            // If listener is [SingleSubscriptionSubscriber<*>] and [ChainingListener<EventT>]
            // so definitely it is also a [SingleSubscriptionSubscriber<EventT>]
            aggregatorPool.getAggregator(key).subscribe(listener as SingleSubscriptionSubscriber<EventT>)
        }
        return createListenerWrapper(key, listener as ChainingListener<EventT>)
    }


    override fun deRegisterListener(key: KClassTypedKey<*>, force: Boolean): CompletionStage<*> {
        checkIsStopping { PoolIsStopped() }
        // Check if we need to de register anything.
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null) doDeRegisterListener(
            key, force, deRegistered
        ) else CompletableFuture.completedFuture(Unit)
    }

    override val isStopped get() = brake.get() != null

    override fun stop(force: Boolean) = brake.push { brk ->
        val listenersCopy = HashMap(registeredListeners)
        val allListenersStopFuture =
            if (listenersCopy.isEmpty()) CompletableFuture.completedFuture(Unit)
            else listenersCopy
                .map { doDeRegisterListener(it.key, force, it.value) }
                .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }
        allListenersStopFuture.thenAccept {
            registeredListeners.clear()
            brk.complete(Unit)
        }
    }

    /**
     * Perform actual listener de registration.
     */
    private fun <EventT : Any> doDeRegisterListener(
        key: KClassTypedKey<EventT>,
        force: Boolean,
        listener: ChainingListener<*>
    ): CompletionStage<*> {
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null && listener === deRegistered) {
            // Remove only that listener, that we are de registering.
            deRegistered.stop(force).thenAccept {
                registeredListeners.computeIfPresent(key) { _, old ->
                    if (old === listener) null else old
                }
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
    private fun checkIsStopping(toThrow: () -> Throwable) = if (isStopped) throw toThrow() else Unit

}