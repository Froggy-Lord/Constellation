package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.OrionConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): features/alerts/SpiritMaskAlert.kt
// ported from devonian (GPL-3.0): features/dungeons/MaskTimers.kt
// ported from Odin (BSD-3-Clause): features/impl/dungeon/InvincibilityTimer.kt
public final class SpiritMaskState {
    private static final Pattern USED = Pattern.compile("^Second Wind Activated! Your Spirit Mask saved your life!$");
    private static OrionConfig cfg;
    private static boolean initialized;
    private static long usedAt;
    private static long readyAt;
    private static boolean readyShown;
    private static ClientLevel lastLevel;

    private SpiritMaskState() {}

    public static void init(OrionConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(1, "orion-spirit-mask", SpiritMaskState::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static boolean active() {
        if (cfg == null || !cfg.enabled || !cfg.spiritMaskTracker || !ConstellationClient.loc().onHypixel()) return false;
        return !cfg.spiritMaskOnlyDungeons || ConstellationClient.loc().inDungeons();
    }

    private static void onChat(String message) {
        if (!active() || !USED.matcher(message).matches()) return;
        long now = System.currentTimeMillis();
        usedAt = now;
        readyAt = now + cooldownMs();
        readyShown = false;
        if (cfg.spiritMaskUsedAlert) alert(false);
    }

    private static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && lastLevel != null && level != lastLevel) reset();
        if (level != null) lastLevel = level;
        if (cfg == null || !cfg.enabled || !cfg.spiritMaskTracker) { reset(); return; }
        if (readyAt == 0 || readyShown || System.currentTimeMillis() < readyAt) return;
        readyShown = true;
        if (!active()) return;
        if (cfg.spiritMaskReadyChat) local(format(cfg.spiritMaskReadyTemplate, true));
        if (cfg.spiritMaskReadyAlert) alert(true);
    }

    private static void alert(boolean ready) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String text = format(ready ? cfg.spiritMaskReadyTemplate : cfg.spiritMaskUsedTemplate, ready);
        boolean title = ready ? cfg.spiritMaskReadyTitle : cfg.spiritMaskUsedTitle;
        boolean chat = ready ? false : cfg.spiritMaskUsedChat;
        boolean sound = ready ? cfg.spiritMaskReadySound : cfg.spiritMaskUsedSound;
        int color = ready ? cfg.spiritMaskReadyColor : cfg.spiritMaskUsedColor;
        if (title) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(0, Math.clamp(cfg.spiritMaskTitleTicks, 5, 200), 10);
            mc.gui.hud.setTitle(Component.literal(text).withColor(color & 0xFFFFFF));
        }
        if (chat) local(text);
        if (sound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, ready ? 1.4f : .8f);
    }

    public static String hudText() {
        if (!active() || !cfg.spiritMaskHud) return null;
        long now = System.currentTimeMillis();
        boolean equipped = isMask(Minecraft.getInstance().player == null ? ItemStack.EMPTY
            : Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.HEAD));
        String prefix = cfg.spiritMaskHudShowEquipped && equipped ? "\u00a75[E] " : "";
        if (readyAt == 0 || now >= readyAt) return cfg.spiritMaskHudShowReady ? prefix + "\u00a7aREADY" : null;
        long immune = usedAt + immunityMs() - now;
        if (immune > 0 && cfg.spiritMaskHudShowImmunity)
            return prefix + "\u00a7bIMM " + String.format(Locale.ROOT, "%.2fs", immune / 1000.0);
        long remaining = readyAt - now;
        double ratio = remaining / (double) cooldownMs();
        String color = ratio >= .75 ? "\u00a7c" : ratio >= .5 ? "\u00a76" : ratio >= .25 ? "\u00a7e" : "\u00a7a";
        return prefix + color + String.format(Locale.ROOT, "%.1fs", remaining / 1000.0);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, Slot slot) {
        if (!active() || !cfg.spiritMaskItemCooldown || slot == null || !isMask(slot.getItem())) return;
        long remaining = readyAt - System.currentTimeMillis();
        if (remaining <= 0) return;
        double ratio = Math.clamp(remaining / (double) cooldownMs(), 0, 1);
        if (cfg.spiritMaskItemShade) {
            int top = slot.y + (int) Math.round((1 - ratio) * 16);
            graphics.fill(slot.x, top, slot.x + 16, slot.y + 16, cfg.spiritMaskCooldownColor);
        }
        if (cfg.spiritMaskItemText) {
            String text = Long.toString(Math.max(1, (remaining + 999) / 1000));
            Font font = Minecraft.getInstance().font;
            float scale = .7f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(slot.x + 15 - font.width(text) * scale, slot.y + 8);
            graphics.pose().scale(scale, scale);
            graphics.text(font, text, 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("spiritmask")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> { reset(); local("State cleared."); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cooldown")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 300))
                    .executes(c -> number("cooldown", IntegerArgumentType.getInteger(c, "seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("immunity")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("milliseconds", IntegerArgumentType.integer(0, 10000))
                    .executes(c -> number("immunity", IntegerArgumentType.getInteger(c, "milliseconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(5, 200))
                    .executes(c -> number("duration", IntegerArgumentType.getInteger(c, "ticks")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(c -> template(StringArgumentType.getString(c, "state"), StringArgumentType.getString(c, "text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("part", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(c -> color(StringArgumentType.getString(c, "part"), StringArgumentType.getString(c, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static int status() {
        long remaining = Math.max(0, readyAt - System.currentTimeMillis());
        local("Tracker " + on(cfg.spiritMaskTracker) + ", used alert " + on(cfg.spiritMaskUsedAlert)
            + ", ready alert " + on(cfg.spiritMaskReadyAlert) + ", HUD " + on(cfg.spiritMaskHud)
            + ", item " + on(cfg.spiritMaskItemCooldown) + ", remaining " + String.format(Locale.ROOT, "%.1fs", remaining / 1000.0) + '.');
        return 1;
    }

    private static int number(String name, int value) {
        if (name.equals("cooldown")) cfg.spiritMaskCooldownSeconds = value;
        else if (name.equals("immunity")) cfg.spiritMaskImmunityMillis = value;
        else cfg.spiritMaskTitleTicks = value;
        ConstellationClient.saveConfig(); local("Updated " + name + '.'); return 1;
    }

    private static int template(String state, String text) {
        if (state.equalsIgnoreCase("used")) cfg.spiritMaskUsedTemplate = text;
        else if (state.equalsIgnoreCase("ready")) cfg.spiritMaskReadyTemplate = text;
        else { local("State must be used or ready."); return 0; }
        ConstellationClient.saveConfig(); local("Template updated."); return 1;
    }

    private static int color(String part, String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw.startsWith("0x") ? raw.substring(2) : raw;
            long parsed = Long.parseUnsignedLong(value, 16);
            int argb = value.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
            if (part.equalsIgnoreCase("used")) cfg.spiritMaskUsedColor = argb;
            else if (part.equalsIgnoreCase("ready")) cfg.spiritMaskReadyColor = argb;
            else if (part.equalsIgnoreCase("cooldown")) cfg.spiritMaskCooldownColor = argb;
            else { local("Part must be used, ready, or cooldown."); return 0; }
            ConstellationClient.saveConfig(); local("Color updated."); return 1;
        } catch (NumberFormatException ignored) { local("Color must be RRGGBB or AARRGGBB."); return 0; }
    }

    private static int option(String name, String state) {
        Boolean value = parse(state); if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "tracker" -> cfg.spiritMaskTracker = value;
            case "dungeon", "dungeonsonly" -> cfg.spiritMaskOnlyDungeons = value;
            case "used", "usedalert" -> cfg.spiritMaskUsedAlert = value;
            case "usedtitle" -> cfg.spiritMaskUsedTitle = value;
            case "usedchat" -> cfg.spiritMaskUsedChat = value;
            case "usedsound" -> cfg.spiritMaskUsedSound = value;
            case "ready", "readyalert" -> cfg.spiritMaskReadyAlert = value;
            case "readytitle" -> cfg.spiritMaskReadyTitle = value;
            case "readychat" -> cfg.spiritMaskReadyChat = value;
            case "readysound" -> cfg.spiritMaskReadySound = value;
            case "hud" -> cfg.spiritMaskHud = value;
            case "equipped" -> cfg.spiritMaskHudShowEquipped = value;
            case "immunity" -> cfg.spiritMaskHudShowImmunity = value;
            case "showready" -> cfg.spiritMaskHudShowReady = value;
            case "item" -> cfg.spiritMaskItemCooldown = value;
            case "itemtext" -> cfg.spiritMaskItemText = value;
            case "itemshade" -> cfg.spiritMaskItemShade = value;
            default -> { local("Unknown option."); return 0; }
        }
        if (!cfg.spiritMaskTracker) reset();
        ConstellationClient.saveConfig(); local("Option updated."); return 1;
    }

    public static void reset() { usedAt = 0; readyAt = 0; readyShown = false; }
    private static long cooldownMs() { return Math.clamp(cfg.spiritMaskCooldownSeconds, 1, 300) * 1000L; }
    private static long immunityMs() { return Math.clamp(cfg.spiritMaskImmunityMillis, 0, 10000); }
    private static String format(String template, boolean ready) { return template.replace("{state}", ready ? "ready" : "used").replace('&', '\u00a7'); }
    private static boolean isMask(ItemStack stack) { String id = id(stack); return id.equals("SPIRIT_MASK") || id.equals("STARRED_SPIRIT_MASK"); }
    private static String id(ItemStack stack) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); if (data == null) return ""; CompoundTag root = data.copyTag(), legacy = root.getCompoundOrEmpty("ExtraAttributes"); return (legacy.isEmpty() ? root : legacy).getStringOr("id", ""); }
    private static String clean(String text) { String value = ChatFormatting.stripFormatting(text); return value == null ? text : value; }
    private static Boolean parse(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean value) { return value ? "\u00a7aon" : "\u00a7coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a75[Spirit Mask] \u00a7f" + text)); }
}
