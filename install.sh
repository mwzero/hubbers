#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BINARY_PATH="$SCRIPT_DIR/target/hubbers"
INSTALL_DIR="/usr/local/bin"
INSTALL_PATH="$INSTALL_DIR/hubbers"

# Mode: dev (symlink) or system (copy)
MODE="system"
if [ "$1" = "--dev" ]; then
    MODE="dev"
fi

echo "========================================="
echo "Hubbers Installation Script"
echo "========================================="
echo ""
echo "Mode: $MODE"
echo ""

# Check if binary exists
if [ ! -f "$BINARY_PATH" ]; then
    echo "ERROR: Native executable not found at $BINARY_PATH"
    echo ""
    echo "Please build the native executable first:"
    echo "  ./build-native.sh"
    echo ""
    exit 1
fi

# Check if install directory is writable
if [ ! -w "$INSTALL_DIR" ]; then
    echo "NOTE: $INSTALL_DIR is not writable. Trying with sudo..."
    SUDO="sudo"
else
    SUDO=""
fi

# Install
if [ "$MODE" = "dev" ]; then
    echo "Creating symlink: $INSTALL_PATH -> $BINARY_PATH"
    $SUDO rm -f "$INSTALL_PATH"
    $SUDO ln -s "$BINARY_PATH" "$INSTALL_PATH"
else
    echo "Copying binary to: $INSTALL_PATH"
    $SUDO cp "$BINARY_PATH" "$INSTALL_PATH"
    $SUDO chmod +x "$INSTALL_PATH"
fi

echo ""
echo "========================================="
echo "Installation successful!"
echo "========================================="
echo ""
echo "The 'hubbers' command is now available in your PATH."
echo ""
echo "Try it:"
echo "  hubbers --version"
echo "  hubbers --help"
echo "  hubbers list agents"
echo ""

if [ "$MODE" = "dev" ]; then
    echo "NOTE: Running in dev mode."
    echo "The installed command points to: $BINARY_PATH"
    echo "Rebuild with ./build-native.sh to update."
    echo ""
fi
