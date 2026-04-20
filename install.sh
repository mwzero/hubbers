#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_BINARY="$SCRIPT_DIR/hubbers-distribution/target/hubbers"
JAR_FILE="$SCRIPT_DIR/hubbers-distribution/target/hubbers.jar"
INSTALL_DIR="/usr/local/bin"
INSTALL_PATH="$INSTALL_DIR/hubbers"

# Determine what to install
if [ -f "$NATIVE_BINARY" ]; then
    BINARY_PATH="$NATIVE_BINARY"
    BINARY_TYPE="native"
elif [ -f "$JAR_FILE" ]; then
    BINARY_PATH="$JAR_FILE"
    BINARY_TYPE="jar"
else
    echo "ERROR: No executable found!"
    echo ""
    echo "Please build first:"
    echo "  For native: ./build-native.sh"
    echo "  For JAR:    mvn clean package"
    echo ""
    exit 1
fi

# Mode: dev (symlink) or system (copy)
MODE="system"
if [ "$1" = "--dev" ]; then
    MODE="dev"
fi

echo "========================================="
echo "Hubbers Installation Script"
echo "========================================="
echo ""
echo "Binary type: $BINARY_TYPE"
echo "Mode: $MODE"
echo ""

# Check if binary exists (redundant but keep for clarity)
if [ ! -f "$BINARY_PATH" ]; then
    echo "ERROR: Executable not found at $BINARY_PATH"
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
if [ "$MODE" = "dev" ] && [ "$BINARY_TYPE" = "native" ]; then
    echo "Creating symlink: $INSTALL_PATH -> $BINARY_PATH"
    $SUDO rm -f "$INSTALL_PATH"
    $SUDO ln -s "$BINARY_PATH" "$INSTALL_PATH"
elif [ "$BINARY_TYPE" = "jar" ]; then
    echo "Installing JAR with wrapper script..."
    $SUDO mkdir -p "$INSTALL_DIR"
    if [ "$MODE" = "dev" ]; then
        HUBBERS_JAR_PATH="$BINARY_PATH"
    else
        HUBBERS_JAR_PATH="$INSTALL_DIR/hubbers.jar"
        $SUDO cp "$BINARY_PATH" "$HUBBERS_JAR_PATH"
    fi
    TEMP_WRAPPER="$(mktemp)"
    printf '#!/bin/bash\nexec java -jar "%s" "$@"\n' "$HUBBERS_JAR_PATH" > "$TEMP_WRAPPER"
    $SUDO mv "$TEMP_WRAPPER" "$INSTALL_PATH"
    $SUDO chmod +x "$INSTALL_PATH"
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
