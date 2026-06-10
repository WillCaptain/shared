#!/usr/bin/env bash
# Fail if duplicate GCP type-display rules appear outside MetaExtractor.formatType.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PATTERN='replace\("INTEGER"'
ALLOW="$ROOT/outline/outline/src/main/java/org/twelve/outline/meta/MetaExtractor.java"
VIOLATIONS=0

while IFS= read -r f; do
  [[ "$f" == "$ALLOW" ]] && continue
  if grep -q "$PATTERN" "$f"; then
    echo "contract violation: $PATTERN in $f (use MetaExtractor.formatType)"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done < <(find "$ROOT" \( -name '*.java' -o -name '*.js' \) \
  ! -path '*/target/*' ! -path '*/node_modules/*' ! -path '*/.git/*' 2>/dev/null)

if [[ "$VIOLATIONS" -gt 0 ]]; then
  echo "$VIOLATIONS duplicate type-format rule(s) found."
  exit 1
fi
echo "editor contract OK (no stray INTEGER→Int rules)"
