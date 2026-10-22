$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
$textureRoot=Join-Path $assetRoot 'textures/block/head'
New-Item -ItemType Directory -Force $textureRoot | Out-Null
$image=[Drawing.Bitmap]::new(1408,512)
$g=[Drawing.Graphics]::FromImage($image)
$g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint=[Drawing.Text.TextRenderingHint]::AntiAliasGridFit
function Brush([string]$color){ [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color)) }
function Rect($color,$x,$y,$w,$h){$b=Brush $color;$g.FillRectangle($b,[single]$x,[single]$y,[single]$w,[single]$h);$b.Dispose()}
function Text($text,$x,$y,$size,$color){
 $font=[Drawing.Font]::new('Arial',[single]$size,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Pixel)
 $b=Brush $color;$g.DrawString($text,$font,$b,[single]$x,[single]$y);$b.Dispose();$font.Dispose()
}
function OutlineText($text,$x,$y,$size){
 $p=[Drawing.Drawing2D.GraphicsPath]::new()
 $p.AddString($text,[Drawing.FontFamily]::GenericSansSerif,[int][Drawing.FontStyle]::Bold,[single]$size,[Drawing.PointF]::new($x,$y),[Drawing.StringFormat]::GenericDefault)
 $bounds=$p.GetBounds();$shift=[Drawing.Drawing2D.Matrix]::new();$shift.Translate(0,[single](171.5-($bounds.Y+$bounds.Height/2)));$p.Transform($shift);$shift.Dispose()
 $pen=[Drawing.Pen]::new([Drawing.Color]::White,5);$pen.LineJoin=[Drawing.Drawing2D.LineJoin]::Round;$g.DrawPath($pen,$p);$b=Brush '#252733';$g.FillPath($b,$p)
 $b.Dispose();$pen.Dispose();$p.Dispose()
}
# Gold background continues through the full one-block pedestal.
Rect '#ffd72c' 0 0 1408 512
for($i=-512;$i -lt 1550;$i+=96){
 $points=[Drawing.PointF[]]@([Drawing.PointF]::new($i,512),[Drawing.PointF]::new(($i+35),512),[Drawing.PointF]::new(($i+547),0),[Drawing.PointF]::new(($i+512),0))
 $b=Brush '#ffe65b';$g.FillPolygon($b,$points);$b.Dispose()
}
for($i=0;$i -lt 6;$i++){
 $pen=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#83e5d4'),3)
 $g.DrawLine($pen,($i*11),378,($i*11+112),266);$g.DrawLine($pen,(1336+$i*10),510,(1406+$i*10),440);$pen.Dispose()
}
# Four equal counter sections. Numbers are decorative placeholders only.
Rect '#0a0b0e' 0 0 1408 112
$labels=@('JUSTICE CRITICAL:  0','JUSTICE:  0','ATTACK:  0','MISS:  0')
$colors=@('#fff26c','#ffb631','#58ed39','#bbc0c7')
for($i=0;$i -lt 4;$i++){
 # Dynamic number display replaces the baked counter labels.
 if($i -gt 0){Rect '#7d8088' ($i*352) 20 2 72}
}
# MAX COMBO and SCORE are dynamic text displays.

# Outer progress frame: 1024 pixels / 128 = exactly eight blocks.
Rect '#252830' 192 231 1024 73
Rect '#f6f3ce' 196 235 1016 65
Rect '#121318' 200 239 1008 57
for($y=247;$y -lt 295;$y+=8){Rect '#1c1d21' 201 $y 1006 1}
Rect '#484952' 702 240 2 54
# Completion cells, parallel bevelled outlines and empty interiors.
# Full-bright slanted cell frames and fills are supplied by display entities.
# CLEAR is a dynamic label centred above the fourth cell.
$border=[Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#282a32'),4)
$g.DrawRectangle($border,2,2,1404,508);$border.Dispose()
$preview=Join-Path $PSScriptRoot '../docs/head-preview.png'
$image.Save($preview,[Drawing.Imaging.ImageFormat]::Png)
for($row=0;$row -lt 4;$row++) {for($col=0;$col -lt 11;$col++) {
 $tile=$image.Clone([Drawing.Rectangle]::new(($col*128),((3-$row)*128),128,128),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
 $tile.Save((Join-Path $textureRoot "head_${col}_${row}.png"),[Drawing.Imaging.ImageFormat]::Png);$tile.Dispose()
 $name="head_${col}_${row}"
 $model=@{parent='minecraft:block/cube';textures=@{particle='minecraftchunithm:block/gold';down='minecraftchunithm:block/gold';up='minecraftchunithm:block/gold';north='minecraftchunithm:block/head/black';south="minecraftchunithm:block/head/$name";east='minecraftchunithm:block/head/purple';west='minecraftchunithm:block/head/purple'}}
 $model | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $assetRoot "models/block/$name.json")
 @{variants=@{'facing=south'=@{model="minecraftchunithm:block/$name"};'facing=west'=@{model="minecraftchunithm:block/$name";y=90};'facing=north'=@{model="minecraftchunithm:block/$name";y=180};'facing=east'=@{model="minecraftchunithm:block/$name";y=270}}} | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $assetRoot "blockstates/$name.json")
}}
$g.Dispose();$image.Dispose()
foreach($surface in @(@('purple','#7000c0'),@('black','#08090c'))) {
 $solid=[Drawing.Bitmap]::new(16,16);$sg=[Drawing.Graphics]::FromImage($solid);$sg.Clear([Drawing.ColorTranslator]::FromHtml($surface[1]));$sg.Dispose()
 $solid.Save((Join-Path $textureRoot ($surface[0]+'.png')),[Drawing.Imaging.ImageFormat]::Png);$solid.Dispose()
}
Write-Output 'Head generated: width 11, main height 3, pedestal height 1, depth 1, progress frame width 8.'
