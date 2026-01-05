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

## Workflow Automation & Permissions
Agents can run commands automatically (without manual approval) if they are defined in workflows with specific annotations.

### Giving Permissions
To grant an agent permission to run a command automatically:
1.  Create or modify a workflow in `.agent/workflows/`.
2.  Add `// turbo` above the command you want to auto-approve.
3.  Add `// turbo-all` at the top of the file to auto-approve ALL commands in that workflow.

### Usage
- Trigger these workflows using slash commands (e.g., `/build`, `/test`).
- Commands run directly in the chat will always require manual approval for safety.
