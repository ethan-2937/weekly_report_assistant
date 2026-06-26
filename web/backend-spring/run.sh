#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(cd ../.. && pwd)"
JAR="$(ls target/weekly-report-backend-*.jar | head -n 1)"
exec java -Dweekly.project-root="$ROOT" -jar "$JAR"
