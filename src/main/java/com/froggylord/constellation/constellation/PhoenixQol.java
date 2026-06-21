package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import net.minecraft.client.Minecraft;

public class PhoenixQol extends BaseConstellation {

    @Override public String id() { return "phoenix"; }
    @Override public String displayName() { return "Phoenix"; }
    @Override public String description() { return "Quality of Life — fullbright, hide annoyances, smooth gameplay"; }

    @Override
    public void init(InitContext ctx) {
        PhoenixConfig cfg = (PhoenixConfig) getConfig();
        if (cfg == null) return;

        if (cfg.autoSprint) {
            ConstellationClient.tick().every(1, "phoenix-sprint", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.options.keyUp.isDown()) {
                    mc.options.keySprint.setDown(true);
                }
            });
        }
        // hide fire overlay — just keep extinguishing the fire ticks each frame
        if (cfg.hideFireOverlay) {
            ConstellationClient.tick().every(1, "phoenix-fire", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.setRemainingFireTicks(0);
            });
        }
        if (cfg.disableVignette) {
            // vignette is a client option — set once per init and leave it off
            Minecraft mc = Minecraft.getInstance();
            mc.options.vignette().set(false);
        }
        if (cfg.fullbright) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.gamma().set(15.0);
        }
        if (cfg.instantSneak) {
            ConstellationClient.tick().every(1, "phoenix-sneak", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                if (mc.options.keyShift.isDown())
                    mc.player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                else if (mc.player.getPose() == net.minecraft.world.entity.Pose.CROUCHING)
                    mc.player.setPose(net.minecraft.world.entity.Pose.STANDING);
            });
        }
        if (cfg.disableFog) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.fovEffectScale().set(0.0);
        }
        if (cfg.signCalculator) {
            // evaluate simple math on signs — on Hypixel this is mostly bazaar/auction
            // pricing, so having the answer in chat saves a mental calculation
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (!cfg.signCalculator) return;
                String s = message.getString();
                // "Sign says: 143 * 64" or chat-edited sign text
                if (!s.contains("Sign says:")) return;
                int colon = s.indexOf(':');
                if (colon < 0) return;
                String expr = s.substring(colon + 1).trim();
                try { evaluateSign(expr); } catch (Exception ignored) {}
            });
        }
    }

    private static void evaluateSign(String expr) {
        // basic: number operator number (e.g. "143 * 64" or "1.45 * 850")
        String[] parts = expr.split("\\s+");
        if (parts.length < 3) return;
        double a = Double.parseDouble(parts[0].replace(",", ""));
        double b = Double.parseDouble(parts[2].replace(",", ""));
        double result = switch (parts[1]) {
            case "*", "x", "×" -> a * b;
            case "+" -> a + b;
            case "-" -> a - b;
            case "/" -> b != 0 ? a / b : 0;
            default -> throw new IllegalArgumentException();
        };
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            String out = "§eSign calc: §f" + a + " " + parts[1] + " " + b + " = §a" + format(result);
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(out));
        }
    }

    private static String format(double n) {
        if (n == Math.floor(n) && n < Long.MAX_VALUE) return String.format("%,d", (long) n);
        return String.format("%,.1f", n);
    }
}
