package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/mining/fossilexcavator/FossilExcavatorApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/fossilexcavator/ExcavatorProfitTracker.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/fossilexcavator/ProfitPerExcavation.kt
public final class AquilaFossilProfit {
    public record ItemRow(String id,String name,long amount,double value,boolean complete,long lastGainAt) {}
    public record Recent(String name,long amount,double value,boolean complete,long at) {}
    public record Stats(List<ItemRow> items,long excavations,long powder,long dust,double rewards,double scrapCost,double dustValue,double profit,boolean complete,double perExcavation,double perHour,long activeMillis,Recent recent) {}
    private static final class Gain {String name;long amount,lastGainAt;Gain(){}Gain(String name,long amount,long at){this.name=name;this.amount=amount;this.lastGainAt=at;}}
    private static final class Data {long excavations,powder,dust,activeMillis,lastActivity;Map<String,Gain> items=new LinkedHashMap<>();}
    private static final class Store {Map<String,Data> profiles=new HashMap<>();}
    private record Loot(String name,int amount) {}
    private record Value(double amount,boolean complete) {}
    private static final Pattern AMOUNT=Pattern.compile("^(?<name>.+?)(?:\\s+x(?<amount>[\\d,]+))?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOK=Pattern.compile("^Enchanted Book \\((?<name>.+) (?<level>[IVXLCDM]+)\\)$");
    private static final Map<String,String> IDS=new HashMap<>();
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("constellation-fossil-profit.json");
    private static final String SCRAP="SUSPICIOUS_SCRAP";
    private static final Data SESSION=new Data();
    private static final Map<String,Data> PROFILES=new HashMap<>();
    private static final List<Loot> FRAME=new ArrayList<>();
    private static AquilaConfig cfg;
    private static String profileKey="";
    private static boolean initialized,inFrame,dirty,wasPersistent;
    private static long lastTick,lastFrameAt,lastSave;
    private static Recent recent;

    static {
        put("Tusk Fossil","TUSK_FOSSIL");put("Webbed Fossil","WEBBED_FOSSIL");put("Clubbed Fossil","CLUBBED_FOSSIL");put("Spine Fossil","SPINE_FOSSIL");
        put("Claw Fossil","CLAW_FOSSIL");put("Footprint Fossil","FOOTPRINT_FOSSIL");put("Helix Fossil","HELIX");put("Ugly Fossil","UGLY_FOSSIL");
        put("Suspicious Scrap",SCRAP);put("Glacite Amalgamation","GLACITE_AMALGAMATION");put("Glacite Jewel","GLACITE_JEWEL");
        put("Enchanted Glacite","ENCHANTED_GLACITE");put("Enchanted Umber","ENCHANTED_UMBER");put("Enchanted Tungsten","ENCHANTED_TUNGSTEN");
        put("Refined Umber","REFINED_UMBER");put("Refined Tungsten","REFINED_TUNGSTEN");put("Mithril Plate","MITHRIL_PLATE");
        put("Diamond Essence","ESSENCE_DIAMOND");put("Gold Essence","ESSENCE_GOLD");put("Fossil Essence","ESSENCE_FOSSIL");
        for(String type:List.of("Onyx","Peridot","Citrine","Aquamarine","Ruby","Jasper","Opal","Amber","Sapphire","Amethyst","Jade","Topaz"))for(String quality:List.of("Rough","Flawed","Fine","Flawless","Perfect"))put(quality+" "+type+" Gemstone",quality.toUpperCase(Locale.ROOT)+"_"+type.toUpperCase(Locale.ROOT)+"_GEM");
    }

    private AquilaFossilProfit() {}
    public static void init(AquilaConfig config){cfg=config;wasPersistent=cfg.fossilProfitPersistProfiles;if(wasPersistent)load();if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"aquila-fossil-profit",AquilaFossilProfit::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(message.getString());return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->connectionReset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->connectionReset());}

    private static void tick(){
        if(cfg==null)return;
        if(cfg.fossilProfitPersistProfiles!=wasPersistent){wasPersistent=cfg.fossilProfitPersistProfiles;if(wasPersistent){load();profileKey=profile();}else profileKey="";}
        String current=profile();if(!current.equals(profileKey)){profileKey=current;resetSessionClock();}
        long now=System.currentTimeMillis();Data data=data();
        if(active()&&lastTick>0&&now-data.lastActivity<=Math.clamp(cfg.fossilProfitAfkSeconds,5,600)*1000L){data.activeMillis+=Math.min(100,now-lastTick);dirty|=cfg.fossilProfitPersistProfiles;}
        lastTick=active()?now:0;
        if(inFrame&&now-lastFrameAt>10_000){inFrame=false;FRAME.clear();}
        flush(false);
    }

    private static void onChat(String raw){
        if(!active())return;
        String clean=ChatFormatting.stripFormatting(raw);if(clean==null)return;
        if(clean.equals("You didn't find anything. Maybe next time!")){finish(List.of());return;}
        if(clean.matches("^  EXCAVATION COMPLETE ?$")){inFrame=true;FRAME.clear();lastFrameAt=System.currentTimeMillis();return;}
        if(!inFrame)return;
        lastFrameAt=System.currentTimeMillis();
        if(separator(clean)){List<Loot> copy=List.copyOf(FRAME);FRAME.clear();inFrame=false;finish(copy);return;}
        if(!clean.startsWith("    ")||clean.startsWith("     "))return;
        Matcher matcher=AMOUNT.matcher(clean.trim());if(!matcher.matches())return;
        int amount=parse(matcher.group("amount"),1);String name=matcher.group("name").trim();if(!name.isBlank()&&amount>0)FRAME.add(new Loot(name,amount));
    }

    private static void finish(List<Loot> loot){
        long now=System.currentTimeMillis();Data data=data();data.excavations++;data.lastActivity=now;
        double frameValue=0;boolean frameComplete=true;String bestName="Nothing";long bestAmount=0;double bestValue=Double.NEGATIVE_INFINITY;
        for(Loot reward:loot){
            if(reward.name.equals("Glacite Powder")){if(cfg.fossilProfitShowPowder)data.powder+=reward.amount;continue;}
            if(reward.name.equals("Fossil Dust")){if(cfg.fossilProfitShowDust)data.dust+=reward.amount;continue;}
            String id=itemId(reward.name);Gain gain=data.items.get(id);if(gain==null)data.items.put(id,new Gain(reward.name,reward.amount,now));else{gain.amount+=reward.amount;gain.name=reward.name;gain.lastGainAt=now;}
            Value value=value(id,reward.amount);frameValue+=value.amount;frameComplete&=value.complete;
            if(value.amount>bestValue){bestValue=value.amount;bestName=reward.name;bestAmount=reward.amount;}
        }
        double scrap=price(SCRAP);if(scrap<=0){PriceProvider.warm(SCRAP);frameComplete=false;}else frameValue-=scrap;
        recent=new Recent(bestName,bestAmount,frameValue,frameComplete,now);
        dirty|=cfg.fossilProfitPersistProfiles;lastFrameAt=now;
        if(cfg.fossilProfitPerExcavationChat)local("Excavation profit: "+coins(frameValue,frameComplete)+".");
        warn(bestName,frameValue,frameComplete);flush(true);
    }

    private static void warn(String name,double amount,boolean complete){
        if(!complete)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        double chat=Math.max(0,cfg.fossilProfitMinimumChatMillions)*1_000_000.0,title=Math.max(0,cfg.fossilProfitMinimumTitleMillions)*1_000_000.0;
        if(cfg.fossilProfitChatWarning&&amount>=chat)local(name+" excavation worth "+coins(amount,true)+".");
        if(cfg.fossilProfitTitleWarning&&amount>=title){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name+"  "+coins(amount,true)).withColor(cfg.fossilProfitPositiveColor&0xFFFFFF));}
        if(cfg.fossilProfitSoundWarning&&amount>=Math.min(chat,title))mc.player.playSound(SoundEvents.PLAYER_LEVELUP,1,1);
    }

    public static Stats stats(){
        if(!enabled())return null;Data data=data();double rewards=0;boolean complete=true;List<ItemRow> rows=new ArrayList<>();
        for(var entry:data.items.entrySet()){Gain gain=entry.getValue();Value value=value(entry.getKey(),gain.amount);rewards+=value.amount;complete&=value.complete;rows.add(new ItemRow(entry.getKey(),gain.name,gain.amount,value.amount,value.complete,gain.lastGainAt));}
        rows.sort(comparator());int limit=Math.clamp(cfg.fossilProfitRows,1,50);if(rows.size()>limit)rows=new ArrayList<>(rows.subList(0,limit));
        double scrapEach=price(SCRAP),scrap=scrapEach*data.excavations,dustEach=scrapEach/500.0,dust=dustEach*data.dust;
        if(data.excavations>0&&scrapEach<=0){PriceProvider.warm(SCRAP);complete=false;}
        double profit=rewards+dust-scrap,per=data.excavations==0?0:profit/data.excavations,hour=data.activeMillis==0?0:profit*3_600_000.0/data.activeMillis;
        Recent shown=cfg.fossilProfitShowRecent&&recent!=null&&System.currentTimeMillis()-recent.at<=Math.clamp(cfg.fossilProfitRecentSeconds,1,300)*1000L?recent:null;
        return new Stats(List.copyOf(rows),data.excavations,data.powder,data.dust,rewards,scrap,dust,profit,complete,per,hour,data.activeMillis,shown);
    }

    public static boolean visible(){
        if(!enabled())return false;Minecraft mc=Minecraft.getInstance();if(mc.player==null||data().excavations==0)return false;
        if(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)return clean(screen.getTitle().getString()).equals("Fossil Excavator");
        return !cfg.fossilProfitOnlyResearchCenter||researchCenter()||System.currentTimeMillis()-lastFrameAt<=Math.clamp(cfg.fossilProfitRecentSeconds,1,300)*1000L;
    }
    public static AquilaConfig config(){return cfg;}
    public static String coins(double value,boolean complete){double a=Math.abs(value);String out=a>=1_000_000?String.format(Locale.ROOT,"%.2fm",a/1_000_000):a>=1_000?String.format(Locale.ROOT,"%.1fk",a/1_000):String.format(Locale.ROOT,"%.0f",a);return(value<0?"-":"")+out+(complete?"":" partial");}
    public static String time(long millis){long seconds=Math.max(0,millis/1000),hours=seconds/3600;seconds%=3600;return hours>0?hours+"h "+seconds/60+"m":seconds/60+"m "+seconds%60+"s";}
    private static Comparator<ItemRow> comparator(){return switch(cfg.fossilProfitSorting.toUpperCase(Locale.ROOT)){case"VALUE_ASC"->Comparator.comparingDouble(ItemRow::value);case"AMOUNT_DESC"->Comparator.comparingLong(ItemRow::amount).reversed();case"AMOUNT_ASC"->Comparator.comparingLong(ItemRow::amount);case"NAME"->Comparator.comparing(ItemRow::name,String.CASE_INSENSITIVE_ORDER);case"RECENT"->Comparator.comparingLong(ItemRow::lastGainAt).reversed();default->Comparator.comparingDouble(ItemRow::value).reversed();};}
    private static Value value(String id,long amount){double price=price(id);if(price<=0){PriceProvider.warm(id);return new Value(0,false);}return new Value(price*amount,true);}
    private static double price(String id){return cfg.fossilProfitPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);}
    private static String itemId(String name){String known=IDS.get(name);if(known!=null)return known;Matcher book=BOOK.matcher(name);if(book.matches())return"ENCHANTMENT_"+book.group("name").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_")+"_"+roman(book.group("level"));return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static int roman(String raw){int total=0,last=0;for(int i=raw.length()-1;i>=0;i--){int value=switch(raw.charAt(i)){case'I'->1;case'V'->5;case'X'->10;case'L'->50;case'C'->100;case'D'->500;case'M'->1000;default->0;};total+=value<last?-value:value;last=Math.max(last,value);}return Math.max(1,total);}
    private static boolean researchCenter(){if(ConstellationClient.loc().area()!=SkyblockArea.DWARVEN_MINES)return false;return ConstellationClient.loc().getSidebarLines().stream().map(AquilaFossilProfit::clean).anyMatch(s->s.contains("Fossil Research Center"));}
    private static boolean active(){return enabled()&&ConstellationClient.loc().area()==SkyblockArea.DWARVEN_MINES;}
    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.fossilHelper&&cfg.fossilProfitSuite&&ConstellationClient.loc().onHypixel();}
    private static Data data(){if(!cfg.fossilProfitPersistProfiles)return SESSION;String key=profileKey.isBlank()?"unknown":profileKey;return PROFILES.computeIfAbsent(key,k->new Data());}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null?"":value;}
    private static void resetSessionClock(){lastTick=0;inFrame=false;FRAME.clear();recent=null;}
    private static void connectionReset(){flush(true);profileKey="";resetSessionClock();}
    private static boolean separator(String raw){String value=raw.trim();if(value.length()!=64)return false;int first=value.codePointAt(0);return value.codePoints().allMatch(cp->cp==first);}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static int parse(String raw,int fallback){if(raw==null||raw.isBlank())return fallback;try{return Integer.parseInt(raw.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static void put(String name,String id){IDS.put(name,id);}
    private static void load(){try{if(!Files.exists(FILE))return;Store store=GSON.fromJson(Files.readString(FILE,StandardCharsets.UTF_8),Store.class);if(store==null||store.profiles==null)return;PROFILES.clear();for(var entry:store.profiles.entrySet()){Data data=entry.getValue();if(data==null)continue;if(data.items==null)data.items=new LinkedHashMap<>();PROFILES.put(entry.getKey(),data);}}catch(Exception e){ConstellationClient.LOGGER.warn("could not load fossil profit history",e);}}
    private static void flush(boolean force){if(!dirty||!cfg.fossilProfitPersistProfiles||!force&&System.currentTimeMillis()-lastSave<1000)return;lastSave=System.currentTimeMillis();try{Files.createDirectories(FILE.getParent());Store store=new Store();store.profiles.putAll(PROFILES);Files.writeString(FILE,GSON.toJson(store),StandardCharsets.UTF_8);dirty=false;}catch(Exception e){ConstellationClient.LOGGER.warn("could not save fossil profit history",e);}}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fossilprofit").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->reset(false)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearprofile").executes(c->reset(true)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,50)).executes(c->{cfg.fossilProfitRows=IntegerArgumentType.getInteger(c,"count");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threshold").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("millions",IntegerArgumentType.integer(0,1000)).executes(c->{int value=IntegerArgumentType.getInteger(c,"millions");cfg.fossilProfitMinimumChatMillions=value;cfg.fossilProfitMinimumTitleMillions=value;save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceSource(StringArgumentType.getString(c,"source")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sorting(StringArgumentType.getString(c,"mode"))))));
    }
    private static int status(){Stats stats=stats();local(stats==null?"Tracker is unavailable.":stats.excavations+" excavations, "+stats.powder+" Glacite Powder, "+stats.dust+" Fossil Dust, "+coins(stats.profit,stats.complete)+" total.");return 1;}
    private static int reset(boolean profile){if(cfg.fossilProfitPersistProfiles&&!profileKey.isBlank()){if(profile)PROFILES.remove(profileKey);else clear(data());dirty=true;flush(true);recent=null;local(profile?"Current profile fossil history cleared.":"Current fossil tracker view reset.");}else{clear(SESSION);recent=null;local("Fossil profit session reset.");}return 1;}
    private static void clear(Data data){data.excavations=0;data.powder=0;data.dust=0;data.activeMillis=0;data.lastActivity=0;data.items.clear();}
    private static int priceSource(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("BUY")&&!value.equals("SELL")){local("Price source must be buy or sell.");return 0;}cfg.fossilProfitPriceSource=value;save();return status();}
    private static int sorting(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!List.of("VALUE_DESC","VALUE_ASC","AMOUNT_DESC","AMOUNT_ASC","NAME","RECENT").contains(value)){local("Sort must be value_desc, value_asc, amount_desc, amount_asc, name, or recent.");return 0;}cfg.fossilProfitSorting=value;save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Fossil Profit] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
