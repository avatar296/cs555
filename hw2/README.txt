CS555 - Distributed Systems - Homework 2
Christopher Cowart
Fall 2025

FILES MANIFEST:
==============
build.gradle                        - Gradle build configuration
settings.gradle                     - Gradle project settings
README.txt                         - This file

Source Code Organization:
------------------------
src/main/java/csx55/threads/
    Registry.java                  - Registry server (entry point)
    ComputeNode.java              - Compute node (entry point)

src/main/java/csx55/threads/core/
    ThreadPool.java               - Fixed-size thread pool implementation
    Worker.java                   - Worker threads for task mining
    TaskQueue.java                - Thread-safe task queue
    Task.java                     - SHA-256 proof-of-work task implementation
    Stats.java                    - Statistics tracking for each node
    OverlayState.java             - Overlay successor/predecessor/ring state holder

src/main/java/csx55/threads/balance/
    RoundAggregator.java          - Per-round ring totals and target computation
    LoadBalancer.java             - Push/pull logic and task migration helpers

src/main/java/csx55/threads/registry/
    StatsAggregator.java          - Aggregates and prints final stats table
    RegistryCommands.java         - Console command parsing and dispatch

src/main/java/csx55/threads/util/
    NetworkUtil.java              - Socket helpers for string/tasks messaging
    Protocol.java                 - Protocol constants for message types
    NodeId.java                   - Utility to parse/format <ip>:<port>
    Config.java                   - Typed accessors for system properties
    WaitUtil.java                 - Helpers for drain/quiescence waiting
    Log.java                      - Lightweight logger

