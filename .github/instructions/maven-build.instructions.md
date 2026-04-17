---
description: "Maven build configuration and dependency management for Hubbers. Use when: editing pom.xml files, adding dependencies, configuring plugins, managing multi-module builds, creating build scripts."
applyTo: "**/pom.xml"
---

# Maven Build Standards

## Project Structure

Hubbers is a multi-module Maven project:

```
hubbers/
├── pom.xml                    # Parent POM
├── hubbers-framework/
│   └── pom.xml               # Core framework module
├── hubbers-repo/
│   └── pom.xml               # Artifact repository module
├── hubbers-distribution/
│   └── pom.xml               # CLI distribution module
├── hubbers-ui/
│   └── pom.xml               # React UI module
└── sandbox/
    └── pom.xml               # Experimental module
```

## Parent POM Configuration

### Essential Properties

```xml
<properties>
    <!-- Java Version -->
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    
    <!-- Dependency Versions -->
    <jackson.version>2.15.2</jackson.version>
    <slf4j.version>2.0.9</slf4j.version>
    <logback.version>1.4.11</logback.version>
    <junit.version>5.10.0</junit.version>
    <lombok.version>1.18.30</lombok.version>
</properties>
```

### Module Declaration

```xml
<modules>
    <module>hubbers-framework</module>
    <module>hubbers-repo</module>
    <module>hubbers-distribution</module>
    <module>hubbers-ui</module>
    <module>sandbox</module>
</modules>
```

## Dependency Management

### Core Dependencies (Framework Module)

```xml
<dependencies>
    <!-- JSON/YAML Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    
    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>${slf4j.version}</version>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>${logback.version}</version>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Inter-Module Dependencies

```xml
<!-- In hubbers-distribution/pom.xml -->
<dependencies>
    <dependency>
        <groupId>org.hubbers</groupId>
        <artifactId>hubbers-framework</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.hubbers</groupId>
        <artifactId>hubbers-repo</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

### Adding New Dependencies

```xml
<!-- ✅ Always specify version in parent POM properties -->
<properties>
    <commons-io.version>2.13.0</commons-io.version>
</properties>

<!-- ✅ Use dependencyManagement in parent POM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>${commons-io.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- ✅ Reference without version in child modules -->
<dependencies>
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
    </dependency>
</dependencies>

<!-- ❌ Don't hardcode versions in child POMs -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.13.0</version>  <!-- BAD! -->
</dependency>
```

## Plugin Configuration

### Compiler Plugin

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <encoding>UTF-8</encoding>
                <compilerArgs>
                    <arg>-parameters</arg>  <!-- Preserve parameter names -->
                    <arg>-Xlint:all</arg>   <!-- Enable all warnings -->
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Surefire Plugin (Testing)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.1.2</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
        </includes>
        <excludes>
            <exclude>**/*IT.java</exclude>  <!-- Exclude integration tests -->
        </excludes>
    </configuration>
</plugin>
```

### Failsafe Plugin (Integration Tests)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.1.2</version>
    <configuration>
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Assembly Plugin (Distribution)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <descriptors>
            <descriptor>src/assembly/distribution.xml</descriptor>
        </descriptors>
        <archive>
            <manifest>
                <mainClass>org.hubbers.cli.HubbersCLI</mainClass>
            </manifest>
        </archive>
    </configuration>
    <executions>
        <execution>
            <id>make-assembly</id>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Shade Plugin (Uber JAR)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>org.hubbers.cli.HubbersCLI</mainClass>
                    </transformer>
                </transformers>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                        </excludes>
                    </filter>
                </filters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## GraalVM Native Image Configuration

### Native Image Plugin

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>0.9.27</version>
                    <extensions>true</extensions>
                    <configuration>
                        <imageName>hubbers</imageName>
                        <mainClass>org.hubbers.cli.HubbersCLI</mainClass>
                        <buildArgs>
                            <buildArg>--no-fallback</buildArg>
                            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                            <buildArg>--initialize-at-build-time=org.slf4j</buildArg>
                        </buildArgs>
                    </configuration>
                    <executions>
                        <execution>
                            <id>build-native</id>
                            <goals>
                                <goal>compile-no-fork</goal>
                            </goals>
                            <phase>package</phase>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### Reflection Configuration

Located in `src/main/resources/META-INF/native-image/`:

```json
// reflect-config.json
[
  {
    "name": "org.hubbers.agent.AgentManifest",
    "allDeclaredConstructors": true,
    "allDeclaredFields": true,
    "allDeclaredMethods": true
  }
]
```

## Build Profiles

### Development Profile

```xml
<profile>
    <id>dev</id>
    <properties>
        <skipTests>false</skipTests>
        <maven.test.skip>false</maven.test.skip>
    </properties>
</profile>
```

### Production Profile

```xml
<profile>
    <id>prod</id>
    <properties>
        <skipTests>true</skipTests>
    </properties>
    <build>
        <plugins>
            <!-- Additional optimizations -->
        </plugins>
    </build>
</profile>
```

### Quick Build Profile

```xml
<profile>
    <id>quick</id>
    <properties>
        <skipTests>true</skipTests>
        <maven.javadoc.skip>true</maven.javadoc.skip>
        <maven.source.skip>true</maven.source.skip>
    </properties>
</profile>
```

## Common Build Commands

```bash
# Clean and install all modules
mvn clean install

# Build without tests
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl hubbers-framework

# Build module and dependencies
mvn clean package -pl hubbers-distribution -am

# Run tests only
mvn test

# Run integration tests
mvn verify

# Build native image
mvn clean package -Pnative

# Quick build (skip tests and docs)
mvn clean package -Pquick

# Update dependency versions
mvn versions:display-dependency-updates

# Display effective POM
mvn help:effective-pom

# Dependency tree
mvn dependency:tree

# Analyze dependencies
mvn dependency:analyze
```

## Resource Filtering

### Enable Resource Filtering

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <filtering>true</filtering>
            <includes>
                <include>**/*.properties</include>
                <include>**/*.yaml</include>
            </includes>
        </resource>
        <resource>
            <directory>src/main/resources</directory>
            <filtering>false</filtering>
            <excludes>
                <exclude>**/*.properties</exclude>
                <exclude>**/*.yaml</exclude>
            </excludes>
        </resource>
    </resources>
</build>
```

### Use Properties in Resources

```properties
# application.properties
application.name=${project.name}
application.version=${project.version}
build.timestamp=${maven.build.timestamp}
```

## Dependency Scope Best Practices

```xml
<!-- ✅ Runtime dependencies -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <scope>runtime</scope>  <!-- Only needed at runtime -->
</dependency>

<!-- ✅ Test dependencies -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>  <!-- Only for testing -->
</dependency>

<!-- ✅ Provided dependencies (compile-time only) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>  <!-- Not needed in final JAR -->
</dependency>

<!-- ✅ Optional dependencies -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>optional-feature</artifactId>
    <optional>true</optional>  <!-- Not transitive -->
</dependency>
```

## Excluding Transitive Dependencies

```xml
<!-- ✅ Exclude unwanted transitive dependencies -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>some-library</artifactId>
    <version>1.0.0</version>
    <exclusions>
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## Version Management

### Use Properties for Version Alignment

```xml
<!-- ✅ Group related dependencies with common version -->
<properties>
    <jackson.version>2.15.2</jackson.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-core</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
        <version>${jackson.version}</version>
    </dependency>
</dependencies>
```

## Build Reproducibility

### Lock Down Plugin Versions

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.1.2</version>
            </plugin>
            <!-- Lock all plugin versions -->
        </plugins>
    </pluginManagement>
</build>
```

## Common Issues and Solutions

### Issue: Dependency Conflicts

```bash
# Identify conflicts
mvn dependency:tree -Dverbose

# Solution: Exclude conflicting transitive dependency
<exclusions>
    <exclusion>
        <groupId>conflicting-group</groupId>
        <artifactId>conflicting-artifact</artifactId>
    </exclusion>
</exclusions>
```

### Issue: ClassNotFoundException at Runtime

```xml
<!-- Solution: Check scope - might need 'compile' instead of 'provided' -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>needed-at-runtime</artifactId>
    <scope>compile</scope>  <!-- Not 'provided' -->
</dependency>
```

### Issue: Slow Builds

```bash
# Solution: Use parallel builds
mvn clean install -T 4  # Use 4 threads

# Or use all available cores
mvn clean install -T 1C  # 1 thread per core
```

### Issue: Tests Failing Only in Maven

```xml
<!-- Solution: Configure test resources properly -->
<build>
    <testResources>
        <testResource>
            <directory>src/test/resources</directory>
            <filtering>false</filtering>
        </testResource>
    </testResources>
</build>
```

## Module Dependencies Best Practices

### DO:
- ✅ Keep framework module independent
- ✅ Repo module depends only on framework
- ✅ Distribution module depends on both framework and repo
- ✅ Use `<dependencyManagement>` in parent POM
- ✅ Lock plugin versions
- ✅ Group dependencies by purpose (logging, testing, etc.)

### DON'T:
- ❌ Create circular dependencies between modules
- ❌ Hardcode versions in child modules
- ❌ Mix compile and test dependencies without proper scope
- ❌ Forget to exclude transitive dependencies causing conflicts
- ❌ Use SNAPSHOT versions in production

## Artifact Publishing

### Maven Central Requirements

```xml
<distributionManagement>
    <repository>
        <id>ossrh</id>
        <url>https://oss.sonatype.org/service/local/staging/deploy/maven2/</url>
    </repository>
</distributionManagement>

<build>
    <plugins>
        <!-- Sources -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-source-plugin</artifactId>
            <version>3.3.0</version>
            <executions>
                <execution>
                    <id>attach-sources</id>
                    <goals>
                        <goal>jar-no-fork</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        
        <!-- JavaDoc -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-javadoc-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <id>attach-javadocs</id>
                    <goals>
                        <goal>jar</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        
        <!-- GPG Signing -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-gpg-plugin</artifactId>
            <version>3.1.0</version>
            <executions>
                <execution>
                    <id>sign-artifacts</id>
                    <phase>verify</phase>
                    <goals>
                        <goal>sign</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Build Checklist

Before committing POM changes:
- [ ] All plugin versions specified
- [ ] Dependency versions in properties
- [ ] No SNAPSHOT dependencies for releases
- [ ] Proper scopes for all dependencies
- [ ] `mvn clean install` succeeds
- [ ] `mvn dependency:analyze` shows no issues
- [ ] Tests pass with `mvn test`
- [ ] Native build works (if applicable): `mvn package -Pnative`
- [ ] Documentation updated if adding new dependencies
