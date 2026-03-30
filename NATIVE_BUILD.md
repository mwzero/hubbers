# Native Build Quick Start

## Prerequisites

1. **Install GraalVM**:
   - Download from: https://www.graalvm.org/downloads/
   - Set `JAVA_HOME` to GraalVM directory
   - Install native-image component:
     ```bash
     gu install native-image
     ```

2. **Windows Only**: Install Visual Studio Build Tools
   - Required for native compilation on Windows

## Build Native Executable

### Linux/macOS

```bash
# Make scripts executable (first time only)
chmod +x build-native.sh install.sh

# Build
./build-native.sh

# Test
./target/hubbers --help
./target/hubbers list agents
```

### Windows

```cmd
REM Build (run in Command Prompt)
build-native.bat

REM Test
target\hubbers.exe --help
target\hubbers.exe list agents
```

## Install Globally

### Linux/macOS

**Option 1: Development Mode** (recommended for development)
- Creates a symlink to target/hubbers
- Rebuilding automatically updates the installed command

```bash
./install.sh --dev
```

**Option 2: System Installation**
- Copies the binary to /usr/local/bin
- Requires sudo

```bash
sudo ./install.sh
```

### Windows

**Run as Administrator:**
1. Right-click on `install.bat`
2. Select "Run as administrator"

Or from an elevated Command Prompt:
```cmd
install.bat
```

## Verify Installation

After installation:

```bash
hubbers --version
hubbers --help
hubbers list agents
hubbers list tools
hubbers list pipelines
```

## Build Comparison

| Feature | JAR Build | Native Build |
|---------|-----------|--------------|
| Command | `mvn clean package` | `./build-native.sh` |
| Output | `target/hubbers-0.1.0-SNAPSHOT.jar` | `target/hubbers` |
| Size | ~50-100 MB | ~100-150 MB |
| Startup Time | 1-3 seconds | ~50-100ms |
| Runtime | Requires JVM | Standalone |
| Distribution | Requires Java 21+ | No JVM needed |

## Troubleshooting

### "native-image not found"
- Ensure GraalVM is installed
- Run: `gu install native-image`
- Check that `native-image` is in your PATH

### Build Errors on Windows
- Ensure Visual Studio Build Tools are installed
- Run from "x64 Native Tools Command Prompt for VS"

### Permission Denied (Linux/macOS)
```bash
chmod +x build-native.sh install.sh
```

### Web Command Issues
The `hubbers web` command uses Javalin/Jetty which may require additional reflection configurations for native-image. If you encounter errors:

1. Use the JAR version for the web command:
   ```bash
   java -jar target/hubbers-0.1.0-SNAPSHOT.jar web
   ```

2. Or capture additional reflection configs using the tracing agent:
   ```bash
   java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image \
        -jar target/hubbers-0.1.0-SNAPSHOT.jar web
   # Use the application, then rebuild native
   ```

## Rolling Back to JAR

The JAR build still works independently:

```bash
# Build JAR
mvn clean package

# Run
java -jar target/hubbers-0.1.0-SNAPSHOT.jar --help
```

Both build options are maintained in parallel.
