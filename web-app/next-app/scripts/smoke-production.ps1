$ErrorActionPreference = "Stop"

$app = Resolve-Path (Join-Path $PSScriptRoot "..")
$credentialsPath = Join-Path $app ".deployment-credentials.json"
if (-not (Test-Path $credentialsPath)) {
    throw "Run scripts/configure-production.ps1 first."
}

$credentials = Get-Content $credentialsPath -Raw | ConvertFrom-Json
$hostName = "piratebrowser-app.2ez.club"
$origin = "https://$hostName"
$address = Resolve-DnsName $hostName -Server 1.1.1.1 -Type A |
    Select-Object -First 1 -ExpandProperty IPAddress
$resolve = "${hostName}:443:$address"
$cookieJar = New-TemporaryFile
$savedPayload = New-TemporaryFile

function Invoke-CurlText {
    param([string[]] $CurlArgs)

    $output = & curl.exe --silent --show-error --fail-with-body --resolve $resolve @CurlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed for $($CurlArgs[-1]) with exit code $LASTEXITCODE`: $output"
    }
    return ($output -join "`n")
}

function Invoke-CurlJson {
    param([string[]] $CurlArgs)
    return Invoke-CurlText -CurlArgs $CurlArgs | ConvertFrom-Json
}

try {
    $index = Invoke-CurlText -CurlArgs @("$origin/")
    $meta = Invoke-CurlJson -CurlArgs @("$origin/api/meta?smoke=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())")
    $csrf = Invoke-CurlJson -CurlArgs @(
        "--cookie-jar", $cookieJar,
        "$origin/api/auth/csrf"
    )
    $csrfHeader = "$($csrf.headerName): $($csrf.token)"
    $loginBody = "username=$([Uri]::EscapeDataString($credentials.adminUsername))" +
        "&password=$([Uri]::EscapeDataString($credentials.adminPassword))"

    Invoke-CurlJson -CurlArgs @(
        "--request", "POST",
        "--cookie", $cookieJar,
        "--cookie-jar", $cookieJar,
        "--header", $csrfHeader,
        "--header", "Origin: $origin",
        "--header", "Content-Type: application/x-www-form-urlencoded",
        "--data", $loginBody,
        "$origin/api/auth/login"
    ) | Out-Null

    $me = Invoke-CurlJson -CurlArgs @("--cookie", $cookieJar, "$origin/api/auth/me")
    $putio = Invoke-CurlJson -CurlArgs @("--cookie", $cookieJar, "$origin/api/putio/status")
    $transfers = Invoke-CurlJson -CurlArgs @("--cookie", $cookieJar, "$origin/api/putio/transfers")
    $sources = Invoke-CurlJson -CurlArgs @("--cookie", $cookieJar, "$origin/api/sources")
    $search = Invoke-CurlJson -CurlArgs @(
        "--cookie", $cookieJar,
        "$origin/api/search?q=ubuntu&minimumSeeders=1"
    )

    $savedBody = @{
        name = "Production smoke " + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        query = "worker smoke"
        minimumSeeders = 1
        enabled = $true
        knownMagnets = @()
    } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText(
        $savedPayload,
        $savedBody,
        (New-Object Text.UTF8Encoding($false))
    )
    $saved = Invoke-CurlJson -CurlArgs @(
        "--request", "POST",
        "--cookie", $cookieJar,
        "--header", $csrfHeader,
        "--header", "Origin: $origin",
        "--header", "Content-Type: application/json",
        "--data-binary", "@$savedPayload",
        "$origin/api/saved-searches"
    )
    $listed = Invoke-CurlJson -CurlArgs @("--cookie", $cookieJar, "$origin/api/saved-searches")
    Invoke-CurlText -CurlArgs @(
        "--request", "DELETE",
        "--cookie", $cookieJar,
        "--header", $csrfHeader,
        "--header", "Origin: $origin",
        "$origin/api/saved-searches/$($saved.id)"
    ) | Out-Null

    $inviteBody = @{
        label = "Production smoke"
        expiryDays = 1
    } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText(
        $savedPayload,
        $inviteBody,
        (New-Object Text.UTF8Encoding($false))
    )
    $generatedInvite = Invoke-CurlJson -CurlArgs @(
        "--request", "POST",
        "--cookie", $cookieJar,
        "--header", $csrfHeader,
        "--header", "Origin: $origin",
        "--header", "Content-Type: application/json",
        "--data-binary", "@$savedPayload",
        "$origin/api/admin/invites"
    )
    $listedInvites = Invoke-CurlJson -CurlArgs @("--cookie", $cookieJar, "$origin/api/admin/invites")
    [IO.File]::WriteAllText(
        $savedPayload,
        (@{ id = $generatedInvite.invite.id } | ConvertTo-Json -Compress),
        (New-Object Text.UTF8Encoding($false))
    )
    Invoke-CurlText -CurlArgs @(
        "--request", "DELETE",
        "--cookie", $cookieJar,
        "--header", $csrfHeader,
        "--header", "Origin: $origin",
        "--header", "Content-Type: application/json",
        "--data-binary", "@$savedPayload",
        "$origin/api/admin/invites"
    ) | Out-Null

    Write-Output (
        "index={0} canary={1} login={2} role={3} putio={4} transfers={5} sources={6} searchResults={7} searchFailures={8} savedRoundTrip={9} inviteRoundTrip={10}" -f
        [bool]($index.Length -gt 1000),
        $meta.canary,
        $me.username,
        $me.role,
        $putio.configured,
        $transfers.Count,
        $sources.Count,
        $search.results.Count,
        $search.failures.Count,
        [bool]($listed | Where-Object id -eq $saved.id),
        [bool](
            $generatedInvite.code -like "PB-*" -and
            ($listedInvites | Where-Object id -eq $generatedInvite.invite.id)
        )
    )
} finally {
    Remove-Item -LiteralPath $cookieJar, $savedPayload -Force -ErrorAction SilentlyContinue
}
