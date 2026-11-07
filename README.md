# Linggango QOL

Small Forge add-on for the **Linggango** modpack (MC 1.20.1). Makes parrying less punishing, gives you room for all your accessories, and adds a few fun extras. Everything is toggleable from one menu.

<img src="docs/hakari-dance.gif" width="260" align="right" alt="hakari jackpot dance">

**Press `O` in game** to open the menu.

- **Parry** – longer window, auto-parry, perfect parry chance, no miss lockout, parry anything from any side
- **Accessories** – up to 128 extra curio slots that take any accessory, no 3-set limit
- **Return by Death** – lethal damage rewinds you a few seconds instead of killing you
- **Hakari jackpot** – Gambler's jackpot gives the green aura, rapid heal, music and the dance
- **Item magnet** – pulls drops and XP to you, hold sneak to pause
- **World tweaks** – turns off the cave hazards, boss gates and a few other pain points

Turning the master switch off puts parry back to normal but never touches your accessories.

## Install

1. Download the jar: [**linggango_easy_parry.jar**](https://github.com/MrGladHubMan/Linggango-QOL/raw/updates/linggango_easy_parry.jar)
2. Drop it in the instance `mods` folder
3. That's it. It updates itself from now on

Playing together? Everyone needs the mod, and the same version. Auto-update handles that.

## Auto-update

On launch the mod checks [`update.json`](https://github.com/MrGladHubMan/Linggango-QOL/blob/updates/update.json). If there's a newer version it downloads it in the background, checks the SHA-256, and swaps the jar when you close the game. You get a chat message when one is waiting.

Don't want it? Turn off **Auto-Update** in the `O` menu.

## Building

No Gradle. `build.ps1` compiles against the jars already in your CurseForge install.

```powershell
.\build.ps1           # -> build\libs\linggango_easy_parry-<version>.jar
.\build.ps1 -Deploy   # also copies it into the instance mods folder
```

Shipping an update:

```powershell
# bump version in src/main/resources/META-INF/mods.toml, then
.\publish_update.ps1 -Changelog "what changed"
```

That builds, puts the jar + `update.json` on the `updates` branch, and pushes. Players get it next launch.

## Needs

Forge 47.x, Curios, Linggango Tweaks, Hit Indicator, playerAnimator, Class API. All already in the pack.
