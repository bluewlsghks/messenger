# Dependency-free regression checks; never launch a tunnel or use real secrets.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Import-Module (Join-Path $root 'scripts/PublicAccess.psm1') -Force
$count = 0
function Assert-Equal($Actual, $Expected) {
    if ($Actual -cne $Expected) { throw 'Assertion failed: values differ.' }
    $script:count++
}
function Assert-Throws([scriptblock]$Action) {
    $thrown = $false
    try { & $Action | Out-Null } catch { $thrown = $true }
    Assert-Equal $thrown $true
}
$files = @(Get-ChildItem (Join-Path $root 'scripts') -Include '*.ps1','*.psm1' -Recurse | ForEach-Object { $_.FullName }) + @($PSCommandPath)
foreach ($path in $files) {
    $tokens = $null
    $errors = $null
    [void][Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
    Assert-Equal $errors.Count 0
}
Assert-Equal (Get-QuickTunnelOrigin 'INF | https://quiet-river-123.trycloudflare.com |') 'https://quiet-river-123.trycloudflare.com'
Assert-Equal (Get-QuickTunnelOrigin 'https://developers.cloudflare.com/foo') $null
Assert-Equal (Get-QuickTunnelOrigin 'http://quiet-river.trycloudflare.com') $null
Assert-Equal (Get-QuickTunnelOrigin 'https://quiet-river.trycloudflare.com.attacker.example') $null
Assert-Equal (Get-QuickTunnelOrigin '') $null
Assert-Equal (Get-MessengerOrigins 'https://quiet-river.trycloudflare.com' 8081) 'https://quiet-river.trycloudflare.com,http://localhost:8081,http://127.0.0.1:8081'
Assert-Throws { Get-MessengerOrigins 'https://quiet-river.trycloudflare.com/login' 8081 }
Assert-Throws { Get-MessengerOrigins 'https://*.trycloudflare.com' 8081 }
Assert-Throws { Get-MessengerOrigins 'https://quiet-river.trycloudflare.com' 80 }
$temp = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString('N') + '.env')
try {
    Assert-Equal (Read-PublicEnv $temp).Count 0
    Set-Content -LiteralPath $temp -Encoding Ascii -Value @(
        '# comment', '', 'MONGODB_URI="mongodb://localhost:27017/test?x=a=b#literal"',
        'APP_AES_KEY_BASE64=abc==', 'APP_JWT_SECRET_BASE64=''$env:NEVER_EXPAND''')
    $values = Read-PublicEnv $temp
    Assert-Equal $values.Count 3
    Assert-Equal $values.MONGODB_URI 'mongodb://localhost:27017/test?x=a=b#literal'
    Assert-Equal $values.APP_AES_KEY_BASE64 'abc=='
    Assert-Equal $values.APP_JWT_SECRET_BASE64 '$env:NEVER_EXPAND'
    foreach ($invalid in @('MONGODB_URI=REPLACE_ME','MONGODB_URI=','BAD_SETTING=value','not a setting')) {
        Set-Content -LiteralPath $temp -Encoding Ascii -Value $invalid
        Assert-Throws { Read-PublicEnv $temp }
    }
    Set-Content -LiteralPath $temp -Encoding Ascii -Value @('MONGODB_URI=one','MONGODB_URI=two')
    Assert-Throws { Read-PublicEnv $temp }
    Assert-Throws { Read-PublicEnv (Join-Path $root '.env.public.example') }
} finally { Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue }
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    Assert-Equal (Test-MessengerLocalPort $port) $true
} finally { $listener.Stop() }
Assert-Equal (Test-MessengerLocalPort $port) $false
Write-Host "PASS: $count public-access script checks; no public tunnel created."
