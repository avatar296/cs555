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

Helper Scripts:
--------------
start_registry.sh                 - Start Registry with port configuration
start_nodes.sh                    - Start multiple ComputeNodes
cleanup.sh                        - Kill all processes and optionally clean logs
test_scenario.sh                  - Automated test scenario runner

BUILDING THE PROJECT:
====================
./gradlew build

RUNNING THE COMPONENTS:
======================
Registry (Terminal 1):
    java -cp build/classes/java/main csx55.threads.Registry <port>
    Example: java -cp build/classes/java/main csx55.threads.Registry 5555

ComputeNode (Terminal 2+):
    java -cp build/classes/java/main csx55.threads.ComputeNode <registry-host> <registry-port>
    Example: java -cp build/classes/java/main csx55.threads.ComputeNode localhost 5555


REGISTRY COMMANDS:
==================
- setup-overlay <thread-pool-size> : Configure ring topology and thread pool size (2-16)
- start <number-of-rounds>        : Begin computation rounds
- quit                            : Graceful shutdown

CONFIGURATION PARAMETERS:
========================
System properties (set with -D flag):

Task Generation:
- cs555.maxTasksPerRound (default: 1000) - Max tasks per node per round
- cs555.difficultyBits (default: 17)     - SHA-256 difficulty (leading zero bits)

Load Balancing:
- cs555.pushThreshold (default: 20)      - Queue size to trigger push
- cs555.pullThreshold (default: 5)       - Queue size to trigger pull request
- cs555.minBatchSize (default: 10)       - Minimum migration batch size
- cs555.balanceCheckInterval (default: 100ms) - Load check frequency

Display:
- cs555.printTasks (default: true)       - Print completed tasks

Example:
    java -Dcs555.pushThreshold=15 -Dcs555.difficultyBits=16 -cp build/classes/java/main \
         csx55.threads.ComputeNode localhost 5555
