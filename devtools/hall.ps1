param([Parameter(Mandatory=$true)][ValidateSet('setup','serve','worker','identities','native','content','publish-2048','publish-botw','build','rebuild','verify','install','capture')][string]$Action, [switch]$WithBotw)
$ErrorActionPreference = 'Stop'
$HallRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$HallPython = Join-Path $HallRoot 'services\.venv\Scripts\python.exe'
$env:PYTHONUTF8 = '1'
$env:PYTHONPATH = (Join-Path $HallRoot 'services') + ';' + $HallRoot
$env:NSA_MODE = 'development'
$env:NSA_LOCAL_ROOT = Join-Path $HallRoot 'services\local'
$env:GRADLE_USER_HOME = Join-Path $HallRoot 'tools\hall\gradle-home'
$env:ANDROID_USER_HOME = Join-Path $HallRoot 'tools\hall\android-user'
$env:TEMP = Join-Path $HallRoot 'tools\hall\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
Push-Location $HallRoot
try {
    # Native programs may write progress to stderr on Windows PowerShell 5.
    # Judge those programs by their exit code, not by the output stream they use.
    $global:LASTEXITCODE = 1
    $ErrorActionPreference = 'Continue'
    switch ($Action) {
        'setup' { & $HallPython devtools\hall_setup.py; if ($LASTEXITCODE -ne 0) { throw 'Toolchain setup failed' }; & $HallPython devtools\hall_local.py init }
        'serve' { & $HallPython -m uvicorn catalog.app:create_app --factory --host 127.0.0.1 --port 8443 --no-access-log --ssl-certfile services\local\local-ca.pem --ssl-keyfile services\local\tls-key.pem }
        'worker' { & $HallPython -m worker.main }
        'identities' { if ($WithBotw) { & $HallPython devtools\hall_local.py identities --with-botw } else { & $HallPython devtools\hall_local.py identities } }
        'native' { & $HallPython devtools\hall_local.py native-build }
        'content' { & $HallPython devtools\hall_local.py content }
        'publish-2048' { & $HallPython -m content_tools.publish --game nsa-2048 }
        'publish-botw' { & $HallPython -m content_tools.publish --game botw }
        'build' { & tools\hall\gradle-8.13\bin\gradle.bat -p android --no-daemon --console=plain assembleDebug; if ($LASTEXITCODE -eq 0) { & $HallPython devtools\hall_verify_apk.py } }
        'rebuild' { & tools\hall\gradle-8.13\bin\gradle.bat -p android --no-daemon --console=plain clean assembleDebug; if ($LASTEXITCODE -eq 0) { & $HallPython devtools\hall_verify_apk.py } }
        'verify' { & $HallPython -m pytest services\tests -q; if ($LASTEXITCODE -ne 0) { throw 'Service integration checks failed' }; & $HallPython -m unittest discover tests; if ($LASTEXITCODE -ne 0) { throw 'Core checks failed' }; & tools\hall\gradle-8.13\bin\gradle.bat -p android --no-daemon --continue --console=plain testDebugUnitTest lintDebug }
        'install' { & $HallPython devtools\hall_device.py install }
        'capture' { & $HallPython devtools\hall_device.py capture }
    }
    if ($LASTEXITCODE -ne 0) { throw "Hall action failed: $Action" }
} finally { Pop-Location }
