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

## Testing Framework

### OpenAPI Tool System
The testing framework uses OpenAPI-based tools for LLM tool calling tests:

- **Tool definitions**: Inline in each test using OpenAPI JSON format
- **Tool execution**: Mock responses only (defined per test)
- **Architecture**: `ToolHandler` interface with `OpenApiToolHandler` implementation
- **Factory pattern**: `ToolHandlerFactory` creates handlers without exposing implementation details

### Test Structure
Each test case contains:
- Single query per test (simplified from previous multi-query design)
- Inline tool definitions in OpenAPI format
- Mock tool responses
- Validation rules

Test files:
- Test definitions: `data/src/main/resources/com/monday8am/koogagent/data/testing/tool_tests.json`
- Test engine: `presentation/src/main/kotlin/com/monday8am/presentation/testing/ToolCallingTestEngine.kt`
- Tool handlers: `agent/src/main/java/com/monday8am/agent/tools/`

### Key Components
- **ToolHandler**: Interface for tool handlers that track calls
- **OpenApiToolHandler**: Extends LiteRT-LM's `OpenApiTool`, returns mock responses, tracks calls
- **ToolHandlerFactory**: Creates tool handlers without exposing `OpenApiTool` dependency
- **TestCase**: Single query with validator function and tool definitions
- **TestRuleValidator**: Converts JSON test definitions to executable test cases

### Per-Test Tool Configuration
Each test creates its own tool handlers and resets the conversation:
1. Tool handlers created from test's OpenAPI definitions
2. `setToolsAndResetConversation()` called with test-specific tools
3. Query executed with those tools
4. Tool calls validated against test rules
5. Next test repeats the process with different tools

This design eliminates global state and enables full test isolation.

## Module Structure
- **agent**: Core inference interfaces, tool handlers, tool definitions
- **data**: Model catalog, test definitions, repositories
- **presentation**: ViewModels, test engine, UI state management
- **app**: Android app, inference implementation, dependency injection

Module dependencies: `agent` → `presentation` → `app` (never circular)

## Recent Changes

### Removed Features
- **Notification screens**: All notification-related UI screens have been removed from the project. The app now focuses on model selection, chat, and testing functionality.

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
