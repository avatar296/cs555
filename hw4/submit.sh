#!/bin/bash

# HW4 Submission Script
# Creates a clean tar file for CS555 submission

echo "Creating CS555 HW4 submission tar file..."

# Apply code formatting
echo "Applying code formatting..."
./gradlew spotlessApply

# Clean build and verify
echo "Building and verifying code..."
./gradlew clean build

if [ $? -ne 0 ]; then
    echo "ERROR: Build failed. Fix errors before submitting."
    exit 1
fi

# Create the tar file
echo "Creating tar file..."
./gradlew createTar

TAR_FILE="build/distributions/Christopher_Cowart_HW4.tar"

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
    echo "- Java files: $(tar -tf "$TAR_FILE" | grep '\.java' | wc -l)"
    echo "- Gradle files: $(tar -tf "$TAR_FILE" | grep '\.gradle' | wc -l)"
    echo "- JAR files: $(tar -tf "$TAR_FILE" | grep '\.jar' | wc -l)"
    echo "- Total files: $(tar -tf "$TAR_FILE" | wc -l)"
    echo ""

    # Check for forbidden files
    echo "Checking for forbidden files..."
    FORBIDDEN=$(tar -tf "$TAR_FILE" | grep -E '(\.class$|bin/|\.git/)' || true)
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
