#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONTAINER="${CONSTELLATION_SHARE_CONTAINER:-share-server}"
SHARE_ROOT="/srv/pages/constellation"
PUBLIC_URL="https://home.zadenzeus.dev/pages/constellation/"

version="$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties" | tr -d '[:space:]')"
jar="$ROOT/build/libs/constellation-$version.jar"
guide="$ROOT/TESTING_GUIDE.md"

[[ -n "$version" ]] || { printf 'missing mod_version\n' >&2; exit 2; }
[[ -f "$jar" ]] || { printf 'missing release jar: %s\n' "$jar" >&2; exit 2; }
[[ -f "$guide" ]] || { printf 'missing testing guide: %s\n' "$guide" >&2; exit 2; }

stamp="$(date +%Y%m%d-%H%M%S)"
stage="/tmp/constellation-private-release-$stamp"
mkdir -p "$stage/Current"

cp "$jar" "$stage/Current/"
cp "$guide" "$stage/Current/TESTING_GUIDE.md"
sha256sum "$stage/Current/constellation-$version.jar" > "$stage/Current/SHA256SUMS.txt"

docker exec "$CONTAINER" sh -c "
set -eu
mkdir -p '$SHARE_ROOT/Current' '$SHARE_ROOT/Archived Releases'
old=\$(find '$SHARE_ROOT/Current' -maxdepth 1 -type f -name 'constellation-*.jar' -print -quit)
if [ -n \"\$old\" ]; then
    old_name=\$(basename \"\$old\" .jar)
    destination='$SHARE_ROOT/Archived Releases'/\"\$old_name\"
    [ ! -e \"\$destination\" ] || destination=\"\$destination-$stamp\"
    mkdir -p \"\$destination\"
    find '$SHARE_ROOT/Current' -mindepth 1 -maxdepth 1 -type f -exec mv -t \"\$destination\" {} +
fi
"

index_tmp="$stage/index.html"
{
    printf '%s\n' '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
    printf '%s\n' '<title>Constellation builds</title><style>body{max-width:760px;margin:48px auto;padding:0 20px;background:#08081a;color:#f0ede0;font:16px system-ui}a{color:#ffcc55}section{margin:24px 0;padding:20px;background:#14142e;border:1px solid #36365e}li{margin:10px 0}.muted{color:#b5b0a5}</style>'
    printf '<h1>Constellation builds</h1><p class="muted">Private test releases. Current always contains the newest verified build and testing guide.</p>'
    printf '<section><h2>Current</h2><ul><li><a href="Current/constellation-%s.jar">Constellation %s</a></li><li><a href="Current/TESTING_GUIDE.md">Testing guide</a></li><li><a href="Current/SHA256SUMS.txt">SHA-256</a></li></ul></section>' "$version" "$version"
    printf '<section><h2>Archived Releases</h2><ul>'
    while IFS= read -r archived_path; do
        [[ -n "$archived_path" ]] || continue
        directory="$(dirname "$archived_path")"
        jar_name="$(basename "$archived_path")"
        name="$(basename "$directory")"
        printf '<li><a href="Archived%%20Releases/%s/%s">%s</a></li>' "$name" "$jar_name" "$name"
    done < <(docker exec "$CONTAINER" sh -c \
        "find '$SHARE_ROOT/Archived Releases' -mindepth 2 -maxdepth 2 -type f -name 'constellation-*.jar' -print 2>/dev/null" \
        | sort -r)
    printf '%s\n' '</ul></section></html>'
} > "$index_tmp"

docker cp "$stage/Current/." "$CONTAINER:$SHARE_ROOT/Current/"
docker cp "$stage/index.html" "$CONTAINER:$SHARE_ROOT/index.html"
docker exec "$CONTAINER" python3 -c '
import json, os
path = "/srv/.pages_meta"
with open(path, encoding="utf-8") as source:
    data = json.load(source)
data["constellation"] = {"title": "Constellation builds", "owner_user": "zaden",
    "public": False, "access": {"mode": "owner", "users": [], "token": ""}}
temporary = path + ".constellation.tmp"
with open(temporary, "w", encoding="utf-8") as target:
    json.dump(data, target, indent=1)
os.replace(temporary, path)
'

anonymous_code="$(curl --silent --output /dev/null --write-out '%{http_code}' "$PUBLIC_URL")"
[[ "$anonymous_code" == "401" ]] || {
    printf 'privacy check failed: anonymous request returned %s\n' "$anonymous_code" >&2
    exit 3
}
docker cp "$CONTAINER:$SHARE_ROOT/Current/constellation-$version.jar" "$stage/published.jar"
cmp -s "$jar" "$stage/published.jar" || {
    printf 'published jar does not match local release\n' >&2
    exit 3
}

mkdir -p "$HOME/Desktop/To-Delete/constellation-release-staging"
mv "$stage" "$HOME/Desktop/To-Delete/constellation-release-staging/$stamp"

printf 'CONSTELLATION_PRIVATE_RELEASE result=PASS version=%s url=%s sha256=%s\n' \
    "$version" "$PUBLIC_URL" "$(sha256sum "$jar" | awk '{print $1}')"
