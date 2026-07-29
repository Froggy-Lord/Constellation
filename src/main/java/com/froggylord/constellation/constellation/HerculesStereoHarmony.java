package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.hud.ThemedHudWidget;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// active vinyl and display ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/stereo/StereoHarmonyDisplay.kt
// vinyl data ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/stereo/VinylType.kt
// pest/crop data ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestType.kt
// menu helper ported from Skyblocker (LGPL-3.0-or-later): skyblock/garden/StereoHarmonyHelper.java
// crop mapping ported from Skyblocker (LGPL-3.0-or-later): skyblock/garden/GardenConstants.java
public final class HerculesStereoHarmony {
    public record DisplayRow(String label, String value) {}
    private record Vinyl(String name, String pest, String crop, Item cropItem) {}

    private static final Pattern NOW_PLAYING = Pattern.compile("^Now Playing:\\s*(?<vinyl>.+?)(?:\\s*\\([^)]*\\))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEST = Pattern.compile("^When playing,\\s*(?<pest>.+?)\\s+Pests?.*$", Pattern.CASE_INSENSITIVE);
    private static final List<Vinyl> VINYLS = List.of(
        new Vinyl("Pretty Fly","Fly","Wheat",Items.WHEAT),
        new Vinyl("Buzzin' Beats","Mosquito","Sugar Cane",Items.SUGAR_CANE),
        new Vinyl("Cricket Choir","Cricket","Carrot",Items.CARROT),
        new Vinyl("Cicada Symphony","Locust","Potato",Items.POTATO),
        new Vinyl("Earthworm Ensemble","Earthworm","Melon Slice",Items.MELON_SLICE),
        new Vinyl("Rodent Revolution","Rat","Pumpkin",Items.PUMPKIN),
        new Vinyl("Wings of Harmony","Moth","Cocoa Beans",Items.COCOA_BEANS),
        new Vinyl("Not Just a Pest","Beetle","Nether Wart",Items.NETHER_WART),
        new Vinyl("DynaMITES","Mite","Cactus",Items.CACTUS),
        new Vinyl("Slow and Groovy","Slug","Mushroom",Items.RED_MUSHROOM),
        new Vinyl("Firefly in the Hole","Firefly","Moonflower",Items.BLUE_ORCHID),
        new Vinyl("Imagine Dragonflies","Dragonfly","Sunflower",Items.SUNFLOWER),
        new Vinyl("Pray For Me","Praying Mantis","Wild Rose",Items.ROSE_BUSH)
    );

    private static HerculesConfig cfg;
    private static String activeVinyl = "";
    private static boolean observed;

    private HerculesStereoHarmony() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        activeVinyl = config.stereoActiveVinyl == null ? "" : config.stereoActiveVinyl;
        ConstellationClient.tick().every(10, "hercules-stereo-harmony", HerculesStereoHarmony::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c) -> observed = false);
        ClientPlayConnectionEvents.DISCONNECT.register((a,b) -> observed = false);
    }

    private static void tick() {
        if (!scope()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen && title(screen).equals("Stereo Harmony") && screen.getMenu().slots.size() > 4) {
            readPlaying(screen.getMenu().getSlot(4).getItem());
            return;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            String id = LyraTooltips.marketId(stack);
            if (id != null && (id.contains("VACUUM") || id.contains("LASSO"))) readPlaying(stack);
        }
    }

    private static void readPlaying(ItemStack stack) {
        for (String line : lore(stack)) {
            Matcher matcher = NOW_PLAYING.matcher(line);
            if (!matcher.matches()) continue;
            String raw = matcher.group("vinyl").replaceAll("\\s+", " ").trim();
            Vinyl vinyl = vinyl(raw);
            change(vinyl == null || raw.equalsIgnoreCase("None") ? "" : vinyl.name);
            return;
        }
    }

    private static void change(String next) {
        if (Objects.equals(activeVinyl, next)) { observed = true; return; }
        String old = activeVinyl;
        activeVinyl = next;
        if (cfg.stereoPersistSelection) {
            cfg.stereoActiveVinyl = next;
            ConstellationClient.saveConfig();
        }
        if (observed && cfg.stereoChangeNotification) notifyChange();
        observed = true;
    }

    private static void notifyChange() {
        Vinyl vinyl = active();
        String text = cfg.stereoChangeTemplate
            .replace("{vinyl}", vinyl == null ? "Nothing" : vinyl.name)
            .replace("{pest}", vinyl == null ? "None" : vinyl.pest)
            .replace("{crop}", vinyl == null ? "None" : vinyl.crop);
        Minecraft mc = Minecraft.getInstance();
        if (cfg.stereoChangeChat) local(text);
        if (mc.player != null && cfg.stereoChangeTitle) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTimes(0, Math.clamp(cfg.stereoTitleTicks, 10, 300), 10);
            mc.gui.hud.setTitle(Component.literal(text).withColor(0x55FFFF));
        }
        if (mc.player != null && cfg.stereoChangeSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .8f, 1.35f);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!scope() || slot == null || slot.getItem().isEmpty() || !title(screen).equals("Stereo Harmony")) return;
        Vinyl vinyl = vinylFromItem(slot.getItem());
        if (vinyl == null) return;
        boolean playing = lore(slot.getItem()).stream().anyMatch(line -> line.toUpperCase(Locale.ROOT).contains("PLAYING"));
        if (cfg.stereoReplaceMenuIcons) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF8B8B8B);
            graphics.item(new ItemStack(vinyl.cropItem), slot.x, slot.y);
        }
        var contest = HerculesGardenTracker.contest();
        boolean match = cfg.stereoContestHelper && contest != null && cropMatches(contest.crop(), vinyl.crop);
        if (match || playing) {
            int color = playing ? cfg.stereoPlayingColor : cfg.stereoMatchingColor;
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
            border(graphics, slot, color | 0xFF000000);
        }
    }

    public static boolean hudVisible() {
        if (!activeGarden() || !cfg.stereoDisplay) return false;
        if (!cfg.stereoAlwaysShow && HerculesGardenTracker.rates() == null) return false;
        return !cfg.stereoHideWhenNone || active() != null;
    }

    public static List<DisplayRow> hudRows() {
        if (!hudVisible()) return List.of();
        Vinyl vinyl = active();
        List<DisplayRow> rows = new ArrayList<>();
        rows.add(new DisplayRow("Playing", vinyl == null ? "Nothing" : vinyl.name));
        if (vinyl != null && cfg.stereoShowPest) rows.add(new DisplayRow("Pest", vinyl.pest));
        if (vinyl != null && cfg.stereoShowCrop) rows.add(new DisplayRow("Crop", vinyl.crop));
        return rows;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stereoharmony")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> { change(""); return status(); }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("titleticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("ticks", IntegerArgumentType.integer(10,300)).executes(c -> { cfg.stereoTitleTicks = IntegerArgumentType.getInteger(c,"ticks"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text", StringArgumentType.greedyString()).executes(c -> template(StringArgumentType.getString(c,"text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb", StringArgumentType.word()).executes(c -> color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state", StringArgumentType.word()).executes(c -> option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int status() {
        Vinyl vinyl = active();
        local("Helper " + on(cfg.stereoHarmony) + ", playing " + (vinyl == null ? "nothing." : vinyl.name + " for " + vinyl.pest + "."));
        return 1;
    }
    private static int template(String raw) { String value = raw.replace('\n',' ').replace('\r',' ').trim(); if(value.isEmpty()||value.length()>160){local("Template must contain 1-160 characters.");return 0;}cfg.stereoChangeTemplate=value;save();return status();}
    private static int color(String target,String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw.startsWith("0x")?raw.substring(2):raw;long parsed=Long.parseUnsignedLong(value,16);int color=value.length()<=6?(int)(0xA0000000L|parsed):(int)parsed;if(target.equalsIgnoreCase("match"))cfg.stereoMatchingColor=color;else if(target.equalsIgnoreCase("playing"))cfg.stereoPlayingColor=color;else{local("Color target must be match or playing.");return 0;}save();return status();}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB.");return 0;}}
    private static int option(String name,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.stereoHarmony=value;case"display"->cfg.stereoDisplay=value;case"always"->cfg.stereoAlwaysShow=value;case"pest"->cfg.stereoShowPest=value;case"crop"->cfg.stereoShowCrop=value;case"hidenone"->cfg.stereoHideWhenNone=value;case"icons"->cfg.stereoReplaceMenuIcons=value;case"contest"->cfg.stereoContestHelper=value;case"notify"->cfg.stereoChangeNotification=value;case"chat"->cfg.stereoChangeChat=value;case"title"->cfg.stereoChangeTitle=value;case"sound"->cfg.stereoChangeSound=value;case"persist"->cfg.stereoPersistSelection=value;default->{local("Unknown Stereo Harmony option.");return 0;}}save();return status();}

    private static Vinyl vinylFromItem(ItemStack stack) { for(String line:lore(stack)){Matcher matcher=PEST.matcher(line);if(matcher.matches())for(Vinyl vinyl:VINYLS)if(vinyl.pest.equalsIgnoreCase(matcher.group("pest").trim()))return vinyl;}return null; }
    private static Vinyl vinyl(String name) { for(Vinyl vinyl:VINYLS)if(vinyl.name.equalsIgnoreCase(name.trim()))return vinyl;return null; }
    private static Vinyl active() { return vinyl(activeVinyl); }
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static boolean cropMatches(String one,String two){return one.replace(" Slice","").replace(" Beans","").equalsIgnoreCase(two.replace(" Slice","").replace(" Beans",""));}
    private static void border(GuiGraphicsExtractor graphics,Slot slot,int color){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.fill(slot.x,slot.y,slot.x+1,slot.y+16,color);graphics.fill(slot.x+15,slot.y,slot.x+16,slot.y+16,color);}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static boolean scope(){return cfg!=null&&cfg.enabled&&cfg.stereoHarmony&&ConstellationClient.loc().onHypixel();}
    private static boolean activeGarden(){return scope()&&ConstellationClient.loc().area()== LocationManager.SkyblockArea.GARDEN;}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a72[Stereo Harmony] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
