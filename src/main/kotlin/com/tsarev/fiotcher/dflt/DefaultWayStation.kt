package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.api.flow.ChainingListener
import com.tsarev.fiotcher.api.flow.WayStation
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.flows.DelegatingTransformer
import java.util.concurrent.*

/**
 * Default implementation of [WayStation] that uses [DelegatingTransformer]
 * for asynchronous event transforming and anonymous objects for
 * synchronous transforming.
 */
class DefaultWayStation(
    /**
     * Max queue capacity, used for transformers.
     */
    private val maxTransformerCapacity: Int,

    /**
     * Executor service, used for asynchronous transformers.
     */
    transformerExecutorService: ExecutorService,

    /**
     * Executor, used at stopping.
     */
    private val stoppingExecutorService: ExecutorService,
) : WayStation, Stoppable {

    /**
     * Stopping brake.
     */
    @Volatile
    private var brake: CompletableFuture<Unit>? = null

    /**
     * Proxied executor service to await for all non done transform tasks.
     */
    private val proxiedTransformerExecutorService =
        RememberingExecutorServiceProxy(transformerExecutorService, stoppingExecutorService)

    override val isStopped get() = brake != null

    override fun <ResourceT : Any> createCommonListener(listener: (ResourceT) -> Unit) = CommonListener(listener)

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncChainFrom(transformer: (FromT) -> ToT?) =
        object : ChainingListener<FromT> {
            override fun onNext(item: FromT) = transformer(item)?.let { this@syncChainFrom.onNext(it) } ?: askNext()
            override fun onSubscribe(subscription: Flow.Subscription?) = this@syncChainFrom.onSubscribe(subscription)
            override fun onError(throwable: Throwable?) = this@syncChainFrom.onError(throwable)
            override fun onComplete() = this@syncChainFrom.onComplete()
            override fun stop(force: Boolean) = this@syncChainFrom.stop(force)
            override fun askNext() = this@syncChainFrom.askNext()
            override val isStopped get() = this@syncChainFrom.isStopped
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.syncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        object : ChainingListener<FromT> {
            override fun onNext(item: FromT) =
                transformer(item)?.filterNotNull()?.forEach { this@syncSplitFrom.onNext(it) } ?: askNext()

            override fun onSubscribe(subscription: Flow.Subscription?) = this@syncSplitFrom.onSubscribe(subscription)
            override fun onError(throwable: Throwable?) = this@syncSplitFrom.onError(throwable)
            override fun onComplete() = this@syncSplitFrom.onComplete()
            override fun stop(force: Boolean) = this@syncSplitFrom.stop(force)
            override fun askNext() = this@syncSplitFrom.askNext()
            override val isStopped get() = this@syncSplitFrom.isStopped
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncChainFrom(transformer: (FromT) -> ToT?) =
        asyncSplitFrom<FromT, ToT> { listOf(transformer(it)) }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncSplitFrom(transformer: (FromT) -> Collection<ToT?>?) =
        asyncDelegateFrom<FromT, ToT> { event, publisher ->
            val split = transformer(event)
            split?.filterNotNull()?.forEach { publisher(it) }
        }

    override fun <FromT : Any, ToT : Any> ChainingListener<ToT>.asyncDelegateFrom(
        transformer: (FromT, (ToT) -> Unit) -> Unit
    ) = DelegatingTransformer(
        proxiedTransformerExecutorService,
        maxTransformerCapacity,
        stoppingExecutorService,
        transformer,
        this
    )

    override fun stop(force: Boolean): CompletionStage<*> {
        brake?.let { return@stop it }
        synchronized(this) {
            brake?.let { return@stop it }
            brake = CompletableFuture()
        }

        // Try to terminate fast or await for running and potentially generated tasks.
        if (force) {
            proxiedTransformerExecutorService.fastShutdown()
                .thenAccept { brake?.complete(Unit) }
        } else {
            proxiedTransformerExecutorService.slowShutdown()
                .thenAccept { brake?.complete(Unit) }
        }

        return brake!!
    }

    /**
     * Simple proxy to remember all task futures to wait for them at need.
     */
    private class RememberingExecutorServiceProxy(
        /**
         * Executor, used at stopping.
         */
        private val stoppingExecutorService: ExecutorService,

        /**
         * Delegate.
         */
        private val delegate: ExecutorService,
    ) : ExecutorService by delegate {

        /**
         * List of registered futures.
         */
        private val rememberedFutures = ConcurrentLinkedQueue<Future<*>>()

        /**
         * Overridden variant of shutdown to clear futures and cancel every future.
         */
        fun fastShutdown() = CompletableFuture.runAsync({
            delegate.shutdownNow()
            rememberedFutures.forEach { it.cancel(true) }
            rememberedFutures.clear()
        }, stoppingExecutorService)

        /**
         * Try to wait for all tasks completion, assuming that
         * task generation will eventually stop.
         */
        fun slowShutdown() = CompletableFuture.runAsync({
            while (!rememberedFutures.isEmpty()) {
                val futureIterator = rememberedFutures.iterator()
                while (futureIterator.hasNext()) {
                    val future = futureIterator.next()
                    future.get()
                    futureIterator.remove()
                }
            }
            delegate.shutdown()
        }, stoppingExecutorService)

        override fun execute(command: Runnable) = submit<Unit> { command.run() }.also { rememberedFutures += it }.let {}

        override fun <T : Any?> submit(task: Callable<T>) = delegate.submit(task).also { rememberedFutures += it }

        override fun <T : Any?> submit(task: Runnable, result: T) =
            delegate.submit(task, result).also { rememberedFutures += it }

        override fun submit(task: Runnable) = delegate.submit(task).also { rememberedFutures += it }

        override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>) =
            delegate.invokeAll(tasks).also { rememberedFutures += it.filterNotNull() }

        override fun <T : Any?> invokeAll(
            tasks: MutableCollection<out Callable<T>>,
            timeout: Long,
            unit: TimeUnit
        ) = delegate.invokeAll(tasks, timeout, unit).also { rememberedFutures += it.filterNotNull() }

        override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>) =
            throw UnsupportedOperationException("RememberingExecutorService does not support some batch operations")

        override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit) =
            throw UnsupportedOperationException("RememberingExecutorService does not support some batch operations")

    }
}