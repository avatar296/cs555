#!/bin/bash
#
# Format all Java code using Spotless
#

echo "=========================================="
echo "Formatting Java Code with Spotless"
echo "=========================================="

# Check for violations first
echo "Checking for formatting violations..."
./gradlew spotlessCheck

CHECK_RESULT=$?

if [ $CHECK_RESULT -eq 0 ]; then
    echo ""
    echo "✓ No formatting violations found!"
    echo "  All code is properly formatted."
else
    echo ""
    echo "⚠ Formatting violations found."
    echo "  Applying automatic fixes..."
    echo ""

    # Apply formatting fixes
    ./gradlew spotlessApply

    APPLY_RESULT=$?

    if [ $APPLY_RESULT -eq 0 ]; then
        echo ""
        echo "✓ Code formatting complete!"
        echo "  All files have been formatted according to the style guide."
    else
        echo ""
        echo "✗ Error applying formatting fixes."
        echo "  Please check the error messages above."
        exit 1
    fi
fi

echo ""
echo "=========================================="
echo "Spotless Commands:"
echo "  ./gradlew spotlessCheck  - Check formatting"
echo "  ./gradlew spotlessApply  - Apply formatting"
echo "=========================================="
