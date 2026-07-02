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

$expectedLocaleDirs = @(
    "values-en",
    "values-de",
    "values-fr",
    "values-es",
    "values-b+es+419",
    "values-it",
    "values-nl",
    "values-ru",
    "values-uk",
    "values-zh-rCN",
    "values-zh-rTW",
    "values-ja",
    "values-ko",
    "values-id",
    "values-vi",
    "values-pt-rBR",
    "values-hi",
    "values-ar",
    "values-tr"
)

function Get-StringNodes([string]$path) {
    [xml]$xml = Get-Content -LiteralPath $path
    return @($xml.resources.string)
}

function Get-PlaceholderSet([string]$value) {
    if ([string]::IsNullOrEmpty($value)) {
        return @()
    }
    return [regex]::Matches($value, "%\d+\$[sd]|%[sd]") | ForEach-Object { $_.Value }
}

$errors = New-Object System.Collections.Generic.List[string]

foreach ($module in $modules) {
    $baseFile = Join-Path $RepoRoot "$module\src\main\res\values\strings.xml"
    if (-not (Test-Path -LiteralPath $baseFile)) {
        $errors.Add("Missing base strings file: $baseFile")
        continue
    }

    $baseNodes = Get-StringNodes $baseFile
    $baseKeys = @($baseNodes | ForEach-Object { $_.name })

    foreach ($localeDir in $expectedLocaleDirs) {
        $localeFile = Join-Path $RepoRoot "$module\src\main\res\$localeDir\strings.xml"
        if (-not (Test-Path -LiteralPath $localeFile)) {
            $errors.Add("Missing locale file: $localeFile")
            continue
        }

        $localeNodes = Get-StringNodes $localeFile
        $localeKeys = @($localeNodes | ForEach-Object { $_.name })

        $missing = $baseKeys | Where-Object { $_ -notin $localeKeys }
        $extra = $localeKeys | Where-Object { $_ -notin $baseKeys }

        if ($missing.Count -gt 0) {
            $errors.Add("$localeFile missing keys: $($missing -join ', ')")
        }
        if ($extra.Count -gt 0) {
            $errors.Add("$localeFile has extra keys: $($extra -join ', ')")
        }

        foreach ($baseNode in $baseNodes) {
            $localeNode = $localeNodes | Where-Object { $_.name -eq $baseNode.name } | Select-Object -First 1
            if ($null -eq $localeNode) {
                continue
            }
            $basePlaceholders = @(Get-PlaceholderSet $baseNode.'#text')
            $localePlaceholders = @(Get-PlaceholderSet $localeNode.'#text')
            if (($basePlaceholders -join '|') -ne ($localePlaceholders -join '|')) {
                $errors.Add("Placeholder mismatch for key '$($baseNode.name)' in $localeFile")
            }
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Offline locale verification passed."
