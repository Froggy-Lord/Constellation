package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/ForagingTracker.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/ForagingTrackerLegacy.kt
public final class ArtemisForagingTracker {
    public record Row(String id,String name,long amount,double value,boolean complete,long lastAt){}
    public record Recent(String name,long amount,double value,boolean complete,long at){}
    public record Stats(List<Row> rows,long trees,double wholeTrees,long foragingXp,long hotfXp,long whispers,
                        double profit,boolean complete,double perHour,long uptime,Recent recent){}
    private enum Tree { FIG("Fig"),MANGROVE("Mangrove");final String display;Tree(String display){this.display=display;}static Tree from(String raw){for(Tree tree:values())if(tree.display.equalsIgnoreCase(raw))return tree;return null;}}
    private static final class Gain{String name;long amount,lastAt;Gain(String name,long amount,long lastAt){this.name=name;this.amount=amount;this.lastAt=lastAt;}}
    private static final Pattern CONTRIBUTION=Pattern.compile("^You helped cut ([\\d.]+)% of the (Fig|Mangrove) Tree\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern REWARDS=Pattern.compile("^\\+([\\d,]+) rewards gained!(?: \\(hover\\))?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern HOVER_REWARD=Pattern.compile("^(.+?)\\s+x?(?:0-)?([\\d,]+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern HOVER_PERCENT=Pattern.compile("^(.+?)\\s+\\(([\\d.]+)%\\)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern BONUS=Pattern.compile("^(.+?) \\(([\\d.]+)%\\)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PHANTOM=Pattern.compile("^A (.+?) fell from the Tree!$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,String> IDS=Map.ofEntries(
        Map.entry("Forest Essence","ESSENCE_FOREST"),Map.entry("Tender Wood","TENDER_WOOD"),
        Map.entry("Vinesap","VINESAP"),Map.entry("Stretching Sticks","STRETCHING_STICKS"),
        Map.entry("Sweep Booster","SWEEP_BOOSTER"),Map.entry("Foraging Wisdom Booster","FORAGING_WISDOM_BOOSTER"),
        Map.entry("Signal Enhancer","SIGNAL_ENHANCER"),Map.entry("Tree the Fish","TREE_THE_FISH"),
        Map.entry("Fig Log","FIG_LOG"),Map.entry("Enchanted Fig Log","ENCHANTED_FIG_LOG"),
        Map.entry("Mangrove Log","MANGROVE_LOG"),Map.entry("Enchanted Mangrove Log","ENCHANTED_MANGROVE_LOG"));
    private static final Map<String,Gain> SESSION=new LinkedHashMap<>();
    private static final Map<Tree,Long> SESSION_TREES=new java.util.EnumMap<>(Tree.class);
    private static final Map<Tree,Double> SESSION_WHOLE=new java.util.EnumMap<>(Tree.class);
    private static final Map<Tree,Long> SESSION_FORAGING_XP=new java.util.EnumMap<>(Tree.class);
    private static final Map<Tree,Long> SESSION_HOTF_XP=new java.util.EnumMap<>(Tree.class);
    private static final Map<Tree,Long> SESSION_WHISPERS=new java.util.EnumMap<>(Tree.class);
    private static final Map<String,Integer> INVENTORY=new HashMap<>();
    private static ArtemisConfig cfg;
    private static boolean initialized,inGift,inBonus,inventoryReady;
    private static Tree giftTree,lastGiftTree;
    private static double giftPercent;
    private static int rewardCount;
    private static long sessionUptime,lastTick,lastActivity,lastAxeAt,lastGiftAt,lastWarnAt;
    private static String profile="";
    private static Recent recent;
    private static final List<String> rareLines=new ArrayList<>();

    private ArtemisForagingTracker(){}

    public static void init(ArtemisConfig config){
        cfg=config;normalize();if(initialized)return;initialized=true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay||onChat(message));
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetTransient());
        ConstellationClient.tick().every(5,"artemis-foraging-tracker",ArtemisForagingTracker::tick);
    }

    private static boolean onChat(Component component){
        if(!active())return true;
        String line=clean(component.getString());
        if(separator(line)){
            boolean wasOpen=inGift;inGift=!inGift;
            if(!wasOpen){beginGift();return !cfg.foragingTrackerCompactGiftChat;}
            finishGift();return !cfg.foragingTrackerCompactGiftChat;
        }
        if(!inGift)return true;
        if(line.equalsIgnoreCase("BONUS GIFT")){inBonus=true;return !cfg.foragingTrackerCompactGiftChat;}
        Matcher match=CONTRIBUTION.matcher(line);
        if(match.matches()){
            giftPercent=decimal(match.group(1));giftTree=Tree.from(match.group(2));
            if(giftTree!=null){lastGiftTree=giftTree;SESSION_TREES.merge(giftTree,1L,Long::sum);SESSION_WHOLE.merge(giftTree,giftPercent/100.0,Double::sum);persistentTree();}
        }
        match=REWARDS.matcher(line);
        if(match.matches()){rewardCount=(int)number(match.group(1));parseHover(component);}
        match=PHANTOM.matcher(line);if(match.matches()&&cfg.foragingTrackerCompactMobs)rareLines.add("A wild "+match.group(1)+" appeared.");
        if(inBonus){match=BONUS.matcher(line);if(match.matches()&&showBonus(match.group(1)))rareLines.add(match.group(1));}
        return !cfg.foragingTrackerCompactGiftChat;
    }

    private static void beginGift(){giftTree=null;giftPercent=0;rewardCount=0;inBonus=false;rareLines.clear();lastGiftAt=System.currentTimeMillis();}
    private static void finishGift(){
        if(cfg.foragingTrackerCompactGiftChat&&giftTree!=null){
            local(giftTree.display+" Tree Gift: "+trim(giftPercent)+"% contribution and "+rewardCount+" rewards.");
            for(String line:rareLines)local("Bonus: "+line);
        }
        inBonus=false;inGift=false;giftTree=null;rareLines.clear();lastActivity=lastGiftAt=System.currentTimeMillis();
    }

    private static void parseHover(Component component){
        List<String> lines=new ArrayList<>();collectHover(component,lines);
        for(String raw:lines){
            String line=clean(raw);Matcher amount=HOVER_REWARD.matcher(line);Matcher chance=HOVER_PERCENT.matcher(line);
            if(amount.matches())recordReward(amount.group(1),(int)number(amount.group(2)));
            else if(chance.matches()&&showBonus(chance.group(1)))rareLines.add(chance.group(1));
        }
    }
    private static void collectHover(Component component,List<String> lines){
        if(component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText text)for(String line:text.value().getString().split("\\R"))lines.add(line);
        for(Component sibling:component.getSiblings())collectHover(sibling,lines);
    }
    private static void recordReward(String raw,int amount){
        if(amount<=0||giftTree==null)return;String name=cleanReward(raw);
        if(name.equalsIgnoreCase("Foraging Experience")){SESSION_FORAGING_XP.merge(giftTree,(long)amount,Long::sum);persistentMetric("fxp",amount);return;}
        if(name.equalsIgnoreCase("HOTF Experience")){SESSION_HOTF_XP.merge(giftTree,(long)amount,Long::sum);persistentMetric("hxp",amount);return;}
        if(name.equalsIgnoreCase("Forest Whispers")){SESSION_WHISPERS.merge(giftTree,(long)amount,Long::sum);persistentMetric("whispers",amount);return;}
        if(name.equalsIgnoreCase("Stretching Sticks"))return;
        gain(id(name),name,amount);
    }

    private static void tick(){
        String nowProfile=profileKey();if(!nowProfile.equals(profile)){profile=nowProfile;resetSession();}
        if(!active()){lastTick=0;inventoryReady=false;return;}
        long now=System.currentTimeMillis();if(holdingAxe())lastAxeAt=now;
        if(lastTick>0&&now-lastActivity<=Math.max(5,cfg.foragingTrackerAfkSeconds)*1000L){
            long delta=Math.min(1000,now-lastTick);sessionUptime+=delta;
            if(cfg.foragingTrackerPersistent)cfg.foragingTrackerUptime.merge(profile,delta,Long::sum);
        }
        lastTick=now;
        scanInventory(now);
    }

    private static void scanInventory(long now){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        Map<String,Integer> current=new HashMap<>();
        for(ItemStack stack:mc.player.getInventory().getNonEquipmentItems()){
            String id=itemId(stack);if(trackInventory(id))current.merge(id,stack.getCount(),Integer::sum);
        }
        if(inventoryReady&&cfg.foragingTrackerTrackInventoryLogs&&(holdingAxe()||now-lastAxeAt<=Math.max(1,cfg.foragingTrackerDisappearSeconds)*1000L||now-lastGiftAt<5000)){
            for(var entry:current.entrySet()){int change=entry.getValue()-INVENTORY.getOrDefault(entry.getKey(),0);if(change>0){Tree tree=entry.getKey().equals("STRETCHING_STICKS")?lastGiftTree:entry.getKey().contains("MANGROVE")?Tree.MANGROVE:Tree.FIG;if(tree==null)continue;Tree old=giftTree;giftTree=tree;gain(entry.getKey(),display(entry.getKey()),change);giftTree=old;}}
        }
        INVENTORY.clear();INVENTORY.putAll(current);inventoryReady=true;
    }

    private static boolean trackInventory(String id){return id.equals("STRETCHING_STICKS")||id.equals("FIG_LOG")||id.equals("ENCHANTED_FIG_LOG")||id.equals("MANGROVE_LOG")||id.equals("ENCHANTED_MANGROVE_LOG");}
    private static void gain(String id,String name,long amount){
        if(amount<=0)return;long now=System.currentTimeMillis();String key=key(giftTree,id);
        Gain gain=SESSION.get(key);if(gain==null)SESSION.put(key,new Gain(name,amount,now));else{gain.amount+=amount;gain.lastAt=now;}
        if(cfg.foragingTrackerPersistent){String full=profile+"|"+key;cfg.foragingTrackerAmounts.merge(full,amount,Long::sum);cfg.foragingTrackerNames.put(full,name);cfg.foragingTrackerLastGains.put(full,now);save();}
        Value value=value(id,amount);recent=new Recent(name,amount,value.amount,value.complete,now);lastActivity=now;warn(name,value);
    }
    private record Value(double amount,boolean complete){}
    private static Value value(String id,long amount){double price=cfg.foragingTrackerPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);if(price<=0){PriceProvider.warm(id);return new Value(0,false);}return new Value(price*amount,true);}

    public static Stats stats(){
        if(!active())return null;String filter=cfg.foragingTrackerTreeFilter.toUpperCase(Locale.ROOT);List<Row> rows=new ArrayList<>();double profit=0;boolean complete=true;
        Map<String,Gain> source=new LinkedHashMap<>();
        if(cfg.foragingTrackerPersistent){String prefix=profile+"|";for(var entry:cfg.foragingTrackerAmounts.entrySet())if(entry.getKey().startsWith(prefix)){String key=entry.getKey().substring(prefix.length());source.put(key,new Gain(cfg.foragingTrackerNames.getOrDefault(entry.getKey(),display(idFromKey(key))),entry.getValue(),cfg.foragingTrackerLastGains.getOrDefault(entry.getKey(),0L)));}}else source.putAll(SESSION);
        for(var entry:source.entrySet()){if(!includeKey(entry.getKey(),filter))continue;String id=idFromKey(entry.getKey());Value value=value(id,entry.getValue().amount);profit+=value.amount;complete&=value.complete;rows.add(new Row(id,entry.getValue().name,entry.getValue().amount,value.amount,value.complete,entry.getValue().lastAt));}
        Comparator<Row> sort=switch(cfg.foragingTrackerSort.toUpperCase(Locale.ROOT)){case"VALUE_ASC"->Comparator.comparingDouble(Row::value);case"AMOUNT_DESC"->Comparator.comparingLong(Row::amount).reversed();case"AMOUNT_ASC"->Comparator.comparingLong(Row::amount);case"NAME"->Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);case"RECENT"->Comparator.comparingLong(Row::lastAt).reversed();default->Comparator.comparingDouble(Row::value).reversed();};
        rows.sort(sort);if(rows.size()>Math.clamp(cfg.foragingTrackerRows,1,50))rows=new ArrayList<>(rows.subList(0,Math.clamp(cfg.foragingTrackerRows,1,50)));
        long trees=metricLong("trees",filter),fxp=metricLong("fxp",filter),hxp=metricLong("hxp",filter),whispers=metricLong("whispers",filter);double whole=metricDouble(filter);
        long uptime=cfg.foragingTrackerPersistent?cfg.foragingTrackerUptime.getOrDefault(profile,0L):sessionUptime;
        Recent shown=recent!=null&&System.currentTimeMillis()-recent.at<=Math.max(1,cfg.foragingTrackerRecentSeconds)*1000L?recent:null;
        return new Stats(List.copyOf(rows),trees,whole,fxp,hxp,whispers,profit,complete,uptime<=0?0:profit*3_600_000.0/uptime,uptime,shown);
    }

    public static boolean visible(){if(!active()||!cfg.foragingTrackerHud)return false;long now=System.currentTimeMillis();return !cfg.foragingTrackerOnlyHoldingAxe||holdingAxe()||now-lastAxeAt<=Math.max(0,cfg.foragingTrackerDisappearSeconds)*1000L;}
    public static ArtemisConfig config(){return cfg;}
    private static void warn(String name,Value value){if(!value.complete||System.currentTimeMillis()-lastWarnAt<1500)return;double chat=Math.max(0,cfg.foragingTrackerMinimumChatMillions)*1_000_000.0,title=Math.max(0,cfg.foragingTrackerMinimumTitleMillions)*1_000_000.0;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;boolean warned=false;if(cfg.foragingTrackerChatWarning&&value.amount>=chat){local("Found "+name+" worth "+coins(value.amount,true)+".");warned=true;}if(cfg.foragingTrackerTitleWarning&&value.amount>=title){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name).withColor(cfg.foragingTrackerRecentColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(coins(value.amount,true)));warned=true;}if(warned&&cfg.foragingTrackerSoundWarning)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.2f);if(warned)lastWarnAt=System.currentTimeMillis();}

    private static boolean showBonus(String name){String lower=name.toLowerCase(Locale.ROOT);if(lower.contains("book")||lower.contains("impression")||lower.contains("missile"))return cfg.foragingTrackerCompactBooks;if(lower.contains("booster"))return cfg.foragingTrackerCompactBoosters;if(lower.contains("shard")||lower.contains("chameleon")||lower.contains("hummingbird"))return cfg.foragingTrackerCompactShards;if(lower.contains("rune")||lower.contains("fading"))return cfg.foragingTrackerCompactRunes;if(lower.contains("fish"))return cfg.foragingTrackerCompactMisc;return cfg.foragingTrackerCompactUncommon;}
    private static boolean separator(String line){if(line.length()<50)return false;char first=line.charAt(0);for(int i=1;i<line.length();i++)if(line.charAt(i)!=first)return false;return true;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.foragingTracker&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;}
    private static boolean holdingAxe(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&(axe(mc.player.getMainHandItem())||axe(mc.player.getOffhandItem()));}
    private static boolean axe(ItemStack stack){if(stack==null||stack.isEmpty())return false;String id=itemId(stack),name=clean(stack.getHoverName().getString());if(id.endsWith("_AXE")||id.contains("_AXE_")||name.matches("(?i).*\\bAxe\\b.*"))return true;ItemLore lore=stack.get(DataComponents.LORE);return lore!=null&&lore.lines().stream().map(line->clean(line.getString())).anyMatch(line->line.matches("(?i).*(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL) AXE.*"));}
    private static String itemId(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static String cleanReward(String raw){return clean(raw).replaceFirst("(?i)^\\+?\\d+\\s+","").trim();}
    private static String id(String name){String known=IDS.get(name);if(known!=null)return known;return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static String display(String id){String value=id.toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder out=new StringBuilder();for(String part:value.split(" "))if(!part.isBlank())out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');return out.toString().trim();}
    private static String key(Tree tree,String id){return(tree==null?Tree.FIG:tree).name()+"|"+id;}private static String idFromKey(String key){int at=key.indexOf('|');return at<0?key:key.substring(at+1);}private static boolean includeKey(String key,String filter){return filter.equals("ALL")||key.startsWith(filter+"|");}
    private static void persistentTree(){if(!cfg.foragingTrackerPersistent||giftTree==null)return;cfg.foragingTrackerTrees.merge(profile+"|"+giftTree.name(),1L,Long::sum);cfg.foragingTrackerWholeTrees.merge(profile+"|"+giftTree.name(),giftPercent/100.0,Double::sum);save();}
    private static void persistentMetric(String metric,long amount){if(cfg.foragingTrackerPersistent&&giftTree!=null){map(metric).merge(profile+"|"+giftTree.name(),amount,Long::sum);save();}}
    private static Map<String,Long> map(String metric){return switch(metric){case"fxp"->cfg.foragingTrackerForagingXp;case"hxp"->cfg.foragingTrackerHotfXp;case"whispers"->cfg.foragingTrackerWhispers;default->cfg.foragingTrackerTrees;};}
    private static long metricLong(String metric,String filter){if(cfg.foragingTrackerPersistent){long total=0;for(Tree tree:Tree.values())if(filter.equals("ALL")||filter.equals(tree.name()))total+=map(metric).getOrDefault(profile+"|"+tree.name(),0L);return total;}Map<Tree,Long> source=switch(metric){case"fxp"->SESSION_FORAGING_XP;case"hxp"->SESSION_HOTF_XP;case"whispers"->SESSION_WHISPERS;default->SESSION_TREES;};return source.entrySet().stream().filter(e->filter.equals("ALL")||filter.equals(e.getKey().name())).mapToLong(Map.Entry::getValue).sum();}
    private static double metricDouble(String filter){if(cfg.foragingTrackerPersistent){double total=0;for(Tree tree:Tree.values())if(filter.equals("ALL")||filter.equals(tree.name()))total+=cfg.foragingTrackerWholeTrees.getOrDefault(profile+"|"+tree.name(),0.0);return total;}return SESSION_WHOLE.entrySet().stream().filter(e->filter.equals("ALL")||filter.equals(e.getKey().name())).mapToDouble(Map.Entry::getValue).sum();}
    private static String profileKey(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static long number(String raw){try{return Long.parseLong(raw.replace(",",""));}catch(Exception ignored){return 0;}}private static double decimal(String raw){try{return Double.parseDouble(raw);}catch(Exception ignored){return 0;}}private static String trim(double value){return value==Math.rint(value)?Long.toString(Math.round(value)):String.format(Locale.ROOT,"%.1f",value);}
    public static String coins(double value,boolean complete){String text=Math.abs(value)>=1_000_000?String.format(Locale.ROOT,"%.2fm",value/1_000_000):Math.abs(value)>=1_000?String.format(Locale.ROOT,"%.1fk",value/1_000):String.format(Locale.ROOT,"%.0f",value);return text+(complete?"":" partial");}
    private static void normalize(){if(cfg.foragingTrackerAmounts==null)cfg.foragingTrackerAmounts=new HashMap<>();if(cfg.foragingTrackerNames==null)cfg.foragingTrackerNames=new HashMap<>();if(cfg.foragingTrackerLastGains==null)cfg.foragingTrackerLastGains=new HashMap<>();if(cfg.foragingTrackerTrees==null)cfg.foragingTrackerTrees=new HashMap<>();if(cfg.foragingTrackerWholeTrees==null)cfg.foragingTrackerWholeTrees=new HashMap<>();if(cfg.foragingTrackerForagingXp==null)cfg.foragingTrackerForagingXp=new HashMap<>();if(cfg.foragingTrackerHotfXp==null)cfg.foragingTrackerHotfXp=new HashMap<>();if(cfg.foragingTrackerWhispers==null)cfg.foragingTrackerWhispers=new HashMap<>();if(cfg.foragingTrackerUptime==null)cfg.foragingTrackerUptime=new HashMap<>();}
    private static void resetTransient(){profile="";resetSession();}private static void resetSession(){SESSION.clear();SESSION_TREES.clear();SESSION_WHOLE.clear();SESSION_FORAGING_XP.clear();SESSION_HOTF_XP.clear();SESSION_WHISPERS.clear();INVENTORY.clear();inventoryReady=false;inGift=inBonus=false;giftTree=lastGiftTree=null;sessionUptime=lastTick=lastActivity=lastAxeAt=lastGiftAt=lastWarnAt=0;recent=null;rareLines.clear();}
    private static void save(){ConstellationClient.saveConfig();}private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Foraging] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("foragingtracker").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Foraging session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearprofile").executes(c->clearProfile())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,50)).executes(c->{cfg.foragingTrackerRows=IntegerArgumentType.getInteger(c,"amount");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("delay").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,60)).executes(c->{cfg.foragingTrackerDisappearSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recent").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,120)).executes(c->{cfg.foragingTrackerRecentSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("afk").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(5,600)).executes(c->{cfg.foragingTrackerAfkSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warning").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("millions",IntegerArgumentType.integer(0,1000)).executes(c->warning(StringArgumentType.getString(c,"type"),IntegerArgumentType.getInteger(c,"millions")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tree").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("filter",StringArgumentType.word()).executes(c->tree(StringArgumentType.getString(c,"filter"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->price(StringArgumentType.getString(c,"source"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){Stats s=stats();local("Tracker "+on(cfg.foragingTracker)+", "+(s==null?0:s.trees)+" tree gifts, "+coins(s==null?0:s.profit,s==null||s.complete)+", filter "+cfg.foragingTrackerTreeFilter.toLowerCase(Locale.ROOT)+".");return 1;}
    private static int clearProfile(){String prefix=profileKey()+"|";cfg.foragingTrackerAmounts.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerNames.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerLastGains.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerTrees.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerWholeTrees.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerForagingXp.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerHotfXp.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerWhispers.keySet().removeIf(k->k.startsWith(prefix));cfg.foragingTrackerUptime.remove(profileKey());resetSession();save();local("Active profile foraging history cleared.");return 1;}
    private static int tree(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!List.of("ALL","FIG","MANGROVE").contains(value)){local("Tree filter must be all, fig, or mangrove.");return 0;}cfg.foragingTrackerTreeFilter=value;save();return status();}
    private static int warning(String type,int millions){if(type.equalsIgnoreCase("chat"))cfg.foragingTrackerMinimumChatMillions=millions;else if(type.equalsIgnoreCase("title"))cfg.foragingTrackerMinimumTitleMillions=millions;else{local("Warning type must be chat or title.");return 0;}save();return status();}
    private static int price(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("PURCHASE")&&!value.equals("SELL")){local("Price source must be purchase or sell.");return 0;}cfg.foragingTrackerPriceSource=value;save();return status();}
    private static int sort(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!List.of("VALUE_DESC","VALUE_ASC","AMOUNT_DESC","AMOUNT_ASC","NAME","RECENT").contains(value)){local("Sort must be value_desc, value_asc, amount_desc, amount_asc, name, or recent.");return 0;}cfg.foragingTrackerSort=value;save();return status();}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.foragingTracker=value;case"hud"->cfg.foragingTrackerHud=value;case"compact"->cfg.foragingTrackerCompactGiftChat=value;case"uncommon"->cfg.foragingTrackerCompactUncommon=value;case"books"->cfg.foragingTrackerCompactBooks=value;case"mobs"->cfg.foragingTrackerCompactMobs=value;case"boosters"->cfg.foragingTrackerCompactBoosters=value;case"shards"->cfg.foragingTrackerCompactShards=value;case"runes"->cfg.foragingTrackerCompactRunes=value;case"misc"->cfg.foragingTrackerCompactMisc=value;case"axe"->cfg.foragingTrackerOnlyHoldingAxe=value;case"whole"->cfg.foragingTrackerShowWholeTrees=value;case"table"->cfg.foragingTrackerShowTable=value;case"recent"->cfg.foragingTrackerShowRecent=value;case"profit"->cfg.foragingTrackerShowProfit=value;case"hour"->cfg.foragingTrackerShowProfitPerHour=value;case"foragingxp"->cfg.foragingTrackerShowForagingXp=value;case"hotfxp"->cfg.foragingTrackerShowHotfXp=value;case"whispers"->cfg.foragingTrackerShowWhispers=value;case"trees"->cfg.foragingTrackerShowTrees=value;case"uptime"->cfg.foragingTrackerShowUptime=value;case"persistent"->cfg.foragingTrackerPersistent=value;case"logs"->cfg.foragingTrackerTrackInventoryLogs=value;case"chat"->cfg.foragingTrackerChatWarning=value;case"title"->cfg.foragingTrackerTitleWarning=value;case"sound"->cfg.foragingTrackerSoundWarning=value;default->{local("Unknown foraging option.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}private static String on(boolean value){return value?"on":"off";}
}
