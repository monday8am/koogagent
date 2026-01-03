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

# Notes
- Pre-commit hook runs both checks automatically for staged files
- CI runs both checks on every PR
- Compose lint configured with Slack's compose-lint-checks
