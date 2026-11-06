param(
    [Parameter(Mandatory=$true)][ValidateSet('start','status','stop')][string]$Action,
    [Parameter(Mandatory=$true)][ValidateSet('Android','Core','Content','Api','Worker','Services','PublishBotw','Smoke')][string]$Kind
)
$ErrorActionPreference = 'Stop'
$HallRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$HallJobRoot = Join-Path $HallRoot ('output\hall\jobs\' + $Kind)
$HallStatePath = Join-Path $HallJobRoot 'state.json'
$HallWorker = Join-Path $PSScriptRoot 'hall_job_worker.ps1'
$HallExisting = $null
if (Test-Path -LiteralPath $HallStatePath) {
    $HallExisting = Get-Content -LiteralPath $HallStatePath -Raw | ConvertFrom-Json
    $HallProcess = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $HallExisting.pid)
    $HallLive = $HallProcess -and $HallProcess.CommandLine -like ('*' + $HallWorker + '*') -and $HallProcess.CommandLine -like ('*-Kind ' + $Kind + '*')
} else { $HallLive = $false }
if ($Action -eq 'status') {
    @{kind=$Kind; live=[bool]$HallLive; recorded_state=$HallExisting; log=(Join-Path $HallJobRoot 'run.log')} | ConvertTo-Json -Depth 5
    exit
}
if ($Action -eq 'stop') {
    if (-not $HallLive) { Write-Output 'No matching live project job to stop.'; exit }
    function Stop-HallJobTree([int]$HallTreeId) {
        foreach ($HallDescendant in @(Get-CimInstance Win32_Process -Filter ('ParentProcessId=' + $HallTreeId))) {
            Stop-HallJobTree $HallDescendant.ProcessId
        }
        Stop-Process -Id $HallTreeId -ErrorAction SilentlyContinue
    }
    Stop-HallJobTree $HallExisting.pid
    $HallExisting.state = 'stopped'
    $HallExisting.observed_at = (Get-Date).ToUniversalTime().ToString('o')
    [IO.File]::WriteAllText($HallStatePath, ($HallExisting | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Write-Output ('Stopped the matching project job: ' + $Kind)
    exit
}
if ($HallLive) { throw 'The matching job is already running; inspect its log instead of starting another.' }
New-Item -ItemType Directory -Force -Path $HallJobRoot | Out-Null
$HallShell = (Get-Process -Id $PID).Path
$HallChild = Start-Process -FilePath $HallShell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $HallWorker + '"'),'-Kind',$Kind) -WindowStyle Hidden -PassThru
@{kind=$Kind; launched_pid=$HallChild.Id; log=(Join-Path $HallJobRoot 'run.log')} | ConvertTo-Json
