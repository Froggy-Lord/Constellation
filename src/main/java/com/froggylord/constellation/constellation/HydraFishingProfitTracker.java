package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HydraConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/tracker/FishingProfitTracker.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/jsonobjects/repo/FishingProfitItemsJson.kt
public final class HydraFishingProfitTracker {
    public record ItemRow(String id,String name,long amount,double value,boolean complete,String category,long lastGainAt) {}
    public record Recent(String name,double value,boolean complete,long at) {}
    public record Stats(List<ItemRow> items,double profit,boolean complete,double perHour,int catches,long activeMillis,Recent recent) {}
    private record Allowed(String id,Set<String> categories) {}
    private static final class Gain {String name;long amount,lastGainAt;Gain(String name,long amount,long at){this.name=name;this.amount=amount;this.lastGainAt=at;}}
    private static final Pattern COINS=Pattern.compile("(?:GOOD|GREAT) CATCH!\\s+You caught\\s+([\\d,]+) Coins!");
    private static final Set<String> PETS=Set.of("GUARDIAN","FLYING_FISH","SQUID","MEGALODON","BABY_YETI");
    private static final String[] PET_RARITY={"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY"};
    private static final Map<String,Allowed> ALLOWED=new HashMap<>();
    private static final Map<String,Map<String,Integer>> FILLETS=new HashMap<>();
    private static final Map<String,Gain> GAINS=new LinkedHashMap<>();
    private static final Map<String,Integer> SNAPSHOT=new HashMap<>();
    private static HydraConfig cfg;
    private static boolean initialized,hadBobber,snapshotReady;
    private static long lastSignal,lastPickup,lastTick,lastActivity,activeMillis,lastSeenCreatureCatch,lastRareAlertAt;
    private static String lastRareAlertId="";
    private static int catches;
    private static Recent recent;
    private static String profileKey="";
    private HydraFishingProfitTracker() {}

    public static void init(HydraConfig config){cfg=config;loadData();if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-fishing-profit",HydraFishingProfitTracker::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void tick(){
        long now=System.currentTimeMillis();Minecraft mc=Minecraft.getInstance();String current=LyraStorageValue.currentProfileKey();if(!current.equals(profileKey)){profileKey=current;resetSession();}
        if(!active()||mc.player==null){hadBobber=false;snapshotReady=false;lastTick=0;lastSeenCreatureCatch=HydraSeaCreatureTracker.lastCatchAt();return;}
        boolean bobber=mc.player.fishing!=null;long creatureCatch=HydraSeaCreatureTracker.lastCatchAt();if(creatureCatch>lastSeenCreatureCatch){lastSeenCreatureCatch=creatureCatch;signalCatch(creatureCatch);}
        if(hadBobber&&!bobber)signalCatch(now);hadBobber=bobber;
        if(lastTick>0&&(bobber||now-lastActivity<=Math.max(5,cfg.fishingProfitAfkSeconds)*1000L))activeMillis+=Math.min(100,now-lastTick);lastTick=now;if(bobber)lastActivity=now;
        Map<String,InventoryItem> inventory=inventory(mc);if(!snapshotReady||mc.gui.screen() instanceof AbstractContainerScreen<?>){baseline(inventory);snapshotReady=true;return;}
        long window=Math.clamp(cfg.fishingProfitPickupWindowSeconds,1,10)*1000L;if(now-lastSignal<=window)for(var entry:inventory.entrySet()){int before=SNAPSHOT.getOrDefault(entry.getKey(),0),delta=entry.getValue().amount-before;if(delta>0)add(entry.getKey(),entry.getValue().name,delta,now);}
        baseline(inventory);
    }

    private static void signalCatch(long now){if(now-lastSignal>1250)catches++;lastSignal=Math.max(lastSignal,now);lastActivity=now;}

    private static void onChat(String message){if(!active())return;Matcher matcher=COINS.matcher(message);if(!matcher.find())return;long amount;try{amount=Long.parseLong(matcher.group(1).replace(",",""));}catch(NumberFormatException ignored){return;}signalCatch(System.currentTimeMillis());add("SKYBLOCK_COIN","Fished Coins",amount,System.currentTimeMillis());}

    private static void add(String id,String name,long amount,long now){Allowed allowed=ALLOWED.get(id);if(allowed==null||amount<=0)return;Gain gain=GAINS.get(id);if(gain==null)GAINS.put(id,new Gain(name,amount,now));else{gain.amount+=amount;gain.lastGainAt=now;if(name!=null&&!name.isBlank())gain.name=name;}Value value=value(id,amount);recent=new Recent(name,value.value,value.complete,now);lastPickup=now;lastActivity=now;warn(id,name,value.value,value.complete);}

    private static void warn(String id,String name,double value,boolean complete){if(!complete||System.currentTimeMillis()-lastRareAlertAt<=5000&&id.equalsIgnoreCase(lastRareAlertId))return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;double chat=Math.max(0,cfg.fishingProfitMinimumChatMillions)*1_000_000.0,title=Math.max(0,cfg.fishingProfitMinimumTitleMillions)*1_000_000.0;if(cfg.fishingProfitChatWarning&&value>=chat)local("Caught "+name+" worth "+coins(value,true)+".");if(cfg.fishingProfitTitleWarning&&value>=title){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name+"  "+coins(value,true)).withColor(cfg.fishingProfitRecentColor&0xFFFFFF));}}
    public static void markRareDropAlert(String id){lastRareAlertId=id==null?"":id;lastRareAlertAt=System.currentTimeMillis();}

    private record InventoryItem(String name,int amount) {}
    private static Map<String,InventoryItem> inventory(Minecraft mc){Map<String,InventoryItem> out=new HashMap<>();for(ItemStack stack:mc.player.getInventory()){if(stack==null||stack.isEmpty())continue;String id=itemId(stack);if(!ALLOWED.containsKey(id))continue;String name=clean(stack.getHoverName().getString());InventoryItem old=out.get(id);out.put(id,new InventoryItem(name,(old==null?0:old.amount)+stack.getCount()));}return out;}
    private static void baseline(Map<String,InventoryItem> inventory){SNAPSHOT.clear();for(var entry:inventory.entrySet())SNAPSHOT.put(entry.getKey(),entry.getValue().amount);}

    public static Stats stats(){if(!active())return null;String category=cfg.fishingProfitCategory.toUpperCase(Locale.ROOT);List<ItemRow> rows=new ArrayList<>();double profit=0;boolean complete=true;for(var entry:GAINS.entrySet()){Allowed allowed=ALLOWED.get(entry.getKey());if(allowed==null||!category.equals("ALL")&&!allowed.categories.contains(category))continue;Gain gain=entry.getValue();Value value=value(entry.getKey(),gain.amount);profit+=value.value;complete&=value.complete;rows.add(new ItemRow(entry.getKey(),gain.name,gain.amount,value.value,value.complete,String.join(",",allowed.categories),gain.lastGainAt));}Comparator<ItemRow> comparator=switch(cfg.fishingProfitSorting.toUpperCase(Locale.ROOT)){case"VALUE_ASC"->Comparator.comparingDouble(ItemRow::value);case"AMOUNT_DESC"->Comparator.comparingLong(ItemRow::amount).reversed();case"AMOUNT_ASC"->Comparator.comparingLong(ItemRow::amount);case"NAME"->Comparator.comparing(ItemRow::name,String.CASE_INSENSITIVE_ORDER);case"RECENT"->Comparator.comparingLong(ItemRow::lastGainAt).reversed();default->Comparator.comparingDouble(ItemRow::value).reversed();};rows.sort(comparator);int top=Math.clamp(cfg.fishingProfitTop,1,50);if(rows.size()>top)rows=new ArrayList<>(rows.subList(0,top));Recent shown=recent!=null&&System.currentTimeMillis()-recent.at<=Math.max(1,cfg.fishingProfitRecentSeconds)*1000L?recent:null;return new Stats(List.copyOf(rows),profit,complete,activeMillis<=0?0:profit*3_600_000.0/activeMillis,catches,activeMillis,shown);}

    private record Value(double value,boolean complete) {}
    private static Value value(String id,long amount){if(id.equals("SKYBLOCK_COIN"))return new Value(amount,true);if(cfg.fishingProfitTrophyFilletValue&&isTrophy(id)){int fillets=fillets(id);double price=price("MAGMA_FISH");if(fillets<=0||price<=0){PriceProvider.warm("MAGMA_FISH");return new Value(0,false);}return new Value(price*fillets*amount,true);}double price=price(id);if(price<=0){PriceProvider.warm(id);return new Value(0,false);}return new Value(price*amount,true);}
    private static double price(String id){return cfg.fishingProfitPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);}
    private static boolean isTrophy(String id){return id.endsWith("_BRONZE")||id.endsWith("_SILVER")||id.endsWith("_GOLD")||id.endsWith("_DIAMOND");}
    private static int fillets(String id){int cut=id.lastIndexOf('_');if(cut<0)return 0;String rarity=id.substring(cut+1).toLowerCase(Locale.ROOT),fish=id.substring(0,cut).replace("_","").toLowerCase(Locale.ROOT);return FILLETS.getOrDefault(fish,Map.of()).getOrDefault(rarity,0);}

    public static boolean visible(){if(!active()||GAINS.isEmpty())return false;Minecraft mc=Minecraft.getInstance();return mc.player!=null&&(mc.player.fishing!=null||cfg.fishingProfitShowWhenPickup&&System.currentTimeMillis()-lastPickup<=Math.max(1,cfg.fishingProfitRecentSeconds)*1000L);}
    public static HydraConfig config(){return cfg;}
    public static String coins(double value,boolean complete){String amount=Math.abs(value)>=1_000_000?String.format(Locale.ROOT,"%.2fm",value/1_000_000):Math.abs(value)>=1_000?String.format(Locale.ROOT,"%.1fk",value/1_000):String.format(Locale.ROOT,"%.0f",value);return amount+(complete?"":" partial");}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.fishingProfitTracker&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()!=com.froggylord.constellation.core.LocationManager.SkyblockArea.KUUDRA;}

    private static String itemId(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);CompoundTag extra=data==null?new CompoundTag():data.copyTag().getCompoundOrEmpty("ExtraAttributes");String raw=extra.getStringOr("id","");if(raw.equals("ATTRIBUTE_SHARD")){CompoundTag attributes=extra.getCompoundOrEmpty("attributes");if(attributes.keySet().size()==1){String key=attributes.keySet().iterator().next();return "ATTRIBUTE_SHARD_"+key.toUpperCase(Locale.ROOT)+";"+attributes.getIntOr(key,1);}}return LyraTooltips.marketId(stack).toUpperCase(Locale.ROOT).replace(':','-');}

    private static void loadData(){ALLOWED.clear();FILLETS.clear();try(var stream=HydraFishingProfitTracker.class.getResourceAsStream("/assets/constellation/fishing/fishingProfitItems.json")){if(stream==null)throw new IllegalStateException("missing fishing profit items");JsonObject categories=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("categories");for(var category:categories.entrySet())for(JsonElement element:category.getValue().getAsJsonArray()){String source=element.getAsString().toUpperCase(Locale.ROOT),id=marketId(source),name=category.getKey().toUpperCase(Locale.ROOT).replace(' ','_');Allowed old=ALLOWED.get(id);var names=new java.util.LinkedHashSet<String>();if(old!=null)names.addAll(old.categories);names.add(name);ALLOWED.put(id,new Allowed(id,Set.copyOf(names)));}}catch(Exception e){ConstellationClient.LOGGER.error("Could not load fishing profit item categories",e);}try(var stream=HydraFishingProfitTracker.class.getResourceAsStream("/assets/constellation/fishing/trophyFish.json")){if(stream==null)throw new IllegalStateException("missing trophy fish data");JsonObject fish=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("trophy_fish");for(var entry:fish.entrySet()){Map<String,Integer> values=new HashMap<>();for(var fillet:entry.getValue().getAsJsonObject().getAsJsonObject("fillet").entrySet())values.put(fillet.getKey().toLowerCase(Locale.ROOT),fillet.getValue().getAsInt());FILLETS.put(entry.getKey().toLowerCase(Locale.ROOT),Map.copyOf(values));}}catch(Exception e){ConstellationClient.LOGGER.error("Could not load trophy fish fillet data",e);}}
    private static String marketId(String source){int semi=source.lastIndexOf(';');if(semi<0)return source.replace(':','-');String base=source.substring(0,semi),level=source.substring(semi+1);if(base.startsWith("ATTRIBUTE_SHARD_"))return source;if(PETS.contains(base)){int rarity;try{rarity=Integer.parseInt(level);}catch(NumberFormatException ignored){return source;}return rarity>=0&&rarity<PET_RARITY.length?base+"_PET_"+PET_RARITY[rarity]:source;}if(base.endsWith("_RUNE"))return base.substring(0,base.length()-5)+"_"+level+"_RUNE";return "ENCHANTMENT_"+base+"_"+level;}

    private static void reset(){profileKey="";resetSession();}
    private static void resetSession(){GAINS.clear();SNAPSHOT.clear();hadBobber=false;snapshotReady=false;lastSignal=0;lastPickup=0;lastTick=0;lastActivity=0;activeMillis=0;lastSeenCreatureCatch=HydraSeaCreatureTracker.lastCatchAt();lastRareAlertAt=0;lastRareAlertId="";catches=0;recent=null;}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fishingprofit").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Fishing profit session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("top").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("rows",IntegerArgumentType.integer(1,50)).executes(c->{cfg.fishingProfitTop=IntegerArgumentType.getInteger(c,"rows");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("window").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,10)).executes(c->{cfg.fishingProfitPickupWindowSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("category").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).executes(c->category(StringArgumentType.getString(c,"name"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceSource(StringArgumentType.getString(c,"source"))))));}
    private static int status(){Stats stats=stats();local("Tracked "+(stats==null?0:stats.catches)+" catches for "+coins(stats==null?0:stats.profit,stats==null||stats.complete)+" using "+cfg.fishingProfitPriceSource.toLowerCase(Locale.ROOT)+" prices.");return 1;}
    private static int category(String name){String value=name.toUpperCase(Locale.ROOT).replace(' ','_');if(!value.equals("ALL")&&ALLOWED.values().stream().noneMatch(a->a.categories.contains(value))){local("Unknown category. Use /fishingprofit category all or a category from the HUD configuration.");return 0;}cfg.fishingProfitCategory=value;save();return status();}
    private static int sort(String mode){String value=mode.toUpperCase(Locale.ROOT);if(!List.of("VALUE_DESC","VALUE_ASC","AMOUNT_DESC","AMOUNT_ASC","NAME","RECENT").contains(value)){local("Sort must be value_desc, value_asc, amount_desc, amount_asc, name, or recent.");return 0;}cfg.fishingProfitSorting=value;save();return status();}
    private static int priceSource(String source){String value=source.toUpperCase(Locale.ROOT);if(!value.equals("PURCHASE")&&!value.equals("SELL")){local("Price source must be purchase or sell.");return 0;}cfg.fishingProfitPriceSource=value;save();return status();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Fishing] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
