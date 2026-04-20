#!/bin/bash
# Quick build script - skips UI module

set -e

echo "========================================"
echo "Hubbers Quick Build"
echo "========================================"
echo ""
echo "Skipping frontend rebuild (requires existing hubbers-ui/dist)"
echo ""

mvn clean package -pl hubbers-distribution -am -DskipTests -Dhubbers.ui.skip.frontend=true

echo ""
echo "========================================"
echo "Build complete!"
echo "========================================"
echo ""
echo "JAR location: hubbers-distribution/target/hubbers.jar"
echo ""
echo "Run with:"
echo "  java -jar hubbers-distribution/target/hubbers.jar <command>"
echo ""
