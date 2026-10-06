# Build only; never installs or changes Minecraft profiles.
param(
    [string]$Task = 'build',
    [switch]$Test,
    [switch]$Clean,
    [string]$JavaHome
)
$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    if (-not $JavaHome) {
        $JavaHome = $env:JAVA_HOME
        if (-not $JavaHome) {
            $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
            if ($compiler) { $JavaHome = Split-Path (Split-Path $compiler.Source -Parent) -Parent }
        }
    }
    if (-not $JavaHome) { throw 'Install a Java 21 JDK and set JAVA_HOME, or pass -JavaHome.' }
    $javaExe = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path $javaExe) -or -not (Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) {
        throw 'A Java 21 JDK is required. Pass -JavaHome with its directory.'
    }
    $releaseFile = Join-Path $JavaHome 'release'
    if (-not (Test-Path $releaseFile) -or (Get-Content -LiteralPath $releaseFile -Raw) -notmatch 'JAVA_VERSION="21(\.|\")') {
        throw 'This Minecraft build requires Java 21. Pass -JavaHome with a Java 21 JDK.'
    }
    $env:JAVA_HOME = $JavaHome
    $tasks = @()
    if ($Clean) { $tasks += 'clean' }
    if ($Test -and $Task -ne 'build' -and $Task -ne 'test') { $tasks += 'test' }
    $tasks += $Task
    & .\gradlew.bat @tasks --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
    if ($Task -eq 'build') { Write-Host 'Built build\libs\blockblend-0.3.1.jar. Installation is a separate step.' }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
