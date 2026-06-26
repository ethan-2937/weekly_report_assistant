#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/logs/weekly-report.pid"
LOG_FILE="$ROOT/logs/server.log"

cd "$ROOT"
mkdir -p "$ROOT/logs" "$ROOT/output"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1"
    echo "Install it in your user environment first, then rerun this script."
    exit 1
  fi
}

need_cmd java
need_cmd mvn
need_cmd node
need_cmd npm
need_cmd python3

if [ ! -f "$ROOT/config/.env" ]; then
  echo "Missing config/.env"
  echo "Run: cp config/.env.example config/.env && nano config/.env"
  exit 1
fi

if [ -f "$PID_FILE" ]; then
  OLD_PID="$(cat "$PID_FILE" || true)"
  if [ -n "${OLD_PID:-}" ] && kill -0 "$OLD_PID" >/dev/null 2>&1; then
    echo "Stopping old weekly-report process: $OLD_PID"
    kill "$OLD_PID" || true
    sleep 2
  fi
fi

echo "Building frontend..."
(cd "$ROOT/web/frontend" && npm ci && npm run build)

echo "Building Spring Boot backend..."
(cd "$ROOT/web/backend-spring" && mvn -DskipTests package)

JAR="$(ls "$ROOT"/web/backend-spring/target/weekly-report-backend-*.jar | head -n 1)"
if [ -z "$JAR" ]; then
  echo "Backend jar not found."
  exit 1
fi

export WEEKLY_REPORT_ROOT="$ROOT"
export WEEKLY_FRONTEND_DIST="$ROOT/web/frontend/dist"
export PYTHON_BIN="${PYTHON_BIN:-python3}"
export WEEKLY_REPORT_PORT="${WEEKLY_REPORT_PORT:-8088}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xms128m -Xmx384m}"

echo "Starting weekly-report on port $WEEKLY_REPORT_PORT..."
nohup java -jar "$JAR" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "Started. PID: $(cat "$PID_FILE")"
echo "Log: $LOG_FILE"
echo "Health check: curl http://127.0.0.1:$WEEKLY_REPORT_PORT/api/health"
