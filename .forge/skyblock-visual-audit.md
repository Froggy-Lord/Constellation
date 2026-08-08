# SkyBlock visual completion audit

This is the release gate for Zaden's SkyBlock visual pass. Do not send the completion email until every row is closed with a real-client capture or a justified live-state inspection. Vanilla workstation and general Minecraft menus are outside this milestone.

## Shared HUD system

- [x] 99 `ThemedHudWidget` panels use the compact translucent Dross Pickles HUD structure, one-pixel outline and Constellation palette.
- [x] HUD editor is only a 12.5% black game overlay plus real HUD previews, with no editor chrome.
- [x] HUD editor exposes only currently visible elements or elements seen in the preceding five seconds.
- [x] Every exposed HUD supports drag positioning and pointer-wheel scaling from 0.5x to 3.0x.
- [x] Custom scoreboard retains its purpose-built configurable SkyBlock layout.
- [x] Dungeon map retains its purpose-built room-map renderer.
- [x] Pet display retains its purpose-built item-icon-first layout.
- [x] Mirrorverse Dance uses the same compact translucent Dross Pickles panel treatment.
- [ ] Capture representative compact, long-row, multi-row, icon-bearing, scoreboard, map, pet and Mirrorverse HUDs at multiple GUI scales.
- [ ] Check simultaneous HUDs for readable spacing and document recommended default positions where collisions remain.

## Full custom screens

- [x] Hub and main configuration screens use the Constellation space identity and shared controls.
- [x] Advanced configuration has search, grouped values and empty/error-safe rendering.
- [x] SkyBlock Craft Item preserves server slots with dedicated grid/result/quick-craft stages.
- [x] Market Search has loading, empty, error, keyboard, scroll and filter states with live screenshots.
- [x] Recipe Browser has loading, updating, empty, error, search/filter, complete-detail and optional REI states with live screenshots.
- [x] Storage Browser has empty, populated, search, rename and retained-state screenshots.
- [x] Profile Viewer has loading, empty/error recovery and real profile sections.
- [x] Dungeon records, carry tracking, party guard, party messages and Spirit Leap settings use shared themed surfaces.
- [x] Inventory Button, slot binding, scoreboard order, refill and speed preset editors use shared themed controls.
- [x] Tunnel Map and terminal simulator retain purpose-built interactive diagrams.
- [ ] Re-open every screen above on the current build at 1280x720 and a narrow window; capture any screen changed since its last release screenshot.
- [ ] Verify every modal, tooltip, scroll boundary, keyboard focus and close/back path in the current build.

## Container-attached SkyBlock overlays

- [ ] Audit Auction, Bazaar, accessory, storage-value, inventory-search, inventory-button and slot-text overlays together on representative server-shaped containers.
- [ ] Audit Garden plot, visitor, Anita, Pesthunter, Composter, Greenhouse, DNA Analyzer and Stereo overlays.
- [ ] Audit experiments, anvil/reforge helpers, Chocolate Factory, Hoppity and SkyBlock Guide overlays.
- [ ] Audit dungeon chest profit, Croesus, Party Finder, terminal and Spirit Leap overlays.
- [ ] Audit Rift price/storage/guide and fishing overlays.
- [ ] Verify layer order: vanilla/SkyBlock items and tooltips must remain above decorative surfaces, while click targets remain authoritative.

## World and event overlays

- [ ] Capture representative waypoint labels, boxes, beams, paths and solver lines with through-walls both enabled and disabled where configurable.
- [ ] Check every puzzle solver is room-gated and never renders outside its matching room.
- [ ] Check title, boss-bar, action-bar, scoreboard and chat replacements for clipping, duplication and readable fallback.
- [ ] Check reduced visual-noise/end-of-run presentation and particle substitutions in a safe local reproduction or stationary Hypixel test.

## Final gate

- [ ] Forbidden-source grep is empty for every touched implementation and user document.
- [ ] Build reports exactly 11 successful and zero failed tests.
- [ ] Clean boot reaches expected timeout with 138 rooms, 14 constellations and no mixin/crash/fatal marker.
- [ ] Gather jar, private shelf and drip queue contain the same final version.
- [ ] `CODEX_HANDOFF.md`, overview and testing guide match the final visual behavior.
- [ ] Send the completion email as Rowan Vale using the Studios self-delivery route.
