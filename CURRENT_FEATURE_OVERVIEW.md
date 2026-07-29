# Constellation feature overview

This is the readable map of what Constellation currently contains. It is organized by game area so you can find things without reading the development handoff or hundreds of changelog entries.

## Start here

- Open the main menu with Right Shift or `/cn config`.
- Enable or disable an entire area with `/cn toggle <constellation>`.
- Open the transparent HUD editor with `/cn hud`.
- HUD elements only appear in the editor when they are currently useful or were visible during the last five seconds. Hover an element and scroll to resize it; drag it to move it.
- Most individual features have their own toggle and detailed settings inside their constellation.
- Puzzle, combat, movement, and aiming helpers are advisory overlays. They do not click or aim for you.

## Andromeda: Rift

- Rift time, area and progression displays
- Enigma Soul waypoints with collected-soul hiding
- Mirrorverse guidance and puzzle waypoints
- Effigy status and waypoint support
- Crux Talisman progress and bonuses
- Motes and area information
- West Village and Rift activity helpers

## Apollo: general HUD

- Movable Performance panel with current/average FPS, current/average/median ping and current/average/minimum/maximum server TPS
- Movable Location panel with integer/decimal coordinates, facing, yaw, pitch, dimension and 12/24-hour local clock
- Movable Movement panel with SkyBlock speed stat, actual horizontal blocks per second and signed vertical speed
- Movable Vitals panel using parsed SkyBlock health, mana, overflow mana, defense and effective health
- Movable Active Effects panel with amplifier levels, durations, sorting, row limit and expiry-warning colors
- Independent panel, row, scope, sampling, threshold and color controls through Apollo settings and `/apollohud`
- Movable and resizable shared HUD framework
- Transparent, chrome-free HUD editor

## Artemis: hunting and foraging

- Exact Galatea Fig and Mangrove Tree Progress with axe-only or own-tree visibility
- Compact/full HUD, progress bar, distance, contributor, colors and optional threshold alerts
- Bounded nearest-tree scanning and full `/treeprogress` controls
- Profile-safe Attribute Menu learning for tier, rarity, next-tier requirement and enabled state
- Cheapest next-tier or max-tier ranking using selectable Bazaar price side and Hunting Box deductions
- Hide-maxed, only-locked, current-inventory and unknown-price filters with complete summary totals
- Optional tier slot text, disabled-attribute tinting, contextual tooltips and `/attributeoverlay` controls
- Exact Hunting Box shard-region and `Owned` quantity parsing with both Bazaar value sides
- Per-shard rows, complete totals, partial-price state, sorting and movable Hunting Box Value HUD
- High-value slot overlays, contextual unit/total tooltips and `/huntingboxvalue` controls
- Galatea-scoped phantom and exact Fusion-machine firework sound suppression
- Independent ambient, bite, death, flap, hurt and swoop controls with future phantom-path coverage
- Fusion volume target/tolerance, any-volume mode, session counters and `/galateasounds` controls
- Absorptio confirmation lock for all five current Huntaxes with future-tier lore fallback
- Air/block scopes, confirmation window, continuous/single-use modes and optional sneak bypass
- Actionbar, chat and sound feedback, session counters and full `/huntaxelock` controls
- User-bound repeat, confirm and cancel Fusion controls with exact menu/button validation
- Duplicate/simultaneous-key rejection, click cooldown, feedback, sound and input-consumption controls
- Session action counts and complete `/fusionkeys` controls; no automatic Fusion interaction
- Profile-safe Attribute Shard goals with Direct, Bazaar, Fuse and Cycle source preservation
- Bounded SkyShards/NoFrills clipboard import and a Minecraft Controls hovered-shard selection key
- Exact catch, Lootshare, charm, Fusion, absorption and Hunting Box ownership accounting
- Completion alerts, menu-aware source filters, sorting, remaining value and movable Shard Tracker HUD
- Manual goal, obtained-count, row, price, filter and presentation recovery through `/shardtracker`
- Exact Fusion Box, Shard Fusion and Confirm Fusion input/output parsing
- Required/owned material counts, readiness, missing state and real output quantity
- Separate input replacement cost and output liquidation value with net value
- Fusion result and optional profile-persistent Pure Reptile tracking
- Movable Fusion HUD, configurable readiness alerts and `/fusionhud` controls
- Player-owned Lasso endpoint detection with exact progress-bar and REEL state
- Rarity-aware Abysmal, Vinerip, Entangler and Everstretch progress offsets
- Movable compact/full Lasso HUD with progress, percentage, target, tool and distance
- Configurable ready threshold and local chat, title, sound or timed repeat alerts
- `/lassohud` status, reset, threshold, search range and presentation controls
- Galatea-only Hideonleaf, Invisibug, Birries, Shellwise and Coralot detection
- Independent target toggles, colors and ranges with boxes, labels, beams, lines and distance
- Optional deduplicated local target chat, title and sound alerts
- `/huntingmobs` status, clearing, range, color and presentation controls
- Exact caught-shard, hunting Lootshare and charm result tracking
- Authoritative Bazaar product resolution including all exceptional shard names
- Session-only or profile-persistent totals, mobs, shards, value, hourly rate and uptime
- Recent-pickup, held-tool or always-visible movable Hunting Profit HUD
- Value/amount/name/recent sorting, row count, buy/sell price source and independent HUD rows
- Configurable high-value chat, title and sound warnings
- `/huntingprofit` status, reset, profile clearing, timing, sorting, pricing and option controls

## Aquila: mining

- Commission HUD, progress parsing and destination guidance
- Crystal Hollows crystal progress and waypoint guidance
- Wishing Compass and nucleus-related helpers
- Powder and mining-session tracking
- Persistent Forge slots, completion times and reminders
- Fetchur and Puzzler solutions
- Exact drill-fuel and Pickonimbus metadata/lore parsing with modern and legacy item support
- Movable mining-tool HUD with configurable tool, amount, percentage, text bar, colors and visibility scope
- Drill and Pickonimbus inventory durability bars plus per-item low-state chat, title and sound warnings
- Glacite Mineshaft pity, cave-in and cold state
- Corpse finder, corpse keys and corpse profit tracking
- Fossil and mining-puzzle helpers
- Scatha, treasure and mining-event assistance
- Own Golden and Diamond Goblin box, beam, line, label and distance guidance
- Crystal Hollows high-heat pant sound filtering with exact sound, heat and height checks
- Sea-lantern-validated Dwarven ore-carpet highlights with bounded cache and scan controls
- Crystal Hollows treasure-chest outlines, particle-derived lock spots and per-chest lock progress
- Pickobulus exposed-block outlines and location-specific block, ore, powder and Mineshaft-pity forecasts
- Pickobulus held-ability/cooldown state with independently movable and configurable HUD rows
- Completed-commission slot highlights in the exact Commissions menu
- Customizable user-clicked Mismyla completion calls and obfuscation-validated Fred redial actions
- Mines of Divan keeper-center detection with all 42 licensed Metal Detector chest offsets
- Stable-reading Metal Detector narrowing, fallback search, exact/possible guidance and collection timing

## Auriga: experiments and utility

- Chronomatron sequence memory with next, second and remaining color guidance
- Ultrasequencer numeric sequence memory, step tracking and ordered slot guidance
- Superpairs item memory, known-pair matching, current-match and powerup highlighting
- Optional wrong/early-click protection with a deliberate hold-Control bypass
- Movable experiment type, phase, step, remaining and remembered-item HUD
- Independent solver, scope, tooltip, memory, overlay, label and color controls
- `/experiments` status, reset and complete main option commands
- Exact Chocolate Factory screen helper with live rabbit and coach efficiency ranking
- Best and best-affordable highlights, affordability/payback tooltips and prestige guidance
- Stray and Golden Rabbit alerts, Hitman status and a movable factory HUD
- Profile-aware Time Tower persistence with configurable warning and expiry channels
- Exact Anvil screen state with complete enchanted-book enchant and level comparison
- Optional mismatch warning, sound and output blocking with deliberate Control bypass
- Matching-book discovery in player inventory, configurable slot overlays and tooltips
- Movable Anvil state/input/match HUD and `/anvilhelper` controls
- Authoritative tab-footer God Potion, Cookie Buff and active-effect status
- Profile-safe countdown persistence with independent warning/expiry chat, title and sound
- Movable Buff Status HUD with optional unknown, expired, profile and source-age rows
- Full `/buffstatus` recovery, testing, thresholds and option controls
- Exact Basic Reforge and Hex menu tracking from authoritative item modifier metadata
- Editable wanted/excluded reforge filters with substring matching and exclusion precedence
- Current-reforge overlay, Hex candidate highlights and contextual filter/cost tooltips
- Optional matched-result reroll/application guard with held-Control bypass and local alerts
- Session attempt/spend/last-result statistics, movable HUD and `/reforgehelper` controls
- Calculator functionality is implemented in Lyra

## Cassiopeia: chat

- Large configurable spam-filter set
- Compact repeated and noisy messages
- Chat timestamps
- Clickable links
- Mention alerts
- Compact damage and notification messages
- SkyBlock command shortcuts such as floor, hub, island, Dungeon Hub, Bazaar and Auction House commands
- Coordinate and waypoint chat utilities

## Cygnus: events and Diana

- Profile-safe unique-gift recipient names and authoritative Generow total synchronization
- Movable unique-recipient goal/remaining HUD plus configurable milestone alerts
- Optional advisory not-yet-gifted player boxes and labels with range and name filters
- `/uniquegifts` history, correction, goal, milestone, filter, color and presentation controls
- Profile-safe gift reward rarity, item, coin, skill XP and North Star tracking
- Market value, manually accounted gift replacement cost, profit and session hourly rate
- Holding-gift or recent-gifting-location HUD visibility with configurable reward breakdowns
- Configurable SWEET/SANTA/PARTY alerts plus reward ID and custom-price correction
- `/gifttracker` gift usage, reset, pricing, rows, rare tiers and presentation controls
- Authoritative current mayor, minister, active perk and live election state
- Temporary perk overrides with partial-state-safe Carnival event gating
- Movable Mayor HUD with perk descriptions/filtering, election leader/votes and cache state
- Deduplicated mayor-change chat, title and sound notifications
- `/mayor` status, perk/election lists, refresh, filters, limit, color and display controls
- Live upcoming-event calendar with active/upcoming countdowns and locations
- Offline last-good calendar cache plus deterministic SkyBlock season, day and year
- Five-minute/one-minute reminders with independent chat, title and sound
- Editable include/exclude filters, row count, active/date/year/location and fetch-state controls
- `/events` status, refresh, reminder, filter and presentation commands
- Exact server-particle detection for empirical Start, Mob and Treasure burrows
- Configurable burrow boxes, beams, labels, distance, range, lifetime, colors and through-wall rendering
- Persistent tracking for eleven mythological mobs, sixteen rare drops and dug coins
- Compact or full movable Diana mob and drop HUDs
- Deduplicated Minos Inquisitor title/sound alerts, expiring coordinate waypoint and optional safe party sharing
- `/diana` status, reset, clearing, range, lifetime, color and presentation controls
- Catch a Fish Golden Fish detection using the exact Carnival head texture
- Configurable Golden Fish box, label, beam, sound, range and through-wall rendering
- Zombie Shootout armor-to-weapon color guidance and live lit-lamp target outlines
- Exact Hub/minigame coordinate gating, movable Carnival HUD and manual test modes
- `/carnivalhelper` status, force, range and presentation controls
- Calendar and upcoming-event information
- Mayor and perk information
- Event notifications
- Diana burrow chain and guess guidance
- Inquisitor detection, highlighting and sharing
- Jerry, Spooky, Winter and Harvest helpers
- Event session counters and reminders

## Draco: Crimson Isle and Kuudra

- Kuudra phase and teammate state
- Supply waypoints, pickup/delivery guidance and supply timers
- Ballista build progress and build information
- Stun, DPS and danger timing
- Kuudra splits, completion breakdown and run history
- Kuudra titles, alerts and teammate highlighting
- Vanquisher alerts and sharing
- Reputation and daily-task information
- Ashfang, dojo and miniboss helpers
- Abiphone and Crimson activity assistance
- Magmafish and Trophy Fish information

## Hercules: Garden and farming

- Anita shop profit-per-medal ranking, detailed offer tooltips, best-offer highlighting and remaining Extra Farming Fortune costs
- Profile-safe Visitor Logbook totals, acceptance/denial rates, page completion, queue correction and visitor rankings
- Pesthunter shop profit-per-Pest ranking, complete cost tooltips, filtering and best-offer highlighting
- Configure Plots price breakdown, locked-plot ranking, material ownership, affordability and cheapest unlock guidance
- Profile-specific Garden XP synchronization, level/overflow progress HUD, visitor XP updates and max-level menu details
- Crop money-per-hour comparison using observed or custom BPS, saved true Fortune and live Bazaar/NPC values
- Automatic profit ranking or persistent manual position assignment for each individual crop
- Sell-offer, instant-sell and NPC columns with Bountiful, Mooshroom, seed, replenish and equipped-armor rare-drop calculations
- Session or profile Rare Crop tracking for 21 drops with value, profit rate, active uptime, recent drop and exact chat hiding
- Visitor shopping list with inventory and sack quantities
- Visitor item prices, total cost, copper value and reward profit
- Rare/new visitor refusal safeguards and configurable loss safeguards
- Visitor option highlighting and hold-key bypass
- Farming target speed per crop
- Target yaw/pitch, current angles and tolerance display
- Recent/session blocks per second and crop-session statistics
- Profile-specific Crop Milestone counters, exact menu synchronization, progress/ETA/rates, custom goals, close warnings and milestone-menu details
- Farming Toolkit tool-to-crop icons for all 13 crops, with optional held-crop highlighting and compact labels
- Garden-only fishing-rod block-damage protection with deliberate sneak bypass and local feedback controls
- Carrolyn donation-item recognition, 3,000-item inventory progress, clickable warp and Crimson Isle destination guidance
- Specialized farming-tool level/XP display with overflow levels, XP rate/ETA, upgrade/overclock state, wrong-crop warning and precise sound muting
- Minimum-swap DNA Analyzer solver with validated color boards, manual next-pair highlights, close protection and optional wrong-click safeguards
- Profile-specific per-crop farming lanes with automatic two-layer detection, manual start/end placement, distance/time HUD, endpoint waypoints and switch warnings
- Profile-specific Composter resource/empty-time tracking, material and profit overlay, inventory numbers, upgrade prices/highlights and low-resource warnings
- Authoritative upcoming Jacob contest schedule with current/next/following crops, boosted-crop correction, countdowns, persistence and crop-filtered warnings
- Profile-specific Jacob contest medal history with last-N bracket averages, time/FF planning, hovered-record details, harvest summaries and Personal Best Fortune gains
- Profile-specific crop start and last-farmed waypoints with current-block or explicit-coordinate per-crop placement and configurable world rendering
- Farming mouse lock and percentage sensitivity reduction with manual commands, keybind, tool auto-modes, ground/plot checks and teleport release
- Garden-only home, Barn and named-plot command shortcuts with configurable no-GUI hotkeys
- Jacob contest crop, collection rate and projected total
- Garden pest spawn title/chat/sound controls
- Pest total and infested-plot HUD
- Accurate plot borders and labels, including learned custom plot names
- Pest cooldown, last-spawn and average-spawn HUD
- Vacuum particle-path pest waypoint with optional box, beam, line, label, distance and particle filtering
- Persistent per-plot Sprayonator type/expiry state, optional HUD, Portable Washer clearing and expiry/away alerts
- Configure Plots status highlighting with priority, colors, letters, pest counts, spray minutes and hover details
- Profile-specific Configure Plots icon editor with exact custom-item rendering, original tooltips and set/reset modes
- Stereo Harmony active-vinyl HUD, carried-vacuum detection, crop-icon menu replacement and Jacob-contest matching
- Greenhouse growth-cycle countdown/overdue HUD, ready/away alerts and diagnostic harvest/water highlights
- True universal/crop Farming Fortune HUD, saved crop values, missing-widget guidance, pest reductions and Pesthunter bonus expiry
- Cooldown warnings and configurable custom cooldown
- Persistent per-pest kills and per-drop quantities
- Session and lifetime pest profit with delayed market-price reconciliation

## Hydra: fishing

- Sea-creature catch counts, rarity breakdown, rates and session history
- Rare sea-creature alerts and party sharing
- Fishing profit tracker with item values and rare-drop warnings
- Complete fishing rare-drop catalog and party drop detection
- Trophy Frog and Trophy Fish discovery alerts, history and sharing
- Fishing Festival tracking, summaries and personal bests
- Hook readiness, timing and bobber information
- Bait type/count, low-bait and empty-bait warnings
- Fishing Bag state protection and clickable recovery
- Fishing armor validation
- Chum Bucket recovery alerts
- Golden Fish cooldown, spawn, entity and interaction guidance
- Fishing hotspot circles, perk labels, sharing and disappearance alerts
- Hotspot Radar advisory trajectory guidance
- Lotus Atoll and Crimson wormhole destination guidance
- Nessie destination alerts and cave guidance
- Barn-fishing entity counts, stack timers and cap warnings
- Owned deployable timers and expiry alerts
- Moby-Duck, salt and optional Blizzard consumable timing
- Fishing boss death and teammate-wait coordination
- Thunder, Storm and Hurricane Bottle progress and charge alerts
- Max-level pet alerts and fishing-pet leveling-profit comparison
- Deliberate Lootshare party key and incoming Lootshare alerts/history
- Labels for fished item entities

## Lyra: economy, storage and inventory

- Purse and currency tracking
- Bazaar and Auction House pricing
- Item-price and value tooltips
- Auction comparison and purchase safeguards
- Bazaar order information
- Storage, backpack and container previews
- Container total-value calculation
- Inventory search and configurable inventory buttons
- Slot/item protection with explicit multi-click overrides where configured
- Museum, salvage, NPC trade, auction and drop protections

## Orion: dungeons

- Dungeon room recognition from the bundled 138-room data set
- Dungeon map, room names, secrets, doors and checkmarks
- Secret waypoints and route rendering
- Online/custom route tools and route recording
- Score, secrets, crypts, deaths, mimic, Prince and puzzle status
- S/S+ reach timing and configurable score alerts
- Blood/Watcher timing and advisory Blood Camp prediction
- Starred mob, miniboss, teammate and special-entity highlighting
- Livid identity and invulnerability timing
- Spirit Bear, Spirit Bow, Bonzo Mask, Spirit Mask and Phoenix state
- Dungeon Breaker charges, blessings, milestones and class information
- Chest profit, Croesus state and run statistics
- Spirit Leap GUI, leap counters and party position information
- Goldor terminal sequencing, terminal progress and terminal timing
- M7 dragon spawn order, priority, health, hit counts, stack guidance and relic timing
- Boss tick timers, Terracotta timing and Spring Boots trajectory guidance
- Party Finder overlay, party checks, queue/requeue assistance and party alerts
- Advisory puzzle helpers for Blaze, Boulder, Creeper Beams, Ice Fill, Silverfish, Teleport Maze, Three Weirdos, Tic Tac Toe, Trivia, Water Board, Arrow Align, Lights On, Simon Says and terminals
- Puzzle solvers are room/phase gated and do not click for you

## Pegasus: parties and carries

- Reparty and party-management commands
- Party command handling and whitelist controls
- Party Finder tools and player checks
- Ready checking and party alerts
- Carry creation, run price, payment and progress tracking
- Dungeon, Slayer and Kuudra carry support
- Marked-player and teammate information
- Customizable party-message system with variables
- Remote Party Finder/webhook-related infrastructure where configured

## Perseus: slayers

- Slayer boss, miniboss and special-mechanic detection
- Boss timers, health and progress HUDs
- Slayer XP and session statistics
- Persistent drop and completion statistics
- Rare-drop alerts
- Enderman beacon, glyph, Nukekubi and laser guidance
- Blaze attunement and special-state guidance
- Vampire effigy, Mania, Holy Ice, Healing Melon and Steak Stake indicators
- Cocoon timing and alerts
- Slayer carry integration

## Phoenix: general quality of life

- Fullbright and fog/overlay controls
- Auto sprint and instant/legacy sneak options
- Etherwarp target overlay
- Armor and equipment wardrobe keys with hotbar, number-row or custom slot mapping
- Wardrobe page, open, unequip and two-slot swap keys
- Wardrobe slot labels, equipped-set protection, cooldown, sound and invalid-key controls
- Multi-target hotbar-to-inventory slot bindings with remembered targets
- Protected shift-click swaps, bound-slot drop/move guards and optional matching hotbar keys
- Global or area-specific binding profiles with automatic selection and clipboard sharing
- Graphical slot-binding editor with per-binding colors, borders, lines and live binding previews
- Profile-aware Century Cake buff timer with a movable active, warning, expired or unknown HUD
- All 16 Century Cake effects recognized with a five-minute missing-cake eating helper
- Configurable cake duration, warning threshold, chat/title/sound alerts, colors and display rules
- Server-authoritative World Age HUD with day number, Minecraft time and day/night phase
- Optional next-transition countdown, real elapsed age, raw ticks, 12-hour clock and independent colors
- Automatic screenshot-to-image-clipboard copying while preserving Minecraft's normal saved file
- Optional last-screenshot retention, manual clipboard retry, bounded busy-clipboard retries and feedback controls
- Slot locking and item protection integration
- Auto-save reminder
- Sign calculator and input helpers
- Hotbar and inventory safety
- Optional hiding of lightning, fire, falling blocks and selected visual clutter

## Shared systems

- One configuration screen for all constellations
- Searchable/sortable customizable party-message editor
- Per-message variables and variant messages
- Persistent HUD positions and scales
- Shared world boxes, highlights, lines, beams and labels
- Shared price provider using Auction House, Bazaar and NPC fallbacks
- Profile/location/dungeon lifecycle tracking
- Diagnostic scraping with `/cn scrape <mode>`
- Build-time room-data and parsing tests

## Known next work

- Remaining Garden progression depth
- Additional non-dungeon gaps from the active 26.1.2 instance

The long engineering history and exact source paths remain in `CODEX_HANDOFF.md`. You do not need that document for normal testing.

## Artemis: Moonglade Beacon

- Exact `Tune Frequency` and `Upgrade Signal Strength` menu gating on Galatea
- Separate normal and enchanted color, speed and pitch states during upgrades
- Filtered moving-pane interval measurement and exact bass-note packet pitch detection
- Signed click offsets, correct-setting slot highlights and a movable menu-only HUD
- Optional user-initiated middle-click conversion and over-click protection with Control bypass
- Stereo Pants interference warning and optional ready chat, title and sound alerts
- Full `/moongladebeacon` status, option, tolerance and sample controls

## Artemis: Heart of the Forest

- Exact `Heart of the Forest` title gating and all 29 current perk names and level caps
- Enabled, disabled and locked perk highlighting with independent saved colors
- Perk levels and unused Tokens of the Forest rendered directly on their menu slots
- Exact SkyHanni cost curves for allocated/max Whispers and next-ten-level calculations
- Number, percentage or combined spent design plus current-balance/next-level guidance
- Shift-held ten-level tooltip projection bounded by each perk's actual maximum
- Menu-only movable HUD for Whispers, tokens, allocated cost and perk completion
- Full `/hotfhelper` status, design and presentation controls

## Artemis: Foraging Tracker

- Transaction-scoped Tree Gift parsing from the exact open/close separators
- Independent Fig/Mangrove contributions and summed fractional whole-tree credit
- Hover-derived actual items, Foraging XP, HOTF XP and Forest Whispers
- Inventory-delta coverage for Fig/Mangrove logs, enchanted logs and Stretching Sticks
- Compact replacement messages with independent uncommon, book, mob, booster, shard, rune and miscellaneous categories
- Session-only live default plus optional profile persistence and profile clearing
- All/Fig/Mangrove filters, row/sort controls and purchase or liquidation pricing
- Profit, profit/hour, recent drops, uptime and configurable confirmed-value alerts
- Axe-only visibility with a saved disappearance delay and movable HUD
- Full `/foragingtracker` status, reset, warning, filter and presentation controls
## Starlyn contests and Agatha Coupon profit

- Exact Agatha Shop offer detection reads sale items and their own material/coupon Cost lore.
- Input and output values have independent purchase/sell price sources, with a manual Agatha Coupon override.
- Offers can be ranked by coupon profit, total profit, cost or name and filtered for losses or incomplete prices.
- Configurable slot colors, compact profit labels, detailed tooltips and a movable HUD expose every calculation.
- Optional compact contest-result and personal-best messages retain the useful bracket, score, previous-best, collection and Sweep details.
- `/starlynhelper` changes rows, sorting, price sources, coupon price and all display/message options.
## Galatea exploration

- Forest Nodes require the exact happy-villager particle and three nearby string item displays before rendering.
- Node boxes, labels, beams, player lines, distance and expiry behavior are independently configurable.
- Forest Temple validates all sixteen wall and floor tiles and shows the shortest left/right rotation for each unsolved tile.
- Lushlilacs and Sea Lumies are learned incrementally from loaded chunks and authoritative block updates.
- Sea Lumies minimum cluster size, resource boxes, labels, beams, distances, colors, range and through-wall behavior are saved.
- `/galateahelper` exposes status, cache reset, range, chunk radius, minimum Lumies count and every main option.
## Sweep guidance

- All eight supported SkyBlock axes are identified by exact item ID before any world prediction appears.
- Current-target prediction uses the licensed Sweep/toughness approximation and bounded 26-neighbor connected-log traversal.
- Throwable axes receive a separate configurable ray, color, one-second cooldown gate and half-log calculation.
- Galatea, Hub and general foraging log rules remain distinct, with the licensed Fig and Mangrove toughness values.
- Sweep Details tracks tree type, toughness, initial/final Sweep, resulting logs, throw penalty, style penalty and correct-style hint.
- The movable HUD and optional compact chat summary preserve multiple penalties from the same calculation.
- `/sweephelper` controls prediction, thrown range, cap, visibility duration, HUD rows and presentation independently.
## Tree cleanup

- Galatea clean view hides only exact decorative block displays, never real world blocks.
- Stripped spruce wood, mangrove wood, mangrove leaves and azalea leaves have independent saved toggles.
- Tree-breaking audio recognizes only the exact `entity.creaking.death` sound.
- Galatea and non-Galatea Hypixel sound scopes are independent; live-profile defaults keep Galatea audible and mute elsewhere.
- `/treecleanup` exposes all settings plus current hidden-display and muted-sound diagnostics.
