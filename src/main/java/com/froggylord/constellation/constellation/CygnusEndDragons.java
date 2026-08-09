package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.CygnusConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.core.StatStore;
import com.froggylord.constellation.data.TabList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// ported from SkyHanni (LGPL-2.1): features/combat/end/DragonFightAPI.kt
// ported from SkyHanni (LGPL-2.1): features/combat/end/DragonFeatures.kt
// ported from SkyHanni (LGPL-2.1): features/combat/end/DragonProfitTracker.kt
// ported from SkyHanni (LGPL-2.1): features/combat/end/ProfitPerDragon.kt
// ported from SkyHanni (LGPL-2.1): features/combat/end/DragonType.kt
public final class CygnusEndDragons {
    public enum DragonType {
        PROTECTOR(0xFFAAAAAA), OLD(0xFFFFFF55), UNSTABLE(0xFFAA00AA), YOUNG(0xFFFFFFFF),
        STRONG(0xFFFF5555), WISE(0xFF55FFFF), SUPERIOR(0xFFFFAA00), UNKNOWN(0xFFFFFFFF);
        final int color; DragonType(int color) { this.color = color; }
        String display() { String s=name().toLowerCase(Locale.ROOT); return Character.toUpperCase(s.charAt(0))+s.substring(1)+" Dragon"; }
        String fragment() { return name()+"_FRAGMENT"; }
        static DragonType parse(String raw) { try { return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(" DRAGON", "")); } catch (Exception ignored) { return UNKNOWN; } }
    }
    private enum EndType { DRAGON, GOLEM }
    private record Drop(String id,long amount,long at) {}

    private static final Pattern SPAWN=Pattern.compile("^The (?<type>Protector|Old|Unstable|Young|Strong|Wise|Superior) Dragon has spawned!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern EYE=Pattern.compile("^You placed a Summoning Eye!(?: Brace yourselves!)? \\((?:\\d|8)/8\\)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern RECOVER=Pattern.compile("^You recovered a Summoning Eye!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAGON_DOWN=Pattern.compile("^(?<type>PROTECTOR|OLD|UNSTABLE|YOUNG|STRONG|WISE|SUPERIOR) DRAGON DOWN!$");
    private static final Pattern GOLEM_DOWN=Pattern.compile("^ENDSTONE PROTECTOR DOWN!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION=Pattern.compile("^Your Damage: (?<damage>[\\d,.]+)(?: \\(NEW RECORD!\\))? \\(Position #(?<position>\\d+)\\)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADER=Pattern.compile("^(?<position>\\d+)(?:st|nd|rd|th) Damager - (?:\\[[^]]+] )?(?<name>.+?) - (?<damage>[\\d,.]+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ZEALOTS=Pattern.compile("^Zealots Contributed: (?<amount>\\d+)/100$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_HP=Pattern.compile("^Dragon HP: (?<hp>[\\d,.]+).*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_DAMAGE=Pattern.compile("^Your Damage: (?<damage>[\\d,.]+).*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern TAB_DAMAGE=Pattern.compile("^(?<name>.+?): (?<damage>[\\d.]+[kKmMbB]?).*$");

    // tracked ids copied from the maintained DragonProfitTrackerItems constant
    private static final Set<String> TRACKED=new LinkedHashSet<>();
    static {
        unique(450,"ENDER_DRAGON;3","ENDER_DRAGON;4","ASPECT_OF_THE_DRAGON");
        unique(295,"UNSTABLE_DRAGON_HELMET","OLD_DRAGON_HELMET","PROTECTOR_DRAGON_HELMET","YOUNG_DRAGON_HELMET","STRONG_DRAGON_HELMET","WISE_DRAGON_HELMET","SUPERIOR_DRAGON_HELMET","DRAGON_SCALE");
        unique(410,"UNSTABLE_DRAGON_CHESTPLATE","OLD_DRAGON_CHESTPLATE","PROTECTOR_DRAGON_CHESTPLATE","YOUNG_DRAGON_CHESTPLATE","STRONG_DRAGON_CHESTPLATE","WISE_DRAGON_CHESTPLATE","SUPERIOR_DRAGON_CHESTPLATE");
        unique(360,"UNSTABLE_DRAGON_LEGGINGS","OLD_DRAGON_LEGGINGS","PROTECTOR_DRAGON_LEGGINGS","YOUNG_DRAGON_LEGGINGS","STRONG_DRAGON_LEGGINGS","WISE_DRAGON_LEGGINGS","SUPERIOR_DRAGON_LEGGINGS");
        unique(290,"UNSTABLE_DRAGON_BOOTS","OLD_DRAGON_BOOTS","PROTECTOR_DRAGON_BOOTS","YOUNG_DRAGON_BOOTS","STRONG_DRAGON_BOOTS","WISE_DRAGON_BOOTS","SUPERIOR_DRAGON_BOOTS");
        unique(452,"DRAGON_HORN"); unique(451,"DRAGON_CLAW"); unique(250,"DRAGON_NEST_TRAVEL_SCROLL");
        unique(-1,"ATTRIBUTE_SHARD_DRAGON_ESSENCE;1","DYE_PEARLESCENT");
        TRACKED.addAll(List.of("ENDER_PEARL","ENCHANTED_ENDER_PEARL","ESSENCE_DRAGON"));
        for(DragonType type:DragonType.values())if(type!=DragonType.UNKNOWN)TRACKED.add(type.fragment());
    }

    private static CygnusConfig cfg;
    private static boolean initialized,dragonSpawned,eggSpawned=true;
    private static DragonType currentType=DragonType.UNKNOWN,lastType=DragonType.UNKNOWN;
    private static EndType endType;
    private static int eyes,fightEyes,currentPlace,endPlace,lastPlace;
    private static double currentDamage,currentTopDamage,endDamage,endTopDamage,lastWeight;
    private static long currentHp,spawnAt,lootUntil,sessionStarted,activeMillis,lastTick,lastActivity,diagnosticUntil;
    private static Object levelIdentity;
    private static String profileIdentity="";
    private static final Map<DragonType,Long> SESSION_KILLS=new EnumMap<>(DragonType.class);
    private static final Map<String,Long> SESSION_LOOT=new LinkedHashMap<>(),INVENTORY=new LinkedHashMap<>();
    private static final Deque<Drop> RECENT=new ArrayDeque<>();

    private CygnusEndDragons() {}
    private static void unique(int weight,String... ids){TRACKED.addAll(List.of(ids));}

    public static void init(CygnusConfig config){cfg=config;if(initialized)return;initialized=true;ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(clean(message.getString()));return true;});ConstellationClient.tick().every(5,"cygnus-end-dragons",CygnusEndDragons::tick);}

    private static void tick(){Minecraft mc=Minecraft.getInstance();String profile=profile();if(mc.level!=levelIdentity||!profile.equals(profileIdentity)){levelIdentity=mc.level;profileIdentity=profile;resetFight();resetSession();INVENTORY.clear();}if(!active()||mc.player==null){lastTick=0;return;}long now=System.currentTimeMillis();readScoreboard();readTab();Map<String,Long> inventory=inventory(mc);if(cfg.endDragonProfitTracker&&!INVENTORY.isEmpty()&&now<=lootUntil)for(var e:inventory.entrySet()){long delta=e.getValue()-INVENTORY.getOrDefault(e.getKey(),0L);if(delta>0)addLoot(e.getKey(),delta);}INVENTORY.clear();INVENTORY.putAll(inventory);if(lastTick>0&&now-lastActivity<=Math.clamp(cfg.endDragonAfkSeconds,5,600)*1000L)activeMillis+=Math.min(1000,now-lastTick);lastTick=now;RECENT.removeIf(d->now-d.at>Math.clamp(cfg.endDragonRecentSeconds,10,3600)*1000L);}

    private static void chat(String line){if(!configured()||!allowedWorld())return;Matcher m=EYE.matcher(line);if(m.matches()){eyes++;return;}if(RECOVER.matcher(line).matches()){eyes=Math.max(0,eyes-1);return;}m=SPAWN.matcher(line);if(m.matches()){currentType=DragonType.parse(m.group("type"));dragonSpawned=true;eggSpawned=false;fightEyes=eyes;spawnAt=lastActivity=System.currentTimeMillis();if(cfg.endDragonProfitTracker)addEyes(fightEyes);if(currentType==DragonType.SUPERIOR&&cfg.endDragonSuperiorNotify)superior();return;}if(line.equalsIgnoreCase("The Dragon Egg has spawned!")){eggSpawned=true;return;}m=DRAGON_DOWN.matcher(line);if(m.matches()){endType=EndType.DRAGON;lastType=DragonType.parse(m.group("type"));lootUntil=System.currentTimeMillis()+Math.clamp(cfg.endDragonLootWindowSeconds,5,60)*1000L;INVENTORY.clear();Minecraft mc=Minecraft.getInstance();if(mc.player!=null)INVENTORY.putAll(inventory(mc));return;}if(GOLEM_DOWN.matcher(line).matches()){endType=EndType.GOLEM;return;}m=LEADER.matcher(line);if(m.matches()&&endType!=null&&Integer.parseInt(m.group("position"))==1){endTopDamage=number(m.group("damage"));return;}m=POSITION.matcher(line);if(m.matches()&&endType!=null){endDamage=number(m.group("damage"));endPlace=Integer.parseInt(m.group("position"));if(endType==EndType.DRAGON)finishDragon();return;}m=ZEALOTS.matcher(line);if(m.matches()&&endType==EndType.GOLEM){double weight=protectorWeight(Integer.parseInt(m.group("amount")),endPlace,endTopDamage,endDamage);if(cfg.endDragonProtectorWeightChat)local("Endstone Protector weight: "+whole(weight)+".");endType=null;endTopDamage=endDamage=0;endPlace=0;}}

    private static void finishDragon(){lastWeight=dragonWeight(fightEyes,endPlace,endTopDamage,endDamage);lastPlace=endPlace;if(cfg.endDragonWeightChat)local(lastType.display()+" weight: "+whole(lastWeight)+" (place #"+lastPlace+", "+fightEyes+" eye"+(fightEyes==1?"":"s")+").");if(cfg.endDragonProfitTracker&&endDamage>0&&(fightEyes>0||cfg.endDragonCountLeeched))addKill(lastType);currentType=DragonType.UNKNOWN;dragonSpawned=false;eyes=fightEyes=0;currentDamage=currentTopDamage=0;currentPlace=0;currentHp=0;endType=null;endTopDamage=endDamage=0;endPlace=0;lastActivity=System.currentTimeMillis();}

    private static void readScoreboard(){if(!dragonSpawned)return;for(String raw:ConstellationClient.loc().getSidebarLines()){String line=clean(raw);Matcher hp=SCORE_HP.matcher(line);if(hp.matches())currentHp=(long)number(hp.group("hp"));Matcher damage=SCORE_DAMAGE.matcher(line);if(damage.matches())currentDamage=number(damage.group("damage"));}}
    private static void readTab(){if(!dragonSpawned)return;Minecraft mc=Minecraft.getInstance();String own=mc.player==null?"":mc.player.getGameProfile().name();Set<String> players=new LinkedHashSet<>();if(mc.getConnection()!=null)for(PlayerInfo info:mc.getConnection().getOnlinePlayers())players.add(info.getProfile().name().toLowerCase(Locale.ROOT));int rank=0;double top=0;for(String raw:TabList.lines()){Matcher m=TAB_DAMAGE.matcher(clean(raw));if(!m.matches())continue;String name=clean(m.group("name"));if(!players.contains(name.toLowerCase(Locale.ROOT)))continue;double damage=shortNumber(m.group("damage"));if(damage<=0)continue;rank++;if(top==0)top=damage;if(name.equalsIgnoreCase(own))currentPlace=rank;}currentTopDamage=top;}

    // exact weight model from the licensed reference
    private static int placementWeight(int place){return switch(place){case-1->10;case 1->200;case 2->175;case 3->150;case 4->125;case 5->110;case 6,7,8->100;case 9,10->90;case 11,12->80;default->70;};}
    private static double dragonWeight(int placed,int place,double first,double yours){return placementWeight(yours==0?-1:place)+100*(placed+yours/(first==0?1:first));}
    private static double protectorWeight(int zealots,int place,double first,double yours){return placementWeight(yours==0?-1:place)+50*yours/(first==0?1:first)+Math.min(100,zealots);}

    private static void addEyes(int amount){if(amount<=0)return;sessionStarted=sessionStarted==0?System.currentTimeMillis():sessionStarted;sessionEyes+=amount;add("eyes",amount);lastActivity=System.currentTimeMillis();}
    private static long sessionEyes;
    private static void addKill(DragonType type){SESSION_KILLS.merge(type,1L,Long::sum);add("kills."+type.name(),1);sessionStarted=sessionStarted==0?System.currentTimeMillis():sessionStarted;lastActivity=System.currentTimeMillis();}
    private static void addLoot(String raw,long amount){String id=normalize(raw);if(amount<=0||!TRACKED.contains(id))return;PriceProvider.warm(id);SESSION_LOOT.merge(id,amount,Long::sum);add("loot."+id,amount);RECENT.addFirst(new Drop(id,amount,System.currentTimeMillis()));while(RECENT.size()>Math.clamp(cfg.endDragonItemRows,1,20))RECENT.removeLast();lastActivity=System.currentTimeMillis();double value=price(id)*amount;if(cfg.endDragonRareDropAlerts&&(value>=Math.max(0,cfg.endDragonMinimumChatMillions)*1_000_000d||value>=Math.max(0,cfg.endDragonMinimumTitleMillions)*1_000_000d))dropAlert(id,amount,value);}

    public static String weightHud(){if(!active()||!dragonSpawned)return diagnostic()?"Superior Dragon | Current: 420 | Eyes: 2 | Place: #3 | Damage: 18.4% | HP: 8.2m":null;int place=currentPlace>0?currentPlace:(currentDamage>0?6:-1);double weight=dragonWeight(fightEyes,place,currentTopDamage,currentDamage),ratio=currentDamage/(currentTopDamage==0?1:currentTopDamage)*100;List<String> rows=new ArrayList<>();if(cfg.endDragonWeightShowType)rows.add(currentType.display());rows.add("Current: "+decimal(weight));if(cfg.endDragonWeightShowEyes)rows.add("Eyes: "+fightEyes);if(cfg.endDragonWeightShowPlace)rows.add("Place: "+(currentPlace>0?"#"+currentPlace:currentDamage>0?"assumed #6":"none"));if(cfg.endDragonWeightShowDamage)rows.add("Damage: "+decimal(ratio)+"%");if(cfg.endDragonWeightShowHp&&currentHp>0)rows.add("HP: "+compact(currentHp));return String.join(" | ",rows);}
    public static String profitHud(){if(!active())return diagnostic()?"Dragons: 12 | Eyes: 7 | Cost: -4.20m | Loot: 8.75m | Profit: +4.55m | Profit/hour: +12.4m | Uptime: 22m 04s":null;boolean life=!cfg.endDragonSessionOnly;Map<String,Long> loot=life?lifetimeLoot():SESSION_LOOT;long kills=life?lifetimeKills():SESSION_KILLS.values().stream().mapToLong(Long::longValue).sum(),used=life?get("eyes",0):sessionEyes;double lootValue=lootValue(loot),cost=price("SUMMONING_EYE")*used,profit=lootValue-cost;List<String> rows=new ArrayList<>();if(cfg.endDragonShowKills)rows.add("Dragons: "+num(kills));if(cfg.endDragonShowEyes)rows.add("Eyes: "+num(used));if(cfg.endDragonShowEyeCost)rows.add("Cost: -"+coins(cost));if(cfg.endDragonShowItems)rows.add("Loot: "+itemText(loot));if(cfg.endDragonShowProfit)rows.add("Profit: "+signed(profit));if(cfg.endDragonShowProfitPerHour)rows.add("Profit/hour: "+signed(activeMillis==0?0:profit*3_600_000/activeMillis));if(cfg.endDragonShowUptime)rows.add("Uptime: "+duration(activeMillis));if(cfg.endDragonShowRecentDrops&&!RECENT.isEmpty())rows.add("Recent: "+RECENT.stream().limit(3).map(d->display(d.id)+" x"+d.amount).collect(Collectors.joining(", ")));return rows.isEmpty()?null:String.join(" | ",rows);}

    private static Map<String,Long> inventory(Minecraft mc){Map<String,Long> out=new LinkedHashMap<>();for(ItemStack stack:mc.player.getInventory()){String id=normalize(LyraTooltips.marketId(stack));if(TRACKED.contains(id))out.merge(id,(long)stack.getCount(),Long::sum);}return out;}
    private static String normalize(String id){if(id==null)return"";String value=id.toUpperCase(Locale.ROOT);if(value.equals("ENDER_DRAGON_PET_EPIC"))return"ENDER_DRAGON;3";if(value.equals("ENDER_DRAGON_PET_LEGENDARY"))return"ENDER_DRAGON;4";if(value.equals("ASPECT_OF_THE_DRAGONS"))return"ASPECT_OF_THE_DRAGON";return value;}
    private static Map<String,Long> lifetimeLoot(){Map<String,Long> out=new LinkedHashMap<>();for(String id:TRACKED){long value=get("loot."+id,0);if(value>0)out.put(id,value);}return out;}
    private static long lifetimeKills(){long total=0;for(DragonType type:DragonType.values())total+=get("kills."+type.name(),0);return total;}
    private static double lootValue(Map<String,Long> loot){double out=0;for(var e:loot.entrySet())out+=price(e.getKey())*e.getValue();return out;}
    private static double price(String id){return cfg.endDragonPriceSource.equalsIgnoreCase("BUY")?PriceProvider.purchaseValue(id):PriceProvider.sellValue(id);}
    private static String itemText(Map<String,Long> loot){if(loot.isEmpty())return"none";return loot.entrySet().stream().sorted((a,b)->Double.compare(price(b.getKey())*b.getValue(),price(a.getKey())*a.getValue())).limit(Math.clamp(cfg.endDragonItemRows,1,20)).map(e->display(e.getKey())+" "+num(e.getValue())).collect(Collectors.joining(", "));}

    private static void superior(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.endDragonSuperiorTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTimes(0,30,10);mc.gui.hud.setTitle(Component.literal("Superior Dragon spawned!").withColor(0xFFAA00));}if(cfg.endDragonSuperiorChat)local("Superior Dragon spawned!");if(cfg.endDragonSuperiorSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.9f,1.2f);}
    private static void dropAlert(String id,long amount,double value){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text=display(id)+" x"+amount+" ("+coins(value)+")";if(cfg.endDragonRareDropTitle&&value>=Math.max(0,cfg.endDragonMinimumTitleMillions)*1_000_000d){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(text).withColor(0xFFAA00));}if(cfg.endDragonRareDropChat&&value>=Math.max(0,cfg.endDragonMinimumChatMillions)*1_000_000d)local("Dragon loot: "+text+".");if(cfg.endDragonRareDropSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),1,1.3f);}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dragontracker").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Session reset. Lifetime totals were kept.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").executes(c->test())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceMode(StringArgumentType.getString(c,"source"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,20)).executes(c->{cfg.endDragonItemRows=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("afk").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(5,600)).executes(c->{cfg.endDragonAfkSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Profit tracker "+on(cfg.endDragonProfitTracker)+", weight HUD "+on(cfg.endDragonWeightHud)+", weight chat "+on(cfg.endDragonWeightChat)+", session-only "+on(cfg.endDragonSessionOnly)+", price "+cfg.endDragonPriceSource+"; "+(lastType==DragonType.UNKNOWN?"no completed dragon this session":lastType.display()+" #"+lastPlace+" weight "+whole(lastWeight))+".");return 1;}
    private static int test(){diagnosticUntil=System.currentTimeMillis()+15_000;lastType=DragonType.SUPERIOR;lastPlace=3;lastWeight=420;superior();local("Dragon HUD test shown for 15 seconds.");return 1;}
    private static int priceMode(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("SELL")&&!value.equals("BUY")){local("Price source must be sell or buy.");return 0;}cfg.endDragonPriceSource=value;save();return status();}
    private static int option(String name,String raw){Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.endDragonSuite=value;case"profit"->cfg.endDragonProfitTracker=value;case"profithud"->cfg.endDragonProfitHud=value;case"weight"->cfg.endDragonWeightHud=value;case"weightchat"->cfg.endDragonWeightChat=value;case"protector"->cfg.endDragonProtectorWeightChat=value;case"superior"->cfg.endDragonSuperiorNotify=value;case"leech"->cfg.endDragonCountLeeched=value;case"session"->cfg.endDragonSessionOnly=value;case"items"->cfg.endDragonShowItems=value;case"recent"->cfg.endDragonShowRecentDrops=value;case"alerts"->cfg.endDragonRareDropAlerts=value;case"local"->cfg.endDragonLocalWorlds=value;default->{local("Unknown option.");return 0;}}save();return status();}

    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.endDragonSuite;}
    private static boolean active(){return configured()&&allowedWorld()&&(ConstellationClient.loc().area()==SkyblockArea.THE_END||diagnostic());}
    private static boolean allowedWorld(){return ConstellationClient.loc().onHypixel()||cfg.endDragonLocalWorlds||diagnostic();}
    private static boolean diagnostic(){return System.currentTimeMillis()<diagnosticUntil;}
    private static void resetFight(){dragonSpawned=false;eggSpawned=true;currentType=DragonType.UNKNOWN;endType=null;eyes=fightEyes=currentPlace=endPlace=0;currentDamage=currentTopDamage=endDamage=endTopDamage=lastWeight=0;currentHp=spawnAt=lootUntil=0;INVENTORY.clear();}
    private static void resetSession(){SESSION_KILLS.clear();SESSION_LOOT.clear();RECENT.clear();sessionEyes=sessionStarted=activeMillis=lastTick=lastActivity=0;}
    private static String profile(){return cfg!=null&&cfg.endDragonPersistentProfiles?LyraStorageValue.currentProfileKey():"global";}
    private static String stat(String suffix){return"cygnus.dragon."+(profileIdentity.isBlank()?profile():profileIdentity)+"."+suffix;}
    private static long get(String suffix,long fallback){return StatStore.getLong(stat(suffix),fallback);}
    private static long add(String suffix,long amount){return StatStore.add(stat(suffix),amount);}
    private static String clean(String value){String plain=ChatFormatting.stripFormatting(value);return plain==null?"":plain.trim().replaceAll("\\s+"," ").replaceFirst("^☬\\s*","");}
    private static double number(String raw){try{return Double.parseDouble(raw.replace(",",""));}catch(Exception ignored){return 0;}}
    private static double shortNumber(String raw){String value=raw.trim().toLowerCase(Locale.ROOT);double factor=value.endsWith("b")?1e9:value.endsWith("m")?1e6:value.endsWith("k")?1e3:1;if(factor!=1)value=value.substring(0,value.length()-1);return number(value)*factor;}
    private static String whole(double value){return String.format(Locale.ROOT,"%,.0f",value);}
    private static String decimal(double value){return String.format(Locale.ROOT,"%,.1f",value);}
    private static String num(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String compact(double value){double abs=Math.abs(value);if(abs>=1e9)return String.format(Locale.ROOT,"%.2fb",value/1e9);if(abs>=1e6)return String.format(Locale.ROOT,"%.2fm",value/1e6);if(abs>=1e3)return String.format(Locale.ROOT,"%.1fk",value/1e3);return whole(value);}
    private static String coins(double value){return compact(Math.max(0,value));}
    private static String signed(double value){return(value>=0?"+":"-")+coins(Math.abs(value));}
    private static String duration(long millis){long s=Math.max(0,millis/1000),h=s/3600;s%=3600;long m=s/60;s%=60;return h>0?h+"h "+m+"m":m>0?m+"m "+s+"s":s+"s";}
    private static String display(String id){if(id.equals("ENDER_DRAGON;3"))return"Epic Ender Dragon Pet";if(id.equals("ENDER_DRAGON;4"))return"Legendary Ender Dragon Pet";String value=id.toLowerCase(Locale.ROOT).replace('_',' ').replace(';',' ');StringBuilder out=new StringBuilder();for(String p:value.split(" ")){if(p.isBlank())continue;if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return out.toString();}
    private static Boolean bool(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Dragon] §f"+text));}
}
