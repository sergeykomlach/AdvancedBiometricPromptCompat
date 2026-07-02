param(
    [string]$RepoRoot = (Get-Location).Path
)

$modules = @(
    "app",
    "common",
    "biometric",
    "biometric-custom-behavior",
    "biometric-custom-face-tf",
    "biometric-custom-voice",
    "biometric-zkfinger"
)

foreach ($module in $modules) {
    $source = Join-Path $RepoRoot "$module\src\main\res\values\strings.xml"
    $targetDir = Join-Path $RepoRoot "$module\src\main\res\values-en"
    $target = Join-Path $targetDir "strings.xml"

    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing source strings file: $source"
    }

    if (-not (Test-Path -LiteralPath $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir | Out-Null
    }

    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Host "Copied default strings into values-en for all modules."
