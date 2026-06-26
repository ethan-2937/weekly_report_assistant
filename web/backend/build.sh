#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p bin
javac -encoding UTF-8 -d bin src/com/yzzhang/weeklyreport/WeeklyReportServer.java
