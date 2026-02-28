#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["requests"]
# ///
"""
Generate route alternatives for the Cycling Copilot simulation.

Reads route.json and segments.json, picks diverge/rejoin points at interesting
segments, calls OpenRouteService to generate real cycling alternatives, and
outputs route-alternatives.json in the same coordinate/index format.

Usage:
    # Set your free API key from https://openrouteservice.org/dev/#/signup
    export ORS_API_KEY="your-key-here"

    uv run generate_alternatives.py \
        --route route.json \
        --segments segments.json \
        --output route-alternatives.json

OpenRouteService free tier: 40 requests/min, 2000/day — more than enough.
"""

import argparse
import json
import math
import os
import time
from dataclasses import dataclass
from typing import Optional

import requests

ORS_BASE_TMPL = "https://api.openrouteservice.org/v2/directions/{profile}"
ORS_KEY = os.environ.get("ORS_API_KEY", "")

# ─── Data structures ───

@dataclass
class RoutePoint:
    lat: float
    lng: float
    alt: float
    t: int  # ms from start
    index: int


@dataclass
class AlternativeScenario:
    """Defines where and why to generate an alternative."""
    query_value: str          # matches get_route_alternatives query param
    label: str                # human-readable name
    diverge_index: int        # coordinate index where alt splits from main route
    rejoin_index: int         # coordinate index where alt rejoins main route
    avoid_features: list      # ORS avoid_features (e.g., ["unpaved"])
    preference: str           # ORS preference: "fastest", "shortest", "recommended"
    ors_profile: str          # ORS profile: "cycling-regular" or "cycling-road"
    description: str          # why this alternative exists
    extra_waypoints: list     # optional intermediate waypoints to guide the route


# ─── Load route data ───

def load_route(path: str) -> list[RoutePoint]:
    with open(path) as f:
        data = json.load(f)
    coords = data["coordinates"]
    return [RoutePoint(lat=c["lat"], lng=c["lng"], alt=c["alt"], t=c["t"], index=i)
            for i, c in enumerate(coords)]


def load_segments(path: str) -> dict:
    with open(path) as f:
        return json.load(f)


# ─── Analyze route to pick diverge/rejoin points ───

def precompute_cum_km(points: list[RoutePoint]) -> list[float]:
    """Precompute cumulative km from start for every index (O(n) once)."""
    cum = [0.0] * len(points)
    for i in range(1, len(points)):
        cum[i] = cum[i - 1] + haversine(
            points[i - 1].lat, points[i - 1].lng,
            points[i].lat, points[i].lng,
        )
    return cum


def haversine(lat1, lon1, lat2, lon2) -> float:
    """Distance in km between two points."""
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * \
        math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2
    return R * 2 * math.asin(math.sqrt(a))


def find_climb_segments(points: list[RoutePoint], min_gain_m: float = 80,
                        window: int = 50) -> list[tuple[int, int, float]]:
    """Find segments with sustained climbing. Returns (start_idx, end_idx, gain)."""
    climbs = []
    i = 0
    while i < len(points) - window:
        gain = 0.0
        j = i
        while j < len(points) - 1:
            delta = points[j + 1].alt - points[j].alt
            if delta > 0:
                gain += delta
            else:
                # Allow small dips (up to 10m) within a climb
                if abs(delta) > 10:
                    break
            j += 1
        if gain >= min_gain_m and (j - i) >= window:  # enforce minimum climb length
            climbs.append((i, j, gain))
            i = j + 1
        else:
            i += 1
    return climbs


def find_gravel_sectors(segments: dict) -> list[dict]:
    """Extract gravel sectors from segments.json."""
    named = segments.get("named_sectors", [])
    gravel = segments.get("gravel_sectors", [])
    # Combine and sort by from_index
    all_sectors = sorted(named + gravel, key=lambda s: s["from_index"])
    return all_sectors


def pick_scenarios(points: list[RoutePoint], segments: dict) -> list[AlternativeScenario]:
    """
    Analyze route data and pick 5 diverge/rejoin points for alternatives.
    Strategy: use actual route features to pick meaningful points.
    """
    gravel_sectors = find_gravel_sectors(segments)
    climbs = find_climb_segments(points, min_gain_m=100)
    total_points = len(points)

    scenarios = []

    # ── 1. FLATTER: before the biggest climb ──
    if climbs:
        biggest = max(climbs, key=lambda c: c[2])
        # Diverge 200 points before climb, rejoin 200 points after
        diverge = max(0, biggest[0] - 200)
        rejoin = min(total_points - 1, biggest[1] + 200)
        scenarios.append(AlternativeScenario(
            query_value="flatter",
            label="Valley bypass",
            diverge_index=diverge,
            rejoin_index=rejoin,
            avoid_features=[],
            preference="recommended",
            ors_profile="cycling-regular",
            description=f"Avoids the biggest climb ({biggest[2]:.0f}m gain). "
                        f"Routes through the valley on lower-gradient roads.",
            extra_waypoints=[],
        ))

    # ── 2. SHORTER: skip a mid-route loop ──
    # Find a section in the middle third where the route doubles back
    mid_start = total_points // 3
    mid_end = 2 * total_points // 3
    # Pick two points ~20km apart (in index space) that are geographically close
    best_shortcut = None
    best_dist = float("inf")
    step = 100  # check every 100th point for efficiency
    for i in range(mid_start, mid_end, step):
        for j in range(i + 400, min(i + 1000, mid_end), step):
            d = haversine(points[i].lat, points[i].lng, points[j].lat, points[j].lng)
            if d < best_dist and d < 3.0:  # points within 3km of each other
                best_dist = d
                best_shortcut = (i, j)

    if best_shortcut:
        scenarios.append(AlternativeScenario(
            query_value="shorter",
            label="Direct shortcut",
            diverge_index=best_shortcut[0],
            rejoin_index=best_shortcut[1],
            avoid_features=[],
            preference="shortest",
            ors_profile="cycling-regular",
            description=f"Cuts across the loop where the route doubles back. "
                        f"Saves distance at the cost of skipping mid-route sectors.",
            extra_waypoints=[],
        ))
    else:
        # Fallback: just cut 25% off the middle
        scenarios.append(AlternativeScenario(
            query_value="shorter",
            label="Direct shortcut",
            diverge_index=mid_start,
            rejoin_index=mid_end,
            avoid_features=[],
            preference="shortest",
            ors_profile="cycling-regular",
            description="Takes the most direct cycling route between these points.",
            extra_waypoints=[],
        ))

    # ── 3. PAVED: before the longest gravel sector ──
    if gravel_sectors:
        longest_gravel = max(gravel_sectors, key=lambda s: s.get("distance_m", 0))
        # Diverge 150 points before gravel, rejoin 150 after
        diverge = max(0, longest_gravel["from_index"] - 150)
        rejoin = min(total_points - 1, longest_gravel["to_index"] + 150)
        scenarios.append(AlternativeScenario(
            query_value="paved",
            label="Paved bypass",
            diverge_index=diverge,
            rejoin_index=rejoin,
            avoid_features=[],          # cycling-road profile inherently prefers paved
            preference="recommended",
            ors_profile="cycling-road", # road profile routes on paved surfaces
            description=f"Avoids the {longest_gravel.get('name', 'gravel')} sector "
                        f"({longest_gravel.get('distance_m', 0) / 1000:.1f}km unpaved). "
                        f"Uses parallel paved roads.",
            extra_waypoints=[],
        ))

    # ── 4. SCENIC: find a main road section and route through nearby villages ──
    # Look for the longest "street" waytype stretch (the boring bits)
    # For now, pick a section in the first third with low elevation variation
    first_third_end = total_points // 3
    flattest_start = 0
    flattest_var = float("inf")
    window = 300
    for i in range(0, first_third_end - window, 50):
        alts = [points[j].alt for j in range(i, i + window)]
        var = max(alts) - min(alts)
        if var < flattest_var:
            flattest_var = var
            flattest_start = i
    scenarios.append(AlternativeScenario(
        query_value="scenic",
        label="Hilltop village detour",
        diverge_index=max(50, flattest_start),  # clamp: avoid starting at index 0
        rejoin_index=min(total_points - 1, flattest_start + window),
        avoid_features=[],
        preference="recommended",
        ors_profile="cycling-regular",
        description="Detours through hilltop villages and viewpoints "
                    "instead of the main road section.",
        extra_waypoints=[],  # Could add a known scenic waypoint here
    ))

    # ── 5. SHELTERED: find the highest/most exposed section ──
    # Highest point = most wind-exposed. Route through lower terrain.
    highest_idx = max(range(len(points)), key=lambda i: points[i].alt)
    # Find a window around the highest point
    diverge = max(0, highest_idx - 300)
    rejoin = min(total_points - 1, highest_idx + 300)
    scenarios.append(AlternativeScenario(
        query_value="sheltered",
        label="Valley shelter route",
        diverge_index=diverge,
        rejoin_index=rejoin,
        avoid_features=[],
        preference="recommended",
        ors_profile="cycling-regular",
        description="Routes through the lower valley to avoid the exposed "
                    "ridge section. Less wind, more tree cover.",
        extra_waypoints=[],
    ))

    return scenarios


# ─── Call OpenRouteService ───

def call_ors(start: RoutePoint, end: RoutePoint,
             scenario: AlternativeScenario,
             api_key: str) -> Optional[dict]:
    """
    Call ORS directions API to get a cycling route between two points.
    Returns the decoded route with coordinates and metadata.
    """
    if not api_key:
        print("  ⚠ No ORS_API_KEY set — generating mock alternative")
        return None

    # ORS expects [lng, lat] order
    coordinates = [[start.lng, start.lat]]

    # Add extra waypoints if any
    for wp in scenario.extra_waypoints:
        coordinates.append([wp[1], wp[0]])  # [lng, lat]

    coordinates.append([end.lng, end.lat])

    payload = {
        "coordinates": coordinates,
        "preference": scenario.preference,
        "geometry": True,
        "format": "geojson",
        "elevation": True,
        "instructions": False,
        "extra_info": ["surface", "waytypes"],
    }

    if scenario.avoid_features:
        payload["options"] = {"avoid_features": scenario.avoid_features}

    headers = {
        "Authorization": api_key,
        "Content-Type": "application/json",
    }

    url = ORS_BASE_TMPL.format(profile=scenario.ors_profile)

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=30)
        resp.raise_for_status()
        data = resp.json()

        if not data.get("features"):
            print(f"  ⚠ No route found for {scenario.query_value}")
            return None

        feature = data["features"][0]
        geometry = feature["geometry"]["coordinates"]  # [[lng, lat, alt], ...]
        properties = feature["properties"]
        summary = properties.get("summary", {})

        return {
            "coordinates": [
                {"lat": round(c[1], 6), "lng": round(c[0], 6),
                 "alt": round(c[2], 1) if len(c) > 2 else 0.0}
                for c in geometry
            ],
            "distance_m": round(summary.get("distance", 0)),
            "duration_s": round(summary.get("duration", 0)),
            "ascent_m": round(properties.get("ascent", 0)),
            "descent_m": round(properties.get("descent", 0)),
            "surface_info": properties.get("extras", {}).get("surface", {}),
            "waytype_info": properties.get("extras", {}).get("waytypes", {}),
        }

    except requests.exceptions.RequestException as e:
        print(f"  ✗ ORS API error for {scenario.query_value}: {e}")
        return None


def generate_mock_alternative(points: list[RoutePoint],
                              scenario: AlternativeScenario) -> dict:
    """
    Generate a geometric mock alternative when ORS is unavailable.
    Offsets coordinates laterally to create a visually distinct route.
    """
    diverge = points[scenario.diverge_index]
    rejoin = points[scenario.rejoin_index]

    # Calculate perpendicular offset direction
    dlat = rejoin.lat - diverge.lat
    dlng = rejoin.lng - diverge.lng
    length = math.sqrt(dlat ** 2 + dlng ** 2)
    if length == 0:
        return {"coordinates": [], "distance_m": 0, "mock": True}

    # Perpendicular unit vector (rotate 90°)
    perp_lat = -dlng / length
    perp_lng = dlat / length

    # Offset magnitude (~1-3km depending on scenario)
    offset_km = 0.015  # roughly 1.5km in degrees
    if scenario.query_value == "shorter":
        offset_km = 0.005

    # Generate arc: diverge → offset midpoint → rejoin
    n_points = 30
    coords = []
    for i in range(n_points + 1):
        frac = i / n_points
        # Linear interpolation + sinusoidal offset
        lat = diverge.lat + frac * (rejoin.lat - diverge.lat) + \
              math.sin(frac * math.pi) * perp_lat * offset_km
        lng = diverge.lng + frac * (rejoin.lng - diverge.lng) + \
              math.sin(frac * math.pi) * perp_lng * offset_km
        alt = diverge.alt + frac * (rejoin.alt - diverge.alt)

        # Flatten altitude for "flatter" and "sheltered"
        if scenario.query_value in ("flatter", "sheltered"):
            mid_alt = (diverge.alt + rejoin.alt) / 2
            alt = mid_alt + (alt - mid_alt) * 0.3

        coords.append({"lat": round(lat, 6), "lng": round(lng, 6),
                        "alt": round(alt, 1)})

    original_segment = points[scenario.diverge_index:scenario.rejoin_index + 1]
    orig_distance = sum(
        haversine(original_segment[i].lat, original_segment[i].lng,
                  original_segment[i + 1].lat, original_segment[i + 1].lng)
        for i in range(len(original_segment) - 1)
    )

    multiplier = {"shorter": 0.7, "flatter": 1.15, "paved": 1.05,
                  "scenic": 1.2, "sheltered": 1.1}
    alt_distance = orig_distance * multiplier.get(scenario.query_value, 1.0)

    return {
        "coordinates": coords,
        "distance_m": round(alt_distance * 1000),
        "duration_s": round(alt_distance * 1000 / 5.5),  # ~20km/h
        "ascent_m": 0,
        "descent_m": 0,
        "mock": True,
    }


# ─── Build output ───

def build_alternative_entry(scenario: AlternativeScenario,
                            route_data: dict,
                            points: list[RoutePoint],
                            cum_km: list[float]) -> dict:
    """Build one entry for route-alternatives.json."""
    diverge = points[scenario.diverge_index]
    rejoin = points[scenario.rejoin_index]

    # Original segment stats for comparison
    orig_segment = points[scenario.diverge_index:scenario.rejoin_index + 1]
    orig_distance = sum(
        haversine(orig_segment[i].lat, orig_segment[i].lng,
                  orig_segment[i + 1].lat, orig_segment[i + 1].lng)
        for i in range(len(orig_segment) - 1)
    )
    orig_ascent = sum(
        max(0, orig_segment[i + 1].alt - orig_segment[i].alt)
        for i in range(len(orig_segment) - 1)
    )

    # Original segment duration from Komoot t values (ms → s)
    orig_duration_s = (orig_segment[-1].t - orig_segment[0].t) // 1000

    # Use precomputed cumulative km (O(1) lookup)
    km_diverge = cum_km[scenario.diverge_index]
    km_rejoin = cum_km[scenario.rejoin_index]

    alt_entry: dict = {
        "coordinates": route_data["coordinates"],
        "distance_m": route_data["distance_m"],
        "duration_s": route_data.get("duration_s", 0),
        "ascent_m": route_data.get("ascent_m", 0),
        "descent_m": route_data.get("descent_m", 0),
        "is_mock": route_data.get("mock", False),
    }
    # Include surface/waytype data when available (ORS routes only)
    if route_data.get("surface_info"):
        alt_entry["surface_info"] = route_data["surface_info"]
    if route_data.get("waytype_info"):
        alt_entry["waytype_info"] = route_data["waytype_info"]

    return {
        "query_value": scenario.query_value,
        "label": scenario.label,
        "description": scenario.description,
        "diverge": {
            "index": scenario.diverge_index,
            "lat": diverge.lat,
            "lng": diverge.lng,
            "km_from_start": round(km_diverge, 1),
        },
        "rejoin": {
            "index": scenario.rejoin_index,
            "lat": rejoin.lat,
            "lng": rejoin.lng,
            "km_from_start": round(km_rejoin, 1),
        },
        "original_segment": {
            "distance_m": round(orig_distance * 1000),
            "ascent_m": round(orig_ascent),
        },
        "alternative": alt_entry,
        "comparison": {
            "distance_delta_m": route_data["distance_m"] - round(orig_distance * 1000),
            "ascent_delta_m": route_data.get("ascent_m", 0) - round(orig_ascent),
            "time_delta_s": route_data.get("duration_s", 0) - orig_duration_s,
        },
    }


# ─── Main ───

def main():
    parser = argparse.ArgumentParser(description="Generate route alternatives")
    parser.add_argument("--route", required=True, help="Path to route.json")
    parser.add_argument("--segments", required=True, help="Path to segments.json")
    parser.add_argument("--output", default="route-alternatives.json",
                        help="Output file path")
    parser.add_argument("--mock-only", action="store_true",
                        help="Skip ORS API, generate geometric mocks only")
    args = parser.parse_args()

    api_key = ORS_KEY
    if not api_key and not args.mock_only:
        print("⚠ ORS_API_KEY not set. Use --mock-only or export ORS_API_KEY=...")
        print("  Get a free key at: https://openrouteservice.org/dev/#/signup")
        print("  Falling back to mock generation.\n")
        args.mock_only = True

    # Load data
    print(f"Loading route from {args.route}...")
    points = load_route(args.route)
    cum_km = precompute_cum_km(points)
    print(f"  {len(points)} points, {cum_km[-1]:.1f}km total")

    print(f"Loading segments from {args.segments}...")
    segments = load_segments(args.segments)
    named = segments.get("named_sectors", [])
    gravel = segments.get("gravel_sectors", [])
    print(f"  {len(named)} named sectors, {len(gravel)} gravel sectors")

    # Analyze and pick scenarios
    print("\nAnalyzing route for alternative scenarios...")
    scenarios = pick_scenarios(points, segments)

    print(f"\nGenerated {len(scenarios)} scenarios:")
    for s in scenarios:
        km_d = cum_km[s.diverge_index]
        km_r = cum_km[s.rejoin_index]
        print(f"  {s.query_value:12s} │ {s.label:30s} │ km {km_d:.0f}→{km_r:.0f} "
              f"│ idx {s.diverge_index}→{s.rejoin_index}")

    # Generate alternatives
    print("\nGenerating alternatives...")
    alternatives = []

    for scenario in scenarios:
        print(f"\n  [{scenario.query_value}] {scenario.label}")
        diverge_pt = points[scenario.diverge_index]
        rejoin_pt = points[scenario.rejoin_index]

        if args.mock_only:
            route_data = generate_mock_alternative(points, scenario)
            source = "mock"
        else:
            route_data = call_ors(diverge_pt, rejoin_pt, scenario, api_key)
            if route_data is None:
                route_data = generate_mock_alternative(points, scenario)
                source = "mock (fallback)"
            else:
                source = "ORS"
                time.sleep(1.5)  # Rate limit only on successful ORS call

        entry = build_alternative_entry(scenario, route_data, points, cum_km)
        alternatives.append(entry)

        dist = route_data["distance_m"]
        print(f"    Source: {source}")
        print(f"    Alt distance: {dist/1000:.1f}km "
              f"(original segment: {entry['original_segment']['distance_m']/1000:.1f}km)")
        print(f"    Delta: {entry['comparison']['distance_delta_m']/1000:+.1f}km")

    # Write output
    output = {
        "route_id": "strade-bianche-granfondo",
        "generated_from": "OpenRouteService" if not args.mock_only else "geometric mock",
        "alternatives": alternatives,
    }

    with open(args.output, "w") as f:
        json.dump(output, f, indent=2)

    print(f"\n✓ Wrote {len(alternatives)} alternatives to {args.output}")
    print("\nOutput structure:")
    print("  route-alternatives.json")
    for alt in alternatives:
        n_coords = len(alt["alternative"]["coordinates"])
        print(f"    ├─ {alt['query_value']:12s} │ {n_coords:4d} points "
              f"│ {alt['alternative']['distance_m']/1000:.1f}km "
              f"│ diverge km {alt['diverge']['km_from_start']}")


if __name__ == "__main__":
    main()
