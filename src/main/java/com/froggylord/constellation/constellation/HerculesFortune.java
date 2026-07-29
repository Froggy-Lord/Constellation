package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.data.TabList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// fortune state and display ported from SkyHanni (LGPL-3.0-or-later): features/garden/FarmingFortuneDisplay.kt
public final class HerculesFortune {
    public record FortuneRow(String label, String value, int color) {}
    private static final Pattern UNIVERSAL = Pattern.compile("^Farming Fortune:\\s*[^0-9+]*(?<fortune>[\\d,.]+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CROP = Pattern.compile("^(?<crop>Wheat|Carrot|Potato|Nether Wart|Pumpkin|Melon|Cocoa Beans|Sugar Cane|Cactus|Mushroom|Sunflower|Moonflower|Wild Rose) Fortune:\\s*[^0-9+]*(?<fortune>[\\d,.]+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BONUS = Pattern.compile("^Bonus:\\s*(?:(?<inactive>INACTIVE)|\\+(?<fortune>\\d+)\\D+\\s+(?<time>.+))$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BONUS_CHANCE = Pattern.compile("^Bonus Pest Chance:\\s*[^0-9]*(?<amount>[\\d,.]+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PESTS_ALIVE = Pattern.compile("^Alive:\\s*(?<amount>[\\d,]+).*$", Pattern.CASE_INSENSITIVE);
    private static HerculesConfig cfg;
    private static double universal, cropFortune, bonusPestChance;
    private static int pestAlive;
    private static HerculesGardenTracker.Crop tabCrop;
    private static boolean foundUniversal, foundCrop, wasGarden;
    private static long joinedAt, farmingAt, lastUniversalWarning, lastCropWarning;

    private HerculesFortune() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        ConstellationClient.tick().every(10, "hercules-fortune", HerculesFortune::tick);
    }

    private static void tick() {
        boolean garden = activeGarden();
        if (!garden) {
            wasGarden = false;
            return;
        }
        if (!wasGarden) {
            wasGarden = true;
            joinedAt = System.currentTimeMillis();
            farmingAt = 0;
            foundUniversal = false;
            foundCrop = false;
        }
        readTab();
        if (HerculesGardenTracker.rates() != null && farmingAt == 0) farmingAt = System.currentTimeMillis();
        missingWarnings();
        bonusExpiry();
    }

    private static void readTab() {
        for (String raw : TabList.lines()) {
            String line = clean(raw);
            Matcher matcher = CROP.matcher(line);
            if (matcher.matches()) {
                HerculesGardenTracker.Crop crop = crop(matcher.group("crop"));
                if (crop != null) {
                    tabCrop = crop;
                    cropFortune = number(matcher.group("fortune"));
                    foundCrop = true;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && HerculesGardenTracker.cropInHand(mc.player.getMainHandItem()) == crop) {
                        double value = universal + cropFortune;
                        Double old = cfg.fortuneLatestByCrop.put(crop.name(), value);
                        if (!Objects.equals(old, value)) save();
                    }
                }
                continue;
            }
            matcher = UNIVERSAL.matcher(line);
            if (matcher.matches()) {
                universal = number(matcher.group("fortune"));
                foundUniversal = true;
                continue;
            }
            matcher = BONUS_CHANCE.matcher(line);
            if (matcher.matches()) {
                bonusPestChance = number(matcher.group("amount"));
                continue;
            }
            matcher = PESTS_ALIVE.matcher(line);
            if (matcher.matches()) {
                pestAlive = (int)number(matcher.group("amount"));
                continue;
            }
            matcher = BONUS.matcher(line);
            if (!matcher.matches()) continue;
            if (matcher.group("inactive") != null) {
                boolean warn = cfg.fortunePestBonusExpiry > 0 && !cfg.fortunePestBonusNotified;
                cfg.fortunePestBonus = 0;
                cfg.fortunePestBonusExpiry = 0;
                if (warn) bonusExpired();
            } else {
                long duration = duration(matcher.group("time"));
                int amount = (int) number(matcher.group("fortune"));
                if (duration > 0 && amount > 0) {
                    long expected = System.currentTimeMillis() + duration;
                    if (Math.abs(expected - cfg.fortunePestBonusExpiry) > 1500 || cfg.fortunePestBonus != amount) {
                        cfg.fortunePestBonus = amount;
                        cfg.fortunePestBonusExpiry = expected;
                        cfg.fortunePestBonusNotified = false;
                        save();
                    }
                }
            }
        }
    }

    private static void missingWarnings() {
        if (cfg.fortuneHideMissingWarnings || !cfg.fortuneMissingChat) return;
        long now = System.currentTimeMillis();
        long repeat = Math.max(5, cfg.fortuneMissingRepeatSeconds) * 1000L;
        if (!foundUniversal && now - joinedAt >= Math.max(1, cfg.fortuneUniversalMissingSeconds) * 1000L && now - lastUniversalWarning >= repeat) {
            lastUniversalWarning = now;
            missing("Farming Fortune is missing from the Stats widget.");
        }
        if (!foundCrop && farmingAt > 0 && now - farmingAt >= Math.max(1, cfg.fortuneCropMissingSeconds) * 1000L && now - lastCropWarning >= repeat) {
            lastCropWarning = now;
            missing("Crop Fortune is missing from the Stats widget.");
        }
    }

    private static void missing(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component message = Component.literal("\u00a72[Farming Fortune] \u00a7c" + text);
        if (cfg.fortuneMissingClickableWidget) {
            message = message.copy().append(Component.literal(" \u00a7e[Open Widgets]")
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/widget"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Run /widget")))));
        }
        mc.player.sendSystemMessage(message);
    }

    private static void bonusExpiry() {
        if (cfg.fortunePestBonusExpiry <= 0 || cfg.fortunePestBonusNotified || System.currentTimeMillis() < cfg.fortunePestBonusExpiry) return;
        bonusExpired();
    }

    private static void bonusExpired() {
        cfg.fortunePestBonusNotified = true;
        cfg.fortunePestBonus = 0;
        cfg.fortunePestBonusExpiry = 0;
        Minecraft mc = Minecraft.getInstance();
        String text = cfg.fortuneBonusExpireTemplate;
        if (mc.player != null && cfg.fortuneBonusExpireChat) {
            Component line = Component.literal("\u00a72[Farming Fortune] \u00a7c" + text);
            if (cfg.fortuneBonusClickableAction) {
                String command = cfg.fortuneBonusCallPhillip ? "/call Phillip" : "/tptoplot barn";
                String label = cfg.fortuneBonusCallPhillip ? "Call Phillip" : "Go to Barn";
                line = line.copy().append(Component.literal(" \u00a7e[" + label + "]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Run " + command)))));
            }
            mc.player.sendSystemMessage(line);
        }
        if (mc.player != null && cfg.fortuneBonusExpireTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(0, Math.clamp(cfg.fortuneTitleTicks, 10, 300), 10);
            mc.gui.hud.setTitle(Component.literal(text).withColor(0xFF5555));
        }
        if (mc.player != null && cfg.fortuneBonusExpireSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, .6f);
        save();
    }

    public static boolean hudVisible() {
        if (!activeGarden() || !cfg.fortuneDisplay) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return !cfg.fortuneRequireTool || HerculesGardenTracker.cropInHand(mc.player.getMainHandItem()) != null;
    }

    public static List<FortuneRow> hudRows() {
        if (!hudVisible()) return List.of();
        Minecraft mc = Minecraft.getInstance();
        HerculesGardenTracker.Crop held = mc.player == null ? null : HerculesGardenTracker.cropInHand(mc.player.getMainHandItem());
        HerculesGardenTracker.Crop displayCrop = held != null ? held : tabCrop;
        if (displayCrop == null) return List.of();
        boolean current = held == null || held == tabCrop;
        Double saved = cfg.fortuneLatestByCrop.get(displayCrop.name());
        double total = current && foundUniversal && foundCrop ? universal + cropFortune : saved == null ? -1 : saved;
        List<FortuneRow> rows = new ArrayList<>();
        String label = cfg.fortuneCompact ? "FF" : cfg.fortuneShowCrop ? displayCrop.display() : "Fortune";
        rows.add(new FortuneRow(label, total < 0 ? "Unknown" : format(total), pestReduction() > 0 ? 0xFFFF5555 : 0xFFFFFF55));
        if (cfg.fortuneShowBreakdown && current) {
            rows.add(new FortuneRow("Universal", format(universal), 0xFF55FFFF));
            rows.add(new FortuneRow("Crop", format(cropFortune), 0xFF55FF55));
        }
        if (!current && !cfg.fortuneHideMissingWarnings) rows.add(new FortuneRow("Update", "Break " + displayCrop.display(), 0xFFFF5555));
        if (cfg.fortuneShowPestReduction && pestReduction() > 0) rows.add(new FortuneRow("Pests", "-" + pestReduction() + "%", 0xFFFF5555));
        if (cfg.fortuneShowPestBonus) {
            long left = cfg.fortunePestBonusExpiry - System.currentTimeMillis();
            rows.add(new FortuneRow("Bonus", left > 0 ? "+" + cfg.fortunePestBonus + " (" + time(left) + ")" : "Inactive", left > 0 ? 0xFF55FFFF : 0xFFFF5555));
        }
        return rows;
    }

    private static int pestReduction() {
        var state = HerculesPests.state();
        int alive = state == null ? pestAlive : state.alive();
        int effective = Math.max(0, alive - (int)Math.floor(bonusPestChance / 100.0));
        return switch (effective) { case 0,1,2,3 -> 0; case 4 -> 5; case 5 -> 15; case 6 -> 30; case 7 -> 50; default -> 75; };
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fortune")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{cfg.fortuneLatestByCrop.clear();universal=0;cropFortune=0;foundUniversal=false;foundCrop=false;save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("missing").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("universal",IntegerArgumentType.integer(1,120)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("crop",IntegerArgumentType.integer(1,120)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("repeat",IntegerArgumentType.integer(5,600)).executes(c->{cfg.fortuneUniversalMissingSeconds=IntegerArgumentType.getInteger(c,"universal");cfg.fortuneCropMissingSeconds=IntegerArgumentType.getInteger(c,"crop");cfg.fortuneMissingRepeatSeconds=IntegerArgumentType.getInteger(c,"repeat");save();return status();})))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("titleticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("ticks",IntegerArgumentType.integer(10,300)).executes(c->{cfg.fortuneTitleTicks=IntegerArgumentType.getInteger(c,"ticks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(StringArgumentType.getString(c,"text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int status(){local("Display "+on(cfg.fortuneDisplay)+", universal "+(foundUniversal?format(universal):"missing")+", crop "+(foundCrop&&tabCrop!=null?tabCrop.display()+" "+format(cropFortune):"missing")+".");return 1;}
    private static int template(String raw){String value=raw.replace('\n',' ').replace('\r',' ').trim();if(value.isEmpty()||value.length()>160){local("Template must contain 1-160 characters.");return 0;}cfg.fortuneBonusExpireTemplate=value;save();return status();}
    private static int option(String name,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.fortuneHelper=value;case"display"->cfg.fortuneDisplay=value;case"compact"->cfg.fortuneCompact=value;case"hidewarnings"->cfg.fortuneHideMissingWarnings=value;case"bonus"->cfg.fortuneShowPestBonus=value;case"bonuschat"->cfg.fortuneBonusExpireChat=value;case"bonustitle"->cfg.fortuneBonusExpireTitle=value;case"bonussound"->cfg.fortuneBonusExpireSound=value;case"bonusaction"->cfg.fortuneBonusClickableAction=value;case"callphillip"->cfg.fortuneBonusCallPhillip=value;case"crop"->cfg.fortuneShowCrop=value;case"breakdown"->cfg.fortuneShowBreakdown=value;case"reduction"->cfg.fortuneShowPestReduction=value;case"tool"->cfg.fortuneRequireTool=value;case"missingchat"->cfg.fortuneMissingChat=value;case"widget"->cfg.fortuneMissingClickableWidget=value;default->{local("Unknown fortune option.");return 0;}}save();return status();}

    private static HerculesGardenTracker.Crop crop(String name){for(var crop:HerculesGardenTracker.Crop.values())if(crop.display().equalsIgnoreCase(name))return crop;return null;}
    private static long duration(String raw){long seconds=0;Matcher matcher=Pattern.compile("(\\d+)m").matcher(raw);if(matcher.find())seconds+=Long.parseLong(matcher.group(1))*60;matcher=Pattern.compile("(\\d+)s").matcher(raw);if(matcher.find())seconds+=Long.parseLong(matcher.group(1));return seconds*1000L;}
    private static String time(long millis){long seconds=Math.max(0,millis/1000);return seconds>=60?String.format(Locale.ROOT,"%dm %02ds",seconds/60,seconds%60):seconds+"s";}
    private static double number(String value){try{return Double.parseDouble(value.replace(",",""));}catch(Exception ignored){return 0;}}
    private static String format(double value){return String.format(Locale.ROOT,"%,.0f",value);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static boolean activeGarden(){return cfg!=null&&cfg.enabled&&cfg.fortuneHelper&&ConstellationClient.loc().area()== LocationManager.SkyblockArea.GARDEN;}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void maps(){if(cfg.fortuneLatestByCrop==null)cfg.fortuneLatestByCrop=new HashMap<>();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a72[Farming Fortune] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
