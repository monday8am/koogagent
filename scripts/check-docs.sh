#!/usr/bin/env bash
# Lightweight doc validation for CI. Catches common drift issues.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ERROR_LOG=$(mktemp)

echo "=== Doc Validation ==="

# 1. Check markdown links in docs/ and CLAUDE.md resolve to existing files
echo ""
echo "--- Checking markdown links ---"
check_links() {
    local file="$1"
    local dir
    dir="$(dirname "$file")"

    # Extract markdown links: [text](path) — skip http/https URLs and anchors
    local links
    links=$(grep -oE '\[.*?\]\([^)]+\)' "$file" 2>/dev/null | \
        grep -oE '\(([^)]+)\)' | tr -d '()' | \
        grep -v '^http' | grep -v '^#' || true)

    for link in $links; do
        # Strip anchor from link
        local path="${link%%#*}"
        [ -z "$path" ] && continue

        # Resolve relative to file's directory
        if [ ! -f "$dir/$path" ] && [ ! -f "$ROOT/$path" ]; then
            echo "  BROKEN: $file -> $path"
            echo "1" >> "$ERROR_LOG"
        fi
    done
}

for f in "$ROOT"/CLAUDE.md "$ROOT"/docs/*.md "$ROOT"/presentation/CLAUDE.md; do
    [ -f "$f" ] && check_links "$f"
done
# Check subdirs of docs/
for f in "$ROOT"/docs/edgelab/*.md "$ROOT"/docs/cyclingcopilot/*.md; do
    [ -f "$f" ] && check_links "$f"
done

# 2. Check modules in settings.gradle.kts are documented in architecture.md
echo ""
echo "--- Checking module sync ---"
SETTINGS="$ROOT/settings.gradle.kts"
ARCH="$ROOT/docs/architecture.md"

if [ -f "$SETTINGS" ] && [ -f "$ARCH" ]; then
    # Extract module names from include() lines
    modules=$(grep 'include(' "$SETTINGS" | sed 's/.*include("\(.*\)").*/\1/' || true)
    for module in $modules; do
        if ! grep -q "$module" "$ARCH"; then
            echo "  MISSING in architecture.md: $module"
            echo "1" >> "$ERROR_LOG"
        fi
    done
else
    echo "  SKIP: settings.gradle.kts or architecture.md not found"
fi

# 3. Check key classes referenced in docs actually exist in source
echo ""
echo "--- Checking key class existence ---"
KEY_CLASSES=(
    "CoreDependencies"
    "LocalInferenceEngine"
    "OnboardViewModel"
    "ModelDownloadManager"
    "ModelRepository"
    "AuthRepository"
)

for cls in "${KEY_CLASSES[@]}"; do
    if ! grep -rqE "(class|interface|object) $cls" \
        "$ROOT/data/src" "$ROOT/agent/src" "$ROOT/presentation/src" "$ROOT/core/src" 2>/dev/null; then
        echo "  MISSING class: $cls (referenced in docs but not found in source)"
        echo "1" >> "$ERROR_LOG"
    fi
done

# Summary
echo ""
ERRORS=$(wc -l < "$ERROR_LOG" 2>/dev/null | tr -d ' ')
rm -f "$ERROR_LOG"

if [ "$ERRORS" -gt 0 ]; then
    echo "FAIL: $ERRORS doc validation error(s) found"
    exit 1
else
    echo "PASS: All doc checks passed"
fi
