# Constellation testing guide

You do not need to test everything in one sitting. Start with the five-minute check, then test one game-area session whenever you naturally play that area. Checkboxes are intentionally split into small groups.

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
- [ ] Hover every ranked upgrade. Expected: added production, affordability time, optional payback and efficiency rank match the menu; zero or missing production reports Unknown.
- [ ] Reach a prestige requirement and inspect Hitman. Expected: prestige changes to Ready and configured egg/slot rows reflect the menu.
- [ ] Wait for normal and Golden stray Rabbits. Expected: the correct slot highlights and enabled sound fires once per appearance, then may fire again after reopening.
- [ ] Activate Time Tower normally, then use `/chocolatefactory warning 1` for a boundary test. Expected: the profile timer persists, warning fires once at one minute and expiry fires once at zero without stale-login spam.
- [ ] Run `/chocolatefactory`, `toggle`, `cleartower` and representative `option <name> <on|off>` commands. Expected: readable status and persistent independent settings.
- [ ] Move and resize the Chocolate Factory HUD in `/cn hud`. Expected: it appears only while the menu or a saved active Time Tower is relevant, plus editor grace.
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
