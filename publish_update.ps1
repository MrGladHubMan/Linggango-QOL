# Ships a new version to everyone. Games check update.json on start, download the jar,
# and swap it in when they close.
#
#   1. bump version="x.y.z" in src/main/resources/META-INF/mods.toml
#   2. .\publish_update.ps1 -Changelog "what changed"
#
# Release files live on the `updates` branch (checked out in .updates/), so main stays source only.
param(
    [string]$Changelog = '',
    [switch]$Deploy,
    [switch]$NoPush
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$rawBase = 'https://raw.githubusercontent.com/MrGladHubMan/Linggango-QOL/updates'
$wt = "$root\.updates"

if (-not (Test-Path "$wt\.git")) {
    git -C $root fetch origin updates 2>$null
    if ($LASTEXITCODE -eq 0) { git -C $root worktree add $wt updates }
    else { git -C $root worktree add --orphan -b updates $wt }
}

$jar = & "$root\build.ps1" -Deploy:$Deploy | Select-Object -Last 1
if ((Split-Path $jar -Leaf) -notmatch '^linggango_easy_parry-(.+)\.jar$') { throw "unexpected jar name: $jar" }
$version = $Matches[1]

# same file name every release so the README download link never goes stale;
# the ?v= in the manifest url dodges GitHub's raw cache
$name = 'linggango_easy_parry.jar'
Copy-Item -LiteralPath $jar -Destination "$wt\$name" -Force
$sha = (Get-FileHash -Algorithm SHA256 -LiteralPath "$wt\$name").Hash.ToLower()

$json = [ordered]@{
    version   = $version
    url       = "$rawBase/$name`?v=$version"
    sha256    = $sha
    changelog = $Changelog
} | ConvertTo-Json
[System.IO.File]::WriteAllText("$wt\update.json", $json + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host $json

git -C $wt add -A
git -C $wt commit -m "v$version"
if (-not $NoPush) {
    git -C $wt push -u origin updates
    Write-Host "v$version is live"
}
