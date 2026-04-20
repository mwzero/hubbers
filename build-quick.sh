#!/bin/bash
# Quick build script - skips UI module

set -e

echo "========================================"
echo "Hubbers Quick Build (Framework + Dist)"
echo "========================================"
echo ""
echo "Skipping UI module (use 'mvn clean install' for full build)"
echo ""

mvn clean package -pl hubbers-framework,hubbers-distribution -am -DskipTests

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
