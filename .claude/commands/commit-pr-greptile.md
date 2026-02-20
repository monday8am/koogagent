---
description: Commit changes, push, create PR, and wait for Greptile review with automatic fix application. Polls Greptile up to 7 times (60s intervals) and applies safe suggestions automatically. Use this when completing a feature and want automated code review.
---

# Commit, PR, and Greptile Review Skill

You are an autonomous coding agent that commits code, creates a PR, waits for Greptile review, and applies suggestions.

## Goal

1. Commit and push current changes
2. Create a GitHub PR
3. Poll Greptile for review (up to 7 times, 60s intervals)
4. Apply safe suggestions and update the PR

---

## Step-by-Step Behavior

### Step 0 — Pre-flight Checks

Run these checks first:

```bash
# Confirm we are in a git repo
git rev-parse --show-toplevel

# Check current branch
git branch --show-current

# Confirm gh CLI is authenticated
gh auth status

# Check for staged or unstaged changes
git status --short
```

If there are no changes to commit:
> "No changes detected to commit. Exiting."

If not on a feature branch (e.g., on `main` or `master`):
> "You appear to be on the main branch. Please switch to a feature branch first."

### Step 1 — Commit and Push Changes

1. **Stage all changes** (or confirm already staged):
   ```bash
   git add -A
   ```

2. **Generate commit message**:
   - Review changed files: `git status --short`
   - Review recent changes: `git diff --staged --stat`
   - Create a descriptive commit message following this format:
     ```
     <Short summary (imperative mood)>

     <Detailed bullet points of changes>

     Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
     ```

3. **Commit with proper formatting**:
   ```bash
   git commit -m "$(cat <<'EOF'
   <commit message here>
   EOF
   )"
   ```

4. **Push to remote**:
   ```bash
   git push -u origin <current-branch>
   ```

   If push fails due to hooks, check the error:
   - If it's ktfmt/formatting: run `./gradlew ktfmtFormat`, stage changes, and retry commit
   - If it's other linting: fix the issues, stage, and retry
   - If it's remote rejection: inform user and stop

### Step 2 — Create Pull Request

1. **Check if PR already exists**:
   ```bash
   gh pr view --json number,url,title 2>/dev/null
   ```

2. **If no PR exists**, create one:
   - Extract info from commits: `git log origin/main..HEAD --oneline`
   - Generate PR title (short, imperative, <70 chars)
   - Generate PR body with:
     - Summary section
     - Changes section (bullet points)
     - Technical details if applicable
     - Footer: "🤖 Generated with [Claude Code](https://claude.com/claude-code)"

   ```bash
   gh pr create --title "<title>" --body "<generated body>"
   ```

3. **Record PR number** from output

### Step 3 — Poll Greptile Review

**Constants:**
- `MAX_POLL_ATTEMPTS = 7`
- `POLL_INTERVAL = 60` seconds

**Initial wait**: Sleep 60 seconds after PR creation to allow Greptile to start

**Polling loop** (attempts 1 to MAX_POLL_ATTEMPTS):

1. Call Greptile MCP tool to check for comments:
   ```
   mcp__greptile__list_merge_request_comments(
       name: "owner/repo",
       remote: "github",
       defaultBranch: "main",
       prNumber: <N>,
       greptileGenerated: true
   )
   ```

2. **Check review completion** with:
   ```bash
   gh pr view <N> --comments
   ```
   Look for Greptile summary comment with confidence score.

3. **If review is complete** (has Greptile summary):
   - Extract confidence score (format: "Confidence Score: X/5")
   - If score is 5/5 → proceed to Step 4 with "no issues found"
   - If score is <5 and there are comments → proceed to Step 4 with comments
   - If score is <5 but no actionable comments → inform user and exit

4. **If review not complete yet**:
   - Show progress: "Waiting for Greptile review (attempt N/7)..."
   - Sleep POLL_INTERVAL seconds
   - Continue loop

5. **If MAX_POLL_ATTEMPTS exhausted**:
   - Check one final time for any comments
   - If still no review: inform user "Greptile review did not complete after 7 attempts (7 minutes). PR is open at <url>."
   - Exit

### Step 4 — Apply Greptile Suggestions

**If confidence score is 5/5 and no comments**:
```
✅ Greptile review complete with perfect score (5/5)
No issues found - PR is ready to merge!
PR: <url>
```
Exit successfully.

**If there are Greptile comments**:

For each comment:

1. **Parse comment details**:
   - File path
   - Line number(s)
   - Suggestion text
   - Confidence level (if available)

2. **Display to user**:
   ```
   📝 Greptile suggestion in <file>:<line>
   <suggestion text>
   ```

3. **Read affected code**:
   - Use Read tool to show context around the suggested change

4. **Classify suggestion**:

   **✅ Auto-apply (safe):**
   - Formatting/style fixes
   - Unused imports/variables
   - Simple typos
   - Obvious null checks
   - Missing error handling (simple cases)
   - Naming improvements
   - Simple logic corrections

   **⚠️ Needs confirmation:**
   - Refactoring >20 lines
   - Architectural changes
   - Logic changes that alter behavior
   - Security-sensitive code
   - Database/API changes
   - Deletions of >10 lines
   - File renames/moves

5. **For safe suggestions**:
   - Apply the fix using Edit tool
   - Add to batch of changes
   - Continue to next comment

6. **For risky suggestions**:
   - Show the proposed change clearly
   - Ask user: "Apply this change? (yes/no/skip)"
   - Wait for response before proceeding

### Step 5 — Test and Commit Fixes

After processing all (or a batch of) suggestions:

1. **Run project tests/checks**:
   - Auto-detect and run: `./gradlew test` or `./gradlew check` or `npm test` or `pytest`
   - If no test command obvious, ask user

2. **If tests pass** ✅:
   ```bash
   git add -A
   git commit -m "Apply Greptile review suggestions

   - <list of fixes applied>

   Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
   git push
   ```

3. **If tests fail** ❌:
   - Show failure output
   - Ask user: "Tests failed after applying Greptile suggestions. Options:
     1. Fix manually
     2. Revert last commit
     3. Skip tests and commit anyway
     4. Abort

     What would you like to do?"
   - Wait for user choice

4. **Update PR** after successful push:
   - Comment on PR: "Applied Greptile review suggestions ✅"

---

## Safety Rules

- **Never auto-apply** changes to:
  - Security-sensitive code (auth, crypto, permissions)
  - Database migrations
  - API contracts
  - Build/deployment configs
  - Files with >50 line changes

- **Always verify** with tests before pushing

- **Commit in batches**: If >5 suggestions, group by file/concern

- **Stop if tests fail twice** - ask user instead of retrying

- **Preserve code formatting**: Run ktfmtFormat if project uses it

---

## Error Handling

| Situation | Action |
|-----------|--------|
| No changes to commit | Exit early with message |
| Push rejected by hooks | Fix issues, retry once, then ask user |
| PR creation fails | Show error, ask user to resolve |
| Greptile not available | Inform user to enable Greptile plugin |
| Review timeout (7 attempts) | Report timeout, provide PR URL |
| Merge conflicts on push | Do not force-push, ask user to resolve |
| Can't parse Greptile comment | Skip it, inform user |
| Ambiguous fix suggestion | Ask user for clarification |

---

## Final Summary

After completing, show:

```
## Greptile PR Review Complete

**PR**: #<N> — <title>
**URL**: <pr-url>
**Branch**: <branch-name>
**Confidence Score**: <X>/5

### ✅ Changes Applied
- [file:line] <fix description>
- ...

### ⚠️ Skipped (needed confirmation)
- [file:line] <suggestion> — <reason>

### ❌ Not Addressed
- [file:line] <comment> — <reason>

### Commits
- <sha> — "Initial feature commit"
- <sha> — "Apply Greptile review suggestions"

### Test Results
- Status: <passed/failed/skipped>
- Command: <test command used>

**Next Steps**: <merge PR / address remaining issues / etc>
```

---

## Usage Examples

**Scenario 1**: Feature complete, all tests passing
```
User: /commit-pr-greptile
→ Commits changes, creates PR, waits for Greptile
→ Greptile gives 5/5, no comments
→ Reports success, PR ready to merge
```

**Scenario 2**: Feature complete, Greptile finds style issues
```
User: /commit-pr-greptile
→ Commits changes, creates PR, waits for Greptile
→ Greptile finds 3 formatting issues
→ Auto-applies all 3 fixes
→ Runs tests (pass), commits fixes, pushes
→ Reports success with applied changes
```

**Scenario 3**: Feature complete, Greptile finds risky refactor
```
User: /commit-pr-greptile
→ Commits changes, creates PR, waits for Greptile
→ Greptile suggests large refactor
→ Shows suggestion, asks user
→ User declines
→ Reports success, notes skipped suggestion
```

---

## Integration with Project Hooks

This skill respects pre-commit hooks:
- If `ktfmt` check fails → auto-runs `ktfmtFormat` and retries
- If other hooks fail → shows error and asks user
- Preserves all existing Git workflow safeguards
