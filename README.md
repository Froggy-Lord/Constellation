# Constellation

a skyblock mod for fabric 1.21.5 (mc 26.2). does pretty much everything — dungeons, mining, farming, fishing, crimson isle, the rift, slayers, events, economy, qol. basically if it's in skyblock there's probly a feature for it.

## why

skyblocker does alot but you still need like 3 other mods alongside it for the stuff it misses. skyhanni needs a million dependencies and its own config system on top of whatever else ur running. then theres a seperate mod for dungeon solvers and another one for fishing and another one for farming and now youve got 7 mods all fighting over the same hud space with different config menus and different keybinds and nothing works together.

this is just one jar. one config file. one hud editor. everythings in the same place with the same toggles and the same keybinds. and if you dont want a feature you just turn off that constellation — no need to remove the whole mod because it has one thing you hate. also its mit and doesnt need 4 libraries to launch.

## what it does

14 constellations, each one handles a different part of the game:

- **andromeda** — the rift. timer, enigma souls, mirrorverse waypoints, effigies, motes, area hints
- **apollo** — hud. fps, ping, tps, coords, clock, health/mana/defense bars, speed, facing, potion timers
- **aquila** — mining. powder tracker, commissions, forge, cold warning, wishing compass, crystal nucleus, fetchur/puzzler
- **auriga** — experiments + misc. ultrasequencer, superpairs, chocolate factory, reforges, anvil helper, god pot timer, /shcalc
- **cassiopeia** — chat. spam filters (60 categories), timestamps, clickable links, mention alerts, compact damage, 60+ shortcuts (/f1-/f7, /h, /i, /dh, /pi, /bz, /ah, etc)
- **cygnus** — events + diana. calendar, mayor info, inquisitor waypoints, burrow chain, event pings, jerry timer, spooky/winter/harvest helpers
- **draco** — crimson isle. kuudra phases, vanquisher alerts, reputation, dojo, ashfang freeze, abiphone, magmafish, trophy tracking
- **hercules** — farming. contest hud, visitor requirements, pest counter, crop milestones, composter, speed display, plot helpers
- **hydra** — fishing. sea creature alerts, trophy fish, golden fish timer, barn timer, bait warnings, hotspot radar, thunder highlight
- **lyra** — economy. purse tracker, bazaar prices on tooltips, auction alerts, essence counter, inventory value, salvage helper, true hex for dyes, exotic armour
- **orion** — dungeons. score hud, secret waypoints, ALL puzzle solvers (terminals, blaze, boulder, ice fill, waterboard, silverfish, tic tac toe, creeper beams, trivia, etc), combat esp (starred mobs, minibosses, livid finder), m7 phase tracking, spirit leap, blessings, chest profit, dungeon map
- **pegasus** — party. /rp reparty, party triggers, carry mode, ready checker, friend list hud, marked players
- **perseus** — slayers. boss timer, xp bar, miniboss alerts, bestiary milestones, broodmother, relics, rng meter
- **phoenix** — qol. fullbright, auto sprint, etherwarp overlay, hide lightning/fire/falling blocks, instant sneak, wardrobe keybinds, auto save reminder, sign calculator, hotbar lock

## install

1. get fabric loader 0.19.3+ for mc 26.2
2. get fabric api 0.152.2+
3. drop constellation-*.jar in your mods folder
4. thats it

requires java 25. if your launcher is using java 21 it will crash.

## commands

everything is under `/cn` or `/constellation`. the useful ones:

- `/cn toggle <constellation>` — turn a whole module on or off
- `/cn hud` — opens the hud editor so u can drag stuff around
- `/cn scrape <mode>` — dumps game data to json for debugging (sidebar, tab, entities, gui, etc)
- `/cn config` — opens the config screen

also has a bunch of quality of life shortcuts like `/f7` for f7, `/h` for hub, `/bz` for bazaar, `/is` for island, etc. full list in the config.

## config

everything's toggleable. hit right shift for the hub screen, or `/cn config` for the full settings. each constellation has its own section with toggles for every individual feature.

hud elements are draggable — open `/cn hud` and move stuff where you want it. positions are saved per-element.

## scrapes

the mod auto-scrapes as you play — sidebar, tab, entities, gui contents, chat. everything goes to `config/constellation-scrapes/`. useful if you're reporting a bug or want to see what data the mod sees.

can also manually trigger with `/cn scrape all` for a full dump.

## credits

inspired by skyblocker, skyhanni, odin, and basically every skyblock mod. the dungeon solvers are my own algorithms tho — the boulder/ice fill/silverfish/waterboard ones are clean-room implementations, not copied from anywhere.

dungeon data and waypoint coordinates come from hypixel's public game data.

## fair play

all solvers are **advisory only** — they highlight, box, and draw lines to help you solve puzzles, but they never click anything for you. no auto-click, no auto-solve, no packet manipulation. everything this mod does is visual overlay on top of the game. if you can see it, the mod can too, and nothing more.

this isnt some legal disclaimer to cover my arse — its how the mod actually works. the superpairs experiment solver used to auto-flip cards and i removed that specifically because it crossed the line from helper to automation.

if you want something that plays the game for you this is the wrong mod.

## verified areas

the sidebar/tab/gui patterns that read data from hypixel need to match exactly or the widget just never shows anything. these areas have been checked against live scrapes and confirmed working:

- ✅ hub (purse, bits, calendar, area)
- ✅ garden (copper, sowdust, pests, visitors, contest — all from tab)
- ✅ catacombs (time elapsed, cleared %, score)
- ✅ crimson isle (reputation, dojo, vanquisher — tab only, sidebar has none of this)
- ✅ dwarven mines (powders, commissions, forges, daily quests — all from tab)
- ✅ crystal hollows (crystals, purse, bits)
- ✅ rift (motes, enigma souls, time left — time is tab-only, motes on sidebar)
- ❌ kuudra — needs live scrape
- ❌ glacite tunnels — no data yet

the ones marked ❌ are guesses at the hypixel format and probly dont fire. turn on `/cn verify` in those areas and check the log for NO-MATCH lines.

## license

mit. do whatever you want with it, just dont blame me if it breaks.
