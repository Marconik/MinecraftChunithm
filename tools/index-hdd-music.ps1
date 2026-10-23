[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string] $DataRoot,

    [Parameter(Position = 1)]
    [string] $OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $PSScriptRoot '..\build\hdd-music-index.csv'
}

function Resolve-MusicPackages {
    param([Parameter(Mandatory = $true)][string] $Root)

    $resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
    $leafName = Split-Path -Leaf $resolvedRoot

    if ($leafName -eq 'music') {
        $packageName = Split-Path -Leaf (Split-Path -Parent $resolvedRoot)
        return [pscustomobject]@{
            PackageName = $packageName
            MusicRoot   = $resolvedRoot
        }
    }

    if ($leafName -match '^A\d+$') {
        $musicRoot = Join-Path $resolvedRoot 'music'
        if (-not (Test-Path -LiteralPath $musicRoot -PathType Container)) {
            throw "The package does not contain a music directory: $resolvedRoot"
        }

        return [pscustomobject]@{
            PackageName = $leafName
            MusicRoot   = (Resolve-Path -LiteralPath $musicRoot).Path
        }
    }

    $packages = @(Get-ChildItem -LiteralPath $resolvedRoot -Directory |
            Where-Object { $_.Name -match '^A\d+$' } |
            Sort-Object { [int]$_.Name.Substring(1) } |
            ForEach-Object {
                $musicRoot = Join-Path $_.FullName 'music'
                if (Test-Path -LiteralPath $musicRoot -PathType Container) {
                    [pscustomobject]@{
                        PackageName = $_.Name
                        MusicRoot   = (Resolve-Path -LiteralPath $musicRoot).Path
                    }
                }
            })

    if ($packages.Count -gt 0) {
        return $packages
    }

    throw "No A-numbered package with a music directory was found in: $Root"
}

function Read-MusicRecord {
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

    $name = $nameNode.InnerText.Trim()
    if ([string]::IsNullOrWhiteSpace($name)) {
        throw "The song name is empty: $($XmlFile.FullName)"
    }

    $packageNumber = -1
    if ($PackageName -match '^A(\d+)$') {
        $packageNumber = [int]$Matches[1]
    }

    $enabledFumenCount = $document.SelectNodes("//MusicFumenData[enable='true']").Count
    $cueIdNode = $document.SelectSingleNode('/MusicData/cueFileName/id')
    $cueId = $id
    if ($null -ne $cueIdNode -and -not [int]::TryParse($cueIdNode.InnerText.Trim(), [ref] $cueId)) {
        throw "The audio cue ID is not a valid integer: $($XmlFile.FullName)"
    }

    [pscustomobject]@{
        Id                = $id
        Name              = $name
        Location          = $XmlFile.Directory.FullName
        Package           = $PackageName
        PackageNumber     = $packageNumber
        EnabledFumenCount = $enabledFumenCount
        CueId             = $cueId
    }
}

function Resolve-AudioRecord {
    param(
        [Parameter(Mandatory = $true)] $MusicRecord,
        [Parameter(Mandatory = $true)][hashtable] $AudioDirectories
    )

    $candidates = @()
    # Music.xml can explicitly share another song's audio (e.g. chart variants).
    foreach ($cueId in @($MusicRecord.CueId, $MusicRecord.Id) | Select-Object -Unique) {
        if ($AudioDirectories.ContainsKey($cueId)) {
            $candidates = @($AudioDirectories[$cueId])
            break
        }
    }
    if ($candidates.Count -eq 0) {
        Write-Warning "No audio directory for song $($MusicRecord.Id) (cue ID $($MusicRecord.CueId))."
        return [pscustomobject]@{ AudioLocation = ''; AcbPath = ''; AwbPath = '' }
    }

    $selected = $candidates | Sort-Object -Property `
        @{ Expression = { if ($_.Package -eq $MusicRecord.Package) { 1 } else { 0 } }; Descending = $true },
        @{ Expression = { if ($_.Package -eq 'A000') { 1 } else { 0 } }; Descending = $true },
        @{ Expression = { $_.PackageNumber }; Descending = $true } | Select-Object -First 1

    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [System.Xml.XmlReader]::Create((Join-Path $selected.Location 'CueFile.xml'), $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
    }
    finally {
        $reader.Dispose()
    }

    $paths = @{}
    foreach ($kind in @('acb', 'awb')) {
        $node = $document.SelectSingleNode("/CueFileData/$($kind)File/path")
        if ($null -eq $node -or [string]::IsNullOrWhiteSpace($node.InnerText)) {
            throw "Missing $kind path in audio definition: $($selected.Location)"
        }
        $path = [System.IO.Path]::GetFullPath((Join-Path $selected.Location $node.InnerText.Trim()))
        $expectedPrefix = $selected.Location.TrimEnd('\') + '\'
        if (-not $path.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Audio path escapes its cue directory: $path"
        }
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Audio file is missing for song $($MusicRecord.Id): $path"
        }
        $paths[$kind] = $path
    }

    [pscustomobject]@{
        AudioLocation = $selected.Location
        AcbPath       = $paths['acb']
        AwbPath       = $paths['awb']
    }
}

$musicPackages = @(Resolve-MusicPackages -Root $DataRoot)
$audioDirectories = @{}
foreach ($package in $musicPackages) {
    $cueRoot = Join-Path (Split-Path -Parent $package.MusicRoot) 'cueFile'
    if (-not (Test-Path -LiteralPath $cueRoot -PathType Container)) { continue }
    foreach ($directory in Get-ChildItem -LiteralPath $cueRoot -Directory) {
        if ($directory.Name -notmatch '^cueFile(\d{6})$') { continue }
        $cueId = [int]$Matches[1]
        $candidate = [pscustomobject]@{
            Package       = $package.PackageName
            PackageNumber = [int]$package.PackageName.Substring(1)
            Location      = $directory.FullName
        }
        if (-not $audioDirectories.ContainsKey($cueId)) { $audioDirectories[$cueId] = @() }
        $audioDirectories[$cueId] += $candidate
    }
}
$records = @(foreach ($package in $musicPackages) {
    $musicDirectories = @(Get-ChildItem -LiteralPath $package.MusicRoot -Directory |
            Where-Object { $_.Name -match '^music\d+$' } |
            Sort-Object Name)

    foreach ($directory in $musicDirectories) {
        $xmlPath = Join-Path $directory.FullName 'Music.xml'
        if (-not (Test-Path -LiteralPath $xmlPath -PathType Leaf)) {
            throw "Music.xml is missing from: $($directory.FullName)"
        }

        Read-MusicRecord -XmlFile (Get-Item -LiteralPath $xmlPath) -PackageName $package.PackageName
    }
})

if ($records.Count -eq 0) {
    throw "No Music.xml files were found under the selected package roots."
}

$duplicateGroups = @($records | Group-Object Id | Where-Object Count -gt 1)
foreach ($group in $duplicateGroups) {
    $names = @($group.Group.Name | Sort-Object -Unique)
    if ($names.Count -gt 1) {
        $locations = ($group.Group | ForEach-Object { "$($_.Package): $($_.Name) @ $($_.Location)" }) -join '; '
        throw "Song ID $($group.Name) has conflicting names: $locations"
    }
}

$mergedRecords = foreach ($group in ($records | Group-Object Id)) {
    $group.Group |
        Sort-Object -Property `
            @{ Expression = { $_.EnabledFumenCount }; Descending = $true },
            @{ Expression = { if ($_.Package -eq 'A000') { 1 } else { 0 } }; Descending = $true },
            @{ Expression = { $_.PackageNumber }; Descending = $true },
            @{ Expression = { $_.Location }; Descending = $true } |
        Select-Object -First 1
}

$sortedRecords = @(foreach ($record in ($mergedRecords | Sort-Object Id, Name)) {
    $audio = Resolve-AudioRecord -MusicRecord $record -AudioDirectories $audioDirectories
    [pscustomobject]@{
        Id            = $record.Id
        Name          = $record.Name
        Location      = $record.Location
        AudioLocation = $audio.AudioLocation
        AcbPath       = $audio.AcbPath
        AwbPath       = $audio.AwbPath
    }
})
$fullOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $fullOutputPath
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

# Excel needs a UTF-8 BOM for reliable Japanese/Chinese detection.
# PowerShell 5 uses BOM with utf8; PowerShell 6+ requires utf8BOM.
$csvEncoding = if ($PSVersionTable.PSVersion.Major -ge 6) { 'utf8BOM' } else { 'utf8' }
$sortedRecords | Export-Csv -LiteralPath $fullOutputPath -NoTypeInformation -Encoding $csvEncoding

Write-Host "Indexed $($sortedRecords.Count) songs."
Write-Host "Packages scanned: $($musicPackages.Count)"
Write-Host "Music definitions scanned: $($records.Count)"
Write-Host "Duplicate overlays applied: $($duplicateGroups.Count)"
Write-Host "Songs with audio paths: $(@($sortedRecords | Where-Object { $_.AwbPath -ne '' }).Count)"
Write-Host "Output: $fullOutputPath"
Write-Host "ID range: $($sortedRecords[0].Id) - $($sortedRecords[-1].Id)"
