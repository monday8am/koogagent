---
description: Run code quality checks (ktlint and Compose lint)
---
// turbo-all
1. Run ktlint check
   ./gradlew ktlintCheck

2. Run Compose lint check
   ./gradlew :app:lintDebug

3. Auto-fix ktlint issues (if needed)
   ./gradlew ktlintFormat

# Lint Reports
- ktlint: Console output
- Compose lint: app/build/reports/lint-results-debug.html

# Git Hooks
- Pre-commit: Runs ktlint check (fast, runs on every commit)
- Pre-push: Runs Compose lint check (slower, runs before push)
- Skip with: git push --no-verify (not recommended)

# Notes
- CI runs both checks on every PR
- Compose lint configured with Slack's compose-lint-checks
- Pre-push hook only runs if app module has changes
