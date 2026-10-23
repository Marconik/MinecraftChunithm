$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
$atlas=[Drawing.Bitmap]::new(832,192)
$g=[Drawing.Graphics]::FromImage($atlas)
try {
    for($z=0;$z -lt 3;$z++){for($x=0;$x -lt 13;$x++){
        $name="ground_slide_${x}_${z}"
        $tile=[Drawing.Bitmap]::FromFile((Join-Path $assetRoot "textures/block/ground_slide/$name.png"))
        $pressedTile=[Drawing.Bitmap]::FromFile((Join-Path $assetRoot "textures/block/ground_slide/${name}_pressed.png"))
        try {
            if($tile.Width-ne 64-or $tile.Height-ne 64){throw "Wrong texture size: $name"}
            $g.DrawImageUnscaled($tile,$x*64,$z*64)
            if($pressedTile.Width-ne 64-or $pressedTile.Height-ne 64){throw "Wrong pressed texture size: $name"}
            for($pz=0;$pz-lt 64;$pz++){for($px=0;$px-lt 64;$px++){
                $normal=$tile.GetPixel($px,$pz);$red=$pressedTile.GetPixel($px,$pz)
                $inside=($x*64+$px)-ge 160-and ($x*64+$px)-lt 672-and ($z*64+$pz)-ge 32-and ($z*64+$pz)-lt 160
                $divider=$normal.ToArgb()-in @([Drawing.ColorTranslator]::FromHtml('#fffef1').ToArgb(),[Drawing.ColorTranslator]::FromHtml('#aa52e8').ToArgb(),[Drawing.ColorTranslator]::FromHtml('#ca88f7').ToArgb())
                if($inside-and -not $divider){
                    if($red.R-lt 200-or $red.G-gt 100-or $red.B-gt 90){throw "Pressed lane is not red: $name $px,$pz"}
                } elseif($normal.ToArgb()-ne $red.ToArgb()){throw "Pressed state changes divider or cabinet: $name $px,$pz"}
            }}
        } finally {$tile.Dispose();$pressedTile.Dispose()}
        $model=Get-Content -Raw (Join-Path $assetRoot "models/block/$name.json") | ConvertFrom-Json
        $tops=@($model.elements | Where-Object {$null-ne $_.faces.up})
        foreach($element in $model.elements){
            if($element.from[1]-lt 0-or $element.to[1]-gt 16){throw "Cabinet exceeds one-block height: $name"}
        }
        # Check a full 16x16 grid per tile: every point has one visible top,
        # at Y=12 inside the aperture and Y=16 everywhere else. This catches
        # both an overhanging full-cube lid and missing strips at the recess walls.
        for($sz=0;$sz-lt 16;$sz++){for($sx=0;$sx-lt 16;$sx++){
            $px=$sx+.5;$pz=$sz+.5
            $inside=($x*16+$px)-ge 40-and ($x*16+$px)-lt 168-and ($z*16+$pz)-ge 8-and ($z*16+$pz)-lt 40
            $cover=@($tops | Where-Object {$px-ge $_.from[0]-and $px-lt $_.to[0]-and $pz-ge $_.from[2]-and $pz-lt $_.to[2]})
            if($cover.Count-ne 1){throw "Overlapping or missing surface: $name $sx,$sz"}
            $top=$cover[0];$height=if($inside){12}else{16}
            if($top.to[1]-ne $height){throw "Wrong recessed surface height: $name $sx,$sz"}
            $uv=@($top.from[0],$top.from[2],$top.to[0],$top.to[2]) -join ','
            if(($top.faces.up.uv -join ',')-ne $uv){throw "Surface UV no longer follows atlas: $name"}
            if($inside-and ($top.shade-ne $false-or $top.light_emission-ne 15-or $top.from[1]-ne 12)){
                throw "Non-emissive or misplaced touch surface: $name"
            }
        }}
        if($model.textures.top-ne "minecraftchunithm:block/ground_slide/$name"){throw "Wrong tile reference: $name"}
        $states=Get-Content -Raw (Join-Path $assetRoot "blockstates/$name.json") | ConvertFrom-Json
        # Placement SOUTH = +X,+Z; WEST = -Z,+X; NORTH = -X,-Z; EAST = +Z,-X.
        for($mask=0;$mask-lt 4;$mask++){
            $modelName=if($mask-eq 0){$name}else{"${name}_pressed_$mask"}
            $pressedModel=Get-Content -Raw (Join-Path $assetRoot "models/block/$modelName.json") | ConvertFrom-Json
            if($pressedModel.elements.Count-ne $model.elements.Count){throw "Pressed state changes geometry: $modelName"}
            for($i=0;$i-lt $model.elements.Count;$i++){
                $normalElement=$model.elements[$i];$element=$pressedModel.elements[$i]
                if(($element.from -join ',')-ne ($normalElement.from -join ',')-or ($element.to -join ',')-ne ($normalElement.to -join ',')){
                    throw "Pressed state moves the model: $modelName"
                }
                if($element.light_emission-eq 15){
                    $half=[int][Math]::Floor($element.from[0]/8)
                    $texture=if(($mask-band (1-shl $half))-ne 0){'#pressed'}else{'#top'}
                    if($element.faces.up.texture-ne $texture){throw "Half-block lane isolation failed: $modelName"}
                } elseif($element.faces.up-and $element.faces.up.texture-ne '#top'){throw "Cabinet turns red: $modelName"}
            }
            foreach($entry in @(@('south',0),@('west',90),@('north',180),@('east',270))){
                $variant=$states.variants.PSObject.Properties["facing=$($entry[0]),pressed=$mask"].Value
                if([int]$variant.y-ne $entry[1]-or $variant.model-ne "minecraftchunithm:block/$modelName"){
                    throw "Tile orientation differs from Placement frame: $name $($entry[0]) mask $mask"
                }
            }
        }
    }}
    # Every X column of the entire 8x2 window must stay identical along its depth.
    # This also checks both tile-row seams, preventing the screenshot's black bands.
    for($x=160;$x-lt 672;$x++){
        $expected=$atlas.GetPixel($x,32).ToArgb()
        for($z=33;$z-lt 160;$z++){
            if($atlas.GetPixel($x,$z).ToArgb()-ne $expected){throw "Broken longitudinal continuity at $x,$z"}
        }
    }
    $white=[Drawing.ColorTranslator]::FromHtml('#fffef1').ToArgb()
    $purple=[Drawing.ColorTranslator]::FromHtml('#ca88f7').ToArgb()
    for($lane=1;$lane-lt 16;$lane++){
        $expected=if(($lane%4)-eq 0){$purple}else{$white}
        if($atlas.GetPixel((160+$lane*32),96).ToArgb()-ne $expected){throw "Wrong divider at lane $lane"}
    }
    foreach($lane in @(4,8,12)){
        for($x=(160+$lane*32-2);$x-lt (160+$lane*32+2);$x++){
            if($atlas.GetPixel($x,96).ToArgb()-eq $white){throw "White divider overlaps purple at lane $lane"}
        }
    }
    for($lane=0;$lane-lt 16;$lane++){
        $centre=$atlas.GetPixel((160+$lane*32+15),96)
        $edge=$atlas.GetPixel((160+$lane*32+4),96)
        if($centre.G-le $edge.G-or $centre.B-le $edge.B){throw "Lane $lane does not glow from centre to sides"}
    }
    # Sample outside the coin plate to measure the exact central window bounds.
    for($x=0;$x-lt 832;$x++){
        $lit=$atlas.GetPixel($x,40).R-gt 100
        if($lit-ne ($x-ge 160-and $x-lt 672)){throw "Light window or 2.5-block side border has wrong width at $x"}
    }
    $slot=[Drawing.ColorTranslator]::FromHtml('#020304').ToArgb()
    $slotPixels=0
    for($z=0;$z-lt 192;$z++){for($x=672;$x-lt 832;$x++){
        if($atlas.GetPixel($x,$z).ToArgb()-eq $slot){
            if($x-lt 750-or $x-ge 754-or $z-lt 68-or $z-ge 124){throw 'Coin slot is off-centre or not longitudinal'}
            $slotPixels++
        }
    }}
    if($slotPixels-ne (4*56)){throw 'Incomplete decorative coin slot'}
    Write-Output 'Ground Slide assets passed: 39 tiles, four facing frames and pressure masks, isolated red half-lanes, unchanged geometry/dividers/cabinet, 1-block rim, 0.75-block touch surface, 8x2 window and centred coin slot.'
} finally {$g.Dispose();$atlas.Dispose()}
