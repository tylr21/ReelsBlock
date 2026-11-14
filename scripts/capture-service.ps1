param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('\A[A-Za-z0-9_-]+\z')]
    [string]$Name
)
$ErrorActionPreference = 'Stop'
$directory = Join-Path $PSScriptRoot '..\dumps'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
# Diagnostics remain in Logcat/process memory; save only the derived structural XML.
adb logcat -c
adb shell am broadcast -a com.example.reelsblock.DUMP -p com.example.reelsblock | Out-Null
$lines = @()
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 300
    $lines = @(adb logcat -d -s ReelsGuard)
    if ($lines -match 'Node dump end') { break }
}
if (-not ($lines -match 'Node dump end .*unavailableChildren=0 failedNodes=0')) {
    throw 'No complete Instagram service tree; stop and check foreground/service.'
}
$document = New-Object System.Xml.XmlDocument
$hierarchy = $document.CreateElement('hierarchy')
$hierarchy.SetAttribute('source', 'ReelsGuard-service-fallback')
$document.AppendChild($hierarchy) | Out-Null
$nodes = @{}
foreach ($line in $lines) {
    if ($line -notmatch 'path=(\d+(?:/\d+)*) viewId=(\S+) .*?class=(\S+) selected=(true|false) .*?bounds=\[(-?\d+),(-?\d+),(-?\d+),(-?\d+)\]') { continue }
    $path = $Matches[1]
    $node = $document.CreateElement('node')
    $node.SetAttribute('resource-id', $Matches[2])
    $node.SetAttribute('class', $Matches[3])
    $node.SetAttribute('selected', $Matches[4])
    $node.SetAttribute('bounds', ('[{0},{1}][{2},{3}]' -f $Matches[5],$Matches[6],$Matches[7],$Matches[8]))
    foreach ($field in @('focused','editable')) {
        if ($line -match "$field=(true|false)") { $node.SetAttribute($field, $Matches[1]) }
    }
    $tags = @([regex]::Matches($line, 'lbl=([A-Z_]+)=true') | ForEach-Object { $_.Groups[1].Value })
    if ($tags.Count) { $node.SetAttribute('labels', $tags -join ',') }
    if ($path.Contains('/')) { $nodes[$path.Substring(0, $path.LastIndexOf('/'))].AppendChild($node) | Out-Null }
    else { $hierarchy.AppendChild($node) | Out-Null }
    $nodes[$path] = $node
}
if (-not $nodes.Count) { throw 'Empty service tree.' }
$document.Save((Join-Path (Resolve-Path $directory) "$Name.xml"))
Write-Output "Saved service fallback: dumps\$Name.xml nodes=$($nodes.Count)"
