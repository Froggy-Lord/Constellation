# Constellation testing guide

You do not need to test everything in one sitting. Start with the five-minute check, then test one game-area session whenever you naturally play that area. Checkboxes are intentionally split into small groups.

## Before testing

1. Launch the `Constellation Gather 26.2` Prism instance.
2. On the title screen, open Mods and confirm Constellation is present.
3. Join Hypixel SkyBlock.
4. Run `/cn config`.
5. Enable only the constellation you are about to test. This avoids duplicate overlays from your other installed mods.
6. Search within that constellation for the named feature and enable its parent toggle first, then its child options.
7. Run `/cn hud` only while the relevant HUD is visible. Drag it where you want it and hover-scroll to resize it.
8. If something is wrong, run `/cn scrape all` in the affected area and keep the generated file from `config/constellation-scrapes/`.

## Five-minute smoke test

- [ ] Open `/cn config`. Expected: the screen opens without disconnecting or freezing.
- [ ] Toggle Apollo on, then off. Expected: its HUD appears and disappears without needing a restart.
- [ ] Open `/cn hud`. Expected: the game remains visible beneath a slightly opaque overlay; there are no decorative panels or borders.
- [ ] Hover a visible HUD and scroll. Expected: only that HUD resizes.
- [ ] Drag a visible HUD, close the editor, reopen it. Expected: position and scale persist.
- [ ] Run `/cn scrape all`. Expected: a local confirmation and a new diagnostic file.
- [ ] Change islands once. Expected: no stale world boxes, labels or timers remain from the previous island.

If all seven pass, the shared framework is healthy. Continue with whichever area you actually play.

## Garden commands: test this release first

### Enable

1. Enable Hercules and `Garden Commands`.
2. In Minecraft Controls, confirm `Garden Home` is Caps Lock, `Garden Set Home` is Left Alt and `Garden Barn` is unbound unless you choose a key.
3. Keep `/home`, `/barn`, `/tp <plot>` and their desired hotkey toggles enabled.

### Commands and hotkeys

- [ ] In the Garden, run `/home`. Expected: Hypixel receives `/warp garden` and returns you to the Garden spawn.
- [ ] Run `/barn`. Expected: Hypixel receives `/tptoplot barn`.
- [ ] Run `/tp 3`, then test a named plot if you use one. Expected: the complete text after `/tp` is sent through `/tptoplot`.
- [ ] Press Garden Home with no screen open. Expected: it sends `/warp garden` exactly once.
- [ ] Press Garden Set Home with no screen open. Expected: it sends `/setspawn` exactly once.
- [ ] Bind and press Garden Barn. Expected: it sends `/tptoplot barn` exactly once.
- [ ] Hold a hotkey, open chat/inventory while pressing it, and close the screen. Expected: no repeat and no delayed command after the screen closes.
- [ ] Leave the Garden and repeat the shortcuts. Expected: hotkeys do nothing and the normal server interpretation of typed commands is preserved.
- [ ] Disable each command and hotkey option separately. Expected: only that shortcut stops.
- [ ] Enable hotkey feedback and change the cooldown. Expected: deliberate key actions show local confirmation and rapid duplicate inputs respect the configured delay.
- [ ] Run `/gardencommands` and `/gardencommands option <name> <on|off>`. Expected: saved status and supported option names are shown without sending a gameplay command.

## Garden mouse sensitivity

### Enable

1. Enable Hercules and `Mouse Sensitivity Helper`.
2. In Minecraft Controls, bind `Garden Sensitivity` if N is unsuitable.
3. Keep auto-enable off for the first manual checks.

### Manual controls

- [ ] Run `/sensreduce`. Expected: camera movement becomes 10% of normal, the Mouse HUD shows `10%`, and Minecraft's sensitivity option does not change.
- [ ] Run `/sensreduce` again. Expected: normal camera movement returns and the HUD disappears.
- [ ] Run `/farmmouselock`. Expected: mouse movement cannot rotate the player, but movement keys and menus still work.
- [ ] Teleport to another Garden plot. Expected with release mode Always: rotation unlocks and a local status message appears.
- [ ] Press the Garden Sensitivity key. Expected: it toggles the configured reduced or locked state without repeating while held.

### Automatic controls

- [ ] Enable auto activation and Tool mode, then hold a crop-specific farming tool on a Garden plot. Expected: sensitivity lowers only while the tool and location conditions match.
- [ ] Walk into the Barn. Expected with Only Plot enabled: automatic reduction stops.
- [ ] Jump. Expected with On Ground enabled: automatic reduction stops while airborne and resumes within the configured ground tolerance.
- [ ] Test optional rod, vacuum, mousemat, Sprayonator and Sun's Grasp modes separately. Expected: only enabled held-item modes activate.
- [ ] Enable Lock Mouse. Expected: automatic modes lock rotation instead of reducing it.
- [ ] Change the percentage, ground tolerance, HUD, chat, mousemat lock and teleport release settings. Expected: each behaves independently and persists after restart.
- [ ] Leave the Garden. Expected: automatic state and its HUD disappear; a deliberate manual state remains until toggled, teleported or disconnected.

## Garden crop locations

### Enable

1. Enable Hercules and `Crop Location Helper`.
2. Keep mode `START`, auto-learn, start box, labels and per-profile storage enabled to match the live profile.
3. Hold a crop-specific farming tool in the Garden.

### Manual per-crop starts

- [ ] Stand at the intended beginning of a farm and run `/cropstart set`. Expected: the held tool’s crop receives a start point at your current block.
- [ ] Without changing tools, stand elsewhere and run `/cropstart set wheat`. Expected: Wheat receives that manually chosen location independently of the held tool.
- [ ] Repeat with compact multi-word crop names such as `/cropstart set netherwart`, `sugarcane`, `wildrose` and `cocoa`. Expected: each specific crop is accepted.
- [ ] Switch between those tools. Expected: only the held crop’s saved start waypoint is shown.
- [ ] Run `/cropstart clearstart wheat`. Expected: only Wheat’s manual start is removed; its last-farmed position and other crops remain.

### Learning and modes

- [ ] Clear one crop, hold its matching tool and break a valid crop block outside the Barn. Expected: its first valid local harvest automatically saves a start point once.
- [ ] Continue farming. Expected: the last-farmed point follows your position without repeatedly moving the original start.
- [ ] Stop and walk at least ten blocks away, then select Last mode. Expected: a purple last-farmed waypoint appears with a beam.
- [ ] Return to farming. Expected: the last waypoint hides while it is being continuously updated and reappears after you leave again.
- [ ] Select Both mode. Expected: start and last-farmed points display together with distinct labels and colors.
- [ ] Disable auto-learn. Expected: new start points are not created, but existing starts remain and last-farmed tracking still works.
- [ ] Enter the Barn or break a crop without its matching tool. Expected: neither action learns a crop location.

### Configuration and persistence

- [ ] Toggle start/last boxes, start/last beams, line, labels, distance and wall visibility independently. Expected: only the selected primitive changes.
- [ ] Change start/last colors, box size, beam height, render range and last-point activation distance through `/cropstart`. Expected: settings persist.
- [ ] Restart and switch profiles. Expected: locations survive restart and remain separate per profile.
- [ ] Disable per-profile storage. Expected: a separate global layout is selected without deleting profile layouts.
- [ ] Run `clear`, `clearstart`, `clearlast`, `clearall` and `clearprofiles` deliberately. Expected: only the documented crop/scope is removed.
- [ ] Leave the Garden. Expected: every crop-location waypoint disappears immediately.

## Garden custom plot icons: test this release first

### Enable

1. Enable Hercules, `Plot Icons`, `Plot Icon Editor Button` and tooltip help.
2. In the Garden, open the Desk and then `Configure Plots`.
3. Look at the bottom-right chest slot. Expected: a wooden axe marked `OFF` appears without replacing the real server item.

### Edit and persistence

- [ ] Left-click the editor axe once. Expected: it changes to Set mode, the click does not reach Hypixel and the tooltip explains the workflow.
- [ ] Click a non-empty item in your player inventory. Expected: the inventory item does not move and local chat confirms it was selected.
- [ ] Click any unlocked non-Barn plot slot. Expected: its visual icon changes to an exact copy of the selected item, including custom model data.
- [ ] Hover the changed plot. Expected: Hypixel's original plot name and lore remain intact, followed by a short custom-icon note.
- [ ] Click the plot normally after returning the editor to Off. Expected: the original plot action works and Constellation does not block or duplicate the click.
- [ ] Close and reopen Configure Plots. Expected: the custom icon persists while the editor safely returns to Off with no pending item.
- [ ] Restart the client and reopen the menu. Expected: the icon still renders from its saved exact-stack data.
- [ ] Right-click the editor from Off. Expected: it enters Reset mode directly.
- [ ] Click a customized plot in Reset mode. Expected: the original plot icon returns and the server receives no edit-mode click.
- [ ] Switch SkyBlock profiles with per-profile mode enabled. Expected: each profile has an independent icon layout.
- [ ] Disable per-profile mode. Expected: a separate global layout is used without deleting profile layouts.
- [ ] Toggle editor button, chat feedback and tooltip help independently. Expected: saved icons remain visible while only the selected editor assistance changes.
- [ ] Run `/ploticons`, `/ploticons option`, `/ploticons clear` and `/ploticons clearall`. Expected: status is accurate and the selected scope is cleared.
- [ ] Open any other inventory or leave the Garden. Expected: there is no editor axe, custom plot icon or click interception.

## Garden plot-menu status: test this release first

### Enable

1. Enable Hercules and `Plot Menu Highlighting`.
2. Keep current, pests, sprays and locked enabled; pasting is available but disabled by default to match the live profile.
3. Keep letters, counts and tooltip status enabled.
4. In the Garden, open the Desk and then `Configure Plots`.

### Status and priority

- [ ] Inspect the plot you are physically standing in. Expected: its slot has the configured current-plot tint and `C` marker.
- [ ] Open the menu while pests are known in one or more plots. Expected: affected slots use the pest tint and show `P` plus the tracked pest count.
- [ ] Open the menu with active Sprayonator effects. Expected: affected slots use the spray tint and show `S` plus rounded-up minutes remaining.
- [ ] Hover a highlighted plot. Expected: the tooltip states the selected status and active sprays include their type and precise remaining time.
- [ ] Inspect an unpurchased plot. Expected: its lore-derived locked status uses the locked tint and `L`.
- [ ] Enable pasting status while a plot paste is running. Expected: lore-derived pasting status uses its own tint and `PA`.
- [ ] Run `/plotmenu priority pests current sprays locked pasting`. Expected: a current plot with pests now displays the pest status because pests has higher priority.
- [ ] Toggle each status with `/plotmenu option <status> <on|off>`. Expected: only that status changes and all choices persist.
- [ ] Toggle letters, counts and tooltip independently. Expected: the tint remains while each selected detail disappears.
- [ ] Run `/plotmenu color pests FF00FF` and then an eight-digit ARGB value. Expected: the pest tint changes and six-digit values retain the default overlay opacity.
- [ ] Open another inventory or leave the Garden. Expected: no plot status tint or tooltip is drawn.
- [ ] Click plot slots normally. Expected: Constellation never blocks, changes or sends an extra click.

## Farming Fortune: test this release first

### Enable

1. Enable Hercules, `Fortune Helper` and `Fortune Display`.
2. Keep compact mode off, missing warnings visible and Pesthunter bonus display off to match the live profile.
3. In `/widget`, enable the Stats widget plus universal Farming Fortune and latest Crop Fortune.
4. Hold a supported farming tool in the Garden.

### Fortune state and warnings

- [ ] Compare the HUD total with universal plus current-crop Fortune in the Stats widget. Expected: the values match exactly.
- [ ] Switch between farming tools. Expected: the HUD follows the held crop and uses the last saved value until that crop's tab line updates.
- [ ] Break the selected crop. Expected: its fresh total is persisted for later tool switches and restarts.
- [ ] Enable breakdown mode. Expected: universal and crop-specific values appear separately beneath the total.
- [ ] Create four or more effective Garden pests. Expected: the configured reduction row follows the exact 5/15/30/50/75 percent thresholds.
- [ ] Temporarily hide universal Farming Fortune from the Stats widget. Expected: a delayed, repeat-limited warning offers a clickable `/widget` action.
- [ ] Restore universal Fortune but hide latest Crop Fortune, then farm. Expected: the separate crop warning appears after its configured delay.

### Pesthunter bonus

- [ ] Enable the bonus row and obtain a Pesthunter Farming Fortune bonus. Expected: amount and stable remaining time appear.
- [ ] Let it expire or observe `Bonus: INACTIVE`. Expected: enabled chat/title/sound channels fire once.
- [ ] Enable the clickable action. Expected: the chat button deliberately runs either `/call Phillip` or `/tptoplot barn`; nothing runs automatically.
- [ ] Run `/fortune` and its option, missing-delay, title-duration and template controls. Expected: all changes persist.

## Greenhouse growth

### Enable

1. Enable Hercules, `Greenhouse Helper`, `Greenhouse Growth`, harvestable highlighting and water highlighting.
2. Keep the countdown visible outside the Garden and ready chat/sound enabled, matching the live profile's always-available timer behavior.
3. Open the Greenhouse's exact `Crop Diagnostics` menu.

### Timer and diagnostic slots

- [ ] Inspect menu slot 20. Expected: `Next Stage` is parsed into a persistent countdown and the movable Greenhouse HUD appears.
- [ ] Reopen the menu while the timer runs. Expected: the deadline remains stable rather than drifting or repeatedly resetting notification state.
- [ ] Inspect Growth Status. Expected: harvestable is green, unavailable is red, and visible drops/rewards use yellow.
- [ ] Inspect Water Status. Expected: enough-water or no-water-needed lore makes the water bucket green.
- [ ] Put similar items in your player inventory. Expected: only server-menu diagnostic slots receive highlights.
- [ ] Let the cycle become ready. Expected: configured chat/title/sound channels fire once.
- [ ] Leave and return after it becomes ready. Expected: an optional while-away message appears once.
- [ ] Keep an overdue timer beyond `Greenhouse Forget After Minutes`. Expected: it hides and does not produce a stale alert.
- [ ] Toggle `Greenhouse Only When Ready`. Expected: the HUD hides until overdue.
- [ ] Run `/greenhouse` plus its option, warning, forget, title-duration, color and template controls. Expected: all changes persist.

## Stereo Harmony

### Enable

1. Enable Hercules, `Stereo Harmony`, `Stereo Display`, `Stereo Replace Menu Icons` and `Stereo Contest Helper`.
2. Keep `Stereo Always Show` and selection notifications disabled initially, matching the live profile.
3. Start breaking crops briefly so the farming-aware HUD is eligible to appear.

### Selection, display and menu

- [ ] Open Stereo Harmony. Expected: each vinyl entry is visually replaced by its associated crop while its original tooltip and click behavior remain unchanged.
- [ ] During a Jacob contest, inspect the matching crop's vinyl. Expected: the matching inactive entry is green; the playing entry is yellow.
- [ ] Select a vinyl. Expected: the HUD shows its exact vinyl, pest and crop after the menu or vacuum lore updates.
- [ ] Close the menu and keep the vacuum anywhere in your inventory. Expected: the active selection remains synchronized.
- [ ] Stop farming for longer than `farmingResetAfterSeconds`. Expected: the HUD hides unless `Stereo Always Show` is enabled.
- [ ] Select None. Expected: the HUD says `Playing: Nothing`, or hides when `Stereo Hide When None` is enabled.
- [ ] Toggle pest/crop rows independently. Expected: the selected rows disappear without losing active-vinyl state.
- [ ] Enable selection notification channels and change vinyl. Expected: one configurable chat/title/sound alert appears; initial login reconciliation does not alert.
- [ ] Run `/stereoharmony` and its option, color, template and title-duration commands. Expected: every setting persists.

## Garden plot sprays

### Enable

1. Enable Hercules and `Spray Tracker`.
2. Keep `Spray Expiry Notification`, `Spray Expiry Chat`, `Spray Show Not Sprayed` and `Spray Notify While Away` enabled.
3. The live-profile default leaves `Spray Hud` and `Spray New Notification` disabled. Enable them only for the HUD/new-spray checks.

### State and expiry

- [ ] Stand in a non-Barn Garden plot and check the Pests tab widget. Expected: its spray type and remaining time become the saved state for that physical plot.
- [ ] Use the Sprayonator. Expected: the exact plot and spray type are stored for 30 minutes without duplicating Hypixel's message.
- [ ] Enable `Spray Hud`. Expected: the movable HUD shows the current plot's spray and countdown, or `Not sprayed`.
- [ ] Disable `Spray Only Current Plot`. Expected: all known active sprayed plots are listed.
- [ ] Enable `Spray New Notification`, then enter a plot with a newly detected or substantially extended spray. Expected: one configurable local message appears, not one per tab refresh.
- [ ] Let a spray expire. Expected: the configured chat/title/sound channels fire once and include the affected plot names.
- [ ] Leave before expiry and return afterward. Expected: the message says it expired while away when that option is enabled.
- [ ] Use a Portable Washer. Expected: every stored active spray clears immediately.
- [ ] Run `/sprays`, `/sprays option`, `/sprays duration`, `/sprays warning`, and the template commands. Expected: settings persist and status reports the active count.

## Garden pest waypoint

### Enable

1. Run `/cn config`, enable Hercules, then enable `Pest Core` and `Pest Waypoint Enabled`.
2. Leave box, beam, label, distance, plot-middle detection, through-walls and arrival cleanup enabled.
3. Leave line and particle hiding disabled for the first test.
4. Warp to the Garden and hold a vacuum.
5. Run `/pestwaypoint status` if you need to confirm the saved state.

### Track and render

- [ ] Left-click once without sneaking while holding the vacuum. Expected: tracking starts, but no target appears until a valid angry-villager particle trail supplies enough points.
- [ ] Right-click, left-click while sneaking, or left-click with a non-vacuum item. Expected: none starts tracking.
- [ ] Follow a real pest-tracker trail. Expected: a red `Pest Guess` box, beam and distance label appear at the predicted endpoint.
- [ ] Track a trail ending exactly at a plot center. Expected: the marker is yellow and includes `(plot middle)`.
- [ ] Enable `pestWaypointLine`. Expected: a line reaches from the crosshair to the waypoint.
- [ ] Change the target and plot-middle colors in config or with `/pestwaypoint color target RRGGBB` and `/pestwaypoint color middle RRGGBB`. Expected: the marker updates.

### Cleanup and filtering

- [ ] Walk within `pestWaypointArrivalRange` horizontally after the first second. Expected: the waypoint clears.
- [ ] Do not approach it. Expected: it clears after `pestWaypointShowSeconds`.
- [ ] Let the Garden pest total reach zero, leave the Garden, disconnect, or change worlds. Expected: all collected points and the marker clear.
- [ ] Enable `pestWaypointHideParticles`. Expected: only the configured tracker firework, enchant and path particle groups are hidden; other particles remain.
- [ ] Run `/pestwaypoint clear`. Expected: the current path clears without changing settings.

## Garden pest core

### Enable

1. Run `/cn config`.
2. Enable `Hercules`.
3. Enable `pestCore`.
4. Leave `pestFinderHud`, `pestFinderWorld`, `pestTimerHud` and `pestStatsHud` enabled.
5. Keep `pestFinderOnlyWithVacuum` and `pestTimerOnlyWithTool` enabled for the first test.
6. Warp to the Garden.

### Basic state

- [ ] Hold a vacuum. Expected: Pest Finder and Pest Timer HUDs appear if the Pests tab widget supplies state.
- [ ] Stop holding the vacuum. Expected: plot guidance disappears after `pestFinderHoldSeconds`; timer visibility follows its held-tool setting.
- [ ] Run `/pests`. Expected: chat reports total pests, detected plots, cooldown and session statistics.
- [ ] Compare the total and plot list with Hypixel's Pests tab widget. Expected: the values match.

### Spawn

- [ ] Farm until a pest spawn message appears. Expected: a configurable green title and sound play once.
- [ ] If `pestCompactSpawnChat` is off, expected: Hypixel's original message remains and no duplicate local line is added.
- [ ] If `pestCompactSpawnChat` is on, expected: one additional compact local line uses `pestSpawnTemplate`.
- [ ] Hold a vacuum after the spawn. Expected: the infested plot receives a border and a label showing its pest count.
- [ ] Stand inside that plot. Expected: it uses `pestFinderCurrentColor` when `pestFinderCurrentPlotRed` is enabled.
- [ ] Use a custom plot name. Expected: after you visit that plot and its name appears on the scoreboard, later spawn messages resolve it to the correct physical plot.

### Cooldown

- [ ] Compare the timer HUD with Hypixel's Pests widget. Expected: `Ready`, `Max pests`, or the remaining time matches.
- [ ] Enable `pestTimerShowAverage`. Spawn at least twice within `pestTimerAverageTimeoutSeconds`. Expected: an average row appears.
- [ ] Let the cooldown approach `pestTimerWarningSeconds`. Expected: enabled title/chat/sound channels fire once.
- [ ] Enable `pestTimerCustomCooldown` and set a known value. Expected: the local estimate counts from the last detected spawn using that value.

### Kill, drops and profit

- [ ] Kill one pest. Expected: alive count falls, current plot count falls, and the Statistics HUD gains one session kill.
- [ ] Compare the pest name and drop amount with the Hypixel reward message. Expected: last pest/drop and quantities match.
- [ ] Wait briefly if the item's market price was not cached. Expected: profit fills in after price data arrives rather than staying at zero.
- [ ] Change HUD visibility options. Expected: kills, drops, profit, profit/hour and session/lifetime text can be independently hidden.
- [ ] Run `/pests reset`. Expected: session values clear but lifetime values remain.
- [ ] Only when you truly want to erase everything, run `/pests resetall`. Expected: session and lifetime pest statistics clear.
- [ ] Leave the Garden. Expected: borders, finder and timer disappear immediately.

## Garden visitors and farming

### Enable

Enable Hercules, `visitorHelper`, `visitorShoppingList`, `farmingControlHud`, `farmingRateHud` and `jacobContestHud`.

### Test

- [ ] Open a Garden visitor. Expected: requested items, inventory amount, sack amount and prices appear.
- [ ] Hover the accept/refuse choices. Expected: configured profit/reward details and safeguards appear.
- [ ] Try refusing a rare/new visitor. Expected: the configured protection blocks the accidental click; the bypass key deliberately overrides it.
- [ ] Hold each farming tool. Expected: the Control HUD identifies the crop and shows its configured speed and target angle.
- [ ] Break crops. Expected: recent BPS, session BPS, blocks and time update only from your own harvests.
- [ ] Enter a Jacob contest. Expected: crop, collected amount, rate and projected total follow the scoreboard.
- [ ] Leave the Garden. Expected: Garden-only HUDs hide unless their outside-Garden option is enabled.

## Dungeons

Because this is the largest area, test it across normal runs instead of staging every room at once.

### Before a run

1. Enable Orion.
2. Enable the dungeon map, score, secrets, puzzle display and the solvers you want to inspect.
3. Disable overlapping dungeon overlays in other mods for a clean comparison.

### Clear phase

- [ ] Enter a dungeon. Expected: map/score/timer appear only after dungeon detection.
- [ ] Walk through several rooms. Expected: room names, shapes and doors align with the actual map.
- [ ] Enter a routed room. Expected: route steps align with blocks and do not continue through unrelated rooms.
- [ ] Collect secrets. Expected: waypoints disappear or advance correctly.
- [ ] Find a starred mob, miniboss or key. Expected: only the configured target receives an overlay.
- [ ] Open Blood. Expected: Watcher/Blood timing begins once and resets after the phase.
- [ ] Reach 270/300. Expected: each configured score alert fires once with the correct floor/time.

### Puzzle rooms

For each puzzle, verify the overlay appears only inside its matching room and disappears immediately after leaving.

- [ ] Blaze: ordered targets match health order.
- [ ] Boulder: boxes follow the valid solution in `boxes-room`.
- [ ] Creeper Beams: exactly the intended beam pairs are connected.
- [ ] Ice Fill: path covers the board without revisiting tiles.
- [ ] Silverfish: maze path leads from the silverfish to the finish.
- [ ] Water Board: the next lever/gate instruction advances after each state change.
- [ ] Tic Tac Toe: suggested move never allows an avoidable loss.
- [ ] Three Weirdos: correct chest/NPC result is highlighted.
- [ ] Trivia: correct answers are highlighted.
- [ ] Teleport Maze: used pads and next guidance update.
- [ ] Simon Says: buttons are recorded in order; current is green and next is yellow; no click is sent automatically.
- [ ] Arrow Align and Lights On: guidance updates from actual board state.
- [ ] Terminals: overlay matches the terminal type and never acts without your click.

### Boss phases

- [ ] F5/M5: correct Livid is highlighted without hiding or mutating clones.
- [ ] F6/M6: Terracotta timing starts and resets correctly.
- [ ] F7/M7 Maxor: Simon/Crystal helpers only exist during Maxor.
- [ ] Goldor: terminal section guidance advances in the correct order and clears when Core opens.
- [ ] M7 dragons: color, spawn timer, priority, health and hit counts match live dragons.
- [ ] Masks: Bonzo, Spirit and Phoenix used/immunity/cooldown states match actual procs.
- [ ] End a run. Expected: chest profit and run data appear; all transient room/boss overlays clear on exit.

## Fishing

### Enable

Enable Hydra, then enable only the tracker/alerts for the fishing area you plan to use. Assign any deliberate keys through Minecraft Controls before testing them.

### Test during normal fishing

- [ ] Cast a rod. Expected: hook/bait HUD follows your own bobber, not another player's.
- [ ] Catch ordinary and rare creatures. Expected: counts and rates increment once per catch.
- [ ] Receive a rare drop. Expected: the correct drop, price, source and configured alert channels appear once.
- [ ] Change or exhaust bait. Expected: bait state updates and recovery action is clickable when enabled.
- [ ] Disable the Fishing Bag or wear incorrect armor. Expected: safety warning appears only while relevant.
- [ ] Approach the area's entity cap. Expected: Barn Fishing warning uses that area's configured threshold.
- [ ] Place your own deployable. Expected: timer is attributed to you and disappears when removed/expired.
- [ ] Find a hotspot. Expected: radius/perk display attaches to the correct hotspot and clears when gone.
- [ ] Use a wormhole or trigger Nessie guidance. Expected: the named destination and world marker agree.
- [ ] During a Fishing Festival, expected: totals, Great Whites, summary and personal best update once.
- [ ] Press the configured Lootshare key. Expected: one customizable party call is sent; incoming calls show the correct sender and deduplicate.
- [ ] Leave the fishing area. Expected: area-specific entities, targets and short-lived alerts clear.

## Mining

### Enable

Enable Aquila and the relevant Dwarven, Crystal Hollows or Glacite options.

### Test

- [ ] View commissions. Expected: names/progress match tab and destination guidance points to the selected commission.
- [ ] Open the Forge. Expected: slots and finish times are captured and persist after relaunch.
- [ ] Complete Fetchur/Puzzler. Expected: exact daily answer appears only in the relevant area.
- [ ] Use a Wishing Compass. Expected: guidance updates from the compass result and does not persist into another island.
- [ ] Open Crystal Nucleus information. Expected: owned/missing crystals agree with game state.
- [ ] Enter a Glacite Mineshaft. Expected: cave-in/cold and pity information appears.
- [ ] Find/loot a corpse. Expected: waypoint, key count and profit update once.
- [ ] Test a fossil puzzle. Expected: advisory solution matches the board without automatic clicking.

## Kuudra and Crimson Isle

### Enable

Enable Draco and the Kuudra/Crimson helpers you want. Avoid enabling duplicate supply/stun overlays in other mods during comparison.

### Test

- [ ] Start Kuudra. Expected: phase, titles and timers begin at the correct event.
- [ ] Pick up a supply. Expected: your supply is recognized and delivery guidance points to the correct build site.
- [ ] Build the ballista. Expected: build progress/timing follows actual progress.
- [ ] Stun Kuudra. Expected: stun state and timers start/end correctly.
- [ ] Finish the run. Expected: splits and breakdown appear once and persist according to settings.
- [ ] Spawn a Vanquisher. Expected: local and optional party alerts identify it once.
- [ ] Test Ashfang/dojo/miniboss helpers naturally. Expected: each is area and encounter gated.

## Slayers

### Enable

Enable Perseus and choose the Slayer types/mechanics you want.

### Test

- [ ] Start a quest. Expected: boss/type/tier and progress HUD identify the quest.
- [ ] Spawn a miniboss. Expected: only your configured minibosses highlight/alert.
- [ ] Spawn the boss. Expected: timer/health/state begin once.
- [ ] Trigger type-specific mechanics. Expected: Enderman, Blaze or Vampire guidance matches the mechanic and clears afterward.
- [ ] Complete/fail the boss. Expected: session stats update once and boss overlays clear.
- [ ] Receive a tracked drop. Expected: rarity, amount and persistent statistics update once.

## Economy, inventory and protection

### Enable

Enable Lyra and Phoenix protection/inventory features. Use inexpensive test items first.

### Test

- [ ] Hover Bazaar, auction and ordinary items. Expected: relevant prices appear without duplicated or impossible values.
- [ ] Open storage/backpacks. Expected: previews and total value correspond to contained items.
- [ ] Search inventory. Expected: matching items remain clear and unrelated items are dimmed as configured.
- [ ] Attempt to drop a protected item. Expected: the drop key itself still registers, but the item does not leave the inventory.
- [ ] Test NPC trade, auction and salvage protection. Expected: unsafe action blocks; three deliberate clicks override where configured.
- [ ] Donate to Museum. Expected: donation is allowed.
- [ ] In Dungeon Hub, test an Enchanted Book drop. Expected: it is allowed there and protected elsewhere according to settings.
- [ ] Test inventory buttons. Expected: clicking the configured button runs only its assigned command.

## Parties and carries

### Enable

Enable Pegasus and configure message templates in the master Messages screen before using automatic messages.

### Test

- [ ] Open the Messages screen. Expected: enabled messages can be searched, sorted and edited; variables are listed for each message.
- [ ] Preview a message. Expected: variables such as score/player/floor substitute without sending anything.
- [ ] Trigger an enabled party event. Expected: exactly one customized message is sent at the correct time.
- [ ] Use reparty/ready checks. Expected: commands occur only after your explicit command/click/key unless that feature is intentionally configured automatic.
- [ ] Create a carry with a run price. Expected: client records carried player, run count, price and matching payment multiples.
- [ ] Finish or cancel a carry. Expected: summary is accurate and persistent state clears according to settings.

## Rift, events and general HUD

- [ ] Enable Andromeda in the Rift. Expected: timer and selected waypoints are Rift-only; collected Enigma Souls hide.
- [ ] Enable Cygnus during a scheduled event. Expected: event timing/alerts match the calendar and fire once.
- [ ] Test Diana with a Spade. Expected: burrow guidance updates from real particles/events and clears after completion.
- [ ] Enable Apollo widgets one at a time. Expected: values update, can be moved/scaled, and do not overlap after you arrange them.
- [ ] Enable Cassiopeia filters individually. Expected: only the selected message class is changed or hidden.

## What to include in a bug report

For each failure, record only these five things:

1. Game area and exact action.
2. Feature name and its enabled settings.
3. What appeared versus what should have appeared.
4. Whether another mod showed the correct result.
5. The newest relevant file from `config/constellation-scrapes/` after `/cn scrape all`.

One issue at a time is ideal. You do not need to retest unrelated sections after reporting a localized parser or overlay problem.
