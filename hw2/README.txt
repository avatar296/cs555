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
    Registry.java                  - Registry server for node coordination
    ComputeNode.java              - Compute node with thread pool and load balancing
    ThreadPool.java               - Fixed-size thread pool implementation
    Worker.java                   - Worker threads for task mining
    Task.java                     - SHA-256 proof-of-work task implementation
    TaskQueue.java                - Thread-safe task queue
    Stats.java                    - Statistics tracking for each node

Helper Scripts:
--------------
start_registry.sh                 - Start Registry with port configuration
start_nodes.sh                    - Start multiple ComputeNodes
cleanup.sh                        - Kill all processes and optionally clean logs
test_scenario.sh                  - Automated test scenario runner

BUILDING THE PROJECT:
====================
./gradlew build

or for clean build:
./gradlew clean build

RUNNING THE COMPONENTS:
======================
Registry (Terminal 1):
    java -cp build/classes/java/main csx55.threads.Registry <port>
    Example: java -cp build/classes/java/main csx55.threads.Registry 5555

ComputeNode (Terminal 2+):
    java -cp build/classes/java/main csx55.threads.ComputeNode <registry-host> <registry-port>
    Example: java -cp build/classes/java/main csx55.threads.ComputeNode localhost 5555

Using Helper Scripts:
    ./start_registry.sh 5555      # Start Registry
    ./start_nodes.sh 3            # Start 3 ComputeNodes
    ./cleanup.sh                  # Stop all processes

CREATING SUBMISSION TAR:
=======================
./gradlew createTar

This will create Christopher_Cowart_HW2.tar in build/distributions/

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

KEY FEATURES:
=============
Thread Pool:
- Fixed-size thread pool (2-16 threads per node)
- Thread pool size validation and dynamic reconfiguration
- Graceful shutdown with task completion

Load Balancing:
- Bidirectional load balancing (push AND pull mechanisms)
- Push: Overloaded nodes send tasks to successor
- Pull: Idle nodes request tasks from predecessor
- Continuous background load monitoring
- Migration marking prevents task ping-ponging

Task Processing:
- SHA-256 proof-of-work mining
- Configurable difficulty via leading zero bits
- Tasks track origin node for debugging

Statistics:
- Per-node tracking: generated, pulled, pushed, completed
- Percentage of total work completed
- Single consolidated report per round
- Proper reset between rounds

Fault Tolerance:
- Node pruning during overlay setup
- Graceful handling of unreachable nodes
- Tasks returned to queue if migration fails
- Quiescence detection before stats collection

DESIGN DECISIONS:
================
1. Thread Pool Architecture:
   - Used custom ThreadPool instead of ExecutorService for educational purposes
   - Workers continuously poll shared TaskQueue (producer-consumer pattern)
   - Pool size changeable via new overlay setup

2. Load Balancing Strategy:
   - Pair-wise push: Direct successor relief when queue > pushThreshold
   - Pull requests: Ask predecessor when queue < pullThreshold
   - Small batch sizes (min 10) to avoid oscillation
   - Mark migrated tasks to prevent re-migration
   - Continuous monitoring thread for dynamic balancing

3. Ring Topology:
   - Each node knows successor AND predecessor
   - Enables bidirectional load balancing
   - Registry maintains membership and coordinates overlay

4. Task Mining:
   - Deterministic nonce increment for reproducibility
   - Big-endian byte ordering for nonce
   - Origin tracking for debugging task migration

5. Statistics Collection:
   - Atomic counters for thread safety
   - In-flight tracking ensures accurate completion counts
   - Quiescence detection waits for late-arriving migrated tasks
   - Registry aggregates and computes percentages

TESTING PROCEDURE:
==================
1. Start Registry:
   ./start_registry.sh 5555

2. Start ComputeNodes (minimum 3 recommended):
   ./start_nodes.sh 3

3. Setup overlay with thread pool size:
   setup-overlay 4

4. Start computation rounds:
   start 10

5. Observe load balancing in action:
   - Check for non-zero pushed/pulled values
   - Verify total completed equals total generated
   - Confirm percentages sum to ~100%

6. Test pool size changes:
   setup-overlay 8
   start 5

7. Test invalid pool sizes:
   setup-overlay 1   (should be rejected)
   setup-overlay 17  (should be rejected)

8. Cleanup:
   ./cleanup.sh --logs

Automated Testing:
   ./test_scenario.sh 3 4 10  (3 nodes, pool size 4, 10 rounds)

NOTES:
======
- Java version: 11 or higher
- Gradle version: 8.x
- All communication uses Java Object Serialization over TCP
- No external libraries required
- Tests disabled per assignment requirements
- Code formatted with Google Java Format via Spotless
- Helper scripts require bash shell
- Logs written to compute_node_*.log and registry.log

KNOWN ISSUES:
============
- Ring doesn't self-heal if node fails after overlay setup
- Stats may be delayed if nodes have very different completion times
- High difficulty settings (>20 bits) may cause long delays

PERFORMANCE NOTES:
=================
- Default difficulty (17 bits) provides good balance
- Larger thread pools don't always mean better performance
- Load balancing overhead can impact small workloads
- Network latency affects migration efficiency