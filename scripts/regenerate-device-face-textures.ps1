Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$textureDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\xllogic\textures\block'
$textureDir = (Resolve-Path $textureDir).Path

$devices = @(
    @{ Name = 'computer'; Accent = [System.Drawing.Color]::FromArgb(255, 108, 210, 255) },
    @{ Name = 'material_io'; Accent = [System.Drawing.Color]::FromArgb(255, 94, 232, 178) },
    @{ Name = 'crafting_io'; Accent = [System.Drawing.Color]::FromArgb(255, 255, 184, 79) },
    @{ Name = 'crafting_cpu'; Accent = [System.Drawing.Color]::FromArgb(255, 108, 165, 255) },
    @{ Name = 'redstone_io'; Accent = [System.Drawing.Color]::FromArgb(255, 255, 99, 99) },
    @{ Name = 'xlapi_block'; Accent = [System.Drawing.Color]::FromArgb(255, 108, 220, 255) },
    @{ Name = 'clock'; Accent = [System.Drawing.Color]::FromArgb(255, 222, 184, 92) },
    @{ Name = 'light_sensor'; Accent = [System.Drawing.Color]::FromArgb(255, 204, 220, 112) },
    @{ Name = 'rain_sensor'; Accent = [System.Drawing.Color]::FromArgb(255, 116, 198, 255) }
)

function Load-Bitmap([string]$path) {
    return [System.Drawing.Bitmap]::FromFile($path)
}

function New-Canvas([int]$width, [int]$height) {
    return [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Blend-Channel([int]$source, [int]$target, [double]$mix) {
    return [Math]::Min(255, [Math]::Max(0, [Math]::Round($source * (1.0 - $mix) + $target * $mix)))
}

function Blend-Color([System.Drawing.Color]$source, [System.Drawing.Color]$target, [double]$mix) {
    return [System.Drawing.Color]::FromArgb(
        255,
        (Blend-Channel $source.R $target.R $mix),
        (Blend-Channel $source.G $target.G $mix),
        (Blend-Channel $source.B $target.B $mix)
    )
}

function Multiply-Color([System.Drawing.Color]$source, [double]$factor) {
    return [System.Drawing.Color]::FromArgb(
        255,
        [Math]::Min(255, [Math]::Round($source.R * $factor)),
        [Math]::Min(255, [Math]::Round($source.G * $factor)),
        [Math]::Min(255, [Math]::Round($source.B * $factor))
    )
}

function Copy-Bitmap([System.Drawing.Bitmap]$source) {
    $copy = New-Canvas $source.Width $source.Height
    for ($x = 0; $x -lt $source.Width; $x++) {
        for ($y = 0; $y -lt $source.Height; $y++) {
            $copy.SetPixel($x, $y, $source.GetPixel($x, $y))
        }
    }
    return $copy
}

function Draw-Frame([System.Drawing.Graphics]$graphics, [System.Drawing.Color]$outer, [System.Drawing.Color]$inner) {
    $outerPen = [System.Drawing.Pen]::new($outer)
    $innerPen = [System.Drawing.Pen]::new($inner)
    try {
        $graphics.DrawRectangle($outerPen, 0, 0, 15, 15)
        $graphics.DrawRectangle($innerPen, 1, 1, 13, 13)
    }
    finally {
        $outerPen.Dispose()
        $innerPen.Dispose()
    }
}

function Save-Bitmap([System.Drawing.Bitmap]$bitmap, [string]$path) {
    if (Test-Path $path) {
        Remove-Item $path -Force
    }
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

foreach ($device in $devices) {
    $name = $device.Name
    $accent = $device.Accent

    $base = Load-Bitmap (Join-Path $textureDir ($name + '.png'))
    $back = Load-Bitmap (Join-Path $textureDir ($name + '_back.png'))
    $bottom = Load-Bitmap (Join-Path $textureDir ($name + '_bottom.png'))

    try {
        $front = Copy-Bitmap $base
        $side = Copy-Bitmap $back
        $top = Copy-Bitmap $bottom

        try {
            for ($x = 0; $x -lt 16; $x++) {
                for ($y = 0; $y -lt 16; $y++) {
                    $side.SetPixel($x, $y, (Multiply-Color ($side.GetPixel($x, $y)) 0.82))
                    $top.SetPixel($x, $y, (Blend-Color ($top.GetPixel($x, $y)) $accent 0.10))
                }
            }

            $frontGraphics = [System.Drawing.Graphics]::FromImage($front)
            $sideGraphics = [System.Drawing.Graphics]::FromImage($side)
            $topGraphics = [System.Drawing.Graphics]::FromImage($top)
            try {
                $frontGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $sideGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $topGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor

                $frameOuter = [System.Drawing.Color]::FromArgb(255, 18, 22, 28)
                $frameInner = [System.Drawing.Color]::FromArgb(255, 64, 72, 84)
                Draw-Frame $frontGraphics $frameOuter $frameInner
                Draw-Frame $sideGraphics $frameOuter (Blend-Color $frameInner $accent 0.18)
                Draw-Frame $topGraphics $frameOuter (Blend-Color $frameInner $accent 0.12)

                $accentBrush = [System.Drawing.SolidBrush]::new($accent)
                $accentDimBrush = [System.Drawing.SolidBrush]::new((Blend-Color $accent ([System.Drawing.Color]::FromArgb(255, 36, 42, 50)) 0.55))
                $metalBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 38, 44, 54))
                $highlightBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 116, 126, 142))
                try {
                    $frontGraphics.FillRectangle($metalBrush, 2, 11, 12, 2)
                    $frontGraphics.FillRectangle($accentBrush, 3, 12, 3, 1)
                    $frontGraphics.FillRectangle($accentBrush, 7, 12, 6, 1)
                    $frontGraphics.FillRectangle($highlightBrush, 2, 2, 12, 1)

                    $sideGraphics.FillRectangle($metalBrush, 3, 3, 10, 10)
                    $sideGraphics.FillRectangle($accentDimBrush, 4, 5, 8, 1)
                    $sideGraphics.FillRectangle($accentDimBrush, 4, 8, 8, 1)
                    $sideGraphics.FillRectangle($highlightBrush, 4, 11, 3, 1)
                    $sideGraphics.FillRectangle($highlightBrush, 9, 11, 3, 1)

                    $topGraphics.FillRectangle($metalBrush, 3, 3, 10, 10)
                    $topGraphics.FillRectangle($accentDimBrush, 4, 4, 8, 1)
                    $topGraphics.FillRectangle($accentDimBrush, 4, 11, 8, 1)
                    $topGraphics.FillRectangle($accentDimBrush, 4, 5, 1, 6)
                    $topGraphics.FillRectangle($accentDimBrush, 11, 5, 1, 6)
                    $topGraphics.FillRectangle($highlightBrush, 6, 6, 4, 4)
                }
                finally {
                    $accentBrush.Dispose()
                    $accentDimBrush.Dispose()
                    $metalBrush.Dispose()
                    $highlightBrush.Dispose()
                }
            }
            finally {
                $frontGraphics.Dispose()
                $sideGraphics.Dispose()
                $topGraphics.Dispose()
            }

            Save-Bitmap $front (Join-Path $textureDir ($name + '_front.png'))
            Save-Bitmap $side (Join-Path $textureDir ($name + '_side.png'))
            Save-Bitmap $top (Join-Path $textureDir ($name + '_top.png'))
        }
        finally {
            $front.Dispose()
            $side.Dispose()
            $top.Dispose()
        }
    }
    finally {
        $base.Dispose()
        $back.Dispose()
        $bottom.Dispose()
    }
}

Write-Host 'Regenerated opaque front/side/top textures for XL Logic device blocks.'