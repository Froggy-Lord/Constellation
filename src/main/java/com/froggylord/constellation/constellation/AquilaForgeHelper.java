package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.data.TabList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

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

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/ForgeWidget.java
// ported from SkyOcean (MIT): features/mining/ForgeReminder.kt
public final class AquilaForgeHelper {
    public enum State { ACTIVE,READY,EMPTY,LOCKED }
    public record ForgeSlot(int slot,String item,State state,long readyAt,long observedAt) { public long remaining(){return state==State.ACTIVE?Math.max(0,readyAt-System.currentTimeMillis()):0;} }
    private static final class Store { Map<String,List<StoredSlot>> profiles=new HashMap<>(); }
    private static final class StoredSlot { int slot;String item;String state;long readyAt;long observedAt;StoredSlot(){}StoredSlot(ForgeSlot s){slot=s.slot;item=s.item;state=s.state.name();readyAt=s.readyAt;observedAt=s.observedAt;} }
    private record Duration(long millis,long precision) {}
    private static final Pattern SLOT=Pattern.compile("^(?<slot>[1-7])\\)\\s*(?<payload>.+)$");
    private static final Pattern PART=Pattern.compile("(?<value>[0-9]+)\\s*(?<unit>[dhms])",Pattern.CASE_INSENSITIVE);
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("constellation-forges.json");
    private static final Map<String,List<ForgeSlot>> PROFILES=new HashMap<>();
    private static final Map<Integer,Long> REMINDED=new HashMap<>();
    private static AquilaConfig cfg;
    private static String profileKey="";
    private static long lastSave;
    private static boolean dirty;
    private static boolean persistenceEnabled;

    private AquilaForgeHelper() {}
    public static void init(AquilaConfig config){cfg=config;persistenceEnabled=cfg.forgePersistProfiles;if(persistenceEnabled)load();ConstellationClient.tick().every(20,"aquila-forge",AquilaForgeHelper::tick);ClientPlayConnectionEvents.JOIN.register((a,b,c)->connectionReset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->connectionReset());}
    private static void tick(){if(cfg==null)return;if(cfg.forgePersistProfiles!=persistenceEnabled){persistenceEnabled=cfg.forgePersistProfiles;PROFILES.clear();REMINDED.clear();dirty=false;if(persistenceEnabled)load();}String current=LyraStorageValue.currentProfileKey();if(!current.equals(profileKey)){profileKey=current;REMINDED.clear();}if(!active()||profileKey.isBlank())return;List<ForgeSlot> parsed=parse(TabList.lines());if(parsed!=null)merge(parsed);remind();flushSave(false);}
    private static List<ForgeSlot> parse(List<String> tab){boolean section=false;List<ForgeSlot> out=new ArrayList<>();long now=System.currentTimeMillis();for(String raw:tab){String line=clean(raw);if(line.startsWith("Forges")){section=true;continue;}if(!section)continue;Matcher m=SLOT.matcher(line);if(!m.matches()){if(!out.isEmpty())break;continue;}int slot=Integer.parseInt(m.group("slot"));String payload=m.group("payload").trim();if(payload.equalsIgnoreCase("EMPTY")){out.add(new ForgeSlot(slot,"Empty",State.EMPTY,0,now));continue;}if(payload.equalsIgnoreCase("LOCKED")){out.add(new ForgeSlot(slot,"Locked",State.LOCKED,0,now));continue;}int split=payload.lastIndexOf(": ");if(split<1)continue;String item=sanitizeItem(payload.substring(0,split));String time=payload.substring(split+2).trim();if(item.isBlank())continue;if(time.equalsIgnoreCase("Ready!")){out.add(new ForgeSlot(slot,item,State.READY,0,now));continue;}Duration duration=duration(time);if(duration!=null)out.add(new ForgeSlot(slot,item,State.ACTIVE,now+duration.millis,now));}if(!section||out.size()<2)return null;for(int i=0;i<out.size();i++)if(out.get(i).slot!=i+1)return null;return List.copyOf(out);}
    private static void merge(List<ForgeSlot> parsed){List<ForgeSlot> old=PROFILES.getOrDefault(profileKey,List.of());Map<Integer,ForgeSlot> prior=new HashMap<>();for(ForgeSlot s:old)prior.put(s.slot,s);List<ForgeSlot> merged=new ArrayList<>();long now=System.currentTimeMillis();for(ForgeSlot next:parsed){ForgeSlot before=prior.remove(next.slot);if(before!=null&&before.state==next.state&&before.item.equals(next.item)){long ready=next.readyAt;if(next.state==State.ACTIVE){long precision=precisionFromRemaining(next.readyAt-next.observedAt);if(Math.abs(before.readyAt-next.readyAt)<=precision+5000)ready=before.readyAt;}long observed=now-before.observedAt>=300_000L?now:before.observedAt;next=new ForgeSlot(next.slot,next.item,next.state,ready,observed);}boolean promoted=before!=null&&before.state==State.ACTIVE&&before.readyAt<=now&&next.state==State.READY&&before.item.equals(next.item);if(before==null||!promoted&&(before.state!=next.state||!before.item.equals(next.item)))REMINDED.remove(next.slot);merged.add(next);}merged.addAll(prior.values());merged.sort(Comparator.comparingInt(ForgeSlot::slot));if(!merged.equals(old)){PROFILES.put(profileKey,List.copyOf(merged));dirty=true;}}
    private static void remind(){if(!cfg.forgeReminder)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||(!cfg.forgeReminderWhileContainerOpen&&mc.player.containerMenu!=mc.player.inventoryMenu))return;long now=System.currentTimeMillis(),delay=Math.max(1,cfg.forgeReminderDelayMinutes)*60_000L;List<ForgeSlot> ready=new ArrayList<>();for(ForgeSlot s:slots()){if(s.state==State.READY||s.state==State.ACTIVE&&s.readyAt<=now){long last=REMINDED.getOrDefault(s.slot,0L);if(last==0||cfg.forgeReminderRepeat&&now-last>=delay)ready.add(s);}}if(ready.isEmpty())return;for(ForgeSlot s:ready)REMINDED.put(s.slot,now);Component line=Component.literal("§3[Forge] §f"+(cfg.forgeReminderListItems?readyItems(ready):ready.size()+" forge slot"+(ready.size()==1?" is":"s are")+" ready"));if(cfg.forgeReminderClickableWarp)line=line.copy().append(Component.literal(" §e[Warp]").withStyle(st->st.withClickEvent(new ClickEvent.RunCommand("/warp forge"))));if(cfg.forgeReminderClickableFred)line=line.copy().append(Component.literal(" §a[Call Fred]").withStyle(st->st.withClickEvent(new ClickEvent.RunCommand("/call fred"))));mc.player.sendSystemMessage(line);}
    public static List<ForgeSlot> slots(){if(!active()||profileKey.isBlank())return List.of();long cutoff=System.currentTimeMillis()-Math.max(1,cfg.forgeStaleHours)*3_600_000L;List<ForgeSlot> out=new ArrayList<>();for(ForgeSlot s:PROFILES.getOrDefault(profileKey,List.of()))if(s.observedAt>=cutoff)out.add(promote(s));if(cfg.forgeReadyFirst)out.sort(Comparator.comparing((ForgeSlot s)->s.state!=State.READY).thenComparingInt(ForgeSlot::slot));else out.sort(Comparator.comparingInt(ForgeSlot::slot));return List.copyOf(out);}
    private static ForgeSlot promote(ForgeSlot s){return s.state==State.ACTIVE&&s.readyAt<=System.currentTimeMillis()?new ForgeSlot(s.slot,s.item,State.READY,s.readyAt,s.observedAt):s;}
    public static AquilaConfig config(){return cfg;}
    private static boolean active(){return cfg.enabled&&cfg.forgeSuite;}
    private static String readyItems(List<ForgeSlot> ready){StringBuilder out=new StringBuilder();for(ForgeSlot s:ready){if(out.length()>0)out.append(", ");out.append(s.item);}return out.append(ready.size()==1?" is ready.":" are ready.").toString();}
    private static Duration duration(String text){Matcher m=PART.matcher(text);long total=0,precision=0;int end=0;while(m.find()){long value=Long.parseLong(m.group("value"));long unit=switch(m.group("unit").toLowerCase(Locale.ROOT)){case"d"->86_400_000L;case"h"->3_600_000L;case"m"->60_000L;default->1000L;};total+=value*unit;precision=unit;end=m.end();}if(total<=0||!text.substring(end).trim().isEmpty())return null;return new Duration(total,precision);}
    private static long precisionFromRemaining(long remaining){if(remaining%86_400_000L==0)return 86_400_000L;if(remaining%3_600_000L==0)return 3_600_000L;if(remaining%60_000L==0)return 60_000L;return 1000L;}
    public static String format(long millis){long seconds=Math.max(0,(millis+999)/1000),days=seconds/86400;seconds%=86400;long hours=seconds/3600;seconds%=3600;long minutes=seconds/60;seconds%=60;if(days>0)return days+"d "+hours+"h";if(hours>0)return hours+"h "+minutes+"m";return minutes+"m "+seconds+"s";}
    private static String sanitizeItem(String item){return item.replaceFirst("^[^A-Za-z0-9]+\\s*","").trim();}
    private static String clean(String raw){String s=ChatFormatting.stripFormatting(raw);return s==null?"":s.trim();}
    private static void connectionReset(){profileKey="";REMINDED.clear();}
    private static void load(){try{if(!Files.exists(FILE))return;Store store=GSON.fromJson(Files.readString(FILE,StandardCharsets.UTF_8),Store.class);if(store==null||store.profiles==null)return;for(var e:store.profiles.entrySet()){List<ForgeSlot> slots=new ArrayList<>();if(e.getValue()!=null)for(StoredSlot s:e.getValue())try{if(s!=null&&s.slot>=1&&s.slot<=7&&s.item!=null)slots.add(new ForgeSlot(s.slot,s.item,State.valueOf(s.state),s.readyAt,s.observedAt));}catch(Exception ignored){}PROFILES.put(e.getKey(),List.copyOf(slots));}}catch(Exception e){ConstellationClient.LOGGER.warn("could not load forge cache",e);}}
    private static void flushSave(boolean force){if(!dirty||!cfg.forgePersistProfiles||!force&&System.currentTimeMillis()-lastSave<500)return;lastSave=System.currentTimeMillis();try{Files.createDirectories(FILE.getParent());Store store=new Store();for(var e:PROFILES.entrySet()){List<StoredSlot> slots=new ArrayList<>();for(ForgeSlot s:e.getValue())slots.add(new StoredSlot(s));store.profiles.put(e.getKey(),slots);}Files.writeString(FILE,GSON.toJson(store),StandardCharsets.UTF_8);dirty=false;}catch(Exception e){ConstellationClient.LOGGER.warn("could not save forge cache",e);}}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("forgehelper").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->clear())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("delay").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,30)).executes(c->{cfg.forgeReminderDelayMinutes=IntegerArgumentType.getInteger(c,"minutes");saveConfig();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("action").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->action(StringArgumentType.getString(c,"mode"))))));}
    private static int status(){local("Tracked "+slots().size()+" forge slots; reminder "+(cfg.forgeReminder?"on":"off")+" every "+cfg.forgeReminderDelayMinutes+"m.");return 1;}
    private static int clear(){if(!profileKey.isBlank())PROFILES.remove(profileKey);REMINDED.clear();dirty=true;flushSave(true);local("Current profile forge cache cleared.");return 1;}
    private static int action(String mode){switch(mode.toLowerCase(Locale.ROOT)){case"warp"->{cfg.forgeReminderClickableWarp=true;cfg.forgeReminderClickableFred=false;}case"fred"->{cfg.forgeReminderClickableWarp=false;cfg.forgeReminderClickableFred=true;}case"both"->{cfg.forgeReminderClickableWarp=true;cfg.forgeReminderClickableFred=true;}case"none"->{cfg.forgeReminderClickableWarp=false;cfg.forgeReminderClickableFred=false;}default->{local("Action must be warp, fred, both, or none.");return 0;}}saveConfig();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Forge] §f"+text));}
    private static void saveConfig(){ConstellationClient.saveConfig();}
}
