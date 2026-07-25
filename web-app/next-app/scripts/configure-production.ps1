$ErrorActionPreference = "Stop"

$app = Resolve-Path (Join-Path $PSScriptRoot "..")
$repository = Resolve-Path (Join-Path $app "..\..")
$settingsPath = Join-Path $repository "data\settings.json"
$credentialsPath = Join-Path $app ".deployment-credentials.json"

if (-not (Test-Path $settingsPath)) {
    throw "The local credential file data/settings.json was not found."
}

$settings = Get-Content $settingsPath -Raw | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($settings.putIoToken)) {
    throw "The local put.io token is empty."
}

if (-not (Test-Path $credentialsPath)) {
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    $adminBytes = New-Object byte[] 18
    $inviteBytes = New-Object byte[] 18
    $generator.GetBytes($adminBytes)
    $generator.GetBytes($inviteBytes)
    $generator.Dispose()
    @{
        adminUsername = "owner"
        adminPassword = "PB!" + ([BitConverter]::ToString($adminBytes) -replace "-", "")
        registrationInviteCode = "INV-" + ([BitConverter]::ToString($inviteBytes) -replace "-", "")
    } | ConvertTo-Json | Set-Content -LiteralPath $credentialsPath -Encoding UTF8
}

$credentials = Get-Content $credentialsPath -Raw | ConvertFrom-Json

$settings.putIoToken | npx wrangler secret put PUTIO_OAUTH_TOKEN
$credentials.registrationInviteCode | npx wrangler secret put REGISTRATION_INVITE_CODE
$credentials.adminPassword | npx wrangler secret put ADMIN_BOOTSTRAP_PASSWORD

Write-Output "Production secrets configured. Owner credentials are stored locally in:"
Write-Output $credentialsPath
