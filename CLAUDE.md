# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KoogAgent is an Android prototype that explores how agentic frameworks and on-device small language models (SLMs) can turn generic push notifications into context-aware, personalized prompts — running offline on Android.

**Key Technologies:**
- JetBrains Koog (agentic framework for language-model-driven agents)
- MediaPipe LLM Inference API (local inference on Android)
- Kotlin with Jetpack Compose
- Local SLM models (Gemma, Mistral)
- Ollama (for JVM-side testing)

**Important:** This is a prototype built for fast iteration, experimentation, and learning. The code prioritizes experimentation over production patterns.

## Module Architecture

### Four-Module Structure (KMP-Ready)

1. **`:data` module** (Pure Kotlin)
   - Data models: `NotificationContext`, `NotificationResult`, `MealType`, `MotivationLevel`, `WeatherCondition`
   - Platform-agnostic interfaces: `WeatherProvider`, `LocationProvider`
   - `DeviceContext` model
   - No dependencies

2. **`:agent` module** (Pure Kotlin/JVM)
   - Platform-agnostic notification generation logic
   - `NotificationGenerator` class orchestrates the AI agent
   - `NotificationAgent` interface abstracts LLM implementations
   - `GemmaAgent` implementation (uses LLM executor abstraction)
   - `OllamaAgent` implementation for JVM testing with Ollama
   - Core models: `LocalLLModel`, `LocalInferenceEngine` interface
   - Tools: `GetLocationTool`, `GetWeatherToolFromLocation`
   - Depends on `:data`

3. **`:presentation` module** (Pure Kotlin - KMP-Ready) ✨ NEW
   - **Package**: `com.monday8am.presentation.notifications`
   - Platform-agnostic state management and business logic
   - `NotificationViewModel` interface and `NotificationViewModelImpl`
   - MVI pattern: `UiState`, `UiAction` sealed class, `ActionState` sealed interface
   - Platform service interfaces (no implementations):
     - `LocalInferenceEngine` (LLM inference)
     - `ModelDownloadManager` (model downloading)
     - `NotificationEngine` (showing notifications)
     - `DeviceContextProvider` (device context)
   - Owns its own coroutine scope for background operations
   - Async notification generation with state updates
   - Depends on `:data` and `:agent`

4. **`:app` module** (Android Application)
   - Android-specific implementations of presentation interfaces
   - `LocalInferenceEngineImpl` wraps MediaPipe LLM Inference API
   - `ModelDownloadManagerImpl` handles model downloading via WorkManager
   - `NotificationEngineImpl` shows Android notifications (with idempotency check)
   - `DeviceContextProviderImpl` extracts device locale/country
   - `AndroidNotificationViewModel` wraps presentation ViewModel with Android lifecycle
   - UI layer with Jetpack Compose (MainActivity, theme)
   - Depends on `:data`, `:agent`, and `:presentation`

### Key Integration Points

**Koog Agent Integration:**
- Both `GemmaAgent` and `OllamaAgent` implement the `NotificationAgent` interface
- They create Koog `AIAgent` instances with:
  - System prompt defining the nutritionist role
  - Custom LLM executors (`SimpleGemmaAIExecutor` for MediaPipe, `simpleOllamaAIExecutor` for Ollama)
  - Event handlers for debugging (tool calls, agent finished, errors)
  - Temperature setting (0.7)

**MediaPipe Integration:**
- `LocalInferenceUtils.initialize()` creates `LlmInference` engine and session
- Supports GPU/CPU backends via `LlmInference.Backend`
- Model parameters: maxTokens, topK, topP, temperature
- `LocalInferenceUtils.prompt()` adds query chunks and generates responses asynchronously
- Custom `await()` extension for `ListenableFuture<T>` to bridge with coroutines

**Data Flow (MVI Pattern):**
1. UI dispatches `UiAction.ShowNotification`
2. `NotificationViewModelImpl` receives action and initializes LLM engine
3. On success, launches background coroutine to create notification asynchronously
4. `createNotification()` merges device context and calls `NotificationGenerator.generate()`
5. Agent returns JSON: `{"title":"...", "body":"...", "language":"...", "confidence":...}`
6. Dispatches `UiAction.NotificationReady` with result
7. Reducer updates `UiState` with notification
8. Side effect in flow shows notification via `NotificationEngine.showNotification()`
9. Fallback mechanism if LLM fails

**ViewModel Architecture:**
- **NotificationViewModelImpl** (in `:presentation`):
  - Pure Kotlin, platform-agnostic
  - Owns coroutine scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
  - Uses Flow operators for reactive state: `flatMapConcat`, `scan`, `distinctUntilChanged`
  - `dispose()` method cancels scope
  - Async notification generation to avoid blocking state updates

- **AndroidNotificationViewModel** (in `:app`):
  - Android wrapper using `ViewModel` from AndroidX
  - Delegates to `NotificationViewModelImpl` via interface
  - Converts Flow to StateFlow using `stateIn()` with `viewModelScope`
  - `onCleared()` calls `impl.dispose()` and `inferenceEngine.closeSession()`

## Build & Development Commands

### Build Project
```bash
./gradlew build
```

### Build Specific Module
```bash
./gradlew :app:build
./gradlew :agent:build
./gradlew :presentation:build
./gradlew :data:build
```

### Run Tests
```bash
./gradlew test                         # All tests
./gradlew :agent:test                  # Agent module tests only
./gradlew :presentation:test           # Presentation module tests only
./gradlew :app:testDebugUnitTest       # App module unit tests
```

### Android Instrumented Tests
```bash
./gradlew :app:connectedAndroidTest
```

### Clean Build
```bash
./gradlew clean
```

### Assemble APK
```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

### Check Dependencies
```bash
./gradlew dependencies
```

## Important Implementation Details

### Model Path Resolution
- Models are downloaded to app's internal storage via `ModelDownloadManagerImpl`
- Default model: `gemma3-1b-it-int4.litertlm` (Gemma 3 1B parameters, int4 quantized)
- Model URL in `NotificationViewModelImpl`: GitHub release at `https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip`
- `ModelDownloadManager.getModelPath()` returns absolute path for MediaPipe
- **Lazy evaluation**: `getLocalModel()` function evaluates path when needed, not at construction time

### LLM Client Bridges
- `GemmaLLMClient` implements Koog's `LLMClient` interface to wrap MediaPipe
- Converts Koog `Prompt` (list of messages) to single concatenated string
- Returns `Message.Assistant` with generated text
- Does not support streaming or moderation (throws exceptions)

### Weather Integration
- **Real weather data** fetched from Open-Meteo API (https://open-meteo.com/)
- `WeatherProvider` interface abstracts weather data sources (app/WeatherProvider.kt in agent module)
- `OpenMeteoWeatherProvider` implementation uses OkHttp to fetch current weather (app/weather/)
- `MockLocationProvider` provides default location (Madrid, Spain: 40.4168, -3.7038)
- Weather codes mapped to `WeatherCondition` enum (SUNNY, CLOUDY, RAINY, HOT, COLD)
- Integration: `NotificationViewModel` fetches weather before generating notification
- No API key required for Open-Meteo (free for non-commercial use)

### Notification Context
- Real weather data from Open-Meteo API (replacing simulated inputs)
- Device context: `DeviceContextProviderImpl.getDeviceContext()` extracts locale and country
- Location from `MockLocationProvider` (can be replaced with real Android LocationManager)
- Context merging happens in `createNotification()` before calling generator
- **Idempotency**: `NotificationEngineImpl` tracks last shown notification to prevent duplicates

### Error Handling
- Fallback notifications defined in `NotificationGenerator.fallback()`
- Hardcoded meal-specific suggestions for each `MealType`
- `NotificationResult.isFallback` flag indicates whether LLM succeeded

### Koog Framework Dependency Exclusion
- Both modules exclude `io.modelcontextprotocol:kotlin-sdk-core-jvm` from Koog
- This avoids conflicts with the project's own MCP simulation

## Configuration

### Android SDK Levels
- `minSdk`: 26 (Android 8.0)
- `targetSdk` / `compileSdk`: 36
- Java/Kotlin toolchain: Java 11 (for `:data`, `:agent`, `:presentation`) / Java 17 (for `:app`)

### Java Version Compatibility
- **Recommended**: Java 17 or Java 21
- **Incompatible**: Java 25 (bleeding edge, not yet supported by Kotlin/Gradle)
- Set Java version: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`
- Or configure in `gradle.properties`: `org.gradle.java.home=/path/to/jdk-17`

### Version Catalog
Dependencies managed in `gradle/libs.versions.toml`:
- Koog agents: 0.4.2
- MediaPipe tasks-genai: 0.10.29
- Compose BOM: 2025.09.01
- Kotlin: 2.2.20
- AGP: 8.13.0-rc02

### ProGuard
Not enabled for release builds (prototype phase)

## Testing Strategy

### JVM Testing with Ollama
- Use `:agent` module with `OllamaAgent`
- Run local Ollama server at `http://10.0.2.2:11434` (Android emulator host)
- Tests can verify prompt construction and JSON parsing without MediaPipe

### Android Testing
- MediaPipe requires actual device or emulator with model files
- UI tests in `app/src/androidTest`

## Common Patterns

### Adding New Meal Types
1. Add enum value to `MealType` in `agent/Models.kt`
2. Update fallback messages in `NotificationGenerator.fallback()`
3. Update UI dropdown in MainActivity

### Modifying Prompt Structure
- System prompt in `NotificationGenerator.systemPrompt`
- User prompt template in `NotificationGenerator.buildPrompt()`
- JSON schema embedded in prompt string
- Parser in `NotificationGenerator.parseResponse()` expects specific keys

### Changing Model Parameters
- Defaults:
  - `DEFAULT_CONTEXT_LENGTH = 4096` (total tokens for input + output)
  - `DEFAULT_MAX_OUTPUT_TOKENS = 512` (max tokens to generate, leaves ~3584 for input)
  - `DEFAULT_TEMPERATURE = 0.5f`
  - `DEFAULT_TOPK = 40`
  - `DEFAULT_TOPP = 0.9f`
- Override in `LocalLLModel` data class
- `NotificationViewModel` sets temperature to 0.8f for Gemma model
- **Note**: Gemma 3n-1b-it supports 1280, 2048, or 4096 token context windows
- MediaPipe's `setMaxTokens()` sets the TOTAL context (input + output combined)

### Working with MediaPipe Sessions
- Sessions maintain conversation state across multiple queries
- `LocalInferenceEngineImpl.prompt()` adds chunks to existing session
- Must call `closeSession()` to free resources (done in `AndroidNotificationViewModel.onCleared()`)

### MVI State Management Pattern

The presentation layer uses MVI (Model-View-Intent) pattern:

**UiState (Single Source of Truth):**
```kotlin
data class UiState(
    val textLog: String = "Initializing!",
    val context: NotificationContext = defaultNotificationContext,
    val isModelReady: Boolean = false,
    val notification: NotificationResult? = null,
    val downloadStatus: ModelDownloadManager.Status = ModelDownloadManager.Status.Pending,
)
```

**UiAction (User Intents):**
```kotlin
sealed class UiAction {
    data object DownloadModel : UiAction()
    data object ShowNotification : UiAction()
    data class UpdateContext(val context: NotificationContext) : UiAction()
    // Internal actions for async results
    internal data object Initialize : UiAction()
    internal data class NotificationReady(val content: NotificationResult): UiAction()
}
```

**Flow Pipeline:**
1. `userActions: MutableStateFlow<UiAction>` receives actions
2. `flatMapConcat` executes action (download, initialize, etc.)
3. `map` wraps results in `ActionState.Success/Error/Loading`
4. `scan` reduces state via `reduce()` function
5. `distinctUntilChanged` prevents duplicate emissions
6. `onEach` triggers side effects (show notification)

**Key Benefits:**
- Unidirectional data flow
- Testable reducers (pure functions)
- Clear separation between sync state updates and async operations
- Side effects isolated in `onEach`

### Tool Naming Convention
- **All tool names must use PascalCase** (e.g., `GetLocationTool`, `GetWeatherTool`)
- Tool names follow Koog's convention using class names with uppercase first letter
- When creating new tools, set the `ToolDescriptor.name` field to PascalCase
- Example:
  ```kotlin
  override val descriptor = ToolDescriptor(
      name = "GetLocationTool",  // ✅ Correct: PascalCase
      // NOT "getLocationTool"   // ❌ Wrong: camelCase
      description = "Get the user location"
  )
  ```

## Architecture Decisions

### Why Four Modules?

**Separation Rationale:**
- **`:data`**: Shared data models across all layers, no dependencies
- **`:agent`**: AI agent logic testable on JVM without Android
- **`:presentation`**: Platform-agnostic ViewModels, KMP-ready for iOS/Desktop
- **`:app`**: Android-specific implementations only

**Benefits:**
1. **KMP-Ready**: `:data`, `:agent`, and `:presentation` have zero Android dependencies
2. **Testability**: Can test business logic on JVM without emulator
3. **Reusability**: Same ViewModels for Android, iOS (future), Desktop (future)
4. **Clear Boundaries**: Each module has single responsibility
5. **Dependency Flow**: Unidirectional (data ← agent ← presentation ← app)

**Why Split Presentation from App?**
- ViewModel state management is platform-agnostic
- Only implementations (MediaPipe, WorkManager, Android notifications) are platform-specific
- Allows testing presentation logic without Android framework
- Enables sharing business logic across platforms

**Module Dependency Graph:**
```
:data (no dependencies)
  ↑
:agent (depends on :data)
  ↑
:presentation (depends on :data, :agent)
  ↑
:app (depends on :data, :agent, :presentation)
```

**What Lives Where:**
- **Interfaces**: `:data` (domain), `:presentation` (platform services)
- **Business Logic**: `:agent` (AI), `:presentation` (state management)
- **Implementations**: `:app` (Android-specific)
- **Models**: `:data` (shared everywhere)

### Why Koog?
- Provides agentic framework with tool support (though not heavily used here)
- Event handlers for debugging LLM interactions
- Abstraction over different LLM executors

### Why MediaPipe?
- On-device inference with no cloud dependency
- Optimized for mobile GPUs
- Supports quantized models (int4, int8) for smaller size

## Common Patterns (continued)

### ViewModel Lifecycle & Resource Management

**Scope Management:**
- `NotificationViewModelImpl` owns: `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
- `AndroidNotificationViewModel` owns: `viewModelScope` (from AndroidX ViewModel)
- Separation allows platform-agnostic cleanup via `dispose()`

**Cleanup Chain:**
```kotlin
AndroidNotificationViewModel.onCleared() {
    impl.dispose()                    // Cancels presentation scope
    inferenceEngine.closeSession()    // Frees MediaPipe resources
}
```

**Why SupervisorJob?**
- If one background operation (e.g., notification generation) fails, others continue
- Parent scope cancellation still cancels all children

### Implementing Real Location Provider
Currently using `MockLocationProvider` with hardcoded coordinates. To add real location:
1. Add location permissions to AndroidManifest.xml
2. Create `AndroidLocationProvider` implementing `LocationProvider` interface (from `:data`)
3. Use `FusedLocationProviderClient` or `LocationManager`
4. Inject into `NotificationViewModelFactory` constructor
5. Handle location permission requests in MainActivity

### Adding New Weather Providers
To add alternative weather sources (e.g., OpenWeatherMap, WeatherAPI):
1. Implement `WeatherProvider` interface (from `:data`) in `:app` module
2. Map API response to `WeatherCondition` enum
3. Inject provider via `NotificationViewModelFactory` constructor
4. No changes needed in `:presentation` module (uses interface only)

## Known Limitations

- No real MCP server integration (weather uses direct API instead of MCP protocol)
- No prompt safety filtering
- No translation layer (relies on model to generate in correct language)
- MediaPipe client doesn't support streaming or moderation
- Single-shot inference only (no multi-turn conversations)
- Model download requires GitHub release URL (no fallback)
- Location is mocked (not using device GPS)
