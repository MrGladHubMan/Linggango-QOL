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

# git prints progress on stderr, so don't treat that as a failure; check exit codes instead
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$rawBase = 'https://raw.githubusercontent.com/MrGladHubMan/Linggango-QOL/updates'
$wt = "$root\.updates"

function Run-Git { git.exe @args 2>&1 | ForEach-Object { "$_" }; if ($LASTEXITCODE -ne 0) { throw "git $args failed" } }

if (-not (Test-Path "$wt\.git")) {
    if (git.exe -C $root ls-remote --heads origin updates) {
        Run-Git -C $root fetch origin updates
        Run-Git -C $root worktree add $wt updates
    } else {
        Run-Git -C $root worktree add --orphan -b updates $wt
    }
}

$jar = & "$root\build.ps1" -Deploy:$Deploy | Select-Object -Last 1
if (-not $jar -or -not (Test-Path $jar)) { throw 'build failed' }
if ((Split-Path $jar -Leaf) -notmatch '^linggango_easy_parry-(.+)\.jar$') { throw "unexpected jar name: $jar" }
$version = $Matches[1]

# same file name every release so the README download link never goes stale;
# the ?v= in the manifest url dodges GitHub's raw cache
$name = 'linggango_easy_parry.jar'
Copy-Item -LiteralPath $jar -Destination "$wt\$name" -Force
$sha = (Get-FileHash -Algorithm SHA256 -LiteralPath "$wt\$name").Hash.ToLower()

$log = $Changelog.Replace('\', '\\').Replace('"', '\"')
$json = @"
{
  "version": "$version",
  "url": "$rawBase/$name`?v=$version",
  "sha256": "$sha",
  "changelog": "$log"
}
"@
[System.IO.File]::WriteAllText("$wt\update.json", $json.Replace("`r`n", "`n") + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host $json

Run-Git -C $wt add -A
Run-Git -C $wt commit -m "v$version"
if (-not $NoPush) {
    Run-Git -C $wt push -u origin updates
    Write-Host "v$version is live"
}
