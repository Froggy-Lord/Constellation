package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.AquilaConfig;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/combat/ghosttracker/GhostTracker.kt
// ported from SkyHanni (LGPL-3.0-or-later): constants/GhostDrops.json
public final class AquilaGhostTracker {
    private record Drop(String id,String name,long amount,long at){}
    private record PendingGem(long amount,long at){}
    private static final Pattern DROP=Pattern.compile("^RARE DROP! (?<item>.+) \\(\\+(?<mf>\\d+)%? .*?Magic Find\\)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern COMBO=Pattern.compile("^Your Kill Combo has expired! You reached a (?<kills>[\\d,.]+) Kill Combo!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern BESTIARY=Pattern.compile("^Ghost (?:\\d+|[XVI]+): (?<kills>[\\d,.]+)/[\\d,.]+$",Pattern.CASE_INSENSITIVE);
    private static final Pattern BESTIARY_MAX=Pattern.compile("^Ghost (?:\\d+|[XVI]+): MAX$",Pattern.CASE_INSENSITIVE);
    private static final Pattern COMBAT_XP=Pattern.compile("\\+(?<xp>[\\d,.]+) Combat(?: \\(|$)",Pattern.CASE_INSENSITIVE);
    private static final Map<String,String> DROP_IDS=Map.of("sorrow","SORROW","plasma","PLASMA","volta","VOLTA","ghostly boots","GHOST_BOOTS","ghost boots","GHOST_BOOTS");
    private static final Set<String> GEM_IDS=Set.of("FLAWED_AMETHYST_GEM","FLAWED_AMBER_GEM","FLAWED_JADE_GEM","FLAWED_SAPPHIRE_GEM","FLAWED_TOPAZ_GEM","ROUGH_AMETHYST_GEM","ROUGH_AMBER_GEM","ROUGH_JADE_GEM","ROUGH_SAPPHIRE_GEM","ROUGH_TOPAZ_GEM");
    private static final Deque<Drop> RECENT=new ArrayDeque<>();private static final Map<String,Long> SESSION_DROPS=new LinkedHashMap<>(),INVENTORY=new LinkedHashMap<>();private static final Map<String,PendingGem> PENDING_GEMS=new LinkedHashMap<>();
    private static AquilaConfig cfg;private static long sessionKills,sessionSorrow,sessionCombo,sessionXp,sessionMf,sessionMfDrops,sessionCoins,sessionStarted,activeMillis,lastTick,lastActivity,lastKillAt,lastBestiary,warnedAt;private static Object levelIdentity;private static String profileIdentity="";
    private AquilaGhostTracker(){}
    public static void init(AquilaConfig config){cfg=config;ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(active())chat(clean(message.getString()),overlay);return true;});ConstellationClient.tick().every(20,"aquila-ghost-tracker",AquilaGhostTracker::tick);}
    private static void tick(){Minecraft mc=Minecraft.getInstance();String currentProfile=profile();if(mc.level!=levelIdentity||!currentProfile.equals(profileIdentity)){levelIdentity=mc.level;profileIdentity=currentProfile;resetSession();lastBestiary=get("bestiary",0);}if(!active()||mc.player==null){lastTick=0;return;}long now=System.currentTimeMillis();parseBestiary(now);if(cfg.ghostTrackerTrackInventoryGems)inventory(mc,now);long purse=LyraEconomy.currentPurse;if(lastTick>0&&now-lastActivity<=Math.clamp(cfg.ghostTrackerAfkSeconds,5,600)*1000L)activeMillis+=Math.min(2000,now-lastTick);if(lastKillAt>0&&now-lastKillAt<=2500&&purse>0){long previous=get("lastPurse",purse),delta=purse-previous;if(delta>=200&&delta<=15_000){sessionCoins+=delta;add("coins",delta);}}if(purse>0)put("lastPurse",purse);lastTick=now;cleanRecent(now);}
    private static void parseBestiary(long now){boolean found=false;long kills=-1;for(String raw:TabList.lines()){String line=clean(raw);if(BESTIARY_MAX.matcher(line).matches()){kills=Math.clamp(cfg.ghostTrackerBestiaryMax,1,1_000_000);found=true;break;}Matcher m=BESTIARY.matcher(line);if(m.matches()){kills=parse(m.group("kills"));found=true;break;}}if(!found){if(cfg.ghostTrackerWarnMissingBestiary&&now-warnedAt>60_000){warnedAt=now;missingWarning();}return;}long stored=get("bestiary",0);if(stored==0||kills<stored||kills-stored>50){put("bestiary",kills);lastBestiary=kills;return;}long delta=kills-stored;if(delta>0){sessionKills+=delta;sessionSorrow+=delta;lastKillAt=lastActivity=now;if(sessionStarted==0)sessionStarted=now;add("kills",delta);add("sorrow",delta);put("bestiary",kills);flushPendingGems(now);}lastBestiary=kills;}
    private static void chat(String line,boolean overlay){Matcher xp=COMBAT_XP.matcher(line);if(xp.find()){long amount=parse(xp.group("xp"));if(amount<=10_000){sessionXp+=amount;add("xp",amount);lastActivity=System.currentTimeMillis();}return;}if(overlay)return;Matcher drop=DROP.matcher(line);if(drop.matches()){String name=drop.group("item").trim(),id=DROP_IDS.get(name.toLowerCase(Locale.ROOT));if(id==null)return;long mf=parse(drop.group("mf"));addDrop(id,name,1);sessionMf+=mf;sessionMfDrops++;add("mf",mf);add("mfDrops",1);if(id.equals("SORROW")){sessionSorrow=0;put("sorrow",0);}return;}Matcher combo=COMBO.matcher(line);if(combo.matches()){long value=parse(combo.group("kills"));sessionCombo=Math.max(sessionCombo,value);put("combo",Math.max(get("combo",0),value));return;}if(line.equalsIgnoreCase("The ghost's death materialized 1,000,000 coins from the mists!")){sessionCoins+=1_000_000;add("coins",1_000_000);}}
    private static void inventory(Minecraft mc,long now){Map<String,Long> current=new LinkedHashMap<>();for(ItemStack stack:mc.player.getInventory()){String id=id(stack);if(GEM_IDS.contains(id))current.merge(id,(long)stack.getCount(),Long::sum);}if(!INVENTORY.isEmpty())for(var entry:current.entrySet()){long delta=entry.getValue()-INVENTORY.getOrDefault(entry.getKey(),0L);if(delta<=0)continue;if(now-lastKillAt<=3000)addDrop(entry.getKey(),display(entry.getKey()),delta);else{PendingGem old=PENDING_GEMS.get(entry.getKey());PENDING_GEMS.put(entry.getKey(),new PendingGem(delta+(old!=null&&now-old.at<=3000?old.amount:0),now));}}INVENTORY.clear();INVENTORY.putAll(current);PENDING_GEMS.entrySet().removeIf(e->now-e.getValue().at>3000);}
    private static void flushPendingGems(long now){for(var entry:new ArrayList<>(PENDING_GEMS.entrySet()))if(now-entry.getValue().at<=3000)addDrop(entry.getKey(),display(entry.getKey()),entry.getValue().amount);PENDING_GEMS.clear();}
    private static void addDrop(String id,String name,long amount){SESSION_DROPS.merge(id,amount,Long::sum);add("drop."+id,amount);Drop drop=new Drop(id,name,amount,System.currentTimeMillis());RECENT.addFirst(drop);while(RECENT.size()>Math.clamp(cfg.ghostTrackerRecentDrops,1,12))RECENT.removeLast();lastActivity=drop.at;if(cfg.ghostTrackerDropAlerts&&PriceProvider.value(id)*amount>=Math.max(0,cfg.ghostTrackerMinimumAlertValue))alert(name+" x"+amount);}
    public static String hudText(){if(!active())return null;Map<String,String> rows=new LinkedHashMap<>();boolean life=cfg.ghostTrackerLifetime;long kills=life?get("kills",0):sessionKills,sorrow=life?get("sorrow",0):sessionSorrow,combo=life?get("combo",0):sessionCombo,xp=life?get("xp",0):sessionXp,mf=life?get("mf",0):sessionMf,mfDrops=life?get("mfDrops",0):sessionMfDrops,coins=life?get("coins",0):sessionCoins;double profit=coins;Map<String,Long> drops=life?lifetimeDrops():SESSION_DROPS;for(var e:drops.entrySet())profit+=PriceProvider.value(e.getKey())*e.getValue();rows.put("kills","Kills: "+num(kills));rows.put("sorrow","Ghosts since Sorrow: "+num(sorrow));rows.put("combo","Max combo: "+num(combo));rows.put("xp","Combat XP: "+num(xp));rows.put("mf","Average MF: "+String.format(Locale.ROOT,"%.1f",mfDrops==0?0:(double)mf/mfDrops));rows.put("bestiary","Bestiary: "+(lastBestiary>=cfg.ghostTrackerBestiaryMax?"MAX":num(lastBestiary)));rows.put("drops","Drops: "+dropText(drops));rows.put("profit","Profit: "+coins(profit));rows.put("rate","Profit/hour: "+coins(activeMillis==0?0:profit*3_600_000/activeMillis));rows.put("uptime","Uptime: "+duration(activeMillis));StringBuilder out=new StringBuilder();for(String key:cfg.ghostTrackerOrder.split(",")){key=key.trim().toLowerCase(Locale.ROOT);if(!enabledRow(key))continue;String row=rows.get(key);if(row!=null){if(!out.isEmpty())out.append(" | ");out.append(row);}}return out.isEmpty()?null:out.toString();}
    private static boolean enabledRow(String key){return switch(key){case"kills"->cfg.ghostTrackerShowKills;case"sorrow"->cfg.ghostTrackerShowSorrowDistance;case"combo"->cfg.ghostTrackerShowMaxCombo;case"xp"->cfg.ghostTrackerShowCombatXp;case"mf"->cfg.ghostTrackerShowAverageMagicFind;case"bestiary"->cfg.ghostTrackerShowBestiary;case"drops"->cfg.ghostTrackerShowDrops;case"profit"->cfg.ghostTrackerShowProfit;case"rate"->cfg.ghostTrackerShowProfitPerHour;case"uptime"->cfg.ghostTrackerShowUptime;default->false;};}
    private static Map<String,Long> lifetimeDrops(){Map<String,Long> out=new LinkedHashMap<>();for(String id:DROP_IDS.values())out.put(id,get("drop."+id,0));for(String id:GEM_IDS)out.put(id,get("drop."+id,0));out.values().removeIf(v->v==0);return out;}
    private static String dropText(Map<String,Long> drops){if(drops.isEmpty())return"none";return drops.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(Math.clamp(cfg.ghostTrackerRecentDrops,1,12)).map(e->display(e.getKey())+" "+num(e.getValue())).collect(java.util.stream.Collectors.joining(", "));}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ghosttracker").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Session reset. Lifetime totals were kept.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("testdrop").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("item",StringArgumentType.word()).executes(c->{String raw=StringArgumentType.getString(c,"item").toUpperCase(Locale.ROOT);String id=raw.equals("SORROW")?"SORROW":raw.equals("PLASMA")?"PLASMA":raw.equals("VOLTA")?"VOLTA":"GHOST_BOOTS";addDrop(id,display(id),1);return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("afk").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(5,600)).executes(c->{cfg.ghostTrackerAfkSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))));}
    private static int status(){local("Session "+sessionKills+" kills, "+sessionSorrow+" since Sorrow, "+coins(sessionCoins)+" direct coins, "+duration(activeMillis)+" active.");return 1;}
    private static int option(String name,String raw){Boolean v=bool(raw);if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"lifetime"->cfg.ghostTrackerLifetime=v;case"drops"->cfg.ghostTrackerShowDrops=v;case"profit"->cfg.ghostTrackerShowProfit=v;case"rate"->cfg.ghostTrackerShowProfitPerHour=v;case"gems"->cfg.ghostTrackerTrackInventoryGems=v;case"alerts"->cfg.ghostTrackerDropAlerts=v;case"warning"->cfg.ghostTrackerWarnMissingBestiary=v;case"mist"->cfg.ghostTrackerOnlyInMist=v;default->{local("Unknown option.");return 0;}}save();return status();}
    private static void missingWarning(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;mc.player.sendSystemMessage(Component.literal("§3[Ghost Tracker] §cGhost Bestiary was not found in the tab widget. ").append(Component.literal("§e[Open widgets]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/widget")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Run /widget"))))));}
    private static void alert(String name){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.ghostTrackerDropAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name));}if(cfg.ghostTrackerDropAlertChat)local("Valuable drop: "+name+".");if(cfg.ghostTrackerDropAlertSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),1,1.2f);}
    private static boolean active(){if(cfg==null||!cfg.enabled||!cfg.ghostTracker||ConstellationClient.loc().area()!=SkyblockArea.DWARVEN_MINES)return false;if(!cfg.ghostTrackerOnlyInMist)return true;for(String line:ConstellationClient.loc().getSidebarLines())if(clean(line).contains("The Mist"))return true;for(String line:TabList.lines())if(clean(line).contains("The Mist"))return true;return false;}
    private static void resetSession(){sessionKills=sessionSorrow=sessionCombo=sessionXp=sessionMf=sessionMfDrops=sessionCoins=sessionStarted=activeMillis=lastTick=lastActivity=lastKillAt=0;RECENT.clear();SESSION_DROPS.clear();INVENTORY.clear();PENDING_GEMS.clear();}
    private static void cleanRecent(long now){RECENT.removeIf(d->now-d.at>300_000);}
    private static String id(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static String display(String id){String s=id.toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder out=new StringBuilder();for(String p:s.split(" ")){if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return out.toString().replace("Gem","Gemstone");}
    private static String num(long value){if(!cfg.ghostTrackerCompactNumbers)return String.format(Locale.ROOT,"%,d",value);if(Math.abs(value)>=1_000_000)return String.format(Locale.ROOT,"%.1fM",value/1_000_000.0);if(Math.abs(value)>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1000.0);return Long.toString(value);}
    private static String coins(double value){if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fM",value/1_000_000);if(value>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1000);return String.format(Locale.ROOT,"%.0f",value);}
    private static String duration(long ms){long s=ms/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60);}
    private static long parse(String value){try{return Math.round(Double.parseDouble(value.replace(",","")));}catch(Exception e){return 0;}}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String clean(String value){return value.replaceAll("§[0-9A-FK-ORa-fk-or]","").trim();}
    private static void save(){ConstellationClient.saveConfig();}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");}
    private static String stat(String suffix){return"ghost."+profile()+"."+suffix;}
    private static long get(String suffix,long fallback){return StatStore.getLong(stat(suffix),fallback);}
    private static void put(String suffix,long value){StatStore.putLong(stat(suffix),value);}
    private static long add(String suffix,long value){return StatStore.add(stat(suffix),value);}
    private static void local(String value){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Ghost Tracker] §f"+value));}
}
