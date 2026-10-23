$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
$textureRoot=Join-Path $assetRoot 'textures/block/ground_slide'
New-Item -ItemType Directory -Force $textureRoot | Out-Null

# 64 pixels per block: 2.5-block side borders and half-block front/back borders.
$pixels=64
$width=13
$lightLeft=($width-8)*$pixels/2
$image=[Drawing.Bitmap]::new(($width*$pixels),(3*$pixels))
$g=[Drawing.Graphics]::FromImage($image)
$g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::None
$g.Clear([Drawing.ColorTranslator]::FromHtml('#101114'))
function Brush([string]$color){[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))}
function Rect($color,$x,$y,$w,$h){$b=Brush $color;$g.FillRectangle($b,$x,$y,$w,$h);$b.Dispose()}
function CabinetElement($x0,$y0,$z0,$x1,$y1,$z1){
    # The rim top uses the same atlas coordinates as the recessed floor.
    $faces=[ordered]@{}
    foreach($face in @('down','north','south','west','east')){$faces[$face]=@{texture='#side';uv=@(0,0,16,16)}}
    if($y1-eq 16){$faces['up']=@{texture='#top';uv=@($x0,$z0,$x1,$z1)}}
    return [ordered]@{from=@($x0,$y0,$z0);to=@($x1,$y1,$z1);shade=$true;faces=$faces}
}

# Cabinet bevel and recessed rim.
Rect '#25272b' 8 8 ($width*$pixels-16) 176
Rect '#07080a' 20 20 ($width*$pixels-40) 152
Rect '#4b4e52' ($lightLeft-4) 28 520 136

# Each half-block lane glows at its centre and fades toward its left/right edges.
# Its colour is constant along Z, including across the three block-row seams.
for($lane=0;$lane -lt 16;$lane++){
    for($column=0;$column -lt 32;$column++){
        $centre=1-[Math]::Abs(($column-15.5)/15.5)
        $r=[int][Math]::Round(246+9*$centre)
        $green=[int][Math]::Round(226+25*$centre)
        $blue=[int][Math]::Round(153+55*$centre)
        $color='#{0:x2}{1:x2}{2:x2}' -f $r,$green,$blue
        Rect $color ($lightLeft+$lane*32+$column) 32 1 128
    }
}

# Quarter dividers replace the white lines at boundaries 4, 8 and 12.
for($lane=1;$lane -lt 16;$lane++){
    if(($lane%4)-eq 0){continue}
    $x=$lightLeft+$lane*32
    Rect '#fffef1' ($x-1) 32 2 128
}
foreach($lane in @(4,8,12)){
    $x=$lightLeft+$lane*32
    Rect '#aa52e8' ($x-2) 32 4 128
    Rect '#ca88f7' ($x-1) 32 2 128
}

# Decorative top-mounted coin slot, centred on the right blank border (X=11.75,
# Z=1.5 blocks). Its long axis follows the cabinet depth. No interaction or inventory.
$coinCentre=($lightLeft+8*$pixels+$width*$pixels)/2
Rect '#30343a' ($coinCentre-18) 46 36 100
Rect '#a8aeb4' ($coinCentre-16) 48 32 96
Rect '#71777f' ($coinCentre-14) 50 28 92
Rect '#51565e' ($coinCentre+14) 50 2 92
Rect '#34383e' ($coinCentre-5) 64 10 64
Rect '#020304' ($coinCentre-2) 68 4 56
Rect '#c6cbd0' ($coinCentre-6) 64 1 64
Rect '#d1d5d8' ($coinCentre-1) 54 2 2
Rect '#363b42' ($coinCentre-1) 136 2 2

# Pressed atlas changes only the lit lane interiors. Divider lines, casing and
# coin decoration stay identical, and the red light keeps its horizontal gradient.
$pressedImage=[Drawing.Bitmap]$image.Clone()
$dividerColors=[Collections.Generic.HashSet[int]]::new()
foreach($color in @('#fffef1','#aa52e8','#ca88f7')){
    [void]$dividerColors.Add([Drawing.ColorTranslator]::FromHtml($color).ToArgb())
}
for($lane=0;$lane-lt 16;$lane++){for($column=0;$column-lt 32;$column++){
    $centre=1-[Math]::Abs(($column-15.5)/15.5)
    $red=[Drawing.Color]::FromArgb(255,[int][Math]::Round(207+48*$centre),[int][Math]::Round(28+72*$centre),[int][Math]::Round(34+56*$centre))
    $px=$lightLeft+$lane*32+$column
    for($pz=32;$pz-lt 160;$pz++){
        if(-not $dividerColors.Contains($image.GetPixel($px,$pz).ToArgb())){$pressedImage.SetPixel($px,$pz,$red)}
    }
}}

$preview=Join-Path $PSScriptRoot '../docs/ground-slide-preview.png'
$large=[Drawing.Bitmap]::new(($width*128),384);$lg=[Drawing.Graphics]::FromImage($large)
$lg.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$lg.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
$lg.DrawImage($image,0,0,($width*128),384);$lg.Dispose()
$large.Save($preview,[Drawing.Imaging.ImageFormat]::Png);$large.Dispose()
for($z=0;$z -lt 3;$z++) {for($x=0;$x -lt $width;$x++) {
    $name="ground_slide_${x}_${z}"
    $tile=$image.Clone([Drawing.Rectangle]::new($x*$pixels,$z*$pixels,$pixels,$pixels),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $tile.Save((Join-Path $textureRoot "$name.png"),[Drawing.Imaging.ImageFormat]::Png);$tile.Dispose()
    $tile=$pressedImage.Clone([Drawing.Rectangle]::new($x*$pixels,$z*$pixels,$pixels,$pixels),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $tile.Save((Join-Path $textureRoot "${name}_pressed.png"),[Drawing.Imaging.ImageFormat]::Png);$tile.Dispose()
    # Clip the exact central 8x2 opening against this tile. All other parts of
    # the cabinet reach Y=16; only the touch surface is at Y=12 (rail height).
    $x0=[Math]::Max(0,($lightLeft-$x*$pixels)/4)
    $x1=[Math]::Min(16,($lightLeft+8*$pixels-$x*$pixels)/4)
    $z0=[Math]::Max(0,(32-$z*$pixels)/4)
    $z1=[Math]::Min(16,(160-$z*$pixels)/4)
    $elements=@()
    if($x0-ge $x1-or $z0-ge $z1){$elements+=CabinetElement 0 0 0 16 16 16}
    else {
        $elements+=CabinetElement 0 0 0 16 12 16
        foreach($rim in @(@(0,0,$x0,16),@($x1,0,16,16),@($x0,0,$x1,$z0),@($x0,$z1,$x1,16))){
            if($rim[0]-lt $rim[2]-and $rim[1]-lt $rim[3]){
                $elements+=CabinetElement $rim[0] 12 $rim[1] $rim[2] 16 $rim[3]
            }
        }
        # Split at the half-block boundary so a neighbouring lane can remain yellow.
        foreach($half in @(0,1)){
            $min=[Math]::Max($x0,$half*8);$max=[Math]::Min($x1,($half+1)*8)
            if($min-lt $max){$elements+=[ordered]@{from=@($min,12,$z0);to=@($max,12,$z1);shade=$false;light_emission=15
                faces=@{up=@{texture='#top';uv=@($min,$z0,$max,$z1)}}}}
        }
    }
    $model=[ordered]@{
        parent='minecraft:block/block';ambientocclusion=$false
        textures=[ordered]@{top="minecraftchunithm:block/ground_slide/$name";pressed="minecraftchunithm:block/ground_slide/${name}_pressed";side='minecraftchunithm:block/ground_slide/side';particle='minecraftchunithm:block/ground_slide/side'}
        elements=$elements
    }
    $variants=[ordered]@{variants=[ordered]@{}}
    for($mask=0;$mask-lt 4;$mask++){
        foreach($element in $elements){
            if($element.light_emission-eq 15){
                $half=[int][Math]::Floor($element.from[0]/8)
                $element.faces.up.texture=if(($mask-band (1-shl $half))-ne 0){'#pressed'}else{'#top'}
            }
        }
        $modelName=if($mask-eq 0){$name}else{"${name}_pressed_$mask"}
        $model | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 (Join-Path $assetRoot "models/block/$modelName.json")
        # Placement's unrotated frame is SOUTH: local X=EAST, local Z=SOUTH.
        foreach($entry in @(@('south',0),@('west',90),@('north',180),@('east',270))){
            $variants.variants["facing=$($entry[0]),pressed=$mask"]=@{model="minecraftchunithm:block/$modelName";y=$entry[1]}
        }
    }
    $variants | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $assetRoot "blockstates/$name.json")
}}
$g.Dispose();$image.Dispose();$pressedImage.Dispose()
$side=[Drawing.Bitmap]::new(16,16);$sg=[Drawing.Graphics]::FromImage($side);$sg.Clear([Drawing.ColorTranslator]::FromHtml('#0b0c0e'))
$sb=[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#181a1d'));$sg.FillRectangle($sb,0,0,16,2);$sb.Dispose()
$sg.Dispose();$side.Save((Join-Path $textureRoot 'side.png'),[Drawing.Imaging.ImageFormat]::Png);$side.Dispose()
Write-Output 'Ground Slide generated: 13x3 footprint, 1-block rim height, 8x2 light surface recessed to 0.75 blocks, 16 lanes, four major regions and a decorative coin slot.'
