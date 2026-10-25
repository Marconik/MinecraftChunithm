$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
foreach($side in @('left','right')){
    $ports=@()
    for($row=0;$row-lt 4;$row++){for($z=0;$z-lt 3;$z++){
        $name="air_sensor_${side}_${row}_${z}"
        $model=Get-Content -Raw (Join-Path $root "models/block/$name.json") | ConvertFrom-Json
        $body=$model.elements[0]
        if(($body.from -join ',')-ne '0,0,0'-or ($body.to -join ',')-ne '16,16,16'){throw "Wrong panel body: $name"}
        foreach($face in @('north','south','east','west')){
            $inside=($side-eq 'left'-and $face-eq 'east')-or ($side-eq 'right'-and $face-eq 'west')
            if($inside){if($null-ne $body.faces.$face){throw "Unframed inner face covers the surface: $name"}}
            elseif($body.faces.$face.texture-ne '#white'){throw "Incorrect white side: $name $face"}
        }
        $insideFace=if($side-eq 'left'){'east'}else{'west'}
        $surface=@($model.elements | Where-Object {$null-ne $_.faces.$insideFace-and $_.faces.$insideFace.texture-eq '#surface'})
        if($surface.Count-ne 1){throw "Missing polished panel surface: $name"}
        $surface=$surface[0]
        $border=16.0/6
        $expected=@([Math]::Max(0,($border-$row*16)),[Math]::Min(16,(64-$border-$row*16)),[Math]::Max(0,($border-$z*16)),[Math]::Min(16,(48-$border-$z*16)))
        $actual=@($surface.from[1],$surface.to[1],$surface.from[2],$surface.to[2])
        for($i=0;$i-lt 4;$i++){if([Math]::Abs($expected[$i]-$actual[$i])-gt .000001){throw "Panel white rim is not exactly 1/6 block: $name"}}
        $otherSide=if($side-eq 'left'){'right'}else{'left'}
        $mirror=Get-Content -Raw (Join-Path $root "models/block/air_sensor_${otherSide}_${row}_${z}.json") | ConvertFrom-Json
        if($model.elements.Count-ne $mirror.elements.Count){throw "Asymmetric panel details: $name"}
        for($i=0;$i-lt $model.elements.Count;$i++){
            $a=$model.elements[$i];$b=$mirror.elements[$i]
            if([Math]::Abs($a.from[0]+$b.to[0]-16)-gt .000001-or [Math]::Abs($a.to[0]+$b.from[0]-16)-gt .000001-or
                $a.from[1]-ne $b.from[1]-or $a.to[1]-ne $b.to[1]-or $a.from[2]-ne $b.from[2]-or $a.to[2]-ne $b.to[2]){
                throw "Cuboid not mirrored exactly: $name element $i"
            }
            if($a.light_emission-eq 15){
                if($z-ne 1){throw "IR aperture is not centred along depth: $name"}
                $texture='#red'
                if($a.faces.east.texture-ne $texture-or $a.faces.west.texture-ne $texture-or $a.shade-ne $false){throw "Incorrect IR emission: $name"}
                $ports+=($row+($a.from[1]+$a.to[1])/32)
            }
        }
        $states=Get-Content -Raw (Join-Path $root "blockstates/$name.json") | ConvertFrom-Json
        foreach($entry in @(@('south',0),@('west',90),@('north',180),@('east',270))){
            $variant=$states.variants.PSObject.Properties["facing=$($entry[0])"].Value
            if([int]$variant.y-ne $entry[1]-or $variant.model-ne "minecraftchunithm:block/$name"){throw "Wrong oriented panel model: $name"}
        }
    }}
    $ports=@($ports | Sort-Object)
    if($ports.Count-ne 5){throw "$side must have five IR apertures"}
    for($i=0;$i-lt 5;$i++){if($ports[$i]-ne 1+$i*.5){throw "$side IR aperture has wrong height"}}
}
$base=Get-Content -Raw (Join-Path $root 'models/block/air_sensor_base.json') | ConvertFrom-Json
if($base.elements.Count-ne 1-or ($base.elements[0].to -join ',')-ne '16,16,16'){throw 'Base must use full-block white tiles'}
foreach($entry in @(@('white','#edf0f3'),@('green','#34ff58'),@('red','#ffb35c'))){
    $texture=[Drawing.Bitmap]::FromFile((Join-Path $root "textures/block/air_sensors/$($entry[0]).png"))
    try{if($texture.GetPixel(8,8).ToArgb()-ne [Drawing.ColorTranslator]::FromHtml($entry[1]).ToArgb()){throw 'Wrong AIR material colour'}}finally{$texture.Dispose()}
}
$atlases=@{}
try {
    foreach($side in @('left','right')){
        $atlas=[Drawing.Bitmap]::new(320,192);$atlases[$side]=$atlas;$g=[Drawing.Graphics]::FromImage($atlas)
        try{
            for($x=0;$x-lt 5;$x++){for($z=0;$z-lt 3;$z++){
                $name="air_sensor_base_${side}_${x}_${z}"
                $model=Get-Content -Raw (Join-Path $root "models/block/$name.json") | ConvertFrom-Json
                if($model.elements[0].faces.up){throw "Plain base lid covers new surface: $name"}
                $surfaces=@($model.elements | Where-Object {$_.faces.up.texture-eq '#surface'})
                $underWall=($side-eq 'left'-and $x-eq 0)-or ($side-eq 'right'-and $x-eq 4)
                if($underWall){if($surfaces.Count-ne 0){throw "Upright footprint should stay white: $name"}}
                else {
                    if($surfaces.Count-ne 1){throw "Missing framed base surface: $name"}
                    $surface=$surfaces[0]
                    $floorMin=if($side-eq 'left'){16+$border}else{$border}
                    $floorMax=if($side-eq 'left'){80-$border}else{64-$border}
                    $expected=@([Math]::Max(0,($floorMin-$x*16)),[Math]::Min(16,($floorMax-$x*16)),[Math]::Max(0,($border-$z*16)),[Math]::Min(16,(48-$border-$z*16)))
                    $actual=@($surface.from[0],$surface.to[0],$surface.from[2],$surface.to[2])
                    for($i=0;$i-lt 4;$i++){if([Math]::Abs($expected[$i]-$actual[$i])-gt .000001){throw "Base white rim is not exactly 1/6 block: $name"}}
                    if($surface.from[1]-ne 16-or $surface.to[1]-ne 16){throw "Base surface changed height: $name"}
                }
                $texture=[Drawing.Bitmap]::FromFile((Join-Path $root "textures/block/air_sensors/$name.png"))
                try{$g.DrawImageUnscaled($texture,$x*64,$z*64)}finally{$texture.Dispose()}
            }}
        }finally{$g.Dispose()}
    }
    $arrow=[Drawing.ColorTranslator]::FromHtml('#d9dfe5').ToArgb()
    $arrowPixels=[Collections.Generic.HashSet[int]]::new();$stoneColors=[Collections.Generic.HashSet[int]]::new()
    for($z=0;$z-lt 192;$z++){for($x=0;$x-lt 320;$x++){
        $pixel=$atlases.left.GetPixel($x,$z).ToArgb()
        if($pixel-ne $atlases.right.GetPixel((319-$x),$z).ToArgb()){throw 'Base arrows/material are not exactly mirrored'}
        if($pixel-eq $arrow){[void]$arrowPixels.Add(($z*320+$x))}else{[void]$stoneColors.Add($pixel)}
    }}
    if($stoneColors.Count-lt 12){throw 'Polished stone surface is too flat to show grain/highlights'}
    $components=0
    while($arrowPixels.Count-gt 0){
        $seed=@($arrowPixels)[0];[void]$arrowPixels.Remove($seed);$queue=[Collections.Generic.Queue[int]]::new();$queue.Enqueue($seed)
        $minX=320;$maxX=-1;$midMin=320
        while($queue.Count-gt 0){
            $pixel=$queue.Dequeue();$x=$pixel%320;$z=[int][Math]::Floor($pixel/320)
            $minX=[Math]::Min($minX,$x);$maxX=[Math]::Max($maxX,$x)
            if($z-eq 96){$midMin=[Math]::Min($midMin,$x)}
            foreach($adjacent in @(($pixel-1),($pixel+1),($pixel-320),($pixel+320))){if($arrowPixels.Remove($adjacent)){$queue.Enqueue($adjacent)}}
        }
        if($midMin-gt $minX+2-or $maxX-$minX-lt 25){throw 'Arrow does not point from inside toward the left upright'}
        $components++
    }
    if($components-ne 3){throw 'Base must show exactly three chevron arrows'}
}finally{foreach($atlas in $atlases.Values){$atlas.Dispose()}}
Write-Output 'AIR models passed: four-block upright; five heights 1..3, 1/6-block white rims without tile seams, mirrored inward panels, redstone-style emissive red lenses on both sides, polished-stone grain, three mirrored outward arrows and four orientations.'
