package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// ported from Feesh (Apache-2.0): features/overlays/DeployablesTimer.kt
// deployable ranges cross-checked with Devonian (GPL-3.0): features/misc/Deployables.kt
public final class HydraDeployables {
    public enum Type { TOTEM,BLACK_HOLE,UMBERELLA,FLARE,DWARVEN_LANTERN,FLUX }
    public record State(Type type,String name,int seconds,boolean warning,int color,Vec3 position) {}
    private static final class Tracked { String name;int seconds=-1,standId=-1;boolean alerted;Vec3 pos;long updatedAt;Tracked(String name){this.name=name;} }
    private record Interact(Type type,long at,String name,Set<UUID> rockets,Set<Integer> stands) {}
    private static final Map<Type,Tracked> TRACKED=new EnumMap<>(Type.class);
    private static final List<String> LANTERNS=List.of("Dwarven Lantern","Mithril Lantern","Titanium Lantern","Glacite Lantern","Will-o'-wisp");
    private static final List<String> FLUXES=List.of("Radiant Power Orb","Mana Flux Power Orb","Overflux Power Orb","Plasmaflux Power Orb");
    private static HydraConfig cfg;
    private static boolean initialized,wasEnabled;
    private static int scanTicks;
    private static Interact interaction;
    private HydraDeployables() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-deployables",HydraDeployables::tick);UseItemCallback.EVENT.register((player,level,hand)->{observeUse(player.getItemInHand(hand),hand==net.minecraft.world.InteractionHand.MAIN_HAND);return InteractionResult.PASS;});UseBlockCallback.EVENT.register((player,level,hand,hit)->{observeUse(player.getItemInHand(hand),hand==net.minecraft.world.InteractionHand.MAIN_HAND);return InteractionResult.PASS;});ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void tick(){boolean enabled=active();if(!enabled){if(wasEnabled)resetSession();wasEnabled=false;return;}wasEnabled=true;long now=System.currentTimeMillis();if(interaction!=null&&interaction.type==Type.FLARE&&now-interaction.at>=500)claimFlare();else if(interaction!=null&&interaction.type!=Type.FLARE)claimNamed();if(++scanTicks<Math.clamp(cfg.deployableScanTicks,1,100))return;scanTicks=0;scan();}

    // ported from Feesh (Apache-2.0): features/overlays/DeployablesTimer.kt
    private static void observeUse(ItemStack stack,boolean mainHand){if(!active()||!mainHand||stack==null||stack.isEmpty())return;String name=clean(stack.getHoverName().getString());Type type=heldType(name);if(type==null||!typeEnabled(type))return;long now=System.currentTimeMillis();if(interaction!=null&&interaction.type==type&&now-interaction.at<250)return;Set<UUID> rockets=new HashSet<>();Set<Integer> stands=new HashSet<>();Minecraft mc=Minecraft.getInstance();if(mc.level!=null)for(var entity:mc.level.entitiesForRendering()){if(type==Type.FLARE&&entity instanceof FireworkRocketEntity)rockets.add(entity.getUUID());if(entity instanceof ArmorStand)stands.add(entity.getId());}interaction=new Interact(type,now,name,rockets,stands);}
    private static Type heldType(String name){if(name.equals("Umberella"))return Type.UMBERELLA;if(name.endsWith("Flare"))return Type.FLARE;if(LANTERNS.contains(name))return Type.DWARVEN_LANTERN;if(FLUXES.stream().anyMatch(name::contains))return Type.FLUX;return null;}
    private static void claimFlare(){Interact use=interaction;if(use==null||use.type!=Type.FLARE)return;interaction=null;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;FireworkRocketEntity rocket=null;double best=100;for(var entity:mc.level.entitiesForRendering()){if(!(entity instanceof FireworkRocketEntity candidate)||use.rockets.contains(candidate.getUUID()))continue;double distance=candidate.distanceToSqr(mc.player);if(distance<=best){best=distance;rocket=candidate;}}if(rocket==null)return;Tracked data=new Tracked(use.name);data.seconds=Math.clamp(cfg.deployableFlareDurationSeconds,30,600)*Math.clamp(cfg.deployableFlareDurationMultiplier,1,2);data.pos=rocket.position();data.updatedAt=System.currentTimeMillis();TRACKED.put(Type.FLARE,data);}

    private static void scan(){Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;Map<Integer,ArmorStand> byId=new HashMap<>();List<ArmorStand> stands=new ArrayList<>();for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.hasCustomName()){stands.add(stand);byId.put(stand.getId(),stand);}trackTotem(mc,stands,byId);trackBlackHole(mc,stands,byId);claimNamed();updateNamed(byId);updateFlare();expireMissing();}
    private static void trackTotem(Minecraft mc,List<ArmorStand> stands,Map<Integer,ArmorStand> byId){if(!typeEnabled(Type.TOTEM)){TRACKED.remove(Type.TOTEM);return;}String player=mc.player.getName().getString();ArmorStand owner=stands.stream().filter(s->owner(clean(s.getName().getString()),"Owner:").equalsIgnoreCase(player)).min(Comparator.comparingDouble(s->s.distanceToSqr(mc.player))).orElse(null);if(owner==null){missing(Type.TOTEM);return;}ArmorStand title=byId.get(owner.getId()-2),timer=byId.get(owner.getId()-1);if(title==null||timer==null||!clean(title.getName().getString()).equals("Totem of Corruption")){missing(Type.TOTEM);return;}String raw=clean(timer.getName().getString());int seconds=raw.contains("Remaining: ")?parseTime(raw.substring(raw.indexOf("Remaining: ")+11)):-1;if(seconds>=0)set(Type.TOTEM,"Totem of Corruption",seconds,title);}
    private static void trackBlackHole(Minecraft mc,List<ArmorStand> stands,Map<Integer,ArmorStand> byId){if(!typeEnabled(Type.BLACK_HOLE)){TRACKED.remove(Type.BLACK_HOLE);return;}String player=mc.player.getName().getString();ArmorStand owner=stands.stream().filter(s->owner(clean(s.getName().getString()),"Spawned by:").equalsIgnoreCase(player)).min(Comparator.comparingDouble(s->s.distanceToSqr(mc.player))).orElse(null);if(owner==null){missing(Type.BLACK_HOLE);return;}ArmorStand timer=byId.get(owner.getId()+1);if(timer==null){missing(Type.BLACK_HOLE);return;}String raw=clean(timer.getName().getString());if(!raw.startsWith("Black Hole")){missing(Type.BLACK_HOLE);return;}String value=raw.substring("Black Hole".length()).trim();int seconds=value.isEmpty()?180:parseSeconds(value);if(seconds>=0)set(Type.BLACK_HOLE,"Black Hole",seconds,timer);}

    private static void claimNamed(){Interact use=interaction;Minecraft mc=Minecraft.getInstance();if(use==null||use.type==Type.FLARE||mc.player==null||mc.level==null||System.currentTimeMillis()-use.at>Math.clamp(cfg.deployableInteractWindowMillis,500,3000)){if(use!=null&&use.type!=Type.FLARE)interaction=null;return;}ArmorStand match=null;double best=25;for(var entity:mc.level.entitiesForRendering()){if(!(entity instanceof ArmorStand stand)||use.stands.contains(stand.getId())||!stand.hasCustomName()||!matchesInitial(use.type,clean(stand.getName().getString())))continue;double distance=stand.distanceToSqr(mc.player);if(distance<=best){best=distance;match=stand;}}if(match==null)return;Tracked data=new Tracked(displayName(use.type,use.name));data.standId=match.getId();data.pos=match.position();data.updatedAt=System.currentTimeMillis();TRACKED.put(use.type,data);interaction=null;}
    private static boolean matchesInitial(Type type,String name){int seconds=parseSeconds(name.substring(name.lastIndexOf(' ')+1));return switch(type){case UMBERELLA->name.startsWith("Umberella ")&&nearInitial(seconds,300,600);case DWARVEN_LANTERN->LANTERNS.stream().anyMatch(name::startsWith)&&nearInitial(seconds,300,600);case FLUX->FLUXES.stream().anyMatch(name::startsWith)&&nearInitial(seconds,30,60,120);default->false;};}
    private static boolean nearInitial(int value,int... durations){for(int duration:durations)if(value<=duration&&value>=duration-5)return true;return false;}
    private static void updateNamed(Map<Integer,ArmorStand> byId){for(Type type:List.of(Type.UMBERELLA,Type.DWARVEN_LANTERN,Type.FLUX)){if(!typeEnabled(type)){TRACKED.remove(type);continue;}Tracked data=TRACKED.get(type);if(data==null)continue;ArmorStand stand=byId.get(data.standId);if(stand==null){missing(type);continue;}String raw=clean(stand.getName().getString());int seconds=parseSeconds(raw.substring(raw.lastIndexOf(' ')+1));if(seconds>=0){data.seconds=seconds;data.pos=stand.position();data.updatedAt=System.currentTimeMillis();maybeAlert(type,data);}}}
    private static void updateFlare(){Tracked data=TRACKED.get(Type.FLARE);if(data==null)return;if(!typeEnabled(Type.FLARE)){TRACKED.remove(Type.FLARE);return;}long now=System.currentTimeMillis();int elapsed=(int)((now-data.updatedAt)/1000);if(elapsed>0){data.seconds=Math.max(0,data.seconds-elapsed);data.updatedAt+=elapsed*1000L;}if(data.seconds<=0)TRACKED.remove(Type.FLARE);else maybeAlert(Type.FLARE,data);}
    private static void expireMissing(){TRACKED.entrySet().removeIf(e->e.getValue().seconds==0);}
    private static void missing(Type type){if(cfg.deployableResetOnMissing){TRACKED.remove(type);return;}Tracked data=TRACKED.get(type);if(data==null||data.seconds<=0)return;long now=System.currentTimeMillis();int elapsed=(int)((now-data.updatedAt)/1000);if(elapsed>0){data.seconds=Math.max(0,data.seconds-elapsed);data.updatedAt+=elapsed*1000L;maybeAlert(type,data);}if(data.seconds<=0)TRACKED.remove(type);}
    private static void set(Type type,String name,int seconds,ArmorStand stand){Tracked data=TRACKED.computeIfAbsent(type,t->new Tracked(name));data.name=name;data.seconds=seconds;data.standId=stand.getId();data.pos=stand.position();data.updatedAt=System.currentTimeMillis();maybeAlert(type,data);}
    private static void maybeAlert(Type type,Tracked data){int threshold=type==Type.FLUX?Math.clamp(cfg.deployableShortWarningSeconds,1,30):Math.clamp(cfg.deployableLongWarningSeconds,1,60);if(data.seconds>threshold){data.alerted=false;return;}if(!cfg.deployableAlerts||!alertType(type)||data.seconds<=0||data.alerted)return;data.alerted=true;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.deployableAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(data.name+" expires soon").withColor(cfg.deployableWarningColor&0xFFFFFF));}if(cfg.deployableAlertChat)mc.player.sendSystemMessage(Component.literal("\u00a76[Deployable] \u00a7fYour "+data.name+" expires in "+data.seconds+"s."));if(cfg.deployableAlertSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);}
    private static void onChat(String message){if(!active())return;if(message.equals("Your flare disappeared because you were too far away!")||message.startsWith("Your previous ")&&message.endsWith(" was removed!")&&message.toLowerCase(Locale.ROOT).contains("flare"))TRACKED.remove(Type.FLARE);}

    public static List<State> states(){if(!active())return List.of();Minecraft mc=Minecraft.getInstance();List<State> out=new ArrayList<>();for(var entry:TRACKED.entrySet()){Type type=entry.getKey();Tracked data=entry.getValue();if(data.seconds<=0||!typeEnabled(type)||cfg.deployableShowOnlyActiveBuff&&mc.player!=null&&data.pos!=null&&data.pos.distanceTo(mc.player.position())>range(type))continue;out.add(new State(type,data.name,data.seconds,data.seconds<=warning(type),color(type),data.pos));}out.sort(Comparator.comparingInt(s->priority(s.type)));return List.copyOf(out);}
    public static List<State> hudStates(){return states().stream().filter(s->hudType(s.type)).toList();}
    public static boolean visible(){return cfg!=null&&cfg.deployableHud&&!hudStates().isEmpty();}
    public static HydraConfig config(){return cfg;}
    public static String time(int total){int minutes=Math.max(0,total)/60,seconds=Math.max(0,total)%60;return minutes>0?String.format(Locale.ROOT,"%dm %02ds",minutes,seconds):seconds+"s";}
    public static void draw(WorldRenderer.Ctx ctx){if(!active()||!cfg.deployableShowWorldLabel&&!cfg.deployableShowWorldBox)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;for(State state:states()){if(state.position==null||state.position.distanceTo(mc.player.position())>Math.clamp(cfg.deployableWorldRange,5,128))continue;int color=state.warning?cfg.deployableWarningColor:state.color;if(cfg.deployableShowWorldBox)ctx.outline(new AABB(state.position.x-.35,state.position.y-.35,state.position.z-.35,state.position.x+.35,state.position.y+.35,state.position.z+.35),color,cfg.deployableThroughWalls);if(cfg.deployableShowWorldLabel)ctx.label(state.position.add(0,.75,0),state.name+" "+time(state.seconds),color,cfg.deployableThroughWalls);}}

    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.totemTimer&&cfg.deployableSuite&&ConstellationClient.loc().onHypixel();}
    private static boolean typeEnabled(Type type){return switch(type){case TOTEM->cfg.deployableTotem;case BLACK_HOLE->cfg.deployableBlackHole;case UMBERELLA->cfg.deployableUmberella;case FLARE->cfg.deployableFlare;case DWARVEN_LANTERN->cfg.deployableDwarvenLantern;case FLUX->cfg.deployableFlux;};}
    private static boolean alertType(Type type){return switch(type){case TOTEM->cfg.deployableAlertTotem;case BLACK_HOLE->cfg.deployableAlertBlackHole;case UMBERELLA->cfg.deployableAlertUmberella;case FLARE->cfg.deployableAlertFlare;case DWARVEN_LANTERN->cfg.deployableAlertDwarvenLantern;case FLUX->cfg.deployableAlertFlux;};}
    private static boolean hudType(Type type){return switch(type){case TOTEM->cfg.deployableHudTotem;case BLACK_HOLE->cfg.deployableHudBlackHole;case UMBERELLA->cfg.deployableHudUmberella;case FLARE->cfg.deployableHudFlare;case DWARVEN_LANTERN->cfg.deployableHudDwarvenLantern;case FLUX->cfg.deployableHudFlux;};}
    private static int warning(Type type){return type==Type.FLUX?Math.clamp(cfg.deployableShortWarningSeconds,1,30):Math.clamp(cfg.deployableLongWarningSeconds,1,60);}
    private static double range(Type type){return switch(type){case FLARE->40;case UMBERELLA->30;case FLUX->20;default->20;};}
    private static int priority(Type type){return switch(type){case FLUX->0;case FLARE->1;case TOTEM->2;case BLACK_HOLE->3;case DWARVEN_LANTERN->4;case UMBERELLA->5;};}
    private static int color(Type type){return switch(type){case TOTEM->cfg.deployableTotemColor;case BLACK_HOLE->cfg.deployableBlackHoleColor;case UMBERELLA->cfg.deployableUmberellaColor;case FLARE->cfg.deployableFlareColor;case DWARVEN_LANTERN->cfg.deployableLanternColor;case FLUX->cfg.deployableFluxColor;};}
    private static String displayName(Type type,String held){return switch(type){case UMBERELLA->"Umberella";case DWARVEN_LANTERN->held;case FLUX->held;default->held;};}
    private static String owner(String text,String marker){int at=text.indexOf(marker);return at<0?"":text.substring(at+marker.length()).trim();}
    private static int parseSeconds(String raw){try{return Integer.parseInt(raw.trim().replace("s",""));}catch(Exception ignored){return-1;}}
    private static int parseTime(String raw){try{String value=raw.trim();if(value.contains("m")){String[] parts=value.split("m",2);return Integer.parseInt(parts[0].trim())*60+parseSeconds(parts[1]);}return parseSeconds(value);}catch(Exception ignored){return-1;}}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void reset(){wasEnabled=false;resetSession();}
    private static void resetSession(){TRACKED.clear();interaction=null;scanTicks=0;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("deployables").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetSession();local("Deployable timers cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,60)).executes(c->{cfg.deployableLongWarningSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("shortwarning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,30)).executes(c->{cfg.deployableShortWarningSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("flaremultiplier").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("multiplier",IntegerArgumentType.integer(1,2)).executes(c->{cfg.deployableFlareDurationMultiplier=IntegerArgumentType.getInteger(c,"multiplier");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local(TRACKED.isEmpty()?"No owned deployables tracked.":states().stream().map(s->s.name+" "+time(s.seconds)).reduce((a,b)->a+", "+b).orElse("No visible deployables."));return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"hud"->cfg.deployableHud=value;case"alerts"->cfg.deployableAlerts=value;case"title"->cfg.deployableAlertTitle=value;case"chat"->cfg.deployableAlertChat=value;case"sound"->cfg.deployableAlertSound=value;case"totem"->cfg.deployableTotem=value;case"blackhole"->cfg.deployableBlackHole=value;case"umberella"->cfg.deployableUmberella=value;case"flare"->cfg.deployableFlare=value;case"lantern"->cfg.deployableDwarvenLantern=value;case"flux"->cfg.deployableFlux=value;case"alerttotem"->cfg.deployableAlertTotem=value;case"alertblackhole"->cfg.deployableAlertBlackHole=value;case"alertumberella"->cfg.deployableAlertUmberella=value;case"alertflare"->cfg.deployableAlertFlare=value;case"alertlantern"->cfg.deployableAlertDwarvenLantern=value;case"alertflux"->cfg.deployableAlertFlux=value;case"hudtotem"->cfg.deployableHudTotem=value;case"hudblackhole"->cfg.deployableHudBlackHole=value;case"hudumberella"->cfg.deployableHudUmberella=value;case"hudflare"->cfg.deployableHudFlare=value;case"hudlantern"->cfg.deployableHudDwarvenLantern=value;case"hudflux"->cfg.deployableHudFlux=value;case"label"->cfg.deployableShowWorldLabel=value;case"box"->cfg.deployableShowWorldBox=value;case"active"->cfg.deployableShowOnlyActiveBuff=value;default->{local("Unknown option. Use hud, alerts, a type, alert<type>, hud<type>, label, box, or active.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a76[Deployable] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
