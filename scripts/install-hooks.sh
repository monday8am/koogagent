#!/bin/sh

# Install git hooks for the project
# Run this once after cloning: ./scripts/install-hooks.sh

HOOKS_DIR=".git/hooks"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Installing git hooks..."

# Create pre-commit hook
cat > "$HOOKS_DIR/pre-commit" << 'EOF'
#!/bin/sh

# Pre-commit hook that runs ktfmt via Gradle
# This ensures consistency between local checks and CI

echo "Running ktfmt check..."

# Get the list of staged Kotlin files
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(kt|kts)$')

if [ -z "$STAGED_FILES" ]; then
    echo "No Kotlin files staged, skipping ktfmt"
    exit 0
fi

# Run ktfmt check via Gradle
./gradlew ktfmtCheck --daemon -q

RESULT=$?

if [ $RESULT -ne 0 ]; then
    echo ""
    echo "❌ ktfmt check failed!"
    echo ""
    echo "Run './gradlew ktfmtFormat' to auto-fix issues"
    echo "Then stage the fixed files and commit again"
    exit 1
fi

echo "✅ ktfmt check passed"
exit 0
EOF

chmod +x "$HOOKS_DIR/pre-commit"

echo "✅ Git hooks installed successfully"
echo ""
echo "Available hooks:"
echo "  - pre-commit: Runs ktfmt check before each commit"
