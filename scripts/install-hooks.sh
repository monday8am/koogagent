#!/bin/sh

# Install git hooks for the project
# Run this once after cloning: ./scripts/install-hooks.sh

HOOKS_DIR=".git/hooks"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Installing git hooks..."

# Create pre-commit hook
cat > "$HOOKS_DIR/pre-commit" << 'EOF'
#!/bin/sh

# Pre-commit hook that runs ktlint via Gradle (uses .editorconfig)
# This ensures consistency between local checks and CI

echo "Running ktlint check..."

# Get the list of staged Kotlin files
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(kt|kts)$')

if [ -z "$STAGED_FILES" ]; then
    echo "No Kotlin files staged, skipping ktlint"
    exit 0
fi

# Run ktlint check via Gradle
./gradlew ktlintCheck --daemon -q

RESULT=$?

if [ $RESULT -ne 0 ]; then
    echo ""
    echo "❌ ktlint check failed!"
    echo ""
    echo "Run './gradlew ktlintFormat' to auto-fix issues"
    echo "Then stage the fixed files and commit again"
    exit 1
fi

echo "✅ ktlint check passed"
exit 0
EOF

chmod +x "$HOOKS_DIR/pre-commit"

echo "✅ Git hooks installed successfully"
echo ""
echo "Available hooks:"
echo "  - pre-commit: Runs ktlint check before each commit"
