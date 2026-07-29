package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.CygnusConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.regex.*;

public final class CygnusGifts {
    // ported from SkyHanni (LGPL-2.1): features/gifting/GiftProfitTracker.kt
    private static final Pattern REWARD=Pattern.compile("^(COMMON|RARE|SWEET|SANTA(?: TIER)?|PARTY(?: TIER)?)! (.+?) gift with .+!$");
    private static final Pattern STARS=Pattern.compile("^EXTRA! \\+([\\d,]+) North Stars?$");
    private static final Pattern COINS=Pattern.compile("^\\+([\\d,]+) Coins$");
    private static final Pattern XP=Pattern.compile("^\\+([\\d,]+) ([\\w ]+) XP$");
    private static final Pattern POTION=Pattern.compile("^(.+) XP Boost ([IVXLCDM]+) Potion$");
    private static final Pattern ENCHANT=Pattern.compile("^(.+) ([IVXLCDM]+)$");
    private static final Set<String> GIFT_IDS=Set.of("WHITE_GIFT","GREEN_GIFT","RED_GIFT","PARTY_GIFT");
    private static CygnusConfig cfg;
    private static boolean initialized;
    private static Vec3 lastGiftLocation;
    private static long lastGiftAt,sessionStarted;
    private static String profile="";
    private static final Map<String,Long> SESSION_REWARDS=new LinkedHashMap<>(),SESSION_RARITIES=new LinkedHashMap<>(),SESSION_USED=new LinkedHashMap<>(),SESSION_XP=new LinkedHashMap<>();
    private static long sessionCoins,sessionStars;
    private CygnusGifts(){}

    public static void init(CygnusConfig config){
        cfg=config;normalize();
        if(initialized)return;
        initialized=true;sessionStarted=System.currentTimeMillis();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->worldReset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->worldReset());
        ConstellationClient.tick().every(20,"cygnus-gifts",CygnusGifts::tick);
    }
    private static void tick(){String p=profileKey();if(!p.equals(profile)){profile=p;resetSession();}if(lastGiftAt>0&&System.currentTimeMillis()-lastGiftAt>Math.clamp(cfg.giftProfitRecentSeconds,10,900)*1000L)lastGiftLocation=null;}

    private static void onChat(String text){
        if(!tracking())return;
        Matcher stars=STARS.matcher(text);if(stars.matches()){long n=number(stars.group(1));sessionStars+=n;if(cfg.giftProfitPersistent)cfg.giftNorthStars.merge(profileKey(),n,Long::sum);mark();save();return;}
        Matcher reward=REWARD.matcher(text);if(!reward.matches())return;
        String rarity=reward.group(1).replace(" TIER",""),body=reward.group(2).trim();
        SESSION_RARITIES.merge(rarity,1L,Long::sum);if(cfg.giftProfitPersistent)cfg.giftRarityCounts.merge(key(rarity),1L,Long::sum);mark();
        Matcher coins=COINS.matcher(body),xp=XP.matcher(body);
        if(coins.matches()){long n=number(coins.group(1));sessionCoins+=n;if(cfg.giftProfitPersistent)cfg.giftCoins.merge(profileKey(),n,Long::sum);}
        else if(xp.matches()){long n=number(xp.group(1));String skill=xp.group(2);SESSION_XP.merge(skill,n,Long::sum);if(cfg.giftProfitPersistent)cfg.giftSkillXp.merge(key(skill),n,Long::sum);}
        else{long amount=rewardAmount(body);String item=cleanReward(body);SESSION_REWARDS.merge(item,amount,Long::sum);if(cfg.giftProfitPersistent)cfg.giftRewardCounts.merge(key(item),amount,Long::sum);cfg.giftRewardIds.putIfAbsent(item,guessId(item));warm(item);}
        if(rare(rarity))rareAlert(rarity,body);save();
    }

    public static String hudText(){
        if(!visible())return null;
        Stats stats=stats();List<String> rows=new ArrayList<>();rows.add("§eRewards §f"+stats.rewards+" §7| §6Coins §f"+compact(stats.coins));
        if(cfg.giftProfitShowValue)rows.add("§aValue §f"+coins(stats.value));
        if(cfg.giftProfitShowCost)rows.add("§cGift cost §f"+coins(stats.cost));
        if(cfg.giftProfitShowProfit)rows.add((stats.profit>=0?"§a":"§c")+"Profit §f"+coins(stats.profit));
        if(cfg.giftProfitShowHourly)rows.add("§bPer hour §f"+coins(stats.hourly));
        if(cfg.giftProfitShowNorthStars&&stats.stars>0)rows.add("§dNorth Stars §f"+compact(stats.stars));
        if(cfg.giftProfitShowSkillXp&&!stats.xp.isEmpty())rows.add("§3Skill XP §f"+stats.xp.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(3).map(e->e.getKey()+" "+compact(e.getValue())).reduce((a,b)->a+", "+b).orElse(""));
        if(cfg.giftProfitShowRarities&&!stats.rarities.isEmpty())rows.add(stats.rarities.entrySet().stream().filter(e->e.getValue()>0).map(e->colorTier(e.getKey())+e.getKey()+" §f"+e.getValue()).reduce((a,b)->a+" "+b).orElse(""));
        if(cfg.giftProfitShowTopRewards)stats.items.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(Math.clamp(cfg.giftProfitTopRows,1,10)).forEach(e->rows.add("§9"+e.getKey()+" §f"+e.getValue()));
        return String.join(" | ",rows);
    }

    private record Stats(long rewards,long coins,long stars,Map<String,Long> rarities,Map<String,Long> items,Map<String,Long> xp,double value,double cost,double profit,double hourly){}
    private static Stats stats(){
        Map<String,Long> items=cfg.giftProfitPersistent?profileMap(cfg.giftRewardCounts):Map.copyOf(SESSION_REWARDS),rarities=cfg.giftProfitPersistent?profileMap(cfg.giftRarityCounts):Map.copyOf(SESSION_RARITIES),used=cfg.giftProfitPersistent?profileMap(cfg.giftUsedCounts):Map.copyOf(SESSION_USED),xp=cfg.giftProfitPersistent?profileMap(cfg.giftSkillXp):Map.copyOf(SESSION_XP);
        long coins=cfg.giftProfitPersistent?cfg.giftCoins.getOrDefault(profileKey(),0L):sessionCoins,stars=cfg.giftProfitPersistent?cfg.giftNorthStars.getOrDefault(profileKey(),0L):sessionStars,rewards=rarities.values().stream().mapToLong(Long::longValue).sum();
        double value=coins;for(var e:items.entrySet())value+=rewardPrice(e.getKey())*e.getValue();
        double cost=0;for(var e:used.entrySet())cost+=giftPrice(e.getKey())*e.getValue();
        double profit=value-cost;long elapsed=Math.max(1000,System.currentTimeMillis()-sessionStarted);double sessionValue=sessionCoins;for(var e:SESSION_REWARDS.entrySet())sessionValue+=rewardPrice(e.getKey())*e.getValue();double sessionCost=0;for(var e:SESSION_USED.entrySet())sessionCost+=giftPrice(e.getKey())*e.getValue();
        return new Stats(rewards,coins,stars,rarities,items,xp,value,cost,profit,(sessionValue-sessionCost)*3_600_000d/elapsed);
    }
    private static Map<String,Long> profileMap(Map<String,Long> source){Map<String,Long> out=new LinkedHashMap<>();String prefix=profileKey()+"|";for(var e:source.entrySet())if(e.getKey().startsWith(prefix))out.put(e.getKey().substring(prefix.length()),e.getValue());return out;}
    private static void mark(){Minecraft mc=Minecraft.getInstance();lastGiftAt=System.currentTimeMillis();if(mc.player!=null)lastGiftLocation=mc.player.position();}
    private static boolean visible(){if(!tracking())return false;if(!cfg.giftProfitHoldingOnly)return true;if(holdingGift())return true;if(!cfg.giftProfitRecentLocation||lastGiftLocation==null)return false;Minecraft mc=Minecraft.getInstance();return mc.player!=null&&mc.player.position().distanceTo(lastGiftLocation)<=Math.clamp(cfg.giftProfitRecentDistance,1,64);}
    private static boolean tracking(){return cfg!=null&&cfg.enabled&&cfg.winterGiftTracker&&cfg.giftProfitTracker&&ConstellationClient.loc().onHypixel();}
    private static boolean holdingGift(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&(GIFT_IDS.contains(LyraTooltips.marketId(mc.player.getMainHandItem()))||GIFT_IDS.contains(LyraTooltips.marketId(mc.player.getOffhandItem())));}
    private static boolean rare(String rarity){Set<String> tiers=new HashSet<>();for(String s:cfg.giftProfitRareTiers.split(","))tiers.add(s.trim().toUpperCase(Locale.ROOT));return tiers.contains(rarity);}
    private static void rareAlert(String rarity,String reward){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.giftProfitRareTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(rarity+" Gift Reward").withColor(cfg.giftProfitColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(reward));}if(cfg.giftProfitRareSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.3f);}
    private static void warm(String item){String id=cfg.giftRewardIds.getOrDefault(item,guessId(item));if(!id.isBlank())PriceProvider.warm(id);}
    private static double rewardPrice(String item){Double custom=cfg.giftRewardCustomPrices.get(item);if(custom!=null&&custom>=0)return custom;String id=cfg.giftRewardIds.getOrDefault(item,guessId(item));return price(id,false);}
    private static double giftPrice(String type){return price(type.toUpperCase(Locale.ROOT)+"_GIFT",true);}
    private static double price(String id,boolean purchase){if(id==null||id.isBlank())return 0;double value=cfg.giftProfitPriceSource.equalsIgnoreCase("BUY")||purchase?PriceProvider.purchaseValue(id):PriceProvider.sellValue(id);if(value<=0)PriceProvider.warm(id);return Math.max(0,value);}
    private static String cleanReward(String raw){return raw.replaceFirst("^\\+","").replaceFirst("^\\d+x ","").trim();}
    private static long rewardAmount(String raw){Matcher m=Pattern.compile("^(\\d+)x ").matcher(raw);return m.find()?Math.max(1,number(m.group(1))):1;}
    private static String guessId(String name){Matcher potion=POTION.matcher(name);if(potion.matches())return"POTION_"+potion.group(1).toUpperCase(Locale.ROOT).replace(' ','_')+"_XP_BOOST;"+roman(potion.group(2));if(name.endsWith("Ice Rune"))return"ICE_RUNE;1";Matcher enchant=ENCHANT.matcher(name);if(enchant.matches()&&(name.contains("Scavenger")||name.contains("Looting")||name.contains("Luck")))return"ENCHANTMENT_"+enchant.group(1).toUpperCase(Locale.ROOT).replace(' ','_')+"_"+roman(enchant.group(2));return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static int roman(String s){int result=0,last=0;for(int i=s.length()-1;i>=0;i--){int n=switch(s.charAt(i)){case'I'->1;case'V'->5;case'X'->10;case'L'->50;case'C'->100;case'D'->500;case'M'->1000;default->0;};result+=n<last?-n:n;last=Math.max(last,n);}return result;}
    private static long number(String raw){try{return Long.parseLong(raw.replace(",",""));}catch(Exception e){return 0;}}
    private static String key(String value){return profileKey()+"|"+value;}
    private static String profileKey(){String p=LyraStorageValue.currentProfileKey();return p==null||p.isBlank()?"unknown":p.toLowerCase(Locale.ROOT);}
    private static String clean(String raw){String s=ChatFormatting.stripFormatting(raw);return s==null?"":s.trim();}
    private static String colorTier(String tier){return switch(tier){case"COMMON"->"§f";case"RARE"->"§9";case"SWEET"->"§e";default->"§c";};}
    private static String compact(long n){if(n<1000)return Long.toString(n);if(n<1_000_000)return String.format(Locale.ROOT,"%.1fk",n/1000d);return String.format(Locale.ROOT,"%.2fM",n/1_000_000d);}
    private static String coins(double n){String sign=n<0?"-":"";n=Math.abs(n);return sign+(n>=1_000_000?String.format(Locale.ROOT,"%.2fM",n/1_000_000):n>=1000?String.format(Locale.ROOT,"%.1fk",n/1000):String.format(Locale.ROOT,"%.0f",n));}
    private static void worldReset(){lastGiftLocation=null;lastGiftAt=0;}
    private static void resetSession(){SESSION_REWARDS.clear();SESSION_RARITIES.clear();SESSION_USED.clear();SESSION_XP.clear();sessionCoins=sessionStars=0;sessionStarted=System.currentTimeMillis();worldReset();}
    private static void normalize(){if(cfg.giftUsedCounts==null)cfg.giftUsedCounts=new LinkedHashMap<>();if(cfg.giftRewardCounts==null)cfg.giftRewardCounts=new LinkedHashMap<>();if(cfg.giftRarityCounts==null)cfg.giftRarityCounts=new LinkedHashMap<>();if(cfg.giftSkillXp==null)cfg.giftSkillXp=new LinkedHashMap<>();if(cfg.giftRewardIds==null)cfg.giftRewardIds=new LinkedHashMap<>();if(cfg.giftRewardCustomPrices==null)cfg.giftRewardCustomPrices=new LinkedHashMap<>();if(cfg.giftCoins==null)cfg.giftCoins=new LinkedHashMap<>();if(cfg.giftNorthStars==null)cfg.giftNorthStars=new LinkedHashMap<>();}
    private static void save(){if(cfg.giftProfitPersistent)ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gifttracker").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetProfile();return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetsession").executes(c->{resetSession();local("Gift session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Long>argument("amount",LongArgumentType.longArg(1)).executes(c->addGift(StringArgumentType.getString(c,"type"),LongArgumentType.getLong(c,"amount")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("id").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("reward",StringArgumentType.string()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("id",StringArgumentType.word()).executes(c->{cfg.giftRewardIds.put(StringArgumentType.getString(c,"reward"),StringArgumentType.getString(c,"id").toUpperCase(Locale.ROOT));save();return status();})))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("reward",StringArgumentType.string()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Double>argument("coins",DoubleArgumentType.doubleArg(0)).executes(c->{cfg.giftRewardCustomPrices.put(StringArgumentType.getString(c,"reward"),DoubleArgumentType.getDouble(c,"coins"));save();return status();})))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pricesource").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceSource(StringArgumentType.getString(c,"source"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recent").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(10,900)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(1,64)).executes(c->{cfg.giftProfitRecentSeconds=IntegerArgumentType.getInteger(c,"seconds");cfg.giftProfitRecentDistance=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,10)).executes(c->{cfg.giftProfitTopRows=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("raretiers").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("tiers",StringArgumentType.greedyString()).executes(c->{cfg.giftProfitRareTiers=StringArgumentType.getString(c,"tiers").toUpperCase(Locale.ROOT);save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int addGift(String raw,long amount){String type=raw.toUpperCase(Locale.ROOT);if(!Set.of("WHITE","GREEN","RED","PARTY").contains(type)){local("Gift type must be white, green, red, or party.");return 0;}SESSION_USED.merge(type,amount,Long::sum);if(cfg.giftProfitPersistent)cfg.giftUsedCounts.merge(key(type),amount,Long::sum);save();local("Added "+amount+" "+type.toLowerCase(Locale.ROOT)+" gifts used.");return 1;}
    private static int status(){Stats s=stats();local(s.rewards+" rewards, value "+coins(s.value)+", cost "+coins(s.cost)+", profit "+coins(s.profit)+". Manual gift usage is required.");return 1;}
    private static void resetProfile(){String prefix=profileKey()+"|";cfg.giftUsedCounts.keySet().removeIf(k->k.startsWith(prefix));cfg.giftRewardCounts.keySet().removeIf(k->k.startsWith(prefix));cfg.giftRarityCounts.keySet().removeIf(k->k.startsWith(prefix));cfg.giftSkillXp.keySet().removeIf(k->k.startsWith(prefix));cfg.giftCoins.remove(profileKey());cfg.giftNorthStars.remove(profileKey());resetSession();save();local("Current profile gift statistics reset.");}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.giftProfitTracker=v;case"hud"->cfg.giftProfitHud=v;case"holding"->cfg.giftProfitHoldingOnly=v;case"recent"->cfg.giftProfitRecentLocation=v;case"persistent"->cfg.giftProfitPersistent=v;case"value"->cfg.giftProfitShowValue=v;case"cost"->cfg.giftProfitShowCost=v;case"profit"->cfg.giftProfitShowProfit=v;case"hourly"->cfg.giftProfitShowHourly=v;case"rarities"->cfg.giftProfitShowRarities=v;case"stars"->cfg.giftProfitShowNorthStars=v;case"xp"->cfg.giftProfitShowSkillXp=v;case"top"->cfg.giftProfitShowTopRewards=v;case"title"->cfg.giftProfitRareTitle=v;case"sound"->cfg.giftProfitRareSound=v;default->{local("Unknown gift option.");return 0;}}save();return status();}
    private static int priceSource(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("SELL")&&!value.equals("BUY")){local("Price source must be sell or buy.");return 0;}cfg.giftProfitPriceSource=value;save();return status();}
    private static int color(String raw){try{long p=raw.startsWith("#")?Long.parseLong(raw.substring(1),16):Long.decode(raw);int c=(int)p;if((c>>>24)==0)c|=0xFF000000;cfg.giftProfitColor=c;save();return status();}catch(NumberFormatException e){local("Color must be #RRGGBB, #AARRGGBB, or a number.");return 0;}}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Gifts] §f"+text));}
}
