#!/usr/bin/env python3
"""
Fix the remaining 9 questions that need explanations.
"""

from database import QuizDatabase

def fix_quiz2_explanations(db):
    """Fix Quiz 2 - Class A synchronized methods questions."""

    # Get Quiz 2 questions
    questions = db.get_quiz_questions(2)

    explanations = {
        "a1.s1() and Thread T2 can be active in a2.s2()":
            "Since a1 and a2 are different object instances of class A, they have separate intrinsic locks. Synchronized methods acquire the lock on the specific object instance (this). Therefore, T1 can hold the lock on a1 while T2 simultaneously holds the lock on a2 without any conflict, allowing both threads to execute concurrently.",

        "a1.u1() and Thread T2 can be active in a1.u2()":
            "Both u1() and u2() are unsynchronized methods. Unsynchronized methods do not require any locks, so multiple threads can execute them concurrently on the same object instance (a1) without any synchronization restrictions. This is true even though they're accessing the same object - the lack of synchronization means there's no mutual exclusion.",

        "a1.s1() and Thread T2 can be active in a1.s2()":
            "Both s1() and s2() are synchronized methods on the same object instance (a1). When T1 enters s1(), it acquires the intrinsic lock on a1. T2 cannot enter s2() until T1 releases the lock, because there is only one lock per object, and all synchronized methods on that object require the same lock. This ensures mutual exclusion between synchronized methods on the same object.",

        "Threads T1 and T2 can be active inside method a1.s1()":
            "The synchronized method s1() requires acquiring the lock on object a1. Only one thread can hold this lock at a time. If T1 is executing inside a1.s1(), it holds the lock on a1, and T2 must wait until T1 exits s1() and releases the lock before T2 can enter. This mutual exclusion is the fundamental purpose of synchronization.",

        "Threads T1, T2, …, TN can all be active in instance a1":
            "Multiple threads can execute unsynchronized methods on the same object instance simultaneously, and each thread always has its own program counter tracking its execution position. Even if threads are executing synchronized methods sequentially, each thread maintains its own program counter. The program counter is per-thread, not per-object."
    }

    for q in questions:
        if q['explanation']:  # Skip if already has explanation
            continue

        # Match based on distinctive phrases
        for key_phrase, explanation in explanations.items():
            if key_phrase in q['question']:
                db.conn.execute("""
                    UPDATE questions
                    SET explanation = ?
                    WHERE id = ?
                """, [explanation, q['id']])
                print(f"  ✓ Fixed Quiz 2: {q['question'][:60]}...")
                break


def fix_quiz6_explanations(db):
    """Fix Quiz 6 - Marshalling questions."""

    # Get Quiz 6 questions
    questions = db.get_quiz_questions(6)

    # Match by looking for specific code patterns and checking correct answer
    for q in questions:
        if q['explanation']:  # Skip if already has explanation
            continue

        question_text = q['question']

        # Question 1: Missing timestamp write, answer = False
        if "dout.writeInt(packetSizes.length);" in question_text and "dout.writeLong(timestamp);" not in question_text and q['correct_answer'] == 'False':
            explanation = "The marshalling (getBytes) and unmarshalling (constructor) are mismatched. In getBytes(), the order is: MACAddress length, MACAddress bytes, packetSizes length, packetSizes values. But 'timestamp' is NEVER written. In the constructor, it tries to read timestamp with readLong(), but timestamp was never marshalled. The read will get garbage data (reading part of packetCount as a long). Fields must be marshalled in the same order they're unmarshalled."

        # Question 2: Loop reads length-1 elements, answer = False
        elif "for (int i = 0; i < packetSizes.length - 1; i++)" in question_text and q['correct_answer'] == 'False':
            explanation = "The unmarshalling loop is wrong: 'for (int i = 0; i < packetSizes.length - 1; i++)' only reads packetSizes.length - 1 elements, leaving the last element uninitialized (value 0). If packetSizes.length is 5, it only reads 4 values. The marshalling writes all 5 values. This mismatch means: (1) last element stays 0, (2) one int remains unread in the stream, (3) subsequent reads (if any) would read wrong data."

        # Question 3: Correct version, answer = True
        elif "for (int i = 0; i < packetCount; i++)" in question_text and "dout.writeLong(timestamp);" in question_text and "dout.writeInt(MACAddressLength);" in question_text and q['correct_answer'] == 'True':
            explanation = "This is CORRECT. The marshalling order matches unmarshalling: (1) Write/read MAC address length, (2) Write/read MAC address bytes, (3) Write/read timestamp (long), (4) Write/read packet count, (5) Write/read all packet sizes. The loop 'for (int i = 0; i < packetCount; i++)' correctly reads all elements. Every field written is read in the same order with the same type, ensuring correct serialization/deserialization."

        # Question 4: Missing length write, answer = False
        elif "dout.write(MACAddressBytes);" in question_text and "dout.writeInt(MACAddressLength);" not in question_text and q['correct_answer'] == 'False':
            explanation = "The marshalling is missing 'dout.writeInt(MACAddressLength)' before writing the bytes. In getBytes(), it writes MAC bytes directly without the length prefix. In the constructor, it reads the length first (readInt()), expecting the length before the bytes. This mismatch causes the first 4 bytes of MACAddressBytes to be interpreted as the length integer, corrupting the data. You must write the length before variable-length data so unmarshalling knows how many bytes to read."
        else:
            continue

        db.conn.execute("""
            UPDATE questions
            SET explanation = ?
            WHERE id = ?
        """, [explanation, q['id']])
        print(f"  ✓ Fixed Quiz 6: {q['question'][:60]}...")


def main():
    db = QuizDatabase('quiz_study.db')

    print("Fixing remaining 9 questions...")
    print()

    fix_quiz2_explanations(db)
    fix_quiz6_explanations(db)

    db.close()

    print()
    print("Done! All questions should now have explanations.")


if __name__ == '__main__':
    main()
