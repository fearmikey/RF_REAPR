#!/bin/bash

# RF-REAPR Test Suite Runner
# This script runs all local unit tests and instrumented UI tests.

echo "======================================"
echo "    RF-REAPR Test Suite Runner"
echo "======================================"
echo ""

echo "1. Running Local Unit Tests (JUnit)..."
./gradlew testDebugUnitTest

if [ $? -eq 0 ]; then
    echo "✅ Local Unit Tests Passed!"
else
    echo "❌ Local Unit Tests Failed!"
    exit 1
fi
echo ""

echo "2. Running Instrumented Tests (Connected Android Device)..."
# Note: An emulator or physical device must be connected.
./gradlew connectedDebugAndroidTest

if [ $? -eq 0 ]; then
    echo "✅ Instrumented Tests Passed!"
else
    echo "❌ Instrumented Tests Failed! Ensure a device/emulator is connected."
    exit 1
fi
echo ""

echo "3. Running Lint Check..."
./gradlew lintDebug

if [ $? -eq 0 ]; then
    echo "✅ Lint checks passed!"
else
    echo "❌ Lint checks found issues. Check the reports in app/build/reports/lint-results-debug.html."
    exit 1
fi

echo "======================================"
echo "    All Tests Completed Successfully!"
echo "======================================"
