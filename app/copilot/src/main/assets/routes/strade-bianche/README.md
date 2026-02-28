# Strade Bianche — Route Assets

Source: Komoot tour 993464885
Distance: 144 km · Elevation: +2220 m · Start/finish: Siena

## File overview

| File | Size | Purpose |
|------|------|---------|
| `route.json` | 321 KB | Coordinates + per-point surface and waytype metadata |
| `segments.json` | 5.7 KB | Named and auto-detected gravel/unpaved sectors |
| `weather.json` | 3.1 KB | Hourly forecast for a race-day scenario |
| `strade-bianche.gpx` | — | Raw GPX track (drop here after downloading from Komoot) |

---

## Coordinate index — the shared key

`route.json` contains a flat array of 3207 points:

```json
"coordinates": [
  { "lat": 43.3226, "lng": 11.3223, "alt": 344.4, "t": 0 },
  { "lat": 43.3230, "lng": 11.3226, "alt": 344.0, "t": 47894 },
  ...
]
```

`t` is milliseconds elapsed from the start of the tour (Komoot estimated pace).

**Every `from` / `to` field in every file is an index into this array.**

---

## route.json — surface and waytype ranges

Both `surfaces` and `way_types` slice the coordinates array into contiguous
typed runs:

```
coordinates:  [0 ··· 3 | 4 ············· 314 | 315 ··· 319 | 320 ··· 321 | ···]
surfaces:          ?       sb#asphalt              sb#unpaved    sb#ground
way_types:      wt#service  wt#street (from 4)
```

```json
"surfaces": [
  { "from": 4,   "to": 315, "type": "asphalt" },
  { "from": 315, "to": 320, "type": "unpaved" },
  ...
]
```

To look up the surface at coordinate index `i`, find the entry where
`from <= i <= to`.  Ranges are non-overlapping and cover the full track
(gaps at the very start/end fall back to `"unknown"`).

---

## segments.json — sectors referencing the same index space

```json
"named_sectors": [
  {
    "name": "Bagnaia Gravel Sector (Strade Bianche)",
    "from_index": 551,   // ← index into route.json coordinates
    "to_index":   684,
    "km_from_start": 30.9,
    "distance_m": 3333,
    "elevation_up_m": 159,
    "surface": "unpaved"
  }
]
```

`named_sectors` — the 5 highlights Komoot tagged by name (Vridritta,
Bagnaia, Radi, Monte Santa Margherita, Farmhouse Path). Note: Komoot
originally tagged "Monte Santa Maria" to the same coordinate range as
Monte Santa Margherita (indices 1806–2118); the duplicate has been removed.

`gravel_sectors` — 15 auto-detected unpaved/compacted stretches ≥ 500 m,
derived from the surface ranges in `route.json`. Use these for the HUD
"gravel ahead" warnings.

Both arrays share the same `from_index` / `to_index` space as `route.json`,
so slicing `coordinates[from_index..to_index]` gives the sector's geometry.

---

## weather.json — standalone, linked by route_id

```json
{
  "route_id": "strade-bianche-granfondo",
  "date": "2026-03-07",
  "hourly": [
    { "hour": 8, "temp_c": 8, "wind_kph": 16, "condition": "partly_cloudy", ... }
  ]
}
```

Not index-linked to coordinates. The copilot uses `t` (elapsed ms) from the
current rider position to pick the matching `hour` bucket for contextual
advice ("wind picking up after the descent", "rain expected on sector 4").

---

## Relationship diagram

```
route.json
│
├── coordinates[0..3206]   ← canonical position array (lat, lng, alt, t)
│        ▲
│        │  from_index / to_index
├── surfaces[55 ranges]    ← surface type per coordinate slice
├── way_types[31 ranges]   ← road type per coordinate slice
│
segments.json
├── named_sectors[5]       ── from/to index → coordinates slice
└── gravel_sectors[15]     ── from/to index → coordinates slice

weather.json               ── standalone; correlated via rider t + hour bucket
```
