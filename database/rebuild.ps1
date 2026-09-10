param(
    [string]$DatabasePath = "database\vCampus.accdb",
    [string[]]$AdditionalScript = @()
)

$ErrorActionPreference = "Stop"
$root = [System.IO.Path]::GetFullPath((Resolve-Path (Join-Path $PSScriptRoot "..")).Path)
$rootPrefix = $root.TrimEnd('\') + '\'
$database = if ([System.IO.Path]::IsPathRooted($DatabasePath)) {
    [System.IO.Path]::GetFullPath($DatabasePath)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $root $DatabasePath))
}
$databaseDirectory = Split-Path -Parent $database

if (-not $database.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Database path must stay inside the repository."
}

$additionalScripts = @()
foreach ($scriptPath in $AdditionalScript) {
    $resolvedScript = if ([System.IO.Path]::IsPathRooted($scriptPath)) {
        [System.IO.Path]::GetFullPath($scriptPath)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $root $scriptPath))
    }
    if (-not $resolvedScript.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Additional script path must stay inside the repository: $scriptPath"
    }
    if (-not (Test-Path -LiteralPath $resolvedScript -PathType Leaf)) {
        throw "Additional script does not exist: $scriptPath"
    }
    $additionalScripts += $resolvedScript
}

New-Item -ItemType Directory -Force -Path $databaseDirectory | Out-Null

Push-Location $root
try {
    # Build and compile before replacing the database, so a toolchain failure never removes it.
    mvn -q -pl server -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    $sourceDirectory = Join-Path $env:TEMP "vcampus-db-init-$PID"
    New-Item -ItemType Directory -Force -Path $sourceDirectory | Out-Null
    $source = Join-Path $sourceDirectory "VCampusDatabaseInitializer.java"

    @'
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class VCampusDatabaseInitializer {
    public static void main(String[] args) throws Exception {
        Path database = Paths.get(args[0]).toAbsolutePath().normalize();
        for (int i = 1; i < args.length; i++) {
            execute(database, Paths.get(args[i]), i == 1);
        }
    }

    private static void execute(Path database, Path script, boolean createDatabase)
            throws Exception {
        String url = "jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true"
                + (createDatabase ? ";newDatabaseVersion=V2010" : "");
        String sql = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String command : removeComments(sql).split(";")) {
                String normalized = command.trim();
                if (!normalized.isEmpty()) {
                    statement.execute(normalized);
                }
            }
        }
    }

    private static String removeComments(String sql) {
        StringBuilder result = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (!line.trim().startsWith("--")) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }
}
'@ | ForEach-Object {
        [System.IO.File]::WriteAllText($source, $_, (New-Object System.Text.UTF8Encoding($false)))
    }

    $jar = Join-Path $root "server\target\vCampusServer.jar"
    javac -cp $jar -d $sourceDirectory $source
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed with exit code $LASTEXITCODE."
    }

    if (Test-Path -LiteralPath $database) {
        $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $backup = "$database.$stamp.bak"
        Copy-Item -LiteralPath $database -Destination $backup
        Remove-Item -LiteralPath $database
        Write-Host "Backed up existing database to $backup"
    }

    $initializerArguments = @(
        $database,
        (Join-Path $root "database\schema.sql"),
        (Join-Path $root "database\seed.sql")
    ) + $additionalScripts
    & java -cp "$sourceDirectory;$jar" VCampusDatabaseInitializer @initializerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Database initialization failed with exit code $LASTEXITCODE."
    }

    Write-Host "Database rebuilt successfully: $database"
}
finally {
    Pop-Location
}
