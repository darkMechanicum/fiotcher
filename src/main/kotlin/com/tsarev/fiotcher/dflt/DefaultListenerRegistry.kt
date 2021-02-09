package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.ListenerAlreadyRegistered
import com.tsarev.fiotcher.api.ListenerRegistryIsStopping
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.tracker.ListenerRegistry
import com.tsarev.fiotcher.api.tracker.TrackerEventBunch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [ListenerRegistry] implementation.
 */
class DefaultListenerRegistry<WatchT : Any> : ListenerRegistry<WatchT>, Stoppable {

    private val brake = Brake<Unit>()

    /**
     * Registered listeners, by key.
     */
    private val registeredListeners = ConcurrentHashMap<String, ChainingListener<TrackerEventBunch<WatchT>>>()

    override fun registerListener(
        listener: ChainingListener<TrackerEventBunch<WatchT>>, key: String
    ): ChainingListener<TrackerEventBunch<WatchT>> {
        // Sync on the pool to handle stopping properly.
        synchronized(this) {
            checkIsStopping { ListenerRegistryIsStopping() }
            if (registeredListeners.putIfAbsent(key, listener) != null) throw ListenerAlreadyRegistered(key)
        }
        return createListenerWrapper(key, listener)
    }

    override fun deRegisterListener(key: String, force: Boolean): CompletionStage<*> {
        checkIsStopping { ListenerRegistryIsStopping() }
        // Check if we need to de register anything.
        val deRegistered = registeredListeners[key]
        return if (deRegistered != null) doDeRegisterListener(
            key, force, deRegistered
        ) else CompletableFuture.completedFuture(Unit)
    }

    override val isStopped get() = brake.get() != null

    override fun stop(force: Boolean) = brake.push { brk ->
        val listenersCopy = HashMap(registeredListeners)
        registeredListeners.clear()
        val allListenersStopFuture = listenersCopy
            .map { deRegisterListener(it.key, force) }
            .reduce { first, second -> first.thenAcceptBoth(second) { _, _ -> } }
        allListenersStopFuture.thenAccept {
            brk.complete(Unit)
        }
    }

    /**
     * Perform actual listener de registration.
     */
    private fun doDeRegisterListener(
        key: String,
        force: Boolean,
        listener: ChainingListener<TrackerEventBunch<WatchT>>
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
    private fun createListenerWrapper(
        key: String,
        listener: ChainingListener<TrackerEventBunch<WatchT>>
    ): ChainingListener<TrackerEventBunch<WatchT>> {
        return object : ChainingListener<TrackerEventBunch<WatchT>> by listener {
            override val isStopped: Boolean
                get() = this@DefaultListenerRegistry.isStopped || listener.isStopped

            override fun stop(force: Boolean) =
                if (!this@DefaultListenerRegistry.isStopped) doDeRegisterListener(key, force, listener)
                else CompletableFuture.completedFuture(Unit)
        }
    }

    /**
     * Throw exception if pool is stopping.
     */
    private fun checkIsStopping(toThrow: () -> Throwable) = if (isStopped) throw toThrow() else Unit

}