#!/bin/sh
# Render-only entrypoint. Never print secrets or accept arbitrary overriding arguments.
set -eu
fail() { printf '%s\n' "$1" >&2; exit 1; }
[ "$#" -eq 0 ] || fail 'Do not set a Docker command override for this free deployment.'
[ -n "${MONGODB_URI:-}" ] || fail 'Set MONGODB_URI in the Render dashboard.'
[ -n "${APP_AES_KEY_BASE64:-}" ] || fail 'Set APP_AES_KEY_BASE64 in the Render dashboard; preserve the existing data key.'
[ -n "${APP_JWT_SECRET_BASE64:-}" ] || fail 'Set APP_JWT_SECRET_BASE64 in the Render dashboard.'
[ -n "${RENDER_EXTERNAL_URL:-}" ] || fail 'RENDER_EXTERNAL_URL is required; Render provides it automatically.'
printf '%s\n' "$RENDER_EXTERNAL_URL" | grep -Eq '^https://[a-z0-9]([a-z0-9-]*[a-z0-9])?\.onrender\.com$' \
    || fail 'Expected the exact HTTPS onrender.com origin, without a path or trailing slash.'
port="${PORT:-10000}"
case "$port" in ''|*[!0-9]*) fail 'PORT must be an integer from 1024 through 65535.' ;; esac
[ "${#port}" -le 5 ] || fail 'PORT must be an integer from 1024 through 65535.'
[ "$port" -ge 1024 ] && [ "$port" -le 65535 ] \
    || fail 'PORT must be an integer from 1024 through 65535.'

# Command-line properties override old localhost/tunnel environment settings.
# Trust no client-supplied forwarded origin, permit no wildcard origins, and disable paid AI.
# Use exec so Render's termination signal reaches Java directly.
exec java -jar /app/messenger.jar \
    "--server.port=$port" \
    --server.address=0.0.0.0 \
    --server.forward-headers-strategy=none \
    --server.shutdown=graceful \
    --spring.lifecycle.timeout-per-shutdown-phase=20s \
    --server.tomcat.threads.max=40 \
    --server.tomcat.threads.min-spare=2 \
    "--app.allowed-origins=$RENDER_EXTERNAL_URL" \
    --app.openai.enabled=false
