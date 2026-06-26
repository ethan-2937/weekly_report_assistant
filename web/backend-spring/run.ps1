$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
$jar = Get-ChildItem "$PSScriptRoot\target\weekly-report-backend-*.jar" | Select-Object -First 1
if (-not $jar) {
  throw "Jar not found. Run .\build.ps1 first."
}
java "-Dweekly.project-root=$root" -jar $jar.FullName
