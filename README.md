[![Build Status](https://travis-ci.com/darkMechanicum/fiotcher.svg?branch=master)](https://travis-ci.com/darkMechanicum/fiotcher)


# Fiotcher
A small resource scanning and handling processor library.

# Examples
Usage examples can be found at:
1. Basic usage - `/test/java/com/tsarev/fiotcher/api/BasicUsage.kt`
2. Advanced usage - `/test/java/com/tsarev/fiotcher/api/BasicUsage.kt`
3. Error handling usage - `/test/java/com/tsarev/fiotcher/api/BasicUsage.kt`
4. Lifecycle control usage - `/test/java/com/tsarev/fiotcher/api/BasicUsage.kt`

Also, there is self parse test results example in module `example` 
(`/src/example/kotlin/com/tsarev/fiotcher/liveJunitReportsParse.kt`).

# About

Fiothcer is a tracking/processing library build upon a subscriber publisher pattern.

The main idea is to simplify implementing various processors and trackers and glue the into single chain.

It allows to:
1. Implement various trackers, just extending base `Tracker` class.
2. Register various listeners, using `ProcessorManager` API, or lower level `Processor` API.
3. Start and stop listeners and trackers, following `Stoppable` interface methods.

Core tracker implementations are:
1. `FileSystemTracker` - tracks file system changes within specified directory.

Listeners are allowed to do asynchronous processing, see `ProcessorManager#ListenerBuilder` interface for details.

Core listeners extensions are located at `extensions.kt`.
They include:
1. `handleSax` - parsing xml files with default java API SAX parser, each file asynchronously.
2. `handleDom` - parsing xml files with default java API DOM parser, each file asynchronously.
2. `handleLines` - parse text files line by line, asynchronously.
2. `handleFiles` - handle raw files, asynchronously.

# Current limitations
There are some limitations at the moment, that can be eliminated within current design:
1. There can't be more than one listener bind to the key at the moment.
   It can be fixed with more complicated records logic in `DefaultListenerPool`.
1. Graceful stop can lost some events, when using custom aggregators via `ProcessorManager#ListenerBuilder#doAggregate` method,
   because aggregators and listeners are stopped simultaneously.
   Aggregators and listener chain dependency management can fix it.
2. There can be infinite event loop, when using custom aggregators via `ProcessorManager#ListenerBuilder#doAggregate` method.
   Aggregators and listener chain dependency management can fix it.
3. Current `FileSystemTracker` implementation is based upon Java API `WatchService`, so if nested directories
   are created fast, so file can be lost. This can be fixed with fallback method, scanning with 
   plain BFS search at second thread and registering new directories in the watcher service.
4. If an error occurs during listener processing, and it is not handled via error handler, so
   it will be thrown and listener chain will stop. There is no way to detect this at the moment.
   It can be fixed with changing `Stoppable` interface and adding more states rather than just `stopped`.