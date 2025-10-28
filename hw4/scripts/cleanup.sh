#!/bin/bash
#
# Cleanup script for the Distributed File System project
# Removes build artifacts, test data, and chunk server storage
#

echo "=========================================="
echo "Cleaning Distributed File System Project"
echo "=========================================="

# Clean Gradle build artifacts
echo "Cleaning Gradle build artifacts..."
./gradlew clean

# Remove chunk server storage directory
if [ -d "/tmp/chunk-server" ]; then
    echo "Removing chunk server storage (/tmp/chunk-server)..."
    rm -rf /tmp/chunk-server
    echo "  ✓ Removed /tmp/chunk-server"
else
    echo "  ✓ No chunk server storage found"
fi

# Remove any test data directories
if [ -d "test-data" ]; then
    echo "Removing test data..."
    rm -rf test-data
    echo "  ✓ Removed test-data/"
else
    echo "  ✓ No test data found"
fi

# Remove log files
if ls *.log 1> /dev/null 2>&1; then
    echo "Removing log files..."
    rm -f *.log
    echo "  ✓ Removed log files"
else
    echo "  ✓ No log files found"
fi

# Remove temporary files
if ls *.tmp 1> /dev/null 2>&1; then
    echo "Removing temporary files..."
    rm -f *.tmp
    echo "  ✓ Removed temporary files"
else
    echo "  ✓ No temporary files found"
fi

# Clean IDE files (optional)
read -p "Remove IDE files (.idea, *.iml, etc.)? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Removing IDE files..."
    rm -rf .idea
    rm -f *.iml
    rm -rf .vscode
    rm -rf .settings
    rm -f .classpath .project
    echo "  ✓ Removed IDE files"
fi

echo ""
echo "=========================================="
echo "Cleanup complete!"
echo "=========================================="
