$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$configPath = Join-Path $root "harness/quality-baseline.json"
$failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure([string]$message) {
    $failures.Add($message)
}

function Get-LineCount([string]$path) {
    return [System.IO.File]::ReadAllLines($path).Count
}

function Get-RelativePath([string]$path) {
    return $path.Substring($root.Length + 1).Replace("\", "/")
}

$required = @(
    "AGENTS.md",
    "scripts/AGENTS.md",
    "web/backend-spring/AGENTS.md",
    "web/frontend/AGENTS.md",
    "codex-skills/weekly-report-assistant/AGENTS.md",
    "docs/PRODUCT.md",
    "docs/ARCHITECTURE.md",
    "docs/QUALITY.md",
    "docs/SECURITY_PRIVACY.md",
    "docs/HARNESS_GUIDE.md",
    "docs/tasks/TEMPLATE.md",
    "harness/quality-baseline.json",
    ".env.example",
    "tests/test_attachment_download.py",
    "tests/test_dingtalk_common.py",
    "tests/test_leader_compliance.py",
    "tests/test_report_collection_window.py",
    "tests/test_submission_reminder.py",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/util/WeekLabelUtilsTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/config/ProductionCredentialValidatorTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/config/SecurityConfigWebMvcTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/service/impl/ReportPermissionServiceImplTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/service/impl/WeeklyReportServiceImplTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/service/impl/SubmissionReminderServiceTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/service/notification/DingTalkWorkNoticeClientTest.java",
    "web/backend-spring/src/test/java/com/yzzhang/weeklyreport/service/notification/FeedbackRecipientResolverTest.java",
    "web/frontend/src/api/client.test.js",
    "web/frontend/src/composables/useAuth.test.js"
)

foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $relative))) {
        Add-Failure "Missing required harness file: $relative."
    }
}

if (-not (Test-Path -LiteralPath $configPath)) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

$config = Get-Content -Raw -LiteralPath $configPath -Encoding UTF8 | ConvertFrom-Json

Get-ChildItem -Path $root -Filter "AGENTS.md" -Recurse -File |
    Where-Object { $_.FullName -notmatch "[\\/](target|node_modules|dist|\.git)[\\/]" } |
    ForEach-Object {
        $lines = Get-LineCount $_.FullName
        if ($lines -gt $config.agentGuideMaxLines) {
            $relative = Get-RelativePath $_.FullName
            Add-Failure "$relative has $lines lines (limit $($config.agentGuideMaxLines)). Keep AGENTS.md as a map and move detail to docs/."
        }
    }

$sourceRoots = @(
    (Join-Path $root "scripts"),
    (Join-Path $root "codex-skills/weekly-report-assistant/scripts"),
    (Join-Path $root "web/backend-spring/src/main/java"),
    (Join-Path $root "web/frontend/src")
)

foreach ($sourceRoot in $sourceRoots) {
    Get-ChildItem -Path $sourceRoot -Recurse -File | ForEach-Object {
        $relative = Get-RelativePath $_.FullName
        $extension = $_.Extension.ToLowerInvariant()
        $budget = $null
        $exact = $config.fileLineBudgets.PSObject.Properties[$relative]
        if ($null -ne $exact) {
            $budget = [int]$exact.Value
        } else {
            $default = $config.defaultLineBudgets.PSObject.Properties[$extension]
            if ($null -ne $default) {
                $budget = [int]$default.Value
            }
        }
        if ($null -ne $budget) {
            $lines = Get-LineCount $_.FullName
            if ($lines -gt $budget) {
                Add-Failure "$relative has $lines lines (budget $budget). Extract a cohesive module instead of increasing the budget."
            }
        }
    }
}

foreach ($property in $config.protectedFiles.PSObject.Properties) {
    $relative = $property.Name
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) {
        Add-Failure "Protected source file is missing: $relative."
        continue
    }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($actual -ne [string]$property.Value) {
        Add-Failure "$relative changed. Only update the real DingTalk template after explicit user confirmation, then record the new hash deliberately."
    }
}

$tracked = @(& git -C $root ls-files)
foreach ($path in $tracked) {
    $normalized = $path.Replace("\", "/")
    if ($normalized -match "^(\.env$|config/\.env$|output/|logs/)") {
        Add-Failure "Sensitive runtime path is tracked by Git: $normalized. Remove it from the index without deleting the user's local data."
    }
}

$controllerRoot = Join-Path $root "web/backend-spring/src/main/java/com/yzzhang/weeklyreport/controller"
$forbiddenControllerImport = '^import com\.yzzhang\.weeklyreport\.(mapper|service\.impl)\.'
Get-ChildItem -Path $controllerRoot -Filter "*.java" -Recurse -File | ForEach-Object {
    foreach ($match in (Select-String -Path $_.FullName -Pattern $forbiddenControllerImport)) {
        $relative = Get-RelativePath $_.FullName
        Add-Failure "$relative imports a concrete lower layer at line $($match.LineNumber). Depend on a service interface."
    }
}

$javaTests = @(Get-ChildItem -Path (Join-Path $root "web/backend-spring/src/test/java") -Filter "*Test.java" -Recurse -File -ErrorAction SilentlyContinue)
if ($javaTests.Count -eq 0) {
    Add-Failure "No Spring backend tests found. Add deterministic *Test.java coverage."
}

$pythonTests = @(Get-ChildItem -Path (Join-Path $root "tests") -Filter "test_*.py" -Recurse -File -ErrorAction SilentlyContinue)
if ($pythonTests.Count -eq 0) {
    Add-Failure "No Python tests found. Add deterministic unittest coverage under tests/."
}

$frontendPackage = Get-Content -Raw -LiteralPath (Join-Path $root "web/frontend/package.json") -Encoding UTF8 | ConvertFrom-Json
if (-not $frontendPackage.scripts.test) {
    Add-Failure "web/frontend/package.json must define a deterministic frontend test command."
}

$applicationConfig = Get-Content -Raw -LiteralPath (Join-Path $root "web/backend-spring/src/main/resources/application.yml") -Encoding UTF8
if ($applicationConfig -notmatch 'WEEKLY_AUTH_DEVELOPMENT_MODE:false') {
    Add-Failure "Production credential validation must be the default; local development mode must be explicit."
}

$composeConfig = Get-Content -Raw -LiteralPath (Join-Path $root "docker-compose.yml") -Encoding UTF8
if ($composeConfig -notmatch 'WEEKLY_JWT_SECRET:\?') {
    Add-Failure "docker-compose.yml must require WEEKLY_JWT_SECRET instead of using a development fallback."
}
if ($composeConfig -notmatch 'WEEKLY_BOOTSTRAP_ADMIN_PASSWORD:\?') {
    Add-Failure "docker-compose.yml must require WEEKLY_BOOTSTRAP_ADMIN_PASSWORD instead of using a development fallback."
}
if ($composeConfig -notmatch 'WEEKLY_REPORT_EXEMPT_SUBMITTERS:') {
    Add-Failure "docker-compose.yml must pass WEEKLY_REPORT_EXEMPT_SUBMITTERS into the collection container."
}
if ($composeConfig -notmatch 'WEEKLY_SUBMISSION_REMINDER_ENABLED:') {
    Add-Failure "docker-compose.yml must pass WEEKLY_SUBMISSION_REMINDER_ENABLED into the application container."
}

$composeEnvExample = Get-Content -Raw -LiteralPath (Join-Path $root ".env.example") -Encoding UTF8
if ($composeEnvExample -notmatch 'WEEKLY_REPORT_EXEMPT_SUBMITTERS=') {
    Add-Failure ".env.example must document WEEKLY_REPORT_EXEMPT_SUBMITTERS for Docker deployments."
}
if ($composeEnvExample -notmatch 'WEEKLY_SUBMISSION_REMINDER_ENABLED=') {
    Add-Failure ".env.example must document WEEKLY_SUBMISSION_REMINDER_ENABLED for Docker deployments."
}

if ($failures.Count -gt 0) {
    Write-Host "Harness checks failed ($($failures.Count)):" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

Write-Host "Harness checks passed." -ForegroundColor Green
