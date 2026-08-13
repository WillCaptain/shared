#!/usr/bin/env bash
# Install portable aipp-development into one or more harness skill directories.
# Usage: ./install.sh [cursor|claude|all]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-all}"

install_cursor() {
  bash "$HERE/adapters/aipp-skill-cursor/install.sh"
}

install_claude() {
  bash "$HERE/adapters/aipp-skill-claude/install.sh"
}

case "$TARGET" in
  cursor) install_cursor ;;
  claude) install_claude ;;
  all)
    install_cursor
    install_claude
    ;;
  *)
    echo "usage: $0 [cursor|claude|all]" >&2
    exit 2
    ;;
esac
