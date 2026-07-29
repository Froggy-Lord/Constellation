package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.mixin.PlayerTabOverlayAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/EffectWidget.java
// ported from SkyblockAddons (LGPL-3.0-only): features/tablist/TabListParser.java, resources/regex.json
public final class AurigaBuffStatus {
    public enum Status { UNKNOWN, ACTIVE, INACTIVE, EXPIRED }
    public record State(Status god, long godExpiry, Status cookie, long cookieExpiry,
                        int effectCount, long footerSeenAt) {}

    private static final Pattern GOD = Pattern.compile("You have a God Potion active!\\s+([^\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EFFECTS = Pattern.compile("You have ([0-9]+) active effect", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("(\\d+)\\s*(days?|hours?|minutes?|seconds?|[dhms])", Pattern.CASE_INSENSITIVE);
    private static AurigaConfig cfg;
    private static State state = new State(Status.UNKNOWN, 0, Status.UNKNOWN, 0, -1, 0);
    private static String trackedProfile = "";
    private static long previousGod = Long.MIN_VALUE;
    private static long previousCookie = Long.MIN_VALUE;
    private static boolean godWarned;
    private static boolean cookieWarned;
    private static int ticks;

    private AurigaBuffStatus() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks >= 20) {
                ticks = 0;
                tick(client);
            }
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("buffstatus")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(c -> {
                cfg.buffStatus = !cfg.buffStatus;
                save();
                return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("godwarning")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("minutes", IntegerArgumentType.integer(0, 1440))
                    .executes(c -> { cfg.buffStatusGodWarningMinutes = IntegerArgumentType.getInteger(c, "minutes"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cookiewarning")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("hours", IntegerArgumentType.integer(0, 168))
                    .executes(c -> { cfg.buffStatusCookieWarningHours = IntegerArgumentType.getInteger(c, "hours"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("setgod")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("minutes", IntegerArgumentType.integer(1, 10080))
                    .executes(c -> manual(true, IntegerArgumentType.getInteger(c, "minutes") * 60_000L))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("setcookie")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("hours", IntegerArgumentType.integer(1, 8760))
                    .executes(c -> manual(false, IntegerArgumentType.getInteger(c, "hours") * 3_600_000L))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void tick(Minecraft mc) {
        if (!active()) return;
        String profile = profile();
        if (!profile.equals(trackedProfile)) {
            trackedProfile = profile;
            resetCrossings();
            loadSaved();
        }
        boundaries();
        String footer = footer(mc);
        if (!footer.isBlank()) parseFooter(footer);
        boundaries();
    }

    private static void parseFooter(String footer) {
        long now = System.currentTimeMillis();
        Matcher god = GOD.matcher(footer);
        Status godStatus;
        long godExpiry;
        if (god.find()) {
            long duration = duration(god.group(1));
            godExpiry = duration > 0 ? now + duration : saved(cfg.buffStatusGodExpiry);
            godStatus = godExpiry > now ? Status.ACTIVE : godExpiry > 0 ? Status.EXPIRED : Status.UNKNOWN;
            persist(cfg.buffStatusGodExpiry, godExpiry);
        } else if (footer.contains("Active Effects")) {
            godExpiry = 0;
            godStatus = Status.INACTIVE;
            remove(cfg.buffStatusGodExpiry);
        } else {
            godExpiry = state.godExpiry();
            godStatus = state.god();
        }

        Status cookieStatus = state.cookie();
        long cookieExpiry = state.cookieExpiry();
        String[] lines = footer.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().equalsIgnoreCase("Cookie Buff")) continue;
            String value = next(lines, i + 1);
            if (value.toLowerCase(Locale.ROOT).startsWith("not active")) {
                cookieStatus = Status.INACTIVE;
                cookieExpiry = 0;
                remove(cfg.buffStatusCookieExpiry);
            } else {
                long duration = duration(value);
                if (duration > 0) {
                    cookieExpiry = now + duration;
                    cookieStatus = Status.ACTIVE;
                    persist(cfg.buffStatusCookieExpiry, cookieExpiry);
                }
            }
            break;
        }
        int effectCount = state.effectCount();
        Matcher effects = EFFECTS.matcher(footer);
        if (effects.find()) effectCount = Integer.parseInt(effects.group(1));
        else if (footer.contains("No effects active") || footer.contains("No Effects")) effectCount = 0;
        state = new State(godStatus, godExpiry, cookieStatus, cookieExpiry, effectCount, now);
    }

    private static void boundaries() {
        long now = System.currentTimeMillis();
        long godRemaining = state.godExpiry() - now;
        long cookieRemaining = state.cookieExpiry() - now;
        long godWarning = Math.clamp(cfg.buffStatusGodWarningMinutes, 0, 1440) * 60_000L;
        long cookieWarning = Math.clamp(cfg.buffStatusCookieWarningHours, 0, 168) * 3_600_000L;
        if (previousGod != Long.MIN_VALUE && previousGod > godWarning && godRemaining <= godWarning && godRemaining > 0
            && cfg.buffStatusGodWarning && !godWarned) {
            godWarned = true;
            alert("God Potion expires in " + format(godRemaining) + ".", false);
        }
        if (previousGod > 0 && godRemaining <= 0 && state.godExpiry() > 0 && cfg.buffStatusGodExpired) {
            alert("God Potion expired.", true);
            state = new State(Status.EXPIRED, state.godExpiry(), state.cookie(), state.cookieExpiry(), state.effectCount(), state.footerSeenAt());
        }
        if (previousCookie != Long.MIN_VALUE && previousCookie > cookieWarning && cookieRemaining <= cookieWarning && cookieRemaining > 0
            && cfg.buffStatusCookieWarning && !cookieWarned) {
            cookieWarned = true;
            alert("Cookie Buff expires in " + format(cookieRemaining) + ".", false);
        }
        if (previousCookie > 0 && cookieRemaining <= 0 && state.cookieExpiry() > 0 && cfg.buffStatusCookieExpired) {
            alert("Cookie Buff expired.", true);
            state = new State(state.god(), state.godExpiry(), Status.EXPIRED, state.cookieExpiry(), state.effectCount(), state.footerSeenAt());
        }
        previousGod = state.godExpiry() > 0 ? godRemaining : Long.MIN_VALUE;
        previousCookie = state.cookieExpiry() > 0 ? cookieRemaining : Long.MIN_VALUE;
    }

    private static void loadSaved() {
        long now = System.currentTimeMillis();
        long god = saved(cfg.buffStatusGodExpiry);
        long cookie = saved(cfg.buffStatusCookieExpiry);
        state = new State(savedStatus(god, now), god, savedStatus(cookie, now), cookie, -1, 0);
        previousGod = god > now ? god - now : Long.MIN_VALUE;
        previousCookie = cookie > now ? cookie - now : Long.MIN_VALUE;
    }

    private static Status savedStatus(long expiry, long now) {
        return expiry <= 0 ? Status.UNKNOWN : expiry > now ? Status.ACTIVE : Status.EXPIRED;
    }

    private static void persist(Map<String, Long> values, long expiry) {
        String profile = profile();
        if (profile.equals("unknown") || expiry <= 0) return;
        long old = values.getOrDefault(profile, 0L);
        if (Math.abs(old - expiry) < 90_000L) return;
        values.put(profile, expiry);
        save();
    }

    private static void remove(Map<String, Long> values) {
        String profile = profile();
        if (!profile.equals("unknown") && values.remove(profile) != null) save();
    }

    private static long saved(Map<String, Long> values) {
        String profile = profile();
        return profile.equals("unknown") ? 0 : values.getOrDefault(profile, 0L);
    }

    private static long duration(String value) {
        Matcher matcher = TIME.matcher(value);
        long millis = 0;
        while (matcher.find()) {
            long amount;
            try { amount = Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException ignored) { return -1; }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            millis += unit.startsWith("d") ? amount * 86_400_000L
                : unit.startsWith("h") ? amount * 3_600_000L
                : unit.startsWith("m") ? amount * 60_000L
                : unit.startsWith("s") ? amount * 1000L : 0;
        }
        return millis;
    }

    private static String footer(Minecraft mc) {
        if (mc.gui == null) return "";
        Component component = ((PlayerTabOverlayAccessor) mc.gui.hud.getTabList()).constellation$footer();
        if (component == null) return "";
        String clean = ChatFormatting.stripFormatting(component.getString());
        return clean == null ? component.getString().trim() : clean.trim();
    }

    private static String next(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) if (!lines[i].isBlank()) return lines[i].trim();
        return "";
    }

    private static void alert(String text, boolean expired) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (expired ? cfg.buffStatusExpiredChat : cfg.buffStatusWarningChat) local(text);
        if (expired ? cfg.buffStatusExpiredTitle : cfg.buffStatusWarningTitle)
            mc.gui.hud.setTitle(Component.literal(text).withColor((expired ? cfg.buffStatusExpiredColor : cfg.buffStatusWarningColor) & 0xFFFFFF));
        if (expired ? cfg.buffStatusExpiredSound : cfg.buffStatusWarningSound)
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, expired ? .7f : 1f);
    }

    public static State state() { return state; }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() {
        if (!active() || !cfg.buffStatusHud) return false;
        if (cfg.buffStatusShowUnknown) return true;
        return state.god() != Status.UNKNOWN || state.cookie() != Status.UNKNOWN || state.effectCount() >= 0;
    }
    public static String profile() {
        String value = LyraStorageValue.currentProfileKey();
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
    public static String format(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m " + seconds + "s";
    }

    private static int clear() {
        String profile = profile();
        if (!profile.equals("unknown")) {
            cfg.buffStatusGodExpiry.remove(profile);
            cfg.buffStatusCookieExpiry.remove(profile);
        }
        state = new State(Status.UNKNOWN, 0, Status.UNKNOWN, 0, -1, 0);
        resetCrossings();
        save();
        return status();
    }

    private static int manual(boolean god, long duration) {
        if (profile().equals("unknown")) {
            local("Wait until the SkyBlock profile is detected before setting a manual timer.");
            return 0;
        }
        long expiry = System.currentTimeMillis() + duration;
        if (god) cfg.buffStatusGodExpiry.put(profile(), expiry);
        else cfg.buffStatusCookieExpiry.put(profile(), expiry);
        save();
        loadSaved();
        return status();
    }

    private static int status() {
        long now = System.currentTimeMillis();
        local("God Potion " + describe(state.god(), state.godExpiry(), now) + ", Cookie Buff "
            + describe(state.cookie(), state.cookieExpiry(), now) + ", effects "
            + (state.effectCount() < 0 ? "unknown" : state.effectCount()) + ", profile " + profile() + ".");
        return 1;
    }

    private static String describe(Status status, long expiry, long now) {
        return status == Status.ACTIVE && expiry > now ? format(expiry - now) : status.name().toLowerCase(Locale.ROOT);
    }

    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.buffStatus = value;
            case "hud" -> cfg.buffStatusHud = value;
            case "god" -> cfg.buffStatusGodPotion = value;
            case "cookie" -> cfg.buffStatusCookie = value;
            case "effects" -> cfg.buffStatusEffectCount = value;
            case "unknown" -> cfg.buffStatusShowUnknown = value;
            case "expired" -> cfg.buffStatusShowExpired = value;
            case "sourceage" -> cfg.buffStatusShowSourceAge = value;
            case "profile" -> cfg.buffStatusShowProfile = value;
            case "godwarning" -> cfg.buffStatusGodWarning = value;
            case "godexpired" -> cfg.buffStatusGodExpired = value;
            case "cookiewarning" -> cfg.buffStatusCookieWarning = value;
            case "cookieexpired" -> cfg.buffStatusCookieExpired = value;
            case "warningchat" -> cfg.buffStatusWarningChat = value;
            case "expiredchat" -> cfg.buffStatusExpiredChat = value;
            case "warningtitle" -> cfg.buffStatusWarningTitle = value;
            case "expiredtitle" -> cfg.buffStatusExpiredTitle = value;
            case "warningsound" -> cfg.buffStatusWarningSound = value;
            case "expiredsound" -> cfg.buffStatusExpiredSound = value;
            default -> { local("Unknown Buff Status option."); return 0; }
        }
        save();
        return status();
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.buffStatus && ConstellationClient.loc().onHypixel();
    }
    private static void resetCrossings() {
        previousGod = Long.MIN_VALUE;
        previousCookie = Long.MIN_VALUE;
        godWarned = false;
        cookieWarned = false;
    }
    private static Boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a75[Buff Status] \u00a7f" + text));
    }
    private static void save() { ConstellationClient.saveConfig(); }
}
