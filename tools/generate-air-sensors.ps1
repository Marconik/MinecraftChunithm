$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
$textureRoot=Join-Path $assetRoot 'textures/block/air_sensors'
New-Item -ItemType Directory -Force $textureRoot | Out-Null
$pixels=64
$border=16.0/6
foreach($surface in @(@('white','#edf0f3'),@('top','#fafbfc'),@('bottom','#c4cbd2'),@('black','#090b0f'),@('green','#34ff58'),@('red','#ff3933'))){
    $image=[Drawing.Bitmap]::new(16,16);$g=[Drawing.Graphics]::FromImage($image)
    $g.Clear([Drawing.ColorTranslator]::FromHtml($surface[1]));$g.Dispose()
    $image.Save((Join-Path $textureRoot ($surface[0]+'.png')),[Drawing.Imaging.ImageFormat]::Png);$image.Dispose()
}
# Pixel-art redstone-style lens: dark red rim, red body and warm central core.
$lens=[Drawing.Bitmap]::new(16,16)
for($y=0;$y-lt 16;$y++){for($x=0;$x-lt 16;$x++){
    $r=[Math]::Max([Math]::Abs($x-7.5),[Math]::Abs($y-7.5))
    $color=if($r-ge 6){'#8c1016'}elseif($r-ge 4){'#d82025'}elseif($r-ge 2){'#ff4935'}else{'#ffb35c'}
    $lens.SetPixel($x,$y,[Drawing.ColorTranslator]::FromHtml($color))
}}
$lens.Save((Join-Path $textureRoot 'red.png'),[Drawing.Imaging.ImageFormat]::Png);$lens.Dispose()
function Box($x0,$y0,$z0,$x1,$y1,$z1,$texture,$emission=0){
    # Mirror each cuboid through the panel's midplane for the right housing.
    if($script:mirror){$oldX=$x0;$x0=16-$x1;$x1=16-$oldX}
    $faces=[ordered]@{}
    foreach($face in @('up','down','north','south','west','east')){$faces[$face]=@{texture=$texture;uv=@(0,0,16,16)}}
    return [ordered]@{from=@($x0,$y0,$z0);to=@($x1,$y1,$z1);shade=($emission-eq 0);light_emission=$emission;faces=$faces}
}
function Plane($u0,$v0,$u1,$v1,$texture,$vertical){
    if($vertical){
        $x=if($script:mirror){0}else{16}
        $face=if($script:mirror){'west'}else{'east'}
        # Native side-face UVs: front depth goes right-to-left on EAST.
        $uv=if($script:mirror){@($u0,(16-$v1),$u1,(16-$v0))}else{@((16-$u1),(16-$v1),(16-$u0),(16-$v0))}
        return [ordered]@{from=@($x,$v0,$u0);to=@($x,$v1,$u1);shade=$true;faces=@{$face=@{texture=$texture;uv=$uv}}}
    }
    return [ordered]@{from=@($u0,16,$v0);to=@($u1,16,$v1);shade=$true;faces=@{up=@{texture=$texture;uv=@($u0,$v0,$u1,$v1)}}}
}
function FramedSurface($u0,$v0,$u1,$v1,$vertical){
    $parts=@()
    foreach($edge in @(@(0,0,$u0,16),@($u1,0,16,16),@($u0,0,$u1,$v0),@($u0,$v1,$u1,16))){
        if($edge[0]-lt $edge[2]-and $edge[1]-lt $edge[3]){$parts+=Plane $edge[0] $edge[1] $edge[2] $edge[3] '#top' $vertical}
    }
    $parts+=Plane $u0 $v0 $u1 $v1 '#surface' $vertical
    return $parts
}
function PolishedSurface($width,$height){
    $bitmap=[Drawing.Bitmap]::new($width,$height)
    $random=[Random]::new(1803)
    for($y=0;$y-lt $height;$y+=4){for($x=0;$x-lt $width;$x+=4){
        $grain=$random.Next(-3,4)
        for($dy=0;$dy-lt 4;$dy++){for($dx=0;$dx-lt 4;$dx++){
            $distance=($x+$dx)*.45+($y+$dy)-$height*.85
            $gloss=8*[Math]::Exp(-$distance*$distance/1100.0)
            $tone=[int][Math]::Round(26+$grain+$random.Next(-1,2)+$gloss)
            $bitmap.SetPixel(($x+$dx),($y+$dy),[Drawing.Color]::FromArgb(255,$tone,($tone+2),($tone+5)))
        }}
    }}
    return $bitmap
}
function Preview($bitmap,$path){
    $preview=[Drawing.Bitmap]$bitmap.Clone();$pg=[Drawing.Graphics]::FromImage($preview)
    $white=[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#fafbfc'))
    $rim=[single]($pixels/6)
    $pg.FillRectangle($white,0,0,$preview.Width,$rim);$pg.FillRectangle($white,0,($preview.Height-$rim),$preview.Width,$rim)
    $pg.FillRectangle($white,0,0,$rim,$preview.Height);$pg.FillRectangle($white,($preview.Width-$rim),0,$rim,$preview.Height)
    $white.Dispose();$pg.Dispose();$preview.Save($path,[Drawing.Imaging.ImageFormat]::Png);$preview.Dispose()
}
function SaveModel($name,$elements){
    $textures=[ordered]@{particle='minecraftchunithm:block/air_sensors/white'}
    foreach($surface in @('white','top','bottom','black','green','red')){$textures[$surface]="minecraftchunithm:block/air_sensors/$surface"}
    if($script:surfaceTexture){$textures['surface']=$script:surfaceTexture}
    [ordered]@{parent='minecraft:block/block';ambientocclusion=$false;textures=$textures;elements=@($elements)} |
        ConvertTo-Json -Depth 14 | Set-Content -Encoding utf8 (Join-Path $assetRoot "models/block/$name.json")
    $variants=[ordered]@{}
    foreach($entry in @(@('south',0),@('west',90),@('north',180),@('east',270))){
        $variants["facing=$($entry[0])"]=@{model="minecraftchunithm:block/$name";y=$entry[1]}
    }
    @{variants=$variants} | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $assetRoot "blockstates/$name.json")
}

$script:mirror=$false
$base=Box 0 0 0 16 16 16 '#white'
$base.faces.up.texture='#top';$base.faces.down.texture='#bottom'
SaveModel 'air_sensor_base' @($base)
$panelImage=PolishedSurface (3*$pixels) (4*$pixels)
$baseLeftImage=PolishedSurface (5*$pixels) (3*$pixels)
$ag=[Drawing.Graphics]::FromImage($baseLeftImage)
$arrowBrush=[Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#d9dfe5'))
foreach($tip in @(1.65,2.45,3.25)){
    # Three chevrons point from the inner edge toward the outer upright (negative X).
    $points=[Drawing.PointF[]]@(
        [Drawing.PointF]::new(($tip*$pixels),(1.5*$pixels)),
        [Drawing.PointF]::new((($tip+.36)*$pixels),(1.08*$pixels)),
        [Drawing.PointF]::new((($tip+.54)*$pixels),(1.08*$pixels)),
        [Drawing.PointF]::new((($tip+.18)*$pixels),(1.5*$pixels)),
        [Drawing.PointF]::new((($tip+.54)*$pixels),(1.92*$pixels)),
        [Drawing.PointF]::new((($tip+.36)*$pixels),(1.92*$pixels)))
    $ag.FillPolygon($arrowBrush,$points)
}
$arrowBrush.Dispose();$ag.Dispose()
$visibleBase=$baseLeftImage.Clone([Drawing.Rectangle]::new(64,0,256,192),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
Preview $visibleBase (Join-Path $PSScriptRoot '../docs/air-sensor-base-left-preview.png');$visibleBase.Dispose()
Preview $panelImage (Join-Path $PSScriptRoot '../docs/air-sensor-surface-preview.png')
foreach($side in @('left','right')){
    $script:mirror=$side-eq 'right'
    $light='#red'
    $baseImage=[Drawing.Bitmap]$baseLeftImage.Clone()
    if($script:mirror){$baseImage.RotateFlip([Drawing.RotateFlipType]::RotateNoneFlipX)}
    for($x=0;$x-lt 5;$x++){for($z=0;$z-lt 3;$z++){
        $name="air_sensor_base_${side}_${x}_${z}"
        $tile=$baseImage.Clone([Drawing.Rectangle]::new(($x*$pixels),($z*$pixels),$pixels,$pixels),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $tile.Save((Join-Path $textureRoot "$name.png"),[Drawing.Imaging.ImageFormat]::Png);$tile.Dispose()
        $script:surfaceTexture="minecraftchunithm:block/air_sensors/$name"
        # Base geometry stays in each side's +X/+Z placement frame; only the arrows mirror.
        $body=Box 0 0 0 16 16 16 '#white';$body.faces.Remove('up');$body.faces.down.texture='#bottom'
        # Frame the exposed four-block floor, excluding the one-block upright footprint.
        $floorMin=if($side-eq 'left'){16+$border}else{$border}
        $floorMax=if($side-eq 'left'){80-$border}else{64-$border}
        $u0=[Math]::Max(0,($floorMin-$x*16));$u1=[Math]::Min(16,($floorMax-$x*16))
        $v0=[Math]::Max(0,($border-$z*16));$v1=[Math]::Min(16,(48-$border-$z*16))
        $parts=if($u0-ge $u1){@(Plane 0 0 16 16 '#top' $false)}else{@(FramedSurface $u0 $v0 $u1 $v1 $false)}
        SaveModel $name (@($body)+$parts)
    }}
    $baseImage.Dispose()
    for($row=0;$row-lt 4;$row++){for($z=0;$z-lt 3;$z++){
        $body=Box 0 0 0 16 16 16 '#white'
        $body.faces.up.texture='#top';$body.faces.down.texture='#bottom'
        $inside=if($side-eq 'left'){'east'}else{'west'}
        $body.faces.Remove($inside)
        $u0=[Math]::Max(0,($border-$z*16));$u1=[Math]::Min(16,(48-$border-$z*16))
        $v0=[Math]::Max(0,($border-$row*16));$v1=[Math]::Min(16,(64-$border-$row*16))
        $name="air_sensor_${side}_${row}_${z}"
        $tile=$panelImage.Clone([Drawing.Rectangle]::new(($z*$pixels),((3-$row)*$pixels),$pixels,$pixels),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        if(-not $script:mirror){$tile.RotateFlip([Drawing.RotateFlipType]::RotateNoneFlipX)}
        $tile.Save((Join-Path $textureRoot "$name.png"),[Drawing.Imaging.ImageFormat]::Png);$tile.Dispose()
        $script:surfaceTexture="minecraftchunithm:block/air_sensors/$name"
        $elements=@($body)+(FramedSurface $u0 $v0 $u1 $v1 $true)
        if($z-eq 1){for($port=0;$port-lt 5;$port++){
            $height=1+$port*.5
            if([Math]::Floor($height)-ne $row){continue}
            $cy=($height-$row)*16
            # Three-model-pixel outer square, two-pixel opening and one-pixel
            # projection. Four separate white bars leave a real hole in the centre.
            $elements+=Box 16 ($cy-1.5) 6.5 17 ($cy-1) 9.5 '#white'
            $elements+=Box 16 ($cy+1) 6.5 17 ($cy+1.5) 9.5 '#white'
            $elements+=Box 16 ($cy-1) 6.5 17 ($cy+1) 7 '#white'
            $elements+=Box 16 ($cy-1) 9 17 ($cy+1) 9.5 '#white'
            # Dark back of the opening, with the emissive lens inset behind its lip.
            $elements+=Box 16.01 ($cy-1) 7 16.10 ($cy+1) 9 '#black'
            $elements+=Box 16.15 ($cy-.75) 7.25 16.25 ($cy+.75) 8.75 $light 15
        }}
        SaveModel $name $elements
    }}
}
$panelImage.Dispose();$baseLeftImage.Dispose()
Write-Output 'AIR sensor assets generated: four-block upright; five apertures at 1..3 above standing surface; redstone-torch red lenses, exact 1/6-block white frames, continuous black polished-stone faces and mirrored outward chevrons.'
