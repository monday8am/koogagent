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

### Two-Module Structure

1. **`:agent` module** (Pure Kotlin/JVM)
   - Platform-agnostic notification generation logic
   - `NotificationGenerator` class orchestrates the AI agent
   - `NotificationAgent` interface abstracts LLM implementations
   - `OllamaAgent` implementation for JVM testing with Ollama
   - Data models: `NotificationContext`, `NotificationResult`, `LocalLLModel`
   - No Android dependencies

2. **`:app` module** (Android Application)
   - Android-specific implementation and UI
   - `GemmaAgent` implementation using MediaPipe for on-device inference
   - `LocalInferenceUtils` wraps MediaPipe LLM Inference API
   - `ModelDownloadManager` handles model downloading and unzipping
   - UI layer with Jetpack Compose (MainActivity, NotificationViewModel)
   - Depends on `:agent` module

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

**Data Flow:**
1. UI sets `NotificationContext` (meal type, motivation, weather, etc.)
2. `NotificationViewModel.processAndShowNotification()` merges device context (locale, country)
3. `NotificationGenerator.generate()` builds prompt and calls agent
4. Agent returns JSON: `{"title":"...", "body":"...", "language":"...", "confidence":...}`
5. Fallback mechanism if LLM fails

## Build & Development Commands

### Build Project
```bash
./gradlew build
```

### Build Specific Module
```bash
./gradlew :app:build
./gradlew :agent:build
```

### Run Tests
```bash
./gradlew test                    # All tests
./gradlew :agent:test             # Agent module tests only
./gradlew :app:testDebugUnitTest  # App module unit tests
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
- Models are downloaded to app's internal storage via `ModelDownloadManager`
- Default model: `gemma3-1b-it-int4.litertlm` (Gemma 3 1B parameters, int4 quantized)
- Model URL in `NotificationViewModel`: GitHub release at `https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip`
- `ModelDownloadManager.getModelPath()` returns absolute path for MediaPipe

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
- Device context: `DeviceContextUtil.getDeviceContext()` extracts locale and country
- Location from `MockLocationProvider` (can be replaced with real Android LocationManager)
- Context merging happens in ViewModel before calling generator

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
- Java/Kotlin toolchain: Java 11

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
- `LocalInferenceUtils.prompt()` adds chunks to existing session
- Must call `LocalInferenceUtils.close()` to free resources (done in ViewModel.onCleared())

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

### Why Two Modules?
- `:agent` can be tested on JVM without Android emulator
- `:agent` logic is reusable for potential iOS Kotlin Multiplatform support
- Clean separation between LLM orchestration and Android UI

### Why Koog?
- Provides agentic framework with tool support (though not heavily used here)
- Event handlers for debugging LLM interactions
- Abstraction over different LLM executors

### Why MediaPipe?
- On-device inference with no cloud dependency
- Optimized for mobile GPUs
- Supports quantized models (int4, int8) for smaller size

## Common Patterns (continued)

### Implementing Real Location Provider
Currently using `MockLocationProvider` with hardcoded coordinates. To add real location:
1. Add location permissions to AndroidManifest.xml
2. Create `AndroidLocationProvider` implementing `LocationProvider` interface
3. Use `FusedLocationProviderClient` or `LocationManager`
4. Inject into `NotificationViewModel` constructor
5. Handle location permission requests in MainActivity

### Adding New Weather Providers
To add alternative weather sources (e.g., OpenWeatherMap, WeatherAPI):
1. Implement `WeatherProvider` interface in app/weather/
2. Map API response to `WeatherCondition` enum
3. Swap provider in `NotificationViewModel` initialization

## Known Limitations

- No real MCP server integration (weather uses direct API instead of MCP protocol)
- No prompt safety filtering
- No translation layer (relies on model to generate in correct language)
- MediaPipe client doesn't support streaming or moderation
- Single-shot inference only (no multi-turn conversations)
- Model download requires GitHub release URL (no fallback)
- Location is mocked (not using device GPS)
