#!/usr/bin/env python3
"""
Fix Quiz 11 (Parallel Average) explanations by matching based on code patterns.
"""

from database import QuizDatabase

def fix_quiz11():
    db = QuizDatabase('quiz_study.db')

    # Get Quiz 11 questions
    questions = db.get_quiz_questions(11)

    print(f"Found {len(questions)} questions in Quiz 11")
    print()

    for i, q in enumerate(questions, 1):
        print(f"Question {i} (ID: {q['id']})")
        print(f"  Answer: {q['correct_answer']}")
        print(f"  Has explanation: {'Yes' if q['explanation'] else 'No'}")

        question_text = q['question']

        # Match based on code patterns and correct answer
        explanation = None

        # Extract computePartialSum method to check for barrier.await() within it
        import re
        computePartialSum_match = re.search(r'private void computePartialSum\(\).*?(?=public |$)', question_text, re.DOTALL)
        computePartialSum_text = computePartialSum_match.group(0) if computePartialSum_match else ""

        # Q1: Missing barrier.await() in computePartialSum (Answer: False)
        if q['correct_answer'] == 'False' and 'barrier.await()' not in computePartialSum_text and 'while (true)' in computePartialSum_text and 'int end = Math.min' in computePartialSum_text:
            explanation = "This version is INCORRECT because computePartialSum() has NO barrier synchronization. The worker threads start executing immediately in their while(true) loop, but there's no coordination with computeAvg(). Race conditions include: (1) Threads may access array before it's set by computeAvg(), potentially causing NullPointerException. (2) Threads may access uninitialized partialSums or continue using old values from previous iterations. (3) computeAvg() calls barrier.await() twice, but threads never await, so the main thread blocks forever. (4) Even if timing works once by luck, there's no mechanism to coordinate subsequent computeAvg() calls. Barriers are essential for coordinating the setup/compute/collect phases."
            print(f"  Matched: Q1 - Missing barrier sync")

        # Q2: Has barrier but missing Math.min (Answer: False)
        elif q['correct_answer'] == 'False' and 'barrier.await()' in question_text and 'int end = start + chunkSize;' in question_text and 'Math.min' not in question_text.split('int end = ')[1].split(';')[0]:
            explanation = "This version has proper barrier synchronization but FAILS due to a boundary check bug. The problem is the line 'int end = start + chunkSize;' which is missing Math.min(). When array.length is not evenly divisible by the number of threads, the last thread's end index can exceed array.length, causing ArrayIndexOutOfBoundsException. Example: 8 threads, 4-element array → chunkSize = ceil(4/8) = 1. Thread 0 processes [0,1), thread 1 processes [1,2), thread 2 processes [2,3), thread 3 processes [3,4), but thread 4 would try [4,5) which is out of bounds. The Math.min() clamp is essential to handle arrays that don't divide evenly across threads."
            print(f"  Matched: Q2 - Missing Math.min")

        # Q3: Correct version (Answer: True)
        elif q['correct_answer'] == 'True' and 'barrier.await()' in question_text and 'Math.min(start + chunkSize, array.length)' in question_text:
            explanation = "This is the CORRECT implementation. It has all necessary components: (1) Proper barrier synchronization - threads wait at the first barrier for computeAvg() to set up array/partialSums, then wait at the second barrier after computing so computeAvg() can safely read results. (2) Correct boundary checking with Math.min(start + chunkSize, array.length) prevents array index out of bounds when array.length doesn't divide evenly. (3) Proper chunk calculation using Math.ceil ensures all array elements are covered. (4) Each thread processes its assigned chunk independently and stores results in its partialSums slot without race conditions. The barriers coordinate the three phases: setup, parallel computation, and result collection."
            print(f"  Matched: Q3 - Correct implementation")

        # Q4: Off-by-one error with id + 1 (Answer: False)
        elif q['correct_answer'] == 'False' and 'Integer.valueOf(Thread.currentThread().getName()) + 1' in question_text:
            explanation = "This version FAILS due to the off-by-one error in 'int id = Integer.valueOf(Thread.currentThread().getName()) + 1;'. Thread names are \"0\" through \"7\" (for 8 threads), so adding 1 makes ids range from 1-8 instead of 0-7. This causes multiple critical bugs: (1) ArrayIndexOutOfBoundsException when accessing partialSums[id] - thread 7 becomes id=8, but partialSums has size 8 (indices 0-7). (2) The first chunk (indices 0 to chunkSize-1) is never processed because no thread has id=0. (3) Each thread processes the wrong chunk - thread 0 (now id=1) processes thread 1's chunk, etc. (4) partialSums[0] is never written, staying 0. This is a classic off-by-one error in parallel index calculations."
            print(f"  Matched: Q4 - Off-by-one error")

        if explanation and not q['explanation']:
            db.conn.execute("""
                UPDATE questions
                SET explanation = ?
                WHERE id = ?
            """, [explanation, q['id']])
            print(f"  ✓ Added explanation")
        elif q['explanation']:
            print(f"  ⊙ Already has explanation")
        else:
            print(f"  ✗ No match found!")

        print()

    db.close()
    print("Done!")

if __name__ == '__main__':
    fix_quiz11()
