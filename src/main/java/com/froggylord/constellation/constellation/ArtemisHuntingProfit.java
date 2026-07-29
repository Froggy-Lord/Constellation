package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.ArtemisConfig;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArtemisHuntingProfit {
    public record Row(String id,String name,long amount,double value,boolean complete,long lastAt){}
    public record Stats(List<Row> rows,long mobs,long shards,double profit,boolean complete,double perHour,long uptime,Recent recent){}
    public record Recent(String name,long amount,double value,boolean complete,long at){}
    private static final class Gain{String name;long amount,lastAt;Gain(String n,long a,long t){name=n;amount=a;lastAt=t;}}

    // ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/AttributeShardsData.kt
    private static final Pattern CAUGHT=Pattern.compile("^You caught(?: (?:a|an))?(?: x(\\d+))? (.+?) Shards?!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOTSHARE=Pattern.compile("^LOOT SHARE You received (?:an?|([\\d,]+)) (.+?) Shards? for assisting .+!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARM=Pattern.compile("^(?:CHARM|NAGA|SALT) You charmed (?:a|an) .+? and captured (?:(\\d+) Shards from it|its Shard)\\.$",Pattern.CASE_INSENSITIVE);
    // ported from SkyHanni (LGPL-3.0-or-later): constants/attribute_shards.json
    private static final Map<String,String> SHARD_ALIASES=Map.of(
        "INFERNO_DEMONLORD","SHARD_BURNINGSOUL","ABYSSAL_LANTERNFISH","SHARD_ABYSSAL_LANTERN",
        "LOCH_EMPEROR","SHARD_SEA_EMPEROR","END_STONE_PROTECTOR","SHARD_ENDSTONE_PROTECTOR",
        "BOGGED","SHARD_SEA_ARCHER","CINDERBAT","SHARD_CINDER_BAT","STRIDERSURFER","SHARD_STRIDER_SURFER");
    private static final Map<String,Gain> SESSION=new LinkedHashMap<>();
    private static ArtemisConfig cfg;
    private static boolean initialized;
    private static long sessionMobs,sessionShards,sessionUptime,lastTick,lastActivity,lastPickup,lastToolAt,lastWarnAt;
    private static String profile="";
    private static Recent recent;

    private ArtemisHuntingProfit(){}

    public static void init(ArtemisConfig config){
        cfg=config;normalize();
        if(initialized)return;initialized=true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetTransient());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetTransient());
        ConstellationClient.tick().every(5,"artemis-hunting-profit",ArtemisHuntingProfit::tick);
    }

    private static void onChat(String line){
        if(!active())return;
        Matcher match=CAUGHT.matcher(line);
        if(match.matches()){gain(match.group(2),number(match.group(1),1),true);return;}
        match=LOOTSHARE.matcher(line);
        if(cfg.huntingProfitIncludeLootshare&&match.matches()){gain(match.group(2),number(match.group(1),1),true);return;}
        match=CHARM.matcher(line);
        if(cfg.huntingProfitIncludeCharm&&match.matches()){
            String name=charmedMob(line);
            gain(name,number(match.group(1),1),true);
        }
    }

    private static void tick(){
        String nowProfile=profileKey();
        if(!nowProfile.equals(profile)){profile=nowProfile;resetSession();}
        if(!active()){lastTick=0;return;}
        long now=System.currentTimeMillis();
        if(holdingTool())lastToolAt=now;
        if(lastTick>0&&now-lastActivity<=Math.max(5,cfg.huntingProfitAfkSeconds)*1000L){
            long delta=Math.min(1000,now-lastTick);sessionUptime+=delta;
            if(cfg.huntingProfitPersistent)cfg.huntingProfitUptime.merge(profile,delta,Long::sum);
        }
        lastTick=now;
    }

    private static void gain(String rawName,int amount,boolean mob){
        if(amount<=0)return;
        String name=cleanName(rawName),id=shardMarketId(name),key=profile+"|"+id;
        long now=System.currentTimeMillis();
        Gain gain=SESSION.get(id);
        if(gain==null)SESSION.put(id,new Gain(name,amount,now));else{gain.amount+=amount;gain.lastAt=now;}
        sessionShards+=amount;if(mob)sessionMobs++;
        if(cfg.huntingProfitPersistent){
            cfg.huntingProfitAmounts.merge(key,(long)amount,Long::sum);
            cfg.huntingProfitNames.put(key,name);
            cfg.huntingProfitLastGains.put(key,now);
            cfg.huntingProfitShards.merge(profile,(long)amount,Long::sum);
            if(mob)cfg.huntingProfitMobs.merge(profile,1L,Long::sum);
            save();
        }
        Value value=value(id,amount);
        recent=new Recent(name,amount,value.amount,value.complete,now);
        lastPickup=lastActivity=now;
        warn(name,value);
    }

    private record Value(double amount,boolean complete){}
    private static Value value(String id,long amount){
        double price=cfg.huntingProfitPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);
        if(price<=0){PriceProvider.warm(id);return new Value(0,false);}
        return new Value(price*amount,true);
    }

    public static Stats stats(){
        if(!active())return null;
        List<Row> rows=new ArrayList<>();double profit=0;boolean complete=true;
        Map<String,Gain> source=new LinkedHashMap<>();
        if(cfg.huntingProfitPersistent){
            String prefix=profile+"|";
            for(var entry:cfg.huntingProfitAmounts.entrySet())if(entry.getKey().startsWith(prefix)){
                String id=entry.getKey().substring(prefix.length());
                source.put(id,new Gain(cfg.huntingProfitNames.getOrDefault(entry.getKey(),displayFromId(id)),entry.getValue(),cfg.huntingProfitLastGains.getOrDefault(entry.getKey(),0L)));
            }
        }else source.putAll(SESSION);
        for(var entry:source.entrySet()){
            Value value=value(entry.getKey(),entry.getValue().amount);profit+=value.amount;complete&=value.complete;
            rows.add(new Row(entry.getKey(),entry.getValue().name,entry.getValue().amount,value.amount,value.complete,entry.getValue().lastAt));
        }
        Comparator<Row> sort=switch(cfg.huntingProfitSorting.toUpperCase(Locale.ROOT)){
            case"VALUE_ASC"->Comparator.comparingDouble(Row::value);
            case"AMOUNT_DESC"->Comparator.comparingLong(Row::amount).reversed();
            case"AMOUNT_ASC"->Comparator.comparingLong(Row::amount);
            case"NAME"->Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);
            case"RECENT"->Comparator.comparingLong(Row::lastAt).reversed();
            default->Comparator.comparingDouble(Row::value).reversed();
        };
        rows.sort(sort);int max=Math.clamp(cfg.huntingProfitRows,1,50);if(rows.size()>max)rows=new ArrayList<>(rows.subList(0,max));
        long uptime=cfg.huntingProfitPersistent?cfg.huntingProfitUptime.getOrDefault(profile,0L):sessionUptime;
        long mobs=cfg.huntingProfitPersistent?cfg.huntingProfitMobs.getOrDefault(profile,0L):sessionMobs;
        long shards=cfg.huntingProfitPersistent?cfg.huntingProfitShards.getOrDefault(profile,0L):sessionShards;
        Recent shown=recent!=null&&System.currentTimeMillis()-recent.at<=Math.max(1,cfg.huntingProfitRecentSeconds)*1000L?recent:null;
        return new Stats(List.copyOf(rows),mobs,shards,profit,complete,uptime<=0?0:profit*3_600_000.0/uptime,uptime,shown);
    }

    public static boolean visible(){
        if(!active())return false;
        long now=System.currentTimeMillis();
        return cfg.huntingProfitAlwaysShow||cfg.huntingProfitShowWhenPickup&&now-lastPickup<=Math.max(1,cfg.huntingProfitRecentSeconds)*1000L
            ||cfg.huntingProfitShowWithTool&&(holdingTool()||now-lastToolAt<=Math.max(1,cfg.huntingProfitToolGraceSeconds)*1000L);
    }

    private static void warn(String name,Value value){
        if(!value.complete||System.currentTimeMillis()-lastWarnAt<1500)return;
        double chat=Math.max(0,cfg.huntingProfitMinimumChatMillions)*1_000_000.0;
        double title=Math.max(0,cfg.huntingProfitMinimumTitleMillions)*1_000_000.0;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;boolean warned=false;
        if(cfg.huntingProfitChatWarning&&value.amount>=chat){local("Caught "+name+" worth "+coins(value.amount,true)+".");warned=true;}
        if(cfg.huntingProfitTitleWarning&&value.amount>=title){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name).withColor(cfg.huntingProfitRecentColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(coins(value.amount,true)));warned=true;}
        if(warned&&cfg.huntingProfitSoundWarning)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.2f);
        if(warned)lastWarnAt=System.currentTimeMillis();
    }

    private static boolean holdingTool(){
        Minecraft mc=Minecraft.getInstance();return mc.player!=null&&(tool(mc.player.getMainHandItem())||tool(mc.player.getOffhandItem()));
    }
    private static boolean tool(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;String id=itemId(stack),name=clean(stack.getHoverName().getString()).toUpperCase(Locale.ROOT);
        return id.contains("LASSO")||id.contains("FISHING_NET")||id.contains("HUNTING")||id.contains("BLACK_HOLE")
            ||name.contains("LASSO")||name.contains("HUNTING NET")||name.contains("BLACK HOLE");
    }
    private static String itemId(ItemStack stack){
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";
        CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;
        return extra.getStringOr("id","").toUpperCase(Locale.ROOT);
    }
    public static String shardMarketId(String name){String normalized=name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");return SHARD_ALIASES.getOrDefault(normalized,"SHARD_"+normalized);}
    private static String displayFromId(String id){String value=id.replaceFirst("^SHARD_","").toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder out=new StringBuilder();for(String part:value.split(" "))if(!part.isBlank())out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');return out.toString().trim();}
    private static String cleanName(String raw){return clean(raw).replaceFirst("(?i) Attribute$","").trim();}
    private static String charmedMob(String line){Matcher m=Pattern.compile("(?i)charmed (?:a|an) (.+?) and captured").matcher(line);return m.find()?m.group(1):"Charmed Mob";}
    private static int number(String raw,int fallback){if(raw==null)return fallback;try{return Integer.parseInt(raw.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String clean(String raw){String out=ChatFormatting.stripFormatting(raw);return out==null?"":out.trim().replaceAll("\\s+"," ");}
    private static String profileKey(){String p=LyraStorageValue.currentProfileKey();return p==null||p.isBlank()?"unknown":p.toLowerCase(Locale.ROOT);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.huntingProfitTracker&&ConstellationClient.loc().onHypixel();}
    public static ArtemisConfig config(){return cfg;}
    public static String coins(double value,boolean complete){String text=Math.abs(value)>=1_000_000?String.format(Locale.ROOT,"%.2fm",value/1_000_000):Math.abs(value)>=1_000?String.format(Locale.ROOT,"%.1fk",value/1_000):String.format(Locale.ROOT,"%.0f",value);return text+(complete?"":" partial");}
    private static void normalize(){if(cfg.huntingProfitAmounts==null)cfg.huntingProfitAmounts=new HashMap<>();if(cfg.huntingProfitNames==null)cfg.huntingProfitNames=new HashMap<>();if(cfg.huntingProfitLastGains==null)cfg.huntingProfitLastGains=new HashMap<>();if(cfg.huntingProfitMobs==null)cfg.huntingProfitMobs=new HashMap<>();if(cfg.huntingProfitShards==null)cfg.huntingProfitShards=new HashMap<>();if(cfg.huntingProfitUptime==null)cfg.huntingProfitUptime=new HashMap<>();}
    private static void resetTransient(){profile="";resetSession();}
    private static void resetSession(){SESSION.clear();sessionMobs=sessionShards=sessionUptime=lastTick=lastActivity=lastPickup=lastToolAt=lastWarnAt=0;recent=null;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Hunting] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("huntingprofit").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Hunting profit session reset.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearprofile").executes(c->clearProfile()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,50)).executes(c->{cfg.huntingProfitRows=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recent").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,120)).executes(c->{cfg.huntingProfitRecentSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("afk").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(5,600)).executes(c->{cfg.huntingProfitAfkSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->price(StringArgumentType.getString(c,"source")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){Stats s=stats();local("Tracker "+(cfg.huntingProfitTracker?"on":"off")+", "+(s==null?0:s.mobs)+" catches, "+(s==null?0:s.shards)+" shards, "+coins(s==null?0:s.profit,s==null||s.complete)+".");return 1;}
    private static int clearProfile(){String prefix=profileKey()+"|";cfg.huntingProfitAmounts.keySet().removeIf(k->k.startsWith(prefix));cfg.huntingProfitNames.keySet().removeIf(k->k.startsWith(prefix));cfg.huntingProfitLastGains.keySet().removeIf(k->k.startsWith(prefix));cfg.huntingProfitMobs.remove(profileKey());cfg.huntingProfitShards.remove(profileKey());cfg.huntingProfitUptime.remove(profileKey());resetSession();save();local("Active profile hunting history cleared.");return 1;}
    private static int price(String raw){String v=raw.toUpperCase(Locale.ROOT);if(!v.equals("PURCHASE")&&!v.equals("SELL")){local("Price source must be purchase or sell.");return 0;}cfg.huntingProfitPriceSource=v;save();return status();}
    private static int sort(String raw){String v=raw.toUpperCase(Locale.ROOT);if(!List.of("VALUE_DESC","VALUE_ASC","AMOUNT_DESC","AMOUNT_ASC","NAME","RECENT").contains(v)){local("Sort must be value_desc, value_asc, amount_desc, amount_asc, name, or recent.");return 0;}cfg.huntingProfitSorting=v;save();return status();}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.huntingProfitTracker=v;case"hud"->cfg.huntingProfitHud=v;case"pickup"->cfg.huntingProfitShowWhenPickup=v;case"always"->cfg.huntingProfitAlwaysShow=v;case"tool"->cfg.huntingProfitShowWithTool=v;case"persistent"->cfg.huntingProfitPersistent=v;case"recent"->cfg.huntingProfitShowRecent=v;case"table"->cfg.huntingProfitShowTable=v;case"hour"->cfg.huntingProfitShowProfitPerHour=v;case"mobs"->cfg.huntingProfitShowMobs=v;case"shards"->cfg.huntingProfitShowShards=v;case"uptime"->cfg.huntingProfitShowUptime=v;case"chat"->cfg.huntingProfitChatWarning=v;case"title"->cfg.huntingProfitTitleWarning=v;case"sound"->cfg.huntingProfitSoundWarning=v;case"lootshare"->cfg.huntingProfitIncludeLootshare=v;case"charm"->cfg.huntingProfitIncludeCharm=v;default->{local("Unknown hunting-profit option.");return 0;}}save();return status();}
}
