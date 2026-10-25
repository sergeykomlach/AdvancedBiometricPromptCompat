[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Config,
    [string]$Python,
    [string]$Codex,
    [ValidatePattern('^([01][0-9]|2[0-3]):[0-5][0-9]$')]
    [string]$At = '03:00',
    [switch]$ShowExisting
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $Config) {
    $Config = Join-Path $PSScriptRoot 'mobile-audit.config.json'
}

function Resolve-InstalledExecutable([string]$Value, [string]$Default) {
    if (-not $Value) {
        $Value = (Get-Command $Default -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    }
    $resolved = (Resolve-Path -LiteralPath $Value -ErrorAction Stop).ProviderPath
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf) -or
            [IO.Path]::GetExtension($resolved) -ne '.exe') {
        throw 'Select an existing executable installed on this computer.'
    }
    return $resolved
}

function Quote-Argument([string]$Value) {
    if ($Value.Contains('"') -or $Value.Contains("`r") -or $Value.Contains("`n") -or
            $Value.EndsWith('\')) {
        throw 'Task arguments must be literal file paths without quotes or line breaks.'
    }
    return '"' + $Value + '"'
}

$configPath = (Resolve-Path -LiteralPath $Config).ProviderPath
$scriptPath = Join-Path $PSScriptRoot 'mobile_audit_auto.py'
$pythonPath = Resolve-InstalledExecutable $Python 'pythonw.exe'
$codexPath = Resolve-InstalledExecutable $Codex 'codex.exe'
$pythonDirectory = Split-Path -Parent $pythonPath
$pythonConsole = Join-Path $pythonDirectory 'python.exe'
$pythonWindowless = Join-Path $pythonDirectory 'pythonw.exe'
if (-not (Test-Path -LiteralPath $pythonConsole -PathType Leaf) -or
        -not (Test-Path -LiteralPath $pythonWindowless -PathType Leaf)) {
    throw 'Select an installed Python directory containing python.exe and pythonw.exe.'
}
$validation = New-Object System.Diagnostics.ProcessStartInfo
$validation.FileName = $pythonConsole
$validation.Arguments = '-E -s -B ' + (Quote-Argument $scriptPath) + ' --check-only --config ' +
    (Quote-Argument $configPath) + ' --codex ' + (Quote-Argument $codexPath)
$validation.UseShellExecute = $false
$validation.CreateNoWindow = $true
$validation.RedirectStandardOutput = $true
$validation.RedirectStandardError = $true
$process = [Diagnostics.Process]::Start($validation)
$outputText = $process.StandardOutput.ReadToEnd()
$errorText = $process.StandardError.ReadToEnd()
$process.WaitForExit()
if ($process.ExitCode -ne 0) {
    throw ('Audit configuration check failed. ' + $outputText + $errorText)
}
$plan = $outputText | ConvertFrom-Json
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes(([string]$plan.repo).ToLowerInvariant())
    $digest = [BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
}
finally {
    $hasher.Dispose()
}
$taskName = 'Codex-MobileAudit-' + (Split-Path -Leaf $plan.repo) + '-' + $digest.Substring(0, 12)
if ($ShowExisting) {
    $existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existing) {
        $existing | Select-Object TaskName, State, Actions, Principal, Settings, Triggers
    }
    else {
        [pscustomobject]@{ TaskName = $taskName; State = 'NotRegistered' }
    }
    return
}
$timeZone = Get-TimeZone
if ($timeZone.Id -notin @('FLE Standard Time', 'Europe/Kyiv', 'Europe/Kiev')) {
    throw ('The daily time uses the computer clock. Configure Kyiv time first. Current zone: ' +
        $timeZone.Id)
}
$actionArguments = '-E -s -B ' + (Quote-Argument $scriptPath) + ' --config ' +
    (Quote-Argument $configPath) + ' --codex ' + (Quote-Argument $codexPath)
$firstRun = [datetime]::Today.Add([TimeSpan]::Parse($At))
if ($firstRun -le (Get-Date)) {
    $firstRun = $firstRun.AddDays(1)
}
$preview = [pscustomobject]@{
    TaskName = $taskName
    Execute = $pythonWindowless
    Arguments = $actionArguments
    WorkingDirectory = $plan.repo
    User = $identity
    Time = $At
    TimeZone = $timeZone.Id
    FirstRunLocal = $firstRun.ToString('o')
    SourceBranch = $plan.source_branch
    MultipleInstances = 'IgnoreNew'
    StartWhenAvailable = $true
    WakeToRun = $false
    RunLevel = 'Limited'
    LogonType = 'Interactive'
}
$preview
if ($PSCmdlet.ShouldProcess($taskName, 'Register the daily local mobile audit for the current user')) {
    $action = New-ScheduledTaskAction -Execute $pythonWindowless -Argument $actionArguments `
        -WorkingDirectory $plan.repo
    $trigger = New-ScheduledTaskTrigger -Daily -At $firstRun
    $principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable `
        -ExecutionTimeLimit ([TimeSpan]::FromSeconds([int]$plan.run_timeout_seconds + 120))
    $task = New-ScheduledTask -Action $action -Trigger $trigger -Principal $principal `
        -Settings $settings -Description ('Review local mobile sources on ' + $plan.source_branch)
    Register-ScheduledTask -TaskName $taskName -InputObject $task -Force | Out-Null
}
