#!/usr/bin/env bash
# Invoked only by isolated CI. Creates local synthetic accounts/data; no deployment.
set -euo pipefail
mkdir -p build/e2e-artifacts
PIDS=()
cleanup(){ for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT
JAR=$(find build/libs -maxdepth 1 -name '*SNAPSHOT.jar' ! -name '*plain.jar' -print -quit)
[[ -n "$JAR" ]]
ready(){ for i in $(seq 1 90); do curl -fs "http://127.0.0.1:$1/login" >/dev/null && return 0; sleep 1; done; return 1; }
SERVER_PORT=8080 java -jar "$JAR" >build/e2e-server.log 2>&1 & PIDS+=("$!")
ready 8080
python3 src/test/e2e/run_workspace.py
python3 src/test/e2e/voice_calls.py
python3 src/test/e2e/roadmap.py
MESSENGER_SOAK_SECONDS=60 python3 scripts/validate/soak.py
kill "${PIDS[0]}";wait "${PIDS[0]}" || true;PIDS=()
# Two independent instances with one shared DB, broker and search projection.
export APP_BROKER_RELAY_ENABLED=true APP_SEARCH_ENABLED=true
export APP_ALLOWED_ORIGINS=http://127.0.0.1:8080,http://127.0.0.1:8081
SERVER_PORT=8080 java -jar "$JAR" >build/cluster-a.log 2>&1 & PIDS+=("$!")
SERVER_PORT=8081 java -jar "$JAR" >build/cluster-b.log 2>&1 & PIDS+=("$!")
ready 8080;ready 8081
MESSENGER_SECOND_URL=http://127.0.0.1:8081 python3 src/test/e2e/infrastructure.py
MESSENGER_SECOND_URL=http://127.0.0.1:8081 python3 src/test/e2e/voice_calls.py
