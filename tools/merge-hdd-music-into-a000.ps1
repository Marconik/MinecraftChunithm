[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string] $DataRoot,

    [Parameter(Position = 1)]
    [string] $BackupRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-MusicDefinition {
    param(
        [Parameter(Mandatory = $true)][System.IO.FileInfo] $XmlFile,
        [Parameter(Mandatory = $true)][string] $PackageName
    )

    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null

    $reader = [System.Xml.XmlReader]::Create($XmlFile.FullName, $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
    }
    finally {
        $reader.Dispose()
    }

    $idNode = $document.SelectSingleNode('/MusicData/name/id')
    $nameNode = $document.SelectSingleNode('/MusicData/name/str')
    if ($null -eq $idNode -or $null -eq $nameNode) {
        throw "MusicData/name/id or MusicData/name/str is missing: $($XmlFile.FullName)"
    }

    $id = 0
    if (-not [int]::TryParse($idNode.InnerText.Trim(), [ref] $id)) {
        throw "The song ID is not a valid integer: $($XmlFile.FullName)"
    }

    $enabledFumenCount = $document.SelectNodes("//MusicFumenData[enable='true']").Count

    [pscustomobject]@{
        Id                = $id
        Name              = $nameNode.InnerText.Trim()
        Package           = $PackageName
        Directory         = $XmlFile.Directory.FullName
        XmlPath           = $XmlFile.FullName
        EnabledFumenCount = $enabledFumenCount
    }
}

function Test-FileContentEqual {
    param(
        [Parameter(Mandatory = $true)][string] $Left,
        [Parameter(Mandatory = $true)][string] $Right
    )

    $leftFile = Get-Item -LiteralPath $Left
    $rightFile = Get-Item -LiteralPath $Right
    if ($leftFile.Length -ne $rightFile.Length) {
        return $false
    }

    $leftStream = [System.IO.File]::OpenRead($leftFile.FullName)
    $rightStream = [System.IO.File]::OpenRead($rightFile.FullName)
    try {
        $leftBuffer = New-Object byte[] 65536
        $rightBuffer = New-Object byte[] 65536
        while ($true) {
            $leftCount = $leftStream.Read($leftBuffer, 0, $leftBuffer.Length)
            $rightCount = $rightStream.Read($rightBuffer, 0, $rightBuffer.Length)
            if ($leftCount -ne $rightCount) {
                return $false
            }
            if ($leftCount -eq 0) {
                return $true
            }
            for ($index = 0; $index -lt $leftCount; $index++) {
                if ($leftBuffer[$index] -ne $rightBuffer[$index]) {
                    return $false
                }
            }
        }
    }
    finally {
        $leftStream.Dispose()
        $rightStream.Dispose()
    }
}

function Assert-ChildPath {
    param(
        [Parameter(Mandatory = $true)][string] $Parent,
        [Parameter(Mandatory = $true)][string] $Child
    )

    $parentPath = [System.IO.Path]::GetFullPath($Parent).TrimEnd('\') + '\'
    $childPath = [System.IO.Path]::GetFullPath($Child)
    if (-not $childPath.StartsWith($parentPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes the expected directory: $childPath"
    }
}

$resolvedDataRoot = (Resolve-Path -LiteralPath $DataRoot).Path
$a000MusicRoot = Join-Path $resolvedDataRoot 'A000\music'
if (-not (Test-Path -LiteralPath $a000MusicRoot -PathType Container)) {
    throw "A000/music was not found under: $resolvedDataRoot"
}
$a000MusicRoot = (Resolve-Path -LiteralPath $a000MusicRoot).Path

if ([string]::IsNullOrWhiteSpace($BackupRoot)) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $BackupRoot = Join-Path $PSScriptRoot "..\build\hdd-merge-backups\$timestamp"
}
$fullBackupRoot = [System.IO.Path]::GetFullPath($BackupRoot)

$packages = @(Get-ChildItem -LiteralPath $resolvedDataRoot -Directory |
        Where-Object { $_.Name -match '^A\d+$' } |
        Sort-Object { [int]$_.Name.Substring(1) })

$definitions = @(foreach ($package in $packages) {
    $musicRoot = Join-Path $package.FullName 'music'
    if (-not (Test-Path -LiteralPath $musicRoot -PathType Container)) {
        continue
    }

    Get-ChildItem -LiteralPath $musicRoot -Directory |
        Where-Object { $_.Name -match '^music\d+$' } |
        Sort-Object Name |
        ForEach-Object {
            $xmlPath = Join-Path $_.FullName 'Music.xml'
            if (Test-Path -LiteralPath $xmlPath -PathType Leaf) {
                Read-MusicDefinition -XmlFile (Get-Item -LiteralPath $xmlPath) -PackageName $package.Name
            }
        }
})

$duplicateGroups = @($definitions | Group-Object Id | Where-Object Count -gt 1)
if ($duplicateGroups.Count -eq 0) {
    Write-Host 'No duplicate song IDs were found.'
    return
}

$mergePlans = @()
$alreadyMerged = @()
foreach ($group in $duplicateGroups) {
    $baseDefinitions = @($group.Group | Where-Object Package -eq 'A000')
    $optionDefinitions = @($group.Group | Where-Object Package -ne 'A000')
    if ($baseDefinitions.Count -ne 1) {
        throw "Song ID $($group.Name) does not have exactly one A000 definition."
    }
    if ($optionDefinitions.Count -ne 1) {
        $packagesForId = ($optionDefinitions.Package | Sort-Object) -join ', '
        throw "Song ID $($group.Name) has multiple option definitions: $packagesForId"
    }

    $base = $baseDefinitions[0]
    $option = $optionDefinitions[0]
    if ($base.Name -ne $option.Name) {
        throw "Song ID $($group.Name) has conflicting names: '$($base.Name)' and '$($option.Name)'"
    }

    Assert-ChildPath -Parent $a000MusicRoot -Child $base.Directory

    $baseFiles = @(Get-ChildItem -LiteralPath $base.Directory -File)
    $optionFiles = @(Get-ChildItem -LiteralPath $option.Directory -File)
    foreach ($baseFile in ($baseFiles | Where-Object Name -ne 'Music.xml')) {
        $optionPath = Join-Path $option.Directory $baseFile.Name
        if (-not (Test-Path -LiteralPath $optionPath -PathType Leaf)) {
            throw "Option package $($option.Package) is missing the A000 file: $($baseFile.Name)"
        }
        if (-not (Test-FileContentEqual -Left $baseFile.FullName -Right $optionPath)) {
            throw "Option package $($option.Package) changes an existing A000 file: $($baseFile.Name)"
        }
    }

    $optionOnlyFiles = @($optionFiles | Where-Object {
            $_.Name -ne 'Music.xml' -and
            -not (Test-Path -LiteralPath (Join-Path $base.Directory $_.Name) -PathType Leaf)
        })

    $xmlEqual = Test-FileContentEqual -Left $base.XmlPath -Right $option.XmlPath
    if ($optionOnlyFiles.Count -eq 0 -and $xmlEqual) {
        $alreadyMerged += $base
        continue
    }
    if ($optionOnlyFiles.Count -eq 0) {
        throw "Song ID $($group.Name) changes Music.xml but adds no chart file."
    }
    if ($option.EnabledFumenCount -le $base.EnabledFumenCount) {
        throw "Song ID $($group.Name) does not add an enabled chart in Music.xml."
    }

    $mergePlans += [pscustomobject]@{
        Id          = $base.Id
        Name        = $base.Name
        Base        = $base
        Option      = $option
        NewFiles    = $optionOnlyFiles
    }
}

Write-Host "Duplicate IDs found: $($duplicateGroups.Count)"
Write-Host "Already merged: $($alreadyMerged.Count)"
Write-Host "Ready to merge: $($mergePlans.Count)"
foreach ($plan in $mergePlans) {
    $newFileNames = ($plan.NewFiles.Name | Sort-Object) -join ', '
    Write-Host "  $($plan.Id) $($plan.Name): $($plan.Option.Package) -> A000; add $newFileNames"
}

if ($mergePlans.Count -eq 0) {
    return
}

if (-not $PSCmdlet.ShouldProcess($a000MusicRoot, "Back up and merge $($mergePlans.Count) option chart definitions")) {
    return
}

New-Item -ItemType Directory -Path $fullBackupRoot -Force | Out-Null

foreach ($plan in $mergePlans) {
    $backupDirectory = Join-Path $fullBackupRoot (Split-Path -Leaf $plan.Base.Directory)
    if (Test-Path -LiteralPath $backupDirectory) {
        throw "Backup directory already exists: $backupDirectory"
    }
    Copy-Item -LiteralPath $plan.Base.Directory -Destination $backupDirectory -Recurse
}

$manifest = @($mergePlans | ForEach-Object {
        [pscustomobject]@{
            Id            = $_.Id
            Name          = $_.Name
            SourcePackage = $_.Option.Package
            Source        = $_.Option.Directory
            Target        = $_.Base.Directory
            AddedFiles    = @($_.NewFiles.Name | Sort-Object)
        }
})
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $fullBackupRoot 'merge-manifest.json') -Encoding utf8

foreach ($plan in $mergePlans) {
    foreach ($newFile in $plan.NewFiles) {
        Copy-Item -LiteralPath $newFile.FullName -Destination (Join-Path $plan.Base.Directory $newFile.Name)
    }
    Copy-Item -LiteralPath $plan.Option.XmlPath -Destination $plan.Base.XmlPath -Force
}

foreach ($plan in $mergePlans) {
    foreach ($newFile in $plan.NewFiles) {
        $targetFile = Join-Path $plan.Base.Directory $newFile.Name
        if (-not (Test-FileContentEqual -Left $newFile.FullName -Right $targetFile)) {
            throw "Verification failed for merged file: $targetFile"
        }
    }
    if (-not (Test-FileContentEqual -Left $plan.Option.XmlPath -Right $plan.Base.XmlPath)) {
        throw "Verification failed for merged Music.xml: $($plan.Base.XmlPath)"
    }
}

Write-Host "Merge completed and verified."
Write-Host "Backup: $fullBackupRoot"
