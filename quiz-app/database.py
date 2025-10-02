#!/usr/bin/env python3
"""
Database utilities for quiz application using DuckDB.
"""

import duckdb
from typing import List, Dict, Optional
from datetime import datetime
from pathlib import Path


class QuizDatabase:
    """Manage quiz data in DuckDB."""

    def __init__(self, db_path: str = "quiz_study.db"):
        self.db_path = db_path
        self.conn = duckdb.connect(db_path)
        self._initialize_schema()

    def _initialize_schema(self):
        """Create database schema."""
        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS quizzes (
                id INTEGER PRIMARY KEY,
                name VARCHAR NOT NULL,
                topic VARCHAR,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS questions (
                id INTEGER PRIMARY KEY,
                quiz_id INTEGER NOT NULL,
                question_text TEXT NOT NULL,
                correct_answer VARCHAR NOT NULL,
                question_type VARCHAR NOT NULL DEFAULT 'boolean',
                explanation TEXT,
                FOREIGN KEY (quiz_id) REFERENCES quizzes(id)
            )
        """)

        # Migration: Add explanation column if it doesn't exist
        try:
            self.conn.execute("""
                ALTER TABLE questions ADD COLUMN explanation TEXT
            """)
        except Exception:
            # Column already exists, ignore
            pass

        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS study_sessions (
                id INTEGER PRIMARY KEY,
                quiz_id INTEGER NOT NULL,
                started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP,
                total_questions INTEGER,
                correct_answers INTEGER,
                score DECIMAL(5,2),
                FOREIGN KEY (quiz_id) REFERENCES quizzes(id)
            )
        """)

        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS session_answers (
                id INTEGER PRIMARY KEY,
                session_id INTEGER NOT NULL,
                question_id INTEGER NOT NULL,
                user_answer VARCHAR,
                is_correct BOOLEAN,
                answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (session_id) REFERENCES study_sessions(id),
                FOREIGN KEY (question_id) REFERENCES questions(id)
            )
        """)

        # Create sequences for auto-increment IDs
        self.conn.execute("""
            CREATE SEQUENCE IF NOT EXISTS quiz_id_seq START 1
        """)
        self.conn.execute("""
            CREATE SEQUENCE IF NOT EXISTS question_id_seq START 1
        """)
        self.conn.execute("""
            CREATE SEQUENCE IF NOT EXISTS session_id_seq START 1
        """)
        self.conn.execute("""
            CREATE SEQUENCE IF NOT EXISTS answer_id_seq START 1
        """)

    def add_quiz(self, name: str, topic: str = None) -> int:
        """Add a new quiz and return its ID."""
        result = self.conn.execute("""
            INSERT INTO quizzes (id, name, topic)
            VALUES (nextval('quiz_id_seq'), ?, ?)
            RETURNING id
        """, [name, topic]).fetchone()
        return result[0]

    def add_questions(self, quiz_id: int, questions: List[Dict]):
        """Add multiple questions to a quiz."""
        for q in questions:
            self.conn.execute("""
                INSERT INTO questions (id, quiz_id, question_text, correct_answer, question_type, explanation)
                VALUES (nextval('question_id_seq'), ?, ?, ?, ?, ?)
            """, [quiz_id, q['question'], q['correct_answer'], q.get('type', 'boolean'), q.get('explanation')])

    def get_all_quizzes(self) -> List[Dict]:
        """Get all quizzes with question counts."""
        result = self.conn.execute("""
            SELECT
                q.id,
                q.name,
                q.topic,
                COUNT(qs.id) as question_count,
                q.created_at
            FROM quizzes q
            LEFT JOIN questions qs ON q.id = qs.quiz_id
            GROUP BY q.id, q.name, q.topic, q.created_at
            ORDER BY q.id
        """).fetchall()

        return [
            {
                'id': row[0],
                'name': row[1],
                'topic': row[2],
                'question_count': row[3],
                'created_at': row[4]
            }
            for row in result
        ]

    def get_quiz_questions(self, quiz_id: int, randomize: bool = False) -> List[Dict]:
        """Get all questions for a quiz."""
        order_clause = "ORDER BY RANDOM()" if randomize else "ORDER BY id"

        result = self.conn.execute(f"""
            SELECT id, question_text, correct_answer, question_type, explanation
            FROM questions
            WHERE quiz_id = ?
            {order_clause}
        """, [quiz_id]).fetchall()

        return [
            {
                'id': row[0],
                'question': row[1],
                'correct_answer': row[2],
                'type': row[3],
                'explanation': row[4]
            }
            for row in result
        ]

    def create_session(self, quiz_id: int) -> int:
        """Create a new study session and return its ID."""
        result = self.conn.execute("""
            INSERT INTO study_sessions (id, quiz_id, total_questions)
            VALUES (nextval('session_id_seq'), ?, 0)
            RETURNING id
        """, [quiz_id]).fetchone()
        return result[0]

    def record_answer(self, session_id: int, question_id: int,
                     user_answer: str, is_correct: bool):
        """Record a user's answer to a question."""
        self.conn.execute("""
            INSERT INTO session_answers (id, session_id, question_id, user_answer, is_correct)
            VALUES (nextval('answer_id_seq'), ?, ?, ?, ?)
        """, [session_id, question_id, user_answer, is_correct])

    def complete_session(self, session_id: int):
        """Mark a session as complete and calculate score."""
        self.conn.execute("""
            UPDATE study_sessions
            SET
                completed_at = CURRENT_TIMESTAMP,
                total_questions = (
                    SELECT COUNT(*) FROM session_answers WHERE session_id = ?
                ),
                correct_answers = (
                    SELECT COUNT(*) FROM session_answers
                    WHERE session_id = ? AND is_correct = true
                ),
                score = (
                    SELECT CAST(SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) AS DECIMAL) /
                           COUNT(*) * 100
                    FROM session_answers
                    WHERE session_id = ?
                )
            WHERE id = ?
        """, [session_id, session_id, session_id, session_id])

    def get_session_results(self, session_id: int) -> Dict:
        """Get results for a study session."""
        result = self.conn.execute("""
            SELECT
                s.quiz_id,
                q.name as quiz_name,
                s.total_questions,
                s.correct_answers,
                s.score,
                s.started_at,
                s.completed_at
            FROM study_sessions s
            JOIN quizzes q ON s.quiz_id = q.id
            WHERE s.id = ?
        """, [session_id]).fetchone()

        if not result:
            return None

        # Get detailed answers
        answers = self.conn.execute("""
            SELECT
                qs.question_text,
                qs.correct_answer,
                sa.user_answer,
                sa.is_correct,
                qs.explanation
            FROM session_answers sa
            JOIN questions qs ON sa.question_id = qs.id
            WHERE sa.session_id = ?
            ORDER BY sa.answered_at
        """, [session_id]).fetchall()

        return {
            'quiz_id': result[0],
            'quiz_name': result[1],
            'total_questions': result[2],
            'correct_answers': result[3],
            'score': result[4],
            'started_at': result[5],
            'completed_at': result[6],
            'answers': [
                {
                    'question': ans[0],
                    'correct_answer': ans[1],
                    'user_answer': ans[2],
                    'is_correct': ans[3],
                    'explanation': ans[4]
                }
                for ans in answers
            ]
        }

    def get_quiz_stats(self, quiz_id: int = None) -> List[Dict]:
        """Get statistics for quizzes."""
        where_clause = f"WHERE s.quiz_id = {quiz_id}" if quiz_id else ""

        result = self.conn.execute(f"""
            SELECT
                q.id,
                q.name,
                COUNT(DISTINCT s.id) as attempts,
                AVG(s.score) as avg_score,
                MAX(s.score) as best_score,
                MIN(s.score) as worst_score
            FROM quizzes q
            LEFT JOIN study_sessions s ON q.id = s.quiz_id AND s.completed_at IS NOT NULL
            {where_clause}
            GROUP BY q.id, q.name
            ORDER BY q.id
        """).fetchall()

        return [
            {
                'quiz_id': row[0],
                'quiz_name': row[1],
                'attempts': row[2],
                'avg_score': round(row[3], 2) if row[3] else 0,
                'best_score': round(row[4], 2) if row[4] else 0,
                'worst_score': round(row[5], 2) if row[5] else 0
            }
            for row in result
        ]

    def close(self):
        """Close database connection."""
        self.conn.close()


if __name__ == '__main__':
    # Test database
    db = QuizDatabase(':memory:')

    # Add a test quiz
    quiz_id = db.add_quiz("Test Quiz", "Testing")
    db.add_questions(quiz_id, [
        {'question': 'Is Python awesome?', 'correct_answer': 'True', 'type': 'boolean'},
        {'question': 'What is 2+2?', 'correct_answer': '4', 'type': 'numeric'}
    ])

    # Get quizzes
    quizzes = db.get_all_quizzes()
    print(f"Quizzes: {quizzes}")

    # Get questions
    questions = db.get_quiz_questions(quiz_id)
    print(f"Questions: {questions}")

    db.close()
    print("Database test successful!")
