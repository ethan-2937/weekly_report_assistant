#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(cd ../.. && pwd)"
exec java -cp bin com.yzzhang.weeklyreport.WeeklyReportServer "$ROOT"
