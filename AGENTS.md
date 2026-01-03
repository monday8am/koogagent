# AGENTS.md

This file provides context and instructions for AI agents working on the KoogAgent project.

## Project Overview
KoogAgent is an Android prototype for on-device agentic notifications using LiteRT-LM and the Koog framework.

## Commands

### Build
- Build all: `./gradlew build`
- Build app: `./gradlew :app:build`
- Clean: `./gradlew clean`

### Test
- Run all tests: `./gradlew test`
- Agent tests: `./gradlew :agent:test`
- Presentation tests: `./gradlew :presentation:test`
- App unit tests: `./gradlew :app:testDebugUnitTest`

### Development
- Assemble debug APK: `./gradlew :app:assembleDebug`

## Coding Standards
- Kotlin-first, using Jetpack Compose for UI.
- MVI pattern in the presentation layer.
- Strictly separate platform-specific code in `:app` from business logic in `:agent` and `:presentation`.
