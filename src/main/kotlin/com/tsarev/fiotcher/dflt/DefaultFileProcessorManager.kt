package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.FileProcessorManager
import com.tsarev.fiotcher.api.InitialEventsBunch
import com.tsarev.fiotcher.api.ProcessorManager
import com.tsarev.fiotcher.api.Stoppable
import com.tsarev.fiotcher.dflt.flows.CommonListener
import com.tsarev.fiotcher.dflt.trackers.FileSystemTracker
import com.tsarev.fiotcher.internal.Processor
import com.tsarev.fiotcher.internal.flow.ChainingListener
import java.io.File
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultFileProcessorManager(
    /**
     * Executor service, used for processing.
     */
    private val executorService: ExecutorService = Executors.newCachedThreadPool(),

    private val processor: Processor<File> = DefaultProcessor(executorService)
) : FileProcessorManager, Stoppable by processor {

    override fun startTrackingFile(path: File, key: String, recursively: Boolean): CompletionStage<out Stoppable> {
        val fileSystemTracker = FileSystemTracker(recursive = recursively)
        return processor.startTracker(path, fileSystemTracker, key)
    }

    override fun stopTracking(resource: File, key: String, force: Boolean): CompletionStage<*> {
        return processor.stopTracker(resource, key, force)
    }

    override fun listenForKey(key: String): ProcessorManager.ListenerBuilder<InitialEventsBunch<File>> {
        return ListenerBuilderInitial { processor.registerListener(it, key) }
    }

    override fun stopListening(key: String, force: Boolean) =
        processor.deRegisterListener(key, force)

    abstract inner class DefaultListenerBuilderBase<InitialT : Any, EventT : Any, PreviousT : Any>(
        private val registerListener: (ChainingListener<InitialEventsBunch<InitialT>>) -> Stoppable
    ) : ProcessorManager.ListenerBuilder<EventT> {

        override fun <NextT : Any> asyncTransform(
            handleErrors: ((Throwable) -> Throwable?)?,
            transformer: (EventT, (NextT) -> Unit) -> Unit
        ) = ListenerBuilderIntermediate<InitialT, NextT, EventT>(
            registerListener, this
        ) {
            it.asyncDelegateFrom(
                executor = executorService,
                maxCapacity = 256,
                transformer = transformer,
                handleErrors = handleErrors
            )
        }

        override fun startListening(
            handleErrors: ((Throwable) -> Throwable?)?,
            listener: (EventT) -> Unit
        ): Stoppable {
            val lastListener = CommonListener(handleErrors = handleErrors, listener = listener)
            val initialListener = chain(lastListener)
            return registerListener(initialListener)
        }

        abstract fun chain(current: ChainingListener<EventT>): ChainingListener<InitialEventsBunch<InitialT>>

    }

    inner class ListenerBuilderInitial<InitialT : Any>(
        registerListener: (ChainingListener<InitialEventsBunch<InitialT>>) -> Stoppable
    ) : DefaultListenerBuilderBase<InitialT, InitialEventsBunch<InitialT>, Unit>(registerListener) {
        override fun chain(current: ChainingListener<InitialEventsBunch<InitialT>>) = current
    }

    inner class ListenerBuilderIntermediate<InitialT : Any, EventT : Any, PreviousT : Any>(
        registerListener: (ChainingListener<InitialEventsBunch<InitialT>>) -> Stoppable,
        private val prev: DefaultListenerBuilderBase<InitialT, PreviousT, *>,
        private val chainer: ((ChainingListener<EventT>) -> ChainingListener<PreviousT>),
    ) : DefaultListenerBuilderBase<InitialT, EventT, PreviousT>(registerListener) {
        override fun chain(current: ChainingListener<EventT>): ChainingListener<InitialEventsBunch<InitialT>> {
            val previous = chainer(current)
            return prev.chain(previous)
        }
    }

}