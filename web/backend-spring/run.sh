#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(cd ../.. && pwd)"
JAR="$(ls target/weekly-report-backend-*.jar | head -n 1)"
export WEEKLY_AUTH_DEVELOPMENT_MODE="${WEEKLY_AUTH_DEVELOPMENT_MODE:-true}"
exec java -Dweekly.project-root="$ROOT" -jar "$JAR"
