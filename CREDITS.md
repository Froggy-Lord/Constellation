# Credits

Constellation is licensed **GPL-3.0-only** (see `LICENSE`). It has to be: it bundles
code lifted from GPL-3 and LGPL projects, and once you ship that in one jar the whole
thing is a GPL-3 derivative. That's the deal and it's a good one — all of these mods are
open, so this one is too.

Below is where things came from. If a file contains ported code, it also carries a short
header saying which mod and which file it came from. This document is the master list.

## Copy-OK sources (code ported directly, licenses compatible with GPL-3)

| Project | License | Used for |
|---------|---------|----------|
| [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) | LGPL-3.0 | Room detection + map coordinate math, dungeon score formula and floor tables, terminal solvers, secret auto-detect, render primitives (`PrimitiveCollector`), Croesus helper, Goldor waypoints |
| [Odin](https://github.com/odtheking/Odin) | BSD-3-Clause | Puzzle solvers and their solution JSONs (Blaze, Boulder, Ice Fill, Creeper Beams, Water Board, Three Weirdos, Trivia, Tic-Tac-Toe), etherwarp DDA traversal, M7 dragon/terminal suite, tick timers, waypoint editor, blood-camp prediction, terminal practice simulator, end-of-run stats |
| [Devonian](https://github.com/otarits/devonian) | GPL-3.0 | Dungeon map renderer, boss-phase `Stages` model, full M7 dragon suite, blessings display, boss splits |
| [NoFrills](https://github.com/kevinthegreat1/NoFrills) | GPL-3.0 | Starred-mob / miniboss / secret ESP and highlights, ArrowAlign 9-solution solver, Livid finder, class nametags/teammate glow, spirit leap overlay, mimic/prince messages |
| [SecretRoutes](https://github.com/Bloud01/SecretRoutes) | GPL-3.0 | Secret route record/playback, rotation-aware coordinate transforms |
| [DungeonRoomsMod](https://github.com/Quantizr/DungeonRoomsMod) | GPL-3.0 | `secretlocations.json` room secret data (124 rooms), room-hash approach |

## Reference-only sources (behaviour studied, NOT a single line copied)

These are AGPL or otherwise incompatible with distributing inside a GPL-3 jar, so anything
here is reimplemented from scratch against observed behaviour — never copied.

| Project | License | Referenced for |
|---------|---------|----------------|
| [SkyHanni](https://github.com/hannibal002/SkyHanni) | LGPL-2.1 | State API shape, blood timer, secret compass, spirit leap, chat filter categories |
| [Skytils](https://github.com/Skytils/SkytilsMod) | AGPL-3.0 | Reference only |
| [DungeonsGuide](https://github.com/SkytilsPlus/DungeonsGuide) | AGPL-3.0 | Reference only — TSP secret routing, Action DAG, inter-mod protocol concepts |
| [SkyOcean](https://github.com/Ocean-Labs/SkyOcean) | SkyOcean License v1 | Reference only |

## Bundled data

- Odin puzzle solution JSONs and room table — Odin (BSD-3), MC-version independent.
- `secretlocations.json` — DungeonRoomsMod (GPL-3).
- `goldorwaypoints.json` — Skyblocker (LGPL-3).
- Room fingerprints for MC 26.2 are regenerated in-house from a live scan; the old
  `.skeleton` files used 1.8.9 block IDs and are not portable.

## Attribution convention for ported files

Any source file that contains ported code starts with a header like:

```
// Ported from <Project> (<license>): <path/in/that/repo>
// Adapted for Minecraft 26.2 / com.froggylord.constellation.
```

If you're reading a file and it has no such header, it's original to this project.
