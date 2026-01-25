# From Flat Notifications to Edge AI

A prototype that explores how **agentic frameworks** and **on-device small language models (SLMs)** can turn generic push notifications into **context-aware, personalized prompts** — running offline on Android.

Inspired by the Yazio app, this project combines:

- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) – on-device LLM inference with GPU/CPU support
- [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference) – alternative inference backend for specific models
- [JetBrains Koog](https://github.com/JetBrains/koog) – agentic framework structure for tool management
- Real weather data via [Open-Meteo API](https://open-meteo.com/)


### Read the Articles

**Part #1:** [From Flat Notifications to Edge AI](https://monday8am.com/blog/2025/10/01/flat-notifications-edge-ai.html) – the initial concept and motivation. ([Medium](https://medium.com/@angel.anton/from-flat-notifications-to-edge-ai-42a594ce3b0c))

**Part #2:** [Function Calling with Edge AI](https://monday8am.com/blog/2025/12/10/function-calling-edge-ai.html) – deep dive into tool calling challenges and the current state of on-device agentic AI. ([Medium](https://medium.com/@angel.anton/researching-tool-calling-in-on-device-ai-1c6143854ff3))

---

_Disclaimer: This is a prototype app created for **fast iteration**, **experimentation**, and **learning**. While exploring the limits of edge AI tool calling, it serves as an alternative to Google's AI Edge Gallery app, focused specifically on text generation and agentic workflows._

### What It Does

This prototype generates smarter notifications using local context and on-device inference:

- Time-aware prompts (e.g., before lunch or late evening)
- Weather- and location-aware suggestions (real weather data from Open-Meteo)
- Dietary and streak-based personalization
- **Tool calling validation** – test framework for evaluating function calling capabilities
- **Model management** – download and switch between different SLMs
- Fully offline — no cloud LLM fallback


### Architecture

The project uses a **four-module KMP-ready architecture**:

| Module | Description |
|--------|-------------|
| `:data` | Pure Kotlin data models and provider interfaces (zero dependencies) |
| `:agent` | Platform-agnostic notification agent with multiple tool calling formats |
| `:presentation` | MVI state management and ViewModels (KMP-ready) |
| `:app` | Android implementations (LiteRT-LM, MediaPipe, Compose UI) |


### Supported Models

| Model | Parameters | Inference | Context |
|-------|------------|-----------|---------|
| Qwen3 0.6B | 0.6B (int8) | LiteRT-LM | 4K tokens |
| Gemma 3 1B | 1B (int4) | LiteRT-LM | 4K tokens |
| Hammer 2.1 0.5B | 0.5B (int8) | MediaPipe | 2K tokens |
| Hammer 2.1 1.5B | 1.5B (int8) | MediaPipe | 2K tokens |


### Key Components

| Component | Description |
|-----------|-------------|
| NotificationAgent | Unified agent supporting multiple LLM backends and tool formats |
| LiteRT-LM Engine | Primary inference runtime with GPU acceleration |
| MediaPipe GenAI | Alternative runtime for Hammer2.1 models |
| Tool Registry | Multiple formats: SIMPLE, OPENAPI, SLIM, REACT, HERMES, NATIVE |
| Model Selector | UI for downloading and switching between models |
| Test Framework | Validates tool calling accuracy across models |


### Tech Stack

- Kotlin (Multiplatform-ready modules)
- Jetpack Compose
- LiteRT-LM 0.8.0
- MediaPipe GenAI 0.10.29
- Local SLM models (Qwen3, Gemma, Hammer2.1)
- Ollama (for JVM-side testing)


### Roadmap

- [ ] Llama.cpp runtime integration
- [ ] RAG-based alternatives for context injection
- [ ] LiteRT-LM native tool calling improvements
- [ ] Play Store publication
- [ ] iOS support via Kotlin Multiplatform


### Screenshots

<img src="screenshots/screenshots.jpg" width="700" />



---

### License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

