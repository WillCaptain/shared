#!/usr/bin/env bash
# Shared installer: symlink portable skills/aipp-development into a harness skills dir.
# Usage: install-core.sh <harness-skills-dir>
set -euo pipefail

HARNESS_DIR="${1:-}"
if [[ -z "$HARNESS_DIR" ]]; then
  echo "usage: install-core.sh <harness-skills-dir>" >&2
  exit 2
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE="$(cd "$HERE/../../aipp-development" && pwd)"
TARGET="$HARNESS_DIR/aipp-development"

if [[ ! -f "$CORE/SKILL.md" ]]; then
  echo "error: core skill missing at $CORE/SKILL.md" >&2
  exit 1
fi

mkdir -p "$HARNESS_DIR"
ln -sfn "$CORE" "$TARGET"
echo "Installed: $TARGET -> $CORE"
ls -la "$TARGET"
