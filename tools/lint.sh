#!/usr/bin/env bash
# Kotlin style (ktlint) and static analysis (detekt). The tools are downloaded once and checked against the
# checksums below, so a tampered download is refused. Needs Java 17+ and curl.
#
#   tools/lint.sh           check; fails on a style problem or a detekt finding that isn't in the baseline
#   tools/lint.sh format    fix what ktlint can fix, then check
#
# Rules are in .editorconfig (ktlint) and config/detekt/detekt.yml. detekt's older findings are listed in
# config/detekt/baseline.xml, so only new ones fail; after fixing some, run
#   java -jar <detekt jar> --build-upon-default-config -c config/detekt/detekt.yml \
#     -i app/src/main/java,app/src/test/java --create-baseline --baseline config/detekt/baseline.xml
set -euo pipefail
cd "$(dirname "$0")/.."

KTLINT_VERSION=1.8.0
KTLINT_SHA256=369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab
DETEKT_VERSION=1.23.8
DETEKT_SHA256=3afe89a11120303c73c9bdda3d8fe558dd9070a6937d27819ddc04b275381245
CACHE="${LINT_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/drivesanity-lint}"
CENTRAL=https://repo1.maven.org/maven2

fetch() { # url, file, sha256
  local url=$1 file=$2 sha=$3
  if [ -f "$file" ] && echo "$sha  $file" | sha256sum -c --status -; then return; fi
  mkdir -p "$(dirname "$file")"
  echo "Downloading $(basename "$file")…" >&2
  # Maven Central sometimes answers 429 to busy networks; wait and try again.
  curl -fsSL --retry 6 --retry-delay 20 --retry-all-errors -o "$file.part" "$url"
  if ! echo "$sha  $file.part" | sha256sum -c --status -; then
    rm -f "$file.part"
    echo "The download of $(basename "$file") doesn't match the pinned checksum; not using it." >&2
    exit 1
  fi
  mv "$file.part" "$file"
}

KTLINT="$CACHE/ktlint-cli-$KTLINT_VERSION-all.jar"
DETEKT="$CACHE/detekt-cli-$DETEKT_VERSION-all.jar"
fetch "$CENTRAL/com/pinterest/ktlint/ktlint-cli/$KTLINT_VERSION/ktlint-cli-$KTLINT_VERSION-all.jar" "$KTLINT" "$KTLINT_SHA256"
fetch "$CENTRAL/io/gitlab/arturbosch/detekt/detekt-cli/$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar" "$DETEKT" "$DETEKT_SHA256"

FILES=("app/src/**/*.kt" "app/*.kts" "*.kts")
if [ "${1:-check}" = format ]; then
  echo "ktlint: fixing…"
  java -jar "$KTLINT" -F "${FILES[@]}" --relative
fi
echo "ktlint…"
java -jar "$KTLINT" "${FILES[@]}" --relative
echo "detekt…"
java -jar "$DETEKT" --build-upon-default-config -c config/detekt/detekt.yml \
  -i app/src/main/java,app/src/test/java --baseline config/detekt/baseline.xml
echo "Clean."
