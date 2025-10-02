#!/usr/bin/env python3
"""
Flask web application for quiz study tool.
"""

from flask import Flask, render_template, request, jsonify, redirect, url_for
from database import QuizDatabase
from quiz_parser import parse_quiz_file
from pathlib import Path
import json

app = Flask(__name__)
app.config['SECRET_KEY'] = 'dev-secret-key-change-in-production'

# Store active sessions in memory (could be moved to DB for persistence)
active_sessions = {}


def get_db():
    """Get database connection (lazy initialization)."""
    if not hasattr(app, 'db'):
        app.db = QuizDatabase('quiz_study.db')
        # Auto-load quizzes on first database initialization
        auto_load_quizzes()
    return app.db


def auto_load_quizzes():
    """Automatically load quiz files if no quizzes exist in database."""
    db = app.db
    existing_quizzes = db.get_all_quizzes()

    # If quizzes already loaded, skip
    if existing_quizzes:
        return

    quizzes_dir = Path(__file__).parent / 'quizzes'

    if not quizzes_dir.exists():
        print("Warning: quizzes directory not found")
        return

    print("Auto-loading quizzes from quizzes/ directory...")
    loaded_count = 0

    for quiz_file in sorted(quizzes_dir.glob('*.txt')):
        try:
            # Parse the quiz
            questions = parse_quiz_file(str(quiz_file))

            if not questions:
                print(f"  ⚠ No questions found in {quiz_file.name}")
                continue

            # Determine topic from filename or content
            quiz_name = quiz_file.stem.replace('_', ' ').title()

            # Determine topic based on quiz name or first question
            topic = "General"
            if "thread" in quiz_name.lower() or any("thread" in q['question'].lower() for q in questions[:3]):
                topic = "Threads & Concurrency"
            elif "synchron" in quiz_name.lower() or any("synchron" in q['question'].lower() for q in questions[:3]):
                topic = "Synchronization"
            elif "lock" in quiz_name.lower():
                topic = "Synchronization"
            elif "p2p" in quiz_name.lower() or "pastry" in quiz_name.lower() or "chord" in quiz_name.lower():
                topic = "Distributed Systems"

            # Add quiz to database
            quiz_id = db.add_quiz(quiz_name, topic)
            db.add_questions(quiz_id, questions)

            print(f"  ✓ Loaded {quiz_name}: {len(questions)} questions")
            loaded_count += 1

        except Exception as e:
            print(f"  ✗ Error loading {quiz_file.name}: {str(e)}")

    if loaded_count > 0:
        print(f"Successfully auto-loaded {loaded_count} quizzes!")
    else:
        print("No quizzes were loaded.")


@app.route('/')
def index():
    """Home page showing all quizzes and stats."""
    db = get_db()
    quizzes = db.get_all_quizzes()
    stats = db.get_quiz_stats()

    # Merge stats into quiz data
    stats_dict = {s['quiz_id']: s for s in stats}
    for quiz in quizzes:
        quiz_stats = stats_dict.get(quiz['id'], {})
        quiz['stats'] = quiz_stats

    return render_template('index.html', quizzes=quizzes)


@app.route('/quiz/<int:quiz_id>')
def quiz_page(quiz_id):
    """Quiz taking page."""
    db = get_db()
    quizzes = db.get_all_quizzes()
    quiz = next((q for q in quizzes if q['id'] == quiz_id), None)

    if not quiz:
        return "Quiz not found", 404

    return render_template('quiz.html', quiz=quiz)


@app.route('/api/quiz/<int:quiz_id>/start', methods=['POST'])
def start_quiz(quiz_id):
    """Start a new quiz session."""
    db = get_db()
    data = request.json or {}
    randomize = data.get('randomize', True)

    # Create session
    session_id = db.create_session(quiz_id)

    # Get questions
    questions = db.get_quiz_questions(quiz_id, randomize=randomize)

    # Store session data
    active_sessions[session_id] = {
        'quiz_id': quiz_id,
        'questions': questions,
        'current_index': 0
    }

    return jsonify({
        'session_id': session_id,
        'total_questions': len(questions),
        'question': questions[0] if questions else None
    })


@app.route('/api/session/<int:session_id>/answer', methods=['POST'])
def submit_answer(session_id):
    """Submit an answer for the current question."""
    db = get_db()
    if session_id not in active_sessions:
        return jsonify({'error': 'Session not found'}), 404

    data = request.json
    user_answer = data.get('answer')

    session = active_sessions[session_id]
    current_idx = session['current_index']
    questions = session['questions']

    if current_idx >= len(questions):
        return jsonify({'error': 'No more questions'}), 400

    current_question = questions[current_idx]

    # Check if answer is correct
    is_correct = user_answer.strip() == current_question['correct_answer'].strip()

    # Record the answer
    db.record_answer(
        session_id,
        current_question['id'],
        user_answer,
        is_correct
    )

    # Move to next question
    session['current_index'] += 1
    current_idx = session['current_index']

    # Check if quiz is complete
    if current_idx >= len(questions):
        db.complete_session(session_id)
        return jsonify({
            'is_correct': is_correct,
            'correct_answer': current_question['correct_answer'],
            'explanation': current_question.get('explanation'),
            'completed': True,
            'session_id': session_id
        })

    # Return next question
    next_question = questions[current_idx]
    return jsonify({
        'is_correct': is_correct,
        'correct_answer': current_question['correct_answer'],
        'explanation': current_question.get('explanation'),
        'completed': False,
        'next_question': next_question,
        'question_number': current_idx + 1,
        'total_questions': len(questions)
    })


@app.route('/results/<int:session_id>')
def results_page(session_id):
    """Show results for a completed session."""
    db = get_db()
    results = db.get_session_results(session_id)

    if not results:
        return "Session not found", 404

    # Clean up active session
    if session_id in active_sessions:
        del active_sessions[session_id]

    return render_template('results.html', results=results)


@app.route('/api/quiz/<int:quiz_id>/stats')
def quiz_stats(quiz_id):
    """Get statistics for a specific quiz."""
    db = get_db()
    stats = db.get_quiz_stats(quiz_id)
    return jsonify(stats[0] if stats else {})


@app.route('/load-quizzes', methods=['POST'])
def load_quizzes():
    """Load all quiz files from the quizzes directory."""
    db = get_db()
    quizzes_dir = Path(__file__).parent / 'quizzes'

    if not quizzes_dir.exists():
        return jsonify({'error': 'Quizzes directory not found'}), 404

    loaded = []
    errors = []

    for quiz_file in quizzes_dir.glob('*.txt'):
        try:
            # Parse the quiz
            questions = parse_quiz_file(str(quiz_file))

            if not questions:
                errors.append(f"No questions found in {quiz_file.name}")
                continue

            # Determine topic from filename or content
            quiz_name = quiz_file.stem.replace('_', ' ').title()

            # Determine topic based on quiz name or first question
            topic = "General"
            if "thread" in quiz_name.lower() or any("thread" in q['question'].lower() for q in questions[:3]):
                topic = "Threads & Concurrency"
            elif "synchron" in quiz_name.lower() or any("synchron" in q['question'].lower() for q in questions[:3]):
                topic = "Synchronization"

            # Add quiz to database
            quiz_id = db.add_quiz(quiz_name, topic)
            db.add_questions(quiz_id, questions)

            loaded.append({
                'name': quiz_name,
                'topic': topic,
                'question_count': len(questions)
            })

        except Exception as e:
            errors.append(f"Error loading {quiz_file.name}: {str(e)}")

    return jsonify({
        'loaded': loaded,
        'errors': errors,
        'total': len(loaded)
    })


if __name__ == '__main__':
    print("Starting Quiz Study App...")
    print("Visit http://localhost:5000")
    app.run(debug=True, port=5000)
