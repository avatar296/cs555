#!/bin/bash

# HW2 Submission Script
# Creates a clean tar file for CS555 submission

echo "Creating CS555 HW2 submission tar file..."

# Clean build artifacts
echo "Cleaning build artifacts..."
./gradlew clean

# Create the tar file
echo "Creating tar file..."
./gradlew createTar

TAR_FILE="build/distributions/Christopher_Cowart_HW2.tar"

# Check if tar was created successfully
if [ -f "$TAR_FILE" ]; then
    echo ""
    echo "Tar file created successfully: $TAR_FILE"
    echo ""
    echo "Verifying contents..."
    echo "===================="
    tar -tf "$TAR_FILE" | head -20
    echo ""

    # Count files by type
    echo "File summary:"
    echo "- Java files: $(tar -tf "$TAR_FILE" | grep '\.java$' | wc -l)"
    echo "- Gradle files: $(tar -tf "$TAR_FILE" | grep '\.gradle$' | wc -l)"
    echo ""

    # Check for forbidden files
    echo "Checking for forbidden files..."
    FORBIDDEN=$(tar -tf "$TAR_FILE" | grep -E '(\.jar$|bin/hw2$|bin/hw2\.bat$|lib/)' || true)
    if [ -n "$FORBIDDEN" ]; then
        echo "WARNING: Found forbidden files in tar:"
        echo "$FORBIDDEN"
    else
        echo "No forbidden files found - submission is clean!"
    fi

    echo ""
    echo "Tar file size: $(ls -lh "$TAR_FILE" | awk '{print $5}')"
    echo ""
    echo "To submit, use: $TAR_FILE"
else
    echo "ERROR: Failed to create tar file"
    exit 1
fi