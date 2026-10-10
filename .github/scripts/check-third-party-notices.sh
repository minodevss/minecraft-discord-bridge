#!/usr/bin/env bash
set -euo pipefail

bundled=$(./gradlew -q :core:dependencies --configuration runtimeClasspath \
  | sed -nE 's/^[| ]*[+\\]--- ([^ ]+)( -> [^ ]+)?$/\1/p' \
  | cut -d: -f1,2 \
  | grep -vE '^(com\.google\.code\.gson|org\.slf4j|org\.jspecify):|^org\.jetbrains:annotations$|-bom$' \
  | sort -u)

listed=$(grep -oE '^(  Maven: +| {13})[a-z][a-z0-9.]*:[A-Za-z0-9.-]+' THIRD-PARTY-NOTICES \
  | sed -E 's/^ +(Maven: +)?//' \
  | grep -v '^me\.lucko:fabric-permissions-api$' \
  | sort -u)

if [[ "$bundled" != "$listed" ]]; then
  echo "::error file=THIRD-PARTY-NOTICES::The bundled libraries changed. Update THIRD-PARTY-NOTICES with their licenses and copyright notices."
  diff <(echo "$listed") <(echo "$bundled") | sed -n 's/^</  listed, not bundled:/p; s/^>/  bundled, not listed:/p'
  exit 1
fi
echo "THIRD-PARTY-NOTICES lists all $(wc -l <<< "$bundled" | tr -d ' ') bundled libraries."
