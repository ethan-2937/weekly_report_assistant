#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/logs/weekly-report.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "No PID file found: $PID_FILE"
  exit 0
fi

PID="$(cat "$PID_FILE" || true)"
if [ -z "${PID:-}" ]; then
  rm -f "$PID_FILE"
  echo "Empty PID file removed."
  exit 0
fi

if kill -0 "$PID" >/dev/null 2>&1; then
  echo "Stopping weekly-report process: $PID"
  kill "$PID" || true
else
  echo "Process is not running: $PID"
fi

rm -f "$PID_FILE"
echo "Stopped."
