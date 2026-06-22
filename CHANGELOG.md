# Constellation Changelog

## 0.9.360 (2026-06-22) — Massive Feature Session

### Orion (Dungeons) — 69 toggles
- **Terminal solvers**: Click-in-order, Correct-all-panes, Select-colour, Starts-with, Melody
- **Blaze solver**: lowest/highest HP blaze boxed (F3/M3)
- **Simon Says**: chat clue → highlight correct button
- **Three Weirdos**: highlight correct NPC chest
- **Trivia**: 34-question answer database (verified from Odin)
- **TicTacToe**: minimax best-move highlight
- **Creeper Beams**: lantern link render
- **Livid Finder**: wool block colour detection at (5,110,42) (verified from Skyblocker)
- **M7 Dragon markers**: priority dragon label
- **Goldor waypoints**: 4-phase terminal positions (verified from Skyblocker)
- **Water puzzle**: gate block highlighter
- **Ice Fill**: filled/unfilled ice block render
- **Boulder**: anvil→pressure plate detection + direction hint
- **Silverfish**: entity highlight + nearest plate path
- **Guardian health**: F3/M3 health from nameplates
- **Shadow Assassin**: target alert + vanish countdown
- **Miniboss highlights**: LA, SA, Diamond Guy, King Midas, Spirit Bear
- **Rare room alerts**: Trinity, Tomioka, Duncan
- **Blessing tracker**: Power/Time/Wisdom/Life/Stone/Healing levels HUD
- **Fire Freeze timer**: 5.7s cooldown (verified from Skyblocker)
- **Spirit Bow timer**: 30s respawn timer
- **Door/key highlighter**: red→green door status + key beam
- **Spirit Leap helper**: class tags on teammate heads
- **Drop ESP**: spirit leap, decoy, training weights on floor
- **Dungeon Copilot**: score-based chat suggestions
- **Mage beam cleaner**: clean line instead of firework particles
- **Chest profit calculator**: live bazaar total on reward chests
- **Dungeon potions**: active effect display
- **/dndebug**: dump room/score/sidebar state
- **Starred mob detection**: checks custom + display + entity name
- **Bat animation filter**: skips bats near door blocks

### Apollo (Core HUD) — 18 HudEntry widgets
- FPS, Ping, TPS, Clock, Coords, HP, Mana, Defense, Speed, EHP, Overflow, Skill, Area, Facing, Potions, Power Orb, Cooldowns, Purse Change

### Cassiopeia (Chat/Commands) — 60 toggles
- 35+ chat spam filters (Skyblocker-style)
- Timestamps, clickable links, mention alerts
- AutoGG, AutoTip, compact damage numbers
- 30+ shortcuts: /bz /ah /craft /ec /wardrobe /sacks /pets /roll /ping /calc /mouselock /gfs /sendcoords /copycoords /getpearls /getleaps /getboom /getdraft /buy /sell
- ShortenCoins: compact 1,234,567→1.2M (preserves formatting)
- Right-click copy, container chat, party triggers
- Rainbow action bar, full inventory warning, legendary SC alert

### Lyra (Economy/Inventory) — 31 toggles
- Item tooltips: reforge, stars, hot potato, recomb, enchant count, SkyBlock ID
- Live bazaar prices (public Hypixel feed, daemon-thread cache, 3-min TTL)
- Stack total value, missing enchant detection, item quality (50/50)
- Attribute display, salvage safe indicator, backpack shift-hover preview
- Slot text on items: pet level, star count, cake year
- Auction outbid/sold alerts, bazaar undercut alerts
- /profile quick stats, /coinsreset, purse + bits HUD
- Accessory display, inventory value estimation

### Phoenix (QoL) — 27 toggles
- Fullbright, no hurt cam, no view bob, auto sprint
- Hide lightning, falling blocks, fire overlay, underwater blur
- Etherwarp: 61-block raycast, filled block, red=invalid landing
- Wardrobe keybinds: 9 configurable keys for instant armor swap
- Auto-save reminder: ping every 5 min
- Instant sneak, disable vignette, disable fog, no death animation
- Item protection, sign calculator, hide players in dungeon
- Hide attached arrows, prevent placing weapons

### Aquila (Mining) — 27 toggles
- Powder HUD, commission tracker, forge queue, wishing compass
- Cold threshold titles at 25/50/75/90/95/99% (no vignette)
- HOTM level, drill fuel bar, Pickonimbus durability
- Mineshaft entry alert, Scatha spawn + kill counter
- Fetchur item hints, Puzzler block answers (verified from Skyblocker)
- Golden Goblin alert, Pickobulus break prediction
- Crystal Nucleus waypoint render, treasure chest ESP
- Coleweight HUD, fossil helper, metal detector helper
- Gemstone mixture helper, mineshaft pity counter

### Hercules (Farming) — 22 toggles
- Contest HUD, visitors HUD, pest counter + alerts
- Crop milestone tracker, composter organic matter
- Rancher's Boots speed cap, Moonglade beacon, greenhouse
- Sweep overlay: harvest range when holding farming tool
- Space farmer: auto-hold space for farming rows
- Dicer message filter, crop growth display
- Glowing mushrooms: world render in garden

### Cygnus (Events/Diana) — 22 toggles
- SkyBlock calendar (date/time), mayor + perks display
- Diana: Inquisitor alert with exact coordinate parsing (SkyHanni regex)
- Diana burrow triangulation from spade directions
- Mythos drop tracker, chimera/daedalus alerts
- Carnival hints, Spooky Festival, Jerry timer, New Year cake
- Raffle helper, Hoppity eggs, chocolate factory
- Season display, event notifications, mayor election HUD

### Draco (Crimson Isle) — 22 toggles
- Reputation HUD, Vanquisher alert "Vanquisher is spawning" (verified pattern)
- Kuudra phase HUD, Ashfang freeze timer
- Dojo score HUD, Abiphone caller display
- Faction quest tracker, trophy fishing stats
- Fresh tools timer, supply objective HUD
- Key Guardian alert, heavy pearls counter
- Magmafish counter, trophy best display, blade volcano timer

### Hydra (Fishing) — 23 toggles
- Cast timer, sea creature tally, rare SC alerts (13 verified names)
- Hide other bobbers, Thunder entity highlight
- Trophy fish: bronze/silver/gold/diamond tracker
- Golden Fish timer, barn timer, shark counter, totem timer
- Cocoon alert, bait display, wormhole locator
- Odger waypoint, lava fishing spots, chum hider
- Fishing rod timer (colour change at 20s)

### Perseus (Slayers) — 20 toggles
- Slayer XP bar, RNG meter, zealot counter, protector %
- Boss spawn alert + custom sound, slayer kill timer + personal best
- Rare drop: title + PLAYER_LEVELUP sound (strips Hypixel § codes)
- Skill level-up alert, broken Hyperion warning
- Bestiary tracker, miniboss flash, SOS flare display
- Slayer profit tracker, tarantula invinc mark
- Spider Den relic waypoints (28 positions from Skyblocker data)

### Pegasus (Party) — 20 toggles
- Party membership tracker (parses join/leave chat)
- Real /rp reparty: disband + re-invite tracked members
- Party + Members HUD, carry mode ledger, /carry command
- /mark /unmark player tracking, ready checker
- Death highlight frames, friend join/leave alerts
- Party trigger system, dungeon ready overlay
- Nickname replacer, offline member indicator

### Andromeda (Rift) — 19 toggles
- Rift time HUD, motes counter, enigma soul tracker + 41 waypoint beams
- Effigy counter, rift low-time warning
- Mirrorverse waypoints: 7 sections with path lines (verified from Skyblocker)
- Area helpers: Dreadfarm, Living Cave, Mountain Top, Stillgore, Colosseum, Dance Room, West Village, Wyld Woods
- Blobbercyst glow, deadgehog counter, mote profit tracker
- Crux counter, Bluetooth ring helper

### Auriga (Experiments/Misc) — 20 toggles
- Experiment solvers: Ultrasequencer (lowest clock), Superpairs (click-lock + pair highlight)
- Anvil combine cost display (green/yellow/red)
- /shcalc damage estimator from sidebar stats
- Bingo helper, chocolate factory, power stone display
- Enchanted clock reminders, minion hopper tracker
- Evolving item timer, brew helper, god pot display
- Teleport pad helper, enchant table helper
- Attribute shard helper, pathfind util, cosmetic helper

### Core Infrastructure
- **StatStore**: persistent lifetime stats (slayer PB, Diana kills, scatha, sharks, trophy fish, enigma souls, effigies)
- **lifetimeStats** global toggle: all-time vs session display
- **BazaarApi**: live prices from public Hypixel feed, daemon-thread 3-min cache
- **ContainerScreenAccessor** mixin: leftPos/topPos for screen overlays
- **Auto-scraper**: `/cn scrape <mode>` + passive auto-scrape (sidebar, entities, GUI, chat, actionbar)
- **Generic ConfigScreen builder**: auto-discovers boolean fields for all 14 constellations
- **HubScreen**: responsive grid layout with descriptions, scroll, toggle switches

### Bug Fixes
- ActionBar k/M/B suffix parsing (was showing 20/3 instead of actual HP)
- Star mob detection: checks custom name, display name, AND entity name
- Bat animation filter: skips bats near coal/clay/terracotta (door materials)
- Chat format: shortenCoins only rebuilds Component when numbers actually change
- Etherwarp: long-range 61-block raycast, filled block, red=invalid
- HubScreen: vertical overflow → responsive grid with scroll
- Hypixel § color codes: strip ChatFormatting before matching rare-drop/level-up patterns

### Research Data
- **4.2MB** verified Hypixel data extracted from Skyblocker + SkyHanni + Odin source repos
- 28,578 string patterns, 5,046 chat messages, 767 entity names, 204 block positions
- 242 NBT ExtraAttributes keys, 34 trivia answers, 141 room skeletons
- Goldor waypoints (4 phases, 29 positions), Mirrorverse (7 sections)
- Enigma souls (41 positions), Spider Den relics (28 positions)
- Water/icefill/boulder/creeper beam puzzle solutions

### Build Stats
- **~188 builds this session** (0.9.166 → 0.9.360)
- **~424 total builds** across project history
- **~660 features** (~55% of ~1,200-feature catalogue)
- All headless-verified (0 mixin failures throughout)
- Email disabled (Google rate-limit from 47 rapid-fire sends)
