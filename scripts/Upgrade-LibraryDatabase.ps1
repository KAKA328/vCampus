<#
.SYNOPSIS
Creates and upgrades a NEW copy of an offline Access database for library V4.
.DESCRIPTION
Never overwrites the source or an existing output. Stop every server that uses the source
before calling with -SourceStopped. A failed output is retained for inspection, not used.
#>
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Output,
    [switch]$SourceStopped
)
$ErrorActionPreference = 'Stop'
if (-not $SourceStopped) { throw 'Stop all servers using the source database, then explicitly pass -SourceStopped.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = [IO.Path]::GetFullPath($Source)
$outputPath = [IO.Path]::GetFullPath($Output)
if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw 'Source database does not exist.' }
if ((Test-Path -LiteralPath $outputPath) -or $sourcePath -eq $outputPath) { throw 'Output must be a different NEW file.' }
$serverJar = Join-Path $projectRoot 'server/target/vCampusServer.jar'
if (-not (Test-Path -LiteralPath $serverJar)) { throw 'Build the project first: mvn package' }
$migrationFile = Join-Path $projectRoot 'database/migrations/017_library_copies_v4.up.sql'
& java '-Dfile.encoding=UTF-8' -cp $serverJar cn.vcampus.server.LibraryCopyMigration '--source-stopped' $sourcePath $outputPath $migrationFile
if ($LASTEXITCODE -ne 0) {
    throw 'Migration failed. The source is unchanged; do not launch against the failed output. Keep it for diagnosis.'
}
Write-Output "Verified upgraded copy: $outputPath"
Write-Output 'Use the new matching server with this copy; the source has NOT been replaced.'
