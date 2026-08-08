# SkyBlock visual completion audit

This is the release gate for Zaden's SkyBlock visual pass. Do not send the completion email until every row is closed with a real-client capture or a justified live-state inspection. Vanilla workstation and general Minecraft menus are outside this milestone.

## Shared HUD system

- [x] 99 `ThemedHudWidget` panels use the compact translucent Dross Pickles HUD structure, one-pixel outline and Constellation palette.
- [x] HUD editor is only a 12.5% black game overlay plus real HUD previews, with no editor chrome or inherited Minecraft blur (`/tmp/hud-editor-no-blur-823.png`).
- [x] HUD editor exposes only currently visible elements or elements seen in the preceding five seconds.
- [x] Every exposed HUD supports drag positioning and pointer-wheel scaling from 0.5x to 3.0x; live wheel input resized only the hovered Performance HUD (`/tmp/hud-editor-wheel-up-823.png`).
- [x] Custom scoreboard retains its purpose-built configurable SkyBlock layout.
- [x] Dungeon map retains its purpose-built room-map renderer.
- [x] Pet display retains its purpose-built item-icon-first layout.
- [x] Mirrorverse Dance uses the same compact translucent Dross Pickles panel treatment.
- [ ] Capture representative compact, long-row, multi-row, icon-bearing, scoreboard, map, pet and Mirrorverse HUDs at multiple GUI scales.
- [x] Check simultaneous default Apollo HUDs for readable spacing; Performance, Location and Movement use the verified 2/38/70 percent stack (`/tmp/hud-defaults-823.png`). Saved user positions remain authoritative.

## Full custom screens

- [x] Hub and main configuration screens use the Constellation space identity and shared controls; the corrected footer leaves a clear gap above its keyboard hint (`/tmp/hub-footer-fixed-823.png`).
- [x] Advanced configuration has search, grouped values and empty/error-safe rendering.
- [x] SkyBlock Craft Item preserves server slots with dedicated grid/result/quick-craft stages.
- [x] Market Search has loading, empty, error, keyboard, scroll and filter states with live screenshots.
- [x] Recipe Browser has loading, updating, empty, error, search/filter, complete-detail and optional REI states with live screenshots.
- [x] Storage Browser has empty, populated, search, rename and retained-state screenshots.
- [x] Profile Viewer has loading, empty/error recovery and real profile sections.
- [x] Dungeon records, carry tracking, party guard, party messages and Spirit Leap settings use shared themed surfaces.
- [x] Inventory Button, slot binding, scoreboard order, refill and speed preset editors use shared themed controls.
- [x] Tunnel Map and terminal simulator retain purpose-built interactive diagrams.
- [ ] Re-open every screen above on the current build at 1280x720 and a narrow window; capture any screen changed since its last release screenshot. Recipe Browser current wide/narrow layout and long Info scroll are proven (`/tmp/recipes-current3-848.png`, `/tmp/recipes-narrow-848.png`, `/tmp/recipes-info-narrow-848.png`, `/tmp/recipes-info-narrow-scroll-848.png`).
- [ ] Verify every modal, tooltip, scroll boundary, keyboard focus and close/back path in the current build.

## Container-attached SkyBlock overlays

- [x] Audit Auction, Bazaar, accessory, storage-value, inventory-search, inventory-button and slot-text overlays together on representative server-shaped containers. A real local chest under a fake `SKYBLOCK` scoreboard proves Inventory Buttons, native item tooltip ordering, Storage Browse click authority, and the manual Storage Value panel (`/tmp/inventory-buttons-tooltip-layer-849.png`, `/tmp/inventory-native-tooltip-layer-849.png`, `/tmp/storage-exact-layer2-849.png`, `/tmp/storage-native-tooltip-layer-849.png`, `/tmp/storage-browse-click-layer-849.png`, `/tmp/value-panel-layer-849.png`). `/tmp/inventory-search-themed-852.png` proves themed search plus match/dim layers, `/tmp/inventory-search-escape-852.png` proves first-Escape dismissal, `/tmp/accessory-page1-852.png` and `/tmp/accessory-page-next-852.png` prove populated responsive Accessory Helper paging/hover, and those captures also prove cake-year/enchantment-level slot text. `/tmp/auction-markers-852.png` and `/tmp/auction-tooltip-852.png` prove sold/expired Auction tint and tooltip order. `/tmp/bazaar-markers-readable-853.png`, `/tmp/bazaar-tooltip-readable-853.png` and `/tmp/bazaar-outbid-matched-853.png` prove all six Bazaar status badges, real market comparison and tooltip order.
- [x] Audit Garden plot, visitor, Anita, Pesthunter, Composter, Greenhouse, DNA Analyzer and Stereo overlays. A local 54-slot chest under fake `SKYBLOCK` and `Garden` sidebar data proves full plot status/editor rendering (`/tmp/garden-plots-full-854.png`), visitor accept/refuse protection and appended native tooltip ordering (`/tmp/garden-visitor-tooltip-854.png`), Anita/Pesthunter side panels (`/tmp/garden-anita-fixed-854.png`, `/tmp/garden-pesthunter-854.png`), compact Composter loading state (`/tmp/garden-composter-fixed3-854.png`), Greenhouse water/harvest/reward highlights (`/tmp/garden-greenhouse-854.png`), crop-icon Stereo playback state (`/tmp/garden-stereo-854.png`) and a parsed 9x4 DNA board with ordered next-swap markers (`/tmp/garden-dna-854.png`).
- [x] Audit experiments, anvil/reforge helpers, Chocolate Factory, Hoppity and SkyBlock Guide overlays. The local fake-SkyBlock 54-slot menu proves spaced-tier Superpairs recognition, known-pair highlighting and solver HUD (`/tmp/skyblock-superpairs-private-855.png`), populated Anvil mismatch highlighting plus native tooltip ordering (`/tmp/skyblock-anvil-mismatch-855.png`), themed Reforge filters and authoritative native tooltips (`/tmp/skyblock-reforge-themed-855.png`), ranked Chocolate Factory upgrades/stray/prestige states and calculated tooltip detail (`/tmp/skyblock-chocolate-factory-855.png`), Hoppity missing/found rarity states and scan warning (`/tmp/skyblock-hoppity-collection-855.png`), and Power Stones Guide price/action detail (`/tmp/skyblock-power-stone-guide-855.png`).
- [x] Audit dungeon chest profit, Croesus, Party Finder, terminal and Spirit Leap overlays. Paginated Croesus in a clean fake Dungeon Hub renders green/orange/red run tints and a complete two-line count panel without colliding with Inventory Search (`/tmp/skyblock-croesus-paginated-two-line-856.png`). Party Finder renders join/dupe/block slot marks, a compact complete summary and native tooltip detail with floor, missing roles, member data and asynchronous profile state (`/tmp/skyblock-party-finder-856.png`, `/tmp/skyblock-party-finder-tooltip-appended-856.png`). Dedicated inventory search/buttons are absent from terminal and leap screens, leaving Melody instructions clear (`/tmp/skyblock-terminal-melody-unobstructed-856.png`); panes are also exercised (`/tmp/skyblock-terminal-panes-856.png`). The populated Spirit Leap replacement shows living Mage/Tank/Archer and a dead Berserk card with role colours and no unrelated container controls (`/tmp/skyblock-spirit-leap-populated-final-856.png`).
- [x] Audit Rift price/storage/guide and fishing overlays. A fake-Rift local container proves Rift Storage high-value markers plus NPC Motes appended beneath the native tooltip (`/tmp/skyblock-rift-storage-857.png`, `/tmp/skyblock-rift-storage-tooltip-857.png`), missing/completed Rift Guide entries (`/tmp/skyblock-rift-guide-marked-857.png`), and Enigma Soul missing/found markers with native right-click route guidance (`/tmp/skyblock-enigma-souls-857.png`, `/tmp/skyblock-enigma-souls-tooltip-857.png`). A Fishing Bag reproduction shows Thunder, Storm and Hurricane Bottle progress at 50%, 75% and 100% without covering stack counts (`/tmp/skyblock-fishing-bottles-857.png`); the user's disabled progress setting was restored after capture.
- [x] Verify layer order: vanilla/SkyBlock items and tooltips must remain above decorative surfaces, while click targets remain authoritative. Source ordering is corrected for Accessory, Storage Value, Inventory Buttons and chest-profit side panels by drawing after the screen background but before native contents/tooltips; slot highlights and full replacement UIs intentionally retain their later phases. The current-build local chest captures above prove native and custom tooltips plus Browse/value click targets.

## World and event overlays

- [x] Capture representative waypoint labels, boxes, beams, paths and solver lines with through-walls both enabled and disabled where configurable. `/tmp/world-primitives-850.png` proves the complete primitive set in open view; `/tmp/world-primitives-occlusion-850.png` proves cyan through-wall highlight, beam, label and path remain visible behind a solid wall while the orange box and magenta depth-tested line are fully occluded.
- [x] Check every puzzle solver is room-gated and never renders outside its matching room; all room solvers match bundled skeleton stems, while F7 devices use exact floor, phase and coordinate gates (source audit, 0.9.825).
- [x] Check title, boss-bar, action-bar, scoreboard and chat replacements for clipping, duplication and readable fallback. Chat/action-bar separation, style retention, original hover/click retention and URL confirmation are proven on 0.9.848 (`/tmp/chatvisual-848.png`, `/tmp/chatvisual-actionbar-848.png`, `/tmp/chatvisual-coin-hover2-848.png`, `/tmp/chatvisual-url-confirm-848.png`). The current local encounter reproduction proves a bounded `S+ CLEAR` title (`/tmp/dungeon-ending-current-857d.png`), Kuudra's preserved name plus appended health (`/tmp/skyblock-kuudra-bossbar-clean-857.png`) and a complete fake-dungeon custom scoreboard (`/tmp/skyblock-hud-combined-clean-857.png`). The same pass removed redundant standalone scoreboard content and fixed every observed default HUD collision.
- [x] Check reduced visual-noise/end-of-run presentation and particle substitutions in a safe local reproduction or stationary Hypixel test. `/dungeonending test` proves the fitted custom title/subtitle (`/tmp/dungeon-ending-844-fitted.png`, `/tmp/dungeon-ending-current-857d.png`); the bounded active window independently suppresses subsequent server title/subtitle, particle and sound packets in `ClientPacketListenerMixin`, then restores ordinary presentation automatically. The completion sound remains local and configurable.

## Final gate

- [ ] Forbidden-source grep is empty for every touched implementation and user document.
- [ ] Build reports exactly 11 successful and zero failed tests.
- [ ] Clean boot reaches expected timeout with 138 rooms, 14 constellations and no mixin/crash/fatal marker.
- [ ] Gather jar, private shelf and drip queue contain the same final version.
- [ ] `CODEX_HANDOFF.md`, overview and testing guide match the final visual behavior.
- [ ] Send the completion email as Rowan Vale using the Studios self-delivery route.
