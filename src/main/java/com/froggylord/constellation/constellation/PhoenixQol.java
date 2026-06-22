package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
            ConstellationClient.tick().once(1, "phoenix-vignette", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options != null) mc.options.vignette().set(false);
            });
        }
        if (cfg.fullbright) {
            // defer to first tick — Minecraft.options is null during mod init
            ConstellationClient.tick().once(1, "phoenix-fullbright", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options != null) mc.options.gamma().set(15.0);
            });
        }
        if (cfg.etherwarpOverlay) {
            ConstellationClient.world().register(wctx -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.hitResult == null) return;
                if (mc.hitResult.getType() != HitResult.Type.BLOCK) return;
                var stack = mc.player.getMainHandItem();
                if (stack.isEmpty()) return;
                String name = stack.getItem().getDescriptionId();
                if (!name.contains("aspect_of_the_end") && !name.contains("aspect_of_the_void")
                    && !name.contains("ender_pearl"))
                    return;
                var hit = (BlockHitResult) mc.hitResult;
                AABB box = new AABB(hit.getBlockPos());
                wctx.outline(box, 0xFFAA00FF, true);
            });
        }
        if (cfg.hideLightning) {
            ConstellationClient.tick().every(4, "phoenix-lightning", () -> {
                var mc = Minecraft.getInstance();
                if (mc.level == null) return;
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.LightningBolt) e.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            });
        }
        if (cfg.hideFallingBlocks) {
            ConstellationClient.tick().every(4, "phoenix-falling", () -> {
                var mc = Minecraft.getInstance();
                if (mc.level == null) return;
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.item.FallingBlockEntity) e.discard();
                }
            });
        }
        if (cfg.hidePlayersInDungeon) {
            ConstellationClient.tick().every(1, "phoenix-hideplayers", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null) return;
                if (!ConstellationClient.loc().inDungeons()) return;
                for (var p : mc.level.players()) {
                    if (p != mc.player) p.setInvisible(true);
                }
            });
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
            ConstellationClient.tick().once(1, "phoenix-fog", () -> {
                var mc = Minecraft.getInstance();
                if (mc.options != null) mc.options.fovEffectScale().set(0.0);
            });
        }
        if (cfg.hideUnderwaterBlur) {
            ConstellationClient.tick().once(1, "phoenix-uwater", () -> {
                var mc = Minecraft.getInstance();
                if (mc.options != null) mc.options.screenEffectScale().set(0.0);
            });
        }
        if (cfg.noDeathAnimation) {
            ConstellationClient.tick().every(1, "phoenix-nodeath", () -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.isDeadOrDying())
                    mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            });
        }
        if (cfg.preventDroppingValuable) {
            // intercept Q (drop) for valuable items — show a warning instead
            ConstellationClient.tick().every(2, "phoenix-itemprotect", () -> {
                var mc = Minecraft.getInstance();
                if (mc.player == null || mc.gui.screen() != null) return;
                if (!mc.options.keyDrop.isDown()) return;
                var stack = mc.player.getMainHandItem();
                if (stack.isEmpty()) return;
                String name = stack.getHoverName().getString();
                // prevent dropping items with rarity colors (valuable)
                if (name.contains("§6") || name.contains("§5") || name.contains("§9") || name.contains("§d") || name.contains("§c")) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⚠ Hold §nshift§r§c to drop §r" + name));
                    mc.options.keyDrop.setDown(false);
                }
            });
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
