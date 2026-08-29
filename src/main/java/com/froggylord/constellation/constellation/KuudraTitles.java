package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraTitles {
    private static final Pattern PROGRESS = Pattern.compile("^[\\[| ]+]\\s*(?<progress>\\d{1,3})%$");
    private static int progress = -1;

    private KuudraTitles() {}

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraTitles.kt
    public static boolean onTitle(Component title) {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraTitles || !cfg.kuudraSupplyProgressHud
            || !activePhase() || title == null) return false;
        String text = ChatFormatting.stripFormatting(title.getString());
        Matcher matcher = PROGRESS.matcher(text == null ? title.getString() : text);
        if (!matcher.matches()) return false;
        progress = Math.clamp(parse(matcher.group("progress"), 0), 0, 100);
        return cfg.kuudraHideOriginalProgressTitle;
    }

    public static void onPickup() {
        progress = -1;
        DracoConfig cfg = config();
        if (cfg != null && cfg.enabled && cfg.kuudraTitles && cfg.kuudraPickupAlert)
            alert(cfg.kuudraPickupAlertMessage, cfg.kuudraPickupAlertTitle,
                cfg.kuudraPickupAlertChat, cfg.kuudraPickupAlertSound, 1.35f);
    }

    public static void onDrop() {
        progress = -1;
        DracoConfig cfg = config();
        if (cfg != null && cfg.enabled && cfg.kuudraTitles && cfg.kuudraDropAlert)
            alert(cfg.kuudraDropAlertMessage, cfg.kuudraDropAlertTitle,
                cfg.kuudraDropAlertChat, cfg.kuudraDropAlertSound, 0.75f);
    }

    public static void tick() {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraTitles || !cfg.kuudraSupplyProgressHud || !activePhase())
            progress = -1;
    }

    public static String hudText() {
        DracoConfig cfg = config();
        if (cfg == null || !cfg.enabled || !cfg.kuudraTitles || !cfg.kuudraSupplyProgressHud
            || !activePhase() || progress < 0) return null;
        int bars = Math.clamp(cfg.kuudraSupplyProgressBars, 5, 30);
        int filled = progress * bars / 100;
        String full = safeCharacter(cfg.kuudraSupplyProgressFilled, "|").repeat(filled);
        String left = safeCharacter(cfg.kuudraSupplyProgressEmpty, "|").repeat(bars - filled);
        return clean(cfg.kuudraSupplyProgressStyle)
            .replace("#perc", Integer.toString(progress)).replace("{percent}", Integer.toString(progress))
            .replace("#bars", full).replace("{filled}", full)
            .replace("#total", left).replace("{empty}", left)
            .replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static void reset() { progress = -1; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("kuudratitles")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bars")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(5, 30))
                    .executes(context -> bars(IntegerArgumentType.getInteger(context, "count")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("character")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(context -> character(StringArgumentType.getString(context, "type"),
                            StringArgumentType.getString(context, "text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("style")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("template", StringArgumentType.greedyString())
                    .executes(context -> style(StringArgumentType.getString(context, "template")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(context -> message(StringArgumentType.getString(context, "type"),
                            StringArgumentType.getString(context, "text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("enabled", BoolArgumentType.bool())
                        .executes(context -> option(StringArgumentType.getString(context, "name"),
                            BoolArgumentType.getBool(context, "enabled")))))));
    }

    private static int status() {
        DracoConfig cfg = config();
        local("Kuudra titles " + on(cfg.kuudraTitles) + "; progress HUD " + on(cfg.kuudraSupplyProgressHud)
            + "; bars " + cfg.kuudraSupplyProgressBars + "; hide original " + on(cfg.kuudraHideOriginalProgressTitle) + ".");
        local("Drop alert " + on(cfg.kuudraDropAlert) + "; pickup alert " + on(cfg.kuudraPickupAlert) + ".");
        return 1;
    }

    private static int bars(int count) {
        config().kuudraSupplyProgressBars = Math.clamp(count, 5, 30);
        save();
        local("Kuudra supply progress bars set to " + count + ".");
        return 1;
    }

    private static int character(String type, String text) {
        String value = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty() || value.length() > 4) {
            local("Bar character must be 1-4 characters.");
            return 0;
        }
        if (type.equalsIgnoreCase("filled")) config().kuudraSupplyProgressFilled = value;
        else if (type.equalsIgnoreCase("empty") || type.equalsIgnoreCase("left")) config().kuudraSupplyProgressEmpty = value;
        else { local("Character type must be filled or empty."); return 0; }
        save();
        local("Kuudra supply " + type + " character updated.");
        return 1;
    }

    private static int style(String template) {
        String value = template.trim();
        if (value.isEmpty() || value.length() > 240) { local("Progress style must be 1-240 characters."); return 0; }
        config().kuudraSupplyProgressStyle = value;
        save();
        local("Kuudra progress style updated. Variables: {filled} {empty} {percent}");
        return 1;
    }

    private static int message(String type, String text) {
        String value = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty() || value.length() > 160) { local("Alert message must be 1-160 characters."); return 0; }
        if (type.equalsIgnoreCase("pickup")) config().kuudraPickupAlertMessage = value;
        else if (type.equalsIgnoreCase("drop")) config().kuudraDropAlertMessage = value;
        else { local("Message type must be pickup or drop."); return 0; }
        save();
        local("Kuudra " + type + " alert message updated.");
        return 1;
    }

    private static int option(String name, boolean enabled) {
        DracoConfig cfg = config();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "master" -> cfg.kuudraTitles = enabled;
            case "hud", "progress" -> cfg.kuudraSupplyProgressHud = enabled;
            case "hide", "hideoriginal" -> cfg.kuudraHideOriginalProgressTitle = enabled;
            case "drop" -> cfg.kuudraDropAlert = enabled;
            case "droptitle" -> cfg.kuudraDropAlertTitle = enabled;
            case "dropchat" -> cfg.kuudraDropAlertChat = enabled;
            case "dropsound" -> cfg.kuudraDropAlertSound = enabled;
            case "pickup" -> cfg.kuudraPickupAlert = enabled;
            case "pickuptitle" -> cfg.kuudraPickupAlertTitle = enabled;
            case "pickupchat" -> cfg.kuudraPickupAlertChat = enabled;
            case "pickupsound" -> cfg.kuudraPickupAlertSound = enabled;
            default -> {
                local("Unknown option. Use enabled, hud, hide, drop/pickup, or their title/chat/sound variants.");
                return 0;
            }
        }
        if (!cfg.kuudraTitles || !cfg.kuudraSupplyProgressHud) progress = -1;
        save();
        local("Kuudra titles " + name + " set to " + enabled + ".");
        return 1;
    }

    private static void alert(String text, boolean title, boolean chat, boolean sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        String value = clean(text).replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isEmpty() || mc.player == null) return;
        if (chat) mc.player.sendSystemMessage(Component.literal(value));
        if (title) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal(value));
        }
        if (sound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, pitch);
    }

    private static boolean activePhase() {
        return KuudraState.inRun() && (KuudraState.phase() == KuudraState.Phase.SUPPLY
            || KuudraState.phase() == KuudraState.Phase.FUEL);
    }

    private static String safeCharacter(String value, String fallback) {
        if (value == null) return fallback;
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.isEmpty() ? fallback : clean.substring(0, Math.min(4, clean.length()));
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replace("<dark_red>", "§4").replace("<dark_gray>", "§8")
            .replace("<gray>", "§7").replace("<red>", "§c").replace("<green>", "§a")
            .replace("<aqua>", "§b").replace("<yellow>", "§e").replace("<orange>", "§6")
            .replace("<white>", "§f").replace("<reset>", "§r").replace("<r>", "§r");
    }

    private static DracoConfig config() {
        return ConstellationClient.cfg() == null ? null : ConstellationClient.cfg().draco;
    }
    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}
