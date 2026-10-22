# Uses JDK 25 without changing the system-wide Java installation.
$ErrorActionPreference = 'Stop'
$taskArgs = $args
if ($taskArgs.Count -eq 0) { $taskArgs = @('build') }
# A development JVM lazily loads .class files from this same checkout. Replacing
# them beneath a running game mixes old initializers/screens with new classes.
$readOnlyTasks = @('help','tasks','projects','dependencies','--version','-v')
$readOnlyRequest = @($taskArgs | Where-Object { $_ -notin $readOnlyTasks }).Count -eq 0
if (-not $readOnlyRequest) {
    $activeClients = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -and
        $_.CommandLine.IndexOf($PSScriptRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $_.CommandLine -match 'net\.fabricmc\.devlaunchinjector\.Main|net\.minecraft\.client\.main\.Main'
    })
    if ($activeClients.Count -gt 0) {
        $clientIds = ($activeClients | ForEach-Object { $_.ProcessId }) -join ', '
        throw "Minecraft test client is still running (PID $clientIds). Save the world and fully close Minecraft before building or starting it again. Returning to the title screen or pressing F3+T does not reload Java code."
    }
}
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
