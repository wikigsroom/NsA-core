param([switch]$WithBotw)
$ErrorActionPreference = 'Stop'
$HallRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:TEMP = Join-Path $HallRoot 'tools\hall\tmp'
$env:TMP = $env:TEMP
$env:PIP_CACHE_DIR = Join-Path $HallRoot 'tools\hall\pip-cache'
$env:PYTHONUTF8 = '1'
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
Push-Location $HallRoot
try {
    $HallPython = Join-Path $HallRoot 'services\.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $HallPython)) {
        & python -m venv services\.venv
        if ($LASTEXITCODE -ne 0) { throw 'Python environment creation failed' }
    }
    & $HallPython -m pip install -r services\requirements-dev.txt
    if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed' }
    $HallScript = Join-Path $PSScriptRoot 'hall.ps1'
    & $HallScript setup
    if ($WithBotw) { & $HallScript identities -WithBotw } else { & $HallScript identities }
} finally { Pop-Location }
