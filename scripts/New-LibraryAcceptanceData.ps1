<#
.SYNOPSIS
Creates a NEW isolated library acceptance Access database. Never replaces existing data.
#>
param(
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$BaseDate = (Get-Date -Format 'yyyy-MM-dd')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = [IO.Path]::GetFullPath($Output)
if (Test-Path -LiteralPath $outputPath) { throw 'Output already exists; choose a NEW file. No data was changed.' }
[void][datetime]::ParseExact($BaseDate, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
$serverJar = Join-Path $projectRoot 'server/target/vCampusServer.jar'
if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf)) { throw 'Build first: mvn package' }
& java '-Dfile.encoding=UTF-8' '-Dvcampus.library.maxActiveLoans=5' -cp $serverJar cn.vcampus.server.LibraryAcceptanceData '--new-demo' $projectRoot $outputPath $BaseDate
if ($LASTEXITCODE -ne 0) { throw 'Creation failed. Keep the failed output for diagnosis; do NOT use it for acceptance.' }
Write-Output "Ready: $outputPath"
Write-Output 'Demo-only accounts/passwords: test-data/LIBRARY_ACCEPTANCE.md'
