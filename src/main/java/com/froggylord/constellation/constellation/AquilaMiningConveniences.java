package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CommissionHighlight.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CallMismyla.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/RedialOnBadSignal.java
public final class AquilaMiningConveniences {
    private static final Pattern COMMISSION = Pattern.compile("^([\\w' ]+) Commission Complete! Visit the King to claim your rewards!$");
    private static final Pattern FRED = Pattern.compile("^\\[NPC] (Fred): .*$");
    private static AquilaConfig cfg;
    private static boolean initialized;

    private AquilaMiningConveniences() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || onChat(message));
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.miningConveniencesSuite && ConstellationClient.loc().onHypixel();
    }

    private static boolean onChat(Component component) {
        if (!active()) return true;
        String text = clean(component.getString());
        Matcher commission = COMMISSION.matcher(text);
        if (commission.matches() && cfg.callMismyla && ConstellationClient.loc().area() != SkyblockArea.GLACITE_MINESHAFT) {
            sendCall(format(cfg.callMismylaMessage, commission.group(1), "Mismyla"), "Mismyla", "/call mismyla");
            return !cfg.callMismylaReplaceOriginal;
        }
        Matcher fred = FRED.matcher(text);
        if (fred.matches() && cfg.redialOnBadSignal && obfuscated(component)) {
            sendCall(format(cfg.redialMessage, "", fred.group(1)), fred.group(1), "/call " + fred.group(1));
            return !cfg.redialReplaceOriginal;
        }
        return true;
    }

    private static void sendCall(String text, String npc, String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component action = Component.literal("§b[Call " + npc + "]").withStyle(style -> {
            var next = style.withClickEvent(new ClickEvent.RunCommand(command));
            return cfg.miningCallHoverText
                ? next.withHoverEvent(new HoverEvent.ShowText(Component.literal("Run " + command)))
                : next;
        });
        mc.player.sendSystemMessage(Component.literal("§3[Mining] §f" + text + " ").append(action));
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || !cfg.commissionHighlight || screen == null || slot == null
            || !clean(screen.getTitle().getString()).equals("Commissions")) return;
        ItemLore lore = slot.getItem().get(DataComponents.LORE);
        if (lore == null || lore.lines().stream().map(line -> clean(line.getString())).noneMatch("COMPLETED"::equals)) return;
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.commissionHighlightColor);
        if (cfg.commissionHighlightLabel)
            graphics.text(Minecraft.getInstance().font, "DONE", slot.x, slot.y + 4, 0xFFFFFFFF, true);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningconveniences")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(context -> color(StringArgumentType.getString(context, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(context -> message(StringArgumentType.getString(context, "type"), StringArgumentType.getString(context, "text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Commission highlights " + on(cfg.commissionHighlight) + ", Mismyla calls " + on(cfg.callMismyla)
            + ", bad-signal redial " + on(cfg.redialOnBadSignal) + ".");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.miningConveniencesSuite = value;
            case "commissions", "highlight" -> cfg.commissionHighlight = value;
            case "label" -> cfg.commissionHighlightLabel = value;
            case "mismyla" -> cfg.callMismyla = value;
            case "replacemismyla" -> cfg.callMismylaReplaceOriginal = value;
            case "redial" -> cfg.redialOnBadSignal = value;
            case "replaceredial" -> cfg.redialReplaceOriginal = value;
            case "hover" -> cfg.miningCallHoverText = value;
            default -> { local("Option must be enabled, commissions, label, mismyla, replacemismyla, redial, replaceredial, or hover."); return 0; }
        }
        save();
        return status();
    }

    private static int message(String type, String text) {
        if (text.isBlank() || text.length() > 240) {
            local("Message must contain 1 to 240 characters.");
            return 0;
        }
        if (type.equalsIgnoreCase("mismyla")) cfg.callMismylaMessage = text;
        else if (type.equalsIgnoreCase("redial")) cfg.redialMessage = text;
        else { local("Message type must be mismyla or redial."); return 0; }
        save();
        return status();
    }

    private static int color(String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.length() != 8) throw new NumberFormatException();
            cfg.commissionHighlightColor = (int) Long.parseLong(value, 16);
            save();
            return status();
        } catch (NumberFormatException exception) {
            local("Color must be an eight-digit ARGB hex value.");
            return 0;
        }
    }

    private static String format(String template, String commission, String npc) {
        return template.replace("{commission}", commission).replace("{npc}", npc);
    }

    private static boolean obfuscated(Component component) {
        if (component.getStyle().isObfuscated()) return true;
        for (Component sibling : component.getSiblings()) if (obfuscated(sibling)) return true;
        return false;
    }

    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§3[Mining] §f" + text));
    }
    private static String clean(String text) { return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim(); }
    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
}
