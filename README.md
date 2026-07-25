<img src="./src/main/resources/assets/constellation/icon.png" width="112" align="right" alt="Constellation icon">

# Constellation

A modular Fabric client for Hypixel SkyBlock on Minecraft 26.2.

[![Build](https://github.com/Froggy-Lord/Constellation/actions/workflows/ci.yml/badge.svg)](https://github.com/Froggy-Lord/Constellation/actions/workflows/ci.yml)

Constellation puts dungeon tools, skill trackers, event helpers and quality-of-life features behind one configuration system and one HUD editor. Each area of the game is a separate “constellation”, so broad modules and individual features can be disabled without rebuilding the rest of the client.

The project is still under active development. Treat releases as test builds and report server-format mismatches with the included scrape tools.

## How it works

Hypixel exposes most useful state through text and ordinary client data rather than a stable mod API. Constellation turns scoreboard lines, the tab list, action-bar messages, chat, inventories and world entities into a shared state model. Feature modules consume that model instead of maintaining their own competing parsers.

The repository is split around that flow:

- `core` and `data` handle location, timing, pattern matching and run state.
- `constellation` contains the game-facing modules.
- `hud`, `render` and `ui` own screen and world presentation.
- `config` keeps every module independently switchable.
- `api` wraps external price and item data.

The pattern test suite uses captured server strings as contracts. If a known Hypixel format stops matching, the build fails before the broken parser reaches a release.

## Feature map

Fourteen modules cover the main SkyBlock areas:

| Module | Area |
| --- | --- |
| Andromeda | Rift timers, souls and waypoints |
| Apollo | HUD, performance and player-state displays |
| Aquila | Mining commissions, forge and Crystal Hollows helpers |
| Auriga | Experiments and general utilities |
| Cassiopeia | Chat filtering, alerts and shortcuts |
| Cygnus | Calendar events and Diana |
| Draco | Crimson Isle and Kuudra |
| Hercules | Garden, farming contests and pests |
| Hydra | Fishing, trophy fish and hotspots |
| Lyra | Bazaar, auctions and inventory value |
| Orion | Dungeons, puzzles, routes, score and map |
| Pegasus | Party tools |
| Perseus | Slayers |
| Phoenix | Client-side quality of life |

See [the feature reference](docs/features.md) for a more detailed inventory.

## Install

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Install Fabric API 0.152.2 or newer.
3. Download the latest JAR from [Releases](https://github.com/Froggy-Lord/Constellation/releases).
4. Place the JAR in the instance’s `mods` directory.

Minecraft 26.2 requires Java 25. A launcher still configured for Java 21 will fail before the mod loads.

Open the hub with <kbd>Right Shift</kbd>, or use `/cn config`. HUD elements can be moved with `/cn hud`.

## Build

```bash
./gradlew build
```

The release JAR is written to `build/libs/`. CI runs the same command on every push and pull request.

## Diagnostics

`/cn scrape all` records the client data seen by the parsers under `config/constellation-scrapes/`. Scrapes are intended for debugging format changes; check them for player or chat data before attaching them to an issue.

The live-test checklist is maintained in [TESTING.md](TESTING.md).

## Fair play

Puzzle and route features provide overlays, highlights and guidance. They do not click, send movement, manipulate packets or complete puzzles for the player.

## Credits and license

Constellation combines original work with compatible open-source implementations and data from the SkyBlock modding community. Source-level attribution and bundled-data credits are listed in [CREDITS.md](CREDITS.md).

The project is distributed under [GPL-3.0-only](LICENSE).
