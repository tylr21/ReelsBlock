param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('\A[A-Za-z0-9_-]+\z')]
    [string]$Name,
    [ValidateSet('com.instagram.android','com.example.reelsblock')]
    [string]$PackageName = 'com.instagram.android'
)
$ErrorActionPreference = 'Stop'
# UI Automator writes to a pseudo-terminal, never a raw XML file on either device.
# Raw XML stays in this process's memory; only the fixed structural fields below reach disk.
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = 'adb'
$start.Arguments = 'shell -tt uiautomator dump /dev/tty'
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::Start($start)
$raw = $process.StandardOutput.ReadToEnd()
$errorText = $process.StandardError.ReadToEnd()
$process.WaitForExit()
if ($process.ExitCode -ne 0 -or $raw -match 'ERROR' -or $errorText -match 'ERROR') {
    throw 'UI Automator could not capture a fresh tree. No UI content saved.'
}
$xmlMatch = [regex]::Match($raw, '(?s)<hierarchy\b.*?</hierarchy>')
if (-not $xmlMatch.Success) {
    throw "No complete XML tree returned (stdout chars=$($raw.Length), hierarchy tag=$($raw.Contains('<hierarchy'))). No UI content saved."
}
try { [xml]$tree = $xmlMatch.Value }
catch { throw 'Invalid UI Automator XML. No UI content saved.' }
$raw = $null
$xmlMatch = $null
$errorText = $null
$foreground = $tree.SelectNodes("//node[@package='$PackageName']")
if ($foreground.Count -eq 0) { throw 'Phone is locked or the requested app is not in the foreground. Stop.' }
if ($tree.SelectNodes('//node[contains(@resource-id,"reply_bar") or contains(@resource-id,"message_composer") or contains(@resource-id,"direct_thread")]').Count -gt 0) {
    throw 'DM screen detected. Stop; no capture saved.'
}
foreach ($node in @($tree.SelectNodes('//node'))) {
    $tags = @()
    $descriptionTags = @()
    foreach ($pair in @(@('AD','Ad'), @('SUGGESTED','Suggested'), @('SUGGESTED_FOR_YOU','Suggested for you'))) {
        if ($node.text -eq $pair[1] -or $node.'content-desc' -eq $pair[1]) { $tags += $pair[0] }
        if ($node.'content-desc' -eq $pair[1]) { $descriptionTags += $pair[0] }
    }
    if ($node.'content-desc' -match '(?i)\bSponsored\b') {
        $descriptionTags += 'SPONSORED'
        $tags += 'SPONSORED'
    }
    foreach ($attribute in @($node.Attributes)) {
        if ($attribute.Name -notin @('resource-id','bounds','selected','class','focused','focusable','editable','clickable','package','checked','checkable','enabled')) { $node.RemoveAttribute($attribute.Name) }
    }
    if ($tags.Count -gt 0) { $node.SetAttribute('labels', $tags -join ',') }
    if ($descriptionTags.Count -gt 0) { $node.SetAttribute('description-labels', $descriptionTags -join ',') }
}
$directory = Join-Path $PSScriptRoot '..\dumps'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$tree.Save((Join-Path (Resolve-Path $directory) "$Name.xml"))
Write-Output "Saved redacted structure: dumps\$Name.xml"
