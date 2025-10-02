#!/usr/bin/env python3
"""
Quiz parser to extract questions and answers from quiz text files.
"""

import re
from typing import List, Dict, Optional
from pathlib import Path


class QuizParser:
    """Parse quiz text into structured question data."""

    def __init__(self, quiz_text: str, quiz_name: str):
        self.quiz_text = quiz_text
        self.quiz_name = quiz_name
        self.questions = []

    def parse(self) -> List[Dict]:
        """Parse quiz text and return list of question dictionaries."""
        # Split by question numbers (e.g., "1\n1 / 1 point" or just "Results for question 2.")
        question_blocks = re.split(r'Results for question \d+\.', self.quiz_text)

        # First block before "Results for question" is the first question
        blocks = [question_blocks[0]] + question_blocks[1:]

        for block in blocks:
            if not block.strip():
                continue

            question_data = self._parse_question_block(block)
            if question_data:
                self.questions.append(question_data)

        return self.questions

    def _parse_question_block(self, block: str) -> Optional[Dict]:
        """Parse a single question block."""
        lines = [line.strip() for line in block.strip().split('\n') if line.strip()]

        if not lines:
            return None

        # Remove question number and point info (e.g., "1\n1 / 1 point")
        # But keep answer options and question text
        filtered_lines = []
        skip_next = False
        for i, line in enumerate(lines):
            # Skip standalone numbers ONLY if they're followed by point info
            if re.match(r'^\d+$', line) and i + 1 < len(lines) and re.match(r'^\d+\s*/\s*\d+\s*point', lines[i+1]):
                skip_next = True
                continue
            if skip_next and re.match(r'^\d+\s*/\s*\d+\s*point', line):
                skip_next = False
                continue
            skip_next = False
            filtered_lines.append(line)

        if not filtered_lines:
            return None

        # Extract question text (everything before "True" or "Correct answer:")
        question_text = []
        answer_section_idx = None

        for i, line in enumerate(filtered_lines):
            if line in ['True', 'False'] or line.startswith('Correct answer:'):
                answer_section_idx = i
                break
            question_text.append(line)

        if not question_text:
            return None

        question = ' '.join(question_text).strip()

        # Extract correct answer
        correct_answer = None
        question_type = 'boolean'  # Default to True/False

        if answer_section_idx is not None:
            # Look for "Correct answer:" label
            for i in range(answer_section_idx, len(filtered_lines)):
                line = filtered_lines[i]
                if line.startswith('Correct answer:'):
                    # Find the first non-empty line that's not ", Not Selected"
                    for j in range(i + 1, len(filtered_lines)):
                        potential_answer = filtered_lines[j].strip()
                        # Skip empty lines and ", Not Selected" lines
                        if potential_answer and ', Not Selected' not in potential_answer:
                            correct_answer = potential_answer
                            break
                    break
                elif line in ['True', 'False']:
                    # Check if this is marked as correct (not preceded by wrong answer indicator)
                    if i == answer_section_idx:
                        correct_answer = line
                    continue

            # Try to detect if it's a numeric answer or other types
            if correct_answer:
                if correct_answer.isdigit():
                    question_type = 'numeric'
                elif correct_answer in ['High', 'Low']:
                    question_type = 'choice'

        if not correct_answer:
            return None

        return {
            'question': question,
            'correct_answer': correct_answer,
            'type': question_type,
            'quiz_name': self.quiz_name
        }


def parse_quiz_file(file_path: str, quiz_name: str = None) -> List[Dict]:
    """Parse a quiz file and return questions."""
    path = Path(file_path)

    if not quiz_name:
        quiz_name = path.stem.replace('_', ' ').title()

    with open(file_path, 'r', encoding='utf-8') as f:
        quiz_text = f.read()

    parser = QuizParser(quiz_text, quiz_name)
    return parser.parse()


if __name__ == '__main__':
    # Test the parser
    import sys

    if len(sys.argv) < 2:
        print("Usage: python quiz_parser.py <quiz_file>")
        sys.exit(1)

    questions = parse_quiz_file(sys.argv[1])

    print(f"Parsed {len(questions)} questions:")
    for i, q in enumerate(questions, 1):
        print(f"\n{i}. {q['question'][:80]}...")
        print(f"   Answer: {q['correct_answer']} (Type: {q['type']})")
