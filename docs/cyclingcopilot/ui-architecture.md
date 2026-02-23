# Cycling Copilot UI Architecture

## Overview

This document defines the UI architecture for the Cycling Copilot app, a demo application showcasing on-device AI for cycling assistance. The app demonstrates offline-capable AI inference using LiteRT-LM (FunctionGemma 2B + Gemma 2 550M) for conversational cycling assistance with real-time context.

## Screen Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Onboard &  │────▶│  Ride Setup │────▶│  Live Ride  │
│   Download  │     │             │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
   one-time            per ride          the experience
```

### Screen 1: Onboard & Download (one-time)
Initial setup screen for model download and welcome. Only shown on first launch or when models are missing.

### Screen 2: Ride Setup (per ride)
Configuration screen for selecting simulation mode, route, playback speed, and advanced settings.

### Screen 3: Live Ride (the experience)
Main experience screen showing live map, HUD metrics, and conversational AI copilot.

---

## Screen 1: Onboard & Download

### Purpose
- Welcome new users
- Download required AI models (FunctionGemma 2B + Gemma 2 550M)
- Explain app capabilities
- Ensure all prerequisites are met before first ride

### UI Layout

```
┌──────────────────────────────────────┐
│  🚴 Cycling Copilot                  │
│  Your AI Riding Companion            │
│                                      │
│  ┌────────────────────────────────┐ │
│  │                                │ │
│  │   [Cyclist illustration]      │ │
│  │   cycling with AI overlay     │ │
│  │                                │ │
│  └────────────────────────────────┘ │
│                                      │
│  On-device AI for smarter rides     │
│  • Route suggestions                │
│  • Weather-aware planning           │
│  • Performance insights             │
│  • Works offline                    │
│                                      │
│  ┌────────────────────────────────┐ │
│  │ DOWNLOAD REQUIRED MODELS       │ │
│  │                                │ │
│  │ ● FunctionGemma 2B             │ │
│  │   [████████░░] 80%   Cancel    │ │
│  │   1.8 GB / 2.2 GB              │ │
│  │                                │ │
│  │ ○ Gemma 2 550M                 │ │
│  │   Waiting...                   │ │
│  │   520 MB                       │ │
│  └────────────────────────────────┘ │
│                                      │
│  Total download: ~2.7 GB            │
│  Storage space: 8.2 GB available    │
│                                      │
│  ┌──────────────────────────────┐   │
│  │  ✓ Continue to Setup         │   │
│  └──────────────────────────────┘   │
│  (enabled when all models ready)    │
└──────────────────────────────────────┘
```

### Components

#### DownloadCard (per model)
- Model name and size
- Download progress bar with percentage
- Download status (Waiting, Downloading, Completed, Failed)
- Cancel button (while downloading)
- Error message (if failed)

#### DownloadSummary
- Total download size
- Available storage space
- Estimated time remaining (while downloading)

#### ContinueButton
- Disabled state until all models are downloaded
- Navigates to Ride Setup when enabled

### State Management

**ViewModel**: `OnboardViewModel` (platform-agnostic in `:presentation`)

**State**:
```kotlin
data class OnboardState(
    val models: List<ModelDownloadInfo>,
    val totalDownloadSize: Long,
    val availableStorage: Long,
    val isAllModelsReady: Boolean,
    val errorMessage: String? = null
)

data class ModelDownloadInfo(
    val modelId: String,
    val displayName: String,
    val sizeBytes: Long,
    val downloadStatus: DownloadStatus,
    val progress: Float = 0f
)

sealed interface DownloadStatus {
    data object Waiting : DownloadStatus
    data object Downloading : DownloadStatus
    data object Completed : DownloadStatus
    data class Failed(val error: String) : DownloadStatus
}
```

**Actions**:
```kotlin
sealed interface OnboardAction {
    data class StartDownload(val modelId: String) : OnboardAction
    data class CancelDownload(val modelId: String) : OnboardAction
    data class RetryDownload(val modelId: String) : OnboardAction
    data object ContinueToSetup : OnboardAction
}
```

### Navigation
- **Entry**: App launch (if models not downloaded) or explicit navigation from Settings
- **Exit**: Navigates to `Route.RideSetup` when all models ready and user taps Continue

---

## Screen 2: Ride Setup

### Purpose
- Select GPS mode (Simulation vs Device GPS)
- Choose simulation route (if in simulation mode)
- Configure playback speed (for simulation)
- Access advanced settings
- Start the ride

### UI Layout

```
┌──────────────────────────────────────┐
│  Ride Setup                          │
│  Configure your ride before starting │
│                                      │
│  GPS MODE                            │
│  ┌────────────┐  ┌────────────┐     │
│  │▶ Simulation│  │◎ Device GPS│     │
│  │  GPS trace │  │  Real loc  │     │
│  └────────────┘  └────────────┘     │
│                                      │
│  SELECT ROUTE                        │
│  ┌──────────────────────────────┐   │
│  │ ● Guadarrama Mountain Loop   │   │
│  │   62km · +1,240m · Hard      │   │
│  ├──────────────────────────────┤   │
│  │ ○ Casa de Campo Circuit      │   │
│  │   28km · +180m · Easy        │   │
│  ├──────────────────────────────┤   │
│  │ ○ Sierra de Gredos           │   │
│  │   95km · +2,100m · Expert    │   │
│  └──────────────────────────────┘   │
│                                      │
│  PLAYBACK SPEED                      │
│  ┌──────────────────────────────┐   │
│  │  0.5x   [1x]   2x   5x  10x │   │
│  └──────────────────────────────┘   │
│                                      │
│  ▸ Advanced Settings                 │
│    ├ Remote LLM         [on]        │
│    ├ Developer HUD      [off]       │
│    └ Auto-voice (TTS)   [on]        │
│                                      │
│  ┌──────────────────────────────┐   │
│  │       ▶  Start Ride          │   │
│  └──────────────────────────────┘   │
└──────────────────────────────────────┘
```

### Components

#### GpsModeSelector
- Two-option segmented control
- **Simulation**: Uses bundled GPX traces with auto-playback
- **Device GPS**: Uses real device location (future, not in V1)

#### RouteList
- List of bundled routes (only shown in Simulation mode)
- Each route card shows:
  - Route name
  - Distance (km)
  - Elevation gain (m)
  - Difficulty badge (Easy/Medium/Hard/Expert)
- Radio button selection

#### PlaybackSpeedControl
- Segmented control with 5 options: 0.5x, 1x, 2x, 5x, 10x
- Only shown in Simulation mode
- **1x** = real-time replay (1 second = 1 second of ride)
- **10x** = fast-forward for quick demos

#### AdvancedSettingsSection
- Expandable section (collapsed by default)
- Toggle switches for:
  - **Remote LLM**: Use remote API for re-routing (requires connectivity)
  - **Developer HUD**: Show tool calls and debug info on Live Ride screen
  - **Auto-voice (TTS)**: Automatically speak copilot responses

#### StartRideButton
- Primary action button
- Validates that a route is selected (in simulation mode)
- Navigates to Live Ride screen

### State Management

**ViewModel**: `RideSetupViewModel` (platform-agnostic in `:presentation`)

**State**:
```kotlin
data class RideSetupState(
    val gpsMode: GpsMode,
    val routes: List<RouteInfo>,
    val selectedRouteId: String?,
    val playbackSpeed: PlaybackSpeed,
    val advancedSettings: AdvancedSettings,
    val isStartEnabled: Boolean
)

enum class GpsMode {
    SIMULATION,
    DEVICE_GPS  // Not implemented in V1
}

data class RouteInfo(
    val id: String,
    val name: String,
    val distanceKm: Float,
    val elevationGainM: Int,
    val difficulty: Difficulty
)

enum class Difficulty {
    EASY, MEDIUM, HARD, EXPERT
}

enum class PlaybackSpeed(val multiplier: Float) {
    SLOW(0.5f),
    NORMAL(1f),
    FAST(2f),
    VERY_FAST(5f),
    ULTRA_FAST(10f)
}

data class AdvancedSettings(
    val useRemoteLLM: Boolean = true,
    val showDeveloperHUD: Boolean = false,
    val enableAutoVoice: Boolean = true
)
```

**Actions**:
```kotlin
sealed interface RideSetupAction {
    data class SelectGpsMode(val mode: GpsMode) : RideSetupAction
    data class SelectRoute(val routeId: String) : RideSetupAction
    data class SetPlaybackSpeed(val speed: PlaybackSpeed) : RideSetupAction
    data class UpdateAdvancedSettings(val settings: AdvancedSettings) : RideSetupAction
    data object StartRide : RideSetupAction
}
```

### Navigation
- **Entry**: From Onboard screen or app home (if models already downloaded)
- **Exit**: Navigates to `Route.LiveRide(rideConfig)` when Start Ride tapped

---

## Screen 3: Live Ride

### Purpose
- Display live map with route, rider position, and POIs
- Show real-time HUD metrics (speed, distance, power, battery)
- Provide conversational AI copilot via chat interface
- Voice input for hands-free interaction
- Real-time updates as ride progresses

### UI Layout

```
┌──────────────────────────────────────┐
│ ◀ Guadarrama Loop    [Online] [⚙]  │  ← nav bar
│   Stage 4 · 1:23:45                 │
├──────────────────────────────────────┤
│                                      │
│         ┌─── MapLibre ───┐          │
│         │                 │          │
│         │  real tiles     │          │
│         │  route polyline │          │
│         │  (blue solid)   │          │
│         │                 │          │
│         │  rider dot ●    │          │
│         │  (pulsing)      │          │
│         │                 │          │
│         │  alt route      │          │
│         │  (yellow dash)  │          │
│         │                 │          │
│         │  POI markers    │          │
│         │  ☕ 💧 🔧       │          │
│         │                 │          │
│  ┌──────────┐ ┌──────────┐         │
│  │Next Climb│ │ Weather  │          │  ← floating cards
│  │El Boalo  │ │ 18°C     │          │
│  │2.4km @7% │ │ NW 12km/h│          │
│  └──────────┘ └──────────┘         │
│                                      │
├──────────────────────────────────────┤
│ 26.4km/h │ 42.1km │ 185W │ 88% 🔋 │  ← HUD strip
├──────────────────────────────────────┤
│  ═══ (drag handle) ═══              │
│  COPILOT: Monitoring your ride...   │  ← chat (collapsed)
│                                      │
│  [🎤]  [ Speak or type...      ▶ ] │  ← input bar
└──────────────────────────────────────┘
```

### Map Component (MapLibre GL)

#### Tile Source
- **Style**: Dark theme (dark navy background, muted roads, minimal labels)
- **Provider**: MapTiler Dark or custom style
- **Offline**: MBTiles bundled for route corridor (~50km buffer) - optional for V1
- **Online**: Requires connectivity for tile fetching in V1

#### Map Layers
1. **Route Polyline** (blue solid line `#0EA5E9`)
   - Full planned route from GPX
   - Thickness: 6dp
   - Completed segment: same color, 50% opacity

2. **Rider Position** (pulsing dot)
   - Current simulated position
   - Animated pulse effect
   - Size: 16dp outer ring, 8dp inner dot
   - Color: `#0EA5E9` (matches route)

3. **Alternative Route** (yellow dashed line `#F59E0B`)
   - Appears dynamically when copilot suggests alternative
   - Dashed pattern: 10dp dash, 5dp gap
   - Only shown when `get_route_alternatives` returns result

4. **POI Markers**
   - From `find_nearby_poi` tool results
   - Categories: Café ☕, Water 💧, Bike Shop 🔧, Shelter 🏠
   - Simple colored dots with category icons
   - Tap to show info card

#### Camera Behavior
- **Auto-center**: Follows rider with slight forward offset
- **Bearing-aware**: Rotates map based on heading (if GPX has bearing data)
- **Rider position**: Lower third of screen (shows more ahead)
- **Manual pan/zoom**: User can freely interact
- **Re-center button**: Appears when user pans away from rider

#### Floating Info Cards
- **Next Climb Card**: Shows upcoming climb info (name, distance, gradient)
- **Weather Card**: Current conditions (temp, wind speed/direction)
- Semi-transparent background
- Auto-hide after 10s of inactivity
- Tap to persist/dismiss

### HUD Strip

Four-column metrics bar showing real-time data:

```kotlin
data class HudMetrics(
    val speed: Float,           // km/h
    val distance: Float,        // km
    val power: Int?,            // watts (optional, null if no power meter)
    val batteryPercent: Int     // percentage
)
```

Layout:
- Equal-width columns
- Large value + small label
- Color: onSurface with 90% opacity
- Updates every 1s (or when value changes)

### Chat Interface

#### Collapsed State (default)
- Shows last copilot message
- Drag handle to expand
- Height: 80dp

#### Expanded State (after drag or tap)
- Scrollable message list
- Auto-scrolls to latest message
- Height: 40% of screen
- Swipe down to collapse

#### Message Types
1. **User Message** (right-aligned, primary container)
2. **Copilot Message** (left-aligned, surface variant)
3. **Tool Call Debug** (only if Developer HUD enabled, tertiary container)

#### Input Bar
- Voice button (🎤) - tap to record, tap again to send
- Text input field - for manual typing
- Send button (▶) - enabled when input not empty
- Auto-focus on expand

### Simulation Playback Controls

Small floating control at top-right of map (only in simulation mode):

```
┌──────────────┐
│ ▶ 1x  km 42 │
└──────────────┘
```

- Tap to pause/resume
- Tap repeatedly to cycle speed (1x → 2x → 5x → 10x → 0.5x)
- Shows current km progress
- Size: 80dp × 40dp
- Semi-transparent background
- Auto-hide after 3s of inactivity

### State Management

**ViewModel**: `LiveRideViewModel` (platform-agnostic in `:presentation`)

**State**:
```kotlin
data class LiveRideState(
    val rideConfig: RideConfig,
    val currentPosition: LatLng,
    val currentHeading: Float,
    val hudMetrics: HudMetrics,
    val routePolyline: List<LatLng>,
    val completedPolyline: List<LatLng>,
    val alternativeRoute: List<LatLng>?,
    val pois: List<PoiMarker>,
    val chatMessages: List<ChatMessage>,
    val isVoiceRecording: Boolean,
    val isProcessing: Boolean,
    val playbackState: PlaybackState
)

data class RideConfig(
    val routeId: String,
    val gpsMode: GpsMode,
    val playbackSpeed: PlaybackSpeed,
    val advancedSettings: AdvancedSettings
)

data class LatLng(val latitude: Double, val longitude: Double)

data class PoiMarker(
    val id: String,
    val position: LatLng,
    val category: PoiCategory,
    val name: String,
    val distance: Float  // meters from current position
)

enum class PoiCategory {
    CAFE, WATER, BIKE_SHOP, SHELTER
}

sealed interface ChatMessage {
    val id: String
    val timestamp: Long

    data class User(
        override val id: String,
        override val timestamp: Long,
        val text: String
    ) : ChatMessage

    data class Copilot(
        override val id: String,
        override val timestamp: Long,
        val text: String,
        val isSpoken: Boolean = false
    ) : ChatMessage

    data class ToolCallDebug(
        override val id: String,
        override val timestamp: Long,
        val toolName: String,
        val args: String,
        val result: String
    ) : ChatMessage
}

data class PlaybackState(
    val isPlaying: Boolean,
    val currentSpeed: PlaybackSpeed,
    val currentKm: Float,
    val totalKm: Float,
    val elapsedTime: Long  // milliseconds
)
```

**Actions**:
```kotlin
sealed interface LiveRideAction {
    data object TogglePlayback : LiveRideAction
    data class SetPlaybackSpeed(val speed: PlaybackSpeed) : LiveRideAction
    data object StartVoiceInput : LiveRideAction
    data object StopVoiceInput : LiveRideAction
    data class SendTextMessage(val text: String) : LiveRideAction
    data class SelectPoi(val poiId: String) : LiveRideAction
    data object RecenterMap : LiveRideAction
    data object ExpandChat : LiveRideAction
    data object CollapseChat : LiveRideAction
    data object PauseRide : LiveRideAction
    data object ResumeRide : LiveRideAction
    data object EndRide : LiveRideAction
}
```

### Interaction Flows

#### Voice Input Flow
```
1. User taps 🎤 → button turns red, pulsing
2. Speech-to-text captures audio
3. User message appears in chat
4. [Dev HUD if on]: shows tool calls (e.g., "get_ride_status() get_segment_ahead()")
5. Loading indicator (FunctionGemma + tools + Gemma 550M)
6. Copilot response appears in chat
7. If TTS on: response spoken aloud
8. Map: POI/route updates if relevant
```

Total latency budget: ~1-2s (FunctionGemma 0.3s + tools <0.1s + Gemma 550M ~1s)

#### Remote Re-planning Flow
```
1. Voice input: "Too windy, find a sheltered route"
2. [Dev HUD]: shows "get_weather_forecast() get_route_alternatives()"
3. Loading indicator (remote API call)
4. Yellow dashed alternative route appears on map
5. Copilot response: "Route B stays in valley — 15km/h less headwind. Adds 3km, ~20min later."
6. Action buttons below response:
   ┌─────────────┐  ┌─────────────┐
   │ ✓ Take it   │  │ ✗ Keep route│
   └─────────────┘  └─────────────┘
7. If "Take it" → route polyline updates, map animates
8. If "Keep route" → alt route fades out
```

#### Graceful Offline Fallback
```
1. Voice input: "Find me a different route to avoid rain"
2. [Dev HUD]: shows "get_weather_forecast() get_route_alternatives()"
3. Tool engine: get_route_alternatives returns { "status": "unavailable" }
4. Gemma 550M handles locally
5. Copilot response: "Can't reroute right now (no signal). Rain expected in ~40min. There's a shelter at km 28, 4km ahead."
6. Map: shelter POI marker highlighted
7. Connectivity pill shows "Offline" (orange)
```

No error screens, no crashes — graceful degradation.

### Navigation
- **Entry**: From Ride Setup screen when Start Ride tapped
- **Exit**: Back button → confirmation dialog → return to Ride Setup

---

## Data Requirements per Bundled Route

Each simulation route requires these files:

| File | Purpose | Size Estimate |
|------|---------|---------------|
| `route.gpx` | Trackpoints with timestamps | ~200KB |
| `route-data.json` | Pre-computed segment profiles, surface types, POIs | ~50KB |
| `route-weather.json` | Simulated weather snapshots along route | ~10KB |
| `route-alternatives.json` | 2-3 pre-computed alternative routes for demo | ~30KB |
| `rider-profile.json` | Default rider baseline metrics | ~5KB |

**Total per route**: ~300KB
**Three routes**: ~1MB (negligible alongside model files)

The tool engine in simulation mode reads from these JSON files instead of hitting real APIs. Same interface, same tool schemas, different data source.

---

## Navigation Architecture

### Route Definition

```kotlin
sealed interface Route {
    @Serializable data object Onboard : Route
    @Serializable data object RideSetup : Route
    @Serializable data class LiveRide(
        val rideConfig: RideConfig
    ) : Route
}
```

### Navigation Graph

```kotlin
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Onboard,  // Or Route.RideSetup if models exist
        modifier = modifier,
    ) {
        composable<Route.Onboard> {
            OnboardScreen(
                onNavigateToSetup = { navController.navigate(Route.RideSetup) }
            )
        }

        composable<Route.RideSetup> {
            RideSetupScreen(
                onNavigateToLiveRide = { config ->
                    navController.navigate(Route.LiveRide(config))
                }
            )
        }

        composable<Route.LiveRide> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.LiveRide>()
            LiveRideScreen(
                rideConfig = args.rideConfig,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## Theme & Styling

### Color Scheme
- Reuse edgelab theme as starting point
- Material3 with dynamic color support
- Dark mode preferred for cycling use (better outdoors visibility)

### Typography
- **Headlines**: For screen titles and section headers
- **Body**: For chat messages and descriptions
- **Label**: For HUD metrics and small labels

### Key Component Styles

#### Cards
- Surface variant background
- 8dp corner radius
- 8dp padding
- Subtle elevation (1dp)

#### Buttons
- Primary: Filled tonal button for main actions
- Secondary: Outlined button for secondary actions
- Icon buttons: For toolbar actions

#### Input Fields
- Outlined text field style
- Bottom border only (minimal)

---

## Dependencies Pattern

### Service Locator (`Dependencies.kt`)

```kotlin
object Dependencies {
    lateinit var appContext: Context

    val applicationScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    // Reuse from :core
    val modelDownloadManager: ModelDownloadManager by lazy {
        CoreDependencies.createDownloadManager(appContext, authRepository)
    }

    val authRepository: AuthRepository by lazy {
        CoreDependencies.createAuthRepository(appContext, applicationScope)
    }

    val inferenceEngine: LocalInferenceEngine by lazy {
        CoreDependencies.createInferenceEngine()
    }

    // Copilot-specific dependencies
    val gpxPlaybackEngine: GpxPlaybackEngine by lazy {
        GpxPlaybackEngineImpl(appContext)
    }

    val toolEngine: ToolEngine by lazy {
        ToolEngineImpl(
            context = appContext,
            inferenceEngine = inferenceEngine,
            isSimulation = true  // hardcoded for V1
        )
    }

    val copilotEngine: CopilotEngine by lazy {
        CopilotEngineImpl(
            inferenceEngine = inferenceEngine,
            toolEngine = toolEngine
        )
    }

    val speechRecognizer: SpeechRecognizer by lazy {
        AndroidSpeechRecognizer(appContext)
    }

    val textToSpeech: TextToSpeech by lazy {
        AndroidTextToSpeech(appContext)
    }
}
```

---

## Implementation Priority

If implementing in order:

1. **Screen 1 (Onboard)** — Model download UI + HuggingFace Hub integration
2. **Screen 3 (Live Ride) with hardcoded data** — Map, HUD, chat UI (no AI yet)
3. **Voice input pipeline** — SpeechRecognizer → text
4. **AI pipeline** — FunctionGemma → tools → Gemma 550M → TTS
5. **Screen 2 (Ride Setup)** — Route selection and playback config
6. **GPX playback engine** — Auto-advance position along trace
7. **Tool engine with simulation data** — Wire up 6 tools against bundled JSONs

---

## MapLibre Integration

### Dependencies

Add to `app/copilot/build.gradle.kts`:
```kotlin
implementation("org.maplibre.gl:android-sdk:11.0.0")
implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.0")
```

### Composable Integration

```kotlin
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    cameraPosition: LatLng,
    bearing: Float,
    routePolyline: List<LatLng>,
    alternativeRoute: List<LatLng>?,
    pois: List<PoiMarker>,
    onMapReady: (MapLibreMap) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            com.mapbox.mapboxsdk.maps.MapView(context).apply {
                getMapAsync { map ->
                    // Configure map style, layers, camera
                    onMapReady(map)
                }
            }
        },
        update = { mapView ->
            // Update camera, polylines, markers when state changes
        }
    )
}
```

### Style Configuration

Use MapTiler Dark or create custom style with:
- Dark navy background (`#0f172a`)
- Muted roads (`#334155`)
- Minimal labels (roads only, no POI labels)
- No building 3D extrusion

---

## Tool Schema Reference

The AI copilot uses 6 tools defined in OpenAPI format:

1. **get_ride_status()** - Current metrics, stage, pace
2. **get_segment_ahead(distance_km)** - Upcoming terrain profile
3. **get_weather_forecast()** - Conditions along route
4. **get_route_alternatives()** - Alternative routes (remote API)
5. **find_nearby_poi(category)** - POIs within 10km
6. **get_rider_profile()** - Rider's baseline metrics

See `ARCHITECTURE.md` for detailed tool schemas.

---

## File Structure

```
app/copilot/src/main/java/com/monday8am/koogagent/copilot/
├── Dependencies.kt
├── MainActivity.kt
├── ui/
│   ├── navigation/
│   │   ├── Routes.kt
│   │   └── AppNavigation.kt
│   ├── screens/
│   │   ├── onboard/
│   │   │   ├── OnboardScreen.kt
│   │   │   ├── OnboardViewModel.kt
│   │   │   └── DownloadCard.kt
│   │   ├── ridesetup/
│   │   │   ├── RideSetupScreen.kt
│   │   │   ├── RideSetupViewModel.kt
│   │   │   ├── GpsModeSelector.kt
│   │   │   ├── RouteList.kt
│   │   │   └── PlaybackSpeedControl.kt
│   │   └── liveride/
│   │       ├── LiveRideScreen.kt
│   │       ├── LiveRideViewModel.kt
│   │       ├── MapView.kt
│   │       ├── HudStrip.kt
│   │       ├── ChatPanel.kt
│   │       └── PlaybackControls.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── engine/
    ├── GpxPlaybackEngine.kt
    ├── ToolEngine.kt
    └── CopilotEngine.kt
```

---

## Testing Strategy

### Preview Support
- All screens should have `@Preview` composables
- Light and dark theme previews
- Different state variations (loading, error, success)

### Unit Tests
- ViewModels in `:presentation` module
- Tool engine logic
- GPX playback engine

### UI Tests
- Navigation flows
- User interactions (voice input, route selection)
- Map interactions

---

## Accessibility

- Voice input primary interaction (eyes-free)
- TTS for copilot responses
- High contrast UI (dark theme)
- Large touch targets (48dp minimum)
- Content descriptions for all interactive elements

---

## Performance Targets

- Model loading: <30s (one-time)
- Voice input latency: <200ms
- AI response latency: <2s (local), <5s (remote)
- Map frame rate: 60fps
- GPX playback precision: ±5 meters

---

## Future Enhancements (Post-V1)

- Real device GPS support
- Live sensor data (heart rate, power meter)
- Route recording and sharing
- Multi-day tour planning
- Offline map tiles bundling
- Custom route import
- Strava integration
