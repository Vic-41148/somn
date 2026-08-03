#!/usr/bin/env bash
# verify-trends.sh — navigate to Trends and dump what renders (metric chips, chart
# title, cycle legend if applicable). Informational; no assertions.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

wake_and_unlock || exit 1
launch_app
goto_trends || exit 1

DUMP=$(ui_dump)
echo "== Trends screen texts =="
ui_texts "$DUMP" | head -30
