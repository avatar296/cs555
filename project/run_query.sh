#!/bin/bash

# Helper script to run the latency metrics query
# Automatically creates venv and installs dependencies if needed

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"
PYTHON_SCRIPT="$SCRIPT_DIR/query_latency_metrics.py"
REQUIREMENTS="$SCRIPT_DIR/requirements.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    error "python3 is not installed. Please install Python 3."
    exit 1
fi

# Create virtual environment if it doesn't exist
if [ ! -d "$VENV_DIR" ]; then
    info "Creating virtual environment at $VENV_DIR..."
    python3 -m venv "$VENV_DIR"
    info "Virtual environment created successfully."
fi

# Activate virtual environment
info "Activating virtual environment..."
source "$VENV_DIR/bin/activate"

# Install/upgrade pip
info "Upgrading pip..."
pip install --quiet --upgrade pip

# Install dependencies if requirements.txt exists
if [ -f "$REQUIREMENTS" ]; then
    info "Installing dependencies from requirements.txt..."
    pip install --quiet -r "$REQUIREMENTS"
else
    warn "requirements.txt not found. Installing dependencies manually..."
    pip install --quiet duckdb tabulate
fi

# Run the Python script with all passed arguments
info "Running query script..."
echo ""

python3 "$PYTHON_SCRIPT" "$@"

# Deactivate virtual environment
deactivate
