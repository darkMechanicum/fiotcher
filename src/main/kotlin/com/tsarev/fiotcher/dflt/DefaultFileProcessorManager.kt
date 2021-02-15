package com.tsarev.fiotcher.dflt

import com.tsarev.fiotcher.api.*
import com.tsarev.fiotcher.dflt.trackers.FileSystemTracker
import com.tsarev.fiotcher.internal.Processor
import com.tsarev.fiotcher.internal.flow.ChainingListener
import com.tsarev.fiotcher.internal.flow.WayStation
import java.io.File
import java.util.concurrent.CompletionStage
import kotlin.reflect.KClass

class DefaultFileProcessorManager(
    override val processor: Processor<File>
) : FileProcessorManager {

    override fun startTrackingFile(path: File, key: String, recursively: Boolean): CompletionStage<out Stoppable> {
        val fileSystemTracker = FileSystemTracker(recursive = recursively)
        return processor.startTracker(path, fileSystemTracker, key)
    }

    override fun stopTracking(resource: File, key: String, force: Boolean): CompletionStage<*> {
        return processor.stopTracker(resource, key, force)
    }

    override fun listenForInitial(key: String): ProcessorManager.ListenerBuilder<InitialEventsBunch<File>> {
        return ListenerBuilderInitial(key.typedKey())
    }

    override fun stopListeningInitial(key: String, force: Boolean) =
        processor.deRegisterListener(key.typedKey<InitialEventsBunch<File>>(), force)

    override fun <EventT : ProcessorManager.EventMarker> listenForKey(
        key: String,
        type: KClass<EventT>
    ): ProcessorManager.ListenerBuilder<EventT> {
        return ListenerBuilderInitial(key.typedKey(type))
    }

    override fun <EventT : ProcessorManager.EventMarker> stopListening(
        key: String,
        type: KClass<EventT>,
        force: Boolean
    ) = processor.deRegisterListener(key.typedKey(type), force)

    abstract inner class DefaultListenerBuilderBase<InitialT : Any, EventT : Any, PreviousT : Any>(
        private val key: KClassTypedKey<InitialT>
    ) : ProcessorManager.ListenerBuilder<EventT> {

        override fun <NextT : Any> chain(
            handleErrors: ((Throwable) -> Throwable?)?,
            async: Boolean,
            transformer: (EventT) -> NextT?
        ) = ListenerBuilderIntermediate<InitialT, NextT, EventT>(
            key, this
        ) {
            if (async) {
                it.asyncChainFrom(transformer = transformer, handleErrors = handleErrors)
            } else {
                it.syncChainFrom(transformer = transformer, handleErrors = handleErrors)
            }
        }

        override fun <NextT : Any> split(
            handleErrors: ((Throwable) -> Throwable?)?,
            async: Boolean,
            transformer: (EventT) -> Collection<NextT?>?
        ) = ListenerBuilderIntermediate<InitialT, NextT, EventT>(
            key, this
        ) {
            if (async) {
                it.asyncSplitFrom(transformer = transformer, handleErrors = handleErrors)
            } else {
                it.syncSplitFrom(transformer = transformer, handleErrors = handleErrors)
            }
        }

        override fun <NextT : Any> delegate(
            handleErrors: ((Throwable) -> Throwable?)?,
            async: Boolean,
            transformer: (EventT, (NextT) -> Unit) -> Unit
        ) = ListenerBuilderIntermediate<InitialT, NextT, EventT>(
            key, this
        ) {
            if (async) {
                it.asyncDelegateFrom(transformer = transformer, handleErrors = handleErrors)
            } else {
                it.syncDelegateFrom(transformer = transformer, handleErrors = handleErrors)
            }
        }

        override fun startListening(
            async: Boolean,
            handleErrors: ((Throwable) -> Throwable?)?,
            listener: (EventT) -> Unit
        ): Stoppable {
            val lastListener = processor.createCommonListener(
                listener = listener,
                handleErrors = handleErrors
            )
            val initialListener = chain(lastListener)
            return processor.registerListener(initialListener, key)
        }

        override fun doAggregate(
            handleErrors: ((Throwable) -> Throwable?)?,
            key: KClassTypedKey<EventT>
        ) {
            val lastListener = processor.doAggregate(
                key = key,
                handleErrors = handleErrors
            )
            val initialListener = chain(lastListener)
            processor.registerListener(initialListener, this.key)
        }

        abstract fun chain(current: ChainingListener<EventT>): ChainingListener<InitialT>

    }

    inner class ListenerBuilderInitial<InitialT : Any>(
        key: KClassTypedKey<InitialT>
    ) : DefaultListenerBuilderBase<InitialT, InitialT, Unit>(key) {
        override fun chain(current: ChainingListener<InitialT>) = current
    }

    inner class ListenerBuilderIntermediate<InitialT : Any, EventT : Any, PreviousT : Any>(
        key: KClassTypedKey<InitialT>,
        private val prev: DefaultListenerBuilderBase<InitialT, PreviousT, *>,
        private val chainer: (WayStation.(ChainingListener<EventT>) -> ChainingListener<PreviousT>),
    ) : DefaultListenerBuilderBase<InitialT, EventT, PreviousT>(key) {
        override fun chain(current: ChainingListener<EventT>): ChainingListener<InitialT> {
            val previous = processor.chainer(current)
            return prev.chain(previous)
        }
    }

}