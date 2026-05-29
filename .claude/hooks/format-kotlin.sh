#!/usr/bin/env bash
# .claude/hooks/format-kotlin.sh
#
# PostToolUse hook for Write/Edit/MultiEdit.
# Reads tool input JSON from stdin, extracts the edited file_path,
# and runs `./gradlew spotlessApply` scoped to that file if it is a Kotlin source.
#
# Exit codes:
#   0  - success (file formatted, or non-kotlin file skipped)
#   2  - blocking error reported back to Claude (Gradle unavailable / Spotless fatal)

set -euo pipefail

# Read entire stdin (Claude Code sends a single JSON object).
INPUT="$(cat)"

# Extract the file path Claude just touched.
FILE_PATH="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')"

# No file path? Nothing to format.
if [[ -z "$FILE_PATH" ]]; then
    exit 0
fi

# Only act on Kotlin sources.
case "$FILE_PATH" in
    *.kt|*.kts) ;;
    *) exit 0 ;;
esac

# Resolve project root (hook runs with CWD = project root, but be explicit).
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# Run Spotless scoped to the edited file via -PspotlessIdeHook=<absolute>.
# If the project's convention plugin does not support this flag, switch to
# plain `spotlessApply` (slower but always works).
cd "$PROJECT_DIR"

ERR_LOG="$(mktemp -t mindstock-format-kotlin.XXXXXX.err)"
trap 'rm -f "$ERR_LOG"' EXIT

if ! ./gradlew \
        -PspotlessIdeHook="$FILE_PATH" \
        spotlessApply \
        --quiet \
        --console=plain \
        2>"$ERR_LOG"
then
    {
        echo "spotlessApply failed for $FILE_PATH"
        echo "--- gradle output ---"
        cat "$ERR_LOG"
    } >&2
    exit 2
fi

exit 0
