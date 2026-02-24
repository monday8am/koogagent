# Architecture

Technical documentation for the EdgeLab project. Covers module structure, key components, integration patterns, and implementation details for developers and AI agents working on this codebase.

**Important:** This is a prototype built for fast iteration, experimentation, and learning.

---

## Module Structure

```
:data ← :agent ← :presentation ← :core ← :app:edgelab
                                        ← :app:copilot
```

Six modules with a strict unidirectional dependency graph:

| Module | Type | Package |
|--------|------|---------|
| `:data` | Pure Kotlin | `com.monday8am.edgelab.data` |
| `:agent` | Pure Kotlin/JVM | `com.monday8am.edgelab.agent` |
| `:presentation` | Pure Kotlin (KMP-ready) | `com.monday8am.edgelab.presentation` |
| `:core` | Android library | `com.monday8am.edgelab.core` |
| `:app:edgelab` | Android app | `com.monday8am.edgelab.explorer` |
| `:app:copilot` | Android app | `com.monday8am.edgelab.copilot` |

---

## `:data` — Data Models & Interfaces

Pure Kotlin, no Android dependencies.

### Authentication
- `AuthRepository` *(interface)* — auth token state and persistence
- `AuthorRepository` *(interface)* — HuggingFace author list management
- `AuthorInfo` — serializable author data (name, avatarUrl, modelCount)

### Model Management
- `ModelRepository` *(interface + impl)* — model catalog with StateFlow-based state, filtering and search
- `ModelConfiguration` — full model metadata: displayName, parameters, quantization, context length, download URL, hardware backend
- `ModelCatalog` — hardcoded fallback catalog (Qwen3-0.6B, Qwen2.5-1.5B, Gemma3-1B)
- `ModelCatalogProvider` *(interface)* — abstraction for model catalog sources
- `LocalModelDataSource` *(interface)* — local storage abstraction for model catalog

### HuggingFace Integration
- `HuggingFaceApiClient` — HTTP client for HF API (model list, file sizes, whoami, author info)
- `HuggingFaceModelRepository` — implements `ModelCatalogProvider`, fetches models from HF API with parallel requests; auto-adds authenticated user to model sources for gated model access
- `HuggingFaceModels` — DTOs: `HuggingFaceModelSummary`, `GatedStatus`
- `ModelFilenameParser` — regex-based parser extracting model metadata from filenames
- `FallbackModelCatalogProvider` — wraps primary provider with fallback to hardcoded catalog

### Testing
- `TestRepository` *(interface)* — loads test definitions
- `AssetsTestRepository` — loads from bundled resources
- `TestRepositoryImpl` — network-aware repository with local caching and fallback
- `TestDefinitions` — test schema: `TestCaseDefinition`, `ValidationRule` (10 rule types)
- `LocalTestDataSource` *(interface)* — local storage for test definitions

---

## `:agent` — Agent Logic & Tool Calling

Pure Kotlin/JVM. Depends on `:data` and LiteRT-LM JVM.

### Core
- `LocalInferenceEngine` *(interface)* — abstract inference engine: `initialize`, `prompt`, streaming, tools, `closeSession`
- `NotificationAgent` — unified agent supporting two backends:
  - **LOCAL**: local inference engines (LiteRT-LM) via `LocalInferenceLLMClient`
  - **KOOG**: Koog framework agents via `simpleOllamaAIExecutor()`
- `LocalInferenceLLMClient` — bridges Koog's `LLMClient` interface to the local inference engine
- `Utils` — event handler configuration for agent tracing and logging

### Tools
- `ToolHandler` *(interface)* — tool handling contract with call tracking
- `OpenApiToolHandler` — extends LiteRT-LM's `OpenApiTool`; returns mock responses and tracks calls
- `ToolHandlerFactory` — creates `OpenApiToolHandler` instances without exposing LiteRT-LM dependency
- `ToolCall` — records tool name, timestamp, and arguments

---

## `:presentation` — ViewModels & State Management

Pure Kotlin, KMP-ready (no Android dependencies). Depends on `:data` and `:agent`.

All ViewModels follow the same pattern: a platform-agnostic interface + implementation, wrapped by an Android-specific class in `:app:*`.

### Model Selector
- `ModelSelectorViewModel` / `ModelSelectorViewModelImpl` — model catalog UI: grouping (Family / Access / Author), download management, HuggingFace auth integration

### Test Runner
- `TestViewModel` / `TestViewModelImpl` — orchestrates test execution with reactive state machine
- `ToolCallingTestEngine` — framework-agnostic test runner with tool call tracking
- `TestRuleValidator` — converts data-layer test definitions to presentation layer; validates against 10 rule types
- `TagProcessor` — parses thinking tags (`<think>`, `<thinking>`), tracks streaming state, measures token speed
- `TestModels` — UI state: `TestUiState`, `TestStatus`, `TestResultFrame`

### Test Details
- `TestDetailsViewModel` / `TestDetailsViewModelImpl` — test catalog display with domain filtering

### Onboard (CyclingCopilot)
- `OnboardViewModel` / `OnboardViewModelImpl` — simplified two-model download flow for the Copilot app

### Author Manager
- `AuthorManagerViewModel` / `AuthorManagerViewModelImpl` — add/remove HuggingFace authors

### Key Interfaces (implemented in `:core`)
- `ModelDownloadManager` — abstract download manager with `Status` sealed interface

---

## `:core` — Android Infrastructure

Android library. Exposes `:data`, `:agent`, and `:presentation` transitively via `api` dependencies. Apps only need `implementation(project(":core"))`.

### Dependency Injection
- `CoreDependencies` *(object)* — factory methods for all shared infrastructure:
  - `createInferenceEngine()`
  - `createDownloadManager(context, authRepository)`
  - `createOAuthManager(context, clientId, redirectScheme, activityClass)`
  - `createAuthRepository(context, scope)`

### Inference
- `LiteRTLmInferenceEngineImpl` — implements `LocalInferenceEngine`; manages LiteRT-LM `Engine` / `Conversation` lifecycle with GPU/CPU backend selection

### Download
- `ModelDownloadManagerImpl` — implements `ModelDownloadManager` using WorkManager for background downloads
- `DownloadUnzipWorker` — `CoroutineWorker` that downloads and unzips model files with progress tracking

### OAuth
- `HuggingFaceOAuthManager` — AppAuth integration for HuggingFace OAuth flow; configurable per app via `redirectScheme` and `activityClass`
- `HuggingFaceOAuthConfig` — OAuth endpoints, scopes, and redirect URI generation

### Storage
- `AuthRepositoryImpl` — implements `AuthRepository` using DataStore with Tink AEAD encryption
- `DataStoreAuthorRepository` — implements `AuthorRepository`, persists HF author list
- `DataStoreModelDataSource` — implements `LocalModelDataSource`, caches model catalog
- `DataStoreTestDataSource` — implements `LocalTestDataSource`, caches test definitions
- `AuthTokenSerializer` — Tink-based serializer for encrypted token storage

---

## `:app:edgelab` — EdgeLab App

**Package**: `com.monday8am.edgelab.explorer`
**Application ID**: `com.monday8am.edgelab.explorer`
**OAuth redirect**: `edgelab://oauth/callback`

Model testing and tool-calling validation platform.

### Screens

| Screen | ViewModel wrapper | Purpose |
|--------|------------------|---------|
| `ModelSelectorScreen` | `AndroidModelSelectorViewModel` | Browse, download, and manage models |
| `TestScreen` | `AndroidTestViewModel` | Run tool-calling test suites |
| `TestDetailsScreen` | `AndroidTestDetailsViewModel` | Browse test catalog with domain filtering |
| `AuthorManagerScreen` | `AndroidAuthorManagerViewModel` | Add/remove HuggingFace authors |

### Navigation
- `Routes.kt` — type-safe sealed class route definitions
- `AppNavigation.kt` — Compose `NavHost` graph

### DI
- `Dependencies.kt` — wires `CoreDependencies` factories into app-level singletons

---

## `:app:copilot` — CyclingCopilot App

**Package**: `com.monday8am.edgelab.copilot`
**Application ID**: `com.monday8am.edgelab.copilot`
**OAuth redirect**: `copilot://oauth/callback`

On-device AI cycling assistant. See [`docs/cyclingcopilot/ui-architecture.md`](cyclingcopilot/ui-architecture.md) for full screen specs.

### Current State
- **Screen 1 (Onboard)**: Complete — model download flow with HuggingFace sign-in
- **Screen 2 (Ride Setup)**: Pending
- **Screen 3 (Live Ride)**: Pending

### Screens

| Screen | ViewModel wrapper | Purpose |
|--------|------------------|---------|
| `OnboardScreen` | `AndroidOnboardViewModel` | HF sign-in + model download (2 models) |

---

## Key Patterns

### MVI State Management
All ViewModels follow a unidirectional data flow:

```
userActions: MutableSharedFlow<Action>
  → flatMapConcat (execute action)
  → map (wrap in ActionState.Success / Error / Loading)
  → scan (reduce into UiState)
  → distinctUntilChanged
  → onEach (side effects)
  → stateIn (expose as StateFlow)
```

### Android ViewModel Wrapper Pattern
Each feature has three layers:
1. **Interface** (`:presentation`) — defines state and actions
2. **Impl** (`:presentation`) — pure Kotlin business logic, owns coroutine scope
3. **Android wrapper** (`:app:*`) — extends `ViewModel`, delegates to impl, converts Flow to StateFlow via `stateIn(viewModelScope)`

### Caching Pattern (Stale-While-Revalidate)
Used by both model catalog and test definitions:
1. Emit cached data immediately if available
2. Fetch fresh data from network in background (fire-and-forget)
3. Deduplicate: only emit if network data differs from cache
4. Persist network data to cache for next launch
5. Fallback to bundled assets on network failure

### Per-Test Tool Isolation
Each test run creates fresh tool handlers and resets the conversation:
1. `ToolHandlerFactory` creates handlers from the test's OpenAPI definitions
2. `setToolsAndResetConversation()` called with test-specific tools
3. Query executed, tool calls tracked
4. Results validated against `TestRuleValidator` rules
5. Next test repeats with a clean state

---

## LiteRT-LM Integration

- `Engine.initialize()` loads model with `EngineConfig` (modelPath, backend, maxNumTokens)
- `Conversation` created via `engine.createConversation(ConversationConfig)`
- `ConversationConfig` parameters: `samplerConfig` (topK, topP, temperature), `systemMessage`, `tools`
- `sendMessageAsync()` extension bridges `MessageCallback` to Kotlin coroutines
- **CRITICAL**: Runtime context cannot exceed the model's compiled context window. The `.litertlm` model has hardcoded KV cache buffers set at export time. Exceeding them causes `GATHER_ND index out of bounds` errors.

### Default Parameters
| Parameter | Default |
|-----------|---------|
| Context length | 4096 tokens |
| Max output tokens | 512 tokens |
| Temperature | 0.7 |
| Top-K | 40 |
| Top-P | 0.9 |

---

## Configuration

| Setting | Value |
|---------|-------|
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` / `compileSdk` | 36 |
| Kotlin toolchain | Java 11 (`:data`, `:agent`, `:presentation`) / Java 17 (`:core`, `:app:*`) |
| Recommended JDK | 17 or 21 |

### Version Catalog (`gradle/libs.versions.toml`)
| Library | Version |
|---------|---------|
| Koog agents | 0.4.2 |
| LiteRT-LM | 0.0.0-alpha05 |
| Compose BOM | 2025.09.01 |
| Kotlin | 2.2.20 |
| AGP | 8.13.0-rc02 |

---

## On-Device Function Calling — Current State

Tool calling on mobile remains experimental as of early 2026.

**Best tested accuracy**: ~68% single-turn function call (Qwen3-0.6B)
**Core limitation**: 0.6B parameter models cannot reliably handle tool-calling JSON generation and reasoning simultaneously.
**Runtime constraint**: LiteRT-LM conversion pipeline requires CPU-only processing; only dynamic-8bit quantization is supported on GPU L4.

See [Part #2](https://monday8am.com/blog/2025/12/10/function-calling-edge-ai.html) and [Part #3](https://monday8am.com/blog/2026/02/08/lets-talk-about-functiongemma.html) for full analysis.

---

## Adding a New App

1. Create `app/newapp/` with a `build.gradle.kts` (copy from `app/copilot/`, update namespace and applicationId)
2. Set a unique OAuth redirect scheme in manifest placeholders
3. Create `MainActivity` and `Dependencies.kt` using `CoreDependencies` factories
4. Add to `settings.gradle.kts`: `include(":app:newapp")`

All infrastructure is available via `implementation(project(":core"))`.
