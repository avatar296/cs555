#!/usr/bin/env python3
"""
Helper script to add explanations to quiz questions.

Usage:
    python add_explanations.py

This will launch an interactive prompt to add explanations to questions.
"""

from database import QuizDatabase


def main():
    db = QuizDatabase('quiz_study.db')

    print("=" * 60)
    print("Quiz Explanation Manager")
    print("=" * 60)
    print()

    # Get all quizzes
    quizzes = db.get_all_quizzes()

    if not quizzes:
        print("No quizzes found. Please load quizzes first.")
        return

    # Display quizzes
    print("Available quizzes:")
    for quiz in quizzes:
        print(f"  {quiz['id']}. {quiz['name']} ({quiz['question_count']} questions)")
    print()

    # Select quiz
    quiz_id = int(input("Enter quiz ID to add explanations: "))

    # Get questions for selected quiz
    questions = db.get_quiz_questions(quiz_id, randomize=False)

    if not questions:
        print("No questions found for this quiz.")
        return

    print(f"\nFound {len(questions)} questions.")
    print("Enter explanations for each question (press Enter to skip):")
    print("-" * 60)
    print()

    for i, q in enumerate(questions, 1):
        print(f"\nQuestion {i}:")
        print(f"  {q['question'][:100]}...")
        print(f"  Correct answer: {q['correct_answer']}")

        if q['explanation']:
            print(f"  Current explanation: {q['explanation']}")
            update = input("  Update explanation? (y/n): ").lower()
            if update != 'y':
                continue

        print()
        explanation = input("  Enter explanation (or press Enter to skip): ").strip()

        if explanation:
            # Update explanation in database
            db.conn.execute("""
                UPDATE questions
                SET explanation = ?
                WHERE id = ?
            """, [explanation, q['id']])
            print("  ✓ Explanation added!")
        else:
            print("  Skipped.")

    db.close()
    print("\n" + "=" * 60)
    print("Done! Explanations have been saved.")
    print("=" * 60)


if __name__ == '__main__':
    main()
