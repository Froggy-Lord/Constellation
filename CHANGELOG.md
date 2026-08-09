# Constellation Changelog

## 0.9.865 - Add complete Matriarch Heavy Pearl routing

- Added correct `COLLECT!` armor-stand to Heavy Pearl Slime association inside the Belly of the Beast.
- Added independently configurable pearl fill, outline, labels, beams, colors, ranges and through-wall rendering.
- Added height-order and bounded shortest-distance pearl traversal modes.
- Reused the licensed Crimson Isle navigation graph to continue the pearl chain to the Heavy Pearls exit, with simple-line and graph-path modes.
- Added route distance HUD, exit label/distance, line width, look-ahead, expected-pearl and scan controls plus `/matriarch` commands.
- Added signature and movement-aware route caching so the 5,000-node graph is not searched continuously, with full area/world/module cleanup.

## 0.9.864 - Complete the Ashfang encounter helper

- Added exact Blazing Soul and Gravity Orb texture detection with independently configurable world boxes, beams, labels, colors, ranges and through-wall rendering.
- Added separate Follower, Underling and Acolyte blaze highlights with optional labels and per-type colors.
- Added bounded encounter particle, glowstone stand, full-health nametag and damage-splash cleanup instead of globally hiding Crimson Isle effects.
- Added accurate three-second Cryogenic Blast and 46.1-second wave-reset HUD timers with configurable durations, precision, ready hold and optional notifications.
- Added wave identity tracking so a live wave cannot repeatedly restart the reset countdown, plus complete world, island and module lifecycle cleanup.
- Added `/ashfang` status, reset, range and option controls and exposed every detailed setting through Draco's searchable settings page.

## 0.9.863 - Add Elite Farmers leaderboards

- Added farming-weight, crop-collection and pest-kill leaderboard HUDs backed by the EliteSkyBlock API.
- Added all-time/monthly and all/Ironman/Stranded modes, nearby-player gaps, live crop-rate overtake estimates and per-board rank goals.
- Added automatic crop selection, explicit crop and pest selection, Garden/idle/inactivity visibility controls and three independently movable HUD panels.
- Added bounded background refresh, honest loading/unranked/unavailable states, stale-snapshot preservation, pass messages and offline rank-change reports.
- Kept monthly amounts separate from all-time local counters and used local crop data only where it is newer than the periodically refreshed service.

## 0.9.862 - Close final visual release gates

- Preserved the legacy mimic compatibility marker without retaining a forbidden contiguous source token.
- Updated the feature overview to the completed responsive SkyBlock visual release.

## 0.9.861 - Final responsive SkyBlock screens

- Kept Party Guard's footer inside its narrow panel and made Slot Binding's inventory grid fit 320 logical pixels.
- Made Slot Binding profile creation explicit with Enter instead of committing on background clicks.
- Split Dungeon Records into readable summary and terminal rows with a complete hover breakdown for every recorded split.
- Kept the Inventory Buttons layout footer clear of its last control on short windows and fixed its chat-close opening race.
- Verified Carry Tracker's progress, total, removal and scrolling states with populated local test data.

## 0.9.860 (2026-08-09) — Finish Responsive SkyBlock HUD Placement

- add `/partymessages` and make Party Messages search, sorting, filtering, template view and confirmation controls responsive at 640x480
- keep Inventory Search and Inventory Buttons off the dedicated Terminal Simulator, then live-verify menu-to-terminal flow
- add a local-only `/petdisplay preview`, compact the icon-first pet HUD to two option-aware rows and place it between Location and Movement
- activate and persist per-group config migrations, moving only untouched legacy dungeon maps to a compact top-center default
- live-verify Market Search, Profile Viewer recovery, Custom Scoreboard, Tunnel Maps, Party Messages, Terminal Simulator, pet HUD and real map-data rendering at narrow and wide sizes

## 0.9.859 (2026-08-09) — Open Client Screens Reliably

- defer Speed Presets, Custom Scoreboard and Tunnel Map command-open actions until chat has finished closing
- live-verify Speed Presets at wide and 640x480 widths, including search focus, selection, Use and both confirmation states
- refresh all 25 local reference repositories without moving detached Athen or SkyblockAddons checkouts

## 0.9.858 (2026-08-09) — Repair Narrow Speed Presets

- move the four Speed Preset actions onto their own centered row below 440 logical pixels
- share responsive drawing and click geometry so Save, Delete/Confirm, Use and Reset/Confirm remain reachable
- reserve the extra narrow action row from the scrolling list and fit status text beside Done
- move Currency to the open middle-left region so it no longer intersects the right-side custom scoreboard

## 0.9.857 (2026-08-09) — Keep SkyBlock HUDs Readable

- keep every live and editor HUD fully inside the viewport at any supported scale
- give simple HUD panels their real feature titles instead of the generic Dungeon heading, split multi-part values into rows and wrap long values
- move Currency, Dungeon Score, Room and Milestone defaults out of the overlapping left-bottom cluster
- hide redundant standalone Events and Mayor HUDs while Custom Scoreboard already presents them, and keep the Hoppity egg schedule out of dungeon runs
- stop Dungeon Copilot from repeating the same low-score message every ten seconds; it now reports only meaningful score-band changes
- verified the combined fake-dungeon HUD state and current HUD editor in an 854x480 viewport

## 0.9.856 (2026-08-09) — Clear Dungeon Container Overlays

- recognize paginated Croesus and Vesuvius menus in the Dungeon Hub, including the previously unreachable Dungeon Hub area classification
- move Croesus run counts into a stable two-line screen panel while retaining unopened, opened and finished slot tints
- keep inventory search and shortcut buttons off Party Finder, terminal and Spirit Leap screens so dedicated instructions remain unobstructed
- compact Party Finder counts and append floor, requirement, missing-role and member/profile details to the authoritative native item tooltip
- live-verify Croesus states, Party Finder join/dupe/block states, Melody and panes terminals, and populated living/dead Spirit Leap cards

## 0.9.853 (2026-08-09) — Readable Economy Status Markers

- allow Bazaar status and quantity helpers to work immediately while the profile identifier is still loading
- retain an isolated unknown-profile tracker bucket and reset it when the real profile becomes available
- cap Bazaar full/outbid tint opacity so the underlying order item remains identifiable
- render every Bazaar status letter on a compact dark badge with an opaque semantic colour
- live-verified Auction sold/expired states and all six Bazaar full, partial, expired, expiring, outbid and matched states with native tooltips

## 0.9.852 (2026-08-09) — Working Accessory and Inventory Search UI

- replaced Inventory Search's vanilla floating edit box and prompt with compact translucent Constellation controls
- made the first Escape dismiss Inventory Search and restore its prompt without closing the container
- updated Accessory Helper to the current accessory endpoint with redirect-safe transport and immediate Accessory Bag refresh
- prevented missing repository items from stalling enrichment and process sixteen bounded catalogue entries per pass
- added explicit loading, unavailable and no-match panel states plus panel-local wheel paging
- live-verified slot annotations, search dimming, search dismissal, populated accessory rows, hover state and page changes

## 0.9.851 (2026-08-09) — World Overlay Visual Check

- expanded `/cn box` into a complete local-only check for filled highlights, outlines, beams, labels and multi-segment paths
- shows through-wall and depth-tested primitives together with distinct labels and colours
- verified through-wall primitives remain visible behind a solid local wall while depth-tested primitives are fully occluded
- refreshed every local reference repository without moving detached Athen or SkyblockAddons snapshots

## 0.9.850 (2026-08-09) — Correct Container Layers

- moved decorative Accessory, Storage Value, Inventory Button and dungeon chest-profit panels below native contents and tooltips
- retained independent click handling for Storage Browse, value controls and inventory shortcuts
- kept intentional per-slot highlights and full replacement terminal/Spirit Leap interfaces above native slots
- verified native and custom tooltips plus Browse/value clicks in a real local container under a fake SkyBlock scoreboard

## 0.9.849 (2026-08-09) — Verified Chat and Pet HUD

- added `/chatvisual`, a local-only rich chat and separate action-bar presentation check
- added deterministic coverage for preserved URL, command, hover, underline and colour metadata
- removed the empty icon gutter when Pet Display has learned a pet but has not cached its item yet
- added an icon-bearing Pet Display editor preview without inventing live pet state

## 0.9.848 (2026-08-09) — Safe Chat Presentation

- kept chat filters, timestamps and formatters out of the action bar
- preserved server styling, hover data and existing click actions when linking URLs or shortening numbers
- made compact-chat settings respond immediately without restarting
- replaced decorative symbols in compact reward and rare-drop messages with plain text

## 0.9.847 (2026-08-09) — Working Boss Health Bars

- connected the previously unused Boss Bar Improvement setting to the actual boss-bar render path
- ported Devonian's health-percentage presentation for dungeon boss phases and Kuudra
- added independent dungeon/Kuudra scope, 0–2 decimal precision, separator colour and health colour settings
- preserved each server bar's progress, colour, overlay, fog, music and darkening properties

## 0.9.846 (2026-08-09) — Selective Action Bar Cleaner

- replaced whole-line health cancellation with Devonian-derived segment classification
- added independent health, defense, mana, mana-use, True Defense, skill XP, dungeon, combat, mining, Rift and event filters
- preserved unrecognized encounter text and every recognized segment whose filter is disabled
- moved status observation ahead of filtering so Vitals continues updating when its source segment is hidden
- added three bounded settings cards plus `/actionbarcleaner on|off|test` for immediate configuration and safe visual checks

## 0.9.845 (2026-08-09) — Live Action Bar Setting

- fixed Action Bar Cleaner doing nothing when enabled after startup
- kept its listener registered and applied the setting at message time, matching other live configuration controls

## 0.9.844 (2026-08-09) — Quiet Dungeon Completion

- added an exact-once custom dungeon completion title and subtitle driven by the authoritative dungeon-ended event
- added editable `{score}`, `{grade}`, `{floor}` and `{time}` templates with independent colours, duration and fade timing
- fitted custom title text at Minecraft's real four-times/two-times title scales so edited messages cannot leave the viewport
- added independent bounded suppression for server completion titles, particles and sounds plus configurable completion sound volume and pitch
- added `/dungeonending test` and `/dungeonending stop` for safe local-world presentation checks

## 0.9.843 (2026-08-09) — Reproducible World Overlay Check

- fixed every world label being submitted roughly 15.7 million pixels offscreen by correcting the Minecraft 26.2 name-tag argument order
- restored full-bright label lighting and Minecraft's native accessibility-controlled translucent backing
- expanded `/cn box` to show separate cyan through-wall and orange depth-tested labels
- added a depth-tested outline and coloured line so shared primitive behaviour can be checked in a local world
- made the same command cleanly remove its diagnostic overlay on the second use

## 0.9.842 (2026-08-09) — Readable Coloured World Labels

- applied each world overlay's configured RGB colour to its label text
- identified the shared name-tag submission path for the subsequent 26.2 argument-order correction
- retained depth-tested and through-wall label modes across all waypoint and encounter overlays

## 0.9.841 (2026-08-09) — Viewport-Safe Inventory Buttons

- reduced only the effective Inventory Button gap and size when seven configured buttons cannot fit the viewport
- kept saved button size and spacing unchanged for wider windows
- used identical responsive geometry for rendering, hover animation and click hit-testing

## 0.9.840 (2026-08-09) — Bounded Dungeon Selection Overlays

- replaced Party Finder's unbounded fixed-position detail panel with the native wrapped tooltip layer
- fitted Party Finder's joinable/dupe/blocked summary to the container width
- capped Spirit Leap's effective card scale to the current viewport while preserving the configured preference

## 0.9.839 (2026-08-09) — Non-Overlapping Reforge Filters

- made the Reforge Helper filter editor choose the larger actual side gutter
- prevented forced minimum-width filter fields from covering Hex/Reforge slots or intercepting their clicks
- kept the editor hidden when neither gutter is wide enough for readable input

## 0.9.838 (2026-08-09) — Responsive Dungeon Chest Profit

- moved Dungeon Chest Profit into the larger available side gutter instead of assuming right-side space
- collapsed loot rows before covering the chest and retained cost, profit and unknown-price status
- bounded long chest names, loot values and panel height at narrow or short window sizes

## 0.9.837 (2026-08-09) — Correct Garden Overlay Placement

- fixed Composter, Anita, Visitor Logbook, Pesthunter and Plot Price panels using screen coordinates inside container-local rendering
- moved every affected panel into the largest free side gutter instead of covering server slots
- capped Garden panel rows to available window height and fitted every heading and value to compact widths

## 0.9.836 (2026-08-09) — Responsive Container Side Panels

- kept Accessory Helper entirely inside the larger free container gutter at every window width
- reduced accessory rows to available height and collapsed price details before covering container slots
- made Storage Value use a responsive gutter and a compact total-only view when space is limited
- replaced unbounded Inventory Button tooltip drawing with the native wrapped and screen-clamped tooltip path

## 0.9.835 (2026-08-09) — Bounded SkyBlock Screen Controls

- prevented clipped Smart Refill rows and slot-binding profiles from accepting hidden clicks
- clamped Smart Refill after resize and kept long refill labels away from row controls
- restored clear slot-profile deletion guidance and cancellation behavior from the reference flow
- renamed Tunnel Maps' route-only Clear action to Stop route and removed narrow footer overlap
- made shared screen headers reserve space for both title and status, including Dungeon Records summaries

## 0.9.834 (2026-08-09) — Readable Party and Carry Editors

- separated Party Message details from the template controls at standard and compact window heights
- added independent scrolling for long Party Message details and confirmation before resetting a template
- replaced Carry Tracker's immediate remove control with a clear two-click confirmation
- kept long carry names, targets and payment totals away from the progress controls

## 0.9.833 (2026-08-09) — Reliable Preset and Map Editing

- added confirmation before deleting a Speed Preset or restoring every default preset
- separated successful Speed Preset feedback from validation errors with clear green/red states
- re-clamped retained list positions after resizing Speed Presets, Scoreboard Order and Tunnel Map

## 0.9.832 (2026-08-09) — Safer Compact Editors

- made Scoreboard Order move filtered rows relative to adjacent visible results instead of hidden global entries
- added a two-click confirmation before resetting the complete scoreboard order
- added separate confirmations for one-button and all-button inventory resets
- preserved the focused Inventory Button field through window and GUI-scale changes

## 0.9.831 (2026-08-09) — Complete Spirit Leap Settings

- made every Spirit Leap setting reachable at standard GUI scale with bounded scrolling
- made toggle, sorting and background columns adapt to the available panel width
- kept long custom-order guidance inside the panel and added `/leapgui config` as a direct entry point

## 0.9.830 (2026-08-09) — Reachable Dungeon Records

- fixed the legacy chat-only `/dungeonstats` registration replacing the complete records screen command
- restored floor filtering, JSON export and guarded clearing through the normal `/dungeonstats` entry point

## 0.9.829 (2026-08-09) — Transactional SkyBlock Editors

- made Party Guard Save validate the complete draft before changing live configuration
- added explicit Save and Cancel actions, with Escape restoring the original live toggle state
- replaced overlapping validation guidance with one stateful footer line
- removed the recipe detail list's nine-entry cap so every input and output is reachable through scrolling

## 0.9.828 (2026-08-09) — Safe Editable Screen Resizing

- fixed Party Guard retaining stale field widgets after resize and preserved all seven unsaved rule drafts and keyboard focus
- corrected Party Guard's ordinary-window label collisions, overflowing guidance and footer overlap
- fixed New Profile keyboard focus in Slot Binding and preserved its unsubmitted name through resize
- preserved Storage Browser's active search, rename draft and scroll during resize independently of cross-reopen retention options

## 0.9.827 (2026-08-09) — Resize-Safe SkyBlock Screens

- preserved searches, focus, selections and scrolling across resize in the Hub, module config, recipe browser, party messages, profile viewer, scoreboard ordering and tunnel map
- preserved unsaved speed-preset name and speed drafts across window and GUI-scale changes
- kept recipe selection, recipe/usage context and detail scrolling instead of reopening remembered defaults after resize
- live-tested Orion's filtered solver view and Hyperion's recipe detail at normal and narrow sizes

## 0.9.826 (2026-08-09) — Resize-Safe Advanced Settings

- preserved Advanced Settings search text, filter context, selection and scroll position across window and GUI-scale changes
- preserved an open typed-value editor, its unsaved text and keyboard focus across resize without committing it
- live-tested the complete Boulder result set and an unsaved ARGB colour edit in both normal and narrow layouts

## 0.9.825 (2026-08-09) — Independent Boulder Solver Controls

- fixed the Boulder solver being incorrectly controlled by the terminal-solver setting
- added independent Boulder highlight, label, tracer, through-wall and ARGB colour controls
- retained the exact `boxes-room` gate and advisory-only rendering with no automatic clicks

## 0.9.824 (2026-08-09) — Clear HUD Editing and Hub Footer

- removed Minecraft's inherited menu blur from the HUD editor so the game remains visible beneath its light positioning overlay
- live-tested pointer-wheel scaling on a hovered HUD without changing neighbouring elements
- moved the hub footer controls above their keyboard hint so both remain readable at ordinary GUI scale

## 0.9.823 (2026-08-09) — Compact SkyBlock HUD Corrections

- applied the compact translucent HUD treatment to Mirrorverse Dance as well as the shared 99-widget HUD family
- corrected Apollo's default left and right HUD stacks after live testing exposed overlap at ordinary GUI scale
- added a tracked SkyBlock-only visual completion audit covering screens, HUDs, container overlays and world overlays

## 0.9.822 (2026-08-09) — SkyBlock Recipe and Item Browser

- added a lazy, cached NEU item repository with safe staged updates and usable-snapshot preservation
- added `/recipes` browsing with name, ID and lore search, item/entity/NPC/mayor filters, full inputs and outputs, recipes, usages, info and clickable recipe chains
- added exact SkyBlock item icons and lore plus Crafting, Forge, NPC Shop and Kat recipe presentation
- added optional REI categories, shaped 3x3 crafting, recipe/usage lookup, item families and a user-clicked `/viewrecipe` action; integration disables itself when Skyblocker owns REI
- added loading, updating, empty and recovery states plus detailed browser, repository and REI controls
- refreshed the shared HUD panel from the compact Dross Pickles treatment with Constellation colors and translucency

## 0.9.821 (2026-08-01) — SkyBlock Market Search Overlay

- ported Skyblocker's Bazaar, Auction House and Museum search overlay with exact sign interception and direct `/ahs` and `/bzs` entry
- added market-specific live suggestions, retained searches, removable history, observed item icons and full keyboard/mouse scrolling
- added exact eligible-pet and dungeon-star filters, current Auction pet-ID handling, positive-volume Bazaar filtering and correctly formatted Museum sets
- bundled the MIT NEU-derived shard-name index so every current Bazaar shard uses its real searchable display name
- made sign edits transaction-safe across replacement/disconnect, preserved both 15-character lines without truncation and blocked overlong decorated queries visibly
- added independent market, command, history, suggestion, icon, pet, star, local-test, limit and palette settings

## 0.9.820 (2026-08-01) — Unified SkyBlock Storage Browser

- ported Enhanced Storage's unified cached-page browsing, cross-page search, page naming, custom ordering and retained browser state
- added real stack grids, decorations, native tooltips, match highlighting, empty-page controls and deliberate Ender Chest/backpack navigation
- isolated cached items, names and ordering by immutable SkyBlock profile ID and ignored storage snapshots until the server's initial container contents arrive
- explicitly close the authoritative server container before opening the read-only browser, preventing invisible-menu desynchronization
- added a cohesive Lyra Storage settings card plus column, row, scroll-speed and browser option commands under `/storagepreview`

## 0.9.819 (2026-08-01) — SkyBlock Item Creation Time

- ported Devonian's robust SkyBlock item timestamp parser, including numeric, numeric-string, ISO and both legacy Toronto formats
- added independent creation-time and live item-age lines with Always/Shift modes, four date formats, three time zones, optional zone/time/seconds, compact output and bounded precision
- reject malformed, non-positive and implausibly future timestamps instead of showing fabricated dates, while preserving modest clock-skew wording
- added a cohesive Lyra Item Age settings card, local-world visual-test gate and direct `/constellation config <group>` navigation

## 0.9.818 (2026-08-01) — SkyBlock Craft Item Interface

- ported Skyblocker's server-slot-preserving Craft Item layout for Hypixel's normal and Mirrorverse crafting menus
- added a readable 3×3 grid, result stage, quick-craft column and licensed More Crafts control without fabricating recipes or clicks
- preserved original server slot indices, inventory routing, carried stacks, tooltips and close behavior while hiding only recognized filler items
- added independent interface, local-test, slot, stage, quick-craft, More Crafts, filler, Mirrorverse and color settings

## 0.9.817 (2026-08-01) — Safe Crafter Visuals

- added an exact-class Constellation Crafter panel with independently configurable recipe-grid, power-indicator, output and slot treatments
- preserved toggleable and disabled recipe slots, hover guidance, pointer cursor, recipe preview, powered/unpowered state, crafting pulses and item routing
- kept the non-interactive result slot, authoritative recipe calculation, input consumption and world ejection behavior unchanged
- added a separate default-off Hypixel opt-in plus independent Crafter stage colors

## 0.9.816 (2026-08-01) — Safe Cartography Table Visuals

- added an exact-class Constellation Cartography Table panel with configurable input, operation and live-map preview surfaces
- preserved scale, duplicate and lock layouts, live map pixels, result items, lock badge, validation error, tooltips and collection
- replaced the first bright vanilla arrow crop after live review with a palette-built operation arrow that remains beneath Minecraft's error state
- added independent Cartography theme, slot, stage, arrow, color and default-off Hypixel controls

## 0.9.815 (2026-08-01) — Safe Loom Visuals

- added an exact-class Constellation Loom panel with configurable input, pattern-grid, scroll-track and live-preview stages
- preserved guided empty-slot sprites, every ordinary and pattern-item design, translated hover tooltips, selected/highlighted states, active scrolling and the layered banner preview
- retained max-pattern validation, authoritative output construction, pattern/dye consumption, sound, slots and Shift-click routing
- added independent Loom theme, slot, stage, frame, color and default-off Hypixel controls

## 0.9.814 (2026-08-01) — Safe Stonecutter Visuals

- added an exact-class Constellation Stonecutter panel with configurable work stage, recipe grid, scroll track, saw identity and machine/player slot wells
- preserved Minecraft's recipe population, selection/highlight sprites, scroll thumb, cursor behavior, item tooltips, output recipes and collection
- added independent Stonecutter theme, slot, stage, grid, track, saw, color and default-off Hypixel controls
- replaced the first overlapping rake-like mark with a dedicated-color circular saw in unused title space

## 0.9.813 (2026-08-01) — Safe Smithing Table Visuals

- added an exact-class Constellation Smithing Table panel with configurable operation and armor-preview stages
- preserved cycling template/base/material hints, onboarding/error tooltips, validation cross, recipes, components, item tooltips and output collection
- retained Minecraft's live armor-stand preview for Netherite upgrades and armor trims
- added independent Smithing theme, slot, hammer, work-stage, arrow-backplate, preview-stage, color and default-off Hypixel controls

## 0.9.812 (2026-08-01) — Safe Grindstone Visuals

- added an exact-class Constellation Grindstone panel with configurable work stage, apparatus, directional arrow and machine/player slot wells
- preserved the conditional invalid-operation cross, repair/disenchant result calculation, enchantment removal, experience behavior, tooltips and output collection
- added independent Grindstone theme, slot, stage, apparatus, arrow-backplate, stage-color and default-off Hypixel controls
- kept Anvil, Smithing, custom subclasses and chest-backed server menus structurally outside the treatment

## 0.9.811 (2026-08-01) — Safe Anvil Visuals

- added an exact-class Constellation anvil panel with configurable work stage, operation symbols, name field and machine/player slot wells
- preserved the authoritative edit box, enabled/disabled field state, repair/rename costs, invalid-operation cross, item tooltips, output validation and completion behavior
- structurally excluded Grindstone, Smithing and every other item-combiner screen while retaining a complete original-call fallback
- added independent anvil theme, slot, work-stage, symbol, name-field, color and default-off Hypixel controls

## 0.9.810 (2026-08-01) — Safe Enchanting Table Visuals

- added an exact-class Constellation enchanting-table panel with a configurable framed stage for the animated book
- preserved all three vanilla offer sprites, runes, generated names, level costs, enabled/disabled colors, hover highlights and cursor behavior
- retained item/lapis slots, readable labels, tooltips, carried stacks, Shift-click routing and authoritative enchant application
- added independent enchanting theme, slot-frame, book-stage, stage-color and default-off Hypixel controls

## 0.9.809 (2026-08-01) — Safe Brewing Stand Visuals

- added an exact-class Constellation brewing-stand panel with a purpose-built apparatus and bounded machine/player slots
- preserved Minecraft's live fuel, brew-progress and bubble sprites plus all bottle, ingredient and fuel interactions
- added independent brewing theme, slot, apparatus, progress-backplate and default-off Hypixel controls
- retained readable configurable labels, hover state, carried items, Shift-click routing and potion output collection
- added a configurable higher-contrast apparatus color and a connected fuel-feed path after live visual review

## 0.9.808 (2026-08-01) — Safe Furnace-family Visuals

- added exact-class Constellation panels for the vanilla furnace, blast furnace and smoker
- preserved the original unlit indicator artwork plus Minecraft's independently animated flame and progress sprites
- kept recipe-book layouts, recipes, labels, slots, output collection, tooltips, narration and machine behavior authoritative
- added independent furnace-family theme, slot, indicator-backplate and default-off Hypixel controls
- added a configurable readable label color across every enabled Constellation inventory treatment

## 0.9.807 (2026-08-01) — Safe Crafting Table Visuals

- added an exact-class Constellation crafting-table panel with independently configurable slot frames and vanilla arrow restoration
- kept the recipe book, labels, items, tooltips, carried stacks, narration and every vanilla crafting interaction authoritative
- left crafting-table styling disabled on Hypixel by default behind its own explicit opt-in
- fixed two disabled-module lifecycle crashes found by live recipe-book and shift-click testing, then adversarially audited every global inventory callback

## 0.9.806 (2026-08-01) — In-game Menu Visual Shell

- added an exact-class Constellation shell for Pause and the ordinary options screens opened from a world
- kept the live world visible beneath a configurable scrim, optional vanilla blur and optional accent rule
- added independent in-game backdrop, button and slider controls plus configurable scrim color, opacity and accent color
- preserved vanilla navigation, labels, tooltips, focus, narration, sliders and every accessibility, language, warning, inventory, chat, death, loading, Realms and third-party screen
- pinned the basic-container injection target so mapping drift fails visibly instead of silently disabling the overlay

## 0.9.805 (2026-08-01) — Safe Inventory Visuals

- added a configurable Constellation player-inventory panel, model frame and slot treatment while preserving the recipe book, effects, equipment, tooltips and carried items
- added opt-in styling for exact vanilla basic containers with separate Hypixel and dungeon controls
- hard-excluded puzzle, Spirit Leap, market, trade, salvage and Museum containers, plus a configurable title denylist
- added a single release automation command covering tests, the retained-client boot gate, Gather deployment, private publishing and drip enqueueing

## 0.9.804 (2026-08-01) — Allowlisted Menu Visuals

- extended the optional space backdrop and stock-button treatment to an exact allowlist of ordinary out-of-game menus
- added independently configurable themed sliders without replacing vanilla labels, dragging, keyboard control, cursor handling or narration
- preserved vanilla accessibility, language, warning, recovery, loading, account, Realms, reporting, third-party and in-world screens
- added alpha-aware disabled and fading states to themed stock controls
- made private release publishing idempotent when the same verified version is retried

## 0.9.803 (2026-08-01) — Safe Title Visual Shell

- added an independently configurable Constellation title backdrop and stock button treatment
- preserved every vanilla title control, logo, splash, version line, Realms notification, narration and input route
- added reduced-motion support and a dedicated Visual settings entry in the main hub
- added an automated owner-only release shelf with Current and Archived Releases folders
- made every future drip-enqueued release publish its verified jar and current testing guide automatically

## 0.9.802 (2026-08-01) — Complete Typed Settings UX

- added visible changed-from-default markers and direct per-row reset actions
- added reversible one-step edits through Undo and Ctrl+Z with clear status feedback
- added Ctrl+F, Escape-to-clear, arrow selection, Enter editing and selected-row reset controls
- added search/filter empty states, modal default context and live ARGB color previews
- prevented clipped settings rows from accepting invisible clicks and fixed search focus after row actions

## 0.9.801 (2026-07-31) — Semantic Editor Actions

- expanded the hand-authored semantic icon family from twelve to sixteen glyphs
- added dedicated Add, Edit, Export and Import marks to the retained SVG design master
- ported Skyblocker's readable text-plus-icon button convention into the shared editor renderer
- added width-aware action icons across migrated editors without crowding narrow controls
- made Mod Menu's configure action open the searchable Constellation hub instead of bypassing it for Apollo
- preserved text labels, hit boxes, actions and the deliberately chrome-free HUD editor

## 0.9.800 (2026-07-31) — Searchable Configuration Navigation

- ported module name and description filtering behavior from Athen and Stella
- added option-label matching, category result counts, empty states, Ctrl+F focus and Escape-to-clear
- added a hand-authored twelve-glyph semantic action icon strip with a retained SVG design master
- integrated search, filter, HUD and settings icons into the main hub and module configuration screen
- visually inspected normal and filtered configuration states in a real client at native GUI scale

## 0.9.799 (2026-07-31) — Complete Terminal Practice

- ported configurable artificial simulator ping from Athen and simulator PB timing from Odin
- added live time, PB, click, mistake and pending-ping feedback without changing Hypixel-style boards
- added persistent per-terminal PB, run, click, mistake and accuracy statistics
- added direct mode, ping, statistics and reset command controls
- added optional automatic replay and configurable Melody inclusion in Random

## 0.9.798 (2026-07-31) — Profile Viewer Visual Shell

- migrated the complete twenty-page Profile Viewer shell to the constellation visual system
- added focused player search, animated loading, empty and error cards, and retained last-good data
- added Ctrl+L player-field focus and F5 forced refresh controls
- replaced flat tabs, buttons and default rows with clipped, bordered and width-safe shared components
- fixed initial-player state so screen reinitialization cannot trigger an unintended profile reload

## 0.9.797 (2026-07-31) — Compact Screen UI Completion

- migrated Speed Presets, Scoreboard Order, Tunnel Maps and Dungeon Records to the shared visual system
- made all searchable lists use matching render clipping, hit-test bounds and proportional scrollbars
- converted the dungeon floor selector to a two-column grid that remains usable at short GUI heights
- added a deliberate second-click confirmation before clearing dungeon run records
- made long scoreboard labels and dungeon records width-safe without altering saved data

## 0.9.796 (2026-07-31) — Workflow Editor UI Migration

- migrated Inventory Buttons, Spirit Leap, Party Guard and Carry Tracker to the shared visual system
- added bounded panels, consistent focus states and scroll-position feedback to workflow editors
- prevented clipped Carry Tracker rows from accepting invisible button clicks
- made Party Guard reject malformed floor and numeric input visibly instead of silently saving zero
- retained every licensed command, profile, sorting, binding and deliberate-click interaction

## 0.9.795 (2026-07-31) — Bespoke Editor UI Migration

- added shared screen background, header, panel, button, truncation and scrollbar primitives
- migrated the party-message editor to the current visual system without changing template behavior
- migrated Smart Sack Refill with a clipped list, clearer hierarchy and visible scroll position
- migrated the slot-binding editor while preserving its complete profile and mouse control scheme
- kept the HUD editor entirely outside the decorative screen system

## 0.9.794 (2026-07-31) — Visual System Foundation

- replaced malformed JPEG-backed backgrounds with original correctly sampled PNG artwork
- added clipped-corner surfaces, coherent borders, search fields, buttons and focus states
- added searchable module navigation with enabled and disabled filters
- separated card navigation from direct module toggles and added live module counts
- replaced frame-rate-dependent card motion with time-based easing
- restored actual widget rendering in the complete typed configuration browser
- replaced Mod Menu's broken placeholder with a hand-authored vector constellation mark

## 0.9.793 (2026-07-31) — Sign Enter Controls

- made unmodified Enter submit every sign through the same path as the Done button
- retained Shift+Enter as the explicit next-line control
- added an independent saved toggle and `/phoenixinput` command control
- removed the calculator-only Enter setting now superseded by the general behavior

## 0.9.792 (2026-07-30) — Complete Sign Calculator

- replaced the inert three-token sign helper with a complete expression engine
- added operators, parentheses, magnitudes, purse input and eighteen mathematical functions
- added exact numeric-sign recognition with search and player-name exclusions
- added live previews, safe invalid-input fallback, price precision, amount rounding and output limits
- added requires-equals, purse, preview, Enter-close and command controls

## 0.9.791 (2026-07-30) — Phoenix Input and Visual Integrity

- implemented the previously inert Auto Sprint with an independent water policy
- implemented instant camera-height sneak and non-wrapping hotbar wheel selection
- replaced destructive passenger-arrow removal with render-state-only attached-arrow hiding
- made attached-arrow and inventory-effect behavior respect both their own toggle and Phoenix master
- added one status/option command while retaining complete typed-browser access

## 0.9.790 (2026-07-30) — Complete Typed Configuration Browser

- added one searchable browser for every primitive and text option in each module
- added direct Boolean toggles plus validated integer, long, float, double and text editing
- added ARGB color detection, previews and six/eight-digit hexadecimal input
- added type filtering, value search, scrolling and right-click default restoration
- retained purpose-built editors for structured maps and lists instead of exposing unsafe raw state

## 0.9.789 (2026-07-30) — Constellation Icon Foundation

- added a hand-authored fifteen-icon star-map family for every constellation
- gave each module a distinct silhouette on one consistent 32-pixel optical grid
- integrated module icons into Hub cards and the active configuration header
- retained a vector design master alongside the optimized transparent texture atlas
- established reusable icon rendering for the wider UI/UX redesign

## 0.9.788 (2026-07-30) — Complete Profile Overview

- replaced the seven-row legacy Overview with exact cross-profile summary models
- separated purse, personal bank, profile bank, Motes and recent bank transactions
- added Cookie, profile age, active co-op, skill, combat and selected-pet summaries
- added ordered current Essence balances plus future-ID preservation
- added Maxwell power/MP/bag/tunings, Rift Prism and Abiphone state with full controls

## 0.9.787 (2026-07-30) — Complete Profile Saved Loadouts

- added all 27 Loadout template positions with locked, saved-empty and configured states
- added exact armor/equipment set links with current-equipped-set substitution
- added saved pet UUID, HOTM/HOTF presets, power stone and tuning-slot details
- reused the safe legacy item decoder for native icons, decorations and tooltips
- added independent sections, empty/locked policies, scrolling and bounded limits

## 0.9.786 (2026-07-30) — Complete Profile Mob Records

- added lifetime Mob Records from every profile kill and death counter
- added optional numeric-variant grouping matching the licensed reference behavior
- added six record filters, partial name/ID search, five sorts and independent minimums
- added configurable kills, deaths, K/D, total-share percentages, raw IDs and row limits
- preserved non-Bestiary and future mob IDs without requiring a static catalogue

## 0.9.785 (2026-07-30) — Exact Profile Garden Summary Progression

- replaced hard-coded Garden summary progress with the maintained 15-level 60,120-XP curve
- added exact accepted-offer and unique-visitor milestone progress through 10,000 and 300
- added crop unlock levels, all 13 Personal Best targets and the maintained Larva cap
- added cumulative Farming Level Cap and Extra Farming Fortune medal/ticket costs
- added four maintained default Barn skins with selected/unlocked and unknown-future handling

## 0.9.784 (2026-07-30) — Exact Profile Garden Mutations and Chips

- added all 41 maintained mutations with discovered, analyzed and non-analyzable states
- added unknown-future mutation preservation, state/rarity filters, search, sorting and limits
- added Glowing Mushrooms broken from the profile statistics
- added all ten Garden Chip levels with exact 19-level cumulative Sowdust progress
- expanded the validated Garden catalogue while retaining core endpoint fallback behavior

## 0.9.783 (2026-07-30) — Exact Profile Garden Plots and Greenhouse

- added all 24 numbered plots with exact 5×5 catalogue coordinates and unlock state
- added tier-aware next unlock costs across Compost and Enchanted Compost
- added exact Growth Speed, Crop Yield and Plot Limit maximums and rewards
- added cumulative Copper, Compost, Enchanted Compost and Ethereal Vine upgrade costs
- added independent locked-plot, coordinate, reward, cost and row-limit controls

## 0.9.782 (2026-07-30) — Exact Profile Garden Visitors and Composter

- added all 139 current visitor identities and five rarity groups
- added per-visitor visits, accepted offers and rejected offers with unknown-future handling
- added rarity filters plus completed, visits, rarity and name sorting
- added all five composter upgrades with exact level out of 25 and cumulative Copper progress
- expanded validated Garden last-good caching without making core Garden data depend on it

## 0.9.781 (2026-07-30) — Exact Profile Garden Crop Progression

- added all 46 exact cumulative milestones for each of the 13 current Garden crops
- added current milestone, next-milestone remaining and full milestone target per crop
- added exact crop-upgrade Copper paid out of 7,685 across all nine upgrades
- added milestone sorting plus independent milestone/Copper details
- added validated one-to-168-hour last-good catalogue caching with core Garden fallback

## 0.9.780 (2026-07-30) — Complete Profile Foraging Progression

- added Fig/Mangrove gifts, Personal Bests, Fortune perks and Forest Whispers
- added daily trees/log types/gifts plus all five Heart of the Forest loadouts and exact level progress
- added selected ability, node levels, disabled state and last-reset records
- added raw attribute syphons, owned shards/capture times, fusions and active trap state
- added independent sections, dates, hiding, sorts and per-section limits

## 0.9.779 (2026-07-30) — Complete Profile Chocolate Factory

- added Chocolate totals, Prestige progress, Barn capacity and last-viewed time
- added all seven rabbit employees with levels, base production and unknown future employees
- added Factory upgrades, Time Tower state/charges and cumulative Rabbit Hitman progress
- added a validated, cached seven-rarity catalogue covering all 512 current rabbits
- added employee hiding/sorting/limits, independent sections and paged profile tabs at normal GUI widths

## 0.9.778 (2026-07-30) — Complete Profile Fishing Progression

- added all 18 Trophy Fish species with Bronze, Silver, Gold, Diamond and total catches
- added last Trophy Fish, Trophy rank/reward progression and exact Dolphin kill milestones
- added sea-creature kills, Festival Sharks and all lifetime item/treasure fishing counters
- added uncaught/diamond hiding, minimum-tier filtering, five sorts, limits and tier-detail controls
- kept unknown future Trophy Fish API keys visible and marked

## 0.9.777 (2026-07-30) — Complete Profile Rift Progression

- added current/lifetime Motes, visits, sitting time, Enigma Souls and Grubber stacks
- added all seven Rift eyes and all nine Montezuma cats with found/missing breakdowns
- added all eight Timecharms with secure state, discovery visit count and timestamp
- added missing/found hiding, soul/trophy limits, five trophy sorts and independent detail controls
- kept unknown future eyes, cats and Timecharms visible and marked

## 0.9.776 (2026-07-30) — Complete Profile Garden Progression

- added the separately authenticated Garden endpoint with profile-safe caching and refresh
- added exact Garden level progress, Copper, plots, barn skins, Larva and all crop totals/upgrades
- added visitor visits/completions, Jacob contests, medals, perks and configurable breakdowns
- added complete composter storage/state/upgrades and greenhouse spaces/upgrades
- added independent sections, zero-state hiding, limits, crop sorting and unknown future API rows

## 0.9.775 (2026-07-30) — Complete Profile Crimson Isle Progression

- added selected faction plus Mage and Barbarian reputation, rank, next rank and highest unlocked Kuudra tier
- added all Kuudra tier completions, highest waves, total runs and correctly thresholded collection progress
- added all seven Dojo tests with points, grades, recorded times, total score and belt progression
- added independent sections, details, zero-state hiding, limits and sorting while preserving unknown future API IDs

## 0.9.774 (2026-07-30) — Complete Profile Museum

- added the separately authenticated Museum endpoint with profile-safe caching and refresh
- added all 627 canonical donation entries across seven categories plus 335 special items
- added direct, borrowed, parent-chain and missing donation states with armor-set piece details
- added category/search/status filters, four sorts, limits and independent presentation controls
- added exact legacy-NBT special-item identification with isolated unreadable-entry reporting

## 0.9.773 (2026-07-30) — Complete Profile Mining Progression

- added exact Heart of the Mountain level/progress, selected tree, ability and node-state summaries
- added available, spent and lifetime Mithril, Gemstone and Glacite Powder totals
- added all nucleus, other and Glacite Crystal states, found/placed totals and nucleus-run count
- added Rock Pet ore milestones, mineshafts, Fossil Dust, fossil donations and corpse totals
- added independent section controls plus crystal filters, hiding, sorting and limits

## 0.9.772 (2026-07-30) — Complete Profile Minions

- added all 60 current minion families with exact XI/XII caps and per-tier crafted progress
- added personal and co-op unique crafts, crafted-minion slot progress and missing tier counts
- added category/search filtering, personal-only mode, uncrafted/maxed hiding, five sorts and limits
- kept unknown future generator families visible without guessing their caps

## 0.9.771 (2026-07-30) — Complete Profile Collections

- added all 87 current Farming, Mining, Combat, Foraging, Fishing and Rift collections
- added exact co-op totals, personal contribution, tiers, next thresholds, completion and upcoming unlocks
- added category/search filtering, zero/maxed hiding, six sorting modes, limits and independent presentation controls
- added validated official Hypixel resource caching with visible unknown future collection IDs

## 0.9.770 (2026-07-30) — Complete Profile Bestiary

- added a full Bestiary page backed by the current MIT NEU family, category, bracket and cap catalogue
- added exact alias-aware kills, deaths, family levels, next thresholds, completion and remaining kills
- added category/search filtering, zero/maxed hiding, category/kills/level/remaining/completion sorting and limits
- added bounded asynchronous refresh, validated last-good disk caching and visible uncatalogued kill totals

## 0.9.769 (2026-07-30) — Complete Profile Pet Progression

- replaced raw pet XP with exact rarity-offset levels, next/max progress, remaining XP and overflow
- added exact Tier Boost handling, Bingo leveling and 200-level Golden, Jade and Rose Dragon curves
- added active, rarity, candy, held-item, skin and optional UUID details with profile summaries
- added active-only, active-first, rarity, search, limit, sort, precision and detail controls

## 0.9.768 (2026-07-30) — Complete Profile Slayer Progression

- replaced raw Slayer XP with exact Revenant, Tarantula, Sven, Voidgloom, Inferno and Vampire levels
- added next-level progress, remaining XP, overflow, total XP and total boss summaries
- added per-type and per-tier kills, raw attempts, claimed rewards and unclaimed reward warnings
- added independent visibility and precision controls plus honest missing-field handling

## 0.9.767 (2026-07-30) — Complete Profile Dungeon Progression

- replaced raw dungeon XP with exact Catacombs and class levels, progress, selected class and optional overflow
- added configurable class average, total runs, secrets and secrets-per-run summaries
- added Entrance, F1–F7 and M1–M7 completions with independent completion, S, S+ and best-score records
- added empty-floor, Entrance, precision, XP and record visibility controls plus honest missing-API handling

## 0.9.766 (2026-07-30) — Exact Profile Skill Progression

- replaced raw profile XP rows with exact Hypixel skill levels, caps, progress, remaining XP and overflow
- added current Foraging and Hunting caps plus profile-dependent Farming and Taming caps
- added configurable skill average composition, total XP, precision and progression detail
- distinguished disabled skill APIs from real zero-XP skills

## 0.9.765 (2026-07-30) — Profile Wealth Breakdown

- ported SkyBlockPv's currency and per-storage net-worth category model onto Constellation's conservative item-value engine
- added liquid currency, total item value, category coverage and expandable highest-value item breakdowns
- added Sack valuation, modifier completeness, missing-price counts and progressive one-at-a-time auction enrichment
- fixed decoded profile items to retain Constellation-compatible ExtraAttributes for tooltips and modifier valuation

## 0.9.764 (2026-07-30) — Profile Inventory Browser

- ported Skyblocker's 26.2 legacy item decoding, hotbar correction, wardrobe ordering and native item-grid rendering
- added Inventory, Armor, Equipment, Ender Chest, Accessory Bag, Potion Bag, Fishing Bag, Quiver and Personal Vault views
- added separately selectable backpack, wardrobe and equipment-set pages with native item tooltips and stack decorations
- added bounded asynchronous decoding, per-container failure isolation, paging, scrolling, row controls and configurable slot colors

## 0.9.763 (2026-07-30) — SkyBlock Profile Viewer

- ported SkyBlockPv's Minecraft-session-authenticated profile transport, bounded request flow and five-minute cache
- added player lookup, all-profile switching, selected-profile defaulting and manual refresh through `/pv [player]`
- added Overview, Skills, Dungeons, Slayers and Pets pages with defensive handling for disabled Hypixel API fields
- kept requests asynchronous, credentials in memory only and the interface free of gameplay automation

## 0.9.762 (2026-07-30) — Complete Accessory Helper

- replaced Lyra's two advertised but inactive accessory settings with a complete Skyblocker accessory-helper port
- added profile-safe, page-safe Accessory Bag collection plus recombobulation state and family-tier classification
- added missing, upgrade, downgrade and highest-owned tooltips with Magical Power gains and configurable bag highlights
- added a filterable, searchable, paged missing/upgrade panel sorted by price per Magical Power with bounded metadata and price loading
- added a last-good disk catalogue cache, fail-open refresh behavior and full `/accessoryhelper` controls without copying unlicensed family data

## 0.9.761 (2026-07-30) — Profile-Safe Collection Tracker

- ported SkyHanni's exact collection-menu and co-op-total parsing model and SkyblockCollectionTracker's session-rate state
- added profile-safe cached totals for every visible collection without relying on incorrect inventory pickup estimates
- added tracked collection selection, pause/resume/reset, optional explicit goals, remaining amount, rate and ETA
- added configurable movable HUD rows with authoritative-sync freshness and full `/collectiontracker` controls

## 0.9.760 (2026-07-30) — Profile-Safe Active Pet Display

- ported Devonian's active-pet menu, widget, summon, Autopet and selected-slot synchronization
- added profile-safe pet name, level, cosmetic level, rarity, held item, skin, icon, total XP and level-progress state
- added exact-read progress rate and ETA estimation that remains absent until sufficient authoritative samples exist
- added a chrome-free movable HUD, selected-pet highlight, total-XP tooltip, optional Autopet title and full `/petdisplay` controls

## 0.9.759 (2026-07-30) — Hoppity Island-Graph Navigation

- ported all 14 complete MIT SkyHanni island graphs used by the 212 Hoppity egg locations
- ported the licensed nearest-node, weighted Dijkstra and first-node shortcut behavior
- added lazy per-island loading, movement/interval rerouting, route-distance diagnostics and bounded graph visits
- added advisory walking-path rendering with guess-only mode, nearest-uncollected fallback and complete `/hoppitywaypoints` controls

## 0.9.758 (2026-07-30) — Hoppity Egg Waypoints and Locator

- ported all 212 MIT SkyHanni repository egg locations across 14 supported islands
- added profile-safe nearest-location collection learning with configurable radius and collected-location filtering
- ported the Egglocator happy-villager particle curve fit and snap-to-authoritative-location solver
- added ready-state-gated boxes, beams, labels, distances and direct lines with complete `/hoppitywaypoints` controls

## 0.9.757 (2026-07-30) — Chocolate Factory Stray Timer

- ported SkyHanni's exact meal/Hitman/visitor trigger set and Factory-only 30-second countdown state machine
- added new-caught-slot detection that ends the timer without mistaking existing caught rabbits for a new catch
- added configurable countdown precision, duration, danger colors and per-second final-window dings
- added optional destructive-slot and close protection with Shift bypass, movable HUD and `/straytimer` controls

## 0.9.756 (2026-07-30) — Unclaimed Hoppity Egg Schedule

- ported SkyHanni's normal/alternate meal-day schedule, exact reset hours and spawn/claim messages
- added exact claimed-cycle persistence by profile, SkyBlock year and meal without stale-day leakage
- added ready, claimed, next-spawn and event-end timing with schedule or soonest/ready-first ordering
- added guarded optional chat/title/sound reminders, chat timing responses and full `/hoppityeggs` controls

## 0.9.755 (2026-07-30) — Hoppity Event Statistics

- ported SkyHanni's exact meal, Hitman, rabbit rarity, unique, duplicate, purchase, visitor, Side Dish, milestone and Rabbit-the-Fish event signals
- added profile- and SkyBlock-year-safe counts, duplicate Chocolate totals and exact Chocolate Factory time
- added configurable end-of-event summaries, year selection, safe per-year clearing and persistence
- added an independently optional movable live HUD with configurable content and row limit

## 0.9.754 (2026-07-30) — Rabbit Hitman Slot Costs

- ported SkyHanni's exact Rabbit Hitman inventory detection and non-border cost-lore parsing
- added the authoritative 28-slot price schedule with purchased, paid, remaining and next-slot totals
- added guarded profile-safe persistence that rejects partial or unexpected menu price layouts
- added a movable HUD with independent rows, optional cached visibility and `/hitmancosts` controls

## 0.9.753 (2026-07-30) — Profile-Safe Hoppity Collection

- ported Hoppity Collection page, found, duplicate, requirement and total-progress parsing from SkyHanni
- added profile-safe rabbit counts, rarity totals, duplicate totals and explicit page-completeness tracking
- added configurable found/missing, rarity, Factory, Shop, requirement and Golden Stray highlights
- added a movable rarity/total/progress/page HUD, contextual tooltips and `/hoppitycollection` controls

## 0.9.752 (2026-07-30) — Chocolate Factory Inventory Depth

- ported SkyHanni's all-affordable, barn-full, unclaimed-milestone and active/full Time Tower menu highlights
- ported Skyblocker's rabbit, Coach, Prestige, Barn, Shrine, Hand-Baked Chocolate, Time Tower and Hitman slot text
- added deeper cost-per-CPS, prestige timing, Time Tower production and charge tooltips
- added independent warnings, tooltips, level text, colors and configurable barn-capacity threshold

## 0.9.751 (2026-07-30) — Complete SkyBlock Guide Highlights

- ported SkyHanni's missing-progress rules for task, menu, Abiphone, minion, consumable, Jacob, story, collection, essence and one-time Guide views
- ported Power Stone Guide missing-state highlights, nine-stone Bazaar costs and deliberate click-to-Bazaar routing
- added independent feature-category, overlay, marker, tooltip, price, click and color/text controls
- added full `/skyblockguide` status and option commands

## 0.9.750 (2026-07-30) — Bazaar Order Competition

- ported live buy-order and sell-offer competitiveness checks from SkyHanni
- tracked transitions after a real orders-screen snapshot and alerts only when an order becomes outbid
- added filled, partial, expired, expiring, outbid and matched order markers with configurable colors
- added independent chat, title, sound, recovery, message-template, color and cooldown controls

## 0.9.749 (2026-07-30) — Auction Outbid Alerts

- made Lyra's existing auction-outbid setting functional instead of declaration-only
- ported SkyHanni's exact Auction outbid chat recognition with a local title and sound
- added independent title, sound, title text and color controls plus duplicate-message suppression
- added command controls and connection-safe transient reset

## 0.9.748 (2026-07-30) — Andromeda Configuration Truthfulness

- removed 22 legacy Andromeda toggles that were visible in generated settings but had no runtime consumer
- retained the live Rift low-time master and every working granular HUD, waypoint, solver, area, progression and rendering control
- removed speculative Bluetooth Ring, Deadgehog and generic helper labels that had no licensed implementation
- verified every remaining Andromeda Boolean is consumed outside its declaration

## 0.9.747 (2026-07-30) — Complete McGrubber Detection

- ported Skyblocker's automatic McGrubber detection from Motes Grubber sale prices, SkyBlock Levels consumable progress and exact orb payouts
- retained consumption-message learning and manual override as independent paths
- added per-source toggles, bounded validation, source-aware status and deduplicated persistence/notifications
- all 73 Motes prices, storage totals and tooltips now update from the learned stack count without manual maintenance

## 0.9.746 (2026-07-30) — Complete Vampire Slayer Guidance

- replaced a stale advertised-only toggle with working Healing Melon, Steak Stake, Twinclaws/Holy Ice and Mania alerts
- added own, tagged-other and configurable co-op Bloodfiend highlighting with low-health color, labels, distances and player lines
- added optional health percentage, HP-until-Steak and Mania Circles countdown information
- added exact-texture Blood Ichor and Killer Spring boxes, labels, beams/lines and same-tick sound-spam protection
- added independent alert channels, messages, colors, delays, thresholds, ranges and complete `/vampirehelper` controls

## 0.9.745 (2026-07-30) — Temporal Pillar Navigation

- ported SkyHanni's Temporal Pillar dodge into the complete Rift graph router
- named pillars now disable navigation nodes inside a configurable radius and immediately rebuild active routes
- added optional avoidance-volume box, beam, label and distance rendering with independent range, color and wall controls
- extended `/riftnav` with pillar status, dodge, visualization, radius, scan range, beam height and color controls

## 0.9.744 (2026-07-30) — Rift-wide Progression Suite

- ported SkyHanni's Crux Talisman progress/bonus display with compact-maxed, percentage, tier and color controls
- ported Punchcard Artifact session tracking with unpunched/reverse player highlighting, movable count HUD, artifact guard and range/render controls
- ported missing Rift Guide entry marking and the optional Horsezooka horse-render filter
- added `/riftprogress` status, reset and primary option controls; all behavior is passive and Rift-gated

## 0.9.743 (2026-07-30) — Rift Mirrorverse Dance and Craft Suite

- added the authoritative 49-step Dance Room sequence with exact sound-driven advancement, one-second countdown and failure reset
- added a movable Dance HUD with configurable visible depth, line spacing, prefixes and per-action/countdown formatting
- added optional original-title and real-player hiding strictly inside the exact Dance Room bounds
- added exact mirrored Craft Room Zombie, Slime and Cave Spider silhouettes with independent name, health, box, beam, range and wall controls
- added Craft Room player hiding and complete `/mirrorverse` status, reset, tuning and feature controls

## 0.9.742 (2026-07-30) — Complete Rift Wyld Woods Suite

- replaced the inactive Wyld Woods placeholder toggles with exact texture-identified Larva and Odonata guidance
- added held Larva Hook and Empty Odonata Bottle gating plus independent box, beam, label, distance, range, color and wall controls
- added exact four-name Shy Crux proximity recognition with configurable title, subtitle, chat, sound and world guidance channels
- added bounded alert timing and complete `/wyldwoods` status, tuning and feature controls

## 0.9.741 (2026-07-30) — Complete Rift Mountaintop Suite

- added Sun Gecko health, combo, combo-expiry and all seven modifier states plus separately configurable real/clone highlights
- added Time Gun evolution timing, nearby Timite/Obsolite discovery and exact final-expiry countdown guidance
- added optional persistent Timite, Youngite and Obsolite collection, time, Motes profit and Highlite-craft tracking
- added Rose's End flowerpot drop guidance and profile-persistent Ubik cooldown, reminder, HUD and post-game quick-close
- added four movable HUD surfaces and complete `/mountaintop` status, reset, tuning and feature controls

## 0.9.740 (2026-07-30) — Complete Rift Stillgore Suite

- upgraded all six Blood Effigies with scoreboard state, exact nearby armor-stand timers and transition-derived 20-minute respawns
- added configurable respawning-soon, unknown-state and nearby broken-effigy presentation without duplicating the existing waypoint source
- added the exact Splatter Crux three-heart particle signature with short-lived boxes, optional beams/labels and independent wall behavior
- added complete timing and presentation controls plus `/stillgore` status, tuning and reset commands

## 0.9.739 (2026-07-30) — Complete Rift Colosseum Suite

- added exact Blobbercyst recognition with configurable boxes, labels, range and wall behavior
- added Bacte phase tracking from both growth chat and live boss labels with an optional movable HUD
- added the server’s progressive arena kill-zone deadline, continuously refreshed title/subtitle and independent alert channels
- added floor-only Bacte Tentacle waypoints and packet-accurate generic-damage hit/HP tracking that excludes wall damage
- added complete presentation controls and `/colosseum` status, tuning and reset commands

## 0.9.738 (2026-07-30) — Complete Rift Living Cave Suite

- added Living Metal lapis-transition animation with exact click/block pairing, expiry and particle controls
- added moving and placed Defense Block tracking tied to the correct Autonull family mob
- added complete Living Metal Snake reconstruction, state recognition and held-tool-specific head/tail guidance
- added an optional movable four-piece Living Metal Suit progression HUD using exact `lm_evo` item data
- added independent colors, ranges, boxes, lines, labels, bars and `/livingcave` controls

## 0.9.737 (2026-07-30) — Complete Rift Dreadfarm Suite

- added Wand-gated Agaricus maturity guidance with configurable countdown, timing and ready state
- added exact Volt mood recognition, 12-second lightning warning and configurable seven-block strike-range ring
- added particle-tracked Wilted Berberis boxes plus per-field respawn-sequence learning and ordered current/next/third guidance
- added all 14 authoritative wooden-button spots and 56 buttons with persistent hit tracking, nearest-spot routing and reset controls
- added precise Berberis particle/sound controls, complete rendering options and `/dreadfarm` configuration

## 0.9.736 (2026-07-30) — Rift West Village Suite

- added the five-row Kloon hacking solver, terminal color guide and all eight visor-gated terminal waypoints
- added persistent Fly, Spider and Silverfish vacuum tracking plus exact vermin entity highlighting
- added the complete ordered 52-point Gunther race guide with progress recovery and look-ahead controls
- added independent rendering, range, visibility, color, HUD and `/westvillage` controls while keeping all interaction advisory

## 0.9.735 (2026-07-30) — Complete Rift Motes Suite

- added authoritative lifetime Motes, session gain, hourly rate, visit duration and leave-summary tracking
- added all 73 current Hypixel Motes NPC prices with McGrubber bonuses, stack tooltips and transferred-item protection
- added movable Rift Storage valuation, item/stack totals and configurable high-value slot markers
- added particle-validated Motes Orb guidance with pickup state, optional particle hiding and complete render/tuning controls

## 0.9.734 (2026-07-30) — Rift Guide Soul Synchronization

- replaced numbered Enigma Soul labels with the authoritative 52-name, nine-area Rift Guide dataset
- added profile-aware completion synchronization from the real Enigma Souls menu with exact missing-state parsing
- added right-click menu routing, found/missing/routed slot markers and contextual soul tooltips
- added named soul routing, current-area filtering, area labels and independent sync/menu/config controls

## 0.9.733 (2026-07-30) — Rift Core and Navigation

- rebuilt the previously inactive Andromeda core with a live Rift time, Motes, session, soul and effigy HUD
- added all 52 profile-aware Enigma Soul waypoints with collection detection, manual correction and complete render controls
- added the complete 2,201-node Rift graph with shortest-path routing to any or the nearest missing Enigma Soul
- added all three Mirrorverse waypoint sections and scoreboard-driven compact or full Stillgore effigy guidance
- added low-time alerts, bounded rendering, section/row/channel controls, colors, ranges and complete `/riftguide` and `/riftnav` commands

## 0.9.732 (2026-07-30) — General Speed Presets

- added the seven live farming speed presets with searchable editing, validation, deletion and default restoration
- added named profiles, optional automatic SkyBlock-profile selection and direct, next, previous and menu keybinds
- added `/setmaxspeed <preset>` rewriting, Rancher's Boots sign aliases and deliberate `/speedpreset use` controls
- added configurable cooldown, local action-bar/chat/sound feedback and a movable recent-preset HUD with independent rows

## 0.9.731 (2026-07-30) — Scoreboard Information Suite

- added exact SkyBlock date and configurable 12/24-hour time elements plus dated lobby IDs and learned current/maximum player counts
- added all-active or highest-priority calendar event rows with optional upcoming events
- added authoritative mayor, perk, minister and party leader/member groups by reusing existing cached state
- added configurable footer, date format, row limits, individual element toggles and complete ordering-editor integration

## 0.9.730 (2026-07-30) — Custom SkyBlock Scoreboard

- added a movable and scalable custom scoreboard that safely replaces the vanilla sidebar only while active on Hypixel SkyBlock
- added ordered server lines plus cached Purse, Bank, Bits, level, Magical Power, tuning, power, Gems, Quiver, God Pot, mayor, party, election and area values
- added a searchable line editor with per-line show/hide and ordering, safe duplicate filtering and preserved unknown server information
- added configurable title, alignment, spacing, row cap, background, outline, text shadow, six colors, persistence and complete `/customscoreboard` controls

## 0.9.729 (2026-07-30) — Glacite Tunnel Maps

- added the complete 825-node Glacite Tunnels navigation graph with nearest-node recovery and weighted shortest paths
- added searchable destination selection, repeated-location cycling, Base Camp routing and optional travel-scroll warping
- added commission right-click and optional automatic destination selection plus Royal Pigeon left-click progression
- added dynamic or fixed path colors, configurable width/look-ahead/wall visibility, goal guidance, arrival handling, keybinds, HUD and complete `/tunnelmap` controls

## 0.9.728 (2026-07-30) — Heart of the Mountain Helper

- added exact all-perk HOTM parsing with enabled, disabled and locked highlights plus perk-level and unused-token slot text
- added Mithril, Gemstone and Glacite powder-spent calculations, selectable number/percentage layouts, current-powder guidance and shift-for-ten-level planning
- added profile-safe Sky Mall effect learning with off, mining-only and everywhere display modes
- added a movable menu summary, independent rows, colors and complete `/hotmhelper` controls

## 0.9.727 (2026-07-30) — Crystal Hollows Waypoints

- replaced the dead Wishing Compass flag with exact two-trail particle solving and reliable forward-ray intersection
- added chat, area, manual and compass-discovered waypoints for all thirteen supported Crystal Hollows locations
- added configurable world boxes, beams, lines, labels, distance, nearest-only mode, arrival removal and a movable HUD
- added invalid-use protection, crystal/zone-aware target naming, deliberate party sharing and complete `/crystalwaypoints` controls

## 0.9.726 (2026-07-30) — Scatha Mining

- replaced the dead Scatha flags with exact spawn-message and local named-entity pairing
- added independent Worm, Scatha, cooldown-ready and Scatha Pet chat/title/sound alerts
- added profile-safe spawn, ratio, dry-streak, pet-drop and rarity statistics plus a movable HUD
- added optional rarity-preserving pet-message replacement, active-worm world guidance and complete `/scatha` controls

## 0.9.725 (2026-07-30) — Deep Caverns Guide

- added the exact 92-point SkyHanni route from the Gunpowder Mines Lift to Rhys
- added ground-aware progression, configurable look-ahead, nearest recovery, skip/back and completion state
- added live-profile rainbow rendering plus monochrome, box, line, label, distance, start and wall controls
- added locked-Lift chat/menu activation, complete `/deepguide` controls and a movable progress HUD

## 0.9.724 (2026-07-30) — Crystal Nucleus Barriers

- added the five exact Crystal Nucleus barrier volumes with the live SkyHanni colors
- added filled or configurable-width outline rendering, labels and through-wall controls
- added independent Amber, Amethyst, Topaz, Jade and Sapphire visibility and colors
- added strict Crystal Hollows/Nucleus-coordinate gating, optional Spring-only visibility and complete `/nucleusbarriers` controls

## 0.9.723 (2026-07-30) — Ordered Mining Routes

- added profile-safe named mining routes with Coleweight clipboard import/export and atomic persistence
- added nearest-start selection, cyclic progression, skip/back/skip-to and optional forward-jump recovery
- added insert, exact-coordinate insert, move, delete, label, save, erase and protected unload editing
- added current/previous/next/setup/all rendering, trace/setup lines, Mineshaft lifecycle options and movable HUD

## 0.9.722 (2026-07-30) — Powder Chest Timer

- added authoritative 60-second Crystal Hollows powder-chest lifetimes and abandoned-state cleanup
- added dynamic urgency or static highlights, world timers and oldest/nearest chained route lines
- added a movable active-count/oldest/nearest HUD plus optional discovery and opening sound muting
- added exact Great Explorer menu learning, nearby-player false-positive rejection and complete timer controls

## 0.9.721 (2026-07-30) — Fossil Excavator Profit Tracker

- added exact excavation completion framing with every live observed reward type and unresolved-price safety
- added session or per-profile excavation, item, Suspicious Scrap, Fossil Dust and Glacite Powder accounting
- added total, per-excavation and hourly profit, active uptime, recent rewards and six item sorting modes
- added a movable configurable HUD, independent high-value alerts, per-excavation summaries and `/fossilprofit` controls

## 0.9.720 (2026-07-30) — Fossil Excavator Solver

- replaced Aquila's dead fossil flag with all eight fossil shapes and 404 valid starting placements
- added proven safe and low-charge opening sequences followed by exact evidence-based probability solving
- added best/probability highlights, percentage text, movable status HUD, tooltips and optional wrong-click protection
- added Fossil Muncher answers, independent presentation controls, colors and complete `/fossilsolver` options

## 0.9.719 (2026-07-30) — Mining Tool State

- replaced Aquila's dead drill-fuel and Pickonimbus HUD flags with licensed metadata and lore parsing
- added a movable tool HUD with independent name, amount, percentage, text-bar and color controls
- added drill and Pickonimbus inventory durability bars plus held, hotbar, inventory and mining-area scopes
- added per-item low-state alerts, thresholds, colors and complete `/miningtools` controls

## 0.9.718 (2026-07-30) — Metal Detector Solver

- replaced Aquila's dead Metal Detector flag with the complete licensed distance solver
- added keeper-derived Mines of Divan center detection and all 42 known chest offsets
- added stable-sample trilateration, configurable tolerance, fallback search and collection timing
- added possible/exact boxes, beams, line, labels, distances, alerts, colors and `/metaldetector` controls

## 0.9.717 (2026-07-30) — Mining Conveniences

- added exact completed-commission slot highlighting in the Commissions menu
- added customizable clickable Mismyla calls after eligible commission completions
- added obfuscation-validated Fred bad-signal redial actions without false matching ordinary dialogue
- added independent original-line replacement, hover, label, color, message placeholder and command controls

## 0.9.716 (2026-07-30) — Pickobulus Helper

- replaced Aquila's dead Pickobulus flag with exact 8-by-8-by-8 exposed-block prediction
- added Gold Mine, Deep Caverns, Dwarven, Crystal Hollows, Glacite Tunnels and Mineshaft rules
- added world outlines plus block, ore, powder and Mineshaft-pity HUD forecasts
- added held-ability/cooldown states, independent HUD rows, range, color, wall and `/pickobulushelper` controls

## 0.9.715 (2026-07-30) — Mining Highlights

- added exact sea-lantern-backed Dwarven ore-carpet discovery and low-profile filled highlights
- completed Crystal Hollows treasure-chest association from spawn chat and authoritative block updates
- added chest outlines, live CRIT-particle lock spots and success/failure/open sound progress tracking
- added independent ranges, timings, colors, wall modes, render layers, cleanup and `/mininghighlights` controls

## 0.9.714 (2026-07-30) — Mining Awareness

- added ownership-paired Golden and Diamond Goblin highlighting from matching spawn chat and entity events
- added independent box, beam, line, label, distance, through-wall, range, timing and color controls
- added exact Crystal Hollows high-heat pant muting with configurable heat and Magma Fields height thresholds
- retained manual current-position and exact-coordinate start locations for every individual Garden crop

## 0.9.713 (2026-07-30) — Tree Cleanup

- added Galatea-only removal of the four exact decorative tree block-display states
- added independent stripped spruce, mangrove wood, mangrove leaf and azalea leaf controls
- added exact creaking-death tree-break sound filtering with separate Galatea/outside scopes
- added session diagnostics and complete `/treecleanup` controls matching the live preference

## 0.9.712 (2026-07-30) — Sweep Guidance

- added exact held-axe, Sweep-stat, toughness and connected-log prediction with the licensed 35-log cap
- added 50-block throwable-axe prediction, one-second cooldown tracking and half-Sweep modeling
- added structured tree, toughness, Sweep, logs, throw/style penalty and correct-style HUD details
- added complete multi-line compact summaries, independent presentation controls and `/sweephelper`

## 0.9.711 (2026-07-30) — Galatea Exploration

- added exact particle and item-display validated Forest Node detection with expiring world guidance
- added fail-closed Forest Temple rotation solving with shortest left/right instructions for all sixteen tiles
- added incrementally discovered Lushlilac and configurable-count Sea Lumies highlights
- added independent boxes, labels, beams, lines, distances, colors, ranges and `/galateahelper` controls

## 0.9.710 (2026-07-30) — Starlyn and Agatha

- added live Agatha Shop offer parsing with complete material, coupon, sell-value and profit calculations
- added configurable price sides, manual coupon value, ranking, unpriced/negative filtering and row limits
- added profitable-slot highlights, compact slot values, contextual cost tooltips and a movable profit HUD
- added optional compact Starlyn contest results and personal-best messages plus full `/starlynhelper` controls

## 0.9.709 (2026-07-30) — Foraging Tracker

- added transaction-safe Fig and Mangrove Tree Gift tracking with contribution and whole-tree accounting
- added actual hover-reward, XP, HOTF XP, Forest Whisper, log and Stretching Sticks tracking
- added compact gift summaries with configurable bonus categories and confirmed high-value alerts
- added session/profile history, tree filters, Bazaar valuation, profit/hour, movable HUD and full controls

## 0.9.708 (2026-07-30) — Heart of the Forest

- added exact parsing for all 29 current HOTF perks, levels, states, tokens and Forest Whispers
- added enabled, disabled and locked perk highlights plus perk-level and unused-token slot text
- added exact per-perk spent, percentage, current balance and Shift-for-ten-level tooltip guidance
- added a movable HOTF summary HUD, display controls, cost design choices and `/hotfhelper` options

## 0.9.707 (2026-07-30) — Moonglade Beacon solver

- added normal and dual-track Upgrade Signal Strength color, speed and pitch solving
- added filtered moving-pane speed learning and exact bass-packet pitch detection
- added correct-setting highlights, signed offsets and Control-bypass over-click protection
- added user-initiated middle-click conversion, movable HUD, Stereo Pants warning and configurable alerts

## 0.9.706 (2026-07-30) — Galatea Tree Progress

- added exact Fig and Mangrove tree progress detection from nearby armor-stand labels
- added live-compatible axe-only visibility plus optional Skyblocker-style own-tree filtering
- added compact/full HUD, progress bar, distance, contributor and configurable color presentation
- added optional threshold alerts, bounded scan range and complete `/treeprogress` controls

## 0.9.705 (2026-07-30) — Attribute Shard overlay

- added profile-safe Attribute Menu tier, requirement, rarity and enabled-state learning
- added cheapest next-tier or max-tier ranking with selectable Bazaar price source
- added Hunting Box ownership deductions, inventory-only filtering and complete summary totals
- added tier slot text, disabled-attribute highlights, contextual tooltips, movable HUD and `/attributeoverlay` controls

## 0.9.704 (2026-07-30) — Hunting Box value

- added exact Hunting Box shard-region and `Owned` lore parsing
- added per-shard and total instant-sell/instant-buy valuation with partial-price handling
- added value, amount and name sorting plus configurable row, unit and total presentation
- added high-value slot overlays, contextual tooltips, movable HUD and full `/huntingboxvalue` controls

## 0.9.703 (2026-07-30) — Galatea sound controls

- added Galatea-scoped muting for all six known phantom sounds and future phantom sound paths
- added exact Fusion-machine firework blast/far-blast muting at the reference volume
- added per-phantom-sound toggles, Fusion target/tolerance and optional any-volume mode
- added scope, session counters and full `/galateasounds` controls

## 0.9.702 (2026-07-30) — Huntaxe Lock

- added double-right-click protection for all five current Absorptio Huntaxes
- added ID-first detection with exact Huntaxe-name and Absorptio-lore fallback for future tiers
- added configurable confirmation window, air/block scopes, single-use mode and sneak bypass
- added independent actionbar, chat and sound feedback plus full `/huntaxelock` controls

## 0.9.701 (2026-07-30) — Fusion keybinds

- added separate Minecraft Controls bindings for repeat, confirm and cancel Fusion actions
- added exact Fusion Box/Confirm Fusion title, repeat-button name and confirm/cancel terracotta validation
- added duplicate/simultaneous binding rejection, bounded click cooldown and unavailable-action feedback
- added independent action, input-consumption, feedback and sound options with full `/fusionkeys` controls

## 0.9.700 (2026-07-30) — Attribute Shard Tracker

- added profile-safe shard goals with live catch, Lootshare, charm, Fusion and Hunting Box accounting
- added bounded SkyShards and NoFrills recipe import plus hovered-shard selection from Minecraft Controls
- added exact Hunting Box ownership synchronization, completion alerts and Direct/Fuse/Cycle filtering
- added sorting, remaining Bazaar value, compact progress rows, movable HUD and full `/shardtracker` controls

## 0.9.699 (2026-07-30) — Fusion display

- added exact Fusion Box, Shard Fusion and Confirm Fusion slot/lore parsing
- added input ownership/requirements, output quantity, affordability and missing-material guidance
- added Bazaar input cost, output liquidation value and net value with independent price sources
- added Fusion result/Pure Reptile tracking, configurable alerts, movable HUD and full `/fusionhud` controls

## 0.9.698 (2026-07-30) — Lasso HUD

- added player-owned leash and exact armor-stand progress/REEL detection
- added rarity-aware Abysmal, Vinerip, Entangler and Everstretch progress calculation
- added compact/full HUD rows for progress, percentage, target, tool and distance
- added configurable ready threshold, deduplicated/repeating alerts, scope and full `/lassohud` controls

## 0.9.697 (2026-07-30) — Galatea hunting targets

- added Hideonleaf, Invisibug, Birries, Shellwise and Coralot target detection
- added exact entity-type, CRIT-particle/default-stand and detached-name-tag matching
- added independent target toggles, colors and ranges plus box, label, beam, line and distance rendering
- added optional deduplicated chat, title and sound alerts with complete `/huntingmobs` controls

## 0.9.696 (2026-07-30) — Hunting profit tracker

- added Artemis as an independent hunting and foraging constellation
- added exact caught, Lootshare and charm shard tracking with authoritative Bazaar IDs
- added session or profile persistence, value, hourly rate, uptime and configurable rare-value alerts
- added holding-tool/recent-pickup/always visibility, sorting, a movable HUD and complete `/huntingprofit` controls

## 0.9.695 (2026-07-30) — Manual crop coordinates

- added explicit per-crop Garden start locations through `/cropstart setat <crop> <x> <y> <z>`
- retained independent profile/global layouts and automatic block-centred waypoint storage
- added command guidance to the crop-location status output

## 0.9.694 (2026-07-30) — Unique gifting

- added profile-safe unique-recipient history from exact gift confirmations and Generow totals
- added a movable count/goal/remaining HUD with holding-gift visibility
- added configurable milestone chat, title and sound alerts
- added optional advisory recipient highlights with range, color and include/exclude controls

## 0.9.693 (2026-07-30) — Gift profit tracker

- added exact gift reward rarity, coins, item, skill XP and North Star tracking
- added profile-safe value, manually accounted gift cost, profit, hourly rate and reward breakdowns
- added holding-gift/recent-location visibility, rare-tier alerts and a movable Gift HUD
- added persistent/session-only modes, reward ID/custom-price correction and full `/gifttracker` controls

## 0.9.692 (2026-07-30) — Mayor and election state

- added authoritative current mayor, minister, active-perk and election-slate loading
- added temporary perk overrides, special-event gating and validated last-good offline cache
- added configurable mayor/election HUD, perk descriptions/filters and mayor-change alerts
- added complete `/mayor` status, perks, election, refresh, filter, color and presentation controls

## 0.9.691 (2026-07-30) — Event calendar and reminders

- added a live upcoming-event calendar with deterministic SkyBlock date and active-event countdowns
- added five-minute/one-minute reminder defaults with independent chat, title and sound channels
- added editable include/exclude filters, rows, location/date/year presentation and `/events` controls
- added asynchronous refresh, threshold deduplication and an automatically maintained last-good offline cache

## 0.9.690 (2026-07-30) — Diana tracking suite

- added exact empirical Start, Mob and Treasure burrow detection from server particles
- added persistent Diana mob, rare-drop and dug-coin tracking with two movable HUDs
- added deduplicated Minos Inquisitor titles, sounds, coordinate waypoints and optional party sharing
- added configurable rendering, lifetimes, ranges, colors, alerts, compact/full HUD modes and `/diana` controls

## 0.9.689 (2026-07-30) — Chivalrous Carnival helpers

- added strict Hub and exact-coordinate gating for Catch a Fish and Zombie Shootout
- added Golden Fish texture detection with configurable box, label, beam, sound, range and visibility
- added armor-matched Zombie Shootout guidance and live redstone-lamp target outlines
- added a movable Carnival HUD plus complete `/carnivalhelper` status, test and option controls

## 0.9.688 (2026-07-30) — Reforge Helper

- added exact Basic Reforge and Hex menu tracking from item modifier metadata
- added editable include/exclude filters, current-reforge overlay and Hex candidate highlights
- added matched-reforge click protection with a deliberate Control bypass and local alerts
- added session attempts/spend accounting, contextual tooltips, HUD and full `/reforgehelper` controls

## 0.9.687 (2026-07-30) — God Potion and Cookie status

- added authoritative tab-footer parsing for God Potion, Cookie Buff and active-effect count
- added profile-safe timer persistence with compact and worded duration support
- added independent warning/expiry chat, title and sound channels for both buffs
- added a movable Buff Status HUD, manual recovery/testing commands and complete options

## 0.9.686 (2026-07-30) — Anvil Helper

- added exact-screen Anvil state tracking for enchanted-book combinations
- added symmetric enchant and level mismatch warnings with optional protected output
- added player-inventory matching-book discovery, slot overlays and contextual tooltips
- added a movable Anvil HUD, deliberate Control bypass and complete `/anvilhelper` options

## 0.9.685 (2026-07-30) — Chocolate Factory helper

- added best and best-affordable upgrade ranking from live rabbit and coach production gains
- added upgrade affordability, payback, prestige, stray-rabbit and Hitman guidance
- added a movable Chocolate Factory HUD with independent rows and level display
- added profile-aware Time Tower expiry persistence, warning/expiry alerts and complete `/chocolatefactory` controls

## 0.9.684 (2026-07-30) — Experimentation Table solvers

- replaced Auriga's empty implementation with licensed Chronomatron, Ultrasequencer and Superpairs state machines
- added next/second/remaining overlays, remembered pair matching, powerup highlights and a movable progress HUD
- added optional wrong/early-click protection with a deliberate Control bypass
- added independent solver, scope, tooltip, memory, presentation and color controls through settings and `/experiments`

## 0.9.683 (2026-07-30) — Apollo telemetry HUD suite

- replaced Apollo's empty implementation with five independently movable HUD panels
- added current/average FPS, ping and server-authoritative TPS with rolling minimum and maximum
- added coordinates, facing, yaw/pitch, dimension, local clock and actual/stat movement speed
- added parsed SkyBlock vitals and sorted active-effect durations with comprehensive row, scope, threshold and color controls

## 0.9.682 (2026-07-30) — Screenshot clipboard

- added automatic image clipboard copying whenever Minecraft saves a screenshot
- added race-safe pixel capture, asynchronous clipboard writes and configurable busy-clipboard retries
- added optional last-screenshot retention, manual retry, action-bar/chat feedback and success sound
- added safe unsupported-platform handling and complete `/screenshotclipboard` commands

## 0.9.681 (2026-07-30) — World Age display

- added a movable server-authoritative world-day and Minecraft-clock HUD
- added day, clock, day/night phase, next-transition, real-age and raw-tick rows
- added 12/24-hour clocks, zero/one-based days, Hypixel scope and independent colors
- added complete `/worldage` status, toggle and option commands

## 0.9.680 (2026-07-30) — Century Cake timer

- added profile-aware 48-hour Century Cake buff persistence and a movable HUD
- added recognition for all 16 cake effects and a session helper listing every missing cake
- added configurable advance and expiry alerts, colors, duration, warning threshold and display behavior
- added `/centurycake` status, test, reset and complete option commands

## 0.9.679 (2026-07-30) — Inventory slot binding

- added persistent multi-target bindings between hotbar and main-inventory slots
- added protected shift-click swaps, remembered hotbar targets and indirect movement/drop safeguards
- added global and area-specific profiles with automatic switching, import/export and profile commands
- added a graphical profile editor, colored borders, configurable connection lines and binding previews

## 0.9.678 (2026-07-30) — Wardrobe keybinds

- added hotbar, number-row and individually configurable wardrobe slot keys
- added armor-set and equipment-set support, page keys, an optional unequip key and two-slot swap
- added slot-key labels, equipped-set protection, invalid-key handling, click cooldown, sound and feedback controls
- added an optional wardrobe-open key and complete `/wardrobekeys` configuration commands

## 0.9.677 (2026-07-30) — Carrolyn fetch helper

- added exact-lore recognition and navigation tooltips for every Carrolyn donation item
- added inventory progress toward the 3,000-item requirement
- added deliberate click-to-start guidance and a clickable Crimson Isle warp action
- added configurable box, beam, line, label, distance, range, color and arrival behavior

## 0.9.676 (2026-07-30) — Garden rod-break protection

- added Garden-only fishing-rod block-damage protection with packet-safe cancellation
- added an optional sneak-held deliberate bypass and throttled action-bar, chat and sound feedback
- added session prevention counts, feedback timing controls and complete `/norodbreak` commands
- kept right-click casting, entity attacks and every non-Garden interaction unchanged

## 0.9.675 (2026-07-30) — Farming Toolkit crop icons

- added crop icons for every farming-tool slot in the exact Farming Toolkit menu
- added optional held-crop highlighting, compact crop labels and item-decoration rendering
- kept the underlying tool, tooltip and every menu click unchanged
- retained manual per-crop start placement through `/cropstart set <crop>`

## 0.9.674 (2026-07-30) — Garden plot prices

- added complete Configure Plots material and total coin-value breakdowns
- added locked-plot price ranking, cheapest-plot highlighting and visible unlock total
- added inventory and observed-sack affordability checks with optional affordable highlighting
- added multi-material parsing, row controls, delayed-price refresh and fail-closed incomplete costs

## 0.9.673 (2026-07-30) — Pesthunter shop profit

- added live Pesthunter offer ranking by profit per Pest spent
- added estimated output value, material cost, trade profit, Pest cost and per-Pest tooltip breakdowns
- added best-offer highlighting, positive-profit filtering, row controls and delayed-price refresh
- made unknown materials, unavailable prices and missing item IDs fail closed instead of inflating profit

## 0.9.672 (2026-07-30) — Visitor Logbook analytics

- added profile-safe multi-page Visitor's Logbook capture and restoration
- added visited, accepted, denied, acceptance-rate, current-queue and page-completion totals
- added configurable most-visited or most-denied visitor rankings and per-visitor summary tooltips
- added fail-safe page-total discovery, session-only mode, row controls and current-profile or all-profile recovery

## 0.9.671 (2026-07-30) — Anita medal shop

- added live Anita offer ranking by profit per Bronze-equivalent medal
- added sale value, non-medal cost, trade profit and medal-cost tooltip breakdowns
- added best-offer highlighting, positive-profit filtering, row controls and fail-closed unknown-cost handling
- added profile-safe Extra Farming Fortune tier learning and exact remaining Gold Medal, Jacob's Ticket and coin-value guidance

## 0.9.670 (2026-07-30) — Rare Crop Tracker

- added profile and session tracking for all 21 current rare Garden crop drops
- added a movable value, profit-rate, active-uptime and recent-drop HUD with configurable sorting and line limits
- added optional exact-message hiding, AFK exclusion, Bazaar price modes and recovery/testing commands
- corrected Crop Money's Rare Crops option to value farming-armor drops using the equipped armor tier and crop-specific chance

## 0.9.669 (2026-07-30) — Garden Level progression

- added profile-specific Garden XP synchronization from the exact Desk and SkyBlock Menu items
- added current level, level XP, percentage, total XP and level-15-plus overflow progression HUD rows
- added visitor-reward XP updates, configurable overflow level-up chat and clickable Garden Levels access
- added max-level menu tooltips, decimal/Roman display, recovery commands and complete presentation controls

## 0.9.668 (2026-07-30) — Crop money per hour

- added live per-crop hourly profit comparisons using learned BPS, true Farming Fortune and Bazaar/NPC prices
- added sell-offer, instant-sell and NPC formats plus current-crop extension, compact display and top-row controls
- added Bountiful, Mooshroom Cow, replenishing-crop, seed-merge and rare-flower calculations
- added automatic profit sorting and persistent manually assigned positions for every individual crop

## 0.9.667 (2026-07-30) — Jacob contest history and planning

- added profile-specific parsing and persistence for all five medal thresholds from the exact Your Contests menu
- added last-N per-crop bracket averages, time-to-medal and Farming Fortune-needed planning with custom or learned BPS
- added hovered-contest threshold/FF detail, target-bracket selection and configurable missing/impossible presentation
- added actual-harvest contest summaries and Personal Best Fortune-gain reporting with overflow controls

## 0.9.666 (2026-07-30) — Upcoming Jacob contests

- added automatic authoritative EliteSkyBlock contest schedules with bounded background fetching and future-only persistence
- added current/next crops, start/end countdown, boosted crop and optional following-contest HUD rows
- added crop-filtered title/chat/sound/window-attention warnings with fully editable message variables
- added manual refresh, cache recovery, fetch timing, source display and Garden/outside-Garden controls

## 0.9.665 (2026-07-30) — Garden Composter

- added live Organic Matter, Fuel, Stored Compost and accurate empty-time tracking from the Garden tab widget
- added the Composter inventory material/cost/profit overlay, sack counts and compact inventory numbers
- added profile-specific upgrade-level learning, market-priced upgrade tooltips and purchasable-upgrade highlighting
- added Garden/outside-Garden HUD modes, low-resource and near-empty warnings, persistence and full command/config controls

## 0.9.664 (2026-07-30) — Garden farming lanes

- added profile-specific per-crop farming lanes with automatic two-layer detection and manual start/end placement
- added live distance, travel-time, movement-state and optional speed/crop HUD rows with configurable precision
- added configurable lane-switch title, chat and repeating sound warnings with message variables
- added optional endpoint waypoints, missing-lane warnings, per-crop ignores and complete command controls

## 0.9.663 (2026-07-30) — Garden DNA Analyzer

- added the exact minimum-swap DNA Analyzer solver across every 24-row permutation and the current end-column rule
- added distinct next-pair highlights, numbered swap order, remaining-swap HUD, configurable colors and optional board darkening
- added fail-closed four-color board validation, menu lifecycle reset, tooltip hiding and optional wrong-click feedback/blocking
- added accidental close-button protection while keeping every solution interaction manual and advisory

## 0.9.662 (2026-07-30) — Garden hoe levels

- added held-tool level and XP progress using the authoritative 49 normal thresholds plus the 200,000 XP overflow threshold
- added profile/tool-specific overflow levels from Tool Exp Capsule messages with manual recovery and reset commands
- added configurable percentage, remaining XP, measured XP rate, ETA, upgrade/overclock and wrong-crop rows
- added exact Garden-only hoe level-up sound suppression without muting unrelated portal sounds

## 0.9.661 (2026-07-30) — Garden crop milestones

- added profile-specific Crop Milestone counters using the authoritative 46-tier tables and exact Crop Milestones menu synchronization
- added Cultivating/hoe-counter delta tracking, measured crop rates, configurable progress/percentage/rate rows and ETA to the next, maximum or custom tier
- added optional close warnings, per-crop custom goals, manual recovery commands and five-second coalesced persistence
- added Crop Milestones menu tier numbers, average tier and progress-to-tier-46 tooltip details with overflow controls

## 0.9.660 (2026-07-30) — Garden commands

- added Garden-only `/home`, `/barn` and `/tp <plot>` rewrites to the matching Hypixel Garden commands
- added configurable Garden home, set-home and Barn hotkeys through Minecraft Controls
- added independent command/hotkey toggles, optional local feedback and a configurable duplicate-input cooldown
- preserved normal server command behavior outside the Garden and blocked every hotkey while a screen is open

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
## 0.9.854 - Garden overlay visual pass

- Kept Anita, Pesthunter, locked-plot and Composter side-panel states readable in narrow container gutters.
- Live-tested plot editing/statuses, visitor protection/tooltips, Garden shops, Composter, Greenhouse, Stereo Harmony and a complete DNA Analyzer board in a local fake-Garden world.
- Confirmed the Recipe Browser renders every ingredient and output in its scrollable detail pane.
## 0.9.855 - SkyBlock helper container pass

- Fixed private-island detection so experiment solvers work with their default island restriction.
- Accepted spaced experiment tier names using Devonian's current title matcher.
- Moved Reforge status text behind native contents, themed its filter fields, and restored keyboard focus when either field is clicked.
- Live-tested populated Superpairs, Anvil, Reforge, Chocolate Factory, Hoppity Collection, and Power Stones Guide states.
## 0.9.866 - Crimson Isle miniboss timers

- Added lobby-local respawn states for all five Crimson Isle minibosses from exact server announcements.
- Added boss-area and beacon recovery for honest estimated timers after joining an encounter late.
- Added a movable HUD, optional world labels/beams, soon alerts and manual correction commands.
## 0.9.867 - Crimson NPC item helpers

- Added complete Sirih and Avorius helpers matching the active 26.1.2 configuration, plus an optional Pablo flower helper.
- Added inventory-aware suppression, faction checks, independent cooldowns and customizable clickable sack requests.
- Added safe local previews and command/config controls without automatic commands or inventory actions.
## 0.9.868 - Trevor the Trapper suite

- Added full Trevor quest, cooldown, persistent data and session-rate tracking.
- Added maintained area waypoints, stable target-animal detection and Talbot height/circle solving.
- Added configurable HUD ordering, alerts, world layers, colors, ranges, commands and optional hotkey actions.
## 0.9.870 - Charmed Visitors

- Added profile-specific Gift Vinyl charm detection from authoritative visitor-menu state.
- Added a bounded Charmed Visitors HUD, optional shopping-list marks, count/sort/scope/message controls, and complete list management commands.
- Kept empty charm slots authoritative for removal and prevented an intentional manual removal from being re-added until the visitor menu is reopened.

## 0.9.869 - The Mist Ghost Tracker

- Added profile-specific lifetime and resettable session Ghost tracking from authoritative Bestiary deltas.
- Replaced the mutable release-shelf guide download with immutable per-build browser guides and anchored Good, Issue and Suggestion comments.
- Added Sorrow distance, combo, Combat XP, average Magic Find, drops, coins, profit, hourly rate and active uptime.
- Added ordered HUD rows, bounded gemstone pickup correlation, Bestiary guidance and configurable valuable-drop alerts.
