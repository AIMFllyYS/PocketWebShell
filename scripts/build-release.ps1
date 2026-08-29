[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+([-.][0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [string]$KeystorePath = "$env:USERPROFILE\.android\keystores\pocket-web-shell-release.jks",
    [string]$CredentialPath = "$env:USERPROFILE\.android\keystores\pocket-web-shell-release.credential.xml"
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$keystore = (Resolve-Path -LiteralPath $KeystorePath).Path
$credentialFile = (Resolve-Path -LiteralPath $CredentialPath).Path
$credential = Import-Clixml -LiteralPath $credentialFile

if ($credential -isnot [System.Management.Automation.PSCredential]) {
    throw "Signing credential is not a PSCredential: $credentialFile"
}

$password = $credential.GetNetworkCredential().Password
$alias = $credential.UserName
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
$sourceApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$distDir = Join-Path $projectRoot 'dist'
$releaseApk = Join-Path $distDir "PocketWebShell-v$Version.apk"
$checksumFile = "$releaseApk.sha256"
$certificateFile = Join-Path $distDir 'PocketWebShell-release-cert.pem'

$buildToolsRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools'
$apksigner = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1

if (-not $apksigner) {
    throw "apksigner.bat was not found under $buildToolsRoot"
}

try {
    $env:POCKET_WEBSHELL_KEYSTORE_PATH = $keystore
    $env:POCKET_WEBSHELL_KEYSTORE_PASSWORD = $password
    $env:POCKET_WEBSHELL_KEY_ALIAS = $alias
    $env:POCKET_WEBSHELL_KEY_PASSWORD = $password

    Push-Location $projectRoot
    try {
        & $gradleWrapper :app:clean :app:assembleRelease
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle release build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path -LiteralPath $sourceApk)) {
        throw "Release APK was not produced: $sourceApk"
    }

    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force

    & $apksigner verify --verbose --print-certs $releaseApk
    if ($LASTEXITCODE -ne 0) {
        throw "APK signature verification failed"
    }

    & keytool -exportcert -rfc `
        -keystore $keystore `
        -storepass:env POCKET_WEBSHELL_KEYSTORE_PASSWORD `
        -alias $alias `
        -file $certificateFile
    if ($LASTEXITCODE -ne 0) {
        throw "Public certificate export failed"
    }

    $hash = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText($checksumFile, "$hash  $([System.IO.Path]::GetFileName($releaseApk))`n")

    Write-Host "Signed APK: $releaseApk"
    Write-Host "SHA-256:   $hash"
    Write-Host "Certificate: $certificateFile"
} finally {
    Remove-Item Env:POCKET_WEBSHELL_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:POCKET_WEBSHELL_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:POCKET_WEBSHELL_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:POCKET_WEBSHELL_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
