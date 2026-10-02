param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$fixtureDirectory = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path (Join-Path $fixtureDirectory 'assets'),(Join-Path $fixtureDirectory 'res/raw') -Force | Out-Null
$fixtureKeytool = (Get-Command keytool -ErrorAction Stop).Source
foreach ($fixtureName in @('trusted', 'untrusted')) {
    & $fixtureKeytool -genkeypair -alias probe -keyalg RSA -keysize 2048 -dname 'CN=probe.test' -validity 3 `
        -ext 'SAN=ip:127.0.0.1,dns:probe.test' -ext 'BC=ca:true' -storetype PKCS12 `
        -keystore (Join-Path $fixtureDirectory "assets/$fixtureName.p12") -storepass probe-only -keypass probe-only
    if ($LASTEXITCODE -ne 0) { throw 'TLS fixture generation failed; use a fresh destination.' }
}
& $fixtureKeytool -exportcert -rfc -alias probe -keystore (Join-Path $fixtureDirectory 'assets/trusted.p12') `
    -storepass probe-only -file (Join-Path $fixtureDirectory 'res/raw/probe_ca.pem')
if ($LASTEXITCODE -ne 0) { throw 'TLS fixture certificate export failed.' }
Write-Output $fixtureDirectory
