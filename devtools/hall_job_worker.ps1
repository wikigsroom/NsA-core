param([Parameter(Mandatory=$true)][ValidateSet('Android','Core','Content','Api','Worker','Services','PublishBotw','Smoke')][string]$Kind)
$ErrorActionPreference = 'Stop'
$HallRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$HallJobRoot = Join-Path $HallRoot ('output\hall\jobs\' + $Kind)
New-Item -ItemType Directory -Force -Path $HallJobRoot | Out-Null
$env:TEMP = Join-Path $HallRoot 'tools\hall\tmp'
$env:TMP = $env:TEMP
$env:GRADLE_USER_HOME = Join-Path $HallRoot 'tools\hall\gradle-home'
$env:ANDROID_USER_HOME = Join-Path $HallRoot 'tools\hall\android-user'
$env:PYTHONUTF8 = '1'
$env:PYTHONUNBUFFERED = '1'
$env:PYTHONPATH = (Join-Path $HallRoot 'services') + ';' + $HallRoot
$env:NSA_MODE = 'development'
$env:NSA_LOCAL_ROOT = Join-Path $HallRoot 'services\local'
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
$HallStarted = (Get-Date).ToUniversalTime().ToString('o')
function Save-HallJobState($State, $Code, $Message) {
    $HallState = @{kind=$Kind; state=$State; pid=$PID; started_at=$HallStarted; observed_at=(Get-Date).ToUniversalTime().ToString('o'); exit_code=$Code; message=$Message}
    [IO.File]::WriteAllText((Join-Path $HallJobRoot 'state.json'), ($HallState | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
}
Save-HallJobState 'running' $null ''
Push-Location $HallRoot
try {
    $HallLog = Join-Path $HallJobRoot 'run.log'
    $HallPython = Join-Path $HallRoot 'services\.venv\Scripts\python.exe'
    # Windows PowerShell 5 wraps native stderr as ErrorRecord objects. INFO output
    # from a healthy server must not terminate the launcher before its exit code.
    $global:LASTEXITCODE = 1
    $ErrorActionPreference = 'Continue'
    switch ($Kind) {
        'Android' { & tools\hall\gradle-8.13\bin\gradle.bat -p android --daemon --continue --console=plain assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug *> $HallLog; if ($LASTEXITCODE -eq 0) { & $HallPython devtools\hall_verify_apk.py *>> $HallLog } }
        'Core' { & $HallPython -m unittest discover tests *> $HallLog }
        'Content' { & $HallPython devtools\hall_local.py content *> $HallLog }
        'Api' { & $HallPython -m uvicorn catalog.app:create_app --factory --host 127.0.0.1 --port 8443 --no-access-log --ssl-certfile services\local\local-ca.pem --ssl-keyfile services\local\tls-key.pem *> $HallLog }
        'Worker' { & $HallPython -m worker.main *> $HallLog }
        'Services' { & $HallPython -m pytest services\tests -q *> $HallLog }
        'PublishBotw' { & $HallPython -m content_tools.publish --game botw *> $HallLog }
        'Smoke' { & $HallPython devtools\hall_smoke.py all *> $HallLog }
    }
    $HallExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Save-HallJobState $(if ($HallExitCode -eq 0) {'succeeded'} else {'failed'}) $HallExitCode ''
} catch {
    Save-HallJobState 'failed' 1 $_.Exception.Message
} finally {
    Pop-Location
}
