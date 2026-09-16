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

- FPS, ping and TPS displays
- Coordinates, facing and real-world clock
- Health, mana, defense and effective-health information
- Speed and movement information
- Potion/effect timers
- Movable and resizable shared HUD framework
- Transparent, chrome-free HUD editor

## Aquila: mining

- Commission HUD, progress parsing and destination guidance
- Crystal Hollows crystal progress and waypoint guidance
- Wishing Compass and nucleus-related helpers
- Powder and mining-session tracking
- Persistent Forge slots, completion times and reminders
- Fetchur and Puzzler solutions
- Drill fuel, Pickonimbus and mining-tool information
- Glacite Mineshaft pity, cave-in and cold state
- Corpse finder, corpse keys and corpse profit tracking
- Fossil and mining-puzzle helpers
- Scatha, treasure and mining-event assistance

## Auriga: experiments and utility

- Ultrasequencer helper
- Superpairs memory helper
- Chronomatron/experiment support where exposed by the current table
- Chocolate Factory information and helpers
- Reforge and anvil assistance
- God Potion timing
- Item/stack calculation commands

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

- Visitor shopping list with inventory and sack quantities
- Visitor item prices, total cost, copper value and reward profit
- Rare/new visitor refusal safeguards and configurable loss safeguards
- Visitor option highlighting and hold-key bypass
- Farming target speed per crop
- Target yaw/pitch, current angles and tolerance display
- Recent/session blocks per second and crop-session statistics
- Jacob contest crop, collection rate and projected total
- Garden pest spawn title/chat/sound controls
- Pest total and infested-plot HUD
- Accurate plot borders and labels, including learned custom plot names
- Pest cooldown, last-spawn and average-spawn HUD
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
- Wardrobe/loadout keybind support
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

- Garden pest particle trajectory waypoint
- Garden spray expiry/plot display
- Stereo Harmony crop and vinyl display
- Remaining Garden progression depth
- Additional non-dungeon gaps from the active 26.1.2 instance

The long engineering history and exact source paths remain in `CODEX_HANDOFF.md`. You do not need that document for normal testing.
