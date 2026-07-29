package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// timer ported from SkyHanni (LGPL-3.0-or-later): features/garden/greenhouse/GrowthCycle.kt
// menu detection ported from SkyHanni (LGPL-3.0-or-later): features/garden/greenhouse/GreenhouseUtils.kt
// slot highlights ported from SkyHanni (LGPL-3.0-or-later): features/garden/greenhouse/HarvestableHighlight.kt
public final class HerculesGreenhouse {
    public record HudRow(String label, String value, int color) {}
    private static final Pattern NEXT_STAGE = Pattern.compile("^Next Stage:\\s*(?<time>(?:\\d{1,2}[hms]\\s*)+)$", Pattern.CASE_INSENSITIVE);
    private static HerculesConfig cfg;
    private static boolean joined;

    private HerculesGreenhouse() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        ConstellationClient.tick().every(20, "hercules-greenhouse", HerculesGreenhouse::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c) -> joined = true);
        ClientPlayConnectionEvents.DISCONNECT.register((a,b) -> joined = true);
    }

    private static void tick() {
        if (!scope()) return;
        Minecraft mc = Minecraft.getInstance();
        if (activeGarden() && mc.gui.screen() instanceof AbstractContainerScreen<?> screen && diagnostic(screen) && screen.getMenu().slots.size() > 20) {
            readCycle(screen.getMenu().getSlot(20).getItem());
        }
        notifyReady();
        joined = false;
    }

    private static void readCycle(ItemStack stack) {
        for (String line : lore(stack)) {
            Matcher matcher = NEXT_STAGE.matcher(line);
            if (!matcher.matches()) continue;
            long duration = duration(matcher.group("time"));
            if (duration <= 0) return;
            long expected = System.currentTimeMillis() + duration;
            if (Math.abs(expected - cfg.greenhouseNextCycle) < 1500) return;
            cfg.greenhouseNextCycle = expected;
            cfg.greenhouseCycleNotified = false;
            ConstellationClient.saveConfig();
            return;
        }
    }

    private static void notifyReady() {
        if (!cfg.greenhouseGrowth || !cfg.greenhouseReadyNotification || cfg.greenhouseNextCycle <= 0 || cfg.greenhouseCycleNotified) return;
        long left = cfg.greenhouseNextCycle - System.currentTimeMillis();
        if (left < -Math.max(1, cfg.greenhouseForgetAfterMinutes) * 60_000L) {
            cfg.greenhouseCycleNotified = true;
            ConstellationClient.saveConfig();
            return;
        }
        if (left > Math.max(0, cfg.greenhouseWarningSeconds) * 1000L) return;
        if (joined && left <= 0 && !cfg.greenhouseNotifyAfterAway) {
            cfg.greenhouseCycleNotified = true;
            ConstellationClient.saveConfig();
            return;
        }
        boolean hasAwayVariable = cfg.greenhouseReadyTemplate.contains("{away}");
        String text = cfg.greenhouseReadyTemplate
            .replace("{time}", left <= 0 ? "ready" : time(left))
            .replace("{away}", joined && left <= 0 ? "While you were away, " : "");
        if (joined && left <= 0 && !hasAwayVariable) text = "While you were away, " + text;
        Minecraft mc = Minecraft.getInstance();
        if (cfg.greenhouseReadyChat) local(text);
        if (mc.player != null && cfg.greenhouseReadyTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(0, Math.clamp(cfg.greenhouseTitleTicks, 10, 300), 10);
            mc.gui.hud.setTitle(Component.literal(text).withColor(0x55FF55));
        }
        if (mc.player != null && cfg.greenhouseReadySound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, 1f);
        cfg.greenhouseCycleNotified = true;
        ConstellationClient.saveConfig();
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (!activeGarden() || !diagnostic(screen) || slot == null || slot.getItem().isEmpty()
            || mc.player == null || slot.container == mc.player.getInventory()) return;
        if (slot.index == 24 && cfg.greenhouseHighlightHarvestable && slot.getItem().is(Items.BEACON)) {
            int color = cfg.greenhouseNotReadyColor;
            for (String line : lore(slot.getItem())) {
                if (line.equals("Status: Harvestable")) color = cfg.greenhouseHarvestableColor;
                if (line.startsWith("Drops: ") || line.startsWith("Rewards: ")) color = cfg.greenhouseRewardColor;
            }
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
            border(graphics, slot, color | 0xFF000000);
        }
        if (slot.index == 21 && cfg.greenhouseHighlightWater && slot.getItem().is(Items.WATER_BUCKET)) {
            boolean enough = lore(slot.getItem()).stream().anyMatch(line -> line.equals("This crop has enough water to") || line.equals("This crop doesn't need water!"));
            if (enough) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.greenhouseWaterColor);
                border(graphics, slot, cfg.greenhouseWaterColor | 0xFF000000);
            }
        }
    }

    public static boolean hudVisible() {
        if (!scope() || !cfg.greenhouseGrowth || cfg.greenhouseNextCycle <= 0) return false;
        if (!cfg.greenhouseShowOutsideGarden && !activeGarden()) return false;
        long left = cfg.greenhouseNextCycle - System.currentTimeMillis();
        if (left < -Math.max(1, cfg.greenhouseForgetAfterMinutes) * 60_000L) return false;
        return !cfg.greenhouseOnlyWhenReady || left <= 0;
    }

    public static HudRow hudRow() {
        if (!hudVisible()) return null;
        long left = cfg.greenhouseNextCycle - System.currentTimeMillis();
        String label = cfg.greenhouseShowStageLabel ? "Next stage" : "Growth";
        if (left <= 0) return new HudRow(label, "Overdue", 0xFFFF5555);
        int color = left <= 60_000 ? 0xFFFF5555 : left <= 300_000 ? 0xFFFFFF55 : 0xFF55FF55;
        return new HudRow(label, time(left), color);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("greenhouse")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> { cfg.greenhouseNextCycle=0;cfg.greenhouseCycleNotified=true;save();return status(); }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,3600)).executes(c->{cfg.greenhouseWarningSeconds=IntegerArgumentType.getInteger(c,"seconds");cfg.greenhouseCycleNotified=false;save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("forget").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,1440)).executes(c->{cfg.greenhouseForgetAfterMinutes=IntegerArgumentType.getInteger(c,"minutes");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("titleticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("ticks",IntegerArgumentType.integer(10,300)).executes(c->{cfg.greenhouseTitleTicks=IntegerArgumentType.getInteger(c,"ticks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(StringArgumentType.getString(c,"text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int status(){long left=cfg.greenhouseNextCycle-System.currentTimeMillis();local("Growth timer "+on(cfg.greenhouseGrowth)+", "+(cfg.greenhouseNextCycle<=0?"unknown.":left<=0?"ready.":time(left)+" remaining."));return 1;}
    private static int template(String raw){String value=raw.replace('\n',' ').replace('\r',' ').trim();if(value.isEmpty()||value.length()>160){local("Template must contain 1-160 characters.");return 0;}cfg.greenhouseReadyTemplate=value;save();return status();}
    private static int color(String target,String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw.startsWith("0x")?raw.substring(2):raw;long parsed=Long.parseUnsignedLong(value,16);int color=value.length()<=6?(int)(0xA0000000L|parsed):(int)parsed;switch(target.toLowerCase(Locale.ROOT)){case"ready"->cfg.greenhouseHarvestableColor=color;case"notready"->cfg.greenhouseNotReadyColor=color;case"reward"->cfg.greenhouseRewardColor=color;case"water"->cfg.greenhouseWaterColor=color;default->{local("Color target must be ready, notready, reward or water.");return 0;}}save();return status();}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB.");return 0;}}
    private static int option(String name,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.greenhouseHelper=value;case"display"->cfg.greenhouseGrowth=value;case"onlyready"->cfg.greenhouseOnlyWhenReady=value;case"harvest"->cfg.greenhouseHighlightHarvestable=value;case"water"->cfg.greenhouseHighlightWater=value;case"notify"->cfg.greenhouseReadyNotification=value;case"chat"->cfg.greenhouseReadyChat=value;case"title"->cfg.greenhouseReadyTitle=value;case"sound"->cfg.greenhouseReadySound=value;case"outside"->cfg.greenhouseShowOutsideGarden=value;case"away"->cfg.greenhouseNotifyAfterAway=value;case"label"->cfg.greenhouseShowStageLabel=value;default->{local("Unknown greenhouse option.");return 0;}}save();return status();}

    private static long duration(String raw){long seconds=0;Matcher matcher=Pattern.compile("(\\d+)h").matcher(raw);if(matcher.find())seconds+=number(matcher.group(1))*3600L;matcher=Pattern.compile("(\\d+)m").matcher(raw);if(matcher.find())seconds+=number(matcher.group(1))*60L;matcher=Pattern.compile("(\\d+)s").matcher(raw);if(matcher.find())seconds+=number(matcher.group(1));return seconds*1000L;}
    private static String time(long millis){long seconds=Math.max(0,millis/1000);if(seconds>=3600)return String.format(Locale.ROOT,"%dh %02dm",seconds/3600,seconds/60%60);if(seconds>=60)return String.format(Locale.ROOT,"%dm %02ds",seconds/60,seconds%60);return seconds+"s";}
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static boolean diagnostic(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString()).equals("Crop Diagnostics");}
    private static void border(GuiGraphicsExtractor graphics,Slot slot,int color){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.fill(slot.x,slot.y,slot.x+1,slot.y+16,color);graphics.fill(slot.x+15,slot.y,slot.x+16,slot.y+16,color);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static boolean scope(){return cfg!=null&&cfg.enabled&&cfg.greenhouseHelper&&ConstellationClient.loc().onHypixel();}
    private static boolean activeGarden(){return scope()&&ConstellationClient.loc().area()== LocationManager.SkyblockArea.GARDEN;}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a72[Greenhouse] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
