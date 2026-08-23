package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): events/publishers/ConsumablesPublishers.kt
// ported from Feesh (Apache-2.0): features/overlays/ConsumablesTimer.kt
// ported from Feesh (Apache-2.0): features/alerts/SaltExpiredAlert.kt
// salt tab parsing ported from SkyHanni (LGPL-3.0-or-later): data/effect/EffectApi.kt
public final class HydraConsumables {
    public record State(String name,int seconds,boolean active,boolean soon,boolean warning,int color) {}
    private static final Pattern MOBY_CONSUMED=Pattern.compile("^You consumed a Moby-Duck: Collector's Edition and gained \\+30\\x{262F} Fishing Wisdom for 60m!$");
    private static final Pattern MOBY_EXPIRING=Pattern.compile("^Moby-Duck expires in (\\d+)s$");
    private static final Pattern BLIZZARD=Pattern.compile("^BLIZZARD! (.+?) opened a Blizzard in a Bottle, improving everyone's Fishing Stats for the next 10 minutes and causing it to snow!$");
    private static final Pattern SALT_EXPIRED=Pattern.compile("^SALT: Your (.*?) has expired!$");
    private static final Pattern SALT_TAB=Pattern.compile("^((?:Prime |Exalted )?Lushlilac Bonbon|Oceandy|Candycomb):\\s*([dhms0-9 ]+)$",Pattern.CASE_INSENSITIVE);
    private static final class Timer { String name;long remainingMillis,lastUpdate,lastSeen;boolean active,serverWarning,alerted;Timer(String name){this.name=name;} }
    private static final Timer MOBY=new Timer("Moby-Duck"),BLIZZARD_TIMER=new Timer("Blizzard in a Bottle");
    private static final Map<String,Timer> SALTS=new HashMap<>();
    private static final Set<String> SALT_WARNED=new HashSet<>();
    private static final Set<String> SALT_EXPIRED_SUPPRESSED=new HashSet<>();
    private static HydraConfig cfg;
    private static Object blizzardLevel;
    private static boolean initialized,wasEnabled;
    private static int scanTicks;
    private HydraConsumables() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-consumables",HydraConsumables::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}
    private static void tick(){boolean enabled=active();if(!enabled){if(wasEnabled)resetSession();wasEnabled=false;return;}wasEnabled=true;long now=System.currentTimeMillis();advance(MOBY,now);Minecraft mc=Minecraft.getInstance();if(BLIZZARD_TIMER.active&&mc.level!=blizzardLevel)clear(BLIZZARD_TIMER);advance(BLIZZARD_TIMER,now);if(BLIZZARD_TIMER.active&&cfg.consumableBlizzardAlert&&BLIZZARD_TIMER.remainingMillis<=Math.clamp(cfg.consumableBlizzardWarningSeconds,1,120)*1000L&&!BLIZZARD_TIMER.alerted)alertTimer(BLIZZARD_TIMER,Math.max(0,BLIZZARD_TIMER.remainingMillis/1000),cfg.consumableBlizzardTitle,cfg.consumableBlizzardChat,cfg.consumableBlizzardSound);if(!cfg.consumableSaltTracker||ConstellationClient.loc().area()!=SkyblockArea.GALATEA){clearSalts();return;}advanceSalts(now);if(++scanTicks<Math.clamp(cfg.consumableScanTicks,5,100))return;scanTicks=0;scanSalts(now);}

    // ported from Feesh (Apache-2.0): events/publishers/ConsumablesPublishers.kt
    private static void onChat(String message){if(!active())return;if(MOBY_CONSUMED.matcher(message).matches()){if(cfg.consumableMobyDuck)start(MOBY,Math.clamp(cfg.consumableMobyDuckDurationMinutes,1,120)*60_000L);return;}if(message.equals("Moby-Duck has expired!")){clear(MOBY);return;}Matcher expiring=MOBY_EXPIRING.matcher(message);if(expiring.matches()&&cfg.consumableMobyDuck){int seconds=parseInt(expiring.group(1));if(seconds>=0){MOBY.active=true;MOBY.serverWarning=true;if(seconds>cfg.consumableMobyDuckWarningSeconds)MOBY.alerted=false;else if(cfg.consumableMobyDuckAlert&&!MOBY.alerted)alertTimer(MOBY,seconds,cfg.consumableMobyDuckTitle,cfg.consumableMobyDuckChat,cfg.consumableMobyDuckSound);if(cfg.consumableMobyDuckServerSync){MOBY.remainingMillis=seconds*1000L;MOBY.lastUpdate=System.currentTimeMillis();}}return;}Matcher blizzard=BLIZZARD.matcher(message);if(blizzard.matches()&&cfg.consumableBlizzard&&(!cfg.consumableBlizzardOwnOnly||ownPlayer(blizzard.group(1)))){start(BLIZZARD_TIMER,Math.clamp(cfg.consumableBlizzardDurationMinutes,1,30)*60_000L);return;}Matcher salt=SALT_EXPIRED.matcher(message);if(salt.matches()&&cfg.consumableSaltExpiryAlert){String name=salt.group(1).trim(),key=normalize(name);if(!name.isEmpty()){SALTS.remove(key);SALT_WARNED.remove(key);SALT_EXPIRED_SUPPRESSED.add(key);saltAlert(name);}}}
    private static boolean ownPlayer(String ranked){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;String value=ranked.trim();int rank=value.lastIndexOf("] ");if(rank>=0)value=value.substring(rank+2);return value.equals(mc.player.getName().getString());}

    // ported from Feesh (Apache-2.0): features/overlays/ConsumablesTimer.kt
    private static void start(Timer timer,long duration){timer.active=true;timer.remainingMillis=duration;timer.lastUpdate=System.currentTimeMillis();timer.serverWarning=false;timer.alerted=false;if(timer==BLIZZARD_TIMER)blizzardLevel=Minecraft.getInstance().level;}
    private static void advance(Timer timer,long now){if(!timer.active)return;long elapsed=Math.max(0,now-timer.lastUpdate);timer.lastUpdate=now;timer.remainingMillis-=elapsed;if(timer==MOBY){long minimum=-Math.clamp(cfg.consumableMobyDuckExtraWaitMinutes,0,15)*60_000L;if(timer.remainingMillis<=minimum)clear(timer);}else if(timer.remainingMillis<=0)clear(timer);}
    private static void clear(Timer timer){timer.active=false;timer.remainingMillis=0;timer.lastUpdate=0;timer.serverWarning=false;timer.alerted=false;if(timer==BLIZZARD_TIMER)blizzardLevel=null;}
    private static void alertTimer(Timer timer,long seconds,boolean title,boolean chat,boolean sound){timer.alerted=true;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(title){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(timer.name+" expires soon").withColor(cfg.consumableWarningColor&0xFFFFFF));}if(chat)mc.player.sendSystemMessage(Component.literal("\u00a75[Consumable] \u00a7f"+timer.name+" expires in "+Math.max(0,seconds)+"s."));if(sound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);}

    // salt tab parsing ported from SkyHanni (LGPL-3.0-or-later): data/effect/EffectApi.kt
    private static void scanSalts(long now){if(!cfg.consumableSaltTracker||ConstellationClient.loc().area()!=SkyblockArea.GALATEA)return;Set<String> seen=new HashSet<>();for(String line:TabList.lines()){Matcher matcher=SALT_TAB.matcher(line.trim());if(!matcher.matches())continue;String name=title(matcher.group(1)),key=normalize(name);long duration=parseDuration(matcher.group(2));if(duration<0)continue;seen.add(key);if(SALT_EXPIRED_SUPPRESSED.contains(key))continue;Timer timer=SALTS.computeIfAbsent(key,k->new Timer(name));timer.name=name;timer.active=true;timer.remainingMillis=duration;timer.lastUpdate=now;timer.lastSeen=now;long warning=Math.clamp(cfg.consumableSaltWarningMinutes,1,120)*60_000L;if(duration>warning)SALT_WARNED.remove(key);else if(cfg.consumableSaltSoonAlert&&SALT_WARNED.add(key))saltSoon(name,duration);}Iterator<Map.Entry<String,Timer>> iterator=SALTS.entrySet().iterator();while(iterator.hasNext()){Map.Entry<String,Timer> entry=iterator.next();if(!seen.contains(entry.getKey())&&now-entry.getValue().lastSeen>60_000L){SALT_WARNED.remove(entry.getKey());iterator.remove();}}SALT_EXPIRED_SUPPRESSED.retainAll(seen);}
    private static void advanceSalts(long now){for(Timer timer:SALTS.values()){long elapsed=Math.max(0,now-timer.lastUpdate);timer.lastUpdate=now;timer.remainingMillis=Math.max(0,timer.remainingMillis-elapsed);if(timer.remainingMillis<=0)timer.active=false;}}
    private static void clearSalts(){if(SALTS.isEmpty()&&SALT_WARNED.isEmpty()&&SALT_EXPIRED_SUPPRESSED.isEmpty())return;SALTS.clear();SALT_WARNED.clear();SALT_EXPIRED_SUPPRESSED.clear();scanTicks=0;}
    private static long parseDuration(String raw){Matcher matcher=Pattern.compile("(\\d+)\\s*([dhms])",Pattern.CASE_INSENSITIVE).matcher(raw);long total=0;boolean found=false;while(matcher.find()){found=true;long value=parseInt(matcher.group(1));total+=value*switch(matcher.group(2).toLowerCase(Locale.ROOT)){case"d"->86_400_000L;case"h"->3_600_000L;case"m"->60_000L;default->1000L;};}return found?total:-1;}
    private static void saltSoon(String name,long duration){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.consumableSaltTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name+" expires soon").withColor(cfg.consumableWarningColor&0xFFFFFF));}if(cfg.consumableSaltChat)mc.player.sendSystemMessage(Component.literal("\u00a7d[Salt] \u00a7f"+name+" expires in "+time((int)(duration/1000))+"."));if(cfg.consumableSaltSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);}
    // ported from Feesh (Apache-2.0): features/alerts/SaltExpiredAlert.kt
    private static void saltAlert(String name){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.consumableSaltTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name+" has expired").withColor(cfg.consumableWarningColor&0xFFFFFF));}if(cfg.consumableSaltChat)mc.player.sendSystemMessage(Component.literal("\u00a7d[Salt] \u00a7f"+name+" has expired."));if(cfg.consumableSaltSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,.8f);}

    public static List<State> states(){if(!active()||cfg.consumableHudOnlyFishingAreas&&!fishingArea())return List.of();List<State> out=new ArrayList<>();boolean mobyVisible=MOBY.active&&MOBY.remainingMillis>0;if(cfg.consumableMobyDuck&&(mobyVisible||cfg.consumableHudShowInactive)){int seconds=(int)Math.max(0,MOBY.remainingMillis/1000);boolean soon=mobyVisible&&cfg.consumableMobyDuckSoonText&&!MOBY.serverWarning&&seconds<=Math.clamp(cfg.consumableMobyDuckSoonSeconds,1,300);out.add(new State(MOBY.name,seconds,mobyVisible,soon,mobyVisible&&seconds<=cfg.consumableMobyDuckWarningSeconds,cfg.consumableMobyDuckColor));}if(cfg.consumableBlizzard&&(BLIZZARD_TIMER.active||cfg.consumableHudShowInactive)){int seconds=(int)Math.max(0,BLIZZARD_TIMER.remainingMillis/1000);out.add(new State("Blizzard",seconds,BLIZZARD_TIMER.active,false,BLIZZARD_TIMER.active&&seconds<=cfg.consumableBlizzardWarningSeconds,cfg.consumableBlizzardColor));}if(cfg.consumableSaltHud&&ConstellationClient.loc().area()==SkyblockArea.GALATEA)for(Timer timer:SALTS.values())if(timer.active&&timer.remainingMillis>0)out.add(new State(timer.name,(int)(timer.remainingMillis/1000),true,false,timer.remainingMillis<=Math.clamp(cfg.consumableSaltWarningMinutes,1,120)*60_000L,cfg.consumableSaltColor));out.sort(Comparator.comparing(State::name));return List.copyOf(out);}
    public static boolean visible(){return cfg!=null&&cfg.consumableHud&&!states().isEmpty();}
    public static HydraConfig config(){return cfg;}
    public static String time(int total){int value=Math.max(0,total),days=value/86400,hours=value/3600%24,minutes=value/60%60,seconds=value%60;if(days>0)return String.format(Locale.ROOT,"%dd %02dh %02dm",days,hours,minutes);if(hours>0)return String.format(Locale.ROOT,"%dh %02dm %02ds",hours,minutes,seconds);if(minutes>0)return String.format(Locale.ROOT,"%dm %02ds",minutes,seconds);return seconds+"s";}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.consumableSuite&&ConstellationClient.loc().onHypixel();}
    private static boolean fishingArea(){return switch(ConstellationClient.loc().area()){case THE_RIFT,GARDEN,KUUDRA,CATACOMBS,MASTER_MODE,DUNGEON_HUB,THE_END,GLACITE_MINESHAFT->false;default->true;};}
    private static int parseInt(String raw){try{return Integer.parseInt(raw.replace(",","").trim());}catch(Exception ignored){return-1;}}
    private static String normalize(String value){return value.toLowerCase(Locale.ROOT).replace(" bonbon","").trim();}
    private static String title(String raw){StringBuilder out=new StringBuilder();for(String word:raw.toLowerCase(Locale.ROOT).trim().split(" ")){if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void reset(){wasEnabled=false;resetSession();}
    private static void resetSession(){clear(MOBY);clear(BLIZZARD_TIMER);clearSalts();scanTicks=0;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("consumables").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetSession();local("Consumable timers cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("startmoby").executes(c->{start(MOBY,Math.clamp(cfg.consumableMobyDuckDurationMinutes,1,120)*60_000L);return status();})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mobywarning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,120)).executes(c->{cfg.consumableMobyDuckWarningSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("saltwarning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,120)).executes(c->{cfg.consumableSaltWarningMinutes=IntegerArgumentType.getInteger(c,"minutes");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){List<State> states=states();local(states.isEmpty()?"No visible consumable timers.":states.stream().map(s->s.name+" "+(s.active?time(s.seconds):"inactive")).reduce((a,b)->a+", "+b).orElse("No timers."));return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"hud"->cfg.consumableHud=value;case"moby"->cfg.consumableMobyDuck=value;case"mobyalert"->cfg.consumableMobyDuckAlert=value;case"serversync"->cfg.consumableMobyDuckServerSync=value;case"blizzard"->cfg.consumableBlizzard=value;case"blizzardalert"->cfg.consumableBlizzardAlert=value;case"blizzardown"->cfg.consumableBlizzardOwnOnly=value;case"salts"->cfg.consumableSaltTracker=value;case"salthud"->cfg.consumableSaltHud=value;case"saltexpired"->cfg.consumableSaltExpiryAlert=value;case"saltsoon"->cfg.consumableSaltSoonAlert=value;case"inactive"->cfg.consumableHudShowInactive=value;case"seconds"->cfg.consumableHudShowSeconds=value;case"fishingonly"->cfg.consumableHudOnlyFishingAreas=value;default->{local("Option must be hud, moby, mobyalert, serversync, blizzard, blizzardalert, blizzardown, salts, salthud, saltexpired, saltsoon, inactive, seconds, or fishingonly.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a75[Consumable] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
