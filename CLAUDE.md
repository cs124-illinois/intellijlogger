# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This is a Kotlin-based project that creates an IntelliJ IDEA plugin for logging student activity in CS 124 @ Illinois. The project consists of two main components:
- **Plugin** (`plugin/`): IntelliJ IDEA plugin that captures user activity (typing, button clicks, test execution)
- **Server** (`server/`): Ktor-based HTTP server that receives and stores activity logs in MongoDB

## Architecture
The project uses date-based versioning (2025.7.0) and is structured as a multi-module Gradle project:

### Plugin Module (`plugin/`)
- **ApplicationService.kt**: Core service managing persistent state and project counters
- **TypingHandlers.kt**: Captures keyboard events (typing, backspace, enter)
- **ButtonAction.kt**: Handles custom grading button action (Shift+Ctrl+G)
- **TestStatusHandler.kt**: Monitors test execution status
- **StartupActivity.kt**: Initializes plugin on IDE startup
- **Counters.kt**: Data structures for tracking activity metrics

### Server Module (`server/`)
- **Main.kt**: Ktor server with MongoDB integration for receiving and storing logs
- REST API endpoints for status checking and log upload
- Support for GZIP compression and CORS
- Docker containerization support

## Common Development Commands

### Build and Development
```bash
# Build entire project
./gradlew build

# Build specific modules
./gradlew plugin:build
./gradlew server:build

# Run the plugin in IntelliJ sandbox
./gradlew plugin:runIde

# Run the server
./gradlew server:run
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific modules
./gradlew plugin:test
./gradlew server:test
```

### Code Quality
```bash
# Lint Kotlin code
./gradlew lintKotlin

# Format Kotlin code
./gradlew formatKotlin

# Run detekt static analysis
./gradlew detekt

# Check all quality gates
./gradlew check
```

### Plugin-Specific Tasks
```bash
# Build plugin distribution
./gradlew plugin:buildPlugin

# Verify plugin structure
./gradlew plugin:verifyPlugin

# Create plugin sandbox
./gradlew plugin:prepareSandbox
```

### Server-Specific Tasks
```bash
# Create fat JAR
./gradlew server:shadowJar

# Build Docker image
./gradlew server:dockerBuild

# Push Docker image
./gradlew server:dockerPush
```

## Development Environment
- **Java**: JDK 17 (configured in toolchain)
- **Kotlin**: 2.1.21
- **IntelliJ Platform**: 2023.1 (majorIntelliJVersion = "231")
- **Build Tool**: Gradle with Kotlin DSL
- **Database**: MongoDB (for server logs)

## Key Dependencies
- **Plugin**: IntelliJ Platform SDK, Kotlinx Serialization, SnakeYAML
- **Server**: Ktor (HTTP server), MongoDB driver, Gson, Logback

## Docker Support
The server can be containerized using the provided Dockerfile and docker-compose.yml. The compose setup includes MongoDB and configures the logging server to connect to it.

## Configuration
- **Detekt**: Static analysis configuration in `config/detekt/detekt.yml`
- **Plugin**: IntelliJ plugin configuration in `plugin/src/main/resources/META-INF/plugin.xml`
- **Server**: Environment-based configuration using Konf library

## Gradle Check Task
- Use the Gradle check task to validate changes in this repository.