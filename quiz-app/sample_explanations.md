# Sample Explanations for Quiz Questions

This file contains sample explanations you can add to your quiz questions to help with learning.

## Quiz 1 - Threads

### Question 1: "Threads within a process share the same program counter."
**Answer:** False

**Explanation:** Each thread has its own program counter. The program counter keeps track of where each thread is in its execution, allowing multiple threads to execute different parts of code simultaneously. Threads share memory and resources, but maintain separate execution contexts including their own program counters and stack.

### Question 2: "Context-switching between threads is more time-consuming than context-switching between processes."
**Answer:** False

**Explanation:** Context-switching between threads is much faster than between processes. Since threads share the same address space, switching between them doesn't require changing memory mappings or flushing the TLB (Translation Lookaside Buffer). Process context switches are expensive because they require saving/restoring more state and switching memory contexts.

### Question 3: "Regardless of the recursion depth of an executing thread, there can only be 1 frame on that thread's stack."
**Answer:** False

**Explanation:** Each method/function call creates a new stack frame. In recursion, each recursive call adds a new frame to the stack. The number of frames equals the current call depth. For example, if method x() calls y() which calls z(), there are 3 frames on the stack.

### Question 5: "A process with 4 threads has 5 program counters; one for each thread, and one for the process."
**Answer:** False

**Explanation:** There is no separate program counter for the process itself. Each of the 4 threads has its own program counter, so there are exactly 4 program counters total. The process is just a container for resources and threads; it doesn't execute code independently.

## Quiz 2 - Synchronization

### Question 1: "Thread T1 can be active in a1.s1() and Thread T2 can be active in a2.s2() at the same time."
**Answer:** True

**Explanation:** Since a1 and a2 are different object instances, they have separate locks. Synchronized methods lock on the object instance. Therefore, T1 can hold the lock on a1 while T2 holds the lock on a2 simultaneously without conflict.

### Question 3: "Thread T1 can be active in a1.s1() and Thread T2 can be active in a1.s2() at the same time."
**Answer:** False

**Explanation:** Both s1() and s2() are synchronized methods on the same object (a1). When T1 enters s1(), it acquires the lock on a1. T2 cannot enter s2() until T1 releases the lock on a1, because there is only one lock per object and synchronized methods require that lock.

## How to Use These

1. Run the add_explanations.py script:
   ```bash
   python3 add_explanations.py
   ```

2. Select the quiz you want to add explanations to

3. For each question, copy and paste the relevant explanation from this file

4. The explanations will then appear when you get questions wrong during quizzes!
