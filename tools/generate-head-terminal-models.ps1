$ErrorActionPreference='Stop'
$assetRoot=Join-Path $PSScriptRoot '../src/main/resources/assets/minecraftchunithm'
# Keep in sync with HeadTerminal: 5/3 x 1 at (5.5, 2.3), recessed by 1/8 block.
function TerminalBox($x0,$y0,$z0,$x1,$y1,$z1){
    if($x0-ge $x1-or $y0-ge $y1-or $z0-ge $z1){return}
    $faces=[ordered]@{}
    foreach($face in @('north','south','east','west','up','down')){
        $faces[$face]=@{texture="#$face";uv=@(0,0,16,16)}
    }
    return [ordered]@{from=@($x0,$y0,$z0);to=@($x1,$y1,$z1);faces=$faces}
}
for($col=4;$col-le 6;$col++){for($row=1;$row-le 2;$row++){
    $path=Join-Path $assetRoot "models/block/head_${col}_${row}.json"
    $model=Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    $x0=[Math]::Max(0,(5.5-5.0/6-$col)*16);$x1=[Math]::Min(16,(5.5+5.0/6-$col)*16)
    $y0=[Math]::Max(0,(1.8-$row)*16);$y1=[Math]::Min(16,(2.8-$row)*16)
    $elements=@(TerminalBox 0 0 2 16 16 16)
    $elements+=@(TerminalBox 0 0 0 $x0 16 2)
    $elements+=@(TerminalBox $x1 0 0 16 16 2)
    $elements+=@(TerminalBox $x0 0 0 $x1 $y0 2)
    $elements+=@(TerminalBox $x0 $y1 0 $x1 16 2)
    [ordered]@{parent='minecraft:block/block';ambientocclusion=$false;textures=$model.textures;elements=$elements} |
        ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -LiteralPath $path
}}
Write-Output 'Generated rear terminal recess across six head tiles; front textures preserved.'
