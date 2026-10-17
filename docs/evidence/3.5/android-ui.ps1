param([string]$Action = 'snapshot', [string]$Value = 'release-ui', [string]$Attribute = 'text')
$ErrorActionPreference = 'Stop'
$adb = 'C:\Users\hanan\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$evidence = $PSScriptRoot
function Read-Ui {
    $dumpOutput = & $adb -s emulator-5554 shell uiautomator dump /sdcard/release-ui.xml 2>&1
    if ($LASTEXITCODE -ne 0 -or "$dumpOutput" -match 'ERROR:') { throw "UI snapshot unavailable: $dumpOutput" }
    & $adb -s emulator-5554 pull /sdcard/release-ui.xml (Join-Path $evidence 'release-ui.xml') 2>&1 | Out-Null
    [xml]$tree = Get-Content -LiteralPath (Join-Path $evidence 'release-ui.xml')
    return $tree
}
if ($Action -eq 'tap') {
    $tree = Read-Ui
    $nodes = @($tree.SelectNodes('//node') | Where-Object { $_.GetAttribute($Attribute) -eq $Value })
    if ($nodes.Count -ne 1) { throw "Expected one visible match for $Attribute=$Value, got $($nodes.Count)" }
    $bounds = [regex]::Matches($nodes[0].bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $adb -s emulator-5554 shell input tap ([int](($bounds[0] + $bounds[2]) / 2)) ([int](($bounds[1] + $bounds[3]) / 2))
} elseif ($Action -eq 'capture') {
    & $adb -s emulator-5554 shell screencap -p /sdcard/release-capture.png
    $screens = Join-Path $evidence '..\..\screenshots\Workout-3.5'
    & $adb -s emulator-5554 pull /sdcard/release-capture.png (Join-Path $screens "$Value.png")
}
$tree = Read-Ui
if ($Action -eq 'snapshot') { Copy-Item -LiteralPath (Join-Path $evidence 'release-ui.xml') -Destination (Join-Path $evidence "$Value.xml") }
$tree.SelectNodes('//node[@text!="" or @content-desc!=""]') | ForEach-Object { '{0} | {1} | {2}' -f $_.text, $_.'content-desc', $_.bounds }


