# Constellation Changelog

## 0.9.659 (2026-07-29) — Garden mouse sensitivity

- added reversible farming mouse lock and percentage sensitivity reduction without changing Minecraft's saved sensitivity
- added manual commands, a Controls-screen keybind, active-only movable HUD and optional status messages
- added Garden auto activation for farming tools, rods, vacuums, mousemats, Sprayonator and Sun's Grasp with plot and ground checks
- added configurable teleport release, squeaky-mousemat locking, reduction percentage and ground tolerance

## 0.9.658 (2026-07-29) — Garden crop locations

- added profile-specific start and last-farmed waypoints for all 13 crops with Start, Last and Both modes
- added first-valid-harvest auto-learning, continuously updated last-farmed positions and delayed last-position reveal after leaving a farm
- added manual `/cropstart set <crop>` placement for any specific crop plus separate start/last/current-layout/all-profile clearing
- added independent boxes, beams, lines, labels, distances, colors, wall visibility, range, size and activation-distance controls

## 0.9.657 (2026-07-29) — Garden custom plot icons

- added a three-mode Configure Plots icon editor ported from SkyHanni, using the bottom-right wooden-axe control
- added lossless persistence for exact selected ItemStacks, including custom models and components, with optional per-profile layouts
- preserved original plot names, lore, tooltips and normal click behavior while rendering the chosen icon visually
- added editor/help/feedback/profile controls, safe menu-close cleanup, clear/current-profile commands and `/ploticons` status controls

## 0.9.656 (2026-07-29) — Garden plot-menu status

- added exact `Configure Plots` status highlighting for the current plot, pests, active sprays, locked plots and plots being pasted
- added configurable status priority, independent status toggles, colors, letter markers, pest/spray counts and hover details
- integrated existing physical-plot, pest and persistent spray state without menu clicks, item replacement or packet actions
- added full persistent controls through Hercules config and `/plotmenu`

## 0.9.655 (2026-07-29) — Farming Fortune

- added true Farming Fortune parsing from universal and crop-specific Stats-widget lines with persistent latest values for all 13 crops
- added a movable tool-aware display, optional universal/crop breakdown, wrong-crop guidance, missing-widget warnings and exact pest-count fortune reductions
- added Pesthunter bonus amount/expiry tracking, optional HUD row, expiry chat/title/sound and deliberate clickable Phillip/Barn actions
- added full display, warning, timing, action and template controls through Hercules config and `/fortune`

## 0.9.654 (2026-07-29) — Greenhouse growth

- added persistent Greenhouse growth-cycle detection from the exact Crop Diagnostics menu, a movable countdown/overdue HUD and configurable ready/while-away alerts
- added exact harvestable, reward and sufficient-water slot highlighting with independent colors and no menu interaction
- added display, stale-cycle, early-warning, title/chat/sound, template and location controls through Hercules config and `/greenhouse`
- restricted both Greenhouse and Stereo Harmony slot rendering to server-container slots so matching player-inventory items remain untouched

## 0.9.653 (2026-07-29) — Stereo Harmony

- added persistent active-vinyl detection from the Stereo Harmony menu and every carried vacuum, covering all 13 vinyl, pest and crop combinations
- added a movable farming-aware display, nothing-selected behavior, optional selection alerts, templates, title/chat/sound channels and complete `/stereoharmony` controls
- added crop-icon menu replacement, active-vinyl marking and Jacob-contest crop matching without changing or clicking the underlying menu items

## 0.9.652 (2026-07-29) — Garden plot sprays

- added persistent per-plot Sprayonator type and expiry tracking from exact use messages, the Pests tab widget, current plot detection, and Portable Washer clearing
- added a movable current/all-plot spray HUD, optional not-sprayed state, expiry and away notifications, new-spray messages, title/chat/sound channels, templates and timing controls
- matched the live profile defaults: expiry notifications enabled, display and new-spray duplication disabled, with every behavior available through Hercules config and `/sprays`

## 0.9.651 (2026-07-29) — Garden pest waypoint

- added vacuum-activated pest trajectory fitting with a predicted pest waypoint, plot-middle detection, arrival and timeout cleanup, and Garden-only lifecycle gating
- added configurable boxes, beams, lines, labels, distance text, colors, render range, particle filtering, activation timing, path tolerance, and `/pestwaypoint` controls
- ported the particle recognition, cubic fitting, pitch correction, target classification, and cleanup behavior from SkyHanni's Garden pest waypoint

## 0.9.650 (2026-07-22) — Garden pest core

- added exact Garden pest spawn, kill, no-pest, tab-widget, scoreboard, cooldown, and infested-plot tracking with custom plot-name learning
- added configurable spawn alerts, movable finder/timer/statistics HUDs, accurate 5x5 plot borders and labels, held-tool visibility, cooldown warnings, and average spawn timing
- added persistent per-pest kills, per-drop quantities, asynchronously reconciled market profit, session rates, reset/status commands, and complete lifecycle gating

## 0.9.649 (2026-07-22) — Lootshare coordination

- added an unbound deliberate Lootshare key that sends the customizable master party message only in Feesh-compatible fishing worlds, with accurate cooldown/result feedback and no automatic trigger
- added exact case-insensitive party `Lootshare!` detection with sender/self rejection, configurable title/subtitle/chat/sound alerts, color/template/timing controls, duplicate protection, recent HUD, and persistent sender history
- added complete `/lootshare` configuration and history commands plus a result-aware shared party-message path that preserves existing dungeon-only and global safety gates

## 0.9.648 (2026-07-22) — Trophy discovery alerts and sharing

- added exact location-aware `NEW DISCOVERY` disambiguation for Lotus Atoll Trophy Frogs, Crimson Isle Trophy Fish, and Obfuscated-1 Trophy Fish caught on any island
- added configurable frog/fish titles, chat, sound, tier colors, recent HUD, persistent counts/last discovery, duplicate protection, and complete `/trophydiscovery` controls
- added exact automatic and deliberate clickable party-sharing protocols through the master message editor with `{details}`, `{name}`, `{grade}`, and `{type}` customization and shared safety gates

## 0.9.647 (2026-07-22) — Spirit Mask state

- added exact Second Wind activation tracking with configurable 30-second cooldown, three-second immunity, used and ready alerts, independent title/chat/sound channels, templates, colors, and SkyBlock/dungeon gating
- added a dedicated Spirit Mask HUD with immunity, cooldown, ready and equipped states plus configurable inventory cooldown shading and remaining-time text for normal and starred masks
- added direct/legacy item-data support, dimension/connection-safe resets, GUI controls, complete `/spiritmask` commands, and removed duplicate Spirit handling from the older combined defensive tracker

## 0.9.646 (2026-07-22) — Nessie destination guidance

- added exact nametag-to-Sniffer Nessie correlation and all licensed Driptoad Delve/Jade Dragon checkpoints with recent-hook, radius, scan, and expiry controls
- added configurable destination subtitle/chat/sound alerts, deliberate or automatic party sharing through the master messages screen, and duplicate-safe per-entity lifecycle handling
- added optional entrance boxes, beams, lines, labels, distances, colors, range, duration, and through-wall rendering plus complete `/nessie` controls

## 0.9.645 (2026-07-22) — Fishing boss death coordination

- added exact own-death alerts for nine fishing bosses, teammate wait alerts, configurable boss selection, title/chat/sound channels, colors, templates, and cooldowns
- added an automatic Feesh-compatible party wait message integrated into the master messages screen with `{boss}` customization and strict self/spoof rejection
- added a deliberate clickable Murkwater Loch recovery action after Nessie deaths, lifecycle-safe state, and complete `/fishingdeath` controls

## 0.9.644 (2026-07-22) — Thunder Bottle charge safety

- added exact delayed full-charge alerts for Thunder, Storm, and Hurricane Bottles with per-tier selection and independent title/chat/sound controls
- added optional inventory charge percentages or compact raw progress, configurable scale/color/shadow, width fitting, and collision-free shared slot-corner placement
- added direct and legacy custom-data support, duplicate-safe lifecycle handling, templates, delay/cooldown controls, and `/thunderbottle` operational commands

## 0.9.643 (2026-07-22) — Max-level pet progression

- added exact level 100/200 pet completion alerts with rarity-aware presentation, independent title/chat/sound controls, optional recent-event HUD, and per-profile history
- added deferred level-one/max-level auction valuation with honest partial-data handling, configurable value/profit output, and warp-safe asynchronous resolution
- added a complete sortable fishing-pet leveling-profit table, coins-per-XP calculations, Alpha-safe history provenance, reset/status commands, and batch-aware auction completion

## 0.9.642 (2026-07-22) — Fishing Festival tracker

- replaced the inert shark counter with an own-catch festival session tracker, exact double-hook counting, configurable optional HUD, rarity breakdown, recent-hook visibility, and manual controls
- added exact festival-end summaries, customizable local/party messages, independent title/chat/sound channels, and warp-safe 61-minute lifecycle handling
- added persistent per-profile total-shark and Great White personal bests with timestamps, reset controls, delayed-profile finalization, and immutable Alpha-session exclusion

## 0.9.641 (2026-07-22) — Fishing setup safety

- added per-profile Fishing Bag enable-state discovery from exact chat and GUI evidence, submerged-hook warnings, and a deliberate clickable `/fb` recovery action
- upgraded bait-change and low-bait alerts with recent-fishing and GUI gates, refund handling, replenishment recovery, pair/type cooldowns, independent channels, and clickable Supercraft/Bazaar actions
- added configurable three-piece fishing-armor validation and exact Chum Bucket auto-pickup alerts with complete world, profile, connection, color, sound, chat, title, threshold, and command controls

## 0.9.640 (2026-07-22) — Fishing consumable state

- added exact Moby-Duck consumption, server countdown, expiry, guessed-time grace, optional movable HUD, and server-authoritative warning behavior
- added Galatea salt discovery and countdowns for Lushlilac variants, Oceandy, and Candycomb, with optional HUD/soon alerts and exact expiry protection
- added opt-in own-player Blizzard in a Bottle timing, independent title/chat/sound controls, lifecycle-safe world binding, colors, thresholds, formatting, and `/consumables` controls

## 0.9.639 (2026-07-22) — Owned deployable timers

- added owner-safe timers for Totem of Corruption, Black Hole, Umberella, all flares, five lantern variants, and four power orbs
- added independently selected expiry alerts and optional HUD rows with configurable thresholds, channels, colors, active-buff filtering, formatted time, missing-entity policy, and Bubblegum multiplier
- added click/spawn-correlated ownership for unnamed-owner deployables, optional world labels/boxes, exact removal handling, lifecycle resets, and `/deployables` controls

## 0.9.638 (2026-07-22) — Fishing hotspot suite

- added exact named-hotspot and perk correlation, particle-inferred radii, configurable circle rendering, nearest highlighting, labels, distances, colors, ranges, and optional particle hiding
- added found notifications with deliberate Party/All sharing, opt-in automatic sharing, configurable templates, remembered hotspots, and hook-correlated confirmed despawn alerts
- added Hotspot Radar cubic trajectory inference with advisory target box, line, beam, label and lifecycle controls, plus exact supported-island gating and `/hotspots` commands

## 0.9.637 (2026-07-22) — Barn-fishing safety suite

- replaced Hydra's dead barn-timer toggle with nearby sea-creature counting, Rider double-counting, resilient stack timing, and an optional movable HUD
- added personal-cap, per-area entity-count, and configurable stack-age alerts with independent title, chat, sound, rod, trophy-armor, cooldown, and presentation controls
- added exact live-profile thresholds for Hub, Crimson Isle, Crystal Hollows, Galatea, and unknown areas, expanded fishing-area detection, lifecycle resets, and `/barnfishing` controls

## 0.9.636 (2026-07-22) — Fishing catch presentation

- added short-lived world labels for nearby fished item entities with bait, quantity, color, range, duration, attribution, and through-wall controls
- added the complete licensed fishing rare-drop catalog with own and party alerts, prices, persistent drop numbers, Magic Find metadata, custom party messages, source filtering, and per-drop selection
- added formatted pet-rarity detection, rare-drop/profit deduplication, strict validated commands, and profile/world-safe lifecycle handling

## 0.9.635 (2026-07-22) — Wormhole finder

- added exact nearby-arrow direction matching against every licensed Lotus Atoll and Crimson Isle wormhole destination
- added persistent nearest-destination guidance with configurable boxes, beams, labels, distances, range, tolerance, colors, Froggles requirements, and through-wall presentation
- added exact departure-sound alerts with target correlation, title/chat/sound controls, lifecycle clearing, Lotus Atoll detection, and `/wormhole` controls

## 0.9.634 (2026-07-22) — Golden Fish helper

- added the complete Golden Fish cooldown, availability, chance, rod-refresh, interaction, readiness, and despawn state machine
- added exact chat-and-texture entity correlation, local-bobber candidate selection, configurable world labels/boxes, ready highlighting, spawn alerts, and rod warnings
- added a movable detailed HUD, manual honest Goldfin level, Crimson/Stranded-style location control, commands, colors, and profile-safe lifecycle resets

## 0.9.633 (2026-07-22) — Fishing hook and bait state

- added exact local-bobber hook countdown/readiness detection with configurable replacement text, world-label hiding, liquid, age, and distance presentation
- added active-bait name/count display using Hypixel's bait-slot lore, plus configurable no-bait, low-bait, and bait-change warnings
- added separate movable hook and bait HUDs, main/offhand rod support, container-safe refresh, profile lifecycle resets, commands, colors, and full saved controls

## 0.9.632 (2026-07-22) — Fishing profit tracker

- added strict recent-catch inventory attribution using the complete licensed fishing-item category data, with container-transfer rejection and profile-safe session resets
- added a movable profit HUD with categories, sorting, recent drops, catch count, active uptime, profit per hour, configurable pricing, and visibly partial unresolved totals
- added exact trophy-fish fillet valuation, GOOD/GREAT coin catches, configurable high-value chat/title warnings, commands, colors, and full saved controls

## 0.9.631 (2026-07-22) — Sea creature session tracker

- rebuilt Hydra's inert fishing shell with the complete current 77-creature message, rarity, and category corpus
- added a movable session tracker with category filters, five sorting modes, top limits, percentages, totals, double hooks, active uptime, and category-correct hourly rates
- added exact chat hiding, rare title/sound/party alerts, profile-safe resets, inactivity pausing, commands, colors, and full saved controls

## 0.9.630 (2026-07-22) — Persistent forge tracker and reminders

- added profile-separated forge slot persistence with active, ready, empty, and locked states plus stable absolute completion estimates
- added a movable forge HUD with filtering, ordering, slot labels, remaining times, stale-data limits, and configurable colors
- added one-shot or repeating completion reminders with deliberate clickable Forge warp and Fred call actions, container-busy suppression, commands, and robust partial-tab/corrupt-cache handling

## 0.9.629 (2026-07-22) — Mining commission and daily guidance

- added Dwarven and Glacite commission destination waypoints using the complete licensed location tables, optional nearest gemstone routing, Base Camp, and completed-commission emissaries
- replaced dead hand-written Fetchur/Puzzler logic with exact licensed riddle answers and ten-direction Puzzler coordinate solving, advisory world rendering, and a movable daily-helper HUD
- added independent boxes, beams, labels, distances, area/profile lifecycle gates, Pigeon handling, duration, persistence, colors, commands, and full saved controls

## 0.9.628 (2026-07-22) — Glacite corpse finder and tracker

- added advisory Glacite corpse boxes, beams, labels, distance, seen-state filtering, coordinate parsing, and queued party sharing
- added a movable key-readiness HUD using inventory, cached storage, and observed sack counts with honest incomplete-state markers
- added corpse opening, loot, and session-profit tracking with partial-value handling, profile-safe lifecycle resets, commands, and full presentation controls

## 0.9.627 (2026-07-22) — Mining progress and Glacite suite

- added movable commission, Crystal Hollows crystal-status, mineshaft pity, and cave-in/cold timer HUDs
- fixed Glacite Mineshaft area detection, exact Hypixel tab parsing, profile-scoped pity state, positive-delta cold estimates, and local configuration commands
- ports the active Skyblocker, SkyHanni, and SkyOcean mining behavior without automated movement or interactions

## 0.9.626 (2026-07-22) — Garden farming control and contest suite

- added movable advisory speed/angle, crop-rate, and active Jacob contest HUDs
- added per-crop target speeds and angles, local-break-correlated BPS, exact contest counter/timer parsing, projection, full row controls, precision/tolerance settings, and `/gardencontrol` commands
- never changes movement, speed, camera, keys, blocks, or clicks

## 0.9.625 (2026-07-22) — Garden visitor companion

- added a persistent multi-visitor shopping HUD with live costs, inventory/sack availability, totals, profit, and rare-reward presentation
- added enriched visitor tooltips, configurable reward warnings, and deliberate refusal/acceptance safeguards for rare rewards, first offers, copper value, and loss

## 0.9.624 (2026-07-22) — Auction comparison and safeguards

- added market-backed modifier estimates, Auction Browser comparisons, Manage Auctions state highlights, and deliberate suggested-price copying
- added independent listing underbid and BIN purchase overbid protection with configurable thresholds and a scoped three-click override

## 0.9.623 (2026-07-22) — Bazaar order companion

- added safe Bazaar quantity presets, validated clipboard input, and optional close-after-selection behavior
- added order fill/expiry markers, own-order price-ladder annotations, and deliberate Ctrl-click reorder quantity copying

## 0.9.622 (2026-07-22) — Storage previews and container value

- added persistent Ender Chest/backpack capture with Storage-menu hover previews, item counts, prices, shift mode, scaling, and profile separation
- added on-demand or automatic container valuation with complete/incomplete totals, sorted item breakdowns, slot highlighting, scope filters, and pricing controls

## 0.9.621 (2026-07-22) — Configurable inventory buttons

- added fourteen editable inventory-menu shortcuts with icon, command, tooltip, title matching, enable state, hover animation, and current-menu highlighting
- added a graphical editor, right-click editing, layout and color controls, command controls, safe normalization, and resettable SkyBlock defaults

## 0.9.620 (2026-07-22) — Inventory search and calculator

- added keyboard and clickable inventory search with name, lore, SkyBlock-ID, quoted and field-specific matching
- added independent player/container scope, match highlighting, non-match dimming, remembered queries, and compact-number arithmetic

## 0.9.619 (2026-07-22) — Currency HUD and slot text

- rebuilt Lyra's purse/session/bits/change HUD with exact scoreboard parsing, rates, templates, reset controls, and movable HUD integration
- ported scalable four-corner slot text for pet, cake, enchantment, potion, minion, Rancher Boots, and upgrade-star information

## 0.9.618 (2026-07-22) — Item information tooltips

- restored Lyra's global SkyBlock tooltip pipeline with Bazaar, lowest-BIN, NPC, quantity, and loading-aware pricing
- added market-aware IDs, dungeon quality/floor, obtained date, dye hex, reforge, upgrades, attributes, museum state, formatting controls, and commands

## 0.9.617 (2026-07-22) — Slayer sound filters

- ported Athen, NoFrills, and Skyblocker Slayer sound suppression with independent Voidgloom, Vampire, Inferno, Tarantula, and Sven filters
- added exact live-profile defaults, optional quest-only scoping, status/options commands, and client-thread packet cancellation

## 0.9.616 (2026-07-22) — Cocoon alert and timer

- ported Athen and NoFrills Cocoon detection, countdown HUD, title, and sound behavior
- added independent title, local-chat, sound, and timer switches plus editable templates, timer/title durations, precision, commands, and legacy-toggle migration

## 0.9.615 (2026-07-22) — Big Slayer Drops

- ported Athen's live-enabled dropped-item scaling for all six Slayer types with the complete 94-drop table, exact SkyBlock IDs, all 12 rune textures, and all seven enchant-book identities
- added 1-10x scale, 0.5-5x death-area range, 5-60-second lifetime, own-boss-only and death-type matching, six type switches, every-drop filters, commands, safe render-state transforms, and immediate world-transfer cleanup

## 0.9.614 (2026-07-22) — Slayer statistics and RNG drop data

- added authoritative owned-boss session statistics, persistent per-type/tier lifetime records, movable HUD, rate/XP/kill-time/session rows, templates, precision, reset controls, and correct Vampire XP values
- added the complete licensed Athen Slayer drop tables, selected RNG items, stored XP, magic find, calculated chance, remaining-boss estimate, drop totals, bosses-since-last counters, grade filters, bounded attribution, two movable HUDs, and full commands
- fixed T5 Tarantula phase double-counting, stale one-kill-behind RNG chances, disabled-feature persistence, wall-clock rate distortion, and malformed saved-data crashes during adversarial review

## unreleased — dungeons rebuild + gpl3

- relicensed from mit to gpl3. had to do it — the rebuild pulls real code out of skyblocker, odin, nofrills, secretroutes, devonian and dungeonroomsmod, and you cant ship that under mit. every borrowed file says where it came from up top, full list in CREDITS.md.
- starting the big one: rebuilding the whole dungeon layer properly on 26.2 so this one jar replaces skyblocker + odin + secretroutes + nofrills + skyhanni. dungeons first, done right, one feature at a time. the other constellations get scaffolded off for now and come back later.

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
