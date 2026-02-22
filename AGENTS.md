# AGENTS.md

This file provides context and instructions for AI agents working on the KoogAgent project.

## Project Overview
KoogAgent is an Android prototype for on-device agentic notifications using LiteRT-LM and the Koog framework.

## Commands

### Build
- Build all: `./gradlew build`
- Build core: `./gradlew :core:build`
- Build edgelab app: `./gradlew :app:edgelab:build`
- Build copilot app: `./gradlew :app:copilot:build`
- Clean: `./gradlew clean`

### Test
- Run all tests: `./gradlew test`
- Agent tests: `./gradlew :agent:test`
- Presentation tests: `./gradlew :presentation:test`
- Core tests: `./gradlew :core:testDebugUnitTest`

### Development
- Assemble debug APKs: `./gradlew :app:edgelab:assembleDebug :app:copilot:assembleDebug`
- Install edgelab: `./gradlew :app:edgelab:installDebug`
- Install copilot: `./gradlew :app:copilot:installDebug`

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
- **data**: Pure Kotlin - Model catalog, test definitions, repositories with remote loading and caching
- **agent**: Pure Kotlin - Core inference interfaces, tool handlers, tool definitions
- **presentation**: Pure Kotlin - ViewModels, test engine, UI state management
- **core**: Android library - Shared infrastructure (inference, download, OAuth, storage implementations)
- **app:edgelab**: Android app - Edge Agent Lab for model testing and validation
- **app:copilot**: Android app - Cycling Copilot (demo app for on-device AI cycling assistance)

Module dependencies: `data` ← `agent` ← `presentation` ← `core` ← `app:edgelab`, `app:copilot`

### Cycling Copilot Architecture
The Cycling Copilot app is a demo application showcasing on-device AI for cycling assistance. Complete UI architecture and implementation details are documented in `COPILOT_UI_ARCHITECTURE.md`.

**Key Features**:
- Three-screen flow: Onboard & Download → Ride Setup → Live Ride
- MapLibre integration for real-time route visualization
- Voice-first interaction with AI copilot
- GPX simulation with variable playback speed
- Offline-capable AI inference (FunctionGemma 2B + Gemma 2 550M)
- Graceful degradation when remote APIs unavailable

**Reference Documents**:
- UI Architecture: `COPILOT_UI_ARCHITECTURE.md` - Complete screen layouts, state management, and component specs
- AI Architecture: See `:agent` module tool definitions and inference interfaces

## Recent Changes

### Added Features

#### Multi-Module Architecture Refactoring
- **Overview**: Split monolithic `:app` module into shared `:core` infrastructure and multiple independent app modules
- **PR**: #65 - Refactor to multi-module architecture with shared core
- **Architecture Pattern**: Shared infrastructure layer with multiple app frontends

##### :core Module (New Android Library)
- **Location**: `core/src/main/java/com/monday8am/koogagent/core/`
- **Purpose**: Shared Android-specific infrastructure for all apps
- **Package**: `com.monday8am.koogagent.core`
- **Components**:
  - `core/inference/LiteRTLmInferenceEngineImpl.kt` - LiteRT-LM inference engine implementation
  - `core/download/ModelDownloadManagerImpl.kt` - Model download manager with WorkManager
  - `core/download/DownloadUnzipWorker.kt` - Background download worker
  - `core/oauth/HuggingFaceOAuthManager.kt` - Multi-app OAuth manager (configurable redirect schemes)
  - `core/oauth/HuggingFaceOAuthConfig.kt` - OAuth configuration
  - `core/storage/AuthRepositoryImpl.kt` - Auth repository with DataStore + Tink encryption
  - `core/storage/DataStoreModelDataSource.kt` - Model catalog local storage
  - `core/storage/DataStoreTestDataSource.kt` - Test definitions local storage
  - `core/di/CoreDependencies.kt` - Factory object for dependency injection
- **Key Dependencies**: LiteRT-LM Android, WorkManager, AppAuth, DataStore, Security Crypto, Tink
- **Exposure Pattern**: Uses `api` dependencies to expose `:data`, `:agent`, and `:presentation` transitively

##### :app:edgelab Module (New Android App)
- **Location**: `app/edgelab/src/main/java/com/monday8am/koogagent/edgelab/`
- **Purpose**: Edge Agent Lab - model testing and validation platform
- **Package**: `com.monday8am.koogagent.edgelab`
- **Application ID**: `com.monday8am.koogagent.edgelab`
- **OAuth Redirect**: `koogagent://oauth/callback`
- **Features**:
  - Model selection and download
  - Test suite execution and validation
  - Token speed metrics
  - Test result visualization
  - HuggingFace OAuth integration
- **UI Structure**:
  - `ui/screens/modelselector/` - Model selection and management
  - `ui/screens/testing/` - Test execution UI
  - `ui/screens/testdetails/` - Test catalog and details
  - `ui/navigation/` - Navigation graph
  - `ui/theme/` - Material3 theme (original app theme)
- **DI**: `Dependencies.kt` - Uses `CoreDependencies` factories for infrastructure

##### :app:copilot Module (New Android App)
- **Location**: `app/copilot/src/main/java/com/monday8am/koogagent/copilot/`
- **Purpose**: Cycling Copilot - minimal app ready for cycling-specific features
- **Package**: `com.monday8am.koogagent.copilot`
- **Application ID**: `com.monday8am.koogagent.copilot`
- **OAuth Redirect**: `copilot://oauth/callback` (placeholder, not actively used)
- **Current State**: Single empty screen with placeholder content
- **UI Structure**:
  - `ui/CyclingScreen.kt` - Main screen (ready for cycling content)
  - `MainActivity.kt` - Entry point
- **Note**: Minimal implementation demonstrates how to create new apps using `:core`

##### OAuth Refactoring for Multi-App Support
- **Problem**: Original OAuth implementation hardcoded redirect URI and activity class
- **Solution**: Made OAuth manager configurable per-app
- **Changes**:
  - `HuggingFaceOAuthConfig.getRedirectUri(appScheme)` - Generates redirect URI based on scheme
  - `HuggingFaceOAuthManager` constructor accepts `redirectScheme` and `activityClass` parameters
  - Each app provides its own scheme ("edgelab", "copilot") and MainActivity reference
- **Example Usage**:
  ```kotlin
  CoreDependencies.createOAuthManager(
      context = appContext,
      clientId = BuildConfig.HF_CLIENT_ID,
      redirectScheme = "edgelab",  // or "copilot"
      activityClass = MainActivity::class.java
  )
  ```
- **Benefit**: Apps can have independent OAuth flows without conflicts

##### Benefits of New Architecture
- **Zero Code Duplication**: All Android infrastructure shared via `:core`
- **Easy App Creation**: New apps just implement UI and provide DI configuration
- **Independent Evolution**: Apps evolve independently with different features and themes
- **Clean Separation**: Platform-agnostic logic (`:data`, `:agent`, `:presentation`) vs Android code (`:core`) vs UI (`:app:*`)
- **Simultaneous Installation**: Different package names allow installing multiple apps
- **Simplified Dependencies**: Apps only need `implementation(project(":core"))`

##### Migration Details
- Moved inference, download, OAuth, and storage implementations from `:app` to `:core`
- Updated all package declarations from `com.monday8am.koogagent.*` to appropriate module packages
- Created `CoreDependencies` factory pattern replacing direct instantiation
- Updated CI/CD pipeline to build and test new modules
- Updated pre-push hooks to target `:app:edgelab:lintDebug`
- Removed old `:app` module after verification

##### Documentation
- Full refactoring details in `REFACTORING_SUMMARY.md`
- Updated build commands in this file
- Updated module structure diagram

#### TestDetails Screen
- **Location**: `app/edgelab/src/main/java/com/monday8am/koogagent/edgelab/ui/screens/testdetails/`
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

#### Cycling Copilot Onboarding
- **PR**: #67 - Add simplified OnboardViewModel for Copilot app
- **Location**: `presentation/src/main/kotlin/com/monday8am/presentation/onboard/`
- **Purpose**: Simplified model download flow for Copilot app with two specific gated models
- **Components**:
  - `OnboardViewModel`: Platform-agnostic ViewModel for two-model download workflow
  - `OnboardViewModelImpl`: Implementation with hardcoded model configurations
  - `OnboardViewModelTest`: Comprehensive test suite (20+ test cases)
- **Architecture**:
  - Simplified from `ModelSelectorViewModel` (no grouping, catalog loading, or multi-model management)
  - Two hardcoded models: User HF Model + Gemma 3 1B (placeholder URLs)
  - Reactive state management with Flow combination (`modelsStatus` + `authToken`)
  - Support for parallel background downloads via `ModelDownloadManager`
  - HuggingFace authentication integration via `AuthRepository`
- **UI**: 3-step wizard in OnboardScreen
  - **Step 1**: Sign in to HuggingFace (prominent button with lock icon, required)
  - **Step 2**: Download AI models (enabled after sign-in, FilledTonalButton style)
  - **Step 3**: Continue to setup (enabled after all downloads complete)
  - Visual states: Step labels (STEP #1, #2, #3), disabled appearance, check icons when complete
  - Smart status messages guide users through required steps
- **OAuth Integration**:
  - Added `HuggingFaceOAuthManager` to Copilot `Dependencies.kt`
  - Updated `MainActivity.kt` to handle OAuth redirect intents (`onNewIntent`)
  - Configured `BuildConfig.HF_CLIENT_ID` in `app/copilot/build.gradle.kts`
  - OAuth redirect scheme: `copilot://oauth/callback`
- **Testing**: Full test coverage for initialization, authentication, downloads, parallel downloads, state derivation
- **Pattern**: Follows edgelab patterns (delegation, stateIn, lifecycle awareness)

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
