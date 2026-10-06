# Reproducible original pixel patterns. Run explicitly; ordinary builds do not modify source.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$targetDirectory = Join-Path $projectRoot 'src\main\resources\assets\blockblend\textures\overlay'
foreach ($name in @('noise','stripes','scratches')) {
    $image = [System.Drawing.Bitmap]::new(32,32)
    try {
        $random = [Random]::new(42621)
        for ($y = 0; $y -lt 32; $y++) {
            for ($x = 0; $x -lt 32; $x++) {
                switch ($name) {
                    'noise' { $value = $random.Next(40,225); $color = [Drawing.Color]::FromArgb(255,$value,[Math]::Min(255,$value+12),[Math]::Max(0,$value-12)) }
                    'stripes' { if ((($x+$y)%8) -lt 3) { $color = [Drawing.Color]::FromArgb(255,48,64,88) } else { $color = [Drawing.Color]::FromArgb(255,224,204,168) } }
                    'scratches' { $value = 128; if ((($x*5+$y*3)%29) -eq 0 -or (($x-$y+32)%17) -eq 0) { $value=230 }; if ((($x*7+$y*2)%31) -eq 0) { $value=35 }; $color = [Drawing.Color]::FromArgb(255,$value,$value,$value) }
                }
                $image.SetPixel($x,$y,$color)
            }
        }
        $image.Save((Join-Path $targetDirectory ($name+'.png')),[Drawing.Imaging.ImageFormat]::Png)
    } finally { $image.Dispose() }
}
