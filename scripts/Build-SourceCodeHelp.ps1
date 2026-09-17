<#
.SYNOPSIS
Builds the full vCampus source-code help document in HTML with the JDK JavaDoc program.
.DESCRIPTION
Compiles all production modules, resolves the course module's Apache POI dependency,
and generates private API documentation for every cn.vcampus package.
The output is suitable for the course's electronic source-code help deliverable.
#>
param(
    [switch]$SkipBuild,
    [switch]$Overwrite,
    [string]$OutputDirectory = 'docs/source-code-help'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$modules = @('common', 'user-management', 'student-management', 'course-selection',
    'library', 'store', 'server', 'client')

function Find-JavaDocCommand {
    $command = Get-Command javadoc -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin/javadoc.exe') }
    $javaRoot = 'C:\Program Files\Java'
    if (Test-Path -LiteralPath $javaRoot) {
        $candidates += Get-ChildItem -LiteralPath $javaRoot -Directory |
            Where-Object { $_.Name -like 'jdk-*' -or $_.Name -eq 'latest' } |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin/javadoc.exe' }
    }
    return $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

Push-Location -LiteralPath $projectRoot
try {
    $outputPath = Join-Path $projectRoot $OutputDirectory
    $docsRoot = Join-Path $projectRoot 'docs'
    $resolvedDocsRoot = [IO.Path]::GetFullPath($docsRoot)
    $resolvedOutput = [IO.Path]::GetFullPath($outputPath)
    if (-not $resolvedOutput.StartsWith($resolvedDocsRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OutputDirectory must stay inside the repository docs directory.'
    }
    if (Test-Path -LiteralPath $outputPath) {
        if (-not $Overwrite) {
            throw "Output exists: $outputPath. Run again with -Overwrite to rebuild generated JavaDoc."
        }
        Remove-Item -LiteralPath $outputPath -Recurse -Force
    }

    if (-not $SkipBuild) {
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Maven package failed: $LASTEXITCODE" }
    }

    & mvn -q -pl course-selection dependency:build-classpath `
        '-Dmdep.outputFile=target/javadoc-classpath.txt' '-Dmdep.pathSeparator=;'
    if ($LASTEXITCODE -ne 0) { throw "Maven dependency classpath generation failed: $LASTEXITCODE" }

    $javadocCommand = Find-JavaDocCommand
    if (-not $javadocCommand) { throw 'No JDK JavaDoc executable was found. Set JAVA_HOME to a JDK installation.' }

    $sourcePaths = @($modules | ForEach-Object { Join-Path $projectRoot "$_/src/main/java" })
    $compiledPaths = @($modules | ForEach-Object { Join-Path $projectRoot "$_/target/classes" })
    foreach ($path in $sourcePaths + $compiledPaths) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Missing required path: $path" }
    }
    $dependencyFile = Join-Path $projectRoot 'course-selection/target/javadoc-classpath.txt'
    if (-not (Test-Path -LiteralPath $dependencyFile)) { throw "Missing dependency classpath: $dependencyFile" }
    $classPath = ($compiledPaths + (Get-Content -LiteralPath $dependencyFile -Raw).Trim()) -join [IO.Path]::PathSeparator

    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    $javadocArguments = @(
        '-J-Dfile.encoding=UTF-8', '-quiet', '-private', '-encoding', 'UTF-8', '-charset', 'UTF-8',
        '-docencoding', 'UTF-8', '-notimestamp', '-Xdoclint:all,-missing',
        '-windowtitle', 'vCampus 源代码帮助文档',
        '-doctitle', 'vCampus 虚拟校园系统源代码帮助文档',
        '-overview', (Join-Path $projectRoot 'docs/source-code-help-overview.html'),
        '-sourcepath', ($sourcePaths -join [IO.Path]::PathSeparator),
        '-classpath', $classPath,
        '-d', $outputPath,
        '-subpackages', 'cn.vcampus'
    )
    & $javadocCommand @javadocArguments
    if ($LASTEXITCODE -ne 0) { throw "JavaDoc generation failed: $LASTEXITCODE" }

    $utf8WithoutBom = [Text.UTF8Encoding]::new($false)
    Get-ChildItem -LiteralPath $outputPath -Recurse -File -Filter '*.html' | ForEach-Object {
        $content = [IO.File]::ReadAllText($_.FullName)
        $normalized = [Text.RegularExpressions.Regex]::Replace(
            $content, '[ \t]+(?=\r?$)', '', [Text.RegularExpressions.RegexOptions]::Multiline)
        if ($normalized -ne $content) {
            [IO.File]::WriteAllText($_.FullName, $normalized, $utf8WithoutBom)
        }
    }

    $index = Join-Path $outputPath 'index.html'
    if (-not (Test-Path -LiteralPath $index)) { throw 'JavaDoc index.html was not generated.' }
    Write-Output "JavaDoc: $index"
} finally {
    Pop-Location
}
