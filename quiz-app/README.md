# CS555 Quiz Study Tool

An interactive web application to help you study for your CS555 midterm using your quiz materials.

## Features

- Parse and import quiz questions from text files
- Interactive quiz-taking with immediate feedback
- **Learning explanations** - See why answers are correct/incorrect
- Track your progress and scores over time
- Review wrong answers with detailed explanations
- Randomize question order for better learning
- Statistics dashboard showing your performance

## Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

## Usage

1. **Add your quiz files**: Place quiz text files in the `quizzes/` directory (already includes quiz1 and quiz2)

2. **Start the application**:
```bash
python app.py
```

3. **Open your browser**: Visit http://localhost:5000

4. **Load quizzes**: Click "Load Quizzes" button on the home page to import quiz data

5. **Start studying**: Select a quiz and begin!

## Project Structure

```
quiz-app/
├── app.py              # Flask web application
├── database.py         # DuckDB database management
├── quiz_parser.py      # Parse quiz text files
├── requirements.txt    # Python dependencies
├── quiz_study.db       # Database (created on first run)
├── quizzes/           # Quiz text files
│   ├── quiz1_threads.txt
│   └── quiz2_synchronization.txt
├── static/
│   └── app.js         # Frontend JavaScript
└── templates/         # HTML templates
    ├── index.html     # Home page
    ├── quiz.html      # Quiz interface
    └── results.html   # Results page
```

## Adding More Quizzes

Simply add new quiz files to the `quizzes/` directory in the same format as the existing files, then click "Load Quizzes" in the web interface.

## Adding Explanations to Questions

To help you learn from your mistakes, you can add explanations to questions:

### Method 1: Interactive Script
```bash
source venv/bin/activate
python3 add_explanations.py
```

This will guide you through adding explanations to each question in a quiz.

### Method 2: Direct SQL
```bash
source venv/bin/activate
python3
```

```python
from database import QuizDatabase

db = QuizDatabase('quiz_study.db')

# Add explanation to a specific question
db.conn.execute("""
    UPDATE questions
    SET explanation = 'Your explanation here'
    WHERE id = 1
""")

db.close()
```

### How Explanations Appear

When you get an answer wrong, you'll see:
```
✗ Incorrect
The correct answer is: False

💡 Why?
[Your explanation will appear here]
```

Explanations also appear in the results page for review.

## Database

Uses DuckDB to store:
- Quiz metadata
- Questions and answers
- Study session history
- Performance statistics

## Tips for Studying

1. Take each quiz multiple times
2. Review questions you got wrong
3. Use randomization to avoid memorizing order
4. Track your progress over time
5. Focus on concepts with lower scores
