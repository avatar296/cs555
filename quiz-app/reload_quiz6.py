#!/usr/bin/env python3
"""
Reload quiz 6 with updated parser that preserves code formatting.
"""

from database import QuizDatabase
from quiz_parser import parse_quiz_file

def reload_quiz_6():
    db = QuizDatabase('quiz_study.db')

    # Delete quiz 6 and all related data
    print("Deleting quiz 6 and related data...")
    db.conn.execute("DELETE FROM session_answers WHERE session_id IN (SELECT id FROM study_sessions WHERE quiz_id = 6)")
    db.conn.execute("DELETE FROM study_sessions WHERE quiz_id = 6")
    db.conn.execute("DELETE FROM questions WHERE quiz_id = 6")
    db.conn.execute("DELETE FROM quizzes WHERE id = 6")

    # Parse quiz 6 from source file
    print("Parsing quiz6_marshalling.txt...")
    questions = parse_quiz_file('quizzes/quiz6_marshalling.txt')
    print(f"Parsed {len(questions)} questions")

    # Add quiz back to database
    print("Adding quiz back to database...")
    db.conn.execute("""
        INSERT INTO quizzes (id, name, topic)
        VALUES (6, 'Quiz6 Marshalling', 'Distributed Systems')
    """)

    # Add questions
    db.add_questions(6, questions)

    print("✓ Quiz 6 reloaded successfully!")

    # Verify
    reloaded_questions = db.get_quiz_questions(6)
    print(f"\nVerification: Found {len(reloaded_questions)} questions in database")

    # Show first question preview
    if reloaded_questions:
        first_q = reloaded_questions[0]['question']
        print(f"\nFirst question preview (first 200 chars):")
        print(first_q[:200])
        print(f"... (total length: {len(first_q)} characters)")
        print(f"\nHas newlines: {'Yes' if '\\n' in first_q else 'No'}")

    db.close()

if __name__ == '__main__':
    reload_quiz_6()
