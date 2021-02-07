# fiotcher
A small resource scanning and handling processor library

# TL;DR
Basic usage can be found now at simpeTester.kt file.

# Design notes
Base design idea it to use publisher/subscriber pattern as much as posiible, since it adds various advantages for this kind of library, like:
1. Asynchronous control of data flow.
2. More precise control of data flow - we can "shutdown" data processing chain in the middle, for example.
3. Aggregation posibilities - we can group same type events to share processing resources, or to guarantee batch processing.
4. Isolation - we can isolate processing handlers (internal and user ones) by putting them in different threads, or more - in different queues.
5. More precise resource control - by parametrizing used executors and queue sizes.
6. Easy to use from user side - since we can hide subscribe/publish logic behind simple "chain like" handlers.

Base API is divided into `Processor` and `FileProcessorManager`.
First is low level chaining interface that exposes publish/subscribe possibilities to the user.
Second is facade, that provides common tasks that are needed with siplier API.

They can be divided into more interfaces in the future, but without significant 
responsibilities change (for example, FileProcessorManager can become just ProcessorManager).

# Future work notes
Right now, there is no way to customize default implementations creation parameters, but it will change shortly,
so most likely some new API like `ProcessorBuilder` or `ProcessorManagerBuilder` will arise.
