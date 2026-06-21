package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

/**
 * Hydra — fishing. Tracks the cast timer (time since last bobber cast) and counts sea
 * creature spawn messages to give a basic SC session tally.
 */
public class HydraFishing extends BaseConstellation {

    @Override public String id() { return "hydra"; }
    @Override public String displayName() { return "Hydra"; }
    @Override public String description() { return "Fishing — sea creatures, cast timer, trophy fish"; }

    private static long castAt = 0;
    private static int seaCreatures = 0;

    private HydraConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (HydraConfig) getConfig();
        // track cast timer: the rod casts when the player right-clicks while holding a fishing rod
        ConstellationClient.tick().every(4, "hydra-cast", () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            var stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof net.minecraft.world.item.FishingRodItem) {
                if (mc.options.keyAttack.isDown()) { castAt = System.currentTimeMillis(); }
            }
        });
        // count sea creatures from chat
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            // "A Sea Creature has spawned!" or "You caught a ..." — count the SC spawn
            if (s.contains("Sea Creature") || s.contains("sea creature") || s.contains("Sea Creature has spawned"))
                seaCreatures++;
        });
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
                    return "§b🎣 " + (ms / 1000) + "s  §7SC: " + seaCreatures;
                },
                HudPosition.of(50, 86), cfg.seaCreatureAlerts));
        }
    }
}
