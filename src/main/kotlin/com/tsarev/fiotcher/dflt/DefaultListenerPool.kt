package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import com.tsarev.fiotcher.internal.*
import com.tsarev.fiotcher.internal.pool.ListenerPool
import com.tsarev.fiotcher.internal.pool.PublisherPool
import java.util.*
import java.util.concurrent.*
import kotlin.collections.HashMap

/**
 * Default [ListenerPool] implementation.
 */
class DefaultListenerPool<InitialT : Any>(
    /**
     * Initial publishers.
     */
    private val publisherPool: PublisherPool<EventWithException<InitialT>>
) : ListenerPool<InitialT>, Stoppable, StoppableBrakeMixin<Unit> {

    override val stopBrake = Brake<Unit>()

    /**
     * Registered listeners, by key.
     */
    private val registeredListeners = ConcurrentHashMap<String, MutableSet<ChainingListener<*>>>()

    override fun registerListener(
        listener: ChainingListener<InitialT>,
        key: String
    ): ChainingListener<InitialT> {
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            if (stopBrake.isPushed) return listener
            registeredListeners.computeIfAbsent(key) { Collections.synchronizedSet(HashSet()) } += listener
            publisherPool.getPublisher(key).subscribe(listener)
        }
        return createListenerWrapper(key, listener)
    }


    override fun deRegisterListener(key: String, force: Boolean): CompletionStage<*> {
        // Return stopping brake if requested to deregister something during stopping.
        if (stopBrake.isPushed) return this.doStop()
        // Check if we need to de register anything.
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null) doDeRegisterListeners(
            key, force, deRegistered.toSet()
        ) else CompletableFuture.completedFuture(Unit)
    }

    override fun doStop(force: Boolean, exception: Throwable?) = stopBrake.push {
        val listenersCopy = HashMap(registeredListeners)
        val allListenersStopFuture =
            if (listenersCopy.isEmpty()) CompletableFuture.completedFuture(Unit)
            else listenersCopy
                .map { doDeRegisterListeners(it.key, force, it.value.toSet()) }
                .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }
        allListenersStopFuture.whenComplete { _, _ ->
            registeredListeners.clear()
            complete(Unit)
        }
    }

    /**
     * Perform actual listeners de registration.
     */
    private fun doDeRegisterListeners(
        key: String,
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
    private fun doDeRegisterListener(
        key: String,
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
        key: String,
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

}