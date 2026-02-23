# EdgeLab Roadmap

> This roadmap tracks development of the **EdgeLab** app. The repository also contains **CyclingCopilot**, which has its own separate roadmap.

## Completed

### Core Features
- [x] Generate task bundle for Hammer2.1
- [x] Test MediaPipe and function calling
- [x] Add model navigation 3 code
- [x] Add UI selector for model
- [x] Move download management from Notification screen to Model management screen
- [x] Add text explaining interaction on top of models (add spinner in model button)
- [x] Fix downloads and review it's working ok
- [x] Simplify list interaction
- [x] Review UI in general (add back button to notification screen)
- [x] Align test and notification buttons
- [x] Review all code and merge
- [x] Check UI problems on Notification screen
- [x] Upload converted models to Github
- [x] Ask if they can be uploaded to HF
- [x] Create charts
- [x] Restart article writing
- [x] Remove unused tests and clients
- [x] Update repository README
- [x] Add delete buttons to model cards
- [x] Add state for thinking, loading and answering in Notification screen (model card)

### 31 Dec
- [x] Fix tool calling for MediaPipe inference — remove fixed tool call formatter
- [x] Connect model list with Hugging Face (without login)
- [x] Integrate everything related to HuggingFace into the repository
- [x] Improve ModelSelectorViewModel
- [x] Add info screen for the models (HuggingFace)
- [x] Add HuggingFace login
- [x] Add grouping list style
- [x] Firebase integration for crashes
- [x] Full Hugging Face integration (sign-in / sign-out)

### 7 Jan
- [x] Add a better agentic example (or a list of them)
- [x] Add unit tests for ModelFilenameParser
- [x] Add tool trace and similar interface to the agentic test

### 22 Jan
- [x] Update library version
- [x] Remove full MediaPipe integration
- [x] Implement completely JSON driven dynamic tools with defined answers
- [x] Fix filtering by Test chips (not working)
- [x] Fix spinner in tests description cells
- [x] Add the tool for reviewing PRs (Greptile)
- [x] Update AuthRepositoryImpl with modern libs
- [x] Try to fix thinking answer (short answers) and update LazyColumn position
- [x] Set current test running position in LazyRow
- [x] Add Screen for test details
- [x] Add message stats (token / seconds) to cells and tests status
- [x] Read JSON tests from remote location
- [x] Create microsite with content

## In Progress / Next Up

- [ ] Upload app to Play Store
- [ ] Deactivate tests filters when running tests
- [ ] Add trained / not trained filtering
- [ ] Show agentic tests only for trainers
- [ ] Add model names matching against tests (agentic - generic)
- [ ] Add Function Gemma at any cost
- [ ] Connect model stats obtained with a server side, available to all developers

## Future

- [ ] Add Stats screen
- [ ] Flow diagram
- [ ] Check CactusCompute: https://cactuscompute.com/
