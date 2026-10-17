$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$target = Join-Path $root 'dist\Workout-3.4-source.zip'
if (Test-Path -LiteralPath $target) { throw 'Versioned source archive already exists; preserve it.' }
$files = @(
    @('.gitignore', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE', 'README.md', 'settings.gradle.kts', 'app\build.gradle.kts', 'app\proguard-rules.pro') | ForEach-Object { Get-Item -LiteralPath (Join-Path $root $_) }
    @('app\src', 'gradle', 'docs') | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $root $_) -File -Recurse }
)
$hashes = @{}
$zip = [IO.Compression.ZipFile]::Open($target, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = [IO.Path]::GetRelativePath($root, $file.FullName).Replace('\', '/')
        $sourceStream = [IO.File]::Open($file.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        $memory = [IO.MemoryStream]::new()
        try { $sourceStream.CopyTo($memory) } finally { $sourceStream.Dispose() }
        try {
            $bytes = $memory.ToArray()
            $hashes[$relative] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
            $entry = $zip.CreateEntry($relative, [IO.Compression.CompressionLevel]::Optimal)
            $output = $entry.Open()
            try { $output.Write($bytes, 0, $bytes.Length) } finally { $output.Dispose() }
        } finally { $memory.Dispose() }
    }
} finally { $zip.Dispose() }
$read = [IO.Compression.ZipFile]::OpenRead($target)
try {
    if ($read.Entries.Count -ne $hashes.Count) { throw 'Archive entry count mismatch' }
    foreach ($entry in $read.Entries) {
        $stream = $entry.Open()
        try { $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) } finally { $stream.Dispose() }
        if ($actual -ne $hashes[$entry.FullName]) { throw "Archive verification failed: $($entry.FullName)" }
    }
    if ($read.Entries.FullName -match '(^|/)(build|\.gradle|dist)/|local\.properties|\.(jks|keystore)$') { throw 'Excluded file entered archive' }
    [pscustomobject]@{Entries=$read.Entries.Count;ContentHashesVerified=$true;ExcludedBuildAndKeys=$true} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'source-archive-check.json')
} finally { $read.Dispose() }
$apk = Get-FileHash -LiteralPath (Join-Path $root 'dist\Workout-3.4.0.apk') -Algorithm SHA256
$source = Get-FileHash -LiteralPath $target -Algorithm SHA256
@("$($apk.Hash)  Workout-3.4.0.apk", "$($source.Hash)  Workout-3.4-source.zip") | Set-Content -LiteralPath (Join-Path $root 'dist\SHA256-3.4.txt')
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'source-archive-check.json')
Get-Content -LiteralPath (Join-Path $root 'dist\SHA256-3.4.txt')
