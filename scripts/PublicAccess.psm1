# Pure helpers. Importing this module never starts a tunnel or a server.
Set-StrictMode -Version Latest

function Get-QuickTunnelOrigin {
    param([AllowEmptyString()][string]$Text)
    # Match only Cloudflare's generated HTTPS origin, never an arbitrary log URL.
    $match = [regex]::Match($Text, 'https://[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.trycloudflare\.com(?=[\s/|]|$)')
    if ($match.Success) { return $match.Value }
    return $null
}

function Get-MessengerOrigins {
    param([Parameter(Mandatory)][string]$PublicOrigin, [ValidateRange(1024,65535)][int]$Port)
    if ((Get-QuickTunnelOrigin "$PublicOrigin ") -cne $PublicOrigin) {
        throw 'Expected an exact https://<name>.trycloudflare.com origin without a path.'
    }
    return "$PublicOrigin,http://localhost:$Port,http://127.0.0.1:$Port"
}

function Read-PublicEnv {
    param([Parameter(Mandatory)][string]$Path)
    $values = @{}
    $allowed = @('MONGODB_URI', 'APP_AES_KEY_BASE64', 'APP_JWT_SECRET_BASE64')
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $values }
    $number = 0
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $number++
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#')) { continue }
        if ($text -notmatch '^([A-Z_][A-Z0-9_]*)\s*=(.*)$') {
            throw "Invalid KEY=value syntax on line $number. Values are not printed."
        }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($allowed -notcontains $key) { throw "Unsupported setting on line $number." }
        if ($values.ContainsKey($key)) { throw "Duplicate setting on line $number." }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # Do not evaluate PowerShell, interpolate $, or expand values from the file.
        if ([string]::IsNullOrWhiteSpace($value) -or $value -match '^REPLACE_') {
            throw "Missing real value on line $number. Copy your EXISTING setting locally."
        }
        $values[$key] = $value
    }
    return $values
}

function Test-MessengerLocalPort {
    param([ValidateRange(1024,65535)][int]$Port)
    $tcp = New-Object Net.Sockets.TcpClient
    try {
        $task = $tcp.ConnectAsync('127.0.0.1', $Port)
        return ($task.Wait(500) -and $tcp.Connected)
    } catch { return $false } finally { $tcp.Dispose() }
}

Export-ModuleMember -Function Get-QuickTunnelOrigin, Get-MessengerOrigins, Read-PublicEnv, Test-MessengerLocalPort
