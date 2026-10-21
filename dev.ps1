# Uses JDK 25 without changing the system-wide Java installation.
$ErrorActionPreference = 'Stop'
$taskArgs = $args
if ($taskArgs.Count -eq 0) { $taskArgs = @('build') }
$candidates = @(
    $env:CHUNITHM_JAVA_HOME,
    (Join-Path $PSScriptRoot '../runtime/jdk-25.0.4.1'),
    $env:JAVA_HOME
)
$selectedJdk = $null
foreach ($candidate in $candidates) {
    if (-not $candidate) { continue }
    $compiler = Join-Path $candidate 'bin/javac.exe'
    if (Test-Path -LiteralPath $compiler) {
        $compilerVersion = & $compiler -version 2>&1
        if ($LASTEXITCODE -eq 0 -and "$compilerVersion" -match '^javac 25(?:\.|$)') {
            $selectedJdk = (Resolve-Path -LiteralPath $candidate).Path
            break
        }
    }
}
if (-not $selectedJdk) {
    throw 'JDK 25 not found. Set CHUNITHM_JAVA_HOME to your JDK 25 directory.'
}
$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
$result = 1
try {
    $env:JAVA_HOME = $selectedJdk
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
    Push-Location $PSScriptRoot
    try {
        & "$PSScriptRoot/gradlew.bat" @taskArgs
        $result = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
}
exit $result
