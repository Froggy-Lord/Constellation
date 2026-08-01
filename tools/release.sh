#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INSTANCE="${CONSTELLATION_GATHER_MODS:-$HOME/.local/share/PrismLauncher/instances/Constellation Gather 26.2/minecraft/mods}"
ARCHIVE="$HOME/Desktop/To-Delete/gather-jars"
BOOT_LOG="${CONSTELLATION_BOOT_LOG:-/tmp/constellation-release-boot.log}"
BUILD_LOG="${CONSTELLATION_BUILD_LOG:-/tmp/constellation-release-build.log}"
ENQUEUE="${CONSTELLATION_DRIP_ENQUEUE:-$HOME/.local/bin/constellation-drip-enqueue-release.sh}"

[ "$#" -eq 1 ] || { printf "usage: %s 'release message'\n" "$0" >&2; exit 2; }
version="$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties" | tr -d '[:space:]')"
jar="$ROOT/build/libs/constellation-$version.jar"

cd "$ROOT"
./gradlew build -q 2>&1 | tee "$BUILD_LOG"
grep -Fq '[        11 tests successful      ]' "$BUILD_LOG"
grep -Fq '[         0 tests failed          ]' "$BUILD_LOG"

set +e
timeout 160 xvfb-run -a ./gradlew runClient > "$BOOT_LOG" 2>&1
boot_rc=$?
set -e
[ "$boot_rc" -eq 124 ] || { printf 'boot check exited %s; see %s\n' "$boot_rc" "$BOOT_LOG" >&2; exit 3; }
CLIENT_LOG="$ROOT/run/logs/latest.log"
grep -Fq '138 rooms across 9 shapes' "$CLIENT_LOG"
grep -Fq 'Constellation ready. 14 constellations loaded.' "$CLIENT_LOG"
if grep -Eiq 'mixin apply failed|crash report|fatal error' "$BOOT_LOG" "$CLIENT_LOG"; then
    printf 'boot log contains a fatal marker; see %s\n' "$BOOT_LOG" >&2
    exit 3
fi

mkdir -p "$ARCHIVE" "$INSTANCE"
stamp="$(date +%Y%m%d-%H%M%S)-$version"
old=("$INSTANCE"/constellation-*.jar)
if [ -e "${old[0]}" ]; then
    mkdir -p "$ARCHIVE/$stamp"
    mv "${old[@]}" "$ARCHIVE/$stamp/"
fi
cp "$jar" "$INSTANCE/"
cmp -s "$jar" "$INSTANCE/$(basename "$jar")"

"$ENQUEUE" "$1"
printf 'CONSTELLATION_RELEASE result=PASS version=%s boot_log=%s\n' "$version" "$BOOT_LOG"
