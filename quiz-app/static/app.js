// Quiz application JavaScript

let sessionId = null;
let currentQuestion = null;
let totalQuestions = 0;
let currentQuestionNum = 0;

async function startQuiz() {
    const randomize = document.getElementById('randomize').checked;

    try {
        const response = await fetch(`/api/quiz/${QUIZ_ID}/start`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ randomize })
        });

        const data = await response.json();

        if (response.ok) {
            sessionId = data.session_id;
            totalQuestions = data.total_questions;
            currentQuestionNum = 1;

            // Hide start screen, show quiz screen
            document.getElementById('start-screen').style.display = 'none';
            document.getElementById('quiz-screen').style.display = 'block';
            document.getElementById('progress-container').style.display = 'block';

            // Display first question
            displayQuestion(data.question);
        } else {
            alert('Error starting quiz: ' + data.error);
        }
    } catch (error) {
        alert('Error starting quiz: ' + error.message);
    }
}

function formatQuestionText(text) {
    // Detect if the text contains code (multiple lines with indentation or code-like syntax)
    const lines = text.split('\n');
    const hasCode = lines.some(line => line.match(/^\s{4,}/) || line.match(/^(public|private|protected|class|interface|void|int|String|byte\[\])/));

    if (hasCode && lines.length > 3) {
        // Split into description and code sections
        let descriptionLines = [];
        let codeLines = [];
        let inCode = false;

        for (let line of lines) {
            // Detect start of code block (indented or starts with code keyword)
            if (!inCode && (line.match(/^\s{4,}/) || line.match(/^(public|private|protected|class|interface|void|int|String|byte\[\])/))) {
                inCode = true;
            }

            if (inCode) {
                codeLines.push(line);
            } else {
                descriptionLines.push(line);
            }
        }

        // Format with description followed by code block
        let html = '';
        if (descriptionLines.length > 0) {
            html += '<p class="mb-4">' + escapeHtml(descriptionLines.join(' ').trim()) + '</p>';
        }
        if (codeLines.length > 0) {
            html += '<pre class="code-block"><code>' + escapeHtml(codeLines.join('\n')) + '</code></pre>';
        }
        return html;
    } else {
        // Regular text, just escape and preserve line breaks
        return escapeHtml(text).replace(/\n/g, '<br>');
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function displayQuestion(question) {
    currentQuestion = question;

    // Update progress
    updateProgress();

    // Update question text
    document.getElementById('question-number').textContent = currentQuestionNum;
    document.getElementById('question-text').innerHTML = formatQuestionText(question.question);

    // Clear previous feedback
    document.getElementById('feedback').style.display = 'none';
    document.getElementById('next-btn').style.display = 'none';
    document.getElementById('results-btn').style.display = 'none';

    // Generate answer options based on question type
    const optionsContainer = document.getElementById('answer-options');
    optionsContainer.innerHTML = '';

    if (question.type === 'boolean') {
        // True/False buttons
        ['True', 'False'].forEach(answer => {
            const button = document.createElement('button');
            button.className = 'w-full text-left p-4 border-2 border-gray-300 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-colors';
            button.textContent = answer;
            button.onclick = () => submitAnswer(answer);
            optionsContainer.appendChild(button);
        });
    } else if (question.type === 'choice') {
        // High/Low choice buttons
        ['High', 'Low'].forEach(answer => {
            const button = document.createElement('button');
            button.className = 'w-full text-left p-4 border-2 border-gray-300 rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-colors';
            button.textContent = answer;
            button.onclick = () => submitAnswer(answer);
            optionsContainer.appendChild(button);
        });
    } else if (question.type === 'numeric') {
        // Number input
        const input = document.createElement('input');
        input.type = 'number';
        input.id = 'numeric-answer';
        input.className = 'w-full p-4 border-2 border-gray-300 rounded-lg focus:border-blue-500 focus:outline-none';
        input.placeholder = 'Enter your answer';
        optionsContainer.appendChild(input);

        const submitBtn = document.createElement('button');
        submitBtn.className = 'w-full mt-3 bg-blue-500 hover:bg-blue-600 text-white font-semibold py-3 px-4 rounded transition-colors';
        submitBtn.textContent = 'Submit Answer';
        submitBtn.onclick = () => {
            const value = document.getElementById('numeric-answer').value;
            if (value) {
                submitAnswer(value);
            }
        };
        optionsContainer.appendChild(submitBtn);

        // Allow Enter key to submit
        input.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                submitBtn.click();
            }
        });
    }
}

async function submitAnswer(answer) {
    // Disable answer options
    const optionsContainer = document.getElementById('answer-options');
    const buttons = optionsContainer.querySelectorAll('button, input');
    buttons.forEach(btn => btn.disabled = true);

    try {
        const response = await fetch(`/api/session/${sessionId}/answer`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ answer })
        });

        const data = await response.json();

        if (response.ok) {
            showFeedback(data.is_correct, data.correct_answer, data.explanation);

            if (data.completed) {
                // Quiz complete
                document.getElementById('results-btn').style.display = 'block';
            } else {
                // More questions
                currentQuestion = data.next_question;
                currentQuestionNum = data.question_number;
                document.getElementById('next-btn').style.display = 'block';
            }
        } else {
            alert('Error submitting answer: ' + data.error);
        }
    } catch (error) {
        alert('Error submitting answer: ' + error.message);
    }
}

function showFeedback(isCorrect, correctAnswer, explanation) {
    const feedback = document.getElementById('feedback');
    const title = document.getElementById('feedback-title');
    const text = document.getElementById('feedback-text');

    feedback.style.display = 'block';

    if (isCorrect) {
        feedback.className = 'p-4 rounded-lg mb-6 bg-green-100 border-l-4 border-green-500';
        title.className = 'font-semibold mb-2 text-green-800';
        title.textContent = '✓ Correct!';
        text.className = 'text-sm text-green-700';
        text.textContent = 'Great job! You got it right.';

        // Show explanation for correct answers if available
        if (explanation) {
            text.innerHTML = `Great job! You got it right.<br><br><strong>💡 Explanation:</strong><br>${explanation}`;
        }
    } else {
        feedback.className = 'p-4 rounded-lg mb-6 bg-red-100 border-l-4 border-red-500';
        title.className = 'font-semibold mb-2 text-red-800';
        title.textContent = '✗ Incorrect';
        text.className = 'text-sm text-red-700';

        // Build feedback message with explanation if available
        let feedbackMessage = `The correct answer is: <strong>${correctAnswer}</strong>`;
        if (explanation) {
            feedbackMessage += `<br><br><strong>💡 Why?</strong><br>${explanation}`;
        }
        text.innerHTML = feedbackMessage;
    }
}

function nextQuestion() {
    displayQuestion(currentQuestion);
}

function viewResults() {
    window.location.href = `/results/${sessionId}`;
}

function updateProgress() {
    const progressBar = document.getElementById('progress-bar');
    const progressText = document.getElementById('progress-text');

    const percentage = (currentQuestionNum / totalQuestions) * 100;

    progressBar.style.width = percentage + '%';
    progressText.textContent = `${currentQuestionNum} / ${totalQuestions}`;
}
