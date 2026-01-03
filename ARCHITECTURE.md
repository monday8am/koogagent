# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KoogAgent is an Android prototype that explores how agentic frameworks and on-device small language models (SLMs) can turn generic push notifications into context-aware, personalized prompts — running offline on Android.

**Key Technologies:**
- JetBrains Koog (agentic framework for language-model-driven agents)
- LiteRT-LM (on-device LLM inference with GPU/CPU/NPU support)
- Kotlin with Jetpack Compose
- Local SLM models (Qwen3-0.6B, Hammer2.1-0.5B - tested for function calling)
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
   - `NotificationAgent` - unified agent supporting multiple LLM backends (local inference and Koog framework)
   - `LocalInferenceLLMClient` - bridge between Koog and platform-specific inference engines (LiteRT-LM, MediaPipe)
   - Core models: `LocalLLModel`, `LocalInferenceEngine` interface
   - Tools: `GetLocationTool`, `GetWeatherToolFromLocation`
   - Tool calling handled natively by platform implementations (no custom protocols)
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
   - `LocalInferenceEngineImpl` wraps LiteRT-LM Engine/Conversation API (in `app/litert/`)
   - `GemmaAgentLiteRT` agent implementation using LiteRT-LM (in `app/litert/`)
   - `GemmaAgentLiteRTFactory` creates agent instances via factory pattern
   - LiteRT native tools: `LocationTools`, `WeatherTools` (in `app/litert/tools/`)
   - `ModelDownloadManagerImpl` handles model downloading via WorkManager
   - `NotificationEngineImpl` shows Android notifications (with idempotency check)
   - `DeviceContextProviderImpl` extracts device locale/country
   - `AndroidNotificationViewModel` wraps presentation ViewModel with Android lifecycle
   - UI layer with Jetpack Compose (MainActivity, theme)
   - Depends on `:data`, `:agent`, and `:presentation`

### Key Integration Points

**Koog Agent Integration:**
- `NotificationAgent` is a unified agent supporting two LLM backends:
  - **LOCAL**: Local inference engines (LiteRT-LM, MediaPipe) via `LocalInferenceLLMClient`
  - **KOOG**: Koog framework agents (Ollama) via `simpleOllamaAIExecutor()`
- Creates Koog `AIAgent` instances with:
  - System prompt defining the nutritionist role
  - LLM executor (either LocalInferenceAIExecutor or simpleOllamaAIExecutor)
  - Event handlers for debugging (tool calls, agent finished, errors)
  - Temperature setting (0.2)
  - Model information (provider, id, capabilities) built from constructor parameters
- Tool calling is handled natively:
  - LiteRT-LM: Uses `@Tool` annotations, tools passed via `ConversationConfig`
  - MediaPipe: Uses `HammerFormatter` with protobuf `Tool` objects
  - Ollama: Uses OpenAI-compatible tool calling API

**LiteRT-LM Integration:**
- `LocalInferenceEngineImpl` implements `LocalInferenceEngine` interface (in `app/litert/`)
- Uses LiteRT-LM's `Engine`/`Conversation` API pattern
- `Engine.initialize()` loads model with `EngineConfig`:
  - `modelPath`: Absolute path to `.litertlm` model file
  - `backend`: GPU or CPU (via `Backend.GPU` / `Backend.CPU`)
  - `maxNumTokens`: Total context length (default 4096)
- `Conversation` created via `engine.createConversation(ConversationConfig)`
- `ConversationConfig` parameters:
  - `samplerConfig`: topK, topP, temperature
  - `systemMessage`: Optional system prompt (as `Message`)
  - `tools`: List of tool objects with `@Tool` annotations (works with Qwen3 and compatible models)
- `sendMessageAsync()` extension (in `ConversationExtensions.kt`) bridges `MessageCallback` to Kotlin coroutines
- Response collected via `MessageCallback`: `onMessage()` → `onDone()` → returns full text

**MediaPipe Integration:**
- `MediaPipeInferenceEngineImpl` implements `LocalInferenceEngine` interface (in `app/inference/mediapipe/`)
- Uses MediaPipe's `GenerativeModel`/`ChatSession` API with `HammerFormatter`
- `LlmInference` created via MediaPipe options, wrapped in `LlmInferenceBackend`
- Tools defined as protobuf `Tool` objects (in `MediaPipeTools.kt`)
- `HammerFormatter` handles function calling protocol for Hammer 2.1 model

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
- `ModelDownloadManager.getModelPath()` returns absolute path for inference engine
- **Lazy evaluation**: `getLocalModel()` function evaluates path when needed, not at construction time

### On-Device Function Calling Status

**Current State (Late 2025): Non-Production-Ready**

Tool calling on mobile devices remains experimental. Based on testing with models from the Berkeley Function-Calling Leaderboard:

**Models Tested:**
- **Qwen3-0.6B**: ~68% accuracy at single-turn function call, slightly better accuracy
- **Hammer2.1-0.5B**: ~68% accuracy, significantly faster inference

**The Core Problem:**
Models capable of function calling cannot reliably execute in available runtimes, while models compatible with runtimes cannot reliably perform function calling. This fundamental incompatibility remains unresolved.

**Cognitive Capacity Limitation:**
0.6B parameter models cannot reliably handle tool calling JSON generation plus reasoning simultaneously. This represents the current ceiling for on-device function calling.

**Runtime Limitations:**
- **LiteRT-LM**: Capable C++ Conversation API but limited Kotlin JNI functionality. Conversion pipeline requires CPU-only processing.
- **MediaPipe**: Mature and well-documented but dormant (~7 months). Offers custom formatter flexibility (HammerFormatter for Hammer models).

**Quantization:**
Only dynamic-8bits quantization is supported during conversion on GPU L4 processors.

**Reference:** [Function Calling at the Edge](https://monday8am.com/blog/2025/12/10/function-calling-edge-ai.html)

### LLM Client Bridge
- `LocalInferenceLLMClient` implements Koog's `LLMClient` interface
- Simple passthrough bridge to platform inference engines:
  - Takes `promptExecutor: suspend (String) -> String?` function
  - Optionally takes `streamPromptExecutor: (String) -> Flow<String>` for streaming
  - Converts Koog's `Prompt` (list of messages) to string and passes to executor
  - Returns `Message.Assistant` with generated text
  - Supports streaming via `executeStreaming()` if stream executor provided
  - Does not support moderation (throws UnsupportedOperationException)

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
- LiteRT-LM: 0.0.0-alpha05
- Compose BOM: 2025.09.01
- Kotlin: 2.2.20
- AGP: 8.13.0-rc02

### ProGuard
Not enabled for release builds (prototype phase)

## Testing Strategy

### JVM Testing with Ollama
- Use `:agent` module with `NotificationAgent.koog()`
- Run local Ollama server at `http://10.0.2.2:11434` (Android emulator host)
- Tests can verify prompt construction and tool calling without LiteRT-LM
- Example: `agent/ollama/App.kt` creates agent with Ollama backend

### Android Testing
- LiteRT-LM and MediaPipe require actual device or emulator with model files
- Models must be downloaded to device storage (`.litertlm` format for LiteRT, `.bin` for MediaPipe)
- UI tests in `app/src/androidTest`
- Tool calling tests verify native platform tool calling (LiteRT-LM or MediaPipe)

## Re-exporting Models with Larger Context

If you need to increase the context window for longer conversations or more complex prompts:

### Option 1: Using AI Edge Torch (Recommended)
```bash
# Install dependencies
pip install ai-edge-torch torch transformers

# Export with custom context length
python export_to_litert.py \
  --model_id Qwen/Qwen3-0.6B-Instruct \
  --output_path qwen3_0.6b_q8_ekv4096.litertlm \
  --max_seq_len 4096 \
  --quantize int8
```

### Option 2: Using Google's Model Explorer
1. Download HuggingFace model: `Qwen/Qwen3-0.6B-Instruct`
2. Convert to TFLite with custom KV cache size
3. Use LiteRT-LM packaging tool to create `.litertlm` bundle

### Key Parameters
- `max_seq_len` / `max_context_length`: Total tokens (input + output)
- `kv_cache_max`: KV cache size (should match or exceed max_seq_len)
- Quantization: Only dynamic-8bits supported on GPU L4 processors

### Context Size Recommendations
- **1024 tokens**: Basic prompts and single-turn conversations
- **2048 tokens**: Multi-turn conversations with moderate history
- **4096 tokens**: Complex prompts with tool calling (recommended)
- **8192+ tokens**: Long conversations with extensive tool calling

**Note**: Larger context = more memory usage. Test on target device to ensure acceptable performance.

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
- Defaults (in `agent/core/Utils.kt`):
  - `DEFAULT_CONTEXT_LENGTH = 4096` (total tokens for input + output)
  - `DEFAULT_MAX_OUTPUT_TOKENS = 512` (max tokens to generate, leaves ~3584 for input)
  - `DEFAULT_TEMPERATURE = 0.7f`
  - `DEFAULT_TOPK = 40`
  - `DEFAULT_TOPP = 0.9f`
- Override in `LocalLLModel` data class (e.g., `getLocalModel()` in NotificationViewModel)
- LiteRT-LM's `EngineConfig.maxNumTokens` sets the TOTAL context (input + output combined)
- **CRITICAL**: Runtime context CANNOT exceed the model's compiled context window
  - The `.litertlm` model has hardcoded KV cache buffers set during TFLite export
  - Attempting to exceed compiled context causes `GATHER_ND index out of bounds` errors
  - To increase context: Must re-export model from HuggingFace with larger KV cache

### Working with LiteRT-LM Conversations
- `Conversation` maintains conversation state across multiple queries
- `LocalInferenceEngineImpl.prompt()` sends messages to existing conversation
- `resetConversation()` closes old conversation and creates new one with same config
- Must call `closeSession()` to free resources (done in `AndroidNotificationViewModel.onCleared()`)
- Cleanup chain: `conversation.close()` → `engine.close()`

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
- Only implementations (LiteRT-LM, WorkManager, Android notifications) are platform-specific
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
- Provides agentic framework with tool registry and execution
- Event handlers for debugging LLM interactions
- Abstraction over different LLM executors (custom text-based clients and Ollama)
- Built-in support for multiple strategies (default, functional, etc.)
- Clean separation between tool definition and execution

### Why LiteRT-LM?
- On-device inference with no cloud dependency
- Optimized for mobile GPU, CPU, and NPU acceleration
- Supports quantized models (int4, int8) for smaller size
- Active development from Google with expanding model support
- `Engine`/`Conversation` API provides clean abstraction for inference
- Built-in support for multimodal inputs (text, vision, audio) in compatible models

### Unified Agent Architecture

**NotificationAgent Design:**
`NotificationAgent` provides a unified interface for creating AI agents with two backend types:

**Two Backend Types:**
1. **LOCAL** - Local inference-based agents (LiteRT-LM, MediaPipe)
   - Requires: `promptExecutor` function
   - Tool calling: Handled natively by platform implementation
   - Use case: On-device models (Qwen3, Gemma3, Hammer2.1)

2. **KOOG** - Koog framework agents (Ollama, etc.)
   - Uses: Built-in executors (no promptExecutor needed)
   - Tool calling: Native Ollama API
   - Use case: Server-based models, future Koog-compatible agents

**Factory Methods:**
```kotlin
// Local inference agent (LiteRT-LM or MediaPipe)
NotificationAgent.local(
    promptExecutor: suspend (String) -> String?,
    modelId: String,
    modelProvider: LLMProvider = LLMProvider.Google,
)

// Koog framework agent (Ollama)
NotificationAgent.koog(
    model: LLModel,
)
```

**Key Design Decisions:**
1. **Explicit Backend Selection**: Factory methods clarify which type of agent you're creating
2. **Minimal Parameters**: LOCAL agents only need `promptExecutor` + `modelId`
3. **Model Configuration**:
   - LOCAL: Builds `LLModel` internally from `modelId` string
   - KOOG: Accepts `LLModel` directly (mirrors `AIAgent` API)
4. **Tool Calling**: Platform-specific, not configured at agent level
5. **Single Class**: No interface needed with only one implementation

**Usage Examples:**
```kotlin
// On-device Qwen3 with LiteRT-LM
val agent = NotificationAgent.local(
    promptExecutor = { prompt -> inferenceEngine.prompt(prompt).getOrThrow() },
    modelId = "qwen3-0.6b-instruct-int4",
)

// Server-based Ollama
val client = OllamaClient()
val llModel = client.getModels().firstOrNull()?.toLLModel()
val agent = NotificationAgent.koog(
    model = llModel,
)
```

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
    inferenceEngine.closeSession()    // Frees LiteRT-LM resources (conversation + engine)
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

### Function Calling
- **Tool calling is non-production-ready** on mobile devices as of late 2025
- 0.6B parameter models cannot reliably handle tool calling JSON + reasoning
- Models capable of function calling cannot run reliably in available runtimes
- Best tested accuracy: ~68% at single-turn function call (Qwen3-0.6B, Hammer2.1-0.5B)

### Runtime Constraints
- LiteRT-LM: Limited Kotlin JNI functionality, CPU-only conversion pipeline
- MediaPipe: Dormant development (~7 months), but has HammerFormatter flexibility
- Only dynamic-8bits quantization supported on GPU L4

### Other Limitations
- No real MCP server integration (weather uses direct API)
- No prompt safety filtering
- No translation layer (relies on model)
- Location is mocked (not using device GPS)
- Model download requires GitHub release URL (no fallback)
