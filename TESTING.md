# Live testing — what to scrape for the last features

Most of whats left cant be done without real game data. The widgets are wired but
the sidebar/gui string formats need to match exactly. Run `/cn scrape all` in each
area below, then send me `config/constellation-scrapes/`.

## the auto-scraper is already on
it passively dumps sidebar/tab/gui/chat as you play. so honestly just play normally
in these areas and the dumps build up. `/cn scrape all` forces a full snapshot.

## per area — what im missing

### dungeons (m7 ideally)
- m7 boss phase HUD is wired off boss dialogue, just confirm it shows P1-P5
- room detection needs a real run to verify anchors/rotation
- secret waypoints need the secretlocations data verified against a live room
- scrape: `/cn scrape room` in a few rooms, `/cn scrape score` mid-run

### kuudra (crimson isle)
- phase HUD reads chat (KUUDRA DOWN etc) — confirm phases switch
- supply/magmafish/fresh-tools widgets read sidebar — need the real sidebar lines
- scrape: `/cn scrape sidebar` during each kuudra phase

### garden
- pest counter, crop profit, farming xp — all read sidebar, need real format
- scrape: `/cn scrape sidebar` + `/cn scrape tab` on garden

### crystal hollows / glacite
- powder, commissions, crystal trackers — sidebar + tab
- metal detector reads sidebar — confirm the line
- scrape: `/cn scrape all` in CH

### rift
- motes session tracker is wired off chat, confirm it counts
- enigma souls / mirrorverse waypoints need a live check on coords

### the rest (read off menu lore, should just work)
- chocolate factory cps — open the factory, confirm the number
- reforge confirm — reforge something, confirm the sound + count
- essence counter — gain essence, confirm
- bazaar P&L — buy/sell on bz, confirm the +/-
- auction outbid — get outbid, confirm the alert

## what i CANT do even with scrapes
- boulder/water/icefill solvers: the solution lookup needs odins grid-hash encoder.
  thats their algorithm not data, so per the no-stolen-code rule its out unless we
  build our own solver from scratch (doable but its real work, not a wire-up)
