$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
$textureRoot=Join-Path $assetRoot 'textures/item/name_panel'
New-Item -ItemType Directory -Force $textureRoot | Out-Null
function Color($hex){[Drawing.ColorTranslator]::FromHtml($hex)}
function Rect($g,$hex,$x,$y,$w,$h){
 $b=[Drawing.SolidBrush]::new((Color $hex));$g.FillRectangle($b,[single]$x,[single]$y,[single]$w,[single]$h);$b.Dispose()
}
function Gradient($g,$top,$bottom,$x,$y,$w,$h){
 $r=[Drawing.Rectangle]::new($x,$y,$w,$h)
 $b=[Drawing.Drawing2D.LinearGradientBrush]::new($r,(Color $top),(Color $bottom),[Drawing.Drawing2D.LinearGradientMode]::Vertical)
 $g.FillRectangle($b,$r);$b.Dispose()
}
function Save($image,$name){
 # The facade is viewed on the generated item's reverse face, which reverses U.
 # Compensate only the baked background; keep the authoring preview readable
 # and leave externally supplied character models in their existing convention.
 $export=$image.Clone()
 if($name -eq 'background'){$export.RotateFlip([Drawing.RotateFlipType]::RotateNoneFlipX)}
 $export.Save((Join-Path $textureRoot "$name.png"),[Drawing.Imaging.ImageFormat]::Png)
 $export.Dispose()
 @{parent='minecraft:item/generated';textures=@{layer0="minecraftchunithm:item/name_panel/$name"}} | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $assetRoot "models/item/name_panel_$name.json")
 $itemDir=Join-Path $assetRoot 'items/name_panel';New-Item -ItemType Directory -Force $itemDir | Out-Null
 @{model=@{type='minecraft:model';model="minecraftchunithm:item/name_panel_$name"}} | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $itemDir "$name.json")
}
# One continuous, smooth facade; no repeated vanilla block textures or coplanar overlays.
$image=[Drawing.Bitmap]::new(896,384);$g=[Drawing.Graphics]::FromImage($image)
Rect $g '#17141b' 0 0 896 384
Gradient $g '#fff3a0' '#c3941e' 4 4 888 376
Rect $g '#282225' 9 9 878 366
Gradient $g '#fff56d' '#ffe9a0' 13 13 870 358
Gradient $g '#fff2a2' '#fff6d6' 22 190 852 172
# TEAM row, full width after removing the penguin.
Rect $g '#b49c4f' 31 28 834 54
Gradient $g '#46464a' '#17191f' 34 31 828 48
Gradient $g '#f8f8f5' '#b7bac0' 34 31 112 48
# Separator and right-side square avatar frame.
Rect $g '#d5b766' 38 282 622 2
Rect $g '#c9af65' 680 199 177 163
Rect $g '#ffffff' 683 202 171 157
Save $image 'background'
$image.Save((Join-Path $PSScriptRoot '../docs/name-panel-preview.png'),[Drawing.Imaging.ImageFormat]::Png)
$g.Dispose();$image.Dispose()
foreach($kind in @('silver','gold')){
 $image=[Drawing.Bitmap]::new(832,72);$g=[Drawing.Graphics]::FromImage($image)
 Rect $g '#c29a29' 0 0 832 72
 Rect $g '#fff7c7' 2 2 828 68
 if($kind -eq 'silver'){
  # Restore the original layered strip: silver rim, grey body, white top band.
  Rect $g '#d8dade' 2 2 828 68
  Rect $g '#a6a8ab' 6 6 820 60
  Rect $g '#fafafa' 6 14 820 19
 }else{
  Rect $g '#f8d34e' 2 2 828 68
  Rect $g '#e2b524' 6 6 820 60
  Rect $g '#fff2b0' 6 14 820 19
 }
 Save $image $kind;$g.Dispose();$image.Dispose()
}
# Neutral character placeholder instead of displaying a large Minecraft name-tag item.
$image=[Drawing.Bitmap]::new(128,128);$g=[Drawing.Graphics]::FromImage($image)
$g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
Gradient $g '#e8edf5' '#b9c7dd' 0 0 128 128
$brush=[Drawing.SolidBrush]::new((Color '#7d8fa9'))
$g.FillEllipse($brush,42,17,44,48);$g.FillEllipse($brush,22,69,84,100)
$brush.Dispose();Save $image 'avatar';$g.Dispose();$image.Dispose()
Write-Output 'Generated smooth name panel, silver/gold title strips and neutral avatar.'
