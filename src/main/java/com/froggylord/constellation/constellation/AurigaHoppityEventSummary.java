package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityEggsManager.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/summary/HoppityEventSummary.kt
public final class AurigaHoppityEventSummary {
    public record Row(String label, String value, int color) {}

    private static final Pattern MEAL = Pattern.compile("^HOPPITY'S HUNT You found a Chocolate ([\\p{L}]+) Egg(?: .*)?!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HITMAN = Pattern.compile("^HOPPITY'S HUNT You found a Hitman Egg!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RABBIT = Pattern.compile("^HOPPITY'S HUNT You found (.+) \\((COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)\\)!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEW = Pattern.compile("^NEW RABBIT!.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUPLICATE = Pattern.compile("^DUPLICATE RABBIT! \\+([\\d,]+) Chocolate$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOUGHT = Pattern.compile("^You bought .+?(?: for [\\d,]+ Coins)?!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VISITOR = Pattern.compile("^\\[NPC] Hoppity: Simply exquisite.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FISH = Pattern.compile("^HOPPITY'S HUNT You found Rabbit the Fish!$", Pattern.CASE_INSENSITIVE);
    private static final String[] RARITIES = {"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","DIVINE"};
    private static final Map<String,Integer> RARITY_COLORS = Map.of(
        "COMMON",0xFFFFFFFF,"UNCOMMON",0xFF55FF55,"RARE",0xFF5555FF,"EPIC",0xFFAA00AA,
        "LEGENDARY",0xFFFFAA00,"MYTHIC",0xFFFF55FF,"DIVINE",0xFF55FFFF);

    private static AurigaConfig cfg;
    private static String pendingRarity;
    private static String lastSignature = "";
    private static long lastSignatureAt;
    private static long factoryTickAt;
    private static long lastFactorySave;
    private static String lastClick = "";
    private static long lastClickAt;
    private static boolean lastSpring;
    private static int selectedYear;

    private AurigaHoppityEventSummary() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        normalize();
        selectedYear = currentYear();
        lastSpring = isSpring();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(message.getString());
            return true;
        });
        ConstellationClient.tick().every(1, "auriga-hoppity-event-summary", AurigaHoppityEventSummary::tick);
    }

    private static void onChat(String formatted) {
        if (!active()) return;
        String text = clean(formatted);
        long now = System.currentTimeMillis();
        String signature = currentYear() + "|" + text;
        if (signature.equals(lastSignature) && now - lastSignatureAt < 1_000) return;
        lastSignature = signature;
        lastSignatureAt = now;

        Matcher matcher = MEAL.matcher(text);
        if (matcher.matches()) { add("meal." + meal(matcher.group(1)), 1); return; }
        if (HITMAN.matcher(text).matches()) { add("hitman", 1); return; }
        matcher = RABBIT.matcher(text);
        if (matcher.matches()) { pendingRarity = matcher.group(2).toUpperCase(Locale.ROOT); if (matcher.group(1).equalsIgnoreCase("Rabbit the Fish")) add("fish",1); return; }
        if (NEW.matcher(text).matches() && pendingRarity != null) { add("unique." + pendingRarity, 1); pendingRarity = null; return; }
        matcher = DUPLICATE.matcher(text);
        if (matcher.matches() && pendingRarity != null) {
            add("duplicate." + pendingRarity, 1);
            add("duplicateChocolate", number(matcher.group(1)));
            pendingRarity = null;
            return;
        }
        if (BOUGHT.matcher(text).matches()) { add("bought", 1); return; }
        if (VISITOR.matcher(text).matches()) { add("visitor", 1); return; }
        if (FISH.matcher(text).matches()) add("fish", 1);
    }

    private static void tick() {
        if (!active()) return;
        boolean spring = isSpring();
        if (lastSpring && !spring && cfg.hoppityEventSummaryChatOnEventEnd) {
            int ended = currentYear();
            if (!cfg.hoppityEventSummarized.getOrDefault(scope(ended), false) && total(ended) > 0) {
                sendSummary(ended);
                cfg.hoppityEventSummarized.put(scope(ended), true);
                save();
            }
        }
        lastSpring = spring;
        Minecraft mc = Minecraft.getInstance();
        boolean factory = mc.gui.screen() instanceof AbstractContainerScreen<?> container
            && clean(container.getTitle().getString()).equals("Chocolate Factory");
        long now = System.currentTimeMillis();
        if (!factory) { factoryTickAt = 0; return; }
        if (factoryTickAt > 0 && spring) addUnsaved("factoryMillis", Math.min(2_000, now - factoryTickAt));
        factoryTickAt = now;
        if (now - lastFactorySave >= 60_000) { lastFactorySave = now; save(); }
    }

    // ported from SkyHanni (LGPL-3.0-or-later): features/event/hoppity/HoppityApi.kt onSlotClick
    public static void onSlotClick(AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || screen == null || slot == null || slot.getItem().isEmpty()) return;
        String inventory = clean(screen.getTitle().getString());
        if (!inventory.matches("^Chocolate (?:Factory|Shop Milestones|Factory Milestones)$")) return;
        String name = clean(slot.getItem().getHoverName().getString());
        List<String> lore = lore(slot);
        long now = System.currentTimeMillis();
        String signature = System.identityHashCode(screen) + "|" + slot.index + "|" + name;
        if (signature.equals(lastClick) && now - lastClickAt < 750) return;
        if (name.matches("^Golden Rabbit - Side Dish$")) {
            lastClick=signature;lastClickAt=now;add("sideDish",1);return;
        }
        if (!name.matches("^\\d{1,2}[a-z]{2} Chocolate Milestone$")
            || lore.stream().noneMatch(line -> line.equals("Click to claim!"))) return;
        lastClick=signature;lastClickAt=now;
        if (lore.stream().anyMatch(line -> line.matches("^Reach [\\d.MBk]+ Chocolate all-time.*"))) add("milestoneFactory",1);
        else if (lore.stream().anyMatch(line -> line.matches("^Spend [\\d.MBk]+ Chocolate in.*"))) add("milestoneShop",1);
    }

    public static List<Row> rows() {
        int year = selectedYear <= 0 ? currentYear() : selectedYear;
        ArrayList<Row> out = new ArrayList<>();
        if (cfg.hoppityEventSummaryShowMeals) out.add(new Row("Meal eggs", String.valueOf(meals(year)), 0xFF55FFFF));
        if (cfg.hoppityEventSummaryShowRabbits) {
            out.add(new Row("Unique rabbits", String.valueOf(sum(year,"unique.")), 0xFF55FF55));
            out.add(new Row("Duplicates", String.valueOf(sum(year,"duplicate.")), 0xFFAAAAAA));
        }
        if (cfg.hoppityEventSummaryShowSources) {
            out.add(new Row("Hitman eggs", String.valueOf(get(year,"hitman")), 0xFFFF5555));
            out.add(new Row("Bought", String.valueOf(get(year,"bought")), 0xFF55FF55));
            out.add(new Row("Garden gifts", String.valueOf(get(year,"visitor")), 0xFFFF55FF));
            out.add(new Row("Side Dish", String.valueOf(get(year,"sideDish")), 0xFFFFAA00));
            out.add(new Row("Milestones", String.valueOf(get(year,"milestoneFactory")+get(year,"milestoneShop")), 0xFFFFAA00));
        }
        if (cfg.hoppityEventSummaryShowChocolate) out.add(new Row("Dupe chocolate", compact(get(year,"duplicateChocolate")), 0xFFFFAA00));
        if (cfg.hoppityEventSummaryShowFactoryTime) out.add(new Row("Factory time", duration(get(year,"factoryMillis")), 0xFFFFAA00));
        if (cfg.hoppityEventSummaryShowRarities) for (String rarity : RARITIES) {
            long value=get(year,"unique."+rarity)+get(year,"duplicate."+rarity);
            if(value>0)out.add(new Row(title(rarity),String.valueOf(value),RARITY_COLORS.get(rarity)));
        }
        int limit=Math.clamp(cfg.hoppityEventSummaryHudRows,1,20);
        return List.copyOf(out.subList(0,Math.min(limit,out.size())));
    }

    public static boolean visible() {
        return active() && (!cfg.hoppityEventSummaryOnlyDuringEvent || isSpring()) && total(selectedYear) > 0;
    }
    public static int selectedYear() { return selectedYear; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hoppitysummary")
            .executes(context -> summary(currentYear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("show").executes(context -> summary(selectedYear)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("current").executes(context -> select(currentYear())))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("year")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("year",IntegerArgumentType.integer(1))
                    .executes(context -> select(IntegerArgumentType.getInteger(context,"year")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,20))
                    .executes(context -> rows(IntegerArgumentType.getInteger(context,"count")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("confirm").executes(context -> clear())))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context,"name"),StringArgumentType.getString(context,"state")))))));
    }

    private static int summary(int year) { sendSummary(year); return 1; }
    private static void sendSummary(int year) {
        local("Hoppity's Hunt #" + (year - 345) + " (Year " + year + ")");
        for(Row row:allRows(year))local("§7"+row.label()+": §f"+row.value());
    }
    private static List<Row> allRows(int year) {
        int old=selectedYear,oldLimit=cfg.hoppityEventSummaryHudRows;selectedYear=year;cfg.hoppityEventSummaryHudRows=20;
        List<Row> value=rows();selectedYear=old;cfg.hoppityEventSummaryHudRows=oldLimit;return value;
    }
    private static int select(int year) { selectedYear=year;local("Selected SkyBlock Year "+year+".");return summary(year); }
    private static int rows(int value) { cfg.hoppityEventSummaryHudRows=value;save();local("HUD rows set to "+value+".");return 1; }
    private static int clear() {
        String prefix=scope(selectedYear)+"|";cfg.hoppityEventStats.keySet().removeIf(key->key.startsWith(prefix));
        cfg.hoppityEventSummarized.remove(scope(selectedYear));save();local("Selected year statistics cleared.");return 1;
    }
    private static int option(String name,String raw) {
        Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.hoppityEventSummary=value;case"hud"->cfg.hoppityEventSummaryHud=value;
            case"eventonly"->cfg.hoppityEventSummaryOnlyDuringEvent=value;case"meals"->cfg.hoppityEventSummaryShowMeals=value;
            case"rabbits"->cfg.hoppityEventSummaryShowRabbits=value;case"sources"->cfg.hoppityEventSummaryShowSources=value;
            case"chocolate"->cfg.hoppityEventSummaryShowChocolate=value;case"time"->cfg.hoppityEventSummaryShowFactoryTime=value;
            case"rarities"->cfg.hoppityEventSummaryShowRarities=value;case"persist"->cfg.hoppityEventSummaryPersistProfiles=value;
            case"endsummary"->cfg.hoppityEventSummaryChatOnEventEnd=value;default->{local("Unknown Hoppity summary option.");return 0;}
        }save();return summary(selectedYear);
    }

    private static void add(String stat,long amount){addUnsaved(stat,amount);save();}
    private static void addUnsaved(String stat,long amount){if(amount<=0)return;cfg.hoppityEventStats.merge(key(currentYear(),stat),amount,Long::sum);}
    private static long get(int year,String stat){return cfg.hoppityEventStats.getOrDefault(key(year,stat),0L);}
    private static long sum(int year,String prefix){String key=scope(year)+"|"+prefix;return cfg.hoppityEventStats.entrySet().stream().filter(e->e.getKey().startsWith(key)).mapToLong(Map.Entry::getValue).sum();}
    private static long meals(int year){return sum(year,"meal.");}
    private static long total(int year){String prefix=scope(year)+"|";return cfg.hoppityEventStats.entrySet().stream().filter(e->e.getKey().startsWith(prefix)&&!e.getKey().endsWith("|factoryMillis")&&!e.getKey().endsWith("|duplicateChocolate")).mapToLong(Map.Entry::getValue).sum();}
    private static String key(int year,String stat){return scope(year)+"|"+stat;}
    private static String scope(int year){return profile()+"|"+year;}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static int currentYear(){final long epoch=1559829300000L,yearMs=124L*60*60*1000;return(int)(Math.max(0,System.currentTimeMillis()-epoch)/yearMs)+1;}
    private static boolean isSpring(){final long epoch=1559829300000L,yearMs=124L*60*60*1000;long within=Math.floorMod(System.currentTimeMillis()-epoch,yearMs);return within<yearMs/4;}
    private static String meal(String value){String clean=value.toUpperCase(Locale.ROOT).replace("É","E");return clean.equals("DEJEUNE")||clean.equals("DEJEUNER")?"DEJEUNER":clean;}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.replaceAll("\\s+"," ").strip();}
    private static List<String> lore(Slot slot){ItemLore value=slot.getItem().get(DataComponents.LORE);return value==null?List.of():value.lines().stream().map(line->clean(line.getString())).toList();}
    private static long number(String value){try{return Long.parseLong(value.replace(",",""));}catch(Exception ignored){return 0;}}
    private static String compact(long value){if(value<1_000)return String.valueOf(value);if(value<1_000_000)return String.format(Locale.ROOT,"%.1fk",value/1_000.0);if(value<1_000_000_000)return String.format(Locale.ROOT,"%.2fM",value/1_000_000.0);return String.format(Locale.ROOT,"%.2fB",value/1_000_000_000.0);}
    private static String duration(long millis){long seconds=millis/1000,hours=seconds/3600,minutes=seconds%3600/60;return hours>0?hours+"h "+minutes+"m":minutes+"m";}
    private static String title(String value){return value.charAt(0)+value.substring(1).toLowerCase(Locale.ROOT);}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.hoppityEventSummary&&ConstellationClient.loc().onHypixel();}
    private static void normalize(){if(cfg.hoppityEventStats==null)cfg.hoppityEventStats=new LinkedHashMap<>();if(cfg.hoppityEventSummarized==null)cfg.hoppityEventSummarized=new LinkedHashMap<>();cfg.hoppityEventSummaryHudRows=Math.clamp(cfg.hoppityEventSummaryHudRows,1,20);}
    private static void save(){if(cfg.hoppityEventSummaryPersistProfiles)ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7d[Hoppity Summary] \u00a7f"+text));}
}
