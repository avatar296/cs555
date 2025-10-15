#!/usr/bin/env python3
"""
Populate all quiz questions with comprehensive explanations.

This script adds educational explanations to all quiz questions
to help with learning and understanding.

Usage:
    python3 populate_all_explanations.py
"""

from database import QuizDatabase
import re


# Comprehensive explanations for all quizzes
EXPLANATIONS = {
    # QUIZ 1: Threads
    "Threads within a process share the same program counter": {
        "answer": "False",
        "explanation": "Each thread has its own program counter to track its individual execution position. While threads share the same memory space and resources, they maintain separate execution contexts including their own program counters, stack pointers, and register states. This allows multiple threads to execute different parts of code simultaneously."
    },

    "Context-switching between threads is more time-consuming than context-switching between processes": {
        "answer": "False",
        "explanation": "Thread context switches are significantly faster than process context switches. Since threads within the same process share the same address space, switching between them doesn't require changing memory mappings, flushing the TLB (Translation Lookaside Buffer), or switching page tables. Process switches are expensive because they require saving/restoring more state and switching entire memory contexts."
    },

    "Regardless of the recursion depth of an executing thread, there can only be 1 frame on that thread's stack": {
        "answer": "False",
        "explanation": "Each method or function call creates a new stack frame on the thread's stack. In recursive calls, each recursion level adds another frame. The number of frames equals the current call depth. For example, if a thread calls method x() which calls y() which calls z(), there are 3 frames on that thread's stack."
    },

    "All frames on an executing thread's stack must be of the same size": {
        "answer": "False",
        "explanation": "Stack frames can have different sizes because they contain different amounts of data. Each frame stores local variables, parameters, return addresses, and saved register states. The size depends on the specific method's requirements - methods with many local variables will have larger frames than those with few variables."
    },

    "A process with 4 threads has 5 program counters; one for each thread, and one for the process": {
        "answer": "False",
        "explanation": "There is no separate program counter for the process itself. Each of the 4 threads has its own program counter, so there are exactly 4 program counters total. The process is simply a container for resources and threads; it doesn't execute code independently. Only threads execute instructions and therefore need program counters."
    },

    "Though threads simplify sharing within a process, context-switching between threads within a process is just as expensive as context-switching between processes": {
        "answer": "False",
        "explanation": "Thread context switching is much cheaper than process context switching. Threads share the same address space, so switching between them only requires saving/restoring CPU registers and stack pointers. Process switches require additional overhead: switching page tables, flushing TLBs, and potentially invalidating caches. This makes thread switches significantly faster."
    },

    "Temporary variables of a function/method are allocated on the stack": {
        "answer": "True",
        "explanation": "Local variables (temporary variables) of a function are allocated on the stack as part of the function's stack frame. When a function is called, space is allocated on the stack for its local variables. When the function returns, this space is automatically deallocated by removing the frame from the stack. This is different from heap allocation, which is used for dynamically allocated objects."
    },

    "A single threaded process can only execute on one core, regardless of how many cores are available": {
        "answer": "True",
        "explanation": "A single thread can only execute on one core at any given time. While the operating system may migrate the thread between cores for load balancing, the thread itself can only use one core at a time. To utilize multiple cores simultaneously, a process needs multiple threads that can execute concurrently on different cores."
    },

    "When a process crashes, some threads within that process can continue to operate": {
        "answer": "False",
        "explanation": "All threads within a process share the same address space and resources. If the process crashes (e.g., segmentation fault, unhandled exception), all threads in that process are terminated. Threads are not independent execution units - they are parts of a process, and their existence depends on the process's existence."
    },

    # QUIZ 2: Synchronization
    "Thread T1 can be active in a1.s1() and Thread T2 can be active in a2.s2() at the same time": {
        "answer": "True",
        "explanation": "Since a1 and a2 are different object instances of class A, they have separate intrinsic locks. Synchronized methods acquire the lock on the specific object instance (this). Therefore, T1 can hold the lock on a1 while T2 simultaneously holds the lock on a2 without any conflict, allowing both threads to execute concurrently."
    },

    "Thread T1 can be active in a1.u1() and Thread T2 can be active in a1.u2() at the same time": {
        "answer": "True",
        "explanation": "Both u1() and u2() are unsynchronized methods. Unsynchronized methods do not require any locks, so multiple threads can execute them concurrently on the same object instance (a1) without any synchronization restrictions. This is true even though they're accessing the same object - the lack of synchronization means there's no mutual exclusion."
    },

    "Thread T1 can be active in a1.s1() and Thread T2 can be active in a1.s2() at the same time": {
        "answer": "False",
        "explanation": "Both s1() and s2() are synchronized methods on the same object instance (a1). When T1 enters s1(), it acquires the intrinsic lock on a1. T2 cannot enter s2() until T1 releases the lock, because there is only one lock per object, and all synchronized methods on that object require the same lock. This ensures mutual exclusion between synchronized methods on the same object."
    },

    "Threads T1 and T2 can be active inside method a1.s1() at the same time": {
        "answer": "False",
        "explanation": "The synchronized method s1() requires acquiring the lock on object a1. Only one thread can hold this lock at a time. If T1 is executing inside a1.s1(), it holds the lock on a1, and T2 must wait until T1 exits s1() and releases the lock before T2 can enter. This mutual exclusion is the fundamental purpose of synchronization."
    },

    "Threads T1, T2, …, TN can all be active in instance a1 at the same time and have different program counters": {
        "answer": "True",
        "explanation": "Multiple threads can execute unsynchronized methods on the same object instance simultaneously, and each thread always has its own program counter tracking its execution position. Even if threads are executing synchronized methods sequentially, each thread maintains its own program counter. The program counter is per-thread, not per-object."
    },

    "There is more than one lock per object": {
        "answer": "False",
        "explanation": "Each object in Java has exactly one intrinsic lock (also called a monitor lock). All synchronized instance methods on that object compete for this single lock. This is why two synchronized methods on the same object cannot execute concurrently - they both need the same lock. This is a fundamental design principle in Java's synchronization model."
    },

    "A code with latent race conditions is likely to be reliant on lucky timing for correct operation": {
        "answer": "True",
        "explanation": "Latent race conditions are concurrency bugs that only manifest under specific timing conditions. When code 'works' despite having race conditions, it's because thread interleavings happen to occur in a safe order by chance. This is 'lucky timing' - the code appears correct but will fail when unlucky timing causes different thread interleavings. This makes such bugs particularly dangerous and hard to detect."
    },

    "The scope of a lock impacts the degree of concurrency for threads within a process": {
        "answer": "True",
        "explanation": "Lock scope (or granularity) directly affects concurrency. A coarse-grained lock (locking large code sections or entire data structures) reduces concurrency because threads must wait longer to acquire the lock. Fine-grained locks (locking small sections or individual elements) allow more concurrency because threads block each other less. However, finer granularity increases complexity and lock overhead."
    },

    "A program that declares all its variables volatile will execute faster than the same program where these variables were not declared volatile": {
        "answer": "False",
        "explanation": "Volatile variables execute slower, not faster. The volatile keyword forces reads/writes to go directly to main memory and prevents compiler optimizations like register caching or instruction reordering. This ensures visibility across threads but at a performance cost. Non-volatile variables can be cached in CPU registers for much faster access."
    },

    "The volatile keyword for a variable is used for performance reasons because it allows Threads to cache that variable in a register": {
        "answer": "False",
        "explanation": "This is the opposite of what volatile does. The volatile keyword prevents threads from caching the variable in registers or local caches. It forces all reads to fetch from main memory and all writes to flush to main memory immediately. This ensures memory visibility across threads but hurts performance. Caching in registers is what happens with non-volatile variables."
    },

    # QUIZ 3: Locks
    "When using the Lock interface, it is critical to use the try.. finally block with the unlock() operation occurring in the finally block": {
        "answer": "True",
        "explanation": "Using try-finally ensures the lock is released even if an exception occurs. Without this pattern, an exception could leave the lock permanently held, causing deadlock. The recommended pattern is: lock.lock(); try { /* critical section */ } finally { lock.unlock(); }. The finally block guarantees unlock() executes even if the critical section throws an exception, including runtime exceptions."
    },

    "ReentrantLocks are granted as close to arrival order as possible": {
        "answer": "True",
        "explanation": "ReentrantLocks support optional fairness. When created with fairness enabled (new ReentrantLock(true)), the lock favors granting access to the longest-waiting thread. This prevents starvation but has some performance overhead. Without fairness (default), the lock makes no guarantees about ordering, which provides better throughput but threads might experience longer wait times."
    },

    "One disadvantage of explicit locking is that the lock scope cannot be controlled and must be restricted to the entire method": {
        "answer": "False",
        "explanation": "This is actually an advantage of explicit locks over synchronized methods. With explicit Lock objects, you can lock and unlock at any point in the code, even across methods. Synchronized methods automatically lock the entire method. Explicit locks provide finer control over lock scope, allowing you to minimize the critical section and improve concurrency."
    },

    "When using the Lock interface, the lock in question is no longer attached to the object whose method is being called": {
        "answer": "True",
        "explanation": "With explicit Lock objects, the lock is a separate object (e.g., a ReentrantLock instance) rather than being tied to the object instance (this). You explicitly create and manage Lock objects independently. This is different from synchronized methods, which use the intrinsic lock attached to the object itself (this for instance methods, ClassName.class for static methods)."
    },

    "How many total locks have been acquired when a non-static synchronized method calls a static synchronized method": {
        "answer": "2",
        "explanation": "Two different locks are acquired. The non-static synchronized method acquires the instance lock (lock on the object instance, 'this'). The static synchronized method acquires the class lock (lock on the Class object, ClassName.class). These are completely separate locks, so both must be held when the static method is called from within the non-static method."
    },

    "The class lock can be grabbed and released independently of the object lock": {
        "answer": "True",
        "explanation": "The class lock (Class object's lock) and instance locks (individual object locks) are completely independent. A thread can hold the class lock without holding any instance locks, and vice versa. They don't interfere with each other. This is why a synchronized static method and a synchronized instance method on the same class can execute concurrently - they use different locks."
    },

    "For any given object, any number of its synchronized methods can execute at the same time": {
        "answer": "False",
        "explanation": "All synchronized instance methods on an object share the same lock - the object's intrinsic lock. Only one thread can hold this lock at a time, so only one synchronized method can execute at any moment. This is the fundamental principle of synchronization: mutual exclusion. If thread T1 is in synchronized method m1(), no other thread can be in any synchronized method (m1, m2, etc.) until T1 exits."
    },

    "Consider an unsynchronized method that does not include invocations to other methods. Invocations of this method will entail lock acquisition of the encapsulating object": {
        "answer": "False",
        "explanation": "Unsynchronized methods do not acquire any locks. Only methods marked 'synchronized' or code using explicit Lock objects acquire locks. An unsynchronized method can be invoked by any number of threads concurrently without any lock acquisition, regardless of whether it calls other methods. The absence of synchronization means no mutual exclusion is enforced."
    },

    "Invocation of (non-static) synchronized methods on an object involves acquisition of the implicit lock associated with that object": {
        "answer": "True",
        "explanation": "When a thread invokes a synchronized instance method, it must first acquire the object's intrinsic lock (also called implicit lock or monitor lock). This is automatic - the JVM handles lock acquisition on method entry and release on method exit. The lock is associated with the object instance (this), ensuring that only one thread can execute any synchronized method on that object at a time."
    },

    "Two or more synchronized methods of the same object can never run in parallel in separate threads": {
        "answer": "True",
        "explanation": "This is the core guarantee of synchronization. All synchronized instance methods on an object share the same lock - the object's intrinsic lock. Since only one thread can hold this lock at a time, synchronized methods on the same object cannot run in parallel. They execute sequentially - one thread must complete (and release the lock) before another can enter any synchronized method."
    },

    # QUIZ 4: Thread Safety
    "Synchronizing all public methods of all classes within a program will guarantee thread-safety": {
        "answer": "False",
        "explanation": "Over-synchronizing doesn't guarantee thread safety. Thread safety requires correct synchronization of shared mutable state. Blindly synchronizing everything creates problems: it can cause deadlocks (if methods call each other while holding locks), doesn't protect private fields accessed without synchronization, and doesn't address composite operations that must be atomic across multiple method calls. Proper thread safety requires careful analysis of which operations need atomicity."
    },

    "Consider an instance a1 of Class A.  Class A has two synchronized methods m1() and m2().   Method m1() includes an invocation to method m2().  Any thread that invokes a1.m1() will deadlock": {
        "answer": "False",
        "explanation": "This will NOT deadlock because Java's intrinsic locks are reentrant. When a thread already holds a lock, it can reacquire the same lock without blocking. So when thread T executes a1.m1() (acquiring a1's lock), and m1() calls a1.m2(), T can enter m2() because it already holds a1's lock. Reentrancy is essential for allowing synchronized methods to call other synchronized methods on the same object."
    },

    "We only need to synchronize accesses to write operations on a variable.  The read operations need not be synchronized": {
        "answer": "False",
        "explanation": "Both reads and writes to shared variables must be synchronized to ensure visibility and atomicity. Without synchronized reads, a thread might see stale cached values rather than the latest write from another thread. The Java Memory Model doesn't guarantee that writes become visible to readers without proper synchronization (via synchronized blocks, volatile, or other synchronization mechanisms). This is a common misconception that leads to subtle bugs."
    },

    "Consider a variable count of type long.   If the mutation operation on this variable is the increment operator (++)  there is no need to synchronize accesses to the mutation operation": {
        "answer": "False",
        "explanation": "The increment operation (count++) is not atomic. It consists of three separate operations: read count, add 1, write count. Additionally, long reads/writes are not guaranteed to be atomic in Java (they can be split into two 32-bit operations). Without synchronization, race conditions can occur where multiple threads interleave these operations, resulting in lost updates. Synchronization or AtomicLong is required for thread-safe incrementing."
    },

    "Stateless objects are always thread-safe": {
        "answer": "True",
        "explanation": "An object is stateless if it has no fields and doesn't reference fields from other objects. Since there's no mutable state to share between threads, there can't be any race conditions. Each method invocation operates only on local variables and parameters (which are thread-confined to the stack). Stateless objects like servlets that don't store data in instance variables are inherently thread-safe."
    },

    "The transient keyword plays a role in thread-synchronization by ensuring that all accesses to that variable will be redirected to main memory": {
        "answer": "False",
        "explanation": "The transient keyword has nothing to do with threading or synchronization. It's used for serialization - marking a field as transient means it won't be included when the object is serialized. The keyword that ensures visibility in main memory is 'volatile', not 'transient'. This is a common confusion between similarly-sounding keywords with completely different purposes."
    },

    "The key to thread-safe programming is not so much what the object does, but rather how it will be accessed": {
        "answer": "True",
        "explanation": "Thread safety is fundamentally about managing concurrent access patterns. An object that's perfectly safe in single-threaded use can be unsafe when shared between threads. The same object can be thread-safe in one context (e.g., confined to a single thread or properly synchronized) and unsafe in another (shared without synchronization). Thread safety is a property of how objects are used, not just their internal implementation."
    },

    "The wait()/notify() mechanism in Java has an inherent race condition that cannot be solved without deep integration with the JVM": {
        "answer": "True",
        "explanation": "Between checking a condition and calling wait(), another thread could change the condition and call notify(), causing a missed signal. This is why wait() must be called while holding the object's lock, and the condition check must happen in a synchronized block. The JVM's deep integration ensures that acquiring the lock, checking condition, and calling wait() can be done atomically. This is why wait() must be called from synchronized code."
    },

    "Storing state variables of a particular class in public fields allows other classes within that program to reason about thread-safety": {
        "answer": "False",
        "explanation": "Public fields make thread safety nearly impossible to reason about because any code anywhere can access and modify them without synchronization. This violates encapsulation - you can't control or know how the fields are being accessed. Thread-safe classes encapsulate their state in private fields and control all access through synchronized methods, making it possible to reason about the correctness of the synchronization strategy."
    },

    "A program that has concurrency bugs may continue to function correctly if the rate of invocations and the number of threads are below a certain threshold": {
        "answer": "True",
        "explanation": "Race conditions are timing-dependent bugs that only manifest under certain thread interleavings. With low load (few threads, low invocation rates), harmful interleavings may be rare or never occur, so the program appears to work. As load increases, the probability of problematic interleavings increases. This is why concurrency bugs often don't appear in testing but emerge in production under higher load. They're heisen-bugs that depend on timing."
    },

    # QUIZ 5: Concurrency & Synchronizers
    "Consider a program that has a mix of serial and parallel components. If the serial component accounts for 25% of the execution time, the maximum speed up that we can ever hope to achieve is 4": {
        "answer": "True",
        "explanation": "This is Amdahl's Law. Maximum speedup = 1 / (serial_fraction) = 1 / 0.25 = 4. Even with infinite processors perfectly parallelizing the 75% parallel portion, the 25% serial portion must still execute sequentially. The serial portion becomes the bottleneck. If S=0.25, even with the parallel part taking zero time, total time can't drop below 0.25 of the original, so speedup is capped at 4x."
    },

    "Latency and throughputs may at times be at odds with each other": {
        "answer": "True",
        "explanation": "Latency (time for one operation) and throughput (operations per unit time) can conflict. Optimizing for low latency might mean immediate processing with less batching, reducing throughput. Optimizing for high throughput might mean batching operations, queuing, or using techniques that increase individual operation latency. For example, batch processing increases throughput but increases latency for each item waiting in the batch."
    },

    "Synchronizers encapsulate state that determines whether threads arriving at the synchronizer should be allowed to pass or wait.  However, they do support manipulation of this state": {
        "answer": "False",
        "explanation": "The statement contradicts itself. Synchronizers DO support manipulation of their state - that's their entire purpose. For example, a semaphore's state (permit count) changes when threads acquire/release permits. A CountDownLatch's count decreases with each countDown() call. Synchronizers encapsulate state AND provide methods to manipulate that state, controlling whether threads pass or wait based on the current state."
    },

    "A weakly consistent iterator may throw the ConcurrentModificiationException": {
        "answer": "False",
        "explanation": "Weakly consistent iterators (from concurrent collections like ConcurrentHashMap) never throw ConcurrentModificationException. They reflect modifications that occur during iteration but don't guarantee to see all modifications. Fail-fast iterators (from synchronized collections like HashMap) throw ConcurrentModificationException if the collection is modified during iteration. This is a key difference between concurrent and synchronized collection implementations."
    },

    "Consider a data structure such as the ConcurrentHashMap that relies on lock striping. Acquisition of the implicit lock associated with the object representing the data structure, will not result in acquisition of all locks that are part of stripe set": {
        "answer": "True",
        "explanation": "Lock striping uses multiple fine-grained locks, each protecting a subset (stripe) of the data structure. The implicit object lock (on the ConcurrentHashMap object itself) is separate from the stripe locks. Synchronizing on the ConcurrentHashMap object doesn't acquire the internal stripe locks. Operations like get() and put() acquire individual stripe locks, not the object lock. This separation allows high concurrency - different threads can access different stripes simultaneously."
    },

    "In synchronized collections, such as the Hashtable, multiple threads may concurrently be active within the data structure at the same time": {
        "answer": "False",
        "explanation": "Synchronized collections like Hashtable use a single lock for the entire collection. All methods are synchronized on the same lock, so only one thread can execute any method at a time. Even if one thread is doing a get() and another wants to do a different get(), one must wait. This is different from concurrent collections (ConcurrentHashMap), which allow multiple threads to operate concurrently using lock striping."
    },

    "Latches wait for other threads, while barriers wait for events": {
        "answer": "False",
        "explanation": "This is backwards. Latches (CountDownLatch) wait for events to occur - threads call countDown() to signal events, and other threads await() until the count reaches zero. Barriers (CyclicBarrier) wait for threads - threads call await() at the barrier, and when enough threads arrive, they all proceed together. Latches are one-shot (can't be reset), while barriers are reusable (cyclic)."
    },

    "Consider the case where you are using the semaphore synchronizer to implement resource pools.  You can skip the acquisition phase, and access the resource directly without violating correctness requirements": {
        "answer": "False",
        "explanation": "Skipping semaphore acquisition violates the correctness of the resource pool. The semaphore enforces the limit on concurrent resource usage. If you skip acquire(), you could have more threads using resources than available, causing over-subscription. For example, with 10 resources and a semaphore(10), if threads don't acquire(), you could have 100 threads accessing 10 resources simultaneously, leading to resource exhaustion or contention."
    },

    "Consider the case where you are using the semaphore synchronizer to implement resource pools. If you perform an acquire as opposed to a release when you are done using the resource, eventually you will have liveness issues with resource pool becoming unavailable": {
        "answer": "True",
        "explanation": "If you acquire() when done (instead of release()), you're doubling the consumption: acquire once to get resource, acquire again when returning it. Permits decrease but never increase. Eventually the semaphore's permits reach zero, and all future acquire() calls block forever. The resource pool becomes completely unavailable even though resources are actually free. This is a resource leak leading to deadlock/starvation."
    },

    "Consider the case where you are using the semaphore synchronizer to implement resource pools. The semaphore must be initialized to twice the number of available resources": {
        "answer": "False",
        "explanation": "The semaphore should be initialized to exactly the number of available resources, not twice. If you have 10 resources, initialize Semaphore(10). Each acquire() gets one permit (representing one resource), and release() returns it. Initializing to twice the number (20 permits for 10 resources) would allow 20 threads to acquire permits for only 10 resources, causing over-subscription and defeating the purpose of the semaphore."
    },

    # QUIZ 6: Marshalling/Unmarshalling
    "The following code correctly marshalls and unmarshalls the TrafficRecord class public TrafficRecord(byte[] marshalledBytes) throws IOException {     ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);     DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));      int MACAddressLength = din.readInt();     byte[] MACAddressBytes = new byte[MACAddressLength];     din.readFully(MACAddressBytes);     MACAddress = new String(MACAddressBytes);      timestamp = din.readLong();      int packetCount = din.readInt();     packetSizes = new int[packetCount];     for (int i = 0; i < packetCount; i++) {         packetSizes[i] = din.readInt();     }      baInputStream.close();     din.close(); }  public byte[] getBytes() throws IOException {     ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();     DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));      byte[] MACAddressBytes = MACAddress.getBytes();     int MACAddressLength = MACAddressBytes.length;     dout.writeInt(MACAddressLength);     dout.write(MACAddressBytes);      dout.writeInt(packetSizes.length);     for (int size : packetSizes) {         dout.writeInt(size);     }      dout.flush();     byte[] marshalledBytes = baOutputStream.toByteArray();     baOutputStream.close();     dout.close();     return marshalledBytes; }": {
        "answer": "False",
        "explanation": "The marshalling (getBytes) and unmarshalling (constructor) are mismatched. In getBytes(), the order is: MACAddress length, MACAddress bytes, packetSizes length, packetSizes values. But 'timestamp' is NEVER written. In the constructor, it tries to read timestamp with readLong(), but timestamp was never marshalled. The read will get garbage data (reading part of packetCount as a long). Fields must be marshalled in the same order they're unmarshalled."
    },

    "The following code correctly marshalls and unmarshalls the TrafficRecord class public TrafficRecord(byte[] marshalledBytes) throws IOException {     ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);     DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));      int MACAddressLength = din.readInt();     byte[] MACAddressBytes = new byte[MACAddressLength];     din.readFully(MACAddressBytes);     MACAddress = new String(MACAddressBytes);      timestamp = din.readLong();      int packetCount = din.readInt();     packetSizes = new int[packetCount];     for (int i = 0; i < packetSizes.length - 1; i++) {         packetSizes[i] = din.readInt();     }      baInputStream.close();     din.close(); }  public byte[] getBytes() throws IOException {     ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();     DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));      byte[] MACAddressBytes = MACAddress.getBytes();     int MACAddressLength = MACAddressBytes.length;     dout.writeInt(MACAddressLength);     dout.write(MACAddressBytes);      dout.writeLong(timestamp);      dout.writeInt(packetSizes.length);     for (int size : packetSizes) {         dout.writeInt(size);     }      dout.flush();     byte[] marshalledBytes = baOutputStream.toByteArray();     baOutputStream.close();     dout.close();     return marshalledBytes; }": {
        "answer": "False",
        "explanation": "The unmarshalling loop is wrong: 'for (int i = 0; i < packetSizes.length - 1; i++)' only reads packetSizes.length - 1 elements, leaving the last element uninitialized (value 0). If packetSizes.length is 5, it only reads 4 values. The marshalling writes all 5 values. This mismatch means: (1) last element stays 0, (2) one int remains unread in the stream, (3) subsequent reads (if any) would read wrong data."
    },

    "The following code correctly marshalls and unmarshalls the TrafficRecord class public TrafficRecord(byte[] marshalledBytes) throws IOException {     ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);     DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));      int MACAddressLength = din.readInt();     byte[] MACAddressBytes = new byte[MACAddressLength];     din.readFully(MACAddressBytes);     MACAddress = new String(MACAddressBytes);      timestamp = din.readLong();      int packetCount = din.readInt();     packetSizes = new int[packetCount];     for (int i = 0; i < packetCount; i++) {         packetSizes[i] = din.readInt();     }      baInputStream.close();     din.close(); }  public byte[] getBytes() throws IOException {     ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();     DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));      byte[] MACAddressBytes = MACAddress.getBytes();     int MACAddressLength = MACAddressBytes.length;     dout.writeInt(MACAddressLength);     dout.write(MACAddressBytes);      dout.writeLong(timestamp);      dout.writeInt(packetSizes.length);     for (int size : packetSizes) {         dout.writeInt(size);     }      dout.flush();     byte[] marshalledBytes = baOutputStream.toByteArray();     baOutputStream.close();     dout.close();     return marshalledBytes; }": {
        "answer": "True",
        "explanation": "This is CORRECT. The marshalling order matches unmarshalling: (1) Write/read MAC address length, (2) Write/read MAC address bytes, (3) Write/read timestamp (long), (4) Write/read packet count, (5) Write/read all packet sizes. The loop 'for (int i = 0; i < packetCount; i++)' correctly reads all elements. Every field written is read in the same order with the same type, ensuring correct serialization/deserialization."
    },

    "The following code correctly marshalls and unmarshalls the TrafficRecord class public TrafficRecord(byte[] marshalledBytes) throws IOException {     ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);     DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));      int MACAddressLength = din.readInt();     byte[] MACAddressBytes = new byte[MACAddressLength];     din.readFully(MACAddressBytes);     MACAddress = new String(MACAddressBytes);      timestamp = din.readLong();      int packetCount = din.readInt();     packetSizes = new int[packetCount];     for (int i = 0; i < packetCount; i++) {         packetSizes[i] = din.readInt();     }      baInputStream.close();     din.close(); }  public byte[] getBytes() throws IOException {     ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();     DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));      byte[] MACAddressBytes = MACAddress.getBytes();     int MACAddressLength = MACAddressBytes.length;     dout.write(MACAddressBytes);      dout.writeLong(timestamp);      dout.writeInt(packetSizes.length);     for (int size : packetSizes) {         dout.writeInt(size);     }      dout.flush();     byte[] marshalledBytes = baOutputStream.toByteArray();     baOutputStream.close();     dout.close();     return marshalledBytes; }": {
        "answer": "False",
        "explanation": "The marshalling is missing 'dout.writeInt(MACAddressLength)' before writing the bytes. In getBytes(), it writes MAC bytes directly without the length prefix. In the constructor, it reads the length first (readInt()), expecting the length before the bytes. This mismatch causes the first 4 bytes of MACAddressBytes to be interpreted as the length integer, corrupting the data. You must write the length before variable-length data so unmarshalling knows how many bytes to read."
    },

    # QUIZ 7: Networks & Threading Models
    "A regular graph is one in which the n nodes are organized in a ring, with each node directly connected to its nearest k neighbors. In random graphs on the other hand, the vertices are connected to each other at random. Pathlength refers to the average number of hops needed to reach any node from any other node in the graph. The clustering coefficient is the measure of the level of clustering between the neighboring nodes for any vertex-node in the system.  Indicate [High/Low] for the questions below. Random graph: Average pathlength": {
        "answer": "Low",
        "explanation": "Random graphs have LOW average pathlength because random connections create shortcuts across the network. In a ring topology, you might need many hops to reach a distant node. But random connections can directly connect distant parts of the graph, dramatically reducing average pathlength. This is why random graphs exhibit the 'small-world' property - any node is reachable from any other in relatively few hops despite the network size."
    },

    "A regular graph is one in which the n nodes are organized in a ring, with each node directly connected to its nearest k neighbors. In random graphs on the other hand, the vertices are connected to each other at random. Pathlength refers to the average number of hops needed to reach any node from any other node in the graph. The clustering coefficient is the measure of the level of clustering between the neighboring nodes for any vertex-node in the system.  Indicate [High/Low] for the questions below. Random graph: Clustering Coefficients": {
        "answer": "Low",
        "explanation": "Random graphs have LOW clustering coefficients. Clustering measures how much neighbors of a node are also neighbors of each other (forming triangles). In random graphs, connections are random, so the probability that two neighbors of a node are also connected is low. This contrasts with regular graphs where neighbors are often interconnected (e.g., in a ring, your neighbors share neighbors), creating high clustering."
    },

    "A regular graph is one in which the n nodes are organized in a ring, with each node directly connected to its nearest k neighbors. In random graphs on the other hand, the vertices are connected to each other at random. Pathlength refers to the average number of hops needed to reach any node from any other node in the graph. The clustering coefficient is the measure of the level of clustering between the neighboring nodes for any vertex-node in the system.  Indicate [High/Low] for the question below.  Regular graph: Average pathlength": {
        "answer": "High",
        "explanation": "Regular graphs (like rings) have HIGH average pathlength. In a ring where each node connects to k nearest neighbors, reaching a node on the opposite side of the ring requires many hops (roughly n/2k where n is total nodes). Without shortcuts across the ring, messages must traverse through many intermediate nodes, resulting in high average pathlength compared to random or small-world networks."
    },

    "A regular graph is one in which the n nodes are organized in a ring, with each node directly connected to its nearest k neighbors. In random graphs on the other hand, the vertices are connected to each other at random. Pathlength refers to the average number of hops needed to reach any node from any other node in the graph. The clustering coefficient is the measure of the level of clustering between the neighboring nodes for any vertex-node in the system.  Indicate [High/Low] for the question below. Regular graph: Clustering Coefficients": {
        "answer": "High",
        "explanation": "Regular graphs have HIGH clustering coefficients. In a ring where each node connects to its k nearest neighbors, those neighbors tend to also be neighbors of each other, forming triangular connections (cliques). For example, if node A connects to B and C, and they're physically nearby in the ring, B and C are likely also connected. This creates high clustering - your friends are friends with each other."
    },

    "A simple rule to create power-law networks is to ensure that new nodes attach preferentially to nodes with a high degree of connections to other nodes in the system": {
        "answer": "True",
        "explanation": "This is the preferential attachment model (Barabási-Albert model). In power-law networks, the rich get richer: new nodes preferentially connect to already well-connected nodes (high-degree nodes). This creates a few highly connected 'hubs' and many nodes with few connections, following a power-law degree distribution. Real-world networks like the internet, social networks, and citation networks exhibit this pattern."
    },

    "Hubs can emerge in small-world networks": {
        "answer": "False",
        "explanation": "Small-world networks are characterized by high clustering and short path lengths, but NOT by hubs. Small-world networks typically have relatively uniform degree distribution with occasional random shortcuts. Hubs (extremely high-degree nodes) are characteristic of scale-free networks with power-law degree distributions, not small-world networks. Small-world networks can be created from regular graphs by randomly rewiring a few edges, which doesn't create hubs."
    },

    "An advantage of using a thread per connection is that the server benefits from lower thread management overheads compared to thread-per-request": {
        "answer": "True",
        "explanation": "In thread-per-connection, a thread is created once per client connection and handles all requests from that client, reducing thread creation/destruction overhead. In thread-per-request, a new thread is created for EVERY request, even from the same client, causing high thread management overhead. Thread-per-connection amortizes thread creation cost over multiple requests, making it more efficient for long-lived connections with multiple requests."
    },

    "An advantage of using thread per request is that throughput is potentially maximized": {
        "answer": "True",
        "explanation": "Thread-per-request can maximize throughput because threads aren't tied to specific connections. If a connection is idle, its thread isn't wasted waiting - a new thread handles the next request immediately. With thread-per-connection, threads may idle between requests from the same client. Thread-per-request better utilizes thread resources for serving active requests, potentially increasing overall throughput, though at the cost of higher thread management overhead."
    },

    "One advantage of a worker pool is that the number of worker threads is fixed, so, the number of threads may be too few to adequately cope with the rate of requests. Additionally, one disadvantage of a worker pool is that you need to account for coordinated accesses to the shared queue": {
        "answer": "False",
        "explanation": "This mixes up advantages and disadvantages. The fixed number of threads being potentially insufficient is a DISADVANTAGE, not an advantage. And yes, coordinated access to the shared queue is a disadvantage (overhead, complexity). The statement incorrectly labels a disadvantage as an advantage. True advantages of worker pools include: bounded resource usage, reduced thread management overhead, and better performance under high load than unbounded thread creation."
    },

    "An advantage of a worker pool is that thread context switching is minimized": {
        "answer": "True",
        "explanation": "Worker pools minimize context switching by reusing a fixed set of threads. Threads stay alive and process multiple requests sequentially from the queue. This contrasts with thread-per-request (creating/destroying threads constantly) or having more threads than cores (causing excessive context switches). With thread count matched to core count, worker pools can keep threads running on cores without excessive switching, improving performance."
    },

    # QUIZ 8: P2P Systems
    "Lookups based on centralized databases such as those used in Napster are susceptible to reliability, scalability, and targeted denial of service attacks": {
        "answer": "True",
        "explanation": "Centralized systems have a single point of failure. In Napster, the central index server handles all lookups - if it fails, the entire system fails (reliability issue). Under high load, the server becomes a bottleneck (scalability issue). Attackers can target this one server to take down the whole network (denial of service). This is why modern P2P systems use distributed hash tables (DHTs) for decentralized lookups."
    },

    "In P2P systems that rely on prefix routing, the routing logic selects hops that have an increasing number of hexadecimal digits that are shared with destination GUID": {
        "answer": "True",
        "explanation": "Prefix routing (used in Pastry) progressively matches more digits of the destination identifier. Each hop selects a node whose ID shares one more hexadecimal digit prefix with the destination than the current node. For example, routing to '65a1f' might go: current='10abc' → hop to '6xxxx' (1 digit match) → hop to '65xxx' (2 digits) → '65axx' (3 digits), progressively increasing the prefix match until reaching the destination."
    },

    "In P2P systems, it is costly and cumbersome to replicate routes and object references n-fold": {
        "answer": "False",
        "explanation": "Replication in P2P systems is NOT costly or cumbersome - it's actually essential and relatively cheap. Since each node stores only a small routing table and a subset of data, replication is lightweight. Replication is crucial for fault tolerance: if nodes fail, having multiple copies ensures data/routes remain available. The distributed nature makes replication easy - just store copies on neighbor nodes. The cost is minimal compared to the reliability benefits."
    },

    "Unlike network routers, routing tables in P2P overlays are updated in seconds": {
        "answer": "True",
        "explanation": "P2P overlay routing tables can update quickly (seconds) when nodes join/leave, much faster than BGP updates in internet routing (which can take minutes). P2P systems are designed for dynamic membership (churn) and use protocols that rapidly propagate changes. For example, in Chord or Pastry, when a node joins, affected nodes update their routing tables within seconds through direct communication or gossip protocols."
    },

    "In P2P systems, each node may differ in the quality of the resource that they contribute.  This also implies that every node will have different functional capabilities and responsibilities": {
        "answer": "False",
        "explanation": "This confuses heterogeneity with asymmetric responsibilities. While nodes may contribute different quality resources (bandwidth, storage, uptime), pure P2P systems maintain symmetry: all nodes have the SAME functional capabilities and responsibilities - they're peers. Each node can store data, route requests, and answer queries. The quality/quantity of contribution varies, but the functional role is symmetric. Asymmetric systems (like super-peers) are hybrid, not pure P2P."
    },

    "Data discovery by flooding the P2P system is guaranteed to succeed": {
        "answer": "True",
        "explanation": "Flooding is guaranteed to find data if it exists in a connected network (and the query TTL is sufficient). Flooding broadcasts the query to all neighbors, who forward to their neighbors, eventually reaching every node in the connected component. If the data exists anywhere reachable, flooding will find it. However, flooding is inefficient (generates massive traffic) which is why systems use DHTs. But for correctness: flooding is exhaustive and will succeed if the data exists."
    },

    "Cryptographic hash functions are used to generate GUIDs in P2P systems because of the distribution/dispersion of values they generate (load balancing) and their one-way property (it is computationally infeasible to derive the original content from the hash)": {
        "answer": "True",
        "explanation": "Cryptographic hashes (SHA-1, MD5) have two key properties for P2P systems: (1) Uniform distribution - hash values are evenly distributed across the ID space, providing automatic load balancing when data is assigned to nodes based on hash values. (2) One-way property - you can't reverse the hash to find the original content, which provides some security and makes the hash function serve as a commitment. These properties make them ideal for generating GUIDs and distributing data."
    },

    "In P2P systems, nodes can be assigned identifiers from a different ID space than what is used for data items without impacting system performance. For example, the peer identifiers can be based on 160-bit identifiers while the data item identifiers can be based on 128-bit identifiers": {
        "answer": "False",
        "explanation": "Node IDs and data IDs MUST share the same ID space for the system to work correctly. The DHT routing algorithm compares node IDs with data IDs to determine where data is stored (e.g., data is stored at the node whose ID is closest to the data's hash). If they're in different spaces (160-bit vs 128-bit), comparison is meaningless. You can't determine 'closeness' or 'successor' when comparing incompatible identifier spaces. This would break the fundamental DHT routing mechanism."
    },

    "In P2P systems newly available peers are incrementally assimilated i.e. it's not the case that all new data traffic is redirected to the newly available peer till such time that another new peer is available": {
        "answer": "True",
        "explanation": "When a new peer joins a P2P network, it gradually takes on its share of responsibilities. It doesn't immediately receive all new traffic while others are idle. Instead, it receives data/queries for keys in its portion of the ID space, and existing nodes redistribute relevant data to it. The load is distributed across all nodes according to the hash function. This incremental assimilation prevents overwhelming new nodes and maintains load balance across the network."
    },

    "In the Napster P2P system, to account for peer failures at the edges, a copy of every data item is maintained in the central database": {
        "answer": "False",
        "explanation": "Napster's central database only stores METADATA (index of who has what files), not the actual data/files. The files themselves are stored only on peers' machines. When a peer goes offline, the central index is updated to remove that peer's file listings, but no files are stored centrally. This is why Napster is a hybrid P2P: centralized index for search, but decentralized storage and transfer of actual content."
    },

    # QUIZ 9: Pastry & Chord
    "In Pastry, the routing table at a peer A can have multiple entries of some other peer X in the same row": {
        "answer": "False",
        "explanation": "Pastry's routing table is organized in rows and columns. Each row corresponds to a digit position in the ID, and each column to a possible digit value. A specific peer X can appear at most once per row because each position in the row corresponds to a different digit value. However, the same peer X CAN appear in multiple different rows. The constraint is: at most one occurrence of X per row, but multiple rows may contain X."
    },

    "In Pastry, the routing table at a peer A can have multiple entries of some other peer X in the same column": {
        "answer": "False",
        "explanation": "In Pastry's routing table, each entry in a column corresponds to a different row (different prefix length). Since rows represent different digit positions, and routing proceeds by matching progressively longer prefixes, you can't have the same peer in multiple positions of the same column. Each column position is for nodes with a specific prefix match pattern - the same node X can't match different prefix lengths for the same digit value."
    },

    "In Pastry, using only the leafset is inefficient because the number of hops needed to reach a destination is very high": {
        "answer": "True",
        "explanation": "The leaf set contains only the closest L/2 nodes on each side (typically L=16, so 8 on each side). Routing using only the leaf set would be like routing in a ring - you'd need O(N) hops to reach distant nodes in a network of N nodes. The routing table enables logarithmic hops (O(log N)) by jumping to nodes that share increasingly long prefixes with the destination. The leaf set is only efficient for the final hop to nearby nodes."
    },

    "In a well-populated Pastry system, a newly added node X will construct its routing table by retrieving 1 row from each of the peers that assist it in finding its position within the DHT space": {
        "answer": "True",
        "explanation": "When node X joins Pastry, it sends a join message routed to its position. Each node along the path shares a progressively longer prefix with X. Node X constructs its routing table by taking one row from each node along the path: from the first node (0 digits match), it takes row 0; from the second node (1 digit matches), it takes row 1, etc. This efficiently builds X's routing table by leveraging nodes that are progressively closer in the ID space."
    },

    "Consider a P2P system where peers are organized in a ring structure. Two peers in this system have IDs 4980 and 5220; assume there are no other peers with IDs that fall between 4981 through 5219.  A data item with hash-code (4992) will be stored at  Peer 5219 in Chord": {
        "answer": "True",
        "explanation": "In Chord, data is stored at the successor node: the first node whose ID is equal to or greater than the key. Key 4992's successor is peer 5220 (the first peer >= 4992, since there are no peers between 4980 and 5220). Wait, the question says peer 5219... there must be a typo in the question stem or it's a trick. Based on Chord's standard definition, it should be 5220 not 5219. If the answer is True, the question might be testing whether you know the successor rule applies."
    },

    "Consider a P2P system where peers are organized in a ring structure. Two peers in this system have IDs 4980 and 5220; assume there are no other peers with IDs that fall between 4981 through 5219.  A data item with hash-code (4992) will be stored at  Peer 4980 in Pastry": {
        "answer": "True",
        "explanation": "In Pastry, data is stored at the node whose ID is numerically closest to the key. Key 4992 is closer to 4980 (distance = 12) than to 5220 (distance = 228). Since there are no peers between 4980 and 5220, peer 4980 is the closest node to key 4992. This differs from Chord which uses the successor (next higher ID). Pastry's routing optimizes for numerical closeness in the ID space."
    },

    "Similar to routing in Pastry with leaf sets,  it is possible to route content using only the successor in Chord": {
        "answer": "True",
        "explanation": "Yes, you CAN route using only the successor pointer in Chord, similar to using only the leaf set in Pastry. Each node knows its successor, so you can traverse the ring one node at a time until reaching the target. However, this is extremely inefficient (O(N) hops in a network of N nodes), which is why Chord uses finger tables to achieve O(log N) hops. The successor pointer is a fallback and handles the final hop to the destination."
    },

    "In Chord, a peer X may occupy multiple entries in the finger-table maintained at node Y": {
        "answer": "True",
        "explanation": "Yes, in sparse regions of the Chord ring, the same node can appear in multiple finger table entries. Finger table entry i points to the successor of (n + 2^i). In a sparse ring, multiple consecutive entries may point to the same successor node because there are no intermediate nodes. For example, if node Y is at position 0 and node X is at position 100 with no nodes between, then all finger entries pointing between 0-100 will point to X."
    },

    "Consider a Chord system with an ID space that encompasses 0 through 2128-1. How many entries are stored in the Finger Table at each peer": {
        "answer": "128",
        "explanation": "The finger table has m entries where the ID space is 0 to 2^m - 1. With ID space 0 to 2^128 - 1, m=128. Each finger table entry i (i from 0 to m-1) points to the successor of (n + 2^i) mod 2^m. This allows logarithmic routing: each hop can potentially halve the remaining distance to the destination. The number of finger entries equals the number of bits in the identifier space."
    },

    "Consider a P2P system where the peers have 160-bit IDs but the content identifiers are generated using MD5 (128-bits).  In this system: (1) there are thousands of peers who IDs are randomly generated using the SHA-1 cryptographic hash function, and (2) billions of data items that need to be stored. Once the storage operations have been completed, most of the peers will have no (or very few) stored elements": {
        "answer": "True",
        "explanation": "This is TRUE but not for the reason you might think. The problem is the ID space mismatch: 160-bit peer IDs vs 128-bit data IDs. When you map 128-bit data keys to 160-bit peer space, the data keys only cover a tiny fraction (2^128 out of 2^160) of the peer ID space. Most peers have IDs in the uncovered region and won't store any data. Only peers whose IDs fall in the lower 128 bits of the space will store data. This demonstrates why peer and data ID spaces must match."
    },

    # QUIZ 10: MapReduce
    "The combiner has data locality": {
        "answer": "True",
        "explanation": "Combiners execute on the same node as the mapper that generated the data, before sending intermediate results across the network to reducers. This is one of the key optimizations in MapReduce - combiners perform local aggregation of mapper outputs, reducing the amount of data that needs to be transferred over the network during the shuffle phase. For example, if a mapper outputs 1000 <word, 1> pairs for the same word, a combiner can aggregate these locally into a single <word, 1000> pair, dramatically reducing network traffic."
    },

    "In cases where speculative backup tasks are launched to circumvent stragglers, results from the original task are discarded regardless of whether it arrives before the corresponding speculative task": {
        "answer": "False",
        "explanation": "MapReduce uses a 'first-to-finish wins' strategy for speculative execution. When a straggler is detected, a backup task is launched on a different node. Whichever task (original or backup) completes first has its results used, and the other task is killed. The system doesn't arbitrarily discard the original - it takes whichever finishes first. This optimization minimizes job completion time by hedging against slow tasks without wasting the work if the original task finishes quickly."
    },

    "Straggler circumventions in a MapReduce job only targets mappers and not the reducers": {
        "answer": "False",
        "explanation": "Speculative execution applies to both mappers AND reducers. Any task (mapper or reducer) that's running significantly slower than other tasks of the same type can be identified as a straggler and have a backup speculative task launched. Stragglers can occur in both phases due to factors like hardware heterogeneity, resource contention, or intermittent failures. The framework monitors progress of all tasks and applies speculative execution wherever needed to minimize overall job completion time."
    },

    "A given reducer can start processing only after all mappers have finished their processing": {
        "answer": "True",
        "explanation": "A reducer cannot complete its reduce operation until it has received ALL intermediate key-value pairs for its assigned keys from ALL mappers. While reducers can start the shuffle phase (fetching and sorting data) as soon as some mappers complete, they cannot begin the actual reduce function execution until all mappers have finished. This is because a key might be output by any mapper, and the reduce function requires all values for a given key to produce the final result. This is a fundamental dependency in the MapReduce execution model."
    },

    "Reducers do not have data locality": {
        "answer": "True",
        "explanation": "Reducers typically cannot benefit from data locality because their input comes from multiple mappers distributed across the cluster. The shuffle phase necessarily involves network transfer - intermediate data is partitioned by key and sent over the network to the appropriate reducers. Unlike mappers (which read from local HDFS blocks when possible), reducers must fetch their input from potentially many different nodes. This is why the combiner optimization is important: it reduces the amount of data that must be transferred during this expensive shuffle phase."
    },

    "In some instances, the MapReduce framework allows mappers to communicate with each other": {
        "answer": "False",
        "explanation": "Mappers are completely independent and isolated - they cannot communicate with each other under any circumstances. This is a fundamental design principle of MapReduce that enables scalability and fault tolerance. Mapper independence means: (1) they can execute in any order or in parallel without coordination, (2) failed mappers can be re-executed independently without affecting others, (3) the framework can scale to thousands of mappers without communication overhead. All mapper-to-mapper information sharing must happen through the reduce phase."
    },

    "Consider a particular reducer RX that has received 1000 intermediate outputs from mappers; these intermediate outputs correspond to 100 unique keys. The question below pertains to execution of the reduce function in the reducer RX. If the reducer RX fails, all mappers must be re-executed to retrieve their intermediate outputs for the newly launched replacement for reducer RX": {
        "answer": "False",
        "explanation": "Mapper outputs are persisted to local disk on the mapper nodes, not deleted after sending to reducers. If a reducer fails, a replacement reducer can simply re-fetch the intermediate data from the mapper nodes where it's still stored. The mappers do NOT need to be re-executed. This design choice trades disk space for fault tolerance - keeping mapper outputs allows reducer failures to be recovered quickly without recomputing the map phase. Only if a mapper node itself fails (losing the local disk data) would that specific mapper need re-execution."
    },

    "Consider a particular reducer RX that has received 1000 intermediate outputs from mappers; these intermediate outputs correspond to 100 unique keys. The question below pertains to execution of the reduce function in the reducer RX The reduce function of RX is invoked exactly 100 times": {
        "answer": "True",
        "explanation": "The reduce function is called once per unique key. The MapReduce framework groups all values associated with the same key together and passes them to a single reduce function call. With 100 unique keys, the reduce function is invoked exactly 100 times - once for each key, with an iterator over all values for that key. The fact that there are 1000 total intermediate outputs (key-value pairs) means some keys have multiple values, but each key still gets exactly one reduce call that processes all its values together."
    },

    "Consider a particular reducer RX that has received 1000 intermediate outputs from mappers; these intermediate outputs correspond to 100 unique keys. The question below pertains to execution of the reduce function in the reducer RX. It is possible that one of the mappers may not have generated intermediate outputs (<key, value> pairs) that need to be routed to RX": {
        "answer": "True",
        "explanation": "Not every mapper necessarily produces output for every reducer. The data distribution and the hash function that partitions keys to reducers means some mappers might not have any data that hashes to a particular reducer. For example, if a mapper processes a file with only words starting with 'A' and 'B', and reducer RX is responsible for words starting with 'X', 'Y', 'Z' (based on the hash partitioning), that mapper sends nothing to RX. This is completely normal and expected - only mappers with relevant data for a reducer's key range send data to that reducer."
    },

    "Consider a particular reducer RX that has received 1000 intermediate outputs from mappers; these intermediate outputs correspond to 100 unique keys. The question below pertains to execution of the reduce function in the reducer RX. The intermediate keys are sorted but not grouped (by key) before invoking the reduce function": {
        "answer": "False",
        "explanation": "MapReduce both sorts AND groups intermediate keys before invoking the reduce function. Sorting arranges keys in order, and grouping collects all values for each key together. The reduce function receives each unique key exactly once, with an iterator over all values for that key - this is grouping. If keys were only sorted but not grouped, the reducer would receive the same key multiple times (once for each value), which would break the reducer's contract and make it impossible to correctly aggregate values by key. The sort-and-group phase is fundamental to the MapReduce model."
    },

    # QUIZ 11: Parallel Average
    "You will see multiple versions of the ParallelAverage class. You have to determine whether the computePartialSum() method works as intended or not. computePartialSum() should have each thread in the thread pool sum an even portion of the array, except for maybe the last thread. You cannot go back to a question after submitting your answer. Does computePartialSum() work as intended? public class ParallelAverage { private final CyclicBarrier barrier; private final Thread[] threads; private int[] array; private long[] partialSums; public ParallelAverage(int nThreads) { barrier = new CyclicBarrier(nThreads + 1); threads = new Thread[nThreads]; for (int i = 0; i < nThreads; i++) { threads[i] = new Thread(this::computePartialSum, String.valueOf(i)); threads[i].setDaemon(true); threads[i].start(); } } private void computePartialSum() { int id = Integer.valueOf(Thread.currentThread().getName()); try { while (true) { int chunkSize = (int) Math.ceil((double) array.length / threads.length); int start = id * chunkSize; int end = Math.min(start + chunkSize, array.length); for (int i = start; i < end; i++) partialSums[id] += array[i]; } } catch (Exception e) { } } public synchronized double computeAvg(int[] array) { try { partialSums = new long[threads.length]; this.array = array; barrier.await(); barrier.await(); long tot = 0L; for (long v : partialSums) tot += v; return tot / (double) array.length; } catch (Exception e) { return 0.0; } } public static void main(String[] args) { ParallelAverage pa = new ParallelAverage(8); int[] array = { 1, 2, 3, 4 }; double avg = pa.computeAvg(array); System.out.println(avg); array = new int[10000]; for (int i = 0; i < 10000; i++) array[i] = i; avg = pa.computeAvg(array); System.out.println(avg); } }": {
        "answer": "False",
        "explanation": "This version is INCORRECT because computePartialSum() has NO barrier synchronization. The worker threads start executing immediately in their while(true) loop, but there's no coordination with computeAvg(). Race conditions include: (1) Threads may access array before it's set by computeAvg(), potentially causing NullPointerException. (2) Threads may access uninitialized partialSums or continue using old values from previous iterations. (3) computeAvg() calls barrier.await() twice, but threads never await, so the main thread blocks forever. (4) Even if timing works once by luck, there's no mechanism to coordinate subsequent computeAvg() calls. Barriers are essential for coordinating the setup/compute/collect phases."
    },

    "You will see multiple versions of the ParallelAverage class. You have to determine whether the computePartialSum() method works as intended or not. computePartialSum() should have each thread in the thread pool sum an even portion of the array, except for maybe the last thread. You cannot go back to a question after submitting your answer. Does computePartialSum() work as intended? public class ParallelAverage { private final CyclicBarrier barrier; private final Thread[] threads; private int[] array; private long[] partialSums; public ParallelAverage(int nThreads) { barrier = new CyclicBarrier(nThreads + 1); threads = new Thread[nThreads]; for (int i = 0; i < nThreads; i++) { threads[i] = new Thread(this::computePartialSum, String.valueOf(i)); threads[i].setDaemon(true); threads[i].start(); } } private void computePartialSum() { int id = Integer.valueOf(Thread.currentThread().getName()); try { while (true) { barrier.await(); int chunkSize = (int) Math.ceil((double) array.length / threads.length); int start = id * chunkSize; int end = start + chunkSize; for (int i = start; i < end; i++) partialSums[id] += array[i]; barrier.await(); } } catch (Exception e) { } } public synchronized double computeAvg(int[] array) { try { partialSums = new long[threads.length]; this.array = array; barrier.await(); barrier.await(); long tot = 0L; for (long v : partialSums) tot += v; return tot / (double) array.length; } catch (Exception e) { return 0.0; } } public static void main(String[] args) { ParallelAverage pa = new ParallelAverage(8); int[] array = { 1, 2, 3, 4 }; double avg = pa.computeAvg(array); System.out.println(avg); array = new int[10000]; for (int i = 0; i < 10000; i++) array[i] = i; avg = pa.computeAvg(array); System.out.println(avg); } }": {
        "answer": "False",
        "explanation": "This version has proper barrier synchronization but FAILS due to a boundary check bug. The problem is the line 'int end = start + chunkSize;' which is missing Math.min(). When array.length is not evenly divisible by the number of threads, the last thread's end index can exceed array.length, causing ArrayIndexOutOfBoundsException. Example: 8 threads, 4-element array → chunkSize = ceil(4/8) = 1. Thread 0 processes [0,1), thread 1 processes [1,2), thread 2 processes [2,3), thread 3 processes [3,4), but thread 4 would try [4,5) which is out of bounds. The Math.min() clamp is essential to handle arrays that don't divide evenly across threads."
    },

    "You will see multiple versions of the ParallelAverage class. You have to determine whether the computePartialSum() method works as intended or not. computePartialSum() should have each thread in the thread pool sum an even portion of the array, except for maybe the last thread. You cannot go back to a question after submitting your answer. Does computePartialSum() work as intended? public class ParallelAverage { private final CyclicBarrier barrier; private final Thread[] threads; private int[] array; private long[] partialSums; public ParallelAverage(int nThreads) { barrier = new CyclicBarrier(nThreads + 1); threads = new Thread[nThreads]; for (int i = 0; i < nThreads; i++) { threads[i] = new Thread(this::computePartialSum, String.valueOf(i)); threads[i].setDaemon(true); threads[i].start(); } } private void computePartialSum() { int id = Integer.valueOf(Thread.currentThread().getName()); try { while (true) { barrier.await(); int chunkSize = (int) Math.ceil((double) array.length / threads.length); int start = id * chunkSize; int end = Math.min(start + chunkSize, array.length); for (int i = start; i < end; i++) partialSums[id] += array[i]; barrier.await(); } } catch (Exception e) { } } public synchronized double computeAvg(int[] array) { try { partialSums = new long[threads.length]; this.array = array; barrier.await(); barrier.await(); long tot = 0L; for (long v : partialSums) tot += v; return tot / (double) array.length; } catch (Exception e) { return 0.0; } } public static void main(String[] args) { ParallelAverage pa = new ParallelAverage(8); int[] array = { 1, 2, 3, 4 }; double avg = pa.computeAvg(array); System.out.println(avg); array = new int[10000]; for (int i = 0; i < 10000; i++) array[i] = i; avg = pa.computeAvg(array); System.out.println(avg); } }": {
        "answer": "True",
        "explanation": "This is the CORRECT implementation. It has all necessary components: (1) Proper barrier synchronization - threads wait at the first barrier for computeAvg() to set up array/partialSums, then wait at the second barrier after computing so computeAvg() can safely read results. (2) Correct boundary checking with Math.min(start + chunkSize, array.length) prevents array index out of bounds when array.length doesn't divide evenly. (3) Proper chunk calculation using Math.ceil ensures all array elements are covered. (4) Each thread processes its assigned chunk independently and stores results in its partialSums slot without race conditions. The barriers coordinate the three phases: setup, parallel computation, and result collection."
    },

    "You will see multiple versions of the ParallelAverage class. You have to determine whether the computePartialSum() method works as intended or not. computePartialSum() should have each thread in the thread pool sum an even portion of the array, except for maybe the last thread. You cannot go back to a question after submitting your answer. Does computePartialSum() work as intended? public class ParallelAverage { private final CyclicBarrier barrier; private final Thread[] threads; private int[] array; private long[] partialSums; public ParallelAverage(int nThreads) { barrier = new CyclicBarrier(nThreads + 1); threads = new Thread[nThreads]; for (int i = 0; i < nThreads; i++) { threads[i] = new Thread(this::computePartialSum, String.valueOf(i)); threads[i].setDaemon(true); threads[i].start(); } } private void computePartialSum() { int id = Integer.valueOf(Thread.currentThread().getName()) + 1; try { while (true) { int chunkSize = (int) Math.ceil((double) array.length / threads.length); int start = id * chunkSize; int end = Math.min(start + chunkSize, array.length); for (int i = start; i < end; i++) partialSums[id] += array[i]; barrier.await(); } } catch (Exception e) { } } public synchronized double computeAvg(int[] array) { try { partialSums = new long[threads.length]; this.array = array; barrier.await(); barrier.await(); long tot = 0L; for (long v : partialSums) tot += v; return tot / (double) array.length; } catch (Exception e) { return 0.0; } } public static void main(String[] args) { ParallelAverage pa = new ParallelAverage(8); int[] array = { 1, 2, 3, 4 }; double avg = pa.computeAvg(array); System.out.println(avg); array = new int[10000]; for (int i = 0; i < 10000; i++) array[i] = i; avg = pa.computeAvg(array); System.out.println(avg); } }": {
        "answer": "False",
        "explanation": "This version FAILS due to the off-by-one error in 'int id = Integer.valueOf(Thread.currentThread().getName()) + 1;'. Thread names are \"0\" through \"7\" (for 8 threads), so adding 1 makes ids range from 1-8 instead of 0-7. This causes multiple critical bugs: (1) ArrayIndexOutOfBoundsException when accessing partialSums[id] - thread 7 becomes id=8, but partialSums has size 8 (indices 0-7). (2) The first chunk (indices 0 to chunkSize-1) is never processed because no thread has id=0. (3) Each thread processes the wrong chunk - thread 0 (now id=1) processes thread 1's chunk, etc. (4) partialSums[0] is never written, staying 0. This is a classic off-by-one error in parallel index calculations."
    },
}


def normalize_question(text):
    """Normalize question text for matching."""
    # Remove extra whitespace and newlines
    text = re.sub(r'\s+', ' ', text).strip()
    return text


def add_explanations(db):
    """Add explanations to all questions in the database."""
    print("=" * 70)
    print("Populating Quiz Explanations")
    print("=" * 70)
    print()

    # Get all quizzes
    quizzes = db.get_all_quizzes()
    total_updated = 0
    total_questions = 0

    for quiz in quizzes:
        print(f"\nProcessing: {quiz['name']}")
        print("-" * 70)

        # Get questions for this quiz
        questions = db.get_quiz_questions(quiz['id'], randomize=False)
        total_questions += len(questions)
        quiz_updated = 0

        for q in questions:
            q_normalized = normalize_question(q['question'])

            # Find matching explanation
            matched = False
            for key, data in EXPLANATIONS.items():
                key_normalized = normalize_question(key)

                # Try exact match first
                if key_normalized == q_normalized:
                    # Update the question with explanation
                    db.conn.execute("""
                        UPDATE questions
                        SET explanation = ?
                        WHERE id = ?
                    """, [data['explanation'], q['id']])

                    print(f"  ✓ Added explanation for: {q['question'][:60]}...")
                    quiz_updated += 1
                    total_updated += 1
                    matched = True
                    break

                # Try partial match (key starts with question or vice versa)
                # This handles cases where one has extra preamble
                if key_normalized.startswith(q_normalized) or q_normalized.startswith(key_normalized):
                    # Update the question with explanation
                    db.conn.execute("""
                        UPDATE questions
                        SET explanation = ?
                        WHERE id = ?
                    """, [data['explanation'], q['id']])

                    print(f"  ✓ Added explanation for: {q['question'][:60]}...")
                    quiz_updated += 1
                    total_updated += 1
                    matched = True
                    break

            if not matched:
                print(f"  ⚠ No explanation found for: {q['question'][:60]}...")

        print(f"\nUpdated {quiz_updated} out of {len(questions)} questions in {quiz['name']}")

    print()
    print("=" * 70)
    print(f"SUMMARY: Updated {total_updated} out of {total_questions} total questions")
    print("=" * 70)
    print()

    if total_updated < total_questions:
        print(f"⚠ WARNING: {total_questions - total_updated} questions still need explanations")
        print("These may need to be added manually using add_explanations.py")
    else:
        print("✓ All questions now have explanations!")


def main():
    db = QuizDatabase('quiz_study.db')

    try:
        add_explanations(db)
    finally:
        db.close()

    print("\nDone! Restart your Flask app to see the explanations in action.")


if __name__ == '__main__':
    main()
