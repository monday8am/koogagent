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
- Test definitions: Loaded remotely with local caching (via `RemoteTestRepository`)
  - Bundled fallback: `data/src/main/resources/com/monday8am/koogagent/data/testing/tool_tests.json`
  - Remote source: GitHub (configurable in `Dependencies.kt`)
- Test engine: `presentation/src/main/kotlin/com/monday8am/presentation/testing/ToolCallingTestEngine.kt`
- Test repository: `data/src/main/java/com/monday8am/koogagent/data/testing/TestRepository.kt`
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
- **data**: Model catalog, test definitions, repositories with remote loading and caching
- **presentation**: ViewModels, test engine, UI state management
- **app**: Android app, inference implementation, dependency injection, DataStore implementations

Module dependencies: `agent` → `presentation` → `app` (never circular)

## Recent Changes

### Added Features

#### TestDetails Screen
- **Location**: `app/src/main/java/com/monday8am/koogagent/ui/screens/testdetails/`
- **Purpose**: Display all available tests in a list format with domain filtering
- **Architecture**: Platform-agnostic ViewModel (`TestDetailsViewModelImpl`) + Android wrapper (`AndroidTestDetailsViewModel`)
- **Navigation**: Type-safe route `Route.TestDetails` accessible from TestScreen via icon button in filter row
- **UI Components**:
  - `TestDetailsScreen`: Main composable with header, filter chips, and test list
  - `TestDetailsCard`: Individual test cards showing name, description, domain, and metadata
  - Domain-specific colors (GENERIC: surfaceVariant, YAZIO: secondaryContainer)
- **Data Flow**: `TestRepository` (remote with caching) → ViewModel → StateFlow → UI

#### Token Speed Metrics
- **Location**: `presentation/src/main/kotlin/com/monday8am/presentation/testing/`
- **Purpose**: Display real-time and average token generation speeds during test execution
- **Implementation**:
  - Added fields to `TestStatus`: `currentTokensPerSecond`, `averageTokensPerSecond`, `totalTokens`
  - Separate tracking for thinking vs content generation time in `TagProcessor`
  - Time tracking: `accumulatedContentTime` and `accumulatedThinkingTime` with pause/resume logic
  - Speed calculation: O(1) complexity by moving timing to parser instead of frame iteration
- **Display**: Shows appropriate speed based on current state (thinking speed during thinking, content speed during generation)
- **Performance**: Optimized from O(n) frame iteration to O(1) by embedding elapsed time in frames

#### Local Model Caching
- **Location**: `data/src/main/java/com/monday8am/koogagent/data/`
- **Purpose**: Cache HuggingFace model catalog locally to improve app startup and offline experience
- **Components**:
  - `LocalModelDataSource`: Interface for local storage abstraction
  - `DataStoreModelDataSource`: Implementation using DataStore with JSON serialization
  - `HuggingFaceModelCatalogProvider`: Refactored to use cache-first pattern with Flow API
  - `ModelRepositoryImpl`: Uses internal scope with fire-and-forget pattern for background refresh
- **Pattern**: Stale-while-revalidate
  1. Emit cached data immediately if available
  2. Fetch fresh data from network in background
  3. Deduplicate: only emit if network data differs from cache
  4. Save network data to cache for next launch
- **Optimizations**:
  - Deduplication prevents unnecessary UI updates when data hasn't changed
  - Fire-and-forget refresh: `refreshModels()` launches background collection without blocking callers
  - Better empty result handling to avoid overwriting valid cache
  - Enhanced logging for debugging (cache hits, updates, deduplication events)

#### Remote Test Loading
- **Location**: `data/src/main/java/com/monday8am/koogagent/data/testing/`
- **Purpose**: Load test definitions from remote URL with local caching, enabling test updates without app releases
- **Components**:
  - `LocalTestDataSource`: Interface for test storage abstraction
  - `DataStoreTestDataSource`: Implementation using DataStore with JSON serialization (reuses app DataStore)
  - `RemoteTestRepository`: Fetches tests from GitHub via OkHttp with cache-first pattern
  - `FallbackTestRepository`: Wraps primary repository with graceful fallback to bundled tests
- **Pattern**: Cache-first with fallback (same as model catalog)
  1. Emit cached tests immediately if available
  2. Fetch fresh tests from network in background
  3. Deduplicate: only emit if network tests differ from cache
  4. Save network tests to cache for next launch
  5. Fallback to bundled `AssetsTestRepository` on network failure
- **Configuration**:
  - Remote URL: `https://raw.githubusercontent.com/monday8am/koogagent/main/data/src/main/resources/.../tool_tests.json`
  - Configurable in `Dependencies.kt` (supports different URLs per environment)
- **Benefits**:
  - Tests can be updated without releasing new APK
  - Offline support: cached tests work without network
  - Graceful degradation: always falls back to bundled tests
  - Consistent architecture: mirrors model catalog caching system
- **Safety**: Continuation guards prevent crashes when requests are cancelled mid-flight

### Removed Features
- **Notification screens**: All notification-related UI screens have been removed from the project. The app now focuses on model selection, chat, and testing functionality.
- **GPU detection from README**: Removed README parsing for GPU support detection. Hardware acceleration is now determined at runtime.

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
