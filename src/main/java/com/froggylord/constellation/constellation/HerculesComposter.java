package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.ui.ConstellationUi;
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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/composter/ComposterApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/composter/ComposterDisplay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/composter/ComposterInventoryNumbers.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/composter/ComposterOverlay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/composter/GardenComposterInventoryFeatures.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/garden/ComposterUpgradesData.kt
public final class HerculesComposter {
    public record Row(String label, String value, int color) {}
    private record State(long organic, long fuel, long stored, long nextMillis, boolean inactive) {}
    private record Material(String id, String name, double factor, boolean fuel) {}

    private static final Pattern VALUE = Pattern.compile("(?i)^\\s*(Organic Matter|Fuel|Time Left|Stored Compost):\\s*(.+)$");
    private static final Pattern BAR = Pattern.compile(".*?([0-9][0-9,.]*)(?:\\s*/\\s*([0-9][0-9,.]*[kKmM]?))?.*");
    private static final Pattern UPGRADE = Pattern.compile("(?i)^(Composter Speed|Multi Drop|Fuel Cap|Organic Matter Cap|Cost Reduction)(?:\\s+([IVXLCDM]+|\\d+))?$");
    private static final List<Material> MATERIALS = List.of(
        new Material("ENCHANTED_SEEDS", "Enchanted Seeds", 160, false),
        new Material("ENCHANTED_BREAD", "Enchanted Bread", 60, false),
        new Material("ENCHANTED_PUMPKIN", "Enchanted Pumpkin", 160, false),
        new Material("ENCHANTED_MELON", "Enchanted Melon", 32, false),
        new Material("ENCHANTED_CARROT", "Enchanted Carrot", 46.4, false),
        new Material("ENCHANTED_POTATO", "Enchanted Potato", 52.8, false),
        new Material("ENCHANTED_SUGAR", "Enchanted Sugar", 80, false),
        new Material("ENCHANTED_NETHER_STALK", "Enchanted Nether Wart", 52.8, false),
        new Material("ENCHANTED_CACTUS_GREEN", "Enchanted Cactus Green", 80, false),
        new Material("ENCHANTED_COCOA", "Enchanted Cocoa", 64, false),
        new Material("CROPIE", "Cropie", 2500, false),
        new Material("SQUASH", "Squash", 10000, false),
        new Material("FERMENTO", "Fermento", 20000, false),
        new Material("HELIANTHUS", "Helianthus", 30000, false),
        new Material("FLOWERING_BOUQUET", "Flowering Bouquet", 6000, false),
        new Material("BIOFUEL", "Biofuel", 3000, true),
        new Material("VOLTA", "Volta", 10000, true),
        new Material("OIL_BARREL", "Oil Barrel", 10000, true),
        new Material("SUNFLOWER_OIL", "Sunflower Oil", 20000, true)
    );

    private static HerculesConfig cfg;
    private static State state;
    private static long lastLowMatter;
    private static long lastLowFuel;
    private static long lastAlmostEmpty;

    private HerculesComposter() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        maps();
        restore();
        ConstellationClient.tick().every(20, "hercules-composter", HerculesComposter::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c) -> { state = null; restore(); });
        ClientPlayConnectionEvents.DISCONNECT.register((a,b) -> state = null);
    }

    private static void tick() {
        if (!active()) return;
        if (state == null) restore();
        if (inGarden()) {
            State found = readTab();
            if (found != null) update(found);
            readOpenMenu();
        }
        notifyLow();
        warnEmpty();
    }

    private static State readTab() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        long organic = -1, fuel = -1, stored = -1, next = -1;
        boolean inactive = false, inside = false;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            String line = clean(display == null ? info.getProfile().name() : display.getString());
            if (line.equalsIgnoreCase("Composter:")) { inside = true; continue; }
            if (!inside) continue;
            if (line.isBlank() || line.endsWith(":") && !line.toLowerCase(Locale.ROOT).startsWith("time left")) break;
            Matcher matcher = VALUE.matcher(line);
            if (!matcher.matches()) continue;
            String value = matcher.group(2).trim();
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "organic matter" -> organic = number(value);
                case "fuel" -> fuel = number(value);
                case "stored compost" -> stored = number(value);
                case "time left" -> {
                    inactive = value.equalsIgnoreCase("INACTIVE");
                    next = inactive ? 0 : duration(value);
                }
            }
        }
        return organic < 0 && fuel < 0 && stored < 0 ? null : new State(Math.max(0, organic), Math.max(0, fuel), Math.max(0, stored), Math.max(0, next), inactive);
    }

    private static void update(State found) {
        State old = state;
        state = found;
        long empty = estimateEmpty(found);
        long emptyAt = empty > 0 ? System.currentTimeMillis() + empty : cfg.composterEmptyAt;
        boolean changed = old == null || old.organic != found.organic || old.fuel != found.fuel
            || old.stored != found.stored || old.inactive != found.inactive || Math.abs(cfg.composterEmptyAt - emptyAt) > 5000;
        cfg.composterEmptyAt = emptyAt;
        if (changed) {
            cfg.composterStates.put(profile(), found.organic + "," + found.fuel + "," + found.stored + "," + found.nextMillis + "," + found.inactive + "," + cfg.composterEmptyAt);
            ConstellationClient.saveConfig();
        }
    }

    private static long estimateEmpty(State data) {
        if (data.inactive || data.nextMillis <= 0) return 0;
        double perTime = timePerCompost();
        double fraction = Math.min(1, data.nextMillis / perTime);
        double organicPer = organicPer(), fuelPer = fuelPer();
        long organicCycles = Math.max(0, (long)Math.floor((data.organic - fraction * organicPer) / organicPer));
        long fuelCycles = Math.max(0, (long)Math.floor((data.fuel - fraction * fuelPer) / fuelPer));
        return data.nextMillis + Math.min(organicCycles, fuelCycles) * (long)perTime;
    }

    private static void readOpenMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        if (title(screen).equals("Composter Upgrades")) {
            boolean changed = false;
            for (Slot slot : screen.getMenu().slots) {
                if (slot.getItem().isEmpty()) continue;
                Matcher matcher = UPGRADE.matcher(clean(slot.getItem().getHoverName().getString()));
                if (matcher.matches()) {
                    String key = profile() + "|" + upgradeKey(matcher.group(1));
                    int level = roman(matcher.group(2));
                    if (!Objects.equals(cfg.composterUpgrades.put(key, level), level)) changed = true;
                }
            }
            if (changed) ConstellationClient.saveConfig();
        }
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot) {
        if (!active() || !inGarden() || slot == null) return;
        String title = title(screen);
        if (!slot.getItem().isEmpty() && title.equals("Composter Upgrades") && cfg.composterHighlightUpgrade && lore(slot.getItem()).stream().anyMatch(line -> line.equals("Click to upgrade!"))) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x70FFAA00);
            border(graphics, slot, 0xFFFFAA00);
        }
        if (!slot.getItem().isEmpty() && title.equals("Composter") && cfg.composterInventoryNumbers) drawNumber(graphics, slot);
        if (title.equals("Composter") && cfg.composterOverlay && slot.index == 0) drawOverlay(graphics, screen);
    }

    private static void drawNumber(GuiGraphicsExtractor graphics, Slot slot) {
        if (slot.index != 13 && slot.index != 46 && slot.index != 52) return;
        String value = "";
        for (String line : lore(slot.getItem())) {
            Matcher matcher = BAR.matcher(line);
            if (matcher.matches()) {
                value = matcher.group(2) == null ? shortNumber(number(matcher.group(1))) : shortNumber(number(matcher.group(1))) + "/" + matcher.group(2);
                break;
            }
        }
        if (value.isBlank()) return;
        int color = slot.index == 46 ? 0xFFFFFF55 : slot.index == 52 ? 0xFF55FF55 : 0xFFFFAA00;
        graphics.text(Minecraft.getInstance().font, value, slot.x + 1, slot.y + 1, color, true);
    }

    private static void drawOverlay(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen) {
        Material organic = best(false), fuel = best(true);
        List<Row> rows = overlayRows(organic, fuel);
        int height = 18 + rows.size() * 11;
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int screenLeft = accessor.constellation$left(), imageWidth = accessor.constellation$imageWidth();
        int leftSpace = Math.max(0, screenLeft - 4), rightSpace = Math.max(0, screen.width - screenLeft - imageWidth - 4);
        boolean useRight = rightSpace >= leftSpace;
        int width = Math.min(154, useRight ? rightSpace : leftSpace);
        if (width < 56) return;
        int x = useRight ? imageWidth + 4 : -width - 4;
        int y = Math.clamp(0, 2 - accessor.constellation$top(),
            Math.max(2 - accessor.constellation$top(), screen.height - accessor.constellation$top() - height - 2));
        graphics.fill(x, y, x + width, y + height, cfg.composterOverlayColor);
        var font = Minecraft.getInstance().font;
        graphics.text(font, ConstellationUi.fit(font, "Composter", width - 12), x + 6, y + 5, 0xFF55FFFF, true);
        int line = y + 17;
        for (Row row : rows) {
            graphics.text(font, ConstellationUi.fit(font, row.label + ": " + row.value, width - 12), x + 6, line, row.color, false);
            line += 11;
        }
    }

    private static List<Row> overlayRows(Material organic, Material fuel) {
        if (state == null) return List.of(new Row("Status", "Loading", 0xFFFFAA00));
        List<Row> rows = new ArrayList<>();
        long missingOrganic = Math.max(0, maxOrganic() - state.organic);
        long missingFuel = Math.max(0, maxFuel() - state.fuel);
        rows.add(new Row("Organic Matter", format(state.organic) + "/" + format(maxOrganic()), 0xFFFFFF55));
        if (organic != null) rows.add(materialRow(organic, missingOrganic));
        rows.add(new Row("Fuel", format(state.fuel) + "/" + format(maxFuel()), 0xFF55FF55));
        if (fuel != null) rows.add(materialRow(fuel, missingFuel));
        if (cfg.composterShowStored) rows.add(new Row("Stored Compost", format(state.stored), 0xFF55FFFF));
        if (cfg.composterShowEmptyTime) rows.add(new Row("Empty in", emptyText(), 0xFF55FFFF));
        if (cfg.composterShowProfit && organic != null && fuel != null) rows.add(new Row("Profit/compost", coins(profit(organic, fuel)), profit(organic, fuel) >= 0 ? 0xFF55FF55 : 0xFFFF5555));
        return rows;
    }

    private static Row materialRow(Material material, long missing) {
        long count = amount(missing, material.factor);
        double cost = count * price(material);
        Integer sacks = cfg.composterShowSackCounts ? HerculesVisitorHelper.observedSackCount(material.id) : null;
        String suffix = sacks == null ? "" : " S:" + shortNumber(sacks);
        return new Row(material.name, format(count) + " (" + coins(cost) + ")" + suffix, material.fuel ? 0xFF55FF55 : 0xFFFFFF55);
    }

    public static List<Row> hudRows() {
        if (!hudVisible()) return List.of();
        List<Row> rows = new ArrayList<>();
        if (state != null && inGarden()) {
            rows.add(new Row("Organic Matter", format(state.organic), state.organic <= cfg.composterLowOrganicMatter ? 0xFFFF5555 : 0xFFFFFF55));
            rows.add(new Row("Fuel", format(state.fuel), state.fuel <= cfg.composterLowFuel ? 0xFFFF5555 : 0xFF55FF55));
            if (cfg.composterShowStored) rows.add(new Row("Stored", format(state.stored), 0xFF55FFFF));
        }
        rows.add(new Row("Empty in", emptyText(), cfg.composterEmptyAt > 0 && cfg.composterEmptyAt <= System.currentTimeMillis() ? 0xFFFF5555 : 0xFF55FFFF));
        return rows;
    }

    public static boolean hudVisible() {
        if (!active()) return false;
        if (inGarden()) return cfg.composterDisplay && (state != null || cfg.composterEmptyAt > 0);
        return cfg.composterDisplayOutsideGarden && cfg.composterEmptyAt > 0;
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> input) {
        if (!active() || !inGarden() || !cfg.composterUpgradePrice || !title(screen).equals("Composter Upgrades") || stack == null || stack.isEmpty()) return input;
        List<Component> out = new ArrayList<>(input);
        double total = 0;
        boolean costs = false;
        for (String line : lore(stack)) {
            if (line.equals("Upgrade Cost:")) { costs = true; continue; }
            if (!costs) continue;
            if (line.isBlank()) break;
            if (line.endsWith(" Copper")) continue;
            ParsedCost parsed = cost(line);
            if (parsed == null) continue;
            double price = PriceProvider.purchaseValue(parsed.id) * parsed.amount;
            if (price <= 0) PriceProvider.warm(parsed.id);
            else total += price;
        }
        if (total > 0) out.add(Component.literal("Market cost: " + coins(total)).withColor(0xFFAA00));
        return out;
    }

    private record ParsedCost(String id, int amount) {}
    private static ParsedCost cost(String raw) {
        Matcher trailing = Pattern.compile("^(.+?)\\s+x?([0-9][0-9,]*)$").matcher(raw.trim());
        Matcher leading = Pattern.compile("^x?([0-9][0-9,]*)\\s+(.+)$").matcher(raw.trim());
        String name;
        String rawAmount;
        if (trailing.matches()) { name = trailing.group(1).trim(); rawAmount = trailing.group(2); }
        else if (leading.matches()) { name = leading.group(2).trim(); rawAmount = leading.group(1); }
        else return null;
        int amount = (int)Math.min(Integer.MAX_VALUE, number(rawAmount));
        String id = name.toUpperCase(Locale.ROOT).replace("'", "").replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        id = switch (id) { case "ENCHANTED_NETHER_WART" -> "ENCHANTED_NETHER_STALK"; case "ENCHANTED_COCOA_BEANS" -> "ENCHANTED_COCOA"; default -> id; };
        return amount <= 0 ? null : new ParsedCost(id, amount);
    }

    private static void notifyLow() {
        if (!cfg.composterNotifyLow || state == null || !inGarden()) return;
        long now = System.currentTimeMillis(), cooldown = Math.max(1, cfg.composterReminderCooldownMinutes) * 60_000L;
        if (state.organic <= cfg.composterLowOrganicMatter && now - lastLowMatter >= cooldown) {
            lastLowMatter = now; alert("Your Organic Matter is low.");
        }
        if (state.fuel <= cfg.composterLowFuel && now - lastLowFuel >= cooldown) {
            lastLowFuel = now; alert("Your Composter Fuel is low.");
        }
    }

    private static void warnEmpty() {
        if (!cfg.composterWarnAlmostEmpty || cfg.composterEmptyAt <= 0) return;
        long left = cfg.composterEmptyAt - System.currentTimeMillis();
        if (left > Math.max(1, cfg.composterAlmostEmptyMinutes) * 60_000L || System.currentTimeMillis() - lastAlmostEmpty < 120_000) return;
        lastAlmostEmpty = System.currentTimeMillis();
        alert(left <= 0 ? "Your Composter is empty." : "Your Composter is almost empty.");
    }

    private static void alert(String text) {
        local(text);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (cfg.composterNotifyLowTitle || cfg.composterWarningTitle && text.contains("empty"))) {
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal(text).withColor(0xFF5555));
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), .9f, 1f);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("composter")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c -> clear()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lowmatter").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(0,1_000_000)).executes(c->{cfg.composterLowOrganicMatter=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lowfuel").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(0,1_000_000)).executes(c->{cfg.composterLowFuel=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int option(String name,String stateName) {
        Boolean value = bool(stateName); if (value == null) { local("Use on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.composterHelper=value; case "overlay" -> cfg.composterOverlay=value;
            case "display" -> cfg.composterDisplay=value; case "outside" -> cfg.composterDisplayOutsideGarden=value;
            case "warning" -> cfg.composterWarnAlmostEmpty=value; case "low" -> cfg.composterNotifyLow=value;
            case "title" -> cfg.composterNotifyLowTitle=value; case "warningtitle" -> cfg.composterWarningTitle=value;
            case "prices" -> cfg.composterUpgradePrice=value;
            case "rounddown" -> cfg.composterRoundDown=value; case "highlight" -> cfg.composterHighlightUpgrade=value;
            case "numbers" -> cfg.composterInventoryNumbers=value; case "sacks" -> cfg.composterShowSackCounts=value;
            case "profit" -> cfg.composterShowProfit=value; default -> { local("Unknown Composter option."); return 0; }
        }
        save(); return status();
    }

    private static int status() { local("Composter helper " + on(cfg.composterHelper) + ", overlay " + on(cfg.composterOverlay) + ", display " + on(cfg.composterDisplay) + ". " + (state == null ? "No current Garden data." : "Organic " + format(state.organic) + ", fuel " + format(state.fuel) + ", empty in " + emptyText() + ".")); return 1; }
    private static int clear() { cfg.composterEmptyAt=0;cfg.composterStates.remove(profile());state=null;save();return status(); }
    private static Material best(boolean fuel) { return MATERIALS.stream().filter(m->m.fuel==fuel&&m.factor>=Math.max(0,cfg.composterMinimumOrganicMatter)||m.fuel==fuel&&fuel).filter(m->price(m)>0).min(Comparator.comparingDouble(m->price(m)/m.factor)).orElse(null); }
    private static double price(Material material) { double value=PriceProvider.purchaseValue(material.id);if(material.id.equals("BIOFUEL")&&value>20000)value=20000;return value; }
    private static double profit(Material organic,Material fuel){double compost=PriceProvider.sellValue("COMPOST");return compost*(1+level("multi_drop")*.03)-price(organic)*(organicPer()/organic.factor)-price(fuel)*(fuelPer()/fuel.factor);}
    private static long amount(long missing,double factor){return cfg.composterRoundDown?(long)Math.floor(missing/factor):(long)Math.ceil(missing/factor);}
    private static int maxOrganic(){return 40000+level("organic_matter_cap")*30000;}
    private static int maxFuel(){return 100000+level("fuel_cap")*30000;}
    private static double timePerCompost(){return 600000.0/(1+level("composter_speed")*.2);}
    private static double organicPer(){return 4000*(1-level("cost_reduction")/100.0);}
    private static double fuelPer(){return 2000*(1-level("cost_reduction")/100.0);}
    private static int level(String key){return cfg.composterUpgrades.getOrDefault(profile()+"|"+key,0);}
    private static String upgradeKey(String value){return value.toLowerCase(Locale.ROOT).replace(' ','_');}
    private static void restore(){String raw=cfg.composterStates.get(profile());if(raw==null)return;try{String[] p=raw.split(",");state=new State(Long.parseLong(p[0]),Long.parseLong(p[1]),Long.parseLong(p[2]),Long.parseLong(p[3]),Boolean.parseBoolean(p[4]));cfg.composterEmptyAt=Long.parseLong(p[5]);}catch(Exception ignored){state=null;}}
    private static String emptyText(){if(cfg.composterEmptyAt<=0)return state!=null&&state.inactive?"Inactive":"Unknown";long left=cfg.composterEmptyAt-System.currentTimeMillis();return left<=0?"Empty":time(left);}
    private static String time(long millis){long s=Math.max(0,millis/1000);if(s>=86400)return s/86400+"d "+s/3600%24+"h";if(s>=3600)return s/3600+"h "+s/60%60+"m";if(s>=60)return s/60+"m "+s%60+"s";return s+"s";}
    private static long duration(String raw){long total=0;Matcher m=Pattern.compile("(?i)([0-9]+)\\s*([dhms])").matcher(raw);while(m.find()){long n=number(m.group(1));total+=switch(m.group(2).toLowerCase(Locale.ROOT)){case"d"->n*86400000L;case"h"->n*3600000L;case"m"->n*60000L;default->n*1000L;};}return total;}
    private static long number(String raw){try{String value=raw.replace(",","").trim();double multiplier=1;if(value.toLowerCase(Locale.ROOT).endsWith("k")){multiplier=1000;value=value.substring(0,value.length()-1);}else if(value.toLowerCase(Locale.ROOT).endsWith("m")){multiplier=1_000_000;value=value.substring(0,value.length()-1);}return Math.round(Double.parseDouble(value)*multiplier);}catch(Exception ignored){return 0;}}
    private static int roman(String raw){if(raw==null||raw.isBlank())return 0;try{return Integer.parseInt(raw);}catch(Exception ignored){}int total=0,last=0;for(int i=raw.length()-1;i>=0;i--){int now=switch(Character.toUpperCase(raw.charAt(i))){case'I'->1;case'V'->5;case'X'->10;case'L'->50;case'C'->100;case'D'->500;case'M'->1000;default->0;};total+=now<last?-now:now;last=Math.max(last,now);}return total;}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(c->clean(c.getString())).toList();}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static String clean(String value){String out=ChatFormatting.stripFormatting(value);return out==null?"":out.trim();}
    private static String profile(){String p=LyraStorageValue.currentProfileKey();return p==null||p.isBlank()?"unknown":p.toLowerCase(Locale.ROOT);}
    private static String format(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String shortNumber(long value){if(value>=1_000_000)return String.format(Locale.ROOT,"%.1fm",value/1_000_000.0);if(value>=1000)return String.format(Locale.ROOT,"%.1fk",value/1000.0);return Long.toString(value);}
    private static String coins(double value){if(!Double.isFinite(value))return "?";return (value<0?"-":"")+shortNumber(Math.round(Math.abs(value)))+" coins";}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.composterHelper&&ConstellationClient.loc().onHypixel();}
    private static boolean inGarden(){return ConstellationClient.loc().area()==LocationManager.SkyblockArea.GARDEN;}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void border(GuiGraphicsExtractor g,Slot s,int c){g.fill(s.x,s.y,s.x+16,s.y+1,c);g.fill(s.x,s.y+15,s.x+16,s.y+16,c);g.fill(s.x,s.y,s.x+1,s.y+16,c);g.fill(s.x+15,s.y,s.x+16,s.y+16,c);}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Composter] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
    private static void maps(){if(cfg.composterStates==null)cfg.composterStates=new HashMap<>();if(cfg.composterUpgrades==null)cfg.composterUpgrades=new HashMap<>();}
}
