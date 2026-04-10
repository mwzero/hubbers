# Build Guide

This document describes how to build and package the Hubbers multi-module project.

## Project Structure

Hubbers is organized as a Maven multi-module project:

```
hubbers/
├── pom.xml                      # Parent aggregator POM
├── hubbers-framework/           # Core Java framework
│   ├── pom.xml
│   └── src/
│       ├── main/java/           # Java source code
│       ├── main/resources/      # Resources (web/, META-INF/)
│       └── test/java/           # Unit tests
├── hubbers-ui/                  # React web UI
│   ├── pom.xml
│   ├── package.json
│   ├── vite.config.ts
│   └── src/                     # React components
├── hubbers-repo/                # Default repository of artifacts
│   ├── pom.xml
│   └── src/main/resources/repo/
│       ├── agents/              # Agent manifests
│       ├── tools/               # Tool manifests
│       ├── pipelines/           # Pipeline manifests
│       ├── skills/              # Skill manifests
│       ├── application.yaml     # Configuration
│       └── logback.xml          # Logging config
└── hubbers-distribution/        # Fat JAR assembly
    ├── pom.xml
    └── target/hubbers.jar       # Executable JAR
```

## Prerequisites

### For Standard Build (JAR)

- **Java 21** or later
- **Maven 3.8+**
- **Node.js 20+** and **npm 10+** (for UI build)

### For Native Build

- All of the above, plus:
- **GraalVM 21** with `native-image` component
- **Visual Studio Build Tools** (Windows only)

## Build Commands

### Full Build (All Modules)

Build everything from the project root:

```bash
# Clean build with tests
mvn clean install

# Skip tests for faster build
mvn clean install -DskipTests

# Verbose output
mvn clean install -X
```

**Build order** (automatic):
1. `hubbers-ui` - React app compiled with Vite → outputs to `dist/`
2. `hubbers-framework` - Java core compiled, UI copied from `../hubbers-ui/dist/` → `src/main/resources/web/`
3. `hubbers-repo` - Repository artifacts packaged
4. `hubbers-distribution` - Fat JAR assembled with all dependencies

### Quick Build (Skip UI)

When UI hasn't changed, skip the UI module for faster builds:

```bash
# Linux/macOS
./build-quick.sh

# Windows
build-quick.bat

# Or manually
mvn clean package -pl hubbers-framework,hubbers-distribution -am -DskipTests
```

This compiles only:
- `hubbers-framework` (with existing UI assets)
- `hubbers-distribution` (fat JAR)

**Use case:** Daily development when only Java code changes.

### Build Individual Modules

```bash
# Build only framework (requires UI already built)
cd hubbers-framework
mvn clean package

# Build only UI
cd hubbers-ui
mvn clean compile

# Build only repo
cd hubbers-repo
mvn clean package

# Build only distribution (requires framework and repo)
cd hubbers-distribution
mvn clean package
```

### Native Executable Build

Build GraalVM native image (framework only):

```bash
# Linux/macOS
./build-native.sh

# Windows
build-native.bat

# Or manually
cd hubbers-framework
mvn clean package -Pnative
```

**Output**: `hubbers-framework/target/hubbers` (or `hubbers.exe` on Windows)

**Note**: Native build takes 5-15 minutes and requires ~8GB RAM.

## Build Profiles

### Native Profile

Activated with `-Pnative`:

- Skips tests
- Uses `native-maven-plugin` to compile to native executable
- Only available in `hubbers-framework` module
- Requires GraalVM with `native-image` installed

```bash
cd hubbers-framework
mvn package -Pnative
```

## Output Artifacts

| Artifact | Location | Description |
|----------|----------|-------------|
| **Framework JAR** | `hubbers-framework/target/hubbers-framework-0.1.0-SNAPSHOT.jar` | Core library (not executable) |
| **UI Assets** | `hubbers-framework/src/main/resources/web/` | Compiled React app |
| **Repo JAR** | `hubbers-repo/target/hubbers-repo-0.1.0-SNAPSHOT.jar` | Repository artifacts as JAR |
| **Distribution JAR** | `hubbers-distribution/target/hubbers.jar` | Executable fat JAR (framework + repo + deps) |
| **Native Executable** | `hubbers-framework/target/hubbers[.exe]` | GraalVM native binary |

## Running the Application

### From Distribution JAR

```bash
java -jar hubbers-distribution/target/hubbers.jar --help
java -jar hubbers-distribution/target/hubbers.jar list agents
java -jar hubbers-distribution/target/hubbers.jar web --port 7070
```

### From Native Executable

```bash
./hubbers-framework/target/hubbers --help
./hubbers-framework/target/hubbers list tools
```

### Install Globally

```bash
# Linux/macOS
sudo ./install.sh

# Windows (as Administrator)
install.bat

# Then use anywhere:
hubbers --version
hubbers list agents
```

## Development Workflow

### Standard Java Development

1. Make changes in `hubbers-framework/src/main/java/`
2. Quick build: `./build-quick.sh` (or `.bat` on Windows)
3. Test: `java -jar hubbers-distribution/target/hubbers.jar`

**Time saved:** ~2-4 minutes (no npm install/build)

### UI Development

**Current: Always bundle mode**

1. Make changes in `hubbers-ui/src/`
2. Build UI: `cd hubbers-ui && mvn compile`
3. UI automatically copied to `hubbers-framework/src/main/resources/web/`
4. Build framework: `cd ../hubbers-framework && mvn package`
5. Test: `java -jar ../hubbers-distribution/target/hubbers.jar web`

**Or full rebuild:**
```bash
mvn clean install -DskipTests
```

**Future: Dev mode (not yet implemented)**

Option to run Vite dev server with hot reload:
```bash
cd hubbers-ui
npm run dev  # Starts on http://localhost:3000, proxies API to :7070
```

### Repository Development

1. Edit manifests in `hubbers-repo/src/main/resources/repo/`
2. Build: `cd hubbers-repo && mvn package`
3. Rebuild distribution: `cd ../hubbers-distribution && mvn package`

Or use external repo:
```bash
hubbers --repo /path/to/hubbers-repo/src/main/resources/repo list agents
```

### Common Workflows

| Scenario | Command | Time |
|----------|---------|------|
| **First build** | `mvn clean install` | 3-5 min |
| **Java code change** | `./build-quick.sh` | 30 sec |
| **UI change** | `mvn clean install -DskipTests` | 3-5 min |
| **Repo artifacts change** | `mvn package -pl hubbers-repo,hubbers-distribution -am` | 20 sec |
| **Test single module** | `cd hubbers-framework && mvn test` | 10 sec |

## Troubleshooting

### "Module not found" errors

Run `mvn clean install` from the project root to ensure all modules are built in order.

### UI not appearing in JAR

Ensure build order is correct:
1. `hubbers-ui` builds first (creates `dist/`)
2. `hubbers-framework` copies UI assets during `process-classes` phase

Check: `hubbers-framework/src/main/resources/web/index.html` should exist after build.

### Native build fails

**Common issues:**
- GraalVM not in PATH: Set `JAVA_HOME` to GraalVM directory
- Missing `native-image`: Run `gu install native-image`
- Out of memory: Add `-Xmx8g` to build args or reduce parallelism
- Reflection errors: Update `reflect-config.json` in `hubbers-framework/src/main/resources/META-INF/native-image/`

**Windows-specific:**
- Visual Studio Build Tools required
- Run `build-native.bat` from "x64 Native Tools Command Prompt for VS"

### Tests failing

Skip tests during development:
```bash
mvn clean package -DskipTests
```

Run specific test:
```bash
cd hubbers-framework
mvn test -Dtest=AgenticExecutorTest
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Hubbers
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build with Maven
        run: mvn clean install
      - name: Upload JAR
        uses: actions/upload-artifact@v3
        with:
          name: hubbers-jar
          path: hubbers-distribution/target/hubbers.jar
```

### Docker Build

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=builder /build/hubbers-distribution/target/hubbers.jar /app/hubbers.jar
ENTRYPOINT ["java", "-jar", "/app/hubbers.jar"]
```

## Performance Notes

- **Full build time**: 2-5 minutes (includes npm install + build)
- **Incremental build**: 10-30 seconds (Java only, no UI rebuild)
- **Native build time**: 5-15 minutes
- **JAR size**: ~100-120 MB (fat JAR with all dependencies)
- **Native binary size**: ~50-80 MB

## Module Dependencies

```
hubbers-parent (aggregator)
  ├── hubbers-ui (no deps)
  ├── hubbers-framework (no internal deps)
  ├── hubbers-repo (no deps)
  └── hubbers-distribution
      ├── depends on: hubbers-framework
      └── depends on: hubbers-repo
```

**Build execution order**:
1. UI → Framework (UI assets copied)
2. Repo (standalone)
3. Distribution (assembles framework + repo)

---

For more information:
- [README.md](README.md) - Project overview and usage
- [AGENTS.md](AGENTS.md) - Agent development guide
- [docs/NATIVE_BUILD.md](docs/NATIVE_BUILD.md) - Detailed native build guide
