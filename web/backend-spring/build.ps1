$ErrorActionPreference = "Stop"
$localMvn = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.16\bin\mvn.cmd"
if (Test-Path $localMvn) {
  & $localMvn -DskipTests package
} else {
  mvn -DskipTests package
}
