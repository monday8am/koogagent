# AGENTS.md

Context and instructions for AI agents working on the EdgeLab project.

## Project Overview

EdgeLab is an Android repository containing two apps:
- **EdgeLab** — on-device AI lab for model testing and tool-calling validation
- **CyclingCopilot** — on-device AI cycling assistant

Both apps share a common multi-module architecture built on `:core`, `:data`, `:agent`, and `:presentation`.

## Documentation Index

| Document | Description |
|----------|-------------|
| [`docs/architecture.md`](docs/architecture.md) | Full technical architecture, module structure, integration patterns, and implementation details |
| [`docs/refactoring.md`](docs/refactoring.md) | Multi-module refactoring history and migration notes |
| [`docs/edgelab/roadmap.md`](docs/edgelab/roadmap.md) | EdgeLab feature roadmap (completed, in progress, future) |
| [`docs/cyclingcopilot/roadmap.md`](docs/cyclingcopilot/roadmap.md) | CyclingCopilot feature roadmap |
| [`docs/cyclingcopilot/ui-architecture.md`](docs/cyclingcopilot/ui-architecture.md) | CyclingCopilot UI screen designs, state management, and component specs |

## Commands

### Build
- Build all: `./gradlew build`
- Build core: `./gradlew :core:build`
- Build EdgeLab: `./gradlew :app:edgelab:build`
- Build CyclingCopilot: `./gradlew :app:copilot:build`
- Clean: `./gradlew clean`

### Test
- Run all tests: `./gradlew test`
- Agent tests: `./gradlew :agent:test`
- Presentation tests: `./gradlew :presentation:test`
- Core tests: `./gradlew :core:testDebugUnitTest`

### Development
- Assemble debug APKs: `./gradlew :app:edgelab:assembleDebug :app:copilot:assembleDebug`
- Install EdgeLab: `./gradlew :app:edgelab:installDebug`
- Install CyclingCopilot: `./gradlew :app:copilot:installDebug`

## Coding Standards
- Kotlin-first, Jetpack Compose for UI
- MVI pattern in the presentation layer
- Platform-agnostic logic in `:data`, `:agent`, `:presentation`; Android-specific code in `:core` and `:app:*`

## Module Structure

```
:data ← :agent ← :presentation ← :core ← :app:edgelab
                                        ← :app:copilot
```

See [`docs/architecture.md`](docs/architecture.md) for full details.

## Workflow Automation & Permissions

Agents can run commands automatically if defined in workflows under `.agent/workflows/`.

- Add `// turbo` above a command to auto-approve it
- Add `// turbo-all` at the top of a workflow file to auto-approve all commands in it
- Trigger workflows via slash commands (e.g., `/build`, `/test`)
