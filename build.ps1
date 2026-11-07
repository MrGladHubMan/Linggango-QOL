# Builds build\libs\linggango_easy_parry-<version>.jar (version comes from mods.toml).
#
#   .\build.ps1            build only
#   .\build.ps1 -Deploy    build and drop it into the instance mods folder (close the game first)
#
# Compiles straight against the jars from your CurseForge install, no Gradle needed.
param(
    [switch]$Deploy,
    [string]$Instance = "$env:USERPROFILE\curseforge\minecraft\Instances\Linggango [6.6.6 - Goety Update]",
    [string]$McInstall = "$env:USERPROFILE\curseforge\minecraft\Install"
)

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$jdk = "$McInstall\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma\bin"
$mods = "$Instance\mods"
$lib = "$McInstall\libraries"

$modJars = 'player-animation-lib-forge-*', 'class_api-*', 'linggango_tweaks-*', 'hit_indicator-*', 'curios-forge-*'
$libJars = @(
    'net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar'
    'net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-universal.jar'
    'net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-client.jar'
    'net/minecraftforge/eventbus/6.2.33/eventbus-6.2.33.jar'
    'net/minecraftforge/fmlcore/1.20.1-47.4.20/fmlcore-1.20.1-47.4.20.jar'
    'net/minecraftforge/fmlloader/1.20.1-47.4.20/fmlloader-1.20.1-47.4.20.jar'
    'net/minecraftforge/javafmllanguage/1.20.1-47.4.20/javafmllanguage-1.20.1-47.4.20.jar'
    'net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar'
    'net/minecraftforge/mergetool/1.1.5/mergetool-1.1.5-api.jar'
    'net/minecraftforge/coremods/5.2.4/coremods-5.2.4.jar'
    'net/minecraftforge/accesstransformers/8.0.4/accesstransformers-8.0.4.jar'
    'cpw/mods/modlauncher/10.0.9/modlauncher-10.0.9.jar'
    'cpw/mods/securejarhandler/2.1.10/securejarhandler-2.1.10.jar'
    'com/mojang/authlib/4.0.43/authlib-4.0.43.jar'
    'com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar'
    'com/mojang/datafixerupper/6.0.8/datafixerupper-6.0.8.jar'
    'com/mojang/logging/1.1.1/logging-1.1.1.jar'
    'it/unimi/dsi/fastutil/8.5.9/fastutil-8.5.9.jar'
    'com/electronwill/night-config/core/3.6.4/core-3.6.4.jar'
    'com/electronwill/night-config/toml/3.6.4/toml-3.6.4.jar'
    'com/google/guava/guava/31.1-jre/guava-31.1-jre.jar'
    'org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar'
    'org/joml/joml/1.10.5/joml-1.10.5.jar'
    'io/netty/netty-buffer/4.1.82.Final/netty-buffer-4.1.82.Final.jar'
    'io/netty/netty-common/4.1.82.Final/netty-common-4.1.82.Final.jar'
    'io/netty/netty-transport/4.1.82.Final/netty-transport-4.1.82.Final.jar'
    'org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar'
    'org/lwjgl/lwjgl-openal/3.3.1/lwjgl-openal-3.3.1.jar'
    'org/apache/maven/maven-artifact/3.8.5/maven-artifact-3.8.5.jar'
)

$cp = @()
foreach ($m in $modJars) {
    $hit = Get-ChildItem -LiteralPath $mods -Filter "$m.jar" | Select-Object -First 1
    if (-not $hit) { throw "missing mod jar in ${mods}: $m" }
    $cp += $hit.FullName
}
foreach ($l in $libJars) {
    $p = Join-Path $lib $l
    if (-not (Test-Path -LiteralPath $p)) { throw "missing library: $l" }
    $cp += $p
}

$toml = Get-Content -LiteralPath "$root\src\main\resources\META-INF\mods.toml" -Raw
if ($toml -notmatch '(?m)^version="([^"]+)"') { throw 'version not found in mods.toml' }
$version = $Matches[1]

$out = "$root\build"
$classes = "$out\classes"
if (Test-Path $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes, "$out\libs" | Out-Null

# javac @argfiles treat backslashes as escapes, so use forward slashes
$javacArgs = @('--release 17', '-encoding UTF-8', '-Xlint:-removal', "-d `"$($classes.Replace('\','/'))`"",
          "-cp `"$(($cp | ForEach-Object { $_.Replace('\','/') }) -join ';')`"")
$javacArgs += Get-ChildItem -Path "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
[System.IO.File]::WriteAllLines("$out\javac.args", $javacArgs)

cmd /c "`"$jdk\javac.exe`" `"@$out\javac.args`" 2>&1" | Where-Object { $_ -notmatch '^Note:' }
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Path "$root\src\main\resources\*" -Destination $classes -Recurse -Force
Remove-Item "$out\libs\*.jar" -Force -ErrorAction SilentlyContinue
$jar = "$out\libs\linggango_easy_parry-$version.jar"
& "$jdk\jar.exe" -cf $jar -C $classes .
if (-not (Test-Path $jar)) { throw 'jar was not created' }
Write-Host "built $jar"

if ($Deploy) {
    Get-ChildItem -LiteralPath $mods -Filter 'linggango_easy_parry-*.jar' | Remove-Item -Force
    Copy-Item -LiteralPath $jar -Destination $mods -Force
    Write-Host "deployed to $mods"
}
$jar
