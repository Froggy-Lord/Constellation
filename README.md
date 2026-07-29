# Constellation

a skyblock mod for fabric 1.21.5 (mc 26.2). does pretty much everything — dungeons, mining, farming, fishing, crimson isle, the rift, slayers, events, economy, qol. basically if it's in skyblock there's probly a feature for it.

## why

skyblocker does alot but you still need like 3 other mods alongside it for the stuff it misses. skyhanni needs a million dependencies and its own config system on top of whatever else ur running. then theres a seperate mod for dungeon solvers and another one for fishing and another one for farming and now youve got 7 mods all fighting over the same hud space with different config menus and different keybinds and nothing works together.

this is just one jar. one config file. one hud editor. everythings in the same place with the same toggles and the same keybinds. and if you dont want a feature you just turn off that constellation — no need to remove the whole mod because it has one thing you hate. also its gpl3 and doesnt need 4 libraries to launch.

## what it does

14 constellations, each one handles a different part of the game:

- **andromeda** — the rift. timer, enigma souls, mirrorverse waypoints, effigies, motes, area hints
- **apollo** — hud. fps, ping, tps, coords, clock, health/mana/defense bars, speed, facing, potion timers
- **aquila** — mining. Powder/commission HUDs, persistent forge slots and reminders, commission destination guidance, live commission and crystal progress, exact Fetchur/Puzzler guidance, profile-safe mineshaft pity, cave-in/cold timing, Glacite corpse finder/key/profit tracking, HOTM, fuel, Pickonimbus, Scatha, treasure/compass/nucleus and mining helpers
- **auriga** — experiments + misc. ultrasequencer, superpairs, chocolate factory, reforges, anvil helper, god pot timer, /shcalc
- **cassiopeia** — chat. spam filters (60 categories), timestamps, clickable links, mention alerts, compact damage, 60+ shortcuts (/f1-/f7, /h, /i, /dh, /pi, /bz, /ah, etc)
- **cygnus** — events + diana. calendar, mayor info, inquisitor waypoints, burrow chain, event pings, jerry timer, spooky/winter/harvest helpers
- **draco** — crimson isle. kuudra phases, vanquisher alerts, reputation, dojo, ashfang freeze, abiphone, magmafish, trophy tracking
- **hercules** — farming. Garden visitor shopping, prices, sack availability, rewards and safeguards; advisory target speed/angles, yaw/pitch, crop rates, true Farming Fortune and Jacob contest progress; Garden pest spawn alerts, finder borders, cooldown timer, kill/drop/profit statistics, custom plot-name learning, vacuum particle-path waypoints, persistent plot-spray expiry tracking, Configure Plots status highlighting and custom icons, Stereo Harmony vinyl/crop guidance and Greenhouse growth diagnostics
- **hydra** — fishing. complete sea-creature and catch-profit trackers, Trophy Frog/Fish discovery alerts/history/sharing, deliberate Lootshare call key and teammate alerts/history, Fishing Festival counts/summaries/personal bests, max-level pet alerts/history and leveling-profit prices, Thunder/Storm/Hurricane Bottle charge alerts and slot progress, fishing-boss death/team coordination, Nessie destination alerts and cave guidance, exact hook readiness/timing, active bait/count/warnings, per-profile Fishing Bag protection, fishing-armor validation, clickable bait recovery, Chum Bucket recovery alerts, full Golden Fish cooldown/spawn/interaction guidance, Lotus Atoll/Crimson wormhole finding and departure alerts, world labels for fished items, complete own/party rare-drop alerts and sharing, area-aware barn-fishing protection, named hotspot/perk/radius rendering, found/share/gone lifecycle alerts, cubic Hotspot Radar guidance, owned deployable timers, Moby-Duck and Galatea salt state, optional Blizzard timing, expiry protection, item/category/value/rate statistics, trophy-fish fillet values and thunder highlighting
- **lyra** — economy. purse tracking, market tooltips, storage previews, inventory search/buttons, Bazaar orders, Auction House comparisons and safeguards
- **orion** — dungeons. score hud, secret waypoints, ALL puzzle solvers (terminals, blaze, boulder, ice fill, waterboard, silverfish, tic tac toe, creeper beams, trivia, etc), combat esp (starred mobs, minibosses, livid finder), complete Spirit Mask alerts/cooldown/immunity/HUD/item state, m7 phase tracking, spirit leap, blessings, chest profit, dungeon map
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

For a readable inventory of the current mod, see `CURRENT_FEATURE_OVERVIEW.md`. For a staged in-game checklist, see `TESTING_GUIDE.md`.

## scrapes

the mod auto-scrapes as you play — sidebar, tab, entities, gui contents, chat. everything goes to `config/constellation-scrapes/`. useful if you're reporting a bug or want to see what data the mod sees.

can also manually trigger with `/cn scrape all` for a full dump.

## credits

inspired by skyblocker, skyhanni, odin, and basically every skyblock mod. licensed implementations and data are ported copy-first where possible, with source-level credits beside each port and the full list in CREDITS.md.

dungeon data and waypoint coordinates come from hypixel's public game data.

## fair play

all solvers are **advisory only** — they highlight, box, and draw lines to help you solve puzzles, but they never click anything for you. no auto-click, no auto-solve, no packet manipulation. everything this mod does is visual overlay on top of the game. if you can see it, the mod can too, and nothing more.

this isnt some legal disclaimer to cover my arse — its how the mod actually works. the superpairs experiment solver used to auto-flip cards and i removed that specifically because it crossed the line from helper to automation.

if you want something that plays the game for you this is the wrong mod.

## verified areas

the sidebar/tab/gui patterns that read data from hypixel need to match exactly or the widget just never shows anything. these areas have been checked against live scrapes and confirmed working:

- verified: hub (purse, bits, calendar, area)
- verified: garden (copper, sowdust, pests, visitors, contest — all from tab)
- verified: catacombs (time elapsed, cleared %, score)
- verified: crimson isle (reputation, dojo, vanquisher — tab only, sidebar has none of this)
- verified: dwarven mines (powders, commissions, forges, daily quests — all from tab)
- verified: crystal hollows (crystals, purse, bits)
- verified: rift (motes, enigma souls, time left — time is tab-only, motes on sidebar)
- unverified: kuudra — needs live scrape
- unverified: glacite tunnels — no data yet

the unverified entries are guesses at the hypixel format and may not fire. turn on `/cn verify` in those areas and check the log for NO-MATCH lines.

## license

gpl3. it borrows real code from a bunch of other open dungeon mods (skyblocker, odin, nofrills, secretroutes, devonian, dungeonroomsmod — full list in CREDITS.md) so it has to be gpl. fine by me. do what you want with it as long as you keep it open, just dont blame me if it breaks.
