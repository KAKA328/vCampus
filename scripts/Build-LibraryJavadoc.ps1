<#
.SYNOPSIS
Builds the library subsystem's HTML JavaDoc and checks production Java source size.
.DESCRIPTION
Uses the JDK's javadoc executable, with no Maven JavaDoc plugin download.
Generated files stay under target and are not committed. Does not change a running database.
#>
param(
    [switch]$SkipBuild,
    [switch]$Offline,
    [string]$MavenRepository
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if (-not $SkipBuild) {
        $mavenArguments = @('-q', '-DskipTests', 'package')
        if ($Offline) { $mavenArguments += '-o' }
        if ($MavenRepository) { $mavenArguments += "-Dmaven.repo.local=$MavenRepository" }
        & mvn @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed: $LASTEXITCODE" }
    }

    $sources = @(
        Get-ChildItem -LiteralPath (Join-Path $projectRoot 'library/src/main/java') -Filter '*.java' -Recurse
        Get-ChildItem -LiteralPath (Join-Path $projectRoot 'client/src/main/java') -Filter '*Library*.java' -Recurse
        Get-ChildItem -LiteralPath (Join-Path $projectRoot 'server/src/main/java') -Filter '*Library*.java' -Recurse
    ) | Sort-Object FullName -Unique

    $tooLong = @($sources | ForEach-Object {
        $lineCount = [System.IO.File]::ReadAllLines($_.FullName).Length
        if ($lineCount -gt 200) { "$($_.FullName): $lineCount lines" }
    })
    if ($tooLong.Count -gt 0) {
        throw ("Library production files exceed 200 lines:" + [Environment]::NewLine +
                ($tooLong -join [Environment]::NewLine))
    }

    $modules = @('common', 'user-management', 'student-management', 'course-selection',
        'library', 'store', 'server', 'client')
    $classPaths = @($modules | ForEach-Object { Join-Path $projectRoot "$_/target/classes" })
    $classPaths += Join-Path $projectRoot 'client/target/vCampusClient.jar'
    $classPaths += Join-Path $projectRoot 'server/target/vCampusServer.jar'
    foreach ($compiledPath in $classPaths) {
        if (-not (Test-Path -LiteralPath $compiledPath)) { throw "Build first; missing $compiledPath" }
    }

    $outputDirectory = Join-Path $projectRoot 'target/library-javadoc'
    $javadocCommand = (Get-Command javadoc -ErrorAction Stop).Source
    $jdkBin = Split-Path -Parent $javadocCommand
    $toolsJar = Join-Path (Split-Path -Parent $jdkBin) 'lib/tools.jar'
    if (-not (Test-Path -LiteralPath $toolsJar)) { throw 'Use a full JDK 8 installation for this course build' }
    $inventoryDirectory = Join-Path $projectRoot 'target/library-documentation-tools'
    New-Item -ItemType Directory -Path $inventoryDirectory -Force | Out-Null
    & (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -cp $toolsJar -d $inventoryDirectory `
        (Join-Path $PSScriptRoot 'LibraryDocInventory.java')
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the read-only JavaDoc inventory tool' }
    $inventoryText = & (Join-Path $jdkBin 'java.exe') '-Dfile.encoding=UTF-8' `
        -cp ($inventoryDirectory + [System.IO.Path]::PathSeparator + $toolsJar) LibraryDocInventory $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Could not parse library source declarations' }
    $inventory = @($inventoryText | ConvertFrom-Json)
    $missingComments = @($inventory | Where-Object { [string]::IsNullOrWhiteSpace($_.comment) })
    if ($missingComments.Count -gt 0) {
        throw ('Missing JavaDoc: ' + (($missingComments | ForEach-Object { "$($_.path):$($_.line) $($_.name)" }) -join ', '))
    }
    $javadocArguments = @(
        '-J-Dfile.encoding=UTF-8', '-quiet', '-private', '-encoding', 'UTF-8',
        '-charset', 'UTF-8', '-docencoding', 'UTF-8', '-notimestamp',
        '-Xdoclint:all,-missing',
        '-windowtitle', 'vCampus Library API',
        '-doctitle', 'vCampus Library Subsystem',
        '-overview', (Join-Path $projectRoot 'docs/library-javadoc-overview.html'),
        '-classpath', ($classPaths -join [System.IO.Path]::PathSeparator),
        '-d', $outputDirectory
    )
    $javadocArguments += @($sources | ForEach-Object { $_.FullName })
    & $javadocCommand @javadocArguments
    if ($LASTEXITCODE -ne 0) { throw "JavaDoc generation failed: $LASTEXITCODE" }
    $index = Join-Path $outputDirectory 'index.html'
    if (-not (Test-Path -LiteralPath $index)) { throw 'JavaDoc index was not generated' }
    Write-Output "Library production files checked: $($sources.Count), all <= 200 lines"
    Write-Output "Documented declarations checked: $($inventory.Count), none missing JavaDoc"
    Write-Output "JavaDoc: $index"
} finally {
    Pop-Location
}
