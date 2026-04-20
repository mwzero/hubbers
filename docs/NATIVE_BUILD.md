---
title: Native Build
---

# Native Build

Hubbers supports native-image packaging through GraalVM, alongside the standard shaded jar flow.

## Standard Build First

Before attempting native-image, verify the normal build:

```bash
mvn clean package
java -jar hubbers-distribution/target/hubbers.jar --help
```

## Prerequisites

- Java 21
- GraalVM with `native-image`
- platform-native build toolchain
- Visual Studio Build Tools on Windows

Install the native-image component:

```bash
gu install native-image
```

## Build Commands

Linux or macOS:

```bash
./build-native.sh
```

Windows:

```bat
build-native.bat
```

## Verification

After building, verify that the binary can at least start and list artifacts:

```bash
./target/hubbers --help
./target/hubbers list agents
./target/hubbers list tools
```

## Current Caveats

- The web runtime depends on bundled frontend assets and should be tested separately after every UI rebuild.
- If you change reflective manifest parsing or web serialization behavior, review the native-image configs under `hubbers-framework/src/main/resources/META-INF/native-image/`.

## Installation

Development install on Linux or macOS:

```bash
./install.sh --dev
```

System install:

```bash
sudo ./install.sh
```

Windows:

```bat
install.bat
```

## Related Docs

- [Software Architecture](SWA.md)
- [GitHub Pages Publishing Guide](GITHUB_PAGES.md)
