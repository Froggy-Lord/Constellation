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
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            if (s.contains("Sea Creature") || s.contains("sea creature")) seaCreatures++;
            if (cfg != null && cfg.rareSeaCreatureAlert) checkRare(s);
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
    }
}
