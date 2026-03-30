#!/bin/bash

set -e

echo "========================================="
echo "Hubbers Native Build Script"
echo "========================================="
echo ""

# Check if GraalVM is installed
if ! command -v native-image &> /dev/null; then
    echo "ERROR: GraalVM native-image not found!"
    echo ""
    echo "Please install GraalVM and ensure 'native-image' is in your PATH."
    echo ""
    echo "Installation instructions:"
    echo "  1. Download GraalVM from: https://www.graalvm.org/downloads/"
    echo "  2. Set JAVA_HOME to GraalVM directory"
    echo "  3. Run: gu install native-image"
    echo ""
    exit 1
fi

# Display GraalVM version
echo "Using GraalVM:"
java -version
echo ""

# Clean and build with native profile
echo "Building native executable..."
echo ""
mvn -Pnative clean package

# Check if build was successful
if [ -f "target/hubbers" ]; then
    echo ""
    echo "========================================="
    echo "Build successful!"
    echo "========================================="
    echo ""
    echo "Native executable: target/hubbers"
    echo "Size: $(du -h target/hubbers | cut -f1)"
    echo ""
    echo "To test the executable:"
    echo "  ./target/hubbers --help"
    echo ""
    echo "To install globally:"
    echo "  ./install.sh"
    echo ""
else
    echo ""
    echo "ERROR: Build failed. Native executable not found."
    exit 1
fi
