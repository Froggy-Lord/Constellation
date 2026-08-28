package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.network.PlayerPositionUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.regex.Pattern;

public final class KuudraStunHelper {
    private static final Vec3 BELLY_ENTRY = new Vec3(-161, 49, -186);
    private static final Pattern STUN = Pattern.compile("^\\w+ destroyed one of Kuudra's pods!$");
    // ported from Athen (BSD-3-Clause): api/kuudra/enums/KuudraPod.kt
    private static final Pod[] PODS = {
        new Pod("Left", box(-150, 31, -173, -154, 24, -170), new AABB(-153, 27, -173, -152, 28, -172)),
        new Pod("Middle", box(-153, 31, -153, -156, 25, -157), new AABB(-156, 28, -157, -155, 29, -156)),
        new Pod("Right", box(-168, 31, -166, -170, 24, -169), new AABB(-168, 27, -169, -167, 28, -168))
    };

    private record Pod(String name, AABB bounds, AABB exact) {}

    private static KeyMapping overrideKey;
    private static boolean initialized;
    private static boolean stunning;
    private static boolean belly;
    private static long lastWarning;

    private KuudraStunHelper() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        overrideKey = ConstellationClient.instance().keys().register(
            "kuudra_stun_override", InputConstants.UNKNOWN.getValue());

        // ported from Athen (BSD-3-Clause): modules/impl/kuudra/StunHelper.kt
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !active()) return true;
            String stripped = ChatFormatting.stripFormatting(message.getString());
            if (stripped == null) stripped = message.getString();
            if (KuudraState.tier() < 3) return true;
            if (stunning && STUN.matcher(stripped).matches()) reset();
            else if (!stunning && stripped.equals("You purchased Human Cannonball!")) stunning = true;
            return true;
        });
        ConstellationClient.instance().packets().register(packet -> {
            if (!(packet instanceof PlayerPositionUpdate update) || !stunning || belly) return;
            Vec3 pos = update.after();
            if (floor(pos.x) == -161 && floor(pos.y) == 49 && floor(pos.z) == -186) belly = true;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> shouldBlock(player.getItemInHand(InteractionHand.MAIN_HAND))
            ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> shouldBlock(player.getItemInHand(InteractionHand.MAIN_HAND))
            ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
            shouldBlock(player.getItemInHand(InteractionHand.MAIN_HAND))
                ? InteractionResult.FAIL : InteractionResult.PASS);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/StunHelper.kt pod rendering
    public static void draw(WorldRenderer.Ctx ctx) {
        DracoConfig cfg = config();
        if (!active() || !stunning || cfg == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (cfg.kuudraStunHighlightPods && belly) {
            for (Pod pod : PODS) drawBox(ctx, pod.bounds, cfg.kuudraStunPodColour,
                cfg.kuudraStunPodFillColour, !cfg.kuudraStunDepthTest, cfg);
        }

        if (!cfg.kuudraStunHighlightExact) return;
        Pod selected = PODS[Math.clamp(cfg.kuudraStunExactPod, 0, PODS.length - 1)];
        AABB exact = selected.exact;
        if (!belly) {
            Vec3 offset = mc.player.position().subtract(BELLY_ENTRY);
            exact = exact.move(offset.x, offset.y, offset.z);
        }
        drawBox(ctx, exact, cfg.kuudraStunExactColour, cfg.kuudraStunExactFillColour, true, cfg);
        if (cfg.kuudraStunLabels)
            ctx.label(exact.getCenter().add(0, 0.8, 0), selected.name + " pod", cfg.kuudraStunExactColour, true);
    }

    public static void reset() {
        stunning = false;
        belly = false;
        lastWarning = 0;
    }

    public static void tick() {
        DracoConfig cfg = config();
        if ((cfg == null || !cfg.enabled || !cfg.kuudraStunHelper || !KuudraState.inRun())
            && (stunning || belly)) reset();
    }

    public static boolean stunning() { return stunning; }
    public static boolean inBelly() { return belly; }
    public static String selectedPod() {
        DracoConfig cfg = config();
        return cfg == null ? "Left" : PODS[Math.clamp(cfg.kuudraStunExactPod, 0, PODS.length - 1)].name;
    }

    private static boolean shouldBlock(ItemStack stack) {
        DracoConfig cfg = config();
        if (cfg == null || !active() || !cfg.kuudraStunBlockAbility || KuudraState.tier() < 3
            || overrideKey != null && overrideKey.isDown() || !hasPickobulus(stack)) return false;

        int mode = Math.clamp(cfg.kuudraStunBlockMode, 0, 2);
        if ((mode == 0 || mode == 2) && !belly) return blocked(cfg);
        if ((mode == 1 || mode == 2) && (!stunning || !belly)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(Math.clamp(cfg.kuudraStunAimRange, 8, 64)));
        HitResult result = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, mc.player));
        if (!(result instanceof BlockHitResult block) || result.getType() != HitResult.Type.BLOCK)
            return blocked(cfg);
        for (Pod pod : PODS) if (contains(pod.bounds, block.getBlockPos())) return false;
        return blocked(cfg);
    }

    // ability-lore detection cross-checked with Skyblocker (LGPL-3.0-or-later): utils/ItemAbility.java
    private static boolean hasPickobulus(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (stack == null || stack.isEmpty() || mc.player == null) return false;
        for (Component line : stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL)) {
            String text = ChatFormatting.stripFormatting(line.getString());
            if (text != null && text.contains("Ability: Pickobulus") && text.contains("RIGHT CLICK")) return true;
        }
        return false;
    }

    private static boolean blocked(DracoConfig cfg) {
        long now = System.currentTimeMillis();
        if (now - lastWarning >= Math.clamp(cfg.kuudraStunWarningCooldownMs, 100, 5_000)) {
            lastWarning = now;
            Minecraft mc = Minecraft.getInstance();
            if (cfg.kuudraStunWarningChat && mc.player != null) {
                String text = cfg.kuudraStunWarningMessage == null ? "" : cfg.kuudraStunWarningMessage
                    .replace('\n', ' ').replace('\r', ' ').trim();
                if (!text.isEmpty()) mc.player.sendSystemMessage(Component.literal(text));
            }
        }
        return true;
    }

    private static void drawBox(WorldRenderer.Ctx ctx, AABB box, int outline, int fill,
                                boolean throughWalls, DracoConfig cfg) {
        if (cfg.kuudraStunOutline) ctx.outline(box, outline, throughWalls, cfg.kuudraStunLineWidth);
        if (cfg.kuudraStunFill) ctx.box(box, fill, throughWalls);
    }

    private static boolean contains(AABB box, net.minecraft.core.BlockPos point) {
        return point.getX() >= box.minX && point.getX() <= box.maxX
            && point.getY() >= box.minY && point.getY() <= box.maxY
            && point.getZ() >= box.minZ && point.getZ() <= box.maxZ;
    }

    private static AABB box(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
            Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1);
    }

    private static int floor(double value) { return (int) Math.floor(value); }

    private static DracoConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().draco;
    }

    private static boolean active() {
        DracoConfig cfg = config();
        return cfg != null && cfg.enabled && cfg.kuudraStunHelper && KuudraState.inRun();
    }
}
