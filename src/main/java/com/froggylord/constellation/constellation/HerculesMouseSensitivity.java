package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.FishingRodItem;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/MouseSensitivityReducer.kt
public final class HerculesMouseSensitivity {
    private enum State { LOCKED, REDUCED }

    private static HerculesConfig cfg;
    private static State manualState;
    private static State autoState;
    private static KeyMapping toggleKey;
    private static boolean keyWasDown;

    private HerculesMouseSensitivity() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        toggleKey = ConstellationClient.instance().keys().register("garden_sensitivity", GLFW.GLFW_KEY_N);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
        });
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("farmmouselock")
            .executes(ctx -> { toggleManual(State.LOCKED); return 1; }));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sensreduce")
            .executes(ctx -> { toggleManual(State.REDUCED); return 1; }));
    }

    public static double remap(double original) {
        if (cfg == null || !cfg.enabled || !cfg.mouseSensitivityHelper) return original;
        State state = activeState();
        if (state == State.LOCKED) return 0;
        if (state == State.REDUCED) return original * Math.clamp(cfg.mouseSensitivityPercent, 0, 100) / 100.0;
        return original;
    }

    public static String hudText() {
        if (cfg == null || !cfg.enabled || !cfg.mouseSensitivityHelper || !cfg.mouseSensitivityHud) return null;
        return switch (activeState()) {
            case LOCKED -> "Locked";
            case REDUCED -> Math.clamp(cfg.mouseSensitivityPercent, 0, 100) + "%";
            case null -> null;
        };
    }

    private static void tick() {
        if (cfg == null) return;
        boolean down = toggleKey != null && toggleKey.isDown();
        if (down && !keyWasDown && cfg.enabled && cfg.mouseSensitivityHelper) {
            toggleManual(cfg.mouseSensitivityLockMouse ? State.LOCKED : State.REDUCED);
        }
        keyWasDown = down;
        autoState = autoEnabled() ? (cfg.mouseSensitivityLockMouse ? State.LOCKED : State.REDUCED) : null;
    }

    private static boolean autoEnabled() {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.enabled || !cfg.mouseSensitivityHelper || !cfg.mouseSensitivityAutoEnable || mc.player == null) return false;
        if (ConstellationClient.loc().area() != LocationManager.SkyblockArea.GARDEN) return false;
        if (cfg.mouseSensitivityOnlyPlot) {
            Integer plot = HerculesPests.plotAt(mc.player.getX(), mc.player.getZ());
            if (plot == null || plot == 0) return false;
        }
        if (cfg.mouseSensitivityOnGround && !onGround(mc)) return false;

        String id = LyraTooltips.marketId(mc.player.getMainHandItem()).toUpperCase(Locale.ROOT);
        return (cfg.mouseSensitivityModeKeybind && toggleKey != null && toggleKey.isDown())
            || (cfg.mouseSensitivityModeTool && HerculesGardenTracker.cropInHand(mc.player.getMainHandItem()) != null)
            || (cfg.mouseSensitivityModeFishingRod && mc.player.getMainHandItem().getItem() instanceof FishingRodItem)
            || (cfg.mouseSensitivityModeVacuum && (id.contains("VACUUM") || id.contains("LASSO")))
            || (cfg.mouseSensitivityModeMousemat && id.contains("MOUSEMAT"))
            || (cfg.mouseSensitivityModeSprayonator && id.contains("SPRAYONATOR"))
            || (cfg.mouseSensitivityModeSunsGrasp && id.contains("SUNS_GRASP"));
    }

    private static State activeState() {
        return manualState != null ? manualState : autoState;
    }

    private static boolean onGround(Minecraft mc) {
        if (mc.player.onGround()) return true;
        double tolerance = Math.clamp(cfg.mouseSensitivityGroundToleranceHundredths, 0, 100) / 100.0;
        return tolerance > 0 && mc.level != null
            && mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().move(0, -tolerance, 0)).iterator().hasNext();
    }

    private static void toggleManual(State wanted) {
        manualState = manualState == wanted ? null : wanted;
        if (!cfg.mouseSensitivityChat) return;
        if (manualState == State.LOCKED) local("Mouse rotation locked. Use /farmmouselock or the farming sensitivity key to unlock.");
        else if (manualState == State.REDUCED) local("Mouse sensitivity lowered. Use /sensreduce or the farming sensitivity key to restore it.");
        else local("Mouse sensitivity restored.");
    }

    private static void onChat(String message) {
        if (cfg == null || !cfg.enabled || !cfg.mouseSensitivityHelper) return;
        if (cfg.mouseSensitivityLockOnMousemat && message.equalsIgnoreCase("Snapped to squeaky mousemat!")) {
            manualState = State.LOCKED;
            if (cfg.mouseSensitivityChat) local("Mouse rotation locked after snapping to the mousemat.");
            return;
        }
        if (manualState == null || "NEVER".equalsIgnoreCase(cfg.mouseSensitivityUnlockOnTeleport)) return;
        boolean teleport = message.matches("(?i)Teleported you to Plot - .+!")
            || message.equalsIgnoreCase("Teleported you to The Barn!")
            || message.equalsIgnoreCase("Warping...");
        if (!teleport) return;
        boolean barnOnly = "BARN_ONLY".equalsIgnoreCase(cfg.mouseSensitivityUnlockOnTeleport);
        if (!barnOnly || message.equalsIgnoreCase("Teleported you to The Barn!")) {
            manualState = null;
            if (cfg.mouseSensitivityChat) local("Mouse sensitivity restored after teleporting.");
        }
    }

    private static void reset() {
        manualState = null;
        autoState = null;
        keyWasDown = false;
    }

    private static String clean(String text) {
        String clean = ChatFormatting.stripFormatting(text);
        return clean == null ? "" : clean.trim();
    }

    private static void local(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§2[Garden] §f" + message));
    }
}
