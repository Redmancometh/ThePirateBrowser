$ErrorActionPreference = "Stop"

$env:PORT = "18080"
$env:DATABASE_URL = "jdbc:h2:mem:pirate-smoke;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
$env:WEB_ADMIN_USERNAME = "smokeadmin"
$env:WEB_ADMIN_PASSWORD = "smoke-test-password"
$env:BUILD_CANARY = "WEB-SMOKE"

$backend = Join-Path $PSScriptRoot "..\backend"
$jar = Join-Path $backend "target\pirate-browser-web-1.0.0-SNAPSHOT.jar"
$outLog = Join-Path $backend "target\smoke-out.log"
$errorLog = Join-Path $backend "target\smoke-error.log"
$java = "C:\Program Files\Java\jdk-21\bin\java.exe"
$process = Start-Process -FilePath $java `
    -ArgumentList "-jar", $jar `
    -WorkingDirectory $backend `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errorLog

try {
    $health = $null
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:18080/actuator/health"
            if ($health.status -eq "UP") {
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if ($null -eq $health -or $health.status -ne "UP") {
        Get-Content $errorLog -Tail 40
        Get-Content $outLog -Tail 40
        throw "Server did not become ready."
    }

    $chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
    if (Test-Path $chrome) {
        $screenshot = Join-Path $backend "target\frontend-ui.png"
        Start-Process -FilePath $chrome `
            -ArgumentList "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--virtual-time-budget=3000", "--window-size=1440,1000",
                "--screenshot=$screenshot",
                "http://127.0.0.1:18080/" `
            -Wait `
            -WindowStyle Hidden
        $mobileScreenshot = Join-Path $backend "target\frontend-ui-mobile.png"
        Start-Process -FilePath $chrome `
            -ArgumentList "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--virtual-time-budget=3000", "--window-size=390,844",
                "--screenshot=$mobileScreenshot",
                "http://127.0.0.1:18080/" `
            -Wait `
            -WindowStyle Hidden
    }

    $web = Invoke-WebRequest "http://127.0.0.1:18080/" -UseBasicParsing
    $anonymousStatus = 0
    try {
        Invoke-WebRequest "http://127.0.0.1:18080/api/auth/me" -UseBasicParsing | Out-Null
    } catch {
        $anonymousStatus = [int]$_.Exception.Response.StatusCode
    }

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf = Invoke-RestMethod "http://127.0.0.1:18080/api/auth/csrf" -WebSession $session
    $headers = @{}
    $headers[$csrf.headerName] = $csrf.token
    Invoke-RestMethod "http://127.0.0.1:18080/api/auth/login" `
        -Method Post `
        -Body @{ username = "smokeadmin"; password = "smoke-test-password" } `
        -Headers $headers `
        -WebSession $session | Out-Null
    $me = Invoke-RestMethod "http://127.0.0.1:18080/api/auth/me" -WebSession $session

    Write-Output (
        "health={0} index={1} reactRoot={2} anonymous={3} login={4} role={5} canary={6}" -f
        $health.status,
        $web.StatusCode,
        $web.Content.Contains('id="root"'),
        $anonymousStatus,
        $me.username,
        $me.role,
        $me.canary
    )
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
}
