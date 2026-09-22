$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixturePath = Join-Path $PSScriptRoot 'publishing-fixture'
$wrapperPath = Join-Path $repoPath 'gradlew.bat'

function Test-PublishingScenario {
    param([string]$Task, [string[]]$ExtraArgs = @(), [string[]]$Uploads = @(),
          [bool]$Transfer = $false, [bool]$Failure = $false)
    $output = & $wrapperPath -p $fixturePath $Task --configure-on-demand --parallel --offline --console=plain @ExtraArgs 2>&1
    $exitCode = $LASTEXITCODE
    $lines = @($output | ForEach-Object { "$_" })
    $actualUploads = @($lines | Where-Object { $_ -match '^FIXTURE_UPLOAD:' } |
        ForEach-Object { $_.Substring('FIXTURE_UPLOAD:'.Length) } | Sort-Object)
    $transfers = @($lines | Where-Object { $_ -eq 'FIXTURE_TRANSFER' })
    $expectedTransfers = if ($Transfer) { 1 } else { 0 }
    $valid = (($exitCode -ne 0) -eq $Failure) -and
        (($actualUploads -join ',') -eq (($Uploads | Sort-Object) -join ',')) -and
        ($transfers.Count -eq $expectedTransfers)
    if ($Transfer -and $valid) {
        $transferIndex = [Array]::IndexOf($lines, 'FIXTURE_TRANSFER')
        $valid = @($lines | Select-Object -Skip ($transferIndex + 1) |
            Where-Object { $_ -match '^FIXTURE_UPLOAD:' }).Count -eq 0
    }
    if (-not $valid) {
        $output | ForEach-Object { Write-Host $_ }
        throw "Publishing scenario failed: $Task $ExtraArgs (exit=$exitCode)"
    }
    Write-Host "PASS: $Task $ExtraArgs (uploads=$($actualUploads -join ','), transfers=$($transfers.Count), expectedFailure=$Failure)"
}

Test-PublishingScenario -Task publish -Uploads alpha,common -Transfer $true
Test-PublishingScenario -Task publishRelease -Uploads alpha,common -Transfer $true
Test-PublishingScenario -Task publish -ExtraArgs '--continue','-PfailModule=alpha' -Uploads alpha,common -Failure $true
Test-PublishingScenario -Task ':alpha:publish' -Uploads alpha
Test-PublishingScenario -Task ':biometric-custom-behavior:publish'
