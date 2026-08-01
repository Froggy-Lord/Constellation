# Constellation

a skyblock mod for fabric 1.21.5 (mc 26.2). does pretty much everything — dungeons, mining, farming, fishing, crimson isle, the rift, slayers, events, economy, qol. basically if it's in skyblock there's probly a feature for it.

## why

skyblocker does alot but you still need like 3 other mods alongside it for the stuff it misses. skyhanni needs a million dependencies and its own config system on top of whatever else ur running. then theres a seperate mod for dungeon solvers and another one for fishing and another one for farming and now youve got 7 mods all fighting over the same hud space with different config menus and different keybinds and nothing works together.

this is just one jar. one config file. one hud editor. everythings in the same place with the same toggles and the same keybinds. and if you dont want a feature you just turn off that constellation — no need to remove the whole mod because it has one thing you hate. also its gpl3 and doesnt need 4 libraries to launch.

the main hub uses a hand-drawn star-map icon family for all 15 constellations, a matching mod mark, a sixteen-glyph semantic action strip, original low-contrast space artwork, searchable module navigation, clear direct toggles and consistent clipped-corner controls. each module screen can search names, descriptions and option labels, while the complete typed browser adds changed/default state, reversible edits, keyboard control and validated value editing.

the optional menu shell uses the same artwork and control language without replacing minecraft's logo, navigation, realms status, narration or input. title backdrop, title buttons, ordinary menu backgrounds, menu buttons, sliders and reduced motion are controlled independently from the hub's visuals screen. pause and ordinary in-world options can use a separate translucent scrim, blur, accent, buttons and sliders while leaving the game visible. accessibility, language, warning, recovery, loading, account, realms and third-party screens remain vanilla.

the player inventory can use a matching panel, model frame and slot treatment without replacing its recipe book, equipment, effects, items or input. ordinary vanilla chests have a separately scoped theme that is off on hypixel by default and always protects puzzle, spirit leap, auction, bazaar, trade, salvage and museum menus.

hypixel's chest-backed Craft Item screen can be rearranged into a proper 3x3 SkyBlock crafting interface with server-owned result and quick-craft slots, More Crafts navigation, filler hiding and the distinct Mirrorverse layout. every click still targets the original Hypixel slot.

vanilla crafting, furnace-family, brewing, enchanting, anvil, grindstone, smithing, stonecutter, loom, cartography and crafter screens can use exact-class workstation treatments with independently configurable stages, slots, machine details and colors. every treatment is off on hypixel by default and leaves minecraft's recipes, validation, dynamic sprites, tooltips, hit boxes and output behavior authoritative.

each module config also has an all settings browser, so its toggles, numbers, text and colors can be searched and edited without remembering feature commands.

## what it does

15 constellations, each one handles a different part of the game:

- **andromeda** — the rift. live time, balance, authoritative lifetime/session/rate and low-time HUD; all 73 current Motes NPC prices with automatic four-source McGrubber detection, stack-aware tooltips, movable Rift Storage valuation and particle-validated orb guidance; all 52 named, area-aware, profile-safe Enigma Souls with real Rift Guide synchronization, missing-entry marking and right-click menu routing; complete Crux Talisman progression/bonus and Punchcard Artifact session guidance; complete 2,201-node shortest-path navigation with live Temporal Pillar avoidance; Lava Path, Upside Down and Turbulator routes plus sound-driven Dance Room and mirrored Craft Room guidance; complete Stillgore Blood Effigy lifecycle/timers and Splatter Heart guidance; complete West Village Kloon hacking/color/terminal guidance, persistent vermin tracking/highlighting and the ordered Gunther race route; Dreadfarm Agaricus, Volt, Wilted Berberis respawn-sequence and all 56 wooden-button helpers; Living Cave Metal movement, Defense Blocks, reconstructed Snake states and optional suit progress; Colosseum Blobbercysts, Bacte phases/kill-zone warning and packet-accurate Tentacle health; Mountaintop Sun Gecko, Timite lifecycle/profit, Rose flowerpot and Ubik helpers; Wyld Woods Larva, Odonata and Shy Crux guidance; optional Horsezooka horse hiding
- **apollo** — hud. movable custom SkyBlock scoreboard with live server lines, cached stats, exact SkyBlock date/time, dated lobby IDs, player counts, event calendar, mayor/perk/minister and party groups, searchable ordering/visibility editor, filtering, styling and safe vanilla replacement; independently movable performance, location, movement, vitals and active-effects panels; rolling FPS/ping/TPS statistics, coordinates, clock, facing, yaw/pitch, dimension, actual/stat speed, parsed health/mana/defense/overflow/effective health and sorted potion timers
- **artemis** — hunting + foraging. exact caught, Lootshare and charm shard tracking; complete Fig/Mangrove Tree Gift, drop, XP, Whisper and profit tracking; Bazaar value, profit per hour, profile/session history, high-value alerts and movable hunting/foraging HUDs; profile-safe Attribute Shard goals, tier-to-max ranking, Hunting Box synchronization, completion alerts and complete box valuation; Heart of the Forest perk/currency guidance, Galatea Tree Progress, clean tree view, Forest Nodes, Forest Temple rotations, Lushlilac/Sea Lumies highlights, complete Sweep block/throw prediction and Sweep Details, Moonglade Beacon tuning, Agatha Coupon offer/profit ranking and compact Starlyn contest results; Hideonleaf, Invisibug, Birries, Shellwise and Coralot guidance plus tree-break, phantom and Fusion-machine sound controls; Huntaxe Absorptio confirmation lock; complete Lasso progress and REEL HUD; Fusion materials, value and result guidance plus explicit repeat, confirm and cancel keybinds
- **aquila** — mining. Powder/commission HUDs, a complete Heart of the Mountain menu helper with perk states, exact powder costs, ten-level planning and Sky Mall display, complete 825-node Glacite Tunnel Maps with shortest-path routing, commission selection and Pigeon controls, completed-commission menu highlights and clickable calls, persistent forge slots and reminders, commission destination guidance, live commission and crystal progress, exact Fetchur/Puzzler guidance, complete Deep Caverns route guidance, profile-safe mineshaft pity, cave-in/cold timing and named ordered mining-route creation/import/editing; Glacite corpse finder/key/profit tracking, own Golden/Diamond Goblin guidance, Dwarven ore-carpet highlights, exact Crystal Nucleus barrier volumes, complete Crystal Hollows chest/lock solving with expiry timers, urgency highlights and chained routes, two-trail Wishing Compass solving plus chat/area/manual structure waypoints, Metal Detector solving, exact Pickobulus block/drop preview, drill-fuel and Pickonimbus tool state, complete Fossil Excavator solving plus session/profile loot, Scrap, Dust, Powder and profit tracking, Fossil Muncher answers, full Scatha spawn/cooldown/pet/stat tracking, high-heat sound filtering, nucleus and mining helpers
- **auriga** — experiments + misc. complete advisory Experimentation Table solvers; SkyBlock Guide missing-task/category/one-time and Power Stone guidance; deep Chocolate Factory ranking, state warnings, level text, timers, Stray catch window and production tooltips; profile-safe Hoppity Collection analytics, Rabbit Hitman slot costs, per-event statistics, unclaimed meal-egg schedule, all-island Egglocator waypoints and full graph-routed walking guidance; Anvil matching; God Potion/Cookie status; Basic/Hex Reforge filtering, matched-result protection, candidate guidance and session accounting
- **cassiopeia** — chat. spam filters (60 categories), timestamps, clickable links, mention alerts, compact damage, 60+ shortcuts (/f1-/f7, /h, /i, /dh, /pi, /bz, /ah, etc)
- **cygnus** — events + diana. authoritative mayor/minister/perk/election state, live/cache-backed event calendar, empirical burrows and Diana tracking, complete gift reward/value/profit statistics, profile-safe unique-recipient goals and optional advisory gifting-opportunity highlights, Inquisitor alerts/sharing, seasonal helpers, and perk/coordinate-gated Carnival guidance
- **draco** — crimson isle. kuudra phases, vanquisher alerts, reputation, dojo, ashfang freeze, abiphone, magmafish, trophy tracking
- **hercules** — farming. Garden visitor shopping, prices, sack availability, rewards and safeguards; profile-safe multi-page Visitor Logbook analytics; profile-safe Garden Level and overflow progression; Anita medal-profit ranking and Extra Farming Fortune costs; Pesthunter profit-per-Pest shop analysis; Configure Plots material prices, affordability and cheapest unlock guidance; Farming Toolkit crop icons, Garden rod-break protection and Carrolyn donation guidance; advisory target speed/angles, yaw/pitch, crop rates, per-crop money-per-hour comparison with automatic or manually assigned crop positions, session/profile Rare Crop drop tracking with value, rate and uptime, profile-safe Crop Milestone progress/ETA/goals, live/upcoming Jacob schedules, persistent medal history, time/FF planning, summaries and warnings, per-crop farming-lane distance/switch guidance with automatic or manual locations, full Composter materials/profit/upgrades/timer guidance, specialized hoe levels/overflow, DNA Analyzer solver, true Farming Fortune, crop-specific start waypoints assignable at the current block or explicit coordinates, last-farmed waypoints, reversible farming mouse sensitivity and Garden warp commands/hotkeys; Garden pest spawn alerts, finder borders, cooldown timer, kill/drop/profit statistics, custom plot-name learning, vacuum particle-path waypoints, persistent plot-spray expiry tracking, Configure Plots status highlighting and custom icons, Stereo Harmony vinyl/crop guidance and Greenhouse growth diagnostics
- **hydra** — fishing. complete sea-creature and catch-profit trackers, Trophy Frog/Fish discovery alerts/history/sharing, deliberate Lootshare call key and teammate alerts/history, Fishing Festival counts/summaries/personal bests, max-level pet alerts/history and leveling-profit prices, Thunder/Storm/Hurricane Bottle charge alerts and slot progress, fishing-boss death/team coordination, Nessie destination alerts and cave guidance, exact hook readiness/timing, active bait/count/warnings, per-profile Fishing Bag protection, fishing-armor validation, clickable bait recovery, Chum Bucket recovery alerts, full Golden Fish cooldown/spawn/interaction guidance, Lotus Atoll/Crimson wormhole finding and departure alerts, world labels for fished items, complete own/party rare-drop alerts and sharing, area-aware barn-fishing protection, named hotspot/perk/radius rendering, found/share/gone lifecycle alerts, cubic Hotspot Radar guidance, owned deployable timers, Moby-Duck and Galatea salt state, optional Blizzard timing, expiry protection, item/category/value/rate statistics, trophy-fish fillet values and thunder highlighting
- **lyra** — economy. purse tracking, market tooltips, storage previews, inventory search/buttons, Bazaar order competition/undercut tracking, Auction House comparisons, safeguards and configurable market alerts; complete profile-safe Accessory Bag collection, family-tier tooltips, missing/upgrade browser, price-per-Magical-Power ranking and bag highlights; authenticated public SkyBlock profile viewer with profile switching, paged tabs, progression, collections, minions, mining, Museum, Crimson Isle, Garden, Rift, Fishing, Chocolate Factory, Foraging, lifetime Mob records and saved Loadouts, plus inventory/storage/wardrobe browsing and category-level wealth analysis
- **orion** — dungeons. score hud, secret waypoints, ALL puzzle solvers (terminals, blaze, boulder, ice fill, waterboard, silverfish, tic tac toe, creeper beams, trivia, etc), combat esp (starred mobs, minibosses, livid finder), complete Spirit Mask alerts/cooldown/immunity/HUD/item state, m7 phase tracking, spirit leap, blessings, chest profit, dungeon map
- **pegasus** — party. /rp reparty, party triggers, carry mode, ready checker, friend list hud, marked players
- **perseus** — slayers. boss timer, xp bar, miniboss alerts, bestiary milestones, broodmother, relics, rng meter; complete Vampire Slayer Healing Melon, Steak, Twinclaws/Holy Ice and Mania alerts; own/tagged/co-op Bloodfiend guidance, HP/percentage/countdown information, Blood Ichor and Killer Spring rendering
- **phoenix** — qol. fullbright; real auto sprint with a water policy; instant camera sneak; non-wrapping hotbar scroll; render-only attached-arrow hiding; Enter-to-submit and Shift+Enter line changes in signs; etherwarp overlay; hide lightning/fire/falling blocks; full armor/equipment wardrobe keybinds with page, swap, labels and unequip protection; multi-profile inventory slot bindings with graphical editor, protected shift-click swaps and area switching; profile-safe active-pet display with menu/widget/Autopet synchronization, pet icon/details, XP progress and selected-pet highlighting; profile-safe authoritative collection-menu synchronization with session totals, rates, goals, ETA and freshness; profile-aware Century Cake timer, missing-cake helper and expiry alerts; server world age, clock, phase and transition HUD; automatic screenshot clipboard copying and retry; named farming speed presets with profiles, editor, sign aliases, keybinds, feedback and HUD; full numeric-sign calculator with expressions, functions, magnitudes, purse input, preview and safe submission; auto save reminder; hotbar lock

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
- `/pv [player]` — opens the SkyBlock profile viewer for yourself or another player

also has a bunch of quality of life shortcuts like `/f7` for f7, `/h` for hub, `/bz` for bazaar, `/is` for island, etc. full list in the config.

## config

everything's toggleable. hit right shift for the hub screen, or `/cn config` for the full settings. each constellation has its own section with toggles for every individual feature. generated settings are audited against runtime consumers so dead placeholder toggles are removed instead of pretending to enable something.

hud elements are draggable — open `/cn hud` and move stuff where you want it. positions are saved per-element.

For a readable inventory of the current mod, see `CURRENT_FEATURE_OVERVIEW.md`. For a staged in-game checklist, see `TESTING_GUIDE.md`. verified test jars and the current guide are mirrored to the owner-only constellation shelf on the share server, with current and archived releases kept separately.

the profile viewer calculates exact skill levels and progress from hypixel's published curves, including hunting, the current foraging cap and profile-specific farming/taming caps.
garden crop records use the maintained licensed 46-level milestone tables and exact nine-upgrade copper costs, with validated offline caching.
garden visitor and composter records use all 139 maintained visitor identities and exact 25-level cumulative upgrade costs.
garden plots use the maintained 24-plot layout and tier-aware unlock costs; greenhouse records include exact reward and cumulative material progress.
garden mutation records cover all 41 maintained crops with discovery/analysis state, while all ten chips show exact 25-million-Sowdust progression.
garden summary records include exact level and visitor milestones, crop unlocks/Personal Bests, Jacob perk costs, Larva cap and maintained Barn skins.
mob records preserve every lifetime kill/death API ID with optional numeric-variant grouping, K/D, filtering, search and sorting.
saved Loadouts preserve all 27 template positions, exact armor/equipment links, pets, HOTM/HOTF presets, power stones and tuning slots.
the profile Overview separates every liquid balance and adds Cookie, profile age, co-op, skills, combat, pet, Essence and Maxwell summaries.

the party-message, Smart Sack Refill and slot-binding editors share the same restrained constellation screen language, including bounded content, clear focus states and real scroll position feedback.
inventory buttons, Spirit Leap, Party Guard and carry tracking use the same system while retaining their purpose-built controls and deliberate-click behavior.
speed presets, scoreboard ordering, tunnel routing and dungeon records complete the compact-screen migration with clipped searchable lists and safe deletion controls.
the twenty-page Profile Viewer now uses the same constellation shell, with animated loading, last-good refresh behavior, keyboard navigation and bounded record lists.
terminal practice mirrors Hypixel’s boards while adding configurable artificial ping, live timing, PBs, accuracy history, direct mode commands and optional automatic replay.

vanilla crafting tables have an exact-class constellation visual treatment with independent slot and arrow controls. the recipe book stays vanilla, server styling is off by default, and normal crafting clicks remain untouched.

furnaces, blast furnaces and smokers use the same exact-class treatment with their original flame and progress animations preserved. they have independent slot, indicator-backplate and hypixel controls and never broaden the ordinary chest scope.

brewing stands have their own exact-class apparatus with the original fuel bar, brew timer and bubble animation layered above it. every bottle, ingredient and output action stays vanilla, and hypixel styling remains a separate opt-in.

enchanting tables place minecraft's animated book on a framed constellation stage while leaving every rune offer, level cost, disabled state, hover tooltip and enchant click under vanilla control.

anvils use a purpose-built repair stage and matching rename field while minecraft keeps costs, validation, tooltips, text editing and output collection authoritative. smithing, grindstone and server menus remain outside the exact-class treatment.

grindstones have their own wheel-and-frame stage with minecraft's invalid cross, repair/disenchant result, enchantment removal, experience and output handling left intact.

smithing tables separate the upgrade row from minecraft's live armor preview while retaining cycling ingredient hints, recipe errors, onboarding tooltips, netherite upgrades and armor trims.

its dungeon page includes exact catacombs and class progression, selected class, class average, secrets per run, every floor's completions and all available personal-best time and score records.

the slayer page calculates exact progression for all six slayers, with boss and tier totals, optional raw attempts, reward-claim state and unclaimed reward warnings.

the pet page uses the current licensed neu leveling data, including tier boost, bingo and all three level-200 dragons, with full progression, metadata, sorting and filtering controls.

the bestiary page follows the current licensed neu family catalogue, aggregates every mob alias correctly and exposes levels, kills, deaths, next thresholds, completion, categories and uncatalogued api kills.

the collections page follows hypixel's current official resource, sums co-op contributions correctly and shows personal contribution, exact tiers, next requirements and upcoming unlocks for all six categories.

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
