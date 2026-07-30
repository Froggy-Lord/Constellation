# Constellation testing guide

You do not need to test everything in one sitting. Start with the five-minute check, then test one game-area session whenever you naturally play that area. Checkboxes are intentionally split into small groups.

## Custom SkyBlock Scoreboard

Enable Apollo and Custom Scoreboard, then join Hypixel SkyBlock. Open `/customscoreboard`.

- [ ] Confirm the custom scoreboard appears and the vanilla sidebar disappears. Expected: only one scoreboard is visible; disabling the feature or leaving SkyBlock restores vanilla immediately.
- [ ] Compare its server rows against the vanilla sidebar before enabling replacement. Expected: all non-hidden lines remain in score order, including unfamiliar lines.
- [ ] Open the tab list and menus containing Bank, Bits, Gems, Magical Power, tuning, power, Quiver, God Pot, mayor, party, election or area information. Expected: recognized values update and can persist between sightings.
- [ ] Search the line editor, click a name, and use Up or Down. Expected: filtering does not change saved order; visibility and order survive restarting.
- [ ] Use Reset. Expected: the documented default server/stat ordering returns and hidden lines clear.
- [ ] Toggle empty, consecutive, edge and irrelevant filtering. Expected: missing custom values and duplicate source rows change independently without discarding unknown server information.
- [ ] Change title/text alignment, line spacing, row cap, title, background, outline, shadow and each color. Expected: changes apply immediately and persist.
- [ ] Move and resize Custom Scoreboard in `/cn hud`. Expected: placement and scale persist.
- [ ] Disable Hide Vanilla while leaving the custom scoreboard on. Expected: both sidebars render for comparison; reenabling it hides only vanilla.
- [ ] Join a non-Hypixel server or leave SkyBlock. Expected: Constellation does not hide that server's scoreboard.
- [ ] Compare SkyBlock date and time against Hypixel. Expected: season, ordinal day, optional year, 12/24-hour mode and exact/ten-minute mode track SkyBlock time.
- [ ] Change the lobby date format and switch islands. Expected: the real date and learned `mini` or `mega` server ID update without inventing an ID while unavailable.
- [ ] Inspect an island tab widget with a Players or Guests count. Expected: current and maximum values are learned; the manual maximum is used only when no maximum has been observed.
- [ ] Enable active events and compare all/highest modes. Expected: cached calendar events show their remaining time; upcoming rows appear only when enabled.
- [ ] Enable mayor perks and extra mayor. Expected: the current mayor, active perks, minister and minister perk come from the shared mayor cache without another request.
- [ ] View a Party Leader and Party Members tab field. Expected: leader and bounded member rows update; missing party data hides the group when empty-line filtering is enabled.
- [ ] Edit footer text and toggle each date, time, lobby, players, events, mayor, party and footer element. Expected: every group is independently visible and reorderable.

## Deep Caverns Guide

Enable Aquila and Deep Caverns Guide. Defaults match the live profile: 30 points ahead, rainbow rendering, boxes and lines.

- [ ] Enter each named Deep Caverns layer. Expected: Gunpowder Mines, Lapis Quarry, Pigmen's Den, Slimehill, Diamond Reserve and Obsidian Sanctuary all classify as Deep Caverns.
- [ ] Open the Lift before Obsidian Sanctuary is unlocked. Expected: slot 49 shows a local `GO` guide button while the real slot 31 is not named Obsidian Sanctuary.
- [ ] Click `GO`. Expected: the click is consumed locally, no container packet is sent for slot 49, and the guide starts.
- [ ] Trigger the exact Lift Operator instruction to venture into Lapis Quarry. Expected: automatic activation occurs only when Auto Chat is enabled.
- [ ] Run `/deepguide start` away from the first point. Expected: Guide Start receives a box, beam, label and optional distance.
- [ ] Reach the first point near `44, 149, 16` while on the ground. Expected: the route begins and the movable HUD shows progress, next point, remaining points and distance.
- [ ] Follow the route downward. Expected: reached points disappear, up to 30 points remain ahead, and progress advances only near route points while grounded.
- [ ] Fall past several points, land near a later route point and keep recovery enabled. Expected: the guide safely resumes from the nearest point inside the configured recovery range.
- [ ] Disable recovery and restart away from the first point. Expected: the guide remains at its start marker until the real first point is reached.
- [ ] Test `/deepguide skip`, `skip <amount>`, `back` and `back <amount>`. Expected: progress stays bounded between points 1 and 92.
- [ ] Set look-ahead to 1 and 30. Expected: the renderer shows only the current point or the full configured forward window without changing progression.
- [ ] Toggle boxes, lines, labels, distance and wall visibility independently. Expected: each changes only its own presentation.
- [ ] Disable rainbow and set an eight-digit monochrome ARGB color. Expected: every visible point and line uses that exact color and alpha.
- [ ] Re-enable rainbow and change speed, saturation, brightness and alpha. Expected: the spatial hue sequence animates at the configured speed.
- [ ] Toggle each HUD row independently, then move and resize Deep Caverns Guide in `/cn hud`. Expected: placement and scale persist independently.
- [ ] Reach point 92 near Rhys. Expected: the route closes and the optional local completion message appears once.
- [ ] Leave the Deep Caverns, switch worlds or disconnect midway. Expected: route, Lift prompt and progress clear immediately.

## Heart of the Mountain Helper

Enable Aquila and Heart of the Mountain Helper, then open the exact `Heart of the Mountain` menu.

- [ ] Inspect unlocked enabled, unlocked disabled and locked perks. Expected: they use the configured green, red and gray overlays respectively; unrelated menus receive nothing.
- [ ] Inspect every tree tier, including Mining V3 Glacite perks, abilities and Core of the Mountain. Expected: all 46 licensed perks are recognized and locked perks are never inferred as owned.
- [ ] Compare partially levelled and maxed perks. Expected: only levels between zero and max receive compact slot text; maxed and level-zero slots retain their normal stack presentation.
- [ ] Inspect the Heart item. Expected: its unused Token of the Mountain count appears only when nonzero.
- [ ] Hover Mithril, Gemstone and Glacite powder perks. Expected: powder spent and maximum use the perk's exact licensed exponent and powder type.
- [ ] Hold Shift over a non-maxed powder perk. Expected: the additional row totals the next ten levels or the exact smaller remainder to max.
- [ ] Compare the current-powder row with the Heart item. Expected: it uses the correct powder type and reports the exact deficit for the next level.
- [ ] Test number, percentage and number-and-percentage designs with `/hotmhelper design`. Expected: only the spent row changes and maxed perks remain clearly labelled.
- [ ] Trigger a new Sky Mall day or reopen HOTM on the Sky Mall perk. Expected: the recognized effect persists by SkyBlock profile and updates without retaining another profile's effect.
- [ ] Test `/hotmhelper skymall off|mining_only|everywhere`. Expected: the Sky Mall row follows the selected scope; the full summary remains menu-only.
- [ ] Toggle every `/hotmhelper option`, set each `/hotmhelper color`, and reopen the menu. Expected: all options persist independently.
- [ ] Move and resize Heart of the Mountain in `/cn hud`. Expected: its placement and scale persist and the widget appears only in its configured useful scope.

## Glacite Tunnel Maps

Enable Aquila and Tunnel Maps, then enter the Glacite Tunnels. Use `/tunnelmap` or its menu keybind.

- [ ] Search for and click a gemstone or mining destination. Expected: a weighted route starts at the nearest graph node and recovers after moving away from it.
- [ ] Check a destination with several locations. Expected: Next spot and the configured keybind select another eligible location without sending a command to the server.
- [ ] Change path width, dynamic color, look-ahead and through-walls settings. Expected: the route immediately uses the saved choices; dynamic color follows the selected destination.
- [ ] Enable goal box, beam, name, distance and node labels independently. Expected: only the selected guidance layers render.
- [ ] Reach the configured arrival radius. Expected: the route clears, repeats cycle with its cooldown, and the optional local arrival message appears once.
- [ ] Open `Commissions`. Expected: eligible Glacite collector commissions show an `R`, right-clicking routes locally, and optional automatic selection follows the first eligible commission.
- [ ] Hold a Royal Pigeon and left-click with Pigeon progression enabled. Expected: the next destination is selected without cancelling the normal attack input.
- [ ] Test Campfire with travel-scroll off and on. Expected: off routes to the Campfire graph node; on deliberately sends `/warp basecamp`.
- [ ] Leave the Glacite Tunnels and try the menu/keybind. Expected: all world/HUD guidance stops and a local availability message replaces the selector.
- [ ] Move and resize Tunnel Maps in `/cn hud`. Expected: placement and scale persist and it is visible only with an active tunnel destination.
- [ ] Observe the entire run. Expected: no movement, jump, aim, click, warp, item use, chat or gameplay packet is generated automatically.

## Crystal Nucleus barriers

Enable Aquila and Crystal Nucleus Barriers. The default is the filled live-profile design with all five barriers visible through walls.

- [ ] Enter the Crystal Hollows but remain outside the central Nucleus coordinates. Expected: no barrier volume is drawn.
- [ ] Enter the Crystal Nucleus. Expected: Amber, Amethyst, Topaz, Jade and Sapphire receive distinct large translucent barrier volumes at their real collision regions.
- [ ] Compare each edge against the invisible collision. Expected: all six faces surround the complete barrier, including SkyHanni's one-block expansion.
- [ ] Run `/nucleusbarriers style outline`, then set `/nucleusbarriers thickness 5`. Expected: filled faces disappear and the five outlines use the wider line.
- [ ] Toggle `/nucleusbarriers option labels on`. Expected: each enabled volume receives its own crystal name above the box.
- [ ] Toggle Amber through Sapphire independently. Expected: only the selected volume changes and at least one repeated toggle proves settings persist.
- [ ] Set each `/nucleusbarriers color <crystal> <argb>` target. Expected: only that crystal changes and filled alpha follows the supplied ARGB value.
- [ ] Toggle walls off while looking through Nucleus terrain. Expected: hidden portions obey depth while visible faces remain.
- [ ] Enable Hoppity-only visibility during a non-Spring SkyBlock season. Expected: all barriers hide. Repeat during Spring and expect them to return.
- [ ] Leave the Nucleus coordinates, Crystal Hollows or Hypixel. Expected: every barrier disappears immediately.
- [ ] Observe normal movement around the boxes. Expected: the helper never moves, clicks, aims, mines, uses an item or sends a gameplay packet.

## Galatea Tree Progress

Enable Artemis and Tree Progress. Defaults match the live profile: enabled, axe-only, full display and not restricted to your own tree.

- [ ] Enter Galatea without holding an axe. Expected: Tree Progress stays hidden.
- [ ] Hold a real foraging axe near an active Fig Tree. Expected: the nearest `FIG TREE <percent>%` label becomes a movable Fig Tree HUD.
- [ ] Repeat at a Mangrove Tree. Expected: the type changes to Mangrove Tree and the current percentage follows the server label.
- [ ] Hold a pickaxe or non-tool whose name merely contains similar letters. Expected: it is not accepted as an axe.
- [ ] Enable own-only near another player's tree. Expected: the HUD hides unless the vertically grouped labels contain your name or the server's shared-player marker.
- [ ] Join a shared tree. Expected: contributor count appears when its row is enabled and ownership filtering accepts the tree.
- [ ] Place two eligible trees inside scan range. Expected: only the nearest eligible tree is displayed.
- [ ] Toggle compact mode. Expected: one type-and-percent row replaces the full panel.
- [ ] Toggle type, percentage, bar, distance and contributor rows independently. Expected: each affects only its own presentation.
- [ ] Set `/treeprogress range 16`, then restore the preferred range. Expected: labels outside the bounded distance disappear.
- [ ] Enable threshold alerts and set `/treeprogress alert 90`. Expected: the selected local chat/title/sound channels fire once when progress crosses 90 percent, not every tick.
- [ ] Leave Galatea, change worlds or disconnect. Expected: all Tree Progress state clears immediately.
- [ ] Confirm no interaction occurs. Expected: the feature never swings, aims, selects a tree, breaks a block or sends a packet.

## Attribute Shard overlay

Enable Artemis, Attribute Overlay and its HUD. The overlay is intentionally visible only while the exact `Attribute Menu` is open.

- [ ] Open `/attributemenu` with Advanced Mode enabled and visit every page. Expected: shard tiers, rarity progression, next-tier requirements and enabled state are learned for the active SkyBlock profile.
- [ ] Close and reopen the menu. Expected: learned rows persist for this profile and do not appear on another profile until that profile is scanned.
- [ ] Use the default max-tier and sell-price settings. Expected: rows are ordered cheapest first by remaining Bazaar cost to tier 10.
- [ ] Run `/attributeoverlay sort next`. Expected: requirements and ranking change to the next tier only.
- [ ] Run `/attributeoverlay price buy`, then return to `sell`. Expected: every row and total consistently use the selected side.
- [ ] Open Hunting Box, then return to Attribute Menu. Expected: owned shard amounts reduce requirements when Hunting Box inclusion is enabled.
- [ ] Toggle Hunting Box inclusion off. Expected: the original full requirement returns without deleting saved box ownership.
- [ ] Toggle Hide Maxed, Only Locked and Current Inventory independently. Expected: each filter affects rows while summary counts remain correct for the selected inventory scope.
- [ ] Enable tier slot text. Expected: each Attribute Menu shard shows tier 0-10 with red, yellow, green or gold progress coloring.
- [ ] Disable an attribute in the real menu. Expected: its slot receives only the configured disabled tint; enabled slots are unchanged.
- [ ] Hover a learned shard. Expected: tier, selected requirement, price and optional Hunting Box ownership are added without replacing vanilla lore.
- [ ] Remove Bazaar connectivity temporarily. Expected: unresolved rows and the total show partial pricing rather than a false complete value.
- [ ] Run `/attributeoverlay rows 5` and each `/attributeoverlay option` control. Expected: settings save and only presentation changes; no menu click is generated.

## Hunting Box value

### Enable

1. Enable Artemis, Hunting Box Value and its HUD.
2. Keep per-row Instant Sell visible and Instant Buy hidden for the compact default, or enable both row sides for comparison.
3. Set the high-value overlay threshold with `/huntingboxvalue threshold <millions>`.

### Test

- [ ] Open exact `Hunting Box`. Expected: only slots 9-44 excluding the two border columns are considered.
- [ ] Compare every displayed amount to exact `Owned: <amount> Shard(s)` lore. Expected: counts match, including zero and comma-separated quantities.
- [ ] Compare per-shard Instant Sell and Instant Buy values with Bazaar. Expected: amount times the correct price side is shown.
- [ ] Compare Total Shards, Instant Sell and Instant Buy with a manual sum. Expected: all parsed rows contribute, even when the HUD row limit is lower.
- [ ] Temporarily make Bazaar data unavailable. Expected: affected rows and totals say `partial`; no zero-price row is presented as a complete valuation.
- [ ] Wait for asynchronous prices to load. Expected: the open HUD and tooltips refresh without reopening the menu.
- [ ] Test Sell Desc, Buy Desc, Amount Desc and Name sorting. Expected: all rows reorder deterministically.
- [ ] Change the row limit. Expected: only HUD detail rows are limited; totals, slot overlays and tooltips still cover every parsed shard.
- [ ] Enable Unit, Sell and Buy row options independently. Expected: the selected fields appear without changing calculations.
- [ ] Hover a shard. Expected: contextual tooltip rows show both total and unit values.
- [ ] Enable slot highlights. Expected: every parsed shard receives the normal/high-value overlay based on liquidation total.
- [ ] Test a missing-priced shard with Missing Highlight disabled and enabled. Expected: it uses the missing color by default or normal threshold behavior when explicitly allowed.
- [ ] Disable Hide Zero. Expected: zero-owned shard entries appear without changing totals.
- [ ] Open another inventory or close Hunting Box. Expected: the HUD, overlays and added tooltips disappear immediately.
- [ ] Open a populated Hunting Box whose lore cannot be parsed. Expected: a visible parse warning appears instead of a false zero total.
- [ ] Run `/huntingboxvalue status`, rows, threshold, sort and options. Expected: settings persist and no slot is clicked.

## Galatea sound controls

### Enable

1. Enable Artemis and Galatea Sound Control.
2. Keep Galatea Only, Mute Phantoms and Mute Fusion Machine enabled.
3. Keep Fusion volume at `2000` hundredths with zero tolerance for SkyHanni's exact volume-20 behavior.

### Test

- [ ] Stand in Galatea near a phantom. Expected: ambient, bite, death, flap, hurt and swoop sounds are muted.
- [ ] Disable one phantom sound type through `/galateasounds option`. Expected: only that type becomes audible.
- [ ] Leave an unrecognized `entity.phantom.*` sound enabled globally. Expected: it is muted under the master Phantom option, matching Skyblocker's future-proof prefix behavior.
- [ ] Disable the Phantom master. Expected: every phantom sound becomes audible regardless of individual type toggles.
- [ ] Use the Fusion machine in Galatea. Expected: volume-20 `firework_rocket.blast` and `blast_far` sounds are muted.
- [ ] Set an incorrect Fusion target volume with zero tolerance. Expected: Fusion firework sounds are no longer muted.
- [ ] Expand tolerance or enable Any Volume. Expected: matching Fusion blast names inside that range are muted.
- [ ] Trigger ordinary fireworks at normal volume. Expected: they remain audible with the default exact-volume mode.
- [ ] Leave Galatea. Expected: phantom and firework sounds elsewhere remain untouched while Galatea Only is enabled.
- [ ] Disable Galatea Only deliberately. Expected: selected filters apply anywhere on Hypixel, but never off Hypixel.
- [ ] Run `/galateasounds status` and `resetstats`. Expected: local phantom/Fusion hidden counts report and reset correctly.
- [ ] Disable Artemis or the master control. Expected: every sound immediately passes through.

## Huntaxe Lock

### Enable

1. Enable Artemis and Huntaxe Lock.
2. Keep Air, Blocks and Actionbar enabled with the ten-tick window for the live NoFrills behavior.
3. Leave Single Use and Sneak Bypass disabled unless every use should confirm or crouching should deliberately bypass the first-click lock.

### Test

- [ ] Hold Genesis, Dominus, Cursus, Praedator or Nex Titanum and right-click air once. Expected: Absorptio is cancelled and the actionbar asks for a second click.
- [ ] Right-click again inside ten ticks. Expected: the interaction is allowed.
- [ ] Wait longer than the configured window before clicking again. Expected: the next click is treated as a new first click and blocked.
- [ ] Right-click a block with a Huntaxe. Expected: the same confirmation state applies while Block scope is enabled.
- [ ] Disable Air or Blocks independently. Expected: that interaction route passes normally while the other remains protected.
- [ ] Change Huntaxes or move the Huntaxe between hands during the confirmation window. Expected: only the same item identity retains the window.
- [ ] Switch away from the Huntaxe. Expected: the confirmation window clears immediately.
- [ ] Enable Single Use. Expected: every allowed Absorptio consumes the window, so another use needs a fresh double-click.
- [ ] Enable Sneak Bypass and crouch-right-click. Expected: the first interaction passes deliberately and clears any old window.
- [ ] Test an unrelated golden axe, dungeon ability item and non-Huntaxe weapon. Expected: none are blocked.
- [ ] Test a Huntaxe whose ID is unavailable but whose name contains Huntaxe and lore contains `Ability: Absorptio`. Expected: it remains protected.
- [ ] Toggle actionbar, chat and sound independently. Expected: only the selected local channels announce the blocked first click.
- [ ] Run `/huntaxelock ticks`, `status`, `reset` and `resetstats`. Expected: timing, current state and local session counters update correctly.
- [ ] Observe normal gameplay without right-clicking a Huntaxe. Expected: no interaction is generated automatically.

## Fusion keybinds

### Enable

1. Enable Artemis and Fusion Keybinds.
2. Open Minecraft Controls and assign different keys to `Fusion Repeat`, `Fusion Confirm` and `Fusion Cancel`.
3. Keep the default 200ms cooldown unless repeated physical key presses need more separation.

### Test

- [ ] Press Repeat in an exact `Fusion Box` with `Repeat Previous Fusion` available. Expected: one deliberate middle-click is sent to that exact button.
- [ ] Press Confirm in exact `Confirm Fusion`. Expected: one deliberate middle-click is sent only to the green terracotta button.
- [ ] Press Cancel in exact `Confirm Fusion`. Expected: one deliberate middle-click is sent only to the red terracotta button.
- [ ] Press any binding in another inventory or over normal gameplay. Expected: nothing is clicked.
- [ ] Press Confirm in Fusion Box or Repeat in Confirm Fusion. Expected: no action occurs.
- [ ] Test when the expected repeat name or action material is absent. Expected: the action fails closed and optional local feedback explains that it is unavailable.
- [ ] Assign the same physical input to two Fusion actions. Expected: Constellation refuses the ambiguous action and warns at most once per second.
- [ ] Hold one Fusion binding while pressing another. Expected: neither action runs.
- [ ] Press a valid action repeatedly faster than `/fusionkeys cooldown`. Expected: only one action per cooldown window is sent.
- [ ] Disable repeat, confirm or cancel independently through `/fusionkeys option`. Expected: the disabled action no longer clicks while the others still work.
- [ ] Disable input consumption. Expected: invalid or disabled Fusion bindings may reach the underlying screen; valid clicks remain consumed.
- [ ] Run `/fusionkeys status` and `resetstats`. Expected: session repeat, confirm and cancel counts are accurate and reset locally.
- [ ] Observe Fusion without pressing a configured key. Expected: no click, repeat, confirmation or cancellation ever occurs automatically.

## Attribute Shard Tracker

### Enable

1. Enable Artemis, Shard Tracker and Shard Tracker HUD.
2. Open Minecraft Controls and bind `Track Hovered Shard` if hovered selection is wanted.
3. Use `/shardtracker import` with a SkyShards `NoFrillsRecipe` or `SkyHanniRecipe` export, or use `/shardtracker add <needed> <name>`.

### Test

- [ ] Import a real SkyShards tree. Expected: valid Direct, Bazaar, Fuse and Cycle rows appear; invalid, oversized or unrelated clipboard text is rejected without changing existing goals.
- [ ] Import a new valid tree. Expected: the active profile's previous goals are replaced, while other SkyBlock profiles remain unchanged.
- [ ] Hover an Attribute Shard in Hunting Box, Attribute Menu or a Fusion menu and press the configured control. Expected: the shard toggles in the tracker without clicking the slot.
- [ ] Catch, Lootshare, charm or fuse a tracked shard. Expected: its obtained count increases by the exact chat quantity once.
- [ ] Send a tracked shard to the Hunting Box. Expected: the absorption quantity is counted.
- [ ] Open Hunting Box. Expected: every tracked shard found there synchronizes to the exact `Owned` lore count.
- [ ] Cross a configured goal. Expected: enabled completion chat, title and sound channels fire once at the crossing.
- [ ] Run `/shardtracker sort remaining`, `progress`, `value`, `name` and `import`. Expected: row ordering changes deterministically.
- [ ] Enable Fusion and Direct filters, then enter and leave Fusion Box. Expected: Direct/Bazaar rows hide inside and Fuse/Cycle rows hide outside.
- [ ] Enable value rows and switch `/shardtracker price purchase|sell`. Expected: remaining-value estimates use the chosen Bazaar side and unresolved prices say `partial`.
- [ ] Close every inventory and continue playing. Expected: the movable HUD remains available because goals are persistent, but no menu is clicked or opened.
- [ ] Switch SkyBlock profiles. Expected: goals and obtained counts change to that profile's independent set.
- [ ] Use `/shardtracker obtained <amount> <name>`, remove and clear. Expected: manual recovery affects only the active profile.

## Hunting profit tracker

### Enable

1. Enable Artemis, Hunting Profit Tracker and Hunting Profit HUD.
2. Keep Show When Pickup and Show With Tool enabled to match the live SkyHanni behavior.
3. Leave Persistent disabled for session-only tracking, or enable it for profile history.

### Test

- [ ] Catch a mob that awards one shard. Expected: Mobs Caught and Shards each increase by one and the shard appears once.
- [ ] Catch an `x2` or larger shard result. Expected: one mob and the complete shard quantity are recorded.
- [ ] Receive a hunting Lootshare shard. Expected: it is counted only while Lootshare inclusion is enabled.
- [ ] Charm a mob with CHARM, NAGA or SALT. Expected: the captured shard quantity and mob shard name are recorded.
- [ ] Compare a tracked shard against Bazaar. Expected: value uses its actual `SHARD_*` product, including renamed exceptions such as Inferno Demonlord and End Stone Protector.
- [ ] Select purchase and sell price sources. Expected: totals update without changing quantities.
- [ ] Sort by value, amount, name and recent. Expected: row order changes while the configured row limit remains enforced.
- [ ] Stop hunting longer than the AFK threshold. Expected: uptime and profit per hour stop accumulating inactive time.
- [ ] Hold a lasso, Hunting Net or Black Hole tool and then release it. Expected: the HUD remains for the configured grace period.
- [ ] Trigger the configured high-value threshold. Expected: each enabled local chat/title/sound channel fires once.
- [ ] Switch profiles with Persistent enabled. Expected: stored totals remain isolated.
- [ ] Run `/huntingprofit reset`. Expected: session values clear. Run `clearprofile` only when deliberately removing persistent active-profile history.

## Galatea hunting targets

### Enable

1. Enable Artemis and Hunting Mob Highlights.
2. Enable Hideonleaf, Invisibug, Shellwise and Coralot. Birries is disabled by default to match the live SkyHanni profile.
3. Enable Boxes and Labels first; add beams or lines only if wanted.

### Test

- [ ] Approach a green Hideonleaf shulker in Galatea. Expected: only that green shulker receives the configured Hideonleaf overlay inside its range.
- [ ] Find a Shellwise turtle and Coralot axolotl. Expected: each receives its own independently colored overlay.
- [ ] Enable Birries and approach one. Expected: the helper links its detached Birries name tag to the nearest living base entity rather than outlining the armor stand.
- [ ] Approach an Invisibug CRIT particle. Expected: the nearest inventory-empty default armor stand within five blocks receives the offset Invisibug marker.
- [ ] Observe unrelated CRIT particles without a matching default stand. Expected: no marker appears.
- [ ] Change every target range and color with `/huntingmobs`. Expected: changes are independent and persist.
- [ ] Toggle box, label, distance, beam, line and through-wall rendering independently. Expected: only selected presentation remains.
- [ ] Enable target alerts and find the same target repeatedly. Expected: selected chat/title/sound channels obey the per-type cooldown.
- [ ] Leave Galatea or change worlds. Expected: every hunting target immediately disappears and no stale marker leaks into another island.

## Lasso HUD

### Enable

1. Enable Artemis and Lasso Display.
2. Keep Galatea Only enabled unless deliberately testing another island.
3. Enable Progress and Percent; enable Target, Tool or Distance if wanted.

### Test

- [ ] Hold a non-Lasso item. Expected: the Lasso HUD remains hidden.
- [ ] Cast a Lasso that creates a leash from the target to you. Expected: the HUD appears only for your own leash.
- [ ] Compare Abysmal, Vinerip, Entangler and Everstretch Lassos. Expected: their two-, three-, three- and four-segment reel offsets produce a correctly normalized percentage.
- [ ] Watch the server progress armor stand. Expected: the HUD updates from the exact twenty-character component structure and ignores unrelated nearby names.
- [ ] Wait for the exact REEL armor stand. Expected: the HUD changes to `REEL`/`NOW` and displays the ready color.
- [ ] Enable Target. Expected: a nearby target label is cleaned of level and health text before display.
- [ ] Toggle compact mode. Expected: progress, ready state and target collapse into one row.
- [ ] Enable ready chat, title and sound independently. Expected: each fires once when the configured threshold is crossed.
- [ ] Enable Repeat and set `/lassohud repeatseconds 3`. Expected: alerts repeat no faster than every three seconds while ready.
- [ ] Kill or release the target, change worlds or leave the configured scope. Expected: the HUD and ready latch clear.
- [ ] Observe normal operation. Expected: Constellation never reels, clicks, aims or sends an interaction.

## Fusion display

### Enable

1. Enable Artemis and Fusion Display.
2. Enable Inputs, Output, Owned, Required, Input Cost, Output Value and Net Value.
3. Keep input pricing on Purchase and output pricing on Sell for practical profit estimates.

### Test

- [ ] Open Fusion Box, Shard Fusion and Confirm Fusion. Expected: the HUD appears only inside these exact menus.
- [ ] Select two valid input shards. Expected: slots 12 and 14 show their exact names and `Required to fuse` quantities.
- [ ] Compare each input’s `Owned` lore. Expected: readiness is green only when both owned quantities meet their separate requirements.
- [ ] Inspect slot 31. Expected: output name and actual stack quantity match the menu.
- [ ] Compare input cost to Bazaar purchase prices and output value to Bazaar sell prices. Expected: Net equals output value minus both complete input costs.
- [ ] Make one price unavailable. Expected: affected values show `partial` rather than inventing a zero-price profit.
- [ ] Switch `/fusionhud price input sell` and `/fusionhud price output purchase`. Expected: only the selected side’s valuation changes.
- [ ] Complete a Fusion. Expected: the exact result and amount are remembered when returning to an otherwise empty Fusion Box.
- [ ] Trigger `PURE REPTILE`. Expected: its count increases once; enabling persistent mode isolates totals by SkyBlock profile.
- [ ] Enable readiness alerts. Expected: selected chat/title/sound channels fire once when both materials become sufficient.
- [ ] Close the Fusion GUI. Expected: the HUD disappears immediately and never persists over normal gameplay.
- [ ] Observe normal operation. Expected: Constellation never selects, confirms, repeats or clicks a Fusion.

## Unique gifting

### Enable

1. Enable Cygnus, Winter Gift Tracker, Unique Gift Counter and Unique Gift HUD.
2. Keep Holding Only enabled if the counter should appear only while holding a gift.
3. Leave Gifting Opportunities disabled until you want advisory world highlights.

### Test

- [ ] Give a gift to a player who has not received one from you. Expected: the count and recorded-name total each increase exactly once.
- [ ] Give the same player another gift. Expected: neither unique total increases.
- [ ] Open Generow. Expected: the profile total synchronizes from `Unique Players Gifted` without discarding recorded names.
- [ ] Switch profiles. Expected: totals and recipient names remain isolated.
- [ ] Set a small goal and milestones with `/uniquegifts goal` and `/uniquegifts milestones`. Expected: enabled alert channels fire once when a configured total is reached.
- [ ] Run `/uniquegifts mark <player>`, `unmark <player>` and `amount <count>`. Expected: corrections persist for the active profile.
- [ ] Enable Gifting Opportunities while holding a gift. Expected: real nearby tab-listed players not in history receive the configured advisory box/label.
- [ ] Add include and exclude filters. Expected: exclusions win and matching is case-insensitive.
- [ ] Disable Show Gifted. Expected: recorded recipients disappear from the overlay.
- [ ] Gift normally. Expected: Constellation never clicks a player, uses a gift or sends an interaction.

## Gift profit tracker

### Enable

1. Enable Cygnus, Winter Gift Tracker, Gift Profit Tracker and Gift Profit HUD.
2. Keep Holding Only enabled to match the live profile, or enable Recent Location fallback.
3. Hold a White, Green or Red Gift and exchange gifts with another player.

### Test

- [ ] Receive COMMON, RARE, SWEET, SANTA and PARTY rewards. Expected: the matching rarity total increments once per exact gift reward line.
- [ ] Receive coins, skill XP and North Stars. Expected: each updates its dedicated total and never appears as a generic item.
- [ ] Receive an item stack reward. Expected: the complete quantity is added under one normalized reward name.
- [ ] Receive a boost potion, enchantment book or Ice Rune. Expected: its specific internal ID is used for pricing.
- [ ] Receive SWEET, SANTA or PARTY tier rewards. Expected: configured title and sound channels fire once.
- [ ] Run `/gifttracker add white 10`. Expected: ten White Gifts are added to cost while reward totals remain unchanged.
- [ ] Compare Value, Gift Cost and Profit. Expected: profit equals reward liquidation value plus coins minus gift replacement cost.
- [ ] Observe Per Hour during a session. Expected: it uses only this session's rewards and manually entered gifts.
- [ ] Hold and release a gift with Holding Only enabled. Expected: the HUD appears and disappears with the held item.
- [ ] Enable Recent Location, exchange one gift, release it and remain within the configured radius. Expected: the HUD remains for the configured window, then hides.
- [ ] Run `/gifttracker id "Snow Suit Helmet" SNOW_SUIT_HELMET` or set a custom price. Expected: the reward valuation updates without changing its count.
- [ ] Switch reward pricing with `/gifttracker pricesource sell` and `buy`. Expected: reward value changes while gift cost continues using replacement cost.
- [ ] Disable Persistent, reset the session and collect rewards. Expected: current-session statistics work but do not return after restart or another config save.
- [ ] Switch profiles. Expected: persistent counts, used gifts and totals are isolated per profile.
- [ ] Run `/gifttracker resetsession`. Expected: only rate/session accounting clears. Run `reset` only to clear the active profile's lifetime totals.
- [ ] Gift normally. Expected: Constellation never clicks a player, uses a gift or moves an item automatically.

## Mayor and election state

### Enable

1. Enable Cygnus, Mayor State and Mayor HUD.
2. Enable Minister, Perks and Election Leader rows as wanted.
3. Join Hypixel SkyBlock and run `/mayor refresh`.

### Test

- [ ] Wait several seconds and run `/mayor status`. Expected: current mayor, minister, active-perk count and election year are populated.
- [ ] Run `/mayor perks`. Expected: every mayor, minister and active temporary perk is listed with a plain-text description and source tag where applicable.
- [ ] Run `/mayor election`. Expected: current candidates show vote totals and available perk counts.
- [ ] Inspect the Mayor HUD. Expected: mayor, minister, filtered perks and current election leader match the command output.
- [ ] Toggle Minister, Perks, Descriptions, Election and Votes independently. Expected: only the selected HUD information changes.
- [ ] Run `/mayor include ritual` and `/mayor exclude mythological`. Expected: substring filters combine and affect only HUD perk rows, never internal active-perk detection.
- [ ] Run `/mayor clearfilters` and `/mayor limit 2`. Expected: filters clear and at most two perk rows display.
- [ ] Run `/mayor refreshminutes 30` and `/mayor color #ffaa00`. Expected: values persist across restart.
- [ ] Refresh repeatedly. Expected: the current mayor never produces a change alert.
- [ ] After a real mayor transition, refresh. Expected: each enabled chat, title and sound channel fires once for the live change.
- [ ] Disconnect after a successful response and restart without network access. Expected: last-good mayor state remains visible with optional cached-state text.
- [ ] Test Carnival with complete live perk state. Expected: auto overlays require Chivalrous Carnival plus their exact Hub coordinate box.
- [ ] Test with the overrides request unavailable. Expected: Carnival falls back to exact coordinate gating rather than being incorrectly disabled.
- [ ] Test outside Hypixel with SkyBlock Only enabled. Expected: Mayor HUD stays hidden while cached state remains intact.

## Event calendar and reminders

### Enable

1. Enable Cygnus, Event Calendar and Event Calendar HUD.
2. Enable Chat, Title and Sound notification channels as wanted.
3. Join Hypixel SkyBlock and run `/events refresh`.

### Test

- [ ] Wait several seconds and run `/events status`. Expected: it reports a populated calendar and the next included event.
- [ ] Inspect the Events HUD. Expected: the current SkyBlock season/day/year and up to three active/upcoming events appear with countdowns.
- [ ] Compare the date to Hypixel's sidebar. Expected: season and day agree; the year row is derived from the SkyBlock epoch.
- [ ] Run `/events rows 1`. Expected: only one event remains while the optional date row stays visible.
- [ ] Run `/events exclude Spooky Festival,Cult of the Fallen Star`. Expected: matching event names disappear and cannot notify.
- [ ] Run `/events include Spooky Festival`. Expected: only matching events pass the include filter, still subject to exclusions.
- [ ] Clear filters with `/events clearfilters`. Expected: every feed event becomes eligible.
- [ ] Run `/events reminders 300,60,0`. Expected: reminders are eligible at five minutes, one minute and event start without duplicates after refresh.
- [ ] Cross a configured reminder threshold. Expected: each enabled chat, title and sound channel fires exactly once.
- [ ] Run `/events refresh` repeatedly near a threshold. Expected: an already delivered reminder never replays.
- [ ] Disable Active, Location, Date, Year and Fetch State independently. Expected: only that HUD information changes.
- [ ] Temporarily disconnect after one successful fetch, then restart. Expected: the last-good cached schedule and deterministic date still render.
- [ ] Test outside Hypixel with SkyBlock Only enabled. Expected: the event HUD and notifications stay hidden.

## Diana tracking suite

### Enable

1. Enable Cygnus, Diana Burrow Waypoints, Diana Mob Tracker and Diana Drop Tracker.
2. Enable both Diana HUDs and the Inquisitor alert options you want.
3. Join the Hub during a Mythological Ritual with a Griffin pet and spade.

### Test

- [ ] Reveal each empirical burrow type. Expected: Start is green, Mob red and Treasure yellow by default, with the correct box, beam and label.
- [ ] Stay near a revealed burrow while its particle repeats. Expected: one waypoint remains instead of duplicates accumulating.
- [ ] Dig a tracked burrow. Expected: the nearby waypoint clears when Hypixel advances the chain.
- [ ] Complete the chain. Expected: all empirical burrow waypoints clear.
- [ ] Run `/diana range 128`, `/diana lifetime 60` and `/diana color treasure #ffaa00`. Expected: rendering range, expiry and Treasure color update and persist.
- [ ] Dig each mythological mob type. Expected: only Hypixel's exact dug-out messages increment the corresponding counter once.
- [ ] Receive a supported rare drop or dug-coin message. Expected: the drop/coin total increments once; configured title and sound fire for rare drops.
- [ ] Spawn an Inquisitor with coordinate information. Expected: one alert and an expiring world waypoint appear.
- [ ] Enable Inquisitor Share. Expected: a locally sourced coordinate is sent once; receiving a teammate's coordinate never echoes it back.
- [ ] Switch Compact/Full HUD modes. Expected: compact shows high-value highlights; full shows every nonzero entry ranked by count.
- [ ] Run `/diana resetmobs`, `resetdrops` and `clearwaypoints`. Expected: each clears only its named state.
- [ ] Leave the Hub or Hypixel. Expected: world waypoints and both HUDs disappear and no incoming chat changes Diana totals.
- [ ] Dig normally. Expected: Constellation never clicks, digs, moves or aims for you.

## Chivalrous Carnival helpers

### Enable

1. Enable Cygnus and `Carnival Helper`.
2. Enable Catch Fish, Zombie Shootout and Carnival HUD as wanted.
3. Join the Hub during a Chivalrous Carnival.

### Test

- [ ] Enter Catch a Fish at coordinates around `-78 70 39`. Expected: Golden Fish armor stands receive the configured box and label; the sound plays once when a target first appears.
- [ ] Leave the Catch a Fish area. Expected: every fish overlay and its HUD row disappear immediately.
- [ ] Run `/carnivalhelper option fishbeam on`. Expected: a vertical advisory beam appears on detected Golden Fish targets.
- [ ] Enter Zombie Shootout at coordinates around `-101 72 44`. Expected: zombies are boxed and labelled Diamond, Gold, Iron or Wood from their chestplate.
- [ ] Watch a target lamp illuminate. Expected: only live lit redstone lamps receive the red outline.
- [ ] Leave Zombie Shootout. Expected: zombie and lamp overlays disappear immediately.
- [ ] Run `/carnivalhelper force fish` or `force zombie` in a safe test world. Expected: that helper can be visually tested without widening normal auto-mode gating. Run `force auto` afterwards.
- [ ] Disable Fish, Zombies, Lamps, Labels, Boxes, Sound and HUD independently. Expected: only the selected presentation disappears.
- [ ] Test outside Hypixel. Expected: no Carnival world overlay or HUD appears, including force modes.
- [ ] Aim and use weapons normally. Expected: Constellation never aims, shoots, fishes or clicks for you.

## Garden plot prices

### Enable

1. Enable Hercules and `Plot Price Helper`.
2. Enter the Garden and open `Configure Plots`.
3. Allow several seconds for Bazaar and Auction prices to load.

### Test

- [ ] Open Configure Plots. Expected: a side panel ranks fully priced locked plots from cheapest to most expensive.
- [ ] Hover a locked plot. Expected: each recognized material line gains its coin value and the tooltip shows the complete plot total.
- [ ] Inspect a multi-material cost. Expected: every material is priced and included exactly once.
- [ ] Keep Show Owned enabled. Expected: tooltip rows compare inventory plus observed sack quantities against each requirement.
- [ ] Hold enough required materials. Expected: the tooltip reports affordability and optional green highlighting appears.
- [ ] Enable Cheapest Highlight while unable to afford the cheapest plot. Expected: only the cheapest fully priced plot receives the configured yellow highlight.
- [ ] Run `/plotprices rows 3`. Expected: the panel shows no more than three plots plus the optional visible-total row.
- [ ] Disable Inline, Total, Panel, Visible Total, Owned, Affordable, Cheapest and Affordable Highlight independently. Expected: only the corresponding presentation changes.
- [ ] Wait for a missing market price to load. Expected: the plot appears automatically after the next refresh.
- [ ] Present a cost with an unknown material or unavailable price. Expected: that plot is omitted rather than shown with a partial total.
- [ ] Click a locked plot manually. Expected: Constellation does not block, modify or generate the click.

## Pesthunter shop profit

### Enable

1. Enable Hercules and `Pesthunter Profit`.
2. Enter the Garden and open the exact `Pesthunter's Wares` menu.
3. Allow several seconds for Bazaar and Auction prices to load.

### Test

- [ ] Open Pesthunter's Wares. Expected: a side panel ranks fully priced offers by estimated profit per Pest spent.
- [ ] Hover a ranked output. Expected: its tooltip shows output value, material cost, trade profit, Pests required and profit per Pest.
- [ ] Compare two trades with different Pest costs. Expected: ranking uses profit divided by Pests, not raw trade profit.
- [ ] Enable Best Highlight. Expected: only the highest-ranked visible offer receives a green advisory outline.
- [ ] Enable Positive Only. Expected: losing and zero-profit offers disappear.
- [ ] Run `/pestshop rows 3`. Expected: no more than three offers appear while the ranking remains correct.
- [ ] Disable Item Price, Materials, Profit, Pests and Per Pest independently. Expected: only the corresponding tooltip line disappears.
- [ ] Reopen the shop after prices refresh. Expected: ranking updates without restarting or changing a menu item.
- [ ] Present an offer with an unresolved material, output ID or required price. Expected: it is omitted rather than priced as though the missing component were free.
- [ ] Click a normal trade manually. Expected: Constellation neither blocks nor synthesizes the click.

## Visitor Logbook analytics

### Enable

1. Enable Hercules and `Visitor Logbook Stats`.
2. Open the exact `Visitor's Logbook` menu in the Garden.
3. Keep Persistent enabled if totals should survive restarts and profile changes.

### Test

- [ ] Open the first Logbook page. Expected: a side panel shows visited, accepted, denied, acceptance rate, waiting visitors and captured pages.
- [ ] Navigate through every page once. Expected: each page is captured without double-counting visitors; completion turns green only after the final page or an explicit total is proven.
- [ ] Reopen an already captured page. Expected: unchanged values do not accumulate or create duplicate visitors.
- [ ] Leave visitors waiting in the Garden queue. Expected: the total denied count subtracts the authoritative current queue where Hypixel exposes it.
- [ ] Hover a real visitor entry. Expected: its tooltip adds accepted, denied and acceptance-rate values.
- [ ] Run `/visitorlog option sortdenied on`. Expected: ranking switches from most visited to most denied.
- [ ] Run `/visitorlog rows 3`. Expected: at most three visitor ranking rows appear while summary rows remain.
- [ ] Disable Visited, Accepted, Denied, Rate, Queue, Pages, Top and Tooltips independently. Expected: only the selected presentation changes.
- [ ] Disable Persistent, run `/visitorlog reset`, and revisit one page. Expected: only the current session is retained.
- [ ] Switch profiles. Expected: captured visitors and pages restore only for the active profile.
- [ ] Run `/visitorlog reset`. Expected: only the current profile is cleared. Use `resetall` only to deliberately clear every saved profile.

## Anita medal shop

### Enable

1. Enable Hercules, `Anita Helper`, `Anita Medal Profit` and `Anita Extra Farming Fortune`.
2. Visit Anita and open the inventory named exactly `Anita`.
3. Allow a few seconds for Bazaar and Auction prices to load.

### Test

- [ ] Open Anita. Expected: a side panel ranks valid shop outputs by estimated profit per Bronze-equivalent medal.
- [ ] Hover one ranked output. Expected: its tooltip shows estimated sale value, non-medal cost, profit per trade, Bronze-equivalent cost and profit per Bronze Medal.
- [ ] Compare a Gold, Silver and Bronze offer. Expected: medal conversion uses 8, 2 and 1 Bronze medals respectively.
- [ ] Enable Best Highlight. Expected: only the highest-ranked visible offer receives a green advisory outline.
- [ ] Enable Positive Only. Expected: zero-loss and losing offers disappear from the panel.
- [ ] Run `/anitahelper rows 3`. Expected: the panel shows at most three ranked offers.
- [ ] Find Extra Farming Fortune and hover it. Expected: the tooltip learns the current profile's tier and shows remaining Gold Medals and Jacob's Tickets through tier 15.
- [ ] If tier learning cannot identify changed Hypixel lore, run `/anitahelper tier <0-15>`. Expected: the tooltip immediately uses the recovery tier without clicking or buying anything.
- [ ] Change profile and reopen Anita. Expected: the saved Fortune tier does not leak between profiles.
- [ ] Disable individual Sale, Materials, Trade Profit, Bronze, Tier, Remaining and Ticket Value options. Expected: only the corresponding tooltip lines disappear.
- [ ] Present an offer with an unrecognized non-medal material or missing price. Expected: that offer is omitted rather than shown with inflated profit.

## Garden Rare Crop Tracker

### Enable

1. Enable Hercules and `Rare Crop Tracker`.
2. Enter the Garden holding a recognized farming tool.
3. Run `/rarecrops` to confirm the tracker is on and using the session or profile view you want.

### Test

- [ ] Receive any `RARE CROP!` or `VERY RARE CROP!` farming drop. Expected: its exact type and count appear once in the movable Rare Crops HUD.
- [ ] Enable Hide Chat and receive another drop. Expected: only that exact rare-crop message is hidden; the HUD still increments.
- [ ] Run `/rarecrops add burrowing_spores 2`. Expected: two local recovery/test entries are added without sending a server command.
- [ ] Toggle Session off. Expected: the current profile's saved totals appear; changing SkyBlock profiles does not leak totals.
- [ ] Stop harvesting for longer than the configured AFK delay. Expected: active uptime and profit/hour stop advancing until harvesting resumes.
- [ ] Toggle Profit, Profit Per Hour, Uptime and Recent independently. Expected: only their HUD rows change.
- [ ] Run `/rarecrops price purchase`, then `/rarecrops price sell`. Expected: value uses Bazaar purchase cost and sell return respectively.
- [ ] Set `/rarecrops lines 2`. Expected: at most two drop-type rows show while summary rows remain.
- [ ] In Crop Money, toggle Rare Crops while wearing zero, one, two, three and four eligible armor pieces. Expected: the relevant crop's hourly value changes only when at least two eligible pieces produce a nonzero drop chance; later armor tiers count for earlier drops.
- [ ] Run `/rarecrops reset`. Expected: session totals clear but profile totals remain. Run `/rarecrops clear` only when you want to erase this profile's totals.

## Garden Level progression

### Enable

1. Enable Hercules and `Garden Level Display`.
2. Enter the Garden and open either the Desk or SkyBlock Menu once.
3. Keep Overflow enabled to show levels above 15.

### Test

- [ ] Open the Desk and inspect its center Garden item. Expected: `/gardenlevel` reports the same level and total XP represented by its lore.
- [ ] Open the SkyBlock Menu. Expected: slot 10 synchronizes the same profile without creating a second value.
- [ ] At level 15 or higher, hover the Garden item. Expected: the tooltip adds current overflow XP, next-level progress and percentage without replacing Hypixel lore.
- [ ] Accept a visitor that rewards Garden Experience. Expected: total and current-level XP increase once by the displayed reward.
- [ ] Cross an overflow level boundary. Expected: optional local level-up chat appears once and its text can be clicked to run `/gardenlevels`.
- [ ] Disable Overflow. Expected: level caps at 15 while earned XP remains saved and can optionally be shown as overflow.
- [ ] Toggle Progress, Percentage, Total XP and Overflow XP independently. Expected: only the selected HUD rows remain.
- [ ] Toggle Roman numerals and change `/gardenlevel precision 2`. Expected: level formatting and percentage precision update immediately.
- [ ] Switch SkyBlock profiles and open the Desk. Expected: each profile restores only its own Garden XP.
- [ ] Run `/gardenlevel clear`. Expected: only the current profile is forgotten and the display asks for a Desk sync.

## Garden crop money per hour

### Enable

1. Enable Hercules and `Crop Money Display`.
2. Keep `Crop Money Use Custom Bps` off to learn each crop from farming, or enable it and set `/cropmoney bps 2000` for a 20.00 BPS comparison.
3. Enable at least one of Sell Offer, Instant Sell or NPC.

### Test

- [ ] Farm one crop for at least five seconds. Expected: its observed BPS is saved and its row appears once true Farming Fortune has been seen for that crop.
- [ ] Compare the current row with Bazaar prices. Expected: hourly profit changes after Bazaar refresh and the current crop is yellow.
- [ ] Toggle Bountiful, Mooshroom, Merge Seeds and Rare Crops separately. Expected: only the relevant contribution or rows change.
- [ ] Set the top count below the current crop's rank. Expected: Show Current adds that crop without hiding a higher-ranked crop.
- [ ] Run `/cropmoney option manual on`, then `/cropmoney position melon 1` and `/cropmoney position wheat 2`. Expected: those crops hold the first two manual positions even when prices change.
- [ ] Assign two crops the same position. Expected: both remain visible and use profit as the stable tie-breaker.
- [ ] Run `/cropmoney resetpositions`. Expected: saved positions clear; automatic profit ordering resumes after `/cropmoney option manual off`.
- [ ] Leave the Garden. Expected: the HUD hides immediately even when Always On is enabled.
- [ ] Open `/cn hud` while the display was visible in the last five seconds and scroll it. Expected: the crop-money HUD can be moved and resized like other current HUD elements.

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

## Jacob contest history and planning: test this release first

### Import contest history

1. Enable Hercules and `Jacob Contest History`.
2. Open Jacob's exact `Your Contests` menu and browse several pages.

- [ ] Expected: only menus whose slot 50 identifies the bulk farming-contest claim control are accepted.
- [ ] Each contest item should contribute its crop and available Diamond, Platinum, Gold, Silver and Bronze thresholds.
- [ ] Close and reopen the menu. Expected: records remain for the current profile.
- [ ] Switch profiles. Expected: neither records nor learned BPS values leak between profiles.
- [ ] Run `/jacobhistory status`. Expected: it reports the target bracket and current-profile record count.

### Medal planner and hovered details

- [ ] Open `Your Contests` with Time Needed enabled. Expected: the movable Jacob Medal Planner lists crops ordered against the selected bracket using the newest configured sample count.
- [ ] Run `/jacobhistory bracket gold` and `/jacobhistory samples 10`. Expected: the planner recalculates from the latest ten matching records.
- [ ] Enable custom BPS and run `/jacobhistory bps 19.9`. Expected: every time and FF estimate uses exactly 19.9 blocks per second.
- [ ] Disable custom BPS and farm each crop. Expected: the planner uses learned profile/crop BPS, with 19.9 as a safe missing-data fallback.
- [ ] Hover a historical contest item. Expected: the planner switches to that exact contest's five known thresholds and Farming Fortune requirements.
- [ ] Toggle thresholds, FF, impossible results and missing rows independently. Expected: only the selected presentation remains.
- [ ] Compare FF values with the latest true Farming Fortune display. Expected: crop-specific values are used and missing FF is reported, never invented.

### Summaries and Personal Bests

- [ ] Complete or leave a Jacob contest after farming. Expected: the summary uses locally validated harvests for total blocks and BPS, plus participation time and the last observed crop score according to enabled rows.
- [ ] Enter a contest without breaking a matching crop. Expected with Hide Zero enabled: no empty summary is sent.
- [ ] Change contest crops. Expected: the old crop summary closes and the new crop measurement starts cleanly.
- [ ] Trigger the three Personal Best chat lines. Expected: one local message reports the gained crop Fortune after all three values arrive.
- [ ] Toggle overflow handling. Expected: values beyond the normal 100-Fortune cap use the licensed overflow calculation only when enabled.
- [ ] Run `/jacobhistory clear`. Expected: only the current profile's historical records clear.
- [ ] Verify menu clicks and server communication remain unchanged.

## Upcoming Jacob contests

### Schedule and HUD

1. Enable Hercules, `Jacob Upcoming Display` and `Jacob Upcoming Fetch Automatically`.
2. Enter SkyBlock and run `/nextcontest refresh`.

- [ ] Expected: the movable Next Jacob Contest HUD shows exactly three upcoming crops and a start countdown from the current EliteSkyBlock schedule.
- [ ] During an active contest, expected: the row changes to Active and counts down to the contest end.
- [ ] In the Garden, compare the boosted marker in the tab widget. Expected: Boosted names the matching crop and prefixes it with `*` in the crop list.
- [ ] Enable following contests and set the following count. Expected: one to five later contests appear in timestamp order.
- [ ] Enable the schedule source row. Expected: the fetched SkyBlock year appears.
- [ ] Disable outside-Garden display and leave the Garden. Expected: the HUD hides; enabling it restores the same persisted countdown.
- [ ] Restart before the next automatic fetch. Expected: up to 40 future contests load immediately from cache without waiting for the network.

### Warnings and recovery

- [ ] Enable upcoming warnings and set `/nextcontest warning 120`. Expected: one warning occurs within two minutes of a selected contest and never repeats for that contest.
- [ ] Toggle a crop with `/nextcontest warncrop <crop>`. Expected: contests containing only disabled crops do not warn.
- [ ] Set `/nextcontest message Farming Contest soon: {crops} in {time}`. Expected: `{crops}`, `{time}` and optional `{boosted}` resolve from live data.
- [ ] Toggle title, chat, sound and window attention independently. Expected: each channel follows its own setting; window attention only requests attention when unfocused.
- [ ] Disable automatic fetching and run `/nextcontest refresh`. Expected: deliberate refresh still works.
- [ ] Run `/nextcontest clear`. Expected: cached contests clear without affecting live contest progress or other Garden data.
- [ ] Disconnect during a fetch or simulate a failed request. Expected: no crash, no partial schedule replacement, and the last valid cache remains.
- [ ] Verify no server command, click, movement or gameplay packet is generated.

## Garden Composter

### Resource and inventory overlay

1. Enable Hercules, `Composter Helper` and `Composter Overlay`.
2. Enter the Garden and wait for the Composter tab widget to appear.
3. Open the `Composter` inventory.

- [ ] Expected: a side panel shows Organic Matter, Fuel, selected fill materials, required quantities, market costs, Stored Compost, estimated empty time and profit per compost.
- [ ] Compare Organic Matter and Fuel with the tab widget and inventory bars. Expected: values match, including decimal and `k`/`m` formatting.
- [ ] Enable sack counts after opening the corresponding sacks. Expected: known counts appear beside the selected materials; unknown counts remain omitted rather than guessed.
- [ ] Toggle round-down. Expected: fill amounts change between conservative floor and complete-fill ceiling quantities.
- [ ] Inspect slots 13, 46 and 52. Expected: compact Stored Compost, Organic Matter and Fuel numbers render only in this exact menu.

### Upgrades, persistence and alerts

- [ ] Open `Composter Upgrades`. Expected: available `Click to upgrade!` entries receive a gold highlight.
- [ ] Hover an upgrade with item costs. Expected: its tooltip adds the combined known market cost and excludes Copper.
- [ ] Close the menu and run `/composter status`. Expected: learned upgrade levels affect capacity, cost reduction, speed and multi-drop profit calculations.
- [ ] Enable the Composter display and move it in `/cn hud`. Expected: the Garden HUD shows current resources and empty time.
- [ ] Enable outside-Garden display, then leave the Garden. Expected: only the persisted empty countdown remains visible.
- [ ] Enable low-resource notifications and set `/composter lowmatter <amount>` or `/composter lowfuel <amount>` above the current value. Expected: one warning occurs and respects the configured cooldown.
- [ ] Enable near-empty warnings. Expected: alerts begin only within the configured threshold and are rate-limited.
- [ ] Switch SkyBlock profiles. Expected: resources and upgrade levels never leak between profiles.
- [ ] Run `/composter clear`. Expected: only the current profile's persisted timer/state clears.
- [ ] Verify all inventory clicks remain unchanged. Expected: the feature only draws and reads data; it never retrieves, buys, clicks or sends a gameplay action.

## Garden farming lanes

### Save a lane

1. Enable Hercules and `Farming Lane Distance Display`.
2. Hold the crop tool you want to configure in the Garden.
3. Run `/farmlane detect`, then farm to the far end of one layer, turn around and travel at least two blocks back.

- [ ] Expected: detection saves the dominant north/south or east/west axis for that crop and profile.
- [ ] Run `/farmlane status`. Expected: it reports the crop, direction and two measured bounds.
- [ ] For manual placement, stand at one end and run `/farmlane start <crop> <northsouth|eastwest>`, then stand at the other end and run `/farmlane end <crop>`. Expected: that specific crop receives the two new bounds.
- [ ] Run `/farmlane set <crop> <direction> <min> <max>`. Expected: exact numeric bounds can also be assigned manually.
- [ ] Configure a second crop. Expected: switching held tools selects each crop's own saved lane.

### Guidance and controls

- [ ] Farm within a saved lane. Expected: the movable Farming Lane HUD reports remaining distance and ETA toward the end you are approaching.
- [ ] Pause, move very slowly, then resume normal speed. Expected: Paused, Too slow and Calculating replace ETA until speed stabilizes.
- [ ] Approach the end within the configured warning time. Expected: enabled title/chat channels fire once and the sound repeats at its configured interval.
- [ ] Use `{crop}`, `{distance}` and `{time}` in the message. Expected: each variable resolves to live lane data.
- [ ] Enable corner waypoints. Expected: both bounds render at the player's current cross-axis position; disabling them removes all world markers.
- [ ] Run `/farmlane ignore <crop>`. Expected: missing-lane reminders toggle only for that crop.
- [ ] Run `/farmlane clear <crop>`. Expected: only that profile and crop's lane is removed.
- [ ] Leave the Garden or disconnect. Expected: the HUD, notifications and world markers stop immediately, while saved lanes remain.
- [ ] Verify movement and turns remain manual. Expected: the feature never changes movement, aiming, clicks or packets.

## Garden DNA Analyzer

### Board recognition and solution

1. Enable Hercules and `DNA Analyzer Solver`.
2. Open any Garden DNA Analyzer board.

- [ ] Wait for all 36 colored DNA slots to load. Expected: the next two manually clickable slots receive distinct green/cyan highlights labelled 1 and 2.
- [ ] Compare every column. Expected: the solver activates only when each column contains red, green, blue and yellow exactly once.
- [ ] Click the two highlighted slots manually. Expected: the board update is rescanned and a new minimum-swap pair is highlighted.
- [ ] Continue until complete. Expected: the HUD counts remaining swaps down and shows Solved when no swap remains.
- [ ] Close and reopen the board. Expected: all previous board state resets and the new board is solved independently.
- [ ] Open an unrelated inventory whose title does not end in ` DNA`. Expected: no highlights, tooltip changes, click blocking or HUD.

### Configuration and safeguards

- [ ] Toggle numbered order, board darkening, HUD and hidden tooltips separately. Expected: each visual behavior changes independently.
- [ ] Change first, second and non-selected ARGB colors. Expected: only the corresponding overlay color changes.
- [ ] Disable end-column swaps. Expected: the solver treats the first and last columns as fixed; restore it afterward because the current live rule allows end swaps.
- [ ] Click slot 49 with close protection enabled. Expected: the accidental close action is blocked with local feedback.
- [ ] Enable wrong-click blocking and click a non-highlighted DNA slot. Expected: the click is swallowed and optional feedback explains why.
- [ ] Disable wrong-click blocking. Expected: all board clicks remain fully manual and pass through normally.
- [ ] Run `/dnasolver status` and `/dnasolver option <name> <on|off>`. Expected: state is reported and supported options persist.
- [ ] Verify ordinary left-clicks are still ordinary clicks. Expected: Constellation never rewrites them to middle click and never sends a solver click itself.

## Garden hoe levels

### Level and progress

1. Enable Hercules and `Hoe Level Display`.
2. Hold a specialized farming tool with `levelable_lvl` and `levelable_exp` data in the Garden.

- [ ] Compare the displayed level and tool XP against the item. Expected: current/next level and XP threshold match.
- [ ] Switch to an ordinary item. Expected: the Hoe Level HUD disappears immediately.
- [ ] Leave the Garden while holding the tool. Expected: the display and sound filter stop.
- [ ] Toggle level, progress, percentage, remaining, XP rate, ETA, upgrade and wrong-crop rows independently. Expected: only the chosen rows appear.
- [ ] Gain tool XP. Expected: XP/min and ETA stabilize from actual item-data deltas, then return to Waiting after the configured inactivity reset.
- [ ] Farm a crop that does not match the held specialized tool. Expected with Wrong Crop enabled: a red warning appears.

### Upgrades, overflow and sound

- [ ] Let XP exceed the current threshold before upgrading. Expected: Upgrade Required appears below level 40 and Overclock Required appears from level 40 onward.
- [ ] Trigger a normal hoe level-up with sound muting enabled. Expected: only the exact portal-travel level-up sound is suppressed; unrelated portal sounds remain.
- [ ] At level 50, trigger a Tool Exp Capsule overflow message. Expected: the message gains the resulting level and that profile/tool UUID gains one overflow level.
- [ ] Switch profiles or tools. Expected: overflow values do not leak between profiles or UUIDs.
- [ ] Disable overflow display. Expected: tracked overflow remains saved but is not added to the visible level.
- [ ] Run `/hoelevel status`. Expected: it reports level, XP, overflow state and sound state.
- [ ] On a level-50 tool, run `/hoelevel set 55`, then `/hoelevel reset`. Expected: the visible overflow level changes deliberately and then returns to its base.
- [ ] Try setting overflow on a tool below level 50 or without a UUID. Expected: it is rejected without modifying another tool.

## Garden crop milestones

### Initial synchronization

1. Enable Hercules and `Crop Milestone Progress`.
2. Enter the Garden and run `/cropmilestone sync`.
3. Keep the `Crop Milestones` menu open briefly.

- [ ] Inspect all 13 crop items. Expected: their exact `Total` values are learned for the current profile.
- [ ] Hold a crop-specific tool after closing the menu. Expected: the Crop Milestone HUD shows that crop, its current and target tiers, progress, ETA/rates when available, and no other crop.
- [ ] Run `/cropmilestone status`. Expected: it reports the same crop, tier, counter and remaining amount.
- [ ] Switch SkyBlock profiles and synchronize again. Expected: each profile retains independent counters and goals.

### Live tracking and display

- [ ] Farm with a Cultivating or counter-bearing tool. Expected: the saved milestone counter rises from actual tool-counter deltas and the measured crops/second stabilizes.
- [ ] Farm Wheat. Expected: seed-inclusive Cultivating deltas are converted to Wheat milestone progress instead of overcounting.
- [ ] Stop for the configured reset time. Expected: rate becomes zero and ETA says Waiting without losing progress.
- [ ] Toggle tier, progress, percentage, ETA, crops/second, crops/minute, crops/hour and blocks/second rows. Expected: every row changes independently.
- [ ] Change `Crop Milestone Row Order`. Expected: valid row names reorder the HUD without changing the underlying values.
- [ ] Enable Show Without Tool, farm briefly, then change slots. Expected: the last actively farmed crop can remain visible; disabling it restores held-tool-only behavior.

### Goals and warnings

- [ ] Run `/cropmilestone goal wheat 46`. Expected: Wheat progress and ETA target the absolute cumulative Tier 46 requirement.
- [ ] Enable Max Tier. Expected: crops without a higher custom goal target Tier 46 instead of only the next tier.
- [ ] Run `/cropmilestone cleargoal wheat`. Expected: Wheat returns to the configured next/max-tier behavior.
- [ ] Use `/cropmilestone set wheat <counter>` only as a recovery test, then reopen Crop Milestones. Expected: the menu restores the authoritative server total.
- [ ] Enable close warning, title and sound with a deliberately reachable goal. Expected: one warning occurs inside the configured final seconds and does not repeat for the same target.

### Crop Milestones menu

- [ ] Reopen Crop Milestones with inventory tiers and average enabled. Expected: every recognized crop has a tier number and the menu shows the average across all 13 crops.
- [ ] Toggle inventory overflow. Expected: displayed tiers cap at 46 when disabled and may exceed it when enabled.
- [ ] Hover a crop item with total-progress tooltip enabled. Expected: progress percentage and exact current/required totals to Tier 46 appear before Rewards.
- [ ] Leave the Garden. Expected: HUD, menu additions and tracking all stop immediately.

## Garden commands

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
- [ ] Run `/cropstart setat carrot 12 73 -45`. Expected: Carrot receives a block-centred start at `12.5, 73.0, -44.5` without requiring you to stand there.
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

### Farming Toolkit crop icons

- [ ] Enable Hercules and run `/toolkiticons`. Expected: status reports Toolkit crop icons on.
- [ ] Open the exact `Farming Toolkit` menu. Expected: farming tools in slots 10-16 and 20-24 render as their crops; unrelated items and player-inventory slots remain unchanged.
- [ ] Hover every replaced icon. Expected: the original farming-tool name and lore remain available because the real slot item is not replaced.
- [ ] Hold a crop-specific farming tool before opening the menu. Expected: the matching crop icon receives a green outline when held highlighting is enabled.
- [ ] Run `/toolkiticons option labels on`. Expected: compact crop abbreviations appear, including distinct NW, SC, SF, MF and WR labels.
- [ ] Test `/toolkiticons option background off`, `decorations on`, `highlight off` and `/toolkiticons color held FF55FFFF`. Expected: each presentation setting changes independently and persists.
- [ ] Click, shift-click and close the menu normally. Expected: no click is blocked, rewritten or generated.

### Garden rod-break protection

- [ ] Enable Hercules, enter the Garden and run `/norodbreak`. Expected: protection and the default sneak bypass report on.
- [ ] Hold any vanilla-based SkyBlock fishing rod and left-click a crop or solid block without sneaking. Expected: the block does not take damage, no attack packet is sent and the action bar explains the protection.
- [ ] Keep holding attack against the same block. Expected: block-damage progress never begins and feedback is throttled rather than flooding the HUD.
- [ ] Right-click with the rod. Expected: casting and retracting work normally.
- [ ] Attack an entity with the rod. Expected: entity attacks are not changed.
- [ ] Hold sneak and left-click a block. Expected: the deliberate bypass permits normal block interaction when `/norodbreak option sneak on`.
- [ ] Run `/norodbreak option sneak off` and repeat while sneaking. Expected: the block remains protected.
- [ ] Test action-bar, chat and sound options plus `/norodbreak cooldown 0` and `2`. Expected: each feedback channel and throttle persists independently.
- [ ] Leave the Garden and left-click a block with the rod. Expected: Constellation does not cancel the interaction.
- [ ] Run `/norodbreak resetcount`. Expected: only the local session prevention count resets.

### Carrolyn fetch helper

- [ ] Enable Hercules and hover an item whose lore says to bring 3,000 to Carrolyn. Expected: the tooltip offers click-to-navigate help and shows the matching inventory total out of 3,000.
- [ ] Hover an ordinary item. Expected: no Carrolyn lines are added.
- [ ] While outside the Crimson Isle, hold a recognized item and left- or right-click into the world. Expected: the click itself remains normal and chat offers a deliberate `[Warp there]` action.
- [ ] Click the warp action. Expected: `/warp crimson` runs only after your click; navigation remains armed during the island change.
- [ ] On the Crimson Isle, start navigation with the item or `/carrolyn start`. Expected: a magenta box, beam, tracer and distance label point to Carrolyn near `(0, 104, -804)`.
- [ ] Approach within four blocks. Expected: navigation stops and the optional arrival message appears.
- [ ] Run `/carrolyn stop`. Expected: all Carrolyn world guidance immediately disappears.
- [ ] Test tooltip, owned, tooltipwarp, click, warp, box, beam, line, label, distance, throughwalls, autostop, startchat and arrivalchat options independently.
- [ ] Test `/carrolyn range`, `beamheight`, `stopdistance` and `color`. Expected: bounded values persist and affect only this helper.
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

### Mining tool state

1. Enable Aquila, `Mining Tool Suite`, `Drill Fuel Hud`, `Pickonimbus Hud`, and `Mining Tool Item Bars`.
2. Enter a mining area and hold a drill with fuel. The movable `Mining Tool` HUD should show its cleaned name, exact current/max fuel, percentage, and text bar. Its inventory slot should show the same proportional durability bar.
3. Hold a Pickonimbus. The HUD and slot bar should use `pickonimbus_durability`; a fresh item without that field should safely show 2,000/2,000. A pre-September-2024 or still-over-2,000 legacy item should use its historical 5,000-use maximum.
4. Use `/miningtools option heldonly off`, then test `hotbar` and `inventory` scope options. Held selection must win; otherwise the first eligible enabled scope is used.
5. Use `/miningtools threshold 10`, lower a tool through the threshold, and verify each enabled chat/title/sound channel fires once for that item. Re-equipping it must not repeat the warning until its value rises above the threshold.
6. Test `hidefull`, the independent drill/Pickonimbus switches, mining-only scope, HUD row switches, `/miningtools barwidth 5-30`, and `/miningtools color good|warning|danger AARRGGBB`.
7. Leave Hypixel or disable Aquila. The HUD and custom durability bars must disappear without affecting ordinary vanilla durability bars.

### Fossil Excavator solver

1. Enable Aquila, `Fossil Helper`, `Fossil Solver Suite`, and `Fossil Solver Hud`, then open an active `Fossil Excavator` board.
2. With at least 18 starting charges, verify the first recommended tile is row 3, column 5. With fewer charges, the first tile is the same but subsequent misses follow the separate low-charge sequence.
3. The recommended dirt should receive the green best highlight and its percentage. Other possible dirt should receive probability-scaled blue highlights when enabled.
4. After uncovering a fossil tile, verify the recommendation changes from the opening sequence to evidence-based solving. Patterns, minimum remaining tiles, charges, possible fossil types and next probability must update without reopening the menu.
5. Hover board tiles with tooltip hiding disabled. Dirt and fossil tooltips should show possible patterns, minimum tiles, tile probability and the fossil name once only one type remains.
6. Test `Hide Dirt Tooltips` and `Hide All Tooltips` independently. Neither may hide tooltips outside the exact Fossil Excavator board.

### Fossil Excavator profit tracker

1. Enable Aquila, Fossil Helper, Fossil Profit Suite and Fossil Profit Hud, then finish one excavation in the Fossil Research Center.
2. Confirm exactly one excavation is recorded and each indented reward has the correct amount. An empty excavation must still count once.
3. Confirm one Suspicious Scrap is deducted, Fossil Dust uses one five-hundredth of the current Scrap price and Glacite Powder has no coin value.
4. Compare Tusk, Webbed, Clubbed, Spine, Claw, Footprint, Helix and Ugly Fossils plus gemstone, essence, enchanted material and enchanted-book rewards. Unknown or temporarily unpriced loot must mark totals partial instead of acting like zero-value profit.
5. Run `/fossilprofit status`, change rows, price source and every sorting mode. Values and order must update without changing stored amounts.
6. Toggle item names, amounts, values, excavations, Scrap, Dust, Powder, total, per-excavation, hourly, uptime and recent rows independently.
7. Enable the per-excavation summary and set warning thresholds. Confirm chat, title and sound channels fire only for a complete value meeting their own threshold.
8. Switch between session and profile persistence, change profiles and reconnect. Session data must reset only when requested; profile data must remain isolated and survive restart.
9. Use `/fossilprofit reset` and `/fossilprofit clearprofile` in their matching modes. Only the current tracker view or current profile may be cleared.
10. Move and resize Fossil Profit in `/cn hud`. With research-center-only visibility enabled, it should appear in the excavator menu, in the recent window after a result, or at the Research Center, and nowhere unrelated.

### Crystal Hollows powder-chest timer

1. Enable Aquila, Mining Highlights, Treasure Chest ESP and Powder Chest Timer, then uncover a chest in the Crystal Hollows.
2. Confirm the chest appears once with approximately 60 seconds remaining. Its timer must count down smoothly and the HUD count must agree.
3. Stand near another player uncovering a chest. Their ordinary block update must not become yours unless the exact level-up discovery sound arrives inside the licensed 200-millisecond window.
4. Wait for a tracked chest to expire, break, disappear or open. Every highlight, timer, route line, lock state and HUD count must clear.
5. Switch `/mininghighlights linemode oldest`, `nearest` and `none`, then change `linecount`. Oldest must prioritize expiry; nearest must prioritize distance; none must draw no route.
6. Toggle dynamic/static color and set timer, good, caution and danger ARGB colors. Dynamic chests must progress green through amber to red without changing the lock highlight color.
7. Toggle chest outline/lock ESP off while leaving Powder Chest Timer on. Timer tracking, labels, highlights and HUD must continue independently.
8. Toggle timer highlight, label, route, through-wall and HUD count/oldest/nearest rows independently.
9. Open Heart of the Mountain and inspect Great Explorer. `/mininghighlights` should learn level 0-20; max-only mode must fail closed until level 20 is known.
10. Toggle discovery and opening sound muting separately. Only exact unit-volume, pitch-one level-up or chest-open sounds in Crystal Hollows may be affected.
11. Move and resize Powder Chests in `/cn hud`. It must be editable during its visibility grace, retain its own placement and disappear outside Crystal Hollows.

### Ordered mining routes

1. Copy a Coleweight route array to the clipboard and run `/ordered import`. Confirm its numeric `options.name` order is respected even when the JSON array is shuffled.
2. Run `/ordered export`, clear the loaded route, then re-import the exported clipboard. Every coordinate, order and custom label must round-trip.
3. Import a plain route containing one `x y z optional label` waypoint per line. Invalid JSON, gaps/duplicates in numbered entries, over-5,000 routes and out-of-world coordinates must fail without replacing the current route.
4. Run `/ordered save <name>`, reconnect, change profiles and load it again. Routes and remembered active names must remain isolated per profile and survive restart.
5. Approach the route after loading. The nearest waypoint should start current; entering its configured range should advance cyclically without moving or aiming the player.
6. Test `/ordered skip`, `back` and `skipto`, including negative/wrapped progression. Each must change only the displayed route state.
7. Enable forward skipping, approach a later waypoint and confirm it jumps only forward to the closest later point inside range.
8. Use `add`, `addat`, `move`, `delete` and `label` at the first, middle and last positions. Numbers must remain contiguous and repeated coordinates must remain allowed.
9. Edit a route, then try ordinary unload and automatic area/world/Mineshaft unload. Unsaved edits must be retained with a warning; `/ordered unload force` may deliberately discard them.
10. Toggle fill/outline, thickness, wall mode, distance, number, custom label, next count and all six colors independently.
11. Test setup mode and setup range. Nearby non-active points and the current-to-next eye-height line must appear without changing the route.
12. Test Show All separately. Every point should use the all color and ordinary trace/setup behavior should not overlap it.
13. Save a route with a real Mineshaft code such as `JASP_1`, enable shaft auto-load and enter that exact scoreboard type. Only the matching profile route may load.
14. Enable auto-unload on area changes and Mineshaft exit. Saved clean routes may unload; dirty routes may not be destroyed.
15. Move and resize Ordered Mining Route in `/cn hud`, then toggle every route/progress/current/next/distance row. It should remain mining-scoped unless that scope is disabled.
7. Enable `Fossil Protect Wrong Clicks`; a dirt tile other than the recommendation must be blocked. The recommendation must remain clickable, and holding either Control key must bypass protection when that option is enabled.
8. Use `/fossilsolver option`, `/fossilsolver color best|probability|impossible AARRGGBB`, and `/fossilsolver reset` to verify every saved display, tooltip, protection, HUD and Muncher control.
9. Trigger each Fossil Muncher riddle. The exact answer should appear locally; original replacement must follow `Fossil Muncher Replace Riddle`, and unknown text must pass through unchanged.
10. Close the menu, leave Hypixel, or disable the suite. Board state, HUD, overlays and protection must clear immediately.

### Test

- [ ] View commissions. Expected: names/progress match tab and destination guidance points to the selected commission.
- [ ] Open the Forge. Expected: slots and finish times are captured and persist after relaunch.
- [ ] Complete Fetchur/Puzzler. Expected: exact daily answer appears only in the relevant area.
- [ ] Use a Wishing Compass. Expected: guidance updates from the compass result and does not persist into another island.
- [ ] Open Crystal Nucleus information. Expected: owned/missing crystals agree with game state.
- [ ] Enter a Glacite Mineshaft. Expected: cave-in/cold and pity information appears.
- [ ] Find/loot a corpse. Expected: waypoint, key count and profit update once.
- [ ] Test a fossil puzzle. Expected: advisory solution matches the board without automatic clicking.
- [ ] Spawn your own Golden or Diamond Goblin in a mining island. Expected: only the goblin paired with your spawn message receives the configured green guidance.
- [ ] Let another player spawn a goblin nearby. Expected: it is not claimed or highlighted as yours.
- [ ] Toggle goblin box, beam, line, label, distance and through-walls independently. Expected: each changes only its own presentation and the optional line remains off by default.
- [ ] Reach at least the configured heat threshold below the configured Crystal Hollows height. Expected: only the exact high-heat wolf pant is muted; other mob and environment sounds remain audible.
- [ ] Repeat above the configured height, below the heat threshold and outside Crystal Hollows. Expected: the pant is not filtered.
- [ ] In the Dwarven Mines, approach gray, light-blue and light-gray carpet placed directly over sea lanterns in ore veins. Expected: only those carpets receive the thin configured fill.
- [ ] Compare ordinary carpet, supported carpet away from a sea lantern and the same blocks outside the Dwarven Mines. Expected: none are highlighted.
- [ ] Uncover a Crystal Hollows treasure chest. Expected: the nearby chest created after the exact uncover message receives an outline; unrelated existing chests do not.
- [ ] Look directly at the active chest during lock picking. Expected: recent CRIT particles on that chest average into one small lock target.
- [ ] Trigger a correct lock, a failed lock and chest-open progression. Expected: correct sounds advance only the targeted chest, failure resets its current progress, and the label never exceeds the learned total.
- [ ] Stop looking at the chest, let the particle expire, remove/open the chest, leave the Hollows and reconnect. Expected: targeting, particles, progress and cached chests clear at the appropriate boundary.
- [ ] Run `/mininghighlights` and test each option, radius, scan interval, association window/range, particle lifetime and ARGB color command. Expected: each setting persists and changes only its stated behavior.
- [ ] Hold a tool whose lore contains the Pickobulus ability and aim at exposed mining blocks. Expected: outlines show the same blocks the ability will affect inside its licensed 8-by-8-by-8 calculation volume.
- [ ] Aim at fully enclosed blocks or into empty space. Expected: enclosed blocks are excluded and empty targeting reports `Not looking at a block` without drawing stale outlines.
- [ ] Compare Gold Mine, Deep Caverns, Dwarven Mines, Crystal Hollows, Glacite Tunnels and a Glacite Mineshaft. Expected: each uses its own licensed conversion/breakable rules rather than sharing one generic block list.
- [ ] In Crystal Hollows, aim at gemstone glass and each Mithril block variant. Expected: the HUD forecasts gemstone blocks and the correct one, three or five Mithril Powder weights.
- [ ] In Glacite Tunnels, aim across ice, gemstones, titanium, Mithril, Umber and Tungsten blocks. Expected: HUD ore counts and two/four-point Mineshaft pity weights match the outlined blocks.
- [ ] In a Glacite Mineshaft, compare stone and other exposed blocks. Expected: all licensed breakable blocks outline while only stone contributes Hardstone.
- [ ] Put Pickobulus on cooldown with `Pickobulus: <time>` visible in the player list. Expected: the HUD shows cooldown or hides according to the saved option, and world prediction does not remain stale.
- [ ] Remove the Pickobulus tool or leave a supported mining area. Expected: world outlines and visible state clear on the next tick.
- [ ] Move and resize Pickobulus in `/cn hud`, then toggle total, pity, drops, powder and errors independently. Expected: each row and the HUD placement remain independent of the world preview.
- [ ] Run `/pickobulushelper` and test preview, HUD, cooldown, row, wall, range and ARGB controls. Expected: every setting persists without using the ability or sending a packet.
- [ ] Open the exact `Commissions` menu with incomplete and completed entries. Expected: only slots whose lore contains the exact `COMPLETED` line receive the configured fill.
- [ ] Open another book-based menu containing similar text. Expected: no commission highlight appears outside the exact menu title.
- [ ] Toggle the optional commission `DONE` label and change its ARGB fill. Expected: the label and fill update independently without changing any slot or click.
- [ ] Complete a commission outside a Glacite Mineshaft. Expected: one local message includes the parsed commission name and a clickable `[Call Mismyla]` action.
- [ ] Click the Mismyla action. Expected: the user click runs `/call mismyla`; receiving the completion message alone sends no command.
- [ ] Complete a commission inside a Glacite Mineshaft. Expected: no Mismyla call is suggested because the source deliberately excludes that location.
- [ ] Receive ordinary Fred dialogue and a genuinely obfuscated bad-signal Fred line. Expected: only the component containing obfuscated text receives `[Call Fred]`.
- [ ] Toggle original-line replacement separately for Mismyla and redial. Expected: each original server line is preserved or hidden independently while the local action remains.
- [ ] Customize Mismyla with `{commission}` and redial with `{npc}` using `/miningconveniences message`. Expected: placeholders resolve locally and saved text survives relaunch.
- [ ] Test every `/miningconveniences option`, hover and color control. Expected: each changes only its named behavior and no action is automatic.
- [ ] Enter Mines of Divan and hold the Metal Detector. Expected: a nearby `Keeper of Diamond/Lapis/Emerald/Gold` label establishes one consistent center.
- [ ] Stand still for the configured number of repeated `TREASURE: <distance>m` actionbar readings. Expected: candidates are created or narrowed only after stable readings.
- [ ] Move to a meaningfully different position and stand still again. Expected: the candidate list shrinks using the new sphere and configured tolerance.
- [ ] Compare solver output with all visible keeper orientations. Expected: every keeper derives the same Mines of Divan center and the 42 licensed offsets remain aligned.
- [ ] Test before keeper labels load. Expected: the bounded fallback ring produces possible locations without freezing the client; once a keeper loads, future treasures use the known offsets.
- [ ] Reach one remaining candidate. Expected: exact treasure color, box, beam, optional player line, label and distance identify the chest position.
- [ ] Test two through eight candidates. Expected: possible markers render with their separate color and the player line remains exact-location only.
- [ ] Produce more than the configured maximum candidates. Expected: rendering fails closed while chat still reports the count for another reading.
- [ ] Collect a treasure whose message contains `with your Metal Detector`. Expected: candidates clear and optional local search time reports once.
- [ ] Trigger an unrelated `You found` message. Expected: it does not reset the Metal Detector search.
- [ ] Leave Crystal Hollows and return. Expected: center, samples, candidate positions and timer do not leak across areas.
- [ ] Run `/metaldetector` and test sample count, maximum waypoints, tolerance, colors and every presentation/alert option. Expected: each persists and no interaction is generated.
- [ ] In the Garden, run `/cropstart set <crop>` and `/cropstart setat <crop> <x> <y> <z>` for two different crops. Expected: each crop retains its own manual location independently.

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

- [ ] Enable Lyra, `auctionHelper` and `auctionOutbidAlert`, then have another player outbid one of your auctions. Expected: the original Hypixel chat remains, the configured local title appears and the configured sound plays once.
- [ ] Toggle `auctionOutbidTitle` and `auctionOutbidSound` independently, then change `auctionOutbidTitleText` and `auctionOutbidColor`. Expected: only the selected presentation channels change; unrelated `[Auction]` messages never trigger it.
- [ ] Run `/auctionhelper option outbid off|on`, `/auctionhelper option outbidtitle off|on` and `/auctionhelper option outbidsound off|on`. Expected: each setting persists and reconnecting clears only transient duplicate-message state.
- [ ] Open `Your Bazaar Orders` or `Co-op Bazaar Orders` with at least one real buy order and sell offer. Expected: order type comes from the exact BUY/SELL item name, not its slot row; filled, partial, expired and expiring states retain their existing markers.
- [ ] With fresh Bazaar API prices available, place a buy order below the highest buy price or a sell offer above the lowest sell price. Expected: the order receives the configured `O` marker and background; an equal/competitive order receives `M` when enabled.
- [ ] First open an already-outbid order. Expected: it is marked but does not immediately alert. Leave it tracked, let the market move from competitive to outbid, and wait up to five seconds. Expected: one configured chat/title/sound alert fires with `{item}`, `{type}`, `{price}` and `{market}` resolved.
- [ ] Enable `bazaarCompetitiveAgainAlert`, then let the order become competitive again. Expected: the configured recovery message appears once. Repeated API refreshes in the same state do not repeat either alert.
- [ ] Toggle `/bazaarhelper option outbid`, `outbidmarker`, `matchedmarker`, `outbidchat`, `outbidtitle`, `outbidsound` and `recovered`. Expected: each affects only its documented channel; profile changes and reconnects discard old order snapshots.
- [ ] Enable Phoenix and `wardrobeKeybinds`, then open Wardrobe, Armor Sets and Equipment Sets. Expected: the helper activates only in the enabled exact menus.
- [ ] With `wardrobeKeyStyle` set to `HOTBAR`, press each configured hotbar key over slots 1 through 9. Expected: the corresponding visible set is selected once.
- [ ] Run `/wardrobekeys style number`. Expected: the physical number-row keys select sets even if Minecraft hotbar keys were rebound.
- [ ] Run `/wardrobekeys style custom`, then bind Wardrobe Slot 1 through 9 in Minecraft Controls. Expected: keyboard and mouse bindings work and unbound slots show no `Unknown` label.
- [ ] Use the Wardrobe Previous Page and Wardrobe Next Page controls. Expected: A and D click the actual previous/next menu buttons by default, subject to the configured cooldown.
- [ ] Test an empty, locked or unavailable set. Expected: no menu click occurs; the original key is consumed only when `wardrobeConsumeInvalidKeys` is enabled.
- [ ] Press the key for the currently equipped set with `wardrobePreventUnequip` enabled. Expected: it remains equipped and optional local feedback explains why.
- [ ] Disable prevent-unequip and bind Wardrobe Unequip. Expected: the explicit key unequips the active set; ordinary set keys still select their set.
- [ ] Run `/wardrobekeys swap 1 2`, enable the swap option and bind Wardrobe Swap. Expected: the key selects slot 2 when slot 1 is equipped and otherwise selects slot 1.
- [ ] Bind Wardrobe Open. Expected: it deliberately sends `/wardrobe` once when pressed on Hypixel and never opens anything automatically.
- [ ] Toggle labels, label position, label color, sound, feedback and cooldown. Expected: each option persists and changes only its documented presentation or input behavior.
- [ ] Enable Phoenix and `slotBinding`, open the player inventory and hold the Slot Binding control over a hotbar slot. Move to a main-inventory slot and release. Expected: the pair is saved with matching colored borders.
- [ ] Bind several main-inventory slots to one hotbar slot. Shift-left-click each main slot. Expected: it swaps with the bound hotbar slot and becomes that hotbar slot's remembered target.
- [ ] Shift-left-click the multi-bound hotbar slot. Expected: it swaps with the last used target, or the first configured target when none has been used.
- [ ] Press and release the binding control over a bound slot. Expected: every binding on a hotbar slot, or the selected single inventory binding, is removed.
- [ ] Run `/slotbind gui`. Expected: the editor shows all 27 inventory and nine hotbar slots; left-click selects, middle-click cycles a binding color and right-click unbinds.
- [ ] In the editor, add, select and two-step-delete profiles. Expected: the last profile cannot be deleted and no profile disappears on a single accidental right click.
- [ ] Run `/slotbind create garden current` while the Garden area is detected, then create/select a global profile. Expected: dynamic profiles select `garden` in the Garden and the global profile elsewhere.
- [ ] Run `/slotbind export`, then `/slotbind import`. Expected: a validated copy appears under a collision-safe imported name; malformed or oversized clipboard content is rejected.
- [ ] With protection enabled, try normal pickup, throw, collect-all, offhand and unrelated number-key swaps involving bound slots. Expected: the bound item does not move or drop.
- [ ] Enable `slotBindingAllowHotbarKeys` and press the matching hotbar key over its exact bound inventory partner. Expected: that deliberate matching swap works; unrelated targets remain blocked.
- [ ] Disable binding protection. Expected: normal inventory interactions work again while explicit shift-left swaps remain available.
- [ ] Test borders, lines, hover-only mode, always/shift/never line modes, width, fixed/per-bind colors, preview, sound and feedback independently. Expected: each control affects only its documented behavior.
- [ ] Enable Phoenix and `centuryCakeTimer`, then run `/centurycake reset`. Expected: `/centurycake` reports the selected profile and about 48 hours remaining.
- [ ] Turn off `centuryCakeOnlyExpired` or use `/centurycake option expiredonly off`. Expected: the movable Century Cakes HUD shows the remaining duration; profile and seconds rows follow their options.
- [ ] Run `/centurycake warning 1`, then `/centurycake duration 1` and `/centurycake reset`. Expected: settings persist without producing an immediate warning.
- [ ] Eat or refresh Century Cakes normally. Expected: each matching Yum message resets the profile timer and reports the session count once; hovering the count lists the remaining cake effects in plain text.
- [ ] Eat every distinct cake within the configured helper window. Expected: the final helper says all 16 cakes were eaten; after the window expires, the next cake begins a fresh set.
- [ ] Switch SkyBlock profiles. Expected: each profile retains its own expiry and the HUD/status follows the active profile.
- [ ] Use `/centurycake clear`. Expected: the current profile becomes Unknown without clearing any other profile.
- [ ] Set a short manual duration and warning threshold for a live expiry test. Expected: configured warning chat/title/sound fires once when crossing the threshold and expiry chat/title/sound fires once at zero; already-expired data does not alert again on every login.
- [ ] Enable Phoenix and `worldAge`, then join Hypixel and run `/worldage`. Expected: status reports the same day, time and phase as the movable World Age HUD.
- [ ] Observe the clock for at least one in-game minute. Expected: it advances from the synchronized server clock and does not restart when changing SkyBlock islands or dimensions.
- [ ] Toggle day, clock, phase, transition, real-age and raw-tick rows independently with `/worldage option <name> <on|off>`. Expected: each row appears or disappears without changing the underlying world.
- [ ] Toggle `twelvehour` and `onebased`. Expected: only clock/day presentation changes; phase and transition timing remain identical.
- [ ] Set `hypixel` off and enter a single-player world. Expected: the same display follows that world's clock; when on, non-Hypixel worlds remain hidden.
- [ ] Watch a phase boundary near tick 12000, 13000, 23000 or 24000. Expected: the countdown reaches zero, changes its target and the phase/color changes once.
- [ ] Change the four World Age colors in Phoenix settings and move/resize it in `/cn hud`. Expected: settings and HUD placement persist after restart.
- [ ] Enable Phoenix and `autoCopyScreenshot`, then press Minecraft's screenshot key. Expected: the normal screenshot file and chat link still appear, followed by the configured local copy feedback.
- [ ] Paste into an image-aware application. Expected: the clipboard contains the exact full-resolution screenshot with correct colors and orientation, not a filename or path.
- [ ] Take several screenshots quickly while another application is reading the clipboard. Expected: Minecraft remains responsive and bounded retries handle transient clipboard contention without duplicate saved files.
- [ ] Run `/screenshotclipboard`. Expected: status reports platform support plus session copy/failure counts without exposing clipboard contents.
- [ ] With retention enabled, overwrite the clipboard and run `/screenshotclipboard copylast`. Expected: the most recently captured screenshot returns to the clipboard.
- [ ] Run `/screenshotclipboard forget`, then `copylast`. Expected: it reports that no retained screenshot exists.
- [ ] Disable retention and take another screenshot. Expected: automatic copying still works, but `copylast` has no image; re-enabling retention affects subsequent screenshots.
- [ ] Toggle action-bar, chat, failure-chat and sound options independently. Expected: only the selected local feedback channels change; no server message is sent.
- [ ] Disable automatic copying. Expected: Minecraft continues saving screenshots normally and existing screenshot chat links still work.
- [ ] Enable Phoenix and `speedPresets`, then run `/speedpreset`. Expected: a searchable editor opens with default, crops, cocoa, mushroom, cane, squash and cactus values.
- [ ] Select, edit and save a preset; create another preset; delete it; then restart. Expected: valid values from 0 to 500 persist, invalid names or values show a local validation message and deletion persists.
- [ ] Run `/speedpreset reset`. Expected: only the active preset profile returns to the seven defaults.
- [ ] Run `/setmaxspeed crops` on Hypixel SkyBlock. Expected: the outgoing command becomes `/setmaxspeed 93`; unknown aliases and commands outside Hypixel are left unchanged.
- [ ] Open a Rancher's Boots speed-cap sign and type `cactus`. Expected: a local `cactus -> 464` preview appears and submitting writes `464`.
- [ ] Bind the Speed Preset Menu, Next and Previous controls plus several direct preset controls. Expected: each deliberate keypress selects exactly one saved preset and observes the configured cooldown.
- [ ] Run `/speedpreset profile test`, save a different value, then return to the default profile. Expected: the profiles retain independent maps.
- [ ] Enable automatic profiles and switch SkyBlock profiles. Expected: the active preset map follows the detected profile key and a previously unseen profile begins with defaults.
- [ ] Toggle chat, action-bar and sound feedback independently. Expected: feedback remains local and only the selected channels fire after a user-triggered speed command.
- [ ] Configure recent-HUD mode and `/speedpreset hudseconds 2`, then use a preset. Expected: selected HUD rows appear for about two seconds; permanent mode keeps them visible.
- [ ] Toggle `hudname`, `hudspeed` and `hudprofile` independently, then move and scroll-resize the Speed Preset HUD in `/cn hud`. Expected: only selected rows render and placement/scale persist.
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

- [ ] Enable Auriga, `experimentSolver` and all three experiment types, then open each exact Experimentation Table game on the Private Island. Expected: no solver activates in similarly named or unrelated containers.
- [ ] Enable Auriga and `skyblockGuideHelper`, then open `/sblevels` and browse Guide task/category pages. Expected: only entries whose exact progress lore matches an incomplete rule receive the configured missing overlay and marker; overview slot 4 and player inventory slots remain untouched.
- [ ] Visit Abiphone Contacts, Crafted Minions, Consumable Items, Jacob's Farming Contest, Story objectives, Fast Travels, Harp Songs, Essence Shops and representative one-time completion pages. Expected: each category follows its independent toggle and completed entries are not marked.
- [ ] Enable `skyblockGuideCollections` and inspect Collections. Expected: undiscovered/incomplete collection entries match the licensed lore rules; disabling the normally-off option removes only these markers.
- [ ] Open `Power Stones Guide`. Expected: only entries with `Learned: Not Yet` are highlighted, and their tooltip shows the cost of nine stones when a current Bazaar price is known.
- [ ] Left-click a missing Power Stone with `skyblockGuidePowerStoneClick` enabled. Expected: the original Guide click is cancelled and the deliberate user action opens `/bz <stone name>` once. Disabling click restores the ordinary Guide behavior.
- [ ] Toggle `/skyblockguide option menu|tasks|powerstones|collections|abiphone|minions|essence|consumables|jacob|story|onetime|highlight|marker|tooltip|price|click on|off`. Expected: each setting persists and affects only its named surface.
- [ ] Start Chronomatron. Expected: every glowing color is remembered once during replay, then slots matching the next color highlight when the timer begins.
- [ ] Click the correct Chronomatron color. Expected: step and remaining counts advance once and next/second/later colors update.
- [ ] Start Ultrasequencer. Expected: the full numbered pattern is captured during Remember, preserved during Wait, and revealed in numeric order during Timer.
- [ ] Click Ultrasequencer slots in order. Expected: the next highlight advances once per accepted click and the HUD reaches End after the final step.
- [ ] Reveal Superpairs items. Expected: remembered items remain highlighted after being covered; known pairs, the current first-click match and powerups use independent colors.
- [ ] Enable wrong-click protection and click a known-wrong Chronomatron/Ultrasequencer target. Expected: the click is blocked. Hold Control and repeat; expected: the deliberate bypass allows it.
- [ ] With known-wrong-second Superpairs protection enabled, reveal one known item then click a different known item. Expected: only the known-wrong second click is blocked; completing or bypassing a pair resets first/second state.

## Moonglade Beacon

- [ ] Enable Artemis and run `/moongladebeacon`. Expected: the solver, middle click and over-click protection report on.
- [ ] Open `Tune Frequency` on Galatea. Expected: no overlay appears before opening it, then Color, Speed and Pitch rows appear in the movable Moonglade Beacon HUD.
- [ ] Wait through several reference-pane movements. Expected: target color appears immediately, target speed resolves after at least two consistent movements, and target pitch resolves after repeated matching bass notes.
- [ ] Compare each signed number on the Color, Speed and Pitch controls. Expected: positive and negative values show the shortest direction to the target; a matching setting is highlighted green.
- [ ] Left-click a tuning control. Expected: with middle-click mode enabled, only that user click is sent as middle click; no setting changes without an input.
- [ ] Click a control already showing zero. Expected: the click is blocked with local feedback. Hold either Control key and repeat; expected: it is allowed.
- [ ] Open `Upgrade Signal Strength`. Expected: separate Normal and Enchanted HUD sections update independently, with normal controls in the upper row and enchanted controls in the lower row.
- [ ] Wear Stereo Pants and reopen either tuning menu. Expected: one local interference warning per menu session.
- [ ] Run `/moongladebeacon tolerance 200`, `/moongladebeacon samples 6` and toggle HUD/reference/current/offset/highlight options. Expected: settings save and only their intended presentation or detector tolerance changes.
- [ ] Enable `/moongladebeacon option alert on` and one or more alert channels, then match every setting. Expected: one ready alert fires and does not repeat until a setting becomes wrong again.
- [ ] Leave Galatea or close the menu. Expected: HUD and solver state disappear immediately and no other inventory is highlighted or click-modified.

## Heart of the Forest

- [ ] Enable Artemis, open `/hotf`, then run `/hotfhelper`. Expected: the exact menu is recognized and status reports the read perk count, available Whispers and unused tokens.
- [ ] Inspect unlocked enabled, unlocked disabled and locked perks. Expected: they use green, red and gray overlays respectively; unrelated menu slots are untouched.
- [ ] Inspect a partly levelled numeric perk. Expected: its current level is rendered on the slot. Maxed perks follow the reference behavior and do not receive redundant level text.
- [ ] Inspect the Heart of the Forest item. Expected: its unused Token of the Forest count is rendered when nonzero.
- [ ] Hover Sweep, Foraging Fortune, Strength Boost, Speed Boost, Luck of the Forest, Daily Wishes, Deep Waters, Efficient Forager, Collector, Forest Strength, Hunter's Luck, Galatea's Might, Essence Fortune, Forest Speed, Half Empty, Ricochet and Half Full. Expected: each uses its own authoritative cost curve and shows spent Whispers.
- [ ] Run `/hotfhelper design number`, `percentage`, then `number_and_percentage`. Expected: only the spent line presentation changes and the choice persists.
- [ ] Hold Shift while hovering a non-maxed numeric perk. Expected: the tooltip shows the cost of the next ten levels or the exact smaller number remaining before max.
- [ ] Hover an unlocked non-maxed numeric perk. Expected: current Forest Whispers and either enough-for-next or the exact shortfall appear.
- [ ] Toggle `/hotfhelper option current off`, `ten off`, `spent off`, `highlight off`, `levels off` and `tokens off`. Expected: each affects only its named overlay or tooltip section.
- [ ] Observe the movable HOTF HUD. Expected: it shows only while the exact menu is open and its Whispers, tokens, allocated, perk and maxed rows obey independent options.
- [ ] Click a perk and wait for only its lore to change. Expected: state, highlights, level text, HUD totals and tooltip costs refresh without reopening the menu.
- [ ] Close `/hotf` and open unrelated inventories. Expected: every HOTF overlay, tooltip addition and HUD row disappears.

## Foraging Tracker

- [ ] Enable Artemis and run `/foragingtracker` on Galatea. Expected: the tracker reports on, session mode is initially empty, and no data is created outside Galatea.
- [ ] Hold a real axe, then switch away from it. Expected: the HUD appears while held and remains for the configured `/foragingtracker delay <0-60>` before disappearing.
- [ ] Contribute to a Fig Tree Gift. Expected: exactly one Fig contribution is counted and whole-tree credit increases by the displayed percentage divided by 100.
- [ ] Contribute to a Mangrove Tree Gift. Expected: Mangrove is tracked independently; `/foragingtracker tree fig`, `mangrove` and `all` filter every item and metric consistently.
- [ ] Inspect a gift containing item rewards, Foraging Experience, HOTF Experience and Forest Whispers. Expected: actual hovered reward amounts enter the matching rows and metrics once.
- [ ] Receive Fig/Mangrove logs, enchanted logs or Stretching Sticks while actively foraging. Expected: positive inventory deltas are counted; opening the world with existing stacks creates no gain.
- [ ] With compact mode on, complete a gift. Expected: the multi-line original transaction is replaced by one contribution/reward summary plus only enabled bonus categories.
- [ ] Toggle uncommon, books, mobs, boosters, shards, runes and misc with `/foragingtracker option`. Expected: each affects only its compact-summary category, not item accounting.
- [ ] Verify profit with `/foragingtracker price purchase` and `sell`. Expected: the selected Bazaar side changes values and unresolved prices remain visibly partial.
- [ ] Change sorting among value, amount, name and recent and set row count from one to 50. Expected: only item rows reorder/truncate; totals remain complete.
- [ ] Enable recent, profit, per-hour, Foraging XP, HOTF XP, Whispers, whole trees, tree gifts and uptime independently. Expected: each HUD row follows its saved toggle.
- [ ] Set `/foragingtracker warning chat 5` and `title 5`, then receive one confirmed reward worth at least five million coins. Expected: configured chat/title/sound channels fire once for the gain.
- [ ] Run `/foragingtracker reset`. Expected: only the current session clears. Enable persistent mode, earn data, reconnect, then run `clearprofile`; expected: the active profile alone is removed.
- [ ] Leave Galatea during an open or completed gift. Expected: the HUD hides, the parser stops, and unrelated chat or inventory changes never enter the tracker.
- [ ] Toggle next, second, remaining, dim-wrong, labels, tooltip hiding, pair memory and pair highlights independently. Expected: each changes only its documented presentation or safeguard.
- [ ] Toggle Hypixel-only and Private-Island-only scope. Expected: exact experiment screens outside selected scope remain untouched.
- [ ] Close and reopen an experiment or run `/experiments reset`. Expected: sequence, pair and click state clears without affecting items.
- [ ] Move and resize the Experiment HUD in `/cn hud`. Expected: it appears only during a live experiment or its five-second grace and persists.
- [ ] Enable Auriga and `chocolateFactoryHelper`, then open the exact Chocolate Factory menu. Expected: no similarly named or unrelated inventory receives an overlay.
- [ ] Compare all seven rabbit upgrades and the Coach. Expected: the best-efficiency highlight follows lowest chocolate-cost per added production, while green marks the best currently affordable option.
- [ ] Enable `chocolateFactoryShowAllAffordable`. Expected: every affordable parsed rabbit/Coach upgrade receives the base affordable color, while best and best-affordable priority colors still win.
- [ ] Inspect rabbit, Coach, Prestige, Rabbit Barn, Hand-Baked Chocolate, Time Tower, Rabbit Shrine and Hitman slots with `chocolateFactoryShowLevels` enabled. Expected: parsed non-maxed levels or Hitman egg/slot state appear in the top-left; unknown and configured max levels remain clean.
- [ ] Fill the Rabbit Barn to the configured remaining-space threshold, leave a Factory milestone reward unclaimed, activate Time Tower and fill its charges. Expected: only slots 35, 53 and 39 use their independent warning colors, with full Tower taking priority over active Tower.
- [ ] Hover every ranked upgrade. Expected: added production, affordability time, optional payback and efficiency rank match the menu; zero or missing production reports Unknown.
- [ ] Enable extra stats and hover an upgrade, Prestige and Time Tower. Expected: cost per added CPS, chocolate/ETA until Prestige, Tower CPS increase, active CPS and current/max charges derive from the same menu snapshot.
- [ ] Reach a prestige requirement and inspect Hitman. Expected: prestige changes to Ready and configured egg/slot rows reflect the menu.
- [ ] Wait for normal and Golden stray Rabbits. Expected: the correct slot highlights and enabled sound fires once per appearance, then may fire again after reopening.
- [ ] Activate Time Tower normally, then use `/chocolatefactory warning 1` for a boundary test. Expected: the profile timer persists, warning fires once at one minute and expiry fires once at zero without stale-login spam.
- [ ] Run `/chocolatefactory`, `toggle`, `cleartower` and representative `option <name> <on|off>` commands. Expected: readable status and persistent independent settings.
- [ ] Run `/chocolatefactory barnthreshold 0`, then `1` and a larger safe value. Expected: Barn warning sensitivity changes without altering the actual parsed rabbit/capacity totals.
- [ ] Move and resize the Chocolate Factory HUD in `/cn hud`. Expected: it appears only while the menu or a saved active Time Tower is relevant, plus editor grace.
- [ ] Enable `hoppityCollectionStats`, open Hoppity's Collection and scroll through every page. Expected: each page is counted once, the HUD reaches `pages seen / maximum`, and completion becomes green only after all pages have actually been scanned.
- [ ] Compare several found, duplicate and missing rabbits. Expected: found count is one plus `Duplicates Found`; missing entries store zero; the Hypixel progress row comes from the menu progress bar rather than inferred local totals.
- [ ] Switch profiles and reopen the collection. Expected: rabbit, rarity, duplicate, page and progress caches remain isolated per profile and restore only that profile's state.
- [ ] Toggle rarity rows, duplicates, total, Hypixel progress and pages independently. Expected: only the selected HUD rows change, and partial page scans remain visibly partial.
- [ ] Test missing/found highlights and missing rabbits sourced from Factory milestones, Shop milestones, requirements and Golden Strays. Expected: each uses its independent toggle/color; rarity mode uses the rabbit name's authoritative menu rarity.
- [ ] Hover found and missing rabbits. Expected: the tooltip shows collection state, rarity and found count; incomplete scans warn to visit every page.
- [ ] Run `/hoppitycollection`, representative `/hoppitycollection option <name> on|off`, then `/hoppitycollection clear`. Expected: status is readable, options persist, and clear removes only the current profile's collection cache.
- [ ] Move and resize the Hoppity Collection HUD in `/cn hud`. Expected: it appears only while the exact collection inventory is open or during editor grace.

### Rabbit Hitman slot costs

- [ ] Enable Auriga, `Chocolate Factory Hitman Costs` and its HUD, then open the exact `Rabbit Hitman` inventory. Expected: purchased and remaining slot counts match the menu.
- [ ] Compare `Total paid`, `Cost left` and the next five prices with the purchasable slot lore. Expected: every number uses the authoritative 28-slot schedule and the next slot matches Hypixel.
- [ ] Open the menu during loading. Expected: an incomplete chest does not overwrite the last good profile value; the display syncs only after all 54 chest slots exist and observed prices fit the schedule.
- [ ] Switch profiles and reopen Rabbit Hitman. Expected: each profile restores only its own purchased count.
- [ ] Run `/hitmancosts`, `/hitmancosts next 3`, and toggle `purchased`, `paid`, `remaining`, `remainingcost`, `next`, `outside` and `persist` through `/hitmancosts option <name> <on|off>`. Expected: each row and cache behavior changes independently and persists.
- [ ] Run `/hitmancosts clear`. Expected: only the current profile cache clears; reopening Rabbit Hitman safely restores it.
- [ ] Move and resize `Hitman Slot Costs` in `/cn hud`. Expected: it is visible in the exact menu by default, remains chrome-free in the editor, and appears outside the menu only when `outside` is enabled and a valid profile snapshot exists.

### Hoppity event statistics

- [ ] Enable Auriga and `Hoppity Event Summary`, then collect each available normal and alternate meal egg. Expected: `/hoppitysummary` increases `Meal eggs` once per real find and ignores duplicate delivery of the same chat packet.
- [ ] Find a new rabbit and a duplicate rabbit. Expected: the proper rarity count increases; unique and duplicate totals remain separate; duplicate Chocolate matches the reward chat.
- [ ] Recover a Hitman egg, buy a Hoppity rabbit, accept Hoppity as a Garden visitor, claim a Side Dish and both milestone types, and find Rabbit the Fish where available. Expected: only the matching source counter increases.
- [ ] Stay in the exact `Chocolate Factory` inventory for at least two minutes, then close it. Expected: Factory time increases by about two minutes, does not count time in similarly named screens, and survives restart when persistence is enabled.
- [ ] Switch SkyBlock profiles and run `/hoppitysummary`. Expected: statistics do not cross profiles.
- [ ] Run `/hoppitysummary year <year>`, `current`, `show`, `rows <1-20>`, and each `/hoppitysummary option <name> <on|off>` setting. Expected: browsing never modifies historical data and every HUD section changes independently.
- [ ] Enable the summary HUD and test `eventonly` both ways. Expected: by default it appears only in Spring during Hoppity's Hunt; disabling `eventonly` allows the selected saved year outside the event.
- [ ] Run `/hoppitysummary clear` without `confirm`. Expected: nothing changes. Run `/hoppitysummary clear confirm`; only the selected profile/year is cleared.
- [ ] At the Spring-to-Summer boundary with `endsummary` enabled, verify one summary is emitted and restart/reconnect. Expected: the same profile/year is not summarized twice.
- [ ] Move and resize `Hoppity Event Summary` in `/cn hud`. Expected: only the content is shown over the translucent editor, with no extra editor chrome.

### Unclaimed Hoppity meal eggs

- [ ] Enable Auriga, `Hoppity Unclaimed Eggs` and its HUD during Spring. Expected: Breakfast/Lunch/Dinner appear on normal days and Brunch/Déjeuner/Supper on alternate days using the 7:00, 14:00 and 21:00 SkyBlock-hour resets.
- [ ] Before a meal reset, compare the displayed countdown with the server clock. Expected: it reaches zero at the exact reset and changes to `Ready`; the following cycle remains two SkyBlock days away.
- [ ] Collect each meal type. Expected: only that exact spawn cycle changes to `Claimed`; the next cycle is unaffected and profile/year data cannot leak into another cycle.
- [ ] Trigger the server's already-collected and no-nearby-eggs messages. Expected: the relevant current cycle or all current cycles synchronize, followed by a next-spawn time when `chattime` is enabled.
- [ ] Log in or switch profiles mid-event before observing an egg message. Expected: the schedule appears, but all-six-ready warnings remain silent until a real spawn, find, already-collected or no-eggs signal establishes current state.
- [ ] Enable warnings and leave all six current cycles unclaimed. Expected: enabled chat/title/sound channels fire at the configured repeat interval; each channel can be disabled independently.
- [ ] Toggle `soonest`, `readyfirst`, `claimed`, `future`, `eventtime`, `eventonly`, `chattime`, `warnings`, warning channels and `persist` through `/hoppityeggs option <name> <on|off>`. Expected: each surface changes independently.
- [ ] Run `/hoppityeggs warningminutes <1-60>` and `/hoppityeggs clear confirm`. Expected: the repeat interval persists and only the current profile's claim-cycle cache clears.
- [ ] Move and resize `Unclaimed Hoppity Eggs` in `/cn hud`. Expected: the display remains chrome-free in the editor and no row sends a warp, click or gameplay packet.

### Chocolate Factory Stray timer

- [ ] Enable Auriga and `Chocolate Factory Stray Timer`, then collect a meal egg, Hitman egg or Hoppity Garden-visitor rabbit outside the Factory. Expected: the timer arms but does not count down until the exact `Chocolate Factory` inventory opens.
- [ ] Keep the Factory open. Expected: the timer counts from 30.00 seconds to zero using real elapsed time, with no similarly named menu activating it.
- [ ] Close the Factory before zero and reopen it. Expected: the timer resets to the configured full duration rather than continuing from the premature close.
- [ ] Catch a newly appeared Stray during the window. Expected: the timer immediately disappears; an already-caught rabbit present when the Factory first opens does not falsely complete it.
- [ ] Set `/straytimer dingseconds 3`. Expected: one pling per second during the final three seconds, with no sound at zero or when `ding` is disabled.
- [ ] Use `/straytimer seconds <5-120>`, `dingseconds <0-30>`, `reset`, and options `hud`, `ding`, `hundredths`, `blockclose`, `blockslots`, and `shift`. Expected: each setting changes independently and persists.
- [ ] Enable close protection during an active timer. Expected: Escape and normal destructive slots 47-51/53 are blocked with clear local feedback; holding either Shift key bypasses both when enabled.
- [ ] Disable `blockslots` while leaving `blockclose` enabled. Expected: destructive clicks are sent normally, while other close attempts remain protected.
- [ ] Move and resize `Stray Timer` in `/cn hud`. Expected: it appears only for an active timer in the exact Factory and remains chrome-free in the editor.

### Hoppity egg locations and Egglocator

- [ ] Enable Auriga, `Hoppity Egg Waypoints`, all-waypoint display and hide-collected during Spring with a ready meal egg. Visit each supported island. Expected: its authoritative candidate locations appear; unsupported areas and periods with no ready egg show nothing.
- [ ] Verify Hub has 17 candidates and every other supported island has 15. Expected: `/hoppitywaypoints` reports the same island total and no coordinates leak between islands.
- [ ] Collect an egg within the configured claim radius. Expected: the nearest authoritative location is saved for only the current profile and is hidden or recolored according to `hidecollected` and `collected`.
- [ ] Collect an egg farther than the claim radius from every known coordinate. Expected: no location is guessed silently and a local diagnostic explains the rejected synchronization.
- [ ] Right-click the real Egglocator and observe its happy-villager trail. Expected: after at least four contiguous particles, the cubic fit snaps to one authoritative island location and `guessonly` replaces the broad candidate set with that single guess.
- [ ] Feed an interrupted trail, particles farther apart than `particlegap`, or wait beyond `timeout`. Expected: invalid particles are ignored and stale trails do not create a guess.
- [ ] Toggle `all`, `hidecollected`, `collected`, `nearest`, `box`, `beam`, `label`, `names`, `distance`, `line`, `walls`, `locator`, `guessonly` and `persist`. Expected: every presentation and state option changes independently.
- [ ] Test `/hoppitywaypoints range`, `beamheight`, `particlegap`, `timeout`, `claimradius`, `color egg`, `color collected`, `resetlocator`, and `clear confirm`. Expected: values persist, only the current profile cache clears, and malformed colors are rejected.
- [ ] Confirm boxes occupy the exact known block, beams and labels use the selected wall behavior, and lines are drawn only for a locator guess or nearest-only mode.
- [ ] With `path` and `pathguessonly` enabled, solve an Egglocator trail on each supported island. Expected: a blue graph-following walking route starts at the player and ends at the guessed egg, while only the current island graph is loaded.
- [ ] Walk away from the route or move farther than `pathmovement`. Expected: the route recalculates without freezing the client; `/hoppitywaypoints` reports its node count and approximate walking distance.
- [ ] Disable `pathguessonly` before solving a locator trail. Expected: the route selects the nearest uncollected candidate; collected locations are skipped.
- [ ] Test `pathwidth`, `pathlookahead`, `pathmovement`, `pathinterval`, `pathlimit`, `color path`, and options `path`, `pathguessonly`, and `pathwalls`. Expected: each value persists independently and route changes recalculate safely.
- [ ] Set a deliberately small `pathlimit` on a long route. Expected: no route is drawn when the bounded search cannot reach the destination, and normal waypoint rendering remains available.
- [ ] Confirm the system never rotates, walks, warps, clicks, or sends a gameplay command.

### Active pet display

- [ ] Enable Phoenix, `Pet Display` and its HUD, then open the exact `Pets` inventory. Expected: the slot whose lore says `Click to despawn!` is highlighted and its pet name, level, rarity, held item, skin state, XP progress and real head icon synchronize.
- [ ] Visit each page of a multi-page Pets inventory. Expected: scanning non-selected pets does not replace the active pet; the selected slot remains the only highlighted entry.
- [ ] Summon and despawn a pet normally. Expected: summon chat selects the matching cached pet details when available; despawn removes only the current profile's active state and hides the HUD.
- [ ] Trigger an Autopet rule. Expected: the exact Autopet message updates name, level, cosmetic level and skin marker. The optional title fires only when enabled and respects `dungeononly`.
- [ ] Enable Hypixel's Pet widget, reconnect without opening the Pets menu and wait one second. Expected: the widget restores the active name and level; unknown rarity, item, XP and icon fields remain absent rather than guessed.
- [ ] Switch SkyBlock profiles with different active pets. Expected: each profile restores only its own saved state and a profile with no observation shows nothing.
- [ ] Reopen the selected pet after its exact level progress increases. Expected: a positive session `%/h` and ETA appear only after the second trustworthy sample; unchanged or decreasing samples do not manufacture a rate.
- [ ] Hover a PET item with `tooltip` enabled. Expected: `Total Pet XP` matches its `petInfo.exp`; malformed/non-pet items receive no extra line.
- [ ] Toggle `hud`, `icon`, `level`, `cosmetic`, `skin`, `rarity`, `item`, `xp`, `rate`, `eta`, `source`, `persist`, `highlight`, `tooltip`, `autopettitle`, and `dungeononly` through `/petdisplay option`, then test `color name|info|progress|highlight <ARGB>`. Expected: each surface changes independently and malformed colors are rejected.
- [ ] Run `/petdisplay clear confirm`. Expected: only the current profile's active-pet state clears, with no pet click, despawn command or packet generated.
- [ ] Move and resize `Active Pet` in `/cn hud`. Expected: only the icon and text are shown with no decorative panel or editor chrome.

### Collection tracker

- [ ] Enable Phoenix and `Collection Tracker`, then open `/collections`. Expected: every visible item with an exact `Total Collected` or tier-progress lore line is cached under only the current SkyBlock profile.
- [ ] Open a collection category such as Mining Collection. Expected: collection names, exact totals and next-tier goals update; roman-numeral tier suffixes do not become part of the saved collection name.
- [ ] Run `/collectiontracker track <name>` using a full or unique partial name. Expected: the synced collection becomes the HUD title; an unknown name asks you to visit Collections and does not invent a value.
- [ ] Increase that collection legitimately, then reopen its collection category. Expected: Total, Session, Last sync and Rate change only after the authoritative menu total changes.
- [ ] Leave the menu closed. Expected: the total remains cached and `Updated` ages visibly; no inventory pickup, Bazaar purchase, trade or NPC purchase changes collection.
- [ ] Run `/collectiontracker goal <amount>`. Expected: the HUD shows remaining amount and ETA only when a positive observed rate exists. Goal `0` returns to a parsed next-tier goal when available.
- [ ] Test `/collectiontracker pause`, `resume` and `reset`. Expected: paused time is excluded from rate time, resume continues the same session, and reset starts a new session from the last synchronized total.
- [ ] Switch SkyBlock profiles. Expected: totals and selections cannot leak between profiles; each profile resumes only its own saved observations.
- [ ] Toggle `hud`, `autoselect`, `persist`, `total`, `session`, `rate`, `goal`, `eta`, `lastgain` and `freshness` through `/collectiontracker option`. Expected: each row or behavior changes independently.
- [ ] Move and resize `Collection Tracker` in `/cn hud`. Expected: it follows normal HUD persistence and never sends an API request, click, command or gameplay packet.
- [ ] In the Garden, run `/cropstart set <crop>` for a crop different from the held tool. Expected: only that named crop receives the manual start and `clearstart <crop>` removes only it.
- [ ] Enable Auriga and `anvilHelper`, then open the exact SkyBlock Anvil. Expected: the helper and movable HUD remain absent from unrelated containers.
- [ ] Put two identical single-enchantment books of the same level into slots 29 and 33. Expected: state reads Matching books and the input/result colors use the configured safe colors.
- [ ] Use the same enchantment at different levels, then two different enchantments. Expected: both combinations read Mismatched books, warn once per transition and use the mismatch color.
- [ ] Try to click output slot 13 during a mismatch. Expected: the optional guard blocks it and reports why; holding Control permits that deliberate click when bypass is enabled.
- [ ] Remove one book while retaining the other. Expected: identical books in player inventory highlight and the HUD/tooltip match count updates immediately.
- [ ] Toggle `exactlevel` off. Expected: matching discovery includes the same enchantment at other levels while mismatch comparison for two inserted books remains strict.
- [ ] Toggle `playeronly` off. Expected: eligible matching books elsewhere in the container may highlight; input slots never count as matches.
- [ ] Hover inputs, output and highlighted matches. Expected: contextual state, complete left/right enchant lists and match count appear only when configured.
- [ ] Close and reopen the Anvil. Expected: parsed names, mismatch latch and match count clear rather than leaking from the prior menu.
- [ ] Run `/anvilhelper`, `toggle` and every `/anvilhelper option <name> <on|off>` family. Expected: readable status and independent persistent controls.
- [ ] Enable Auriga and `buffStatus`, then join Hypixel with tab-footer effects enabled. Expected: God Potion, Cookie Buff and active-effect count match the authoritative tab footer.
- [ ] Test compact and worded durations if available. Expected: values such as `1d 2h 3m` and `1 day 2 hours` produce equivalent countdowns.
- [ ] Switch SkyBlock profiles. Expected: saved God Potion and Cookie timers follow the active profile and never share an `unknown` profile entry.
- [ ] Let the footer refresh for several minutes. Expected: countdowns remain stable without jumping forward and config writes are not performed every second.
- [ ] Set `/buffstatus godwarning 1`, then `/buffstatus setgod 2`. Expected: the warning fires once when crossing one minute and expiry fires once at zero.
- [ ] Set `/buffstatus cookiewarning 1`, then `/buffstatus setcookie 1` for a longer test. Expected: Cookie warning/expiry channels remain independent from God Potion channels.
- [ ] Remove or expire a buff normally. Expected: a live timer crossing still alerts before the refreshed footer changes it to Inactive; stale expired data does not alert on login.
- [ ] Toggle God, Cookie, effects, unknown, expired, profile and source-age rows independently. Expected: only the selected rows change.
- [ ] Toggle warning/expiry chat, title and sound independently. Expected: each channel affects both configured buff alerts without sending server chat.
- [ ] Run `/buffstatus clear`. Expected: only the active profile's saved buff timers clear and status becomes Unknown until the next footer refresh.
- [ ] Move and resize Buff Status in `/cn hud`. Expected: its independent placement and scale persist.
- [ ] Enable Auriga and `reforgeHelper`, then open exact `Reforge Item` and `The Hex ➜ Reforges` menus. Expected: no overlay, editor or guard appears in unrelated containers.
- [ ] Enter comma-separated wanted reforges and excluded substrings in the left-side editor. Expected: both fields retain focus while typing, persist after reopening and use exclusions to override partial wanted matches.
- [ ] Repeat with `/reforgehelper include <list>`, `exclude <list>` and `clear include|exclude|all`. Expected: commands and live fields stay synchronized.
- [ ] Insert an item in Basic Reforge slot 13 and Hex slot 19. Expected: current reforge comes from its modifier metadata and appears below the item plus in the movable HUD.
- [ ] Roll until a wanted non-excluded reforge appears. Expected: match chat/sound fires once, colors change and repeated screen ticks do not replay the alert.
- [ ] Click Basic button 22 or Hex apply/candidate controls while matched. Expected: the optional guard blocks another change; holding Control deliberately bypasses it.
- [ ] Browse Hex reforge choices. Expected: applicable wanted candidates highlight, while page arrows/navigation heads and unrelated items never highlight.
- [ ] Hover the item, action button and Hex candidates. Expected: current/candidate reforge, cost and configured wanted/excluded filters appear contextually.
- [ ] Complete successful reforges and applications. Expected: session attempts increment only on authoritative success chat and spend uses the recently clicked menu cost; failed/blocked clicks do not count.
- [ ] Run `/reforgehelper reset`. Expected: attempts, spent and last-result state clear without changing filters.
- [ ] Toggle editor, overlay, candidates, guard, bypass, alerts, tooltip, item, cost, attempts, spent, last and tracking independently. Expected: each changes only its documented behavior.
- [ ] Close and reopen both menus. Expected: screen references, pending cost, match latch and editor widgets clear safely while session statistics and configured filters remain.
- [ ] Enable Apollo. Expected: Performance, Location and Movement appear in a loaded world; Vitals follows its Hypixel scope; Active Effects appears only while at least one effect is active or was visible during the HUD editor's five-second grace.
- [ ] Stand on Hypixel for at least five seconds. Expected: FPS, player-list ping and negative-ping server TPS settle to plausible values without sending an additional ping packet.
- [ ] Compare current, average, median, minimum and maximum rows while changing `/apollohud samples <seconds>`. Expected: current responds fastest and rolling statistics remain bounded to the selected window.
- [ ] Simulate poor FPS/ping or join a lagging server. Expected: configured good, warning and bad thresholds select the correct colors independently.
- [ ] Move, jump and stop. Expected: horizontal blocks per second smooths over the configured movement ticks, vertical speed is signed when enabled, and both settle near zero while stationary.
- [ ] Compare Speed stat to the SkyBlock statistic. Expected: it follows the movement-speed attribute rather than confusing it with physical blocks per second.
- [ ] Turn and move between blocks. Expected: cardinal facing, yaw/pitch, integer or decimal XYZ, dimension and local clock rows follow their individual options.
- [ ] Receive a SkyBlock action-bar update. Expected: health, mana, overflow mana, defense and effective health match the parsed values; percent mode uses the current maximum.
- [ ] Apply effects with different levels and durations. Expected: amplifier numerals, sorting, infinite filtering, row limit and warning colors follow settings.
- [ ] Toggle panels and representative rows with `/apollohud option <name> <on|off>`. Expected: no toggle changes another panel or sends anything to the server.
- [ ] Move and resize each Apollo panel independently in `/cn hud`. Expected: only visible or recently visible panels are editable and placement/scale persists.
- [ ] Enable Andromeda, enter the Rift and run `/riftguide`. Expected: status reports real time, Motes, current-profile soul count and currently parsed unbroken effigies; nothing appears outside the Rift.
- [ ] Compare the movable Rift HUD with sidebar/tab values. Expected: time counts down accurately, Motes match, positive Mote gains accumulate only in the session row and leaving the Rift clears visit state.
- [ ] Expand Rift Info in the player list and gain Motes. Expected: Lifetime matches the server widget exactly; Gained is current lifetime minus the entry baseline and Rate uses actual visit duration rather than purse balance changes.
- [ ] Leave the Rift after gaining at least the configured summary minimum. Expected: one local summary reports gained Motes, time and Motes per hour; spending Motes during the visit does not erase lifetime gains.
- [ ] Run `/riftmotes reset`, then gain more Motes. Expected: only the local visit baseline and timer reset; lifetime server data and items remain unchanged.
- [ ] Hover every available Rift item, including stacks and a Rift-exported transferred item. Expected: known items show the current NPC Motes total, optional per-item breakdown and McGrubber stacks; transferred exportables do not claim a Rift sell value.
- [ ] Trigger the McGrubber stack confirmation. Expected: the exact stack count from zero to five is learned once, saved, optionally reported locally and applies a five-percent bonus per stack.
- [ ] Open Rift Storage. Expected: the independent movable value HUD reports total Motes plus optional item/stack counts, and eligible slots above the configured threshold receive only an `M` edge marker.
- [ ] Compare the storage total manually using several stacks. Expected: each current Hypixel base price is multiplied by stack count and McGrubber bonus exactly once.
- [ ] Approach a real flying Motes Orb. Expected: no marker appears during the first validation window; a 60-90 entity-effect-particle-per-second cluster becomes a magenta orb box/label and unrelated effect particles remain untouched.
- [ ] Pick up or let an orb finish. Expected: its marker turns gray from the exact pickup chat or particle stop, then expires; changing worlds or leaving the Rift clears every candidate.
- [ ] Toggle original-particle hiding. Expected: only a validated Motes Orb's future particles are hidden, while validation particles and unrelated entity-effect particles remain visible.
- [ ] Test `/riftmotes burgers`, every `option`, every `number` and active/picked/storage colors. Expected: tracking, HUD rows, summary, tooltip, storage, orb rendering, bounds and presentation remain independent and persist.
- [ ] Move and resize Rift Storage Value in `/cn hud`. Expected: it has an independent position from the main Rift HUD and appears at runtime only while real Rift Storage values are available.
- [ ] Set `/riftguide lowtime 60` and test near one minute. Expected: enabled local chat/title/sound channels fire once when crossing the threshold and re-arm only after time rises above it.
- [ ] Run `/riftguide souls allmissing`. Expected: all 52 green missing waypoints become eligible inside the configured range; nearest-only and found visibility obey their independent options.
- [ ] Stand within four blocks of a soul and collect it normally. Expected: the closest waypoint becomes found for the active SkyBlock profile and hides immediately unless found visibility is enabled.
- [ ] Run closest-found, closest-missing, all-found and all-missing corrections. Expected: only the current profile changes and state persists after restart.
- [ ] Switch SkyBlock profiles. Expected: found-soul sets remain independent; an unknown profile starts with no fabricated collection state.
- [ ] Open the real Enigma Souls menu and visit several area pages containing both completed and missing souls. Expected: each displayed named soul synchronizes to the active profile from its exact completion lore, with one optional local summary per changed page.
- [ ] Compare completed and missing menu entries. Expected: each receives an `F` or `M` edge marker in the configured color, without changing or replacing the server item.
- [ ] Right-click a named soul in the menu. Expected: the server click is cancelled locally, that entry gains an `R` marker and a route starts to its exact named location; normal left-click Rift Guide navigation still works.
- [ ] Hover a soul entry. Expected: the tooltip names its area, current found/missing state and right-click route action.
- [ ] Run `/riftnav named tough bark`, then try an area-qualified partial name. Expected: a matching named soul routes with its name and area; unknown text fails locally without sending a command.
- [ ] Toggle Soul Sync, Sync Chat, Menu Tracking, Menu Highlights, Current Area and Area Label independently. Expected: menu authority, feedback, right-click interception, markers and world filtering each change separately.
- [ ] Run `/riftnav soul 1`, then `/riftnav nearest`. Expected: a blue path follows the actual Rift graph from the closest node to the selected or closest missing soul, followed by a bounded final segment.
- [ ] Walk along a route and deviate by several blocks. Expected: shortest path recalculates from the new nearest graph node without teleporting, moving or aiming the player.
- [ ] Test `/riftnav width`, `lookahead`, `arrival`, `beamheight`, `color` and every option. Expected: each affects only its saved presentation or arrival behavior.
- [ ] Enable automatic nearest routing. Expected: the next closest missing soul is selected locally; finding it advances to another missing soul, while no server command or interaction is generated.
- [ ] Enter each Mirrorverse section. Expected: only nearby Lava Path, Upside Down or Turbulator blocks render within the saved range; section toggles, colors, labels, line and wall mode remain independent.
- [ ] Enter Stillgore Chateau with mixed broken/unbroken effigies. Expected: only gray scoreboard-marked unbroken effigies render, using exact compact-below or full source coordinates.
- [ ] Toggle effigy box, beam, label, distance and wall mode; change range and beam height. Expected: every control changes only its matching primitive and no stale effigy remains after leaving.
- [ ] Move and scroll-resize the Rift HUD in `/cn hud`. Expected: placement and scale persist and only enabled rows appear.
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

## Scatha Mining

- [ ] Enable Aquila, Scatha Alert, Scatha Counter, Scatha Mining Suite and Scatha HUD in `/cn config`.
- [ ] Enter the Crystal Hollows and trigger a natural Worm spawn. Expected: the exact approaching chat starts a roughly 31-second HUD cooldown, then the nearby level-5 Worm is counted once and receives only the enabled local alert and world guidance.
- [ ] Trigger a natural Scatha. Expected: it is distinguished from a Worm, uses the separate Scatha color/title and increments both total and Scatha counts exactly once.
- [ ] Stand near unrelated named armor stands before and after a spawn. Expected: nothing is paired without the exact recent approaching message, the level/name signature and the licensed local-axis bounds.
- [ ] Let the cooldown expire. Expected: Ready appears and each enabled ready chat/title/sound channel fires once, not every tick.
- [ ] Toggle box, label, distance, beam, line and through-walls separately. Expected: only the active paired Worm or Scatha changes and no interaction occurs.
- [ ] Receive a Scatha Pet drop with compact replacement enabled. Expected: one local replacement preserves proven rarity and Magic Find, the original is hidden, counters update and enabled title/sound channels fire.
- [ ] Test an unrecognized or newly formatted pet message. Expected: it remains visible unchanged rather than being swallowed.
- [ ] Compare `/scatha`, HUD totals, Scatha rate, dry streak and pet rarity totals across a profile switch. Expected: persistent totals follow the profile while session state resets.
- [ ] Run `/scatha reset` and `/scatha clearprofile`. Expected: session reset leaves saved totals intact; clearprofile removes only the active profile history.
- [ ] Change cooldown, range and every `/scatha option` switch. Expected: settings persist and none sends movement, mining, attack, aim, click, chat or gameplay packets.
- [ ] Move and resize Scatha Mining in `/cn hud`. Expected: it is editable only while visible or during editor grace and keeps its independent placement.

## Crystal Hollows Waypoints

- [ ] Enable Aquila, Wishing Compass Helper, Crystal Waypoints Suite and Compass Solver in `/cn config`, then enter the Crystal Hollows.
- [ ] Use a Wishing Compass outside the Nucleus. Expected: the HUD counts the first happy-villager trail to the configured particle target and then says to move for the second use.
- [ ] Try using it again too soon or less than the configured distance away. Expected: the optional guard blocks only that invalid use and gives a local explanation.
- [ ] Move at least eight blocks in the same major zone and use it again. Expected: the second trail solves to a bounded forward intersection and creates the structure appropriate to the zone, inventory/effect and unfinished crystal.
- [ ] Repeat after moving to another major zone between readings. Expected: the old reading is discarded and the new trail becomes the first reading.
- [ ] Test in Jungle with and without a Jungle Key, and Goblin Holdout with and without King's Scent. Expected: target names switch between Temple/Odawa and Queen/Yolkar respectively.
- [ ] Finish a zone's crystal and repeat. Expected: the location is marked Unknown rather than claiming the already-completed structure.
- [ ] Send party chat coordinates naming a supported structure. Expected: valid 202-823, 31-188, 202-823 coordinates add once; ordinary structure discussion or out-of-bounds numbers do nothing.
- [ ] Enter a supported named sub-area and trigger exact linked NPC/crystal messages. Expected: the first authoritative local position records the waypoint without broad chat false positives.
- [ ] Toggle boxes, beams, lines, labels, distances, walls, nearest-only and auto-remove separately. Expected: each affects presentation/lifecycle independently and never moves or aims.
- [ ] Use `/crystalwaypoints add`, `remove`, `list`, `clear`, `resetsolver`, `color`, `tuning` and every `option`. Expected: all changes are bounded, readable and persistent where applicable.
- [ ] Run `/crystalwaypoints share <name>`. Expected: only this deliberate command sends one party message; Fairy Grotto remains blocked unless its separate sharing toggle is enabled.
- [ ] Change server or leave the Crystal Hollows. Expected: randomized-server waypoints and partial compass trails clear rather than leaking into another lobby.
- [ ] Move and resize Crystal Waypoints in `/cn hud`. Expected: its independent placement and scale persist.
- [ ] Enable Artemis and open exact `Agatha's Shop`. Expected: only actual sale offers with an Agatha Coupon cost appear; unrelated menus remain unchanged.
- [ ] Compare every displayed material count against each offer's Cost lore, including any `1k`, `1m`, prefix-count or `x<count>` form. Expected: total input cost uses the full quantity.
- [ ] Compare sell value, total cost, sale profit and profit per coupon against Bazaar prices. Expected: the arithmetic agrees, and missing market data says `partial` instead of silently becoming free.
- [ ] Run `/starlynhelper couponprice 0`, then set a known manual coupon price. Expected: zero uses market pricing; a positive value immediately changes every affected offer.
- [ ] Change input/output sources with `/starlynhelper price <input|output> <purchase|sell>`. Expected: each side changes independently.
- [ ] Test all `/starlynhelper sort` modes, row count, negative and hide-unpriced options. Expected: ranking/filtering is stable and never changes the shop.
- [ ] Toggle highlight, labels and tooltips independently. Expected: each affects only Agatha offers and labels remain readable for large or negative values.
- [ ] Move and resize the Agatha Coupon Profit HUD in `/cn hud`. Expected: it is editable only while visible or during editor grace and retains its own placement.
- [ ] Enable compact Starlyn results and finish a contest. Expected: the multi-line result becomes one local summary with bracket, points, previous best, reward location and clickable Open action.
- [ ] Enable compact personal bests and trigger both collection/Sweep and sister PB messages. Expected: each complete sequence becomes one local summary without hiding unrelated chat.
- [ ] Disconnect or change servers during a partial result sequence. Expected: no stale contest or personal-best line is reused later.
- [ ] Enable Artemis and Galatea Exploration, then approach a real Forest Node. Expected: it appears only after the node particle and three-string display signature exist.
- [ ] Leave or consume the Forest Node. Expected: its guidance disappears within five seconds and never persists after changing server or world.
- [ ] Toggle node box, label, beam, line and distance separately. Expected: each presentation changes independently and no interaction is generated.
- [ ] Enter the Forest Temple puzzle. Expected: every valid unsolved floor tile shows the minimum number of right or left clicks matching its wall tile.
- [ ] Solve one tile. Expected: it disappears when Hide Solved is enabled or reads Solved when disabled.
- [ ] Observe the temple before all tiles load or anywhere away from its exact coordinates. Expected: the solver fails closed and draws nothing.
- [ ] Approach flowering azalea used as Lushlilac. Expected: magenta guidance appears after its chunk is incrementally scanned.
- [ ] Approach Sea Lumies clusters with different pickle counts and run `/galateahelper lumies <1-4>`. Expected: only clusters meeting the selected minimum remain.
- [ ] Change a highlighted resource block. Expected: the authoritative block update adds/removes it without waiting for a reconnect.
- [ ] Toggle resource boxes, labels, beams and distances independently and change range/chunk radius. Expected: presentation and discovery bounds remain separate.
- [ ] Run `/galateahelper clear`, leave Galatea and return. Expected: cached nodes/resources clear safely and nearby loaded chunks are relearned incrementally.
- [ ] Add Sweep to the player list with `/tablist`, hold each supported axe and aim at a connected log structure. Expected: predicted blocks match the number permitted by current Sweep and tree toughness.
- [ ] Hold a non-axe or unsupported axe. Expected: no Sweep overlay appears.
- [ ] Compare Fig, Mangrove, Hub oak and ordinary Park logs. Expected: each uses its licensed location-specific log set and toughness.
- [ ] Aim at more than 35 connected logs and change `/sweephelper maxlogs`. Expected: traversal stops at the lower configured/licensed cap without frame stalls.
- [ ] With a throwable axe, aim beyond normal block reach. Expected: the separate thrown color follows the ray target and predicts half the normal log count.
- [ ] Use the throwable ability. Expected: thrown prediction pauses for one second when cooldown respect is enabled, then returns.
- [ ] Remove Sweep from the player list. Expected: prediction fails closed and the optional local `/tablist` notice appears only once per connection.
- [ ] Enable Sweep Details at Swoop and cut a tree. Expected: the movable HUD shows tree, toughness, Sweep and final logs from the complete server sequence.
- [ ] Trigger an axe-throw penalty, wrong-style penalty and both together. Expected: every penalty and correct-style hint remains in the HUD and the compact summary contains all penalties.
- [ ] Toggle compact chat off. Expected: original server detail lines remain and the local compact replacement is not sent.
- [ ] Toggle each overlay and HUD row independently, then move/resize Sweep Details in `/cn hud`. Expected: every choice persists without changing axe behavior.
- [ ] Run `/sweephelper clear` or reconnect during an incomplete detail sequence. Expected: no stale tree, penalty or log value leaks into the next result.
- [ ] Enable Artemis and Tree Cleanup, then inspect active Fig and Mangrove trees on Galatea. Expected: only decorative stripped-spruce/mangrove wood and selected leaf block displays disappear.
- [ ] Compare real world blocks and unrelated block displays. Expected: they remain rendered and unchanged.
- [ ] Toggle spruce wood, mangrove wood, mangrove leaves and azalea leaves separately. Expected: each exact display state returns or hides immediately without a reconnect.
- [ ] Disable Clean View or leave Galatea. Expected: every decorative display renders normally.
- [ ] With outside-Galatea mute enabled, trigger the tree-breaking creaking-death sound in another Hypixel foraging area. Expected: only that exact sound is muted.
- [ ] Repeat on Galatea with Galatea mute disabled and enabled. Expected: the sound remains audible by live-profile default and mutes only after enabling its separate scope.
- [ ] Listen for other creaking, block, mob and environmental sounds. Expected: none are affected.
- [ ] Run `/treecleanup`, `resetstats` and every `/treecleanup option <name> <on|off>` setting. Expected: status reports current choices and session counts, and every choice persists.
- [ ] Change world or reconnect. Expected: hidden-display diagnostics reset and no stale entity UUIDs remain.

## Rift West Village suite

Enable Andromeda, enter the Rift and travel to West Village. Run `/westvillage` first; it should report terminal, vermin and race state without sending a server command.

- [ ] Equip the Retro-Encabulating Visor. Expected: unfinished Kloon terminals receive red boxes, beams and labels; removing the visor hides them by default.
- [ ] Complete a terminal color selection. Expected: only the nearby terminal matching the confirmed color becomes persistently complete and disappears when Hide Completed is enabled.
- [ ] Run `/westvillage resetterminals`. Expected: all eight terminal waypoints become eligible again without changing server progress.
- [ ] Open `Hacking` and `Hacking (As seen on CSI)`. Expected: each row's target slot is green when correct or red when wrong, while another matching choice in that row is yellow.
- [ ] Click highlighted hacking choices. Expected: Constellation does not convert, repeat, cancel or generate the click.
- [ ] Open `Hacked Terminal Color Picker` within eight blocks of a terminal. Expected: only a choice whose lore contains that terminal's color receives a green marker.
- [ ] Hold a Turbomax Vacuum near a Rift Silverfish, Fly and Spider. Expected: exact vermin receive the configured box; unrelated armor stands and normal Silverfish do not.
- [ ] Put the vacuum away. Expected: entity highlighting hides by default. Toggle Holding Vacuum off and confirm it returns only in West Village or Infested House.
- [ ] Vacuum one of each vermin. Expected: the movable HUD increments the correct independent count. Chat remains visible unless Hide Chat is enabled.
- [ ] Open the Vermin Bin. Expected: this release does not fabricate totals from unverified modern menu data; observed vacuum counts remain intact.
- [ ] Toggle Outside and Without Vacuum independently. Expected: the tracker HUD follows each visibility rule without enabling world highlighting outside West Village.
- [ ] Run `/westvillage resetvermin`. Expected: all three saved local counters return to zero.
- [ ] Start Gunther's Rift race. Expected: the first of the complete 52 ordered points appears, with the configured number of look-ahead points.
- [ ] Cross checkpoints normally and skip close enough across two adjacent points. Expected: progress advances in order within the configured detection radius and never rewinds from ordinary movement.
- [ ] Finish, cancel, leave the Rift or reconnect. Expected: race rendering and transient progress clear immediately.
- [ ] Test `/westvillage number terminalrange|beamheight|verminrange|lookahead|detection` and `/westvillage option`. Expected: bounded values and every exposed choice persist after restart.

## Rift Dreadfarm suite

Enable Andromeda and travel to Dreadfarm. Run `/dreadfarm`; it should report Agaricus, Volt, Berberis and saved button state locally.

- [ ] Hold the Wand of Farming and look directly at a brown Agaricus mushroom. Expected: a configurable four-second countdown appears over that exact block.
- [ ] Keep looking until the mushroom becomes red. Expected: the label switches to `Click!`; looking away, changing item or leaving Dreadfarm/West Village clears it.
- [ ] Toggle Countdown off. Expected: the same exact observation displays elapsed growth time instead, without guessing another mushroom.
- [ ] Find friendly, hostile and charging Volts. Expected: each exact skull texture is classified correctly and unrelated armor stands are ignored.
- [ ] Enable Volt Mood. Expected: friendly is green, hostile red and charging blue using independently saved colors.
- [ ] Trigger a nearby Volt charge. Expected: a seven-block circular strike boundary and roughly 12-second lightning countdown appear; neither moves nor attacks.
- [ ] Change Strike Range, ring segments, charge duration, scan range, labels, distance and through-wall behavior independently. Expected: each applies without changing recognition.
- [ ] Hold the Wand near active Wilted Berberis. Expected: moving particles produce a white path/box, then the settled bush becomes yellow and labelled.
- [ ] Enable Hide Particles. Expected: only tracked nearby Berberis firework/villager particles hide; unrelated particles remain.
- [ ] Stand off farmland without the Wand while another Berberis produces donkey hurt/death sounds. Expected: those two exact sounds mute by live-profile default; holding the Wand on farmland restores them.
- [ ] Enable Only Farmland. Expected: Berberis rendering hides when the player is grounded away from farmland and returns on farmland.
- [ ] Observe a complete respawn cycle in each field. Expected: the helper learns only after the authoritative expected count for that field, announces locally once, and shows current green, next yellow and third red.
- [ ] Break the current dead bush. Expected: its authoritative server block transition advances the sequence. Breaking an unrelated bush does not advance it.
- [ ] Leave a learned field and return after another player changes it. Expected: stale/inconsistent sequences fail closed instead of directing to a missing bush.
- [ ] Run `/dreadfarm resetsequences`. Expected: session sequence learning clears without altering the world.
- [ ] Follow the nearest wooden-button spot route. Expected: one nearest incomplete spot receives a line, beam and label, then its unhit buttons appear only inside the configured local range.
- [ ] Activate buttons by hand and Blowgun. Expected: powered-block/chat evidence marks only authoritative dataset positions and persists them across restart.
- [ ] Run `/dreadfarm resetbuttons`. Expected: all 56 local button markers return without claiming the server quest changed.
- [ ] Complete all 56. Expected: the exact completion chat marks the full local set and removes button routing.
- [ ] Run every `/dreadfarm number` and `/dreadfarm option` control, then restart. Expected: all saved settings persist and remain bounded.

## Rift Living Cave suite

Enable Andromeda and enter Living Cave or Living Stillness. Run `/livingcave`; it should report Metal, Defense Blocks, reconstructed Snake count and suit-HUD state locally.

- [ ] Left-click a lapis-ore Living Metal block, then observe the nearby server-created lapis ore. Expected: an aqua box animates from the clicked block to only that adjacent replacement, with an optional line.
- [ ] Trigger an unrelated or distant lapis update. Expected: it is ignored because it is not within two blocks of the deliberate clicked origin and seven blocks of the player.
- [ ] Wait beyond the default four-second expiry or receive a Living Metal title. Expected: the animation clears.
- [ ] Enable Living Metal particle hiding. Expected: only particles within three blocks of the paired destination hide; unrelated Living Cave particles remain.
- [ ] Damage an Autonull, Autocap, Autochest, Autopants or Autoboots and watch a Defense Block move. Expected: the Enchanted Hit trail is associated with that exact damaged nearby mob.
- [ ] Observe the moving Defense Block become stained glass or diamond. Expected: it becomes a persistent `Break!` target with a line back to its owning mob.
- [ ] Break the placed Defense Block or kill/despawn its mob. Expected: the marker and association clear.
- [ ] Test Defense particle hiding around moving and placed blocks. Expected: only correlated particles are cancelled.
- [ ] Observe a Living Metal Snake spawn. Expected: authoritative lapis-block additions reconstruct one connected ordered Snake rather than independent random blocks.
- [ ] Force two Snake heads close together. Expected: collision correction prefers the non-calm Snake as the source does.
- [ ] Hold Frozen Water Pungi. Expected: the Snake head is highlighted for calming. Hold Self-Recursive, Anti-Sentient, Eon or Chrono Pickaxe. Expected: a calm multi-block Snake highlights its tail for breaking.
- [ ] Inspect Spawning, Active, not-touching-air and Calm states. Expected: aqua, yellow, red and green presentation follows actual blocks and motion.
- [ ] Look at or click a Snake. Expected: only the selected Snake gains its state/block-count label; no action is generated.
- [ ] Remove a middle block, create a gap over three blocks or leave a head invalid for more than one second. Expected: invalid reconstruction fails closed and clears.
- [ ] Enable Living Metal Suit HUD while wearing suit pieces. Expected: exact `lm_evo` values produce a movable total and optional piece bars/percent; unrelated armor is excluded.
- [ ] Test one to four pieces, missing `lm_evo`, 100% pieces and all four maxed. Expected: values clamp from 0-100 and Compact Maxed reduces the full maxed set to one row.
- [ ] Use `/livingcave number metalrange|animation|expiry|defenserange|snakerange|barlength` and every option, then restart. Expected: values remain bounded and every choice persists.
- [ ] Leave Living Cave, change world or disconnect mid-animation. Expected: Metal, Defense and Snake transient state clears without affecting the suit item data.

## Rift Colosseum suite

Enable Andromeda and enter the Rift Colosseum. Run `/colosseum`; it should report Bacte phase, Blobbercyst, warning and tracked-Tentacle state locally.

- [ ] Spawn a Blobbercyst. Expected: only the exact RemotePlayer name receives the configured red box and label; normal players and similarly named entities remain unchanged.
- [ ] Toggle Blobber box, label, distance, range and through-wall behavior independently. Expected: recognition stays exact and dead/despawned Blobbercysts disappear immediately.
- [ ] Start Bacte and observe each growth line from B through Bacte. Expected: phase advances from 1 through 5 based on the authoritative new-name length.
- [ ] Hide chat or miss a growth line while the boss label is loaded. Expected: the cleaned `[Lv] name current/max` label corrects phase within a tick.
- [ ] Despawn/end Bacte. Expected: after the two-second missing-label grace, phase and Tentacles clear.
- [ ] Enable the default-off Bacte Phase HUD. Expected: it appears only in Colosseum during an active phase by default and optionally includes the current partial boss name.
- [ ] Enable Show Inactive. Expected: `Not Active` remains visible in Colosseum but still hides outside the area.
- [ ] Step outside the arena and observe warning lines with increasing exclamation marks. Expected: each exact line recalculates `250ms x (12 - warning level)`, plays the configured sound and refreshes a hundredth-second title countdown.
- [ ] Return safely or stop receiving warning lines. Expected: the forced short-lived title expires within roughly 250ms and never remains stale.
- [ ] Toggle title, subtitle, sound and local chat independently. Expected: each channel changes without suppressing Hypixel’s original warning.
- [ ] Observe Bacte Tentacles at the arena floor. Expected: only Slimes sized 4-8 whose ceiling Y is exactly 68 and which pair with a `Bacte Tentacle` label receive waypoints.
- [ ] Damage a Tentacle normally. Expected: its display loses one HP from a generic damage packet.
- [ ] Let the wall or another non-generic source damage it. Expected: hit/HP display does not decrement.
- [ ] Compare phases 1-3, phase 4 and phase 5. Expected: Tentacles show remaining out of 4, remaining out of 3, then accumulated Hits respectively.
- [ ] Kill/despawn a Tentacle. Expected: its marker clears on zero health, death or missing observation.
- [ ] Test Tentacle box, beam, label, distance, range, height, color and through-wall options independently.
- [ ] Run `/colosseum reset`, every number control and every option, then restart. Expected: transient fight state clears and saved preferences remain bounded and persistent.
- [ ] Leave Colosseum or reconnect mid-fight. Expected: phase, warnings and Tentacles clear immediately.

## Rift Stillgore suite

Enable Andromeda and enter Stillgore Chateau or Oubliette. Run `/stillgore`; it should report the current unbroken count and local Effigy/heart settings.

- [ ] Compare all six Effigy markers against the sidebar. Expected: only gray/unbroken sidebar markers render as red `Break Effigy` waypoints.
- [ ] Break an Effigy and remain nearby. Expected: its red waypoint disappears and its armor-stand countdown becomes the exact respawn deadline.
- [ ] Move away after breaking an Effigy. Expected: the learned deadline continues locally and the waypoint turns yellow only within the configured final one-to-fifteen minutes.
- [ ] Observe another player breaking an Effigy while its prior state was visible. Expected: a 20-minute deadline starts from the scoreboard transition.
- [ ] Enable Unknown and reconnect in Stillgore without approaching every Effigy. Expected: unresolved broken states use gray guidance; disabling Unknown hides them.
- [ ] Enable Nearby Broken Labels. Expected: a broken Effigy within 15 blocks has a quiet identifying label even when it is not yet respawning soon.
- [ ] Kill a Splatter Crux and watch its heart particles. Expected: only the three-heart, zero-speed Splatter pattern gets a short red block-sized highlight.
- [ ] Change heart lifetime between 100 and 2,000 milliseconds and toggle box, beam, label and wall visibility. Expected: each option changes independently and unrelated heart particles remain untouched.
- [ ] Run `/stillgore reset`. Expected: transient Effigy deadlines and heart locations clear without changing settings.
- [ ] Leave Stillgore for another Rift area. Expected: no Effigy or Splatter Heart overlay renders there.

## Rift Mountaintop suite

Enable Andromeda and enter any Mountaintop subarea. Run `/mountaintop`; it should report Sun Gecko, Timite, discovered ore, tracker and Ubik state locally.

- [ ] Enter the Time Chamber and begin a Sun Gecko fight. Expected: the movable Sun Gecko HUD shows health, combo progress/multiplier and a millisecond combo-expiry timer only in this subarea.
- [ ] Open the Modifiers menu or start a modifier fight. Expected: lime slots and the server's active-modifier announcement identify Revival, Combo Manic, Time Sliced, Buffantics, Collective, Brand New Dance and Culmination.
- [ ] Enable modifier rows. Expected: every observed active modifier appears; Collective extends the combo deadline and Culmination/long Time Sliced reduce the hit target.
- [ ] Fight with clones enabled and real highlighting disabled. Expected: question-mark clones receive the configured red box/label while the real Gecko is not highlighted; the independent real toggle uses green.
- [ ] Right-click blue or light-blue Timite glass with the Time Gun. Expected: a movable evolution countdown starts at two seconds; a same-position state transition uses 1.8 seconds.
- [ ] Stop holding the Time Gun or leave Mountaintop. Expected: the evolution HUD hides immediately without cancelling or changing the click.
- [ ] Stand near blue/cyan Timite or Obsolite panes. Expected: they are discovered within the configured scan range and show boxes/countdowns only inside the configured final warning window before the 31-second expiry.
- [ ] Enable the Timite Tracker and collect Timite, Youngite and Obsolite. Expected: only positive inventory deltas after the initial baseline count; totals persist and show independent item, two-seconds-per-Timite time, NPC Motes profit and Highlite craftability rows.
- [ ] Compare Highlite craftability manually. Expected: one requires 32 Youngite, 32 Timite and 16 Obsolite and is valued at 25,000 Motes.
- [ ] Enable tracker holding-only mode. Expected: it appears only with Anti-Sentient, Eon or Chrono Pickaxe or the Time Gun held.
- [ ] Enter Rose's End's bounded parkour area near x25-52, y165-185, z90-120. Expected: the drop at 40,161,116 receives the configured box, beam and `Drop` label only while inside.
- [ ] Play Split or Steal with Ubik Quick Close enabled. Expected: clicks work normally while slot 4 is a clock; after it changes, any click closes locally without sending that final container click.
- [ ] Enable Ubik Reminder, then open Split or Steal or trigger its cooldown message. Expected: the exact observed cooldown replaces the two-hour fallback, persists, and the optional HUD counts down.
- [ ] Let Ubik become ready. Expected: one local chat/sound reminder fires according to settings, the HUD shows Ready, and ready-only mode hides all earlier countdown state.
- [ ] Run `/mountaintop reset` and `/mountaintop resettracker`. Expected: transient combat/ore state and persistent collection totals reset independently.
- [ ] Move to a non-Mountaintop Rift area. Expected: all Mountaintop world and HUD overlays hide.

## Rift Wyld Woods suite

Enable Andromeda and enter the Rift. Run `/wyldwoods`; it should report Larva, Odonata and Shy warning state locally.

- [ ] Hold a Larva Hook near tree Larvas. Expected: only armor stands wearing the exact Rift Larva head receive the configured highlight.
- [ ] Switch away from the Larva Hook. Expected: Larva guidance hides immediately with the live-default require-hook option; disabling that option makes it always visible in the Rift.
- [ ] Hold an Empty Odonata Bottle near flying Odonatas. Expected: only armor stands holding the exact Odonata head receive guidance.
- [ ] Switch away from the bottle. Expected: Odonata guidance hides immediately with require-bottle enabled.
- [ ] Independently toggle Larva and Odonata box, beam, label, distance and through-wall options and change their range/color. Expected: the two target types do not share presentation state.
- [ ] Approach a Shy Crux displaying `I'm ugly! :(`, `Eek!`, `Don't look at me!` or `Look away!`. Expected: the title refreshes while it remains inside the default eight-block range.
- [ ] Enable Shy subtitle, chat, sound, box and label one at a time. Expected: each channel works independently and uses the configured alert color.
- [ ] Change Shy range between three and twenty blocks and cooldown between 50 and 2,000 milliseconds. Expected: detection and repeat frequency remain bounded to those values.
- [ ] Approach another named entity that is not one of the exact four phrases. Expected: it never triggers the Shy warning.
- [ ] Leave the Rift or disable Andromeda. Expected: all Larva, Odonata and Shy rendering/alerts stop immediately.

## Rift Mirrorverse Dance and Craft suite

Enable Andromeda and enter Mirrorverse. Run `/mirrorverse`; it should report Dance progress and Craft state locally. Dance defaults off and Craft defaults on to match the live 26.1.2 profile.

- [ ] Enable Dance and enter the exact room around x-267 to -260, y32-40, z-110 to -102. Expected: the movable HUD begins at step one of 49 and remains hidden outside those bounds.
- [ ] Compare the opening sequence. Expected: the first five instructions are Move, followed by Sneak, Stand and the authoritative remaining order.
- [ ] Complete one correct dance instruction. Expected: either exact bass success pitch advances one step and starts a one-second millisecond countdown.
- [ ] Deliberately fail after advancing. Expected: the exact burp or high-pitch level-up failure sound resets to step one and clears the countdown.
- [ ] Change visible lines from one to 49 and spacing from -5 to 10. Expected: the movable HUD resizes and retains Now, Next and Later ordering.
- [ ] Edit Now/Next/Later and Move/Stand/Sneak/Jump/Punch/countdown/fallback formatting. Expected: ampersand color codes render as Minecraft formatting without changing instruction logic.
- [ ] Enable Hide Original Title. Expected: title packets hide only while Dance is enabled and the player is inside its exact bounds.
- [ ] Enable Hide Players. Expected: other real players hide only inside Dance; local player and non-player entities remain.
- [ ] Enter Craft Room around x-117 to -108, y51-58, z-128 to -106. Expected: source-side Zombies, Slimes and Cave Spiders at or behind z-116.5 produce mirrored guidance at equal distance across that plane.
- [ ] Move a supported mob on the source side. Expected: its mirrored silhouette follows without moving or creating a world entity.
- [ ] Toggle Craft name, health, box, beam, label, color, range and wall options independently. Expected: only presentation changes; source mobs remain untouched.
- [ ] Enable Craft player hiding. Expected: other real players hide only while the local player is in the exact Craft Room bounds.
- [ ] Approach unsupported mobs or supported mobs on the wrong side of the mirror plane. Expected: no mirrored guidance is produced.
- [ ] Leave Mirrorverse or disconnect. Expected: Dance index/countdown and room flags clear, all hiding stops, and no stale Craft silhouettes remain.
## Rift-wide Progression

Enable Andromeda and enter the Rift. Run `/riftprogress`; it should report Crux, Punchcard, Guide and Horsezooka states locally without sending a server command.

- [ ] Put a Crux Talisman with progress lore in your inventory and enable `/riftprogress option crux on`. Expected: the movable Crux Talisman HUD shows the parsed Crux rows and total percentage; it disappears outside the Rift and inside Mirrorverse.
- [ ] Toggle `cruxcompact` and `bonuses`, then inspect a fully maxed talisman. Expected: compact mode reduces the display to MAXED while bonus rows independently follow their toggle.
- [ ] Equip a Punchcard Artifact and enable `punchhighlight` and `punchhud`. Expected: eligible players inside the configured range are highlighted until a successful punch message confirms them; the HUD count updates.
- [ ] Disable the Artifact requirement only for comparison. Expected: tracking remains available without the item. Re-enable it and remove the Artifact; tracking pauses and the bounded local warning appears.
- [ ] Test reverse, remaining, box, label, distance and through-wall settings. Expected: each option changes only its named presentation behavior.
- [ ] Run `/riftprogress resetpunchcard`. Expected: the current session set clears immediately. Disconnecting or changing worlds also starts a fresh session.
- [ ] Open the Rift Guide. Expected: incomplete entries receive a colored border and optional `M`; completed entries and unrelated inventories are untouched.
- [ ] Hold a Horsezooka with Horsezooka hiding enabled. Expected: horses stop rendering only while in the Rift and holding that exact item; no entities are removed or modified.
- [ ] Leave the Rift or disable Andromeda. Expected: both HUDs, player highlights, Guide marks and Horsezooka filtering stop immediately.

## Rift Temporal Pillar Navigation

Enable Andromeda, enable Rift pathfinding, select a missing soul with `/riftnav nearest`, and travel near a real Temporal Pillar.

- [ ] Run `/riftnav`. Expected: status includes the detected Temporal Pillar count and the number of graph nodes currently blocked.
- [ ] Observe the route before and after a Pillar enters scan range. Expected: it rebuilds away from nodes inside the default seven-block danger radius without moving or steering the player.
- [ ] Run `/riftnav option pillardodge off`. Expected: the route may use those graph nodes again. Turn it back on after comparison.
- [ ] Enable `/riftnav option pillardanger on`. Expected: each detected Pillar receives the configured danger box and label even if detouring is temporarily disabled.
- [ ] Test `pillarbox`, `pillarbeam`, `pillarlabel`, `pillardistance` and `pillarwalls`. Expected: each affects only its named visual channel.
- [ ] Test `/riftnav pillarradius 10`, `/riftnav pillarrange 100`, `/riftnav pillarbeamheight 12` and `/riftnav pillarcolor 80FF5555`. Expected: status remains local, routing uses the new radius, and rendering follows the new range/height/color.
- [ ] Leave the Rift, disable Andromeda or disable Rift pathfinding. Expected: discovery, graph blocking and all danger rendering stop immediately.

## Complete Vampire Slayer Guidance

Enable Perseus, keep `vampireHelper` enabled, and enter Stillgore Chateau or Oubliette. Run `/vampirehelper`; it should report every major helper locally.

- [ ] Lower your health to the configured `/vampirehelper number hearts 4` threshold. Expected: `Heal now!` uses the enabled title/chat/sound channels and stops when health recovers.
- [ ] Fight your own Bloodfiend. Expected: it receives the own-boss color, box and optional label/distance/line only inside the valid Rift areas.
- [ ] Attack another player's Bloodfiend after enabling `other`. Expected: only that deliberately tagged boss receives other-boss guidance.
- [ ] Run `/vampirehelper coop Name1,Name2` and enable `coop`. Expected: bosses whose parsed owner matches either name use the independent co-op color and controls.
- [ ] Reach the final twenty percent. Expected: the boss changes to the Steak color and `Steak now!` appears when either health or the authoritative Stake marker confirms readiness.
- [ ] Trigger Twinclaws. Expected: `Use Holy Ice!` appears after the configured zero-to-40-tick delay and uses its independent channels/message/color.
- [ ] Trigger Mania while standing on and off the safe green terracotta. Expected: `Mania!` switches between the configured safe and danger colors.
- [ ] Enable `countdown`, `hptillsteak` and `percentage`. Expected: the boss labels show the vehicle-timed Mania countdown, sub-300 HP Steak countdown/readiness and current percentage.
- [ ] Enable `ichor` and `spring`. Expected: only the exact Blood Ichor and Killer Spring head textures receive their independently configured boxes/labels; Ichor beam and optional boss lines follow their toggles.
- [ ] With Killer Spring sound-spam protection enabled and Vampire sound muting disabled, trigger duplicate Wither-spawn sounds in one tick. Expected: the first remains and same-tick duplicates are suppressed.
- [ ] Test `/vampirehelper message`, `color`, `number` and `option`. Expected: every change persists and affects only its named feature.
- [ ] Leave Stillgore/Oubliette, leave the Rift, or disable Perseus. Expected: alerts, entity scans, highlights and sound filtering stop immediately.

## Complete McGrubber Detection

Enable Andromeda and Motes tooltips. Run `/riftmotes`; status should show the current zero-to-five McGrubber count and its last detection source.

- [ ] Enter the Rift and open `Motes Grubber` with a sellable Rift item in player inventory. Expected: its displayed Motes price is compared with the authoritative base price and the solved stack count is learned.
- [ ] Outside the Rift, open `/sblevels`, then `Miscellaneous ➜ Consumable Items`. Expected: `MCGRUBBER_BURGER` `Total Progress` is divided by twenty and learned.
- [ ] Pick up a Motes Orb. Expected: only the exact `5 + 60 × stacks` payout message updates the count; malformed or out-of-range payouts are ignored.
- [ ] Consume a McGrubber Burger. Expected: the exact `You have n Grubber Stacks` message remains an independent learning path.
- [ ] After each path, run `/riftmotes`. Expected: status identifies `Motes Grubber`, `Consumable Items`, `orb pickup`, or `consumption`.
- [ ] Verify an NPC Motes tooltip and Rift Storage total before and after a stack-count change. Expected: every valuation updates by five percent per stack.
- [ ] Toggle `grubbermenu`, `consumables`, `orblearn`, and `consumption` independently. Expected: only the disabled source stops learning.
- [ ] Run `/riftmotes burgers 0` through `5`. Expected: this deliberately sets a manual value and status reports `manual`.
- [ ] Leave menus and reconnect. Expected: learned configuration persists, while transient menu signatures do not create duplicate saves or notices.

## Andromeda Configuration Integrity

Open `/cn config`, select Andromeda, and compare visible settings with the Andromeda sections in this guide.

- [ ] Confirm `timeHud`, `enigmaSoulTracker`, `effigyTracker`, and the old generic area-helper toggles are absent. Expected: their real `riftHud`, Enigma Soul, Effigy and per-area controls remain.
- [ ] Confirm speculative `deadgehogCounter`, `bluetoothRingHelper`, and the obsolete duplicate Vampire toggle are absent.
- [ ] Confirm `riftLowTimeAlert` remains visible and `/riftguide option alert on|off` still controls it.
- [ ] Toggle representative remaining settings from each Andromeda area. Expected: every visible Boolean corresponds to behavior documented in the relevant test section.
- [ ] Launch with an older configuration containing removed JSON keys. Expected: the client loads normally, ignores unknown historical keys and preserves all recognized preferences.
