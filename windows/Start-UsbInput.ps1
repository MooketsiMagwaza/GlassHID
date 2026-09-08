$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$receiver = Join-Path $PSScriptRoot 'usb_input_host.py'

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb was not found at $adb"
}

python $receiver --adb $adb
