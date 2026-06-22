package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Hydra — fishing. Cast timer, a running sea-creature tally, and a loud alert for the rare
 * catches that are easy to miss in the spawn spam (and worth a party heads-up).
 */
public class HydraFishing extends BaseConstellation {

    @Override public String id() { return "hydra"; }
    @Override public String displayName() { return "Hydra"; }
    @Override public String description() { return "Fishing — sea creatures, cast timer, trophy fish"; }

    private static long castAt = 0;
    private static int seaCreatures = 0;
    private static int seaCreatureCap = 20;
    private static long lastRareAt = 0;
    private static int tBronze = 0, tSilver = 0, tGold = 0, tDiamond = 0;
    private static long goldenFishAt = 0;
    private static long barnOpenAt = 0, barnCloseAt = 0;

    // the catches worth stopping for — their names are distinct enough to spot in a chat line
    private static final String[] RARE = {
        "Sea Emperor", "Water Hydra", "Lord Jawbus", "Thunder", "Plhlegblast",
        "Phantom Fisher", "Grim Reaper", "Reindrake", "Yeti", "Carrot King",
        "Great White Shark", "Abyssal Miner", "Titanoboa"
    };

    private HydraConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (HydraConfig) getConfig();
        // cast timer — the rod casts on right-click while held
        ConstellationClient.tick().every(4, "hydra-cast", () -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof net.minecraft.world.item.FishingRodItem
                && mc.options.keyAttack.isDown()) castAt = System.currentTimeMillis();
        });
        // hide other players' bobbers so the water around you stays readable
        ConstellationClient.tick().every(2, "hydra-hooks", () -> {
            if (cfg == null || !cfg.hideOtherHooks) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.projectile.FishingHook h
                    && h.getPlayerOwner() != mc.player) h.setInvisible(true);
            }
        });
        // Thunder entity highlight — rare SC, box it in the water
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.thunderHighlight || !ConstellationClient.loc().onHypixel()) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            for (var e : mc.level.entitiesForRendering()) {
                var name = e.getCustomName();
                if (name != null && name.getString().contains("Thunder")) {
                    wctx.outline(e.getBoundingBox().inflate(0.3), 0xFFFFFF00, true);
                    wctx.label(e.position().add(0, e.getBbHeight() + 0.5, 0), "⚡ Thunder", 0xFFFFFF00, true);
                }
            }
        });

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            if (s.contains("Sea Creature") || s.contains("sea creature")) seaCreatures++;
            if (cfg != null && cfg.rareSeaCreatureAlert) checkRare(s);
            // golden fish spawn — start a 60s timer
            if (cfg != null && cfg.goldenFishTimer && s.contains("Golden Fish")) {
                if (s.contains("appeared") || s.contains("spawned")) goldenFishAt = System.currentTimeMillis();
                else if (s.contains("caught") || s.contains("despawned")) goldenFishAt = 0;
            }
            // barn timer — track open/close from chat
            if (cfg != null && cfg.barnTimer) {
                if (s.contains("barn") && (s.contains("open") || s.contains("doors"))) {
                    barnOpenAt = System.currentTimeMillis();
                    // try to parse the duration: "for X minutes" or "closes in Xm"
                    var bm = java.util.regex.Pattern.compile("(\\d+)\\s*(?:minute|min|m)").matcher(s.toLowerCase(java.util.Locale.ROOT));
                    if (bm.find()) barnCloseAt = barnOpenAt + Long.parseLong(bm.group(1)) * 60_000L;
                }
                if (s.contains("barn") && (s.contains("close") || s.contains("shut"))) barnOpenAt = 0;
            }
            // trophy fish — the catch line names the tier
            if (cfg != null && cfg.trophyFishTracker && (s.contains("TROPHY FISH") || s.contains("You caught"))) {
                if (s.contains("Diamond")) { tDiamond++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.diamond", 1); }
                else if (s.contains("Gold")) { tGold++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.gold", 1); }
                else if (s.contains("Silver")) { tSilver++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.silver", 1); }
                else if (s.contains("Bronze")) { tBronze++; com.froggylord.constellation.core.StatStore.add("hydra.trophy.bronze", 1); }
            }
        });
    }

    private void checkRare(String s) {
        // skip kill/death lines so we only fire on the spawn
        String low = s.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("killed") || low.contains("slain") || low.contains("defeated") || low.contains("died")) return;
        for (String name : RARE) {
            if (!s.contains(name)) continue;
            long now = System.currentTimeMillis();
            if (now - lastRareAt < 4000) return; // dedupe the multi-line spawn flavour
            lastRareAt = now;
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.gui.hud.resetTitleTimes();
                mc.gui.hud.setTitle(Component.literal("§b🎣 " + name + "!"));
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 1.4f);
                if (cfg.rareSeaCreaturePartyPing)
                    mc.player.connection.sendCommand("pc " + name + " spawned!");
            }
            return;
        }
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (HydraConfig) getConfig();
        if (cfg == null) return;

        if (cfg.seaCreatureAlerts) {
            hud.register(new HudWidget("hydra-timer", "Cast",
                () -> {
                    if (!ConstellationClient.loc().onHypixel() || castAt == 0) return null;
                    long ms = System.currentTimeMillis() - castAt;
                    if (ms > 60_000) return null;
                    String sc = seaCreatures > 0 ? " §7SC: §f" + seaCreatures + "§7/" + seaCreatureCap : "";
                    return "§b🎣 " + (ms / 1000) + "s" + sc;
                },
                HudPosition.of(50, 86), cfg.seaCreatureAlerts));
        }
        if (cfg.trophyFishTracker) {
            hud.register(new HudWidget("hydra-trophy", "Trophy",
                () -> {
                    boolean life = ConstellationClient.cfg() != null && ConstellationClient.cfg().lifetimeStats;
                    long b = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.bronze", tBronze) : tBronze;
                    long si = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.silver", tSilver) : tSilver;
                    long g = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.gold", tGold) : tGold;
                    long di = life ? com.froggylord.constellation.core.StatStore.getLong("hydra.trophy.diamond", tDiamond) : tDiamond;
                    if (b + si + g + di == 0) return null;
                    return "§c⬤" + b + " §7⬤" + si + " §6⬤" + g + " §b⬤" + di;
                },
                HudPosition.of(50, 94), cfg.trophyFishTracker));
        }
        if (cfg.goldenFishTimer) {
            hud.register(new HudWidget("hydra-golden", "Golden",
                () -> {
                    if (goldenFishAt == 0) return null;
                    long elapsed = System.currentTimeMillis() - goldenFishAt;
                    if (elapsed > 90_000) return null;
                    return "§6🐟 Golden " + (elapsed / 1000) + "s ago";
                },
                HudPosition.of(50, 102), cfg.goldenFishTimer));
        }
        if (cfg.barnTimer) {
            hud.register(new HudWidget("hydra-barn", "Barn",
                () -> {
                    if (barnOpenAt == 0) return null;
                    long left = barnCloseAt - System.currentTimeMillis();
                    if (left <= 0) return null;
                    long min = left / 60000, sec = (left % 60000) / 1000;
                    return "§c🏚 Barn " + min + ":" + String.format("%02d", sec);
                },
                HudPosition.of(50, 110), cfg.barnTimer));
        }
    }
}
