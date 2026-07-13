$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Assert-ExternalCommand([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed with exit code $LASTEXITCODE."
    }
}

Write-Host "[1/5] Checking repository harness and privacy boundaries..." -ForegroundColor Cyan
& (Join-Path $PSScriptRoot "check-harness.ps1")
if (-not $?) {
    throw "Harness checks failed."
}

Write-Host "[2/5] Running Python checks..." -ForegroundColor Cyan
$python = if (Get-Command python -ErrorAction SilentlyContinue) { "python" } elseif (Get-Command python3 -ErrorAction SilentlyContinue) { "python3" } else { throw "Python 3 is required." }
& $python -m compileall -q (Join-Path $root "scripts") (Join-Path $root "codex-skills/weekly-report-assistant/scripts")
Assert-ExternalCommand "Python compile check"
& $python -m unittest discover -s (Join-Path $root "tests") -p "test_*.py"
Assert-ExternalCommand "Python tests"

Write-Host "[3/5] Running Spring backend tests..." -ForegroundColor Cyan
Push-Location (Join-Path $root "web/backend-spring")
try {
    if ($env:OS -eq "Windows_NT") {
        & ".\mvnw.cmd" test
    } else {
        & "./mvnw" test
    }
    Assert-ExternalCommand "Spring backend tests"
} finally {
    Pop-Location
}

Write-Host "[4/5] Running Vue frontend tests..." -ForegroundColor Cyan
Push-Location (Join-Path $root "web/frontend")
try {
    $npm = if ($env:OS -eq "Windows_NT") { "npm.cmd" } else { "npm" }
    if (-not (Test-Path -LiteralPath "node_modules")) {
        & $npm ci
        Assert-ExternalCommand "Frontend dependency install"
    }
    & $npm test
    Assert-ExternalCommand "Frontend tests"
} finally {
    Pop-Location
}

Write-Host "[5/5] Building Vue frontend..." -ForegroundColor Cyan
Push-Location (Join-Path $root "web/frontend")
try {
    $npm = if ($env:OS -eq "Windows_NT") { "npm.cmd" } else { "npm" }
    & $npm run build
    Assert-ExternalCommand "Frontend build"
} finally {
    Pop-Location
}

Write-Host "All verification steps passed." -ForegroundColor Green
