#!/usr/bin/env bash
# Constellation build helper (gitignored). builds, archives a copy of the jar,
# drops it into the test instance, uploads a share link, and emails the jar + changelog.
#   ./dev-build.sh          build + archive + notify + install + share
#   ./dev-build.sh --push   ... and push to gitea after
set -uo pipefail
cd "$(dirname "$0")"

export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

ARCHIVE="$HOME/constellation-builds"
mkdir -p "$ARCHIVE"

# the prism instance froggy tests in
INSTANCE_MODS="$HOME/.local/share/PrismLauncher/instances/Froggy__Lord Constellation 26.2/minecraft/mods"

VER=$(grep -E '^mod_version' gradle.properties | cut -d= -f2 | tr -d ' \r')
HASH=$(git rev-parse --short HEAD 2>/dev/null || echo nogit)
STAMP=$(date +%Y%m%d-%H%M%S)

notify() {
  local status="$1" extra="$2" jar="${3:-}"
  local cfg="$HOME/.config/cryptkit/notify.env"
  [ -f "$cfg" ] && . "$cfg"

  local subject="Constellation $status - v$VER ($HASH)"
  local commit recent body
  commit=$(git log -1 --pretty='%s' 2>/dev/null || echo '')
  recent=$(git log -6 --pretty='- %s' 2>/dev/null || echo '')
  body=$(printf 'Constellation build %s\n\nversion: %s (%s)\nlast change: %s\n%s\n\nrecent commits:\n%s\n' \
    "$status" "$VER" "$HASH" "$commit" "$extra" "$recent")

  # ntfy push
  if [ -n "${NTFY_URL:-}" ]; then
    curl -fsS -H "Title: Constellation $status" -d "$subject - $commit" "$NTFY_URL" >/dev/null 2>&1 || true
  fi

  # telegram
  if [ -n "${TG_BOT_TOKEN:-}" ] && [ -n "${TG_CHAT_ID:-}" ]; then
    curl -fsS "https://api.telegram.org/bot${TG_BOT_TOKEN}/sendMessage" \
      --data-urlencode "chat_id=${TG_CHAT_ID}" --data-urlencode "text=$subject - $commit" >/dev/null 2>&1 || true
  fi

  # email via Gmail SMTP — text only (jar attachment times out on Gmail's SMTP;
  # the share URL is already in the body, so the jar is one click away anyway)
  if [ -n "${NOTIFY_EMAIL:-}" ]; then
    python3 - "$subject" "$body" "$NOTIFY_EMAIL" <<'PYEOF'
import smtplib, sys
from email.mime.text import MIMEText

subject, body, to = sys.argv[1], sys.argv[2], sys.argv[3]
user = "javapro91@gmail.com"
pwd = "cotpuifrppbobwyk"

msg = MIMEText(body, 'plain')
msg['From'] = user
msg['To'] = to
msg['Subject'] = subject

with smtplib.SMTP('smtp.gmail.com', 587, timeout=30) as s:
    s.starttls()
    s.login(user, pwd)
    s.send_message(msg)
PYEOF
  fi
}

echo ">> building Constellation v$VER ($HASH)"

if ./gradlew build --console=plain; then
  JAR="build/libs/constellation-$VER.jar"
  if [ -f "$JAR" ]; then
    OUT="$ARCHIVE/constellation-$VER+$HASH-$STAMP.jar"
    cp "$JAR" "$OUT"
    COUNT=$(ls -1 "$ARCHIVE"/*.jar 2>/dev/null | wc -l | tr -d ' ')
    echo ">> archived: $OUT ($COUNT builds kept)"

    # drop it into the test instance
    if [ -d "$INSTANCE_MODS" ]; then
      rm -f "$INSTANCE_MODS"/constellation-*.jar
      cp "$OUT" "$INSTANCE_MODS/"
      echo ">> installed to instance: $INSTANCE_MODS/$(basename "$OUT")"
    else
      echo "!! instance mods folder not found, skipped install: $INSTANCE_MODS"
    fi

    # upload to share server for laptop download link
    SHARE_URL=""
    if curl -fsS --max-time 30 -F "file=@$OUT" -F "expire=360" \
      https://home.zadenzeus.dev/share/upload -o /tmp/cn-share.json 2>/dev/null; then
      SHARE_URL=$(python3 -c "import json; print(json.load(open('/tmp/cn-share.json'))['url'])" 2>/dev/null)
      [ -n "$SHARE_URL" ] && echo ">> share: $SHARE_URL"
      rm -f /tmp/cn-share.json
    fi

    EXTRA="archived, $COUNT builds kept"
    [ -n "$SHARE_URL" ] && EXTRA="$EXTRA
share: $SHARE_URL"

    notify "OK" "$EXTRA" "$OUT"
  else
    echo "!! build said ok but no jar at $JAR"
    notify "OK?" "no jar found"
  fi
  true
else
  echo "!! build failed"
  notify "FAILED" "see gradle output"
  exit 1
fi
