#requires -Version 5.1
<#
.SYNOPSIS
Publish this Windows development server through a free, temporary HTTPS tunnel.
.DESCRIPTION
Default: build and launch the app using existing secrets in the shell or .env.public.
-TunnelOnly: keep your IntelliJ server; print the exact origins to apply and restart it.
No account, domain purchase, port forwarding, firewall change, or Windows service.
The HTTPS URL changes on restart. This is NOT a production deployment.
#>
[CmdletBinding()]
param(
    [ValidateRange(1024,65535)][int]$Port = 8081,
    [string]$CloudflaredPath = 'cloudflared',
    [string]$EnvFile = '.env.public',
    [switch]$TunnelOnly,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'PublicAccess.psm1') -Force
$root = Split-Path $PSScriptRoot -Parent
$cloud = $null
$app = $null
$oldEnv = @{}
$runDir = $null

function Read-RunLog([string]$Name) {
    $path = Join-Path $runDir $Name
    if (Test-Path -LiteralPath $path) {
        return [string](Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue)
    }
    return ''
}
function Test-WebEndpoint([string]$Url, [string]$Origin = '') {
    try {
        $headers = @{}
        if ($Origin) { $headers['Origin'] = $Origin }
        $response = Invoke-WebRequest -Uri $Url -Headers $headers -UseBasicParsing -TimeoutSec 5
        return ($response.StatusCode -eq 200)
    } catch { return $false }
}
function Stop-OwnedProcess($Process) {
    if ($null -ne $Process) {
        $Process.Refresh()
        if (-not $Process.HasExited) {
            # Only the exact processes this script started; never kill all Java processes.
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

Push-Location $root
try {
    if ($env:OS -ne 'Windows_NT') { throw 'Use this launcher on the Windows PC running Messenger.' }
    $command = Get-Command $CloudflaredPath -CommandType Application -ErrorAction SilentlyContinue
    if (-not $command) {
        throw 'Install cloudflared first: winget install --id Cloudflare.cloudflared --exact ; then reopen PowerShell.'
    }
    if ($TunnelOnly -and $SkipBuild) { throw '-SkipBuild is not applicable to -TunnelOnly.' }
    $jar = $null
    $java = $null
    if (-not $TunnelOnly) {
        if (Test-MessengerLocalPort $Port) {
            throw "Port $Port is already occupied. Stop your IntelliJ run first, use -Port 8082, or use -TunnelOnly."
        }
        $java = Get-Command java -CommandType Application -ErrorAction SilentlyContinue
        if (-not $java) { throw 'Java 21 is required. Add your JDK bin directory to PATH.' }
        if (-not [IO.Path]::IsPathRooted($EnvFile)) { $EnvFile = Join-Path $root $EnvFile }
        $values = Read-PublicEnv $EnvFile
        foreach ($key in @('MONGODB_URI', 'APP_AES_KEY_BASE64', 'APP_JWT_SECRET_BASE64')) {
            $existing = [Environment]::GetEnvironmentVariable($key, 'Process')
            if ([string]::IsNullOrWhiteSpace($existing)) {
                if (-not $values.ContainsKey($key)) {
                    throw "Missing $key. Copy .env.public.example to .env.public and fill in your EXISTING values locally. Do not send secrets in chat."
                }
                $oldEnv[$key] = $existing
                [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
            }
        }
        if (-not $SkipBuild) {
            Write-Host 'Building the application (no tunnel is open yet)...'
            & .\gradlew.bat bootJar --no-daemon
            if ($LASTEXITCODE -ne 0) { throw 'Build failed. No tunnel was started.' }
        }
        $jars = @(Get-ChildItem (Join-Path $root 'build/libs') -Filter '*.jar' |
            Where-Object { $_.Name -notlike '*-plain.jar' })
        if ($jars.Count -ne 1) { throw 'Expected one bootJar under build/libs. Remove obsolete jars or run gradlew clean bootJar.' }
        $jar = $jars[0]
    }

    $runDir = Join-Path $root ('.public/' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    # Isolate the Quick Tunnel from an existing named tunnel config. Never rename user config.
    $config = Join-Path $runDir 'quick.yml'
    Set-Content -LiteralPath $config -Value '{}' -Encoding Ascii
    Write-Warning 'Public development preview: anyone with the URL can reach login/registration. Use a development DB and test accounts. HTTPS traffic passes through Cloudflare.'
    $cloudArgs = @('tunnel', '--config', ('"' + $config + '"'), '--no-autoupdate',
        '--protocol', 'http2', '--url', "http://127.0.0.1:$Port")
    $cloud = Start-Process -FilePath $command.Source -ArgumentList $cloudArgs -PassThru -NoNewWindow `
        -RedirectStandardOutput (Join-Path $runDir 'tunnel-out.log') `
        -RedirectStandardError (Join-Path $runDir 'tunnel-err.log')
    $origin = $null
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        $cloud.Refresh()
        if ($cloud.HasExited) { throw "Tunnel exited. Read $runDir\tunnel-err.log locally." }
        $origin = Get-QuickTunnelOrigin ((Read-RunLog 'tunnel-out.log') + "`n" + (Read-RunLog 'tunnel-err.log'))
        if ($origin) { break }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $origin) { throw 'No HTTPS URL was issued. Check network access to Cloudflare TCP 7844/HTTPS 443 and the tunnel logs. Do not disable your firewall.' }
    $origins = Get-MessengerOrigins -PublicOrigin $origin -Port $Port
    Write-Host "`nISSUED URL (not verified yet): $origin"
    Set-Content -LiteralPath (Join-Path $runDir 'url.txt') -Value $origin -Encoding Ascii

    if ($TunnelOnly) {
        Write-Host 'IntelliJ: keep your existing MongoDB/AES/JWT settings; ADD/REPLACE these environment variables, then restart the app:'
        Write-Host "SERVER_PORT=$Port;SERVER_ADDRESS=127.0.0.1;APP_ALLOWED_ORIGINS=$origins;SERVER_FORWARD_HEADERS_STRATEGY=none;APP_OPENAI_ENABLED=false"
        Write-Host 'Leave this terminal running. The checker will retry while you restart IntelliJ.'
    } else {
        # Exact public origin, loopback-only binding, no trust in client-forwarded headers.
        # Turn paid AI calls off for an Internet-accessible development preview.
        $appArgs = @('-jar', ('"' + $jar.FullName + '"'), "--server.port=$Port",
            '--server.address=127.0.0.1', "--app.allowed-origins=$origins",
            '--server.forward-headers-strategy=none', '--app.openai.enabled=false')
        $app = Start-Process -FilePath $java.Source -ArgumentList $appArgs -PassThru -NoNewWindow `
            -RedirectStandardOutput (Join-Path $runDir 'app-out.log') `
            -RedirectStandardError (Join-Path $runDir 'app-err.log')
    }

    $verified = $false
    $attempt = 0
    Write-Host "Logs (local only): $runDir"
    Write-Host 'Press Ctrl+C to close this tunnel and the app started by this script.'
    while ($true) {
        $cloud.Refresh()
        if ($cloud.HasExited) { throw 'Tunnel stopped. Restart the script to get a new URL.' }
        if ($null -ne $app) {
            $app.Refresh()
            if ($app.HasExited) { throw "Messenger stopped. Read $runDir\app-out.log and app-err.log locally." }
        }
        if (-not $verified) {
            $attempt++
            $localOk = (Test-WebEndpoint "http://127.0.0.1:$Port/login") -and
                (Test-WebEndpoint "http://127.0.0.1:$Port/ws-stomp/info" $origin)
            if ($localOk -and (Test-WebEndpoint "$origin/login") -and (Test-WebEndpoint "$origin/ws-stomp/info" $origin)) {
                $verified = $true
                Write-Host "`nHTTPS LOGIN + SOCKJS ORIGIN CHECK PASSED: $origin"
                Write-Host 'Share this HTTPS URL, with no :8081 suffix. Test actual chat using two accounts.'
                Write-Host 'Enable desktop notifications in Messenger settings at this new HTTPS origin.'
            } elseif ($attempt % 6 -eq 0) {
                Write-Warning 'Not verified yet: check app startup, exact APP_ALLOWED_ORIGINS, and tunnel logs. The issued URL alone does not prove the app is reachable.'
            }
            if ($attempt -ge 40 -and -not $verified) { throw 'Startup/reachability check timed out. Fix the local error and restart.' }
        }
        Start-Sleep -Seconds 3
    }
} finally {
    Stop-OwnedProcess $cloud
    Stop-OwnedProcess $app
    foreach ($key in $oldEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $oldEnv[$key], 'Process') }
    Pop-Location
}
