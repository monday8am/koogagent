# CyclingCopilot Roadmap

> This roadmap tracks development of the **CyclingCopilot** app. The repository also contains **EdgeLab**, which has its own separate roadmap.

## Completed

- [x] Screen 1: Onboard & Download (model download UI + HuggingFace Hub integration)
- [x] Screen 3: Live Ride — map, HUD metrics strip, chat panel, playback controls (MapLibre Compose declarative map, dark tile style)
- [x] Route assets — Strade Bianche GranFondo (144 km, +2220 m): `route.json` (3207 GPS coords + surface/waytype metadata), `segments.json` (5 named sectors + 15 auto-detected gravel sectors), `weather.json` (hourly Tuscany forecast), `generate_alternatives.py` (ORS-based alternative generator)
- [x] Route → ViewModel wiring: `RouteRepository` interface (`:data`), `AssetRouteRepository` (`:core`), `LiveRideViewModelImpl` loads real route async, `ImmutableList` UiState, `isLoading` / `routeName` in UI

## In Progress / Next Up

- [ ] Screen 2: Ride Setup (route selection, GPS mode, playback speed config)
- [ ] Voice input pipeline (SpeechRecognizer → text)
- [ ] AI pipeline (FunctionGemma → tools → Gemma 550M → TTS)
- [ ] GPX playback engine (auto-advance position along trace at realistic pace using route `t` timestamps)
- [ ] Tool engine with simulation data (6 tools wired against bundled JSONs: route, segments, weather, alternatives)

## Future

- [ ] Real device GPS support
- [ ] Live sensor data (heart rate, power meter)
- [ ] Route recording and sharing
- [ ] Multi-day tour planning
- [ ] Offline map tiles bundling
- [ ] Custom route import
- [ ] Strava integration
