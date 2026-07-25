$ErrorActionPreference = "Stop"

$app = Resolve-Path (Join-Path $PSScriptRoot "..")
$node = (Get-Command node).Source
$wrangler = Join-Path $app "node_modules\wrangler\bin\wrangler.js"
$output = Join-Path $app ".wrangler\smoke-out.log"
$errors = Join-Path $app ".wrangler\smoke-error.log"
$process = Start-Process -FilePath $node `
    -ArgumentList $wrangler, "dev", "--local", "--port", "8789" `
    -WorkingDirectory $app `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $output `
    -RedirectStandardError $errors

try {
    $index = $null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $index = Invoke-WebRequest "http://127.0.0.1:8789/" -UseBasicParsing
            if ($index.StatusCode -eq 200) {
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if ($null -eq $index) {
        Get-Content $errors -Tail 60
        Get-Content $output -Tail 60
        throw "Worker preview did not become ready."
    }

    $meta = Invoke-RestMethod "http://127.0.0.1:8789/api/meta"
    $csrf = Invoke-RestMethod "http://127.0.0.1:8789/api/auth/csrf" `
        -SessionVariable session
    $anonymousStatus = 0
    try {
        Invoke-WebRequest "http://127.0.0.1:8789/api/auth/me" `
            -UseBasicParsing `
            -WebSession $session | Out-Null
    } catch {
        $anonymousStatus = [int]$_.Exception.Response.StatusCode
    }

    Write-Output (
        "index={0} reactRoot={1} anonymous={2} csrf={3} canary={4}" -f
        $index.StatusCode,
        $index.Content.Contains('The Pirate Browser'),
        $anonymousStatus,
        (-not [string]::IsNullOrWhiteSpace($csrf.token)),
        $meta.canary
    )
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
}
