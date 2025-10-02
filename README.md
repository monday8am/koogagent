# From Flat Notifications to Edge AI

A prototype that explores how **agentic frameworks** and **on-device small language models (SLMs)** can turn generic push notifications into **context-aware, personalized prompts** — running offline on Android.

Inspired by the Yazio app, this project combines:

- [JetBrains Koog](https://github.com/JetBrains/koog) – agentic framework for language-model-driven agents  
- [MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference) – local inference on Android  
- Simulated MCPs – mock weather, season, and local meal context inputs


### Read the Full Article

Check out the full technical write-up:  
**[From Flat Notifications to Edge AI (Medium)](https://medium.com/@angel.anton/from-flat-notifications-to-edge-ai-42a594ce3b0c)**

---

_Disclaimer: It's a prototype app. Althout it solves problems like MediaPipe Inference API + Koog agents integration, the code is created for **fast iteration**, **experimentation** and **learning**._ 

### What It Does

This prototype generates smarter notifications using local context and on-device inference:

- Time-aware prompts (e.g. before lunch or late evening)
- Weather- and location-aware suggestions
- Dietary and streak-based personalization
- Fully offline — no cloud LLM fallback in the initial version


### Key Components

| Component       | Description |
|----------------|-------------|
| Koog Agent      | Orchestrates task execution, context assembly, and prompt creation |
| MediaPipe SLM   | Loads and runs the small language model locally on Android |
| Input/Output DTOs | Structured data exchange with the model (context in, text out) |
| Notification Engine | Displays the generated messages as Android push notifications |


### Tech Stack

- Kotlin (Multiplatform-ready)
- Jetpack Compose
- MediaPipe Inference API
- Local SLM models (Gemma, Mistral)
- Ollama (for JVM-side testing)


### Roadmap

- [ ] Add real MCP inputs (weather, season, meals)
- [ ] Prompt tuning and safety filtering
- [ ] Remote LLM fallback with opt-in
- [ ] iOS support via Kotlin Multiplatform
- [ ] Translation and post-processing layer

### Screenshots

<img src="screenshots/prototype.png" width="700" />


---
