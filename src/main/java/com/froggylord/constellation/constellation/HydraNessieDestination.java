package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from Feesh (Apache-2.0): features/alerts/NessieDestinationAlert.kt
public final class HydraNessieDestination {
    private record Destination(String key,String name,Vec3 marker,List<Vec3> checkpoints) {}
    private static final class Tracked {final long detectedAt;boolean sent;Tracked(long detectedAt){this.detectedAt=detectedAt;}}
    private record Guidance(Destination destination,long at,int entityId) {}
    private static final Destination DRIPTOAD=new Destination("driptoad","Driptoad Delve",new Vec3(-663,71,12),List.of(
        new Vec3(-663,71,12),new Vec3(-665,71,20),new Vec3(-666,69,17),new Vec3(-675,63,40),new Vec3(-674,81,44),new Vec3(-680,70,51),new Vec3(-674,59,30),new Vec3(-675,62,39),new Vec3(-678,65,47),new Vec3(-682,68,54),new Vec3(-686,74,60)));
    private static final Destination JADE=new Destination("jade","Jade Dragon",new Vec3(-660,71,0),List.of(
        new Vec3(-660,71,0),new Vec3(-637,60,-7),new Vec3(-660,68,-2),new Vec3(-656,55,-12),new Vec3(-648,54,-13),new Vec3(-640,53,-10),new Vec3(-638,51,-4),new Vec3(-634,50,-10),new Vec3(-627,50,-5),new Vec3(-620,48,5),new Vec3(-620,47,13),new Vec3(-626,47,19),new Vec3(-636,47,7),new Vec3(-640,45,13),new Vec3(-644,44,15)));
    private static final List<Destination> DESTINATIONS=List.of(DRIPTOAD,JADE);
    private static final Map<Integer,Tracked> TRACKED=new HashMap<>();
    private static HydraConfig cfg;
    private static boolean initialized,wasConfigured;
    private static int ticks;
    private static long lastSubmerged;
    private static Guidance guidance;
    private static Level level;

    private HydraNessieDestination() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-nessie-destination",HydraNessieDestination::tick);ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetConnection());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetConnection());}

    private static void tick(){Minecraft mc=Minecraft.getInstance();boolean configured=configured();if(!configured){if(wasConfigured)resetTransient();wasConfigured=false;return;}wasConfigured=true;if(mc.level!=level){level=mc.level;resetTransient();}if(mc.player==null||mc.level==null||!ConstellationClient.loc().onHypixel())return;if(mc.player.fishing!=null&&(mc.player.fishing.isInWater()||mc.player.fishing.isInLava()))lastSubmerged=System.currentTimeMillis();if(ConstellationClient.loc().area()!=SkyblockArea.GALATEA)return;if(++ticks<Math.clamp(cfg.nessieScanTicks,1,100))return;ticks=0;scan(mc);}

    // ported from Feesh (Apache-2.0): features/alerts/NessieDestinationAlert.kt
    private static void scan(Minecraft mc){long now=System.currentTimeMillis();if(!cfg.nessieRequireRecentHook||now-lastSubmerged<=Math.clamp(cfg.nessieRecentHookMinutes,1,30)*60_000L)for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&nessieTag(stand)){int mobId=stand.getId()-1;Entity mob=mc.level.getEntity(mobId);if(mob instanceof Sniffer)TRACKED.putIfAbsent(mobId,new Tracked(now));}double radius=Math.clamp(cfg.nessieCheckpointRadius,1,15),radiusSqr=radius*radius;for(var entry:new ArrayList<>(TRACKED.entrySet())){Tracked tracked=entry.getValue();if(tracked.sent)continue;Entity entity=mc.level.getEntity(entry.getKey());if(!(entity instanceof Sniffer sniffer))continue;for(Destination destination:DESTINATIONS){if(!selected(destination))continue;boolean reached=false;for(Vec3 checkpoint:destination.checkpoints)if(sniffer.position().distanceToSqr(checkpoint)<=radiusSqr){reached=true;break;}if(reached){tracked.sent=true;guidance=new Guidance(destination,now,entry.getKey());if(cfg.nessieDestinationAlert)alert(destination);break;}}}long expiry=Math.clamp(cfg.nessieTrackExpirationMinutes,1,15)*60_000L;Iterator<Map.Entry<Integer,Tracked>> iterator=TRACKED.entrySet().iterator();while(iterator.hasNext())if(now-iterator.next().getValue().detectedAt>expiry)iterator.remove();if(guidance!=null&&now-guidance.at>Math.clamp(cfg.nessieGuidanceSeconds,10,900)*1000L)guidance=null;}
    private static boolean nessieTag(ArmorStand stand){if(!stand.isAlive()||!stand.hasCustomName())return false;String name=clean(stand.getName().getString());return name.contains("[Lv")&&name.contains("]")&&name.contains("Nessie")&&name.contains("\u2764");}
    private static boolean selected(Destination destination){return destination==DRIPTOAD?cfg.nessieTrackDriptoad:cfg.nessieTrackJade;}

    private static void alert(Destination destination){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text=format(cfg.nessieAlertTemplate,destination);if(cfg.nessieAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.empty());mc.gui.hud.setSubtitle(Component.literal("Nessie goes to "+destination.name+" cave").withColor(color(destination)&0xFFFFFF));}if(cfg.nessieAlertChat)localComponent(Component.literal("\u00a75[Nessie] \u00a7f"+text).append(shareButton(destination)));if(cfg.nessieAlertSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);if(cfg.nessieAutoShare)PartyMessages.sendAnywhere("nessie-destination",Map.of("destination",destination.name));}
    private static Component shareButton(Destination destination){if(!cfg.nessieClickableShare||!PartyMessages.enabled("nessie-destination"))return Component.empty();String message=partyText(destination);if(message.isEmpty())return Component.empty();return Component.literal(" \u00a79[Share]").withStyle(style->style.withClickEvent(new ClickEvent.RunCommand("/pc "+message)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Share Nessie's destination to party chat"))));}
    private static String partyText(Destination destination){String value=PartyMessages.template("nessie-destination").replace("{destination}",destination.name).replace('\n',' ').replace('\r',' ').trim();return value.substring(0,Math.min(120,value.length()));}
    private static String format(String template,Destination destination){String value=template==null?"":template;return value.replace("{destination}",destination.name).replace("{x}",Integer.toString((int)destination.marker.x)).replace("{y}",Integer.toString((int)destination.marker.y)).replace("{z}",Integer.toString((int)destination.marker.z));}

    public static void draw(WorldRenderer.Ctx ctx){Minecraft mc=Minecraft.getInstance();Guidance current=guidance;if(!active()||!cfg.nessieGuidance||current==null||mc.player==null||System.currentTimeMillis()-current.at>Math.clamp(cfg.nessieGuidanceSeconds,10,900)*1000L)return;Vec3 target=current.destination.marker;if(mc.player.position().distanceToSqr(target)>Math.pow(Math.clamp(cfg.nessieRenderRange,25,1000),2))return;int color=color(current.destination);boolean through=cfg.nessieThroughWalls;if(cfg.nessieMarkerBox)ctx.highlight(AABB.ofSize(target.add(0,.5,0),1.2,1.2,1.2),color,through);if(cfg.nessieMarkerBeam)ctx.beam(target.x,target.y,target.z,color,Math.clamp(cfg.nessieBeamHeight,2,100),through);if(cfg.nessieMarkerLine)ctx.line(mc.player.position().add(0,1,0),target.add(0,.5,0),color,through);if(cfg.nessieMarkerLabel){String label=current.destination.name+(cfg.nessieMarkerDistance?" "+String.format(Locale.ROOT,"%.0fm",mc.player.position().distanceTo(target)):"");ctx.label(target.add(0,1.5,0),label,color,through);}}
    private static int color(Destination destination){return destination==DRIPTOAD?cfg.nessieDriptoadColor:cfg.nessieJadeColor;}

    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.nessieDestinationSuite;}
    private static boolean active(){return configured()&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;}
    private static String clean(String value){String plain=ChatFormatting.stripFormatting(value);return plain==null?"":plain.trim();}
    private static void resetConnection(){level=null;wasConfigured=false;resetTransient();}
    private static void resetTransient(){TRACKED.clear();ticks=0;lastSubmerged=0;guidance=null;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nessie").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetTransient();local("Nessie tracking cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recent").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,30)).executes(c->number("recent",IntegerArgumentType.getInteger(c,"minutes"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("radius").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(1,15)).executes(c->number("radius",IntegerArgumentType.getInteger(c,"blocks"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(10,900)).executes(c->number("duration",IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("expiration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,15)).executes(c->number("expiration",IntegerArgumentType.getInteger(c,"minutes"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(25,1000)).executes(c->number("range",IntegerArgumentType.getInteger(c,"blocks"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("beamheight").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,100)).executes(c->number("beam",IntegerArgumentType.getInteger(c,"blocks"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scanticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("ticks",IntegerArgumentType.integer(1,100)).executes(c->number("scan",IntegerArgumentType.getInteger(c,"ticks"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(StringArgumentType.getString(c,"text"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("destination",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"destination"),StringArgumentType.getString(c,"argb")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Tracked "+TRACKED.size()+", destination "+(guidance==null?"unknown":guidance.destination.name)+", recent hook "+on(cfg.nessieRequireRecentHook)+", auto-share "+on(cfg.nessieAutoShare)+", guidance "+on(cfg.nessieGuidance)+".");return 1;}
    private static int number(String type,int value){switch(type){case"recent"->cfg.nessieRecentHookMinutes=value;case"radius"->cfg.nessieCheckpointRadius=value;case"duration"->cfg.nessieGuidanceSeconds=value;case"expiration"->cfg.nessieTrackExpirationMinutes=value;case"range"->cfg.nessieRenderRange=value;case"beam"->cfg.nessieBeamHeight=value;case"scan"->cfg.nessieScanTicks=value;}save();return status();}
    private static int template(String raw){String value=raw==null?"":raw.replace('\n',' ').replace('\r',' ').trim();if(value.isEmpty()||value.length()>160){local("Template must contain 1-160 characters.");return 0;}cfg.nessieAlertTemplate=value;save();return status();}
    private static int color(String destination,String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw;if(value.length()==6)value="FF"+value;if(value.length()!=8)throw new NumberFormatException();int color=(int)Long.parseLong(value,16);if(destination.equalsIgnoreCase("driptoad"))cfg.nessieDriptoadColor=color;else if(destination.equalsIgnoreCase("jade"))cfg.nessieJadeColor=color;else{local("Destination must be driptoad or jade.");return 0;}save();return status();}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB hex.");return 0;}}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"alert"->cfg.nessieDestinationAlert=value;case"title"->cfg.nessieAlertTitle=value;case"chat"->cfg.nessieAlertChat=value;case"sound"->cfg.nessieAlertSound=value;case"autoshare"->cfg.nessieAutoShare=value;case"share"->cfg.nessieClickableShare=value;case"recent"->cfg.nessieRequireRecentHook=value;case"driptoad"->cfg.nessieTrackDriptoad=value;case"jade"->cfg.nessieTrackJade=value;case"guidance"->cfg.nessieGuidance=value;case"box"->cfg.nessieMarkerBox=value;case"beam"->cfg.nessieMarkerBeam=value;case"line"->cfg.nessieMarkerLine=value;case"label"->cfg.nessieMarkerLabel=value;case"distance"->cfg.nessieMarkerDistance=value;case"throughwalls"->cfg.nessieThroughWalls=value;default->{local("Option must be alert, title, chat, sound, autoshare, share, recent, driptoad, jade, guidance, box, beam, line, label, distance, or throughwalls.");return 0;}}save();return status();}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){localComponent(Component.literal("\u00a75[Nessie] \u00a7f"+text));}
    private static void localComponent(Component component){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(component);}
}
