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
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): utils/HotspotUtils.kt
// ported from Feesh (Apache-2.0): features/chat/HotspotFoundMessage.kt
// ported from Feesh (Apache-2.0): features/alerts/HotspotGoneAlert.kt
// ported from SkyOcean (MIT): api/HotspotAPI.kt
// ported from SkyOcean (MIT): features/fishing/HotspotFeatures.kt
// radar math ported from SkyHanni (LGPL-3.0-or-later): features/fishing/FishingHotspotRadar.kt
// polynomial fitter ported from SkyHanni (LGPL-3.0-or-later): utils/PolynomialFitter.kt
public final class HydraHotspots {
    private enum Type { SEA_CREATURE,FISHING_SPEED,DOUBLE_HOOK,TREASURE,TROPHY_FISH,UNKNOWN }
    private static final class Spot { final UUID id; final ArmorStand stand; final Vec3 pos; Type type; String perk; double radius; boolean fishedIn; long lastFishedAt; Spot(ArmorStand stand){this.id=stand.getUUID();this.stand=stand;this.pos=stand.position();this.type=Type.UNKNOWN;this.perk="";} }
    private record Gone(UUID id,String perk,Type type,long due) {}
    private static final Pattern SEA=Pattern.compile("\\+\\d+.*Sea Creature Chance");
    private static final Pattern SPEED=Pattern.compile("\\+\\d+.*Fishing Speed");
    private static final Pattern DOUBLE=Pattern.compile("\\+\\d+.*Double Hook Chance");
    private static final Pattern TREASURE=Pattern.compile("\\+\\d+.*Treasure Chance");
    private static final Pattern TROPHY=Pattern.compile("\\+\\d+.*Trophy Chance");
    private static final Map<UUID,Spot> SPOTS=new HashMap<>();
    private static final ArrayDeque<UUID> REMEMBERED=new ArrayDeque<>();
    private static final List<Vec3> RADAR_POINTS=new ArrayList<>();
    private static HydraConfig cfg;
    private static boolean initialized,wasEnabled;
    private static int scanTicks;
    private static Spot nearest,hookSpot;
    private static Gone pendingGone;
    private static long radarUseAt,radarParticleAt,radarTargetAt;
    private static Vec3 radarTarget;
    private HydraHotspots() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-hotspots",HydraHotspots::tick);UseItemCallback.EVENT.register((player,level,hand)->{if(!active()||!cfg.hotspotRadarEnabled||!"HOTSPOT_RADAR".equals(LyraTooltips.marketId(player.getItemInHand(hand))))return InteractionResult.PASS;RADAR_POINTS.clear();radarTarget=null;radarUseAt=System.currentTimeMillis();return InteractionResult.PASS;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void tick(){boolean enabled=active();if(!enabled){if(wasEnabled)resetSession();wasEnabled=false;return;}wasEnabled=true;long now=System.currentTimeMillis();if(pendingGone!=null&&now>=pendingGone.due){Gone gone=pendingGone;pendingGone=null;if(SPOTS.containsKey(gone.id)||entityPresent(gone.id)||Minecraft.getInstance().level==null)return;goneAlert(gone);}if(radarTarget!=null&&(now-radarTargetAt>Math.clamp(cfg.hotspotRadarExpireSeconds,3,120)*1000L||playerDistance(radarTarget)<=Math.clamp(cfg.hotspotRadarArriveRange,2,30)))clearRadar();if(++scanTicks<Math.clamp(cfg.hotspotScanTicks,1,100))return;scanTicks=0;if(cfg.hotspotTrackNamed)scan();else clearNamed();}

    // ported from Feesh (Apache-2.0): utils/HotspotUtils.kt
    private static void scan(){Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;double goneRange=Math.clamp(cfg.hotspotGonePlayerRange,5,80),range=Math.max(Math.clamp(cfg.hotspotScanRange,10,128),Math.max(goneRange,Math.clamp(cfg.hotspotFoundRange,2,30))+5),range2=range*range;Map<UUID,ArmorStand> current=new HashMap<>();List<ArmorStand> stands=new ArrayList<>();for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.distanceToSqr(mc.player)<=range2){stands.add(stand);if(stand.hasCustomName()&&clean(stand.getName().getString()).equals("HOTSPOT"))current.put(stand.getUUID(),stand);}Set<UUID> gone=new HashSet<>(SPOTS.keySet());gone.removeAll(current.keySet());for(UUID id:gone){Spot old=SPOTS.remove(id);double playerDistance=old.pos.distanceTo(mc.player.position());if(playerDistance<=goneRange)REMEMBERED.remove(id);if(old==nearest)nearest=null;if(old==hookSpot&&playerDistance<=goneRange){int confirm=Math.max(Math.clamp(cfg.hotspotGoneConfirmTicks,1,20),Math.clamp(cfg.hotspotScanTicks,1,100)+1);pendingGone=new Gone(old.id,old.perk,old.type,System.currentTimeMillis()+confirm*50L);}}for(ArmorStand stand:current.values()){Spot spot=SPOTS.computeIfAbsent(stand.getUUID(),id->new Spot(stand));findPerk(spot,stands);}nearest=SPOTS.values().stream().min(Comparator.comparingDouble(s->s.pos.distanceToSqr(mc.player.position()))).orElse(null);trackHook(mc);found(mc);}
    private static boolean entityPresent(UUID id){Minecraft mc=Minecraft.getInstance();if(mc.level==null)return false;for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(id)&&entity instanceof ArmorStand stand&&stand.hasCustomName()&&clean(stand.getName().getString()).equals("HOTSPOT"))return true;return false;}

    private static void findPerk(Spot spot,List<ArmorStand> stands){for(ArmorStand candidate:stands){if(candidate==spot.stand||!candidate.hasCustomName()||candidate.getX()!=spot.pos.x||candidate.getZ()!=spot.pos.z||candidate.getY()>=spot.pos.y||spot.pos.y-candidate.getY()>1||candidate.getXRot()!=spot.stand.getXRot())continue;String perk=clean(candidate.getName().getString());Type type=type(perk);if(type!=Type.UNKNOWN){spot.perk=perk;spot.type=type;return;}}}
    private static Type type(String perk){if(SEA.matcher(perk).matches())return Type.SEA_CREATURE;if(SPEED.matcher(perk).matches())return Type.FISHING_SPEED;if(DOUBLE.matcher(perk).matches())return Type.DOUBLE_HOOK;if(TREASURE.matcher(perk).matches())return Type.TREASURE;if(TROPHY.matcher(perk).matches())return Type.TROPHY_FISH;return Type.UNKNOWN;}

    // ported from Feesh (Apache-2.0): features/chat/HotspotFoundMessage.kt
    private static void found(Minecraft mc){if(nearest==null||nearest.pos.distanceTo(mc.player.position())>Math.clamp(cfg.hotspotFoundRange,2,30)||!hasRod()||REMEMBERED.contains(nearest.id))return;REMEMBERED.addFirst(nearest.id);while(REMEMBERED.size()>Math.clamp(cfg.hotspotRememberCount,1,10))REMEMBERED.removeLast();if(cfg.hotspotFoundMessage){String perk=nearest.perk.isBlank()?"":nearest.perk+" ";mc.player.sendSystemMessage(Component.literal("\u00a77[Hotspot] \u00a7fYou found "+perk+"Hotspot.").append(shareButtons(nearest)));}if(cfg.hotspotFoundSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),0.8f,1.3f);if(cfg.hotspotAutoShare)share(nearest,cfg.hotspotAutoShareParty);}
    private static Component shareButtons(Spot spot){Component out=Component.empty();String message=message(spot);if(cfg.hotspotClickableParty)out=out.copy().append(Component.literal(" \u00a79[Party]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/pc "+message)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Share this hotspot to party chat")))));if(cfg.hotspotClickableAll)out=out.copy().append(Component.literal(" \u00a7e[All]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/ac "+message)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Share this hotspot to all chat")))));return out;}
    private static void share(Spot spot,boolean party){Minecraft mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.connection!=null)mc.player.connection.sendCommand((party?"pc ":"ac ")+message(spot));}
    private static String message(Spot spot){String perk=spot.perk.isBlank()?"":spot.perk+" ";String zone=" at "+areaName();String out=cfg.hotspotShareTemplate.replace("{x}",Integer.toString((int)Math.floor(spot.pos.x))).replace("{y}",Integer.toString((int)Math.floor(spot.pos.y))).replace("{z}",Integer.toString((int)Math.floor(spot.pos.z))).replace("{perk}",perk).replace("{zone}",zone);return out.length()>220?out.substring(0,220):out;}

    // ported from Feesh (Apache-2.0): features/alerts/HotspotGoneAlert.kt
    private static void trackHook(Minecraft mc){if(!cfg.hotspotGoneAlert||mc.player.fishing==null||cfg.hotspotGoneRequireHook&&!(mc.player.fishing.isInWater()||mc.player.fishing.isInLava()))return;double range=Math.clamp(cfg.hotspotGoneHookRange,1,20);Spot found=SPOTS.values().stream().filter(s->s.pos.distanceTo(mc.player.fishing.position())<=range).min(Comparator.comparingDouble(s->s.pos.distanceToSqr(mc.player.fishing.position()))).orElse(null);if(found!=null){hookSpot=found;found.fishedIn=true;found.lastFishedAt=System.currentTimeMillis();}}
    private static void goneAlert(Gone gone){if(!cfg.hotspotGoneAlert)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String perk=gone.perk.isBlank()?"":gone.perk+" ";if(cfg.hotspotGoneTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Hotspot is gone").withColor(color(gone.type)&0xFFFFFF));}if(cfg.hotspotGoneChat)mc.player.sendSystemMessage(Component.literal("\u00a7d[Hotspot] \u00a7f"+perk+"Hotspot is gone, find another one."));if(cfg.hotspotGoneSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),0.9f,0.7f);hookSpot=null;}

    // ported from SkyOcean (MIT): api/HotspotAPI.kt
    public static boolean onParticle(ClientboundLevelParticlesPacket packet){if(!active())return false;boolean radiusParticle=isRadiusParticle(packet);if(radiusParticle){Spot spot=closestHorizontal(new Vec3(packet.getX(),packet.getY(),packet.getZ()),maxRadius()+.5);if(spot!=null){double dx=packet.getX()-spot.pos.x,dz=packet.getZ()-spot.pos.z;spot.radius=Math.round(Math.sqrt(dx*dx+dz*dz)*2)/2.0;}}if(cfg.hotspotRadarEnabled&&packet.getParticle().getType()==ParticleTypes.FLAME&&packet.getCount()==1&&packet.getMaxSpeed()==0f)radarParticle(packet);return radiusParticle&&cfg.hotspotHideParticles&&(cfg.hotspotShowOutline||cfg.hotspotShowSurface);}
    private static boolean isRadiusParticle(ClientboundLevelParticlesPacket p){if(ConstellationClient.loc().area()==SkyblockArea.CRIMSON_ISLE)return p.getParticle().getType()==ParticleTypes.SMOKE&&(p.getCount()==5||p.getCount()==2);if(!(p.getParticle() instanceof DustParticleOptions dust)||p.getCount()!=0||p.getXDist()!=1f||p.getMaxSpeed()!=1f)return false;Vector3f c=dust.getColor();return near(c.x,1f)&&near(c.y,.4117647f)&&near(c.z,.7058824f);}
    private static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
    private static double maxRadius(){return switch(ConstellationClient.loc().area()){case CRIMSON_ISLE->25;case JERRY_WORKSHOP,LOTUS_ATOLL->16;default->9;};}
    private static Spot closestHorizontal(Vec3 p,double max){double max2=max*max;return SPOTS.values().stream().filter(s->{double dx=p.x-s.pos.x,dz=p.z-s.pos.z;return dx*dx+dz*dz<=max2;}).min(Comparator.comparingDouble(s->{double dx=p.x-s.pos.x,dz=p.z-s.pos.z;return dx*dx+dz*dz;})).orElse(null);}

    // ported from SkyHanni (LGPL-3.0-or-later): features/fishing/FishingHotspotRadar.kt
    private static void radarParticle(ClientboundLevelParticlesPacket p){long now=System.currentTimeMillis();if(now-radarUseAt>Math.clamp(cfg.hotspotRadarAbilityMillis,250,3000))return;Vec3 point=new Vec3(p.getX(),p.getY(),p.getZ());if(!RADAR_POINTS.isEmpty()){double distance=point.distanceTo(RADAR_POINTS.getLast());if(distance==0||distance>Math.clamp(cfg.hotspotRadarParticleGapTenths,5,100)/10.0)return;}RADAR_POINTS.add(point);radarParticleAt=now;if(RADAR_POINTS.size()<4)return;Vec3 guess=solveRadar();if(guess!=null&&guess.y>=-64&&guess.y<=400&&playerDistance(guess)<=1000){radarTarget=guess;radarTargetAt=now;}}
    private static Vec3 solveRadar(){double[][] coefficients=new double[3][];for(int axis=0;axis<3;axis++){coefficients[axis]=fit(axis);if(coefficients[axis]==null)return null;}Vec3 derivative=new Vec3(coefficients[0][1],coefficients[1][1],coefficients[2][1]);double length=derivative.length();if(!Double.isFinite(length)||length<1e-6)return null;double control=Math.sqrt(24*Math.sin(pitch(derivative)-Math.PI)+25);double t=3*control/length;return new Vec3(at(coefficients[0],t),at(coefficients[1],t),at(coefficients[2],t));}
    private static double[] fit(int axis){double[][] a=new double[4][5];for(int row=0;row<4;row++)for(int col=0;col<4;col++){double sum=0;for(int i=0;i<RADAR_POINTS.size();i++)sum+=Math.pow(i,row+col);a[row][col]=sum;}for(int row=0;row<4;row++){double sum=0;for(int i=0;i<RADAR_POINTS.size();i++){Vec3 p=RADAR_POINTS.get(i);double y=axis==0?p.x:axis==1?p.y:p.z;sum+=Math.pow(i,row)*y;}a[row][4]=sum;}for(int col=0;col<4;col++){int pivot=col;for(int row=col+1;row<4;row++)if(Math.abs(a[row][col])>Math.abs(a[pivot][col]))pivot=row;double[] swap=a[col];a[col]=a[pivot];a[pivot]=swap;if(Math.abs(a[col][col])<1e-10)return null;double d=a[col][col];for(int j=col;j<5;j++)a[col][j]/=d;for(int row=0;row<4;row++)if(row!=col){double f=a[row][col];for(int j=col;j<5;j++)a[row][j]-=f*a[col][j];}}return new double[]{a[0][4],a[1][4],a[2][4],a[3][4]};}
    private static double at(double[] c,double t){return ((c[3]*t+c[2])*t+c[1])*t+c[0];}
    // ported from SkyHanni (LGPL-3.0-or-later): utils/LocationUtils.kt
    private static double pitch(Vec3 derivative){double expected=-Math.atan2(derivative.y,Math.sqrt(derivative.x*derivative.x+derivative.z*derivative.z)),guess=expected,min=-Math.PI/2,max=Math.PI/2;for(int i=0;i<100;i++){double result=Math.atan2(Math.sin(guess)-.75,Math.cos(guess));if(result<expected){min=guess;guess=(min+max)/2;}else{max=guess;guess=(min+max)/2;}}return guess;}

    public static void draw(WorldRenderer.Ctx ctx){if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;double render=Math.clamp(cfg.hotspotRenderRange,10,256);for(Spot spot:SPOTS.values()){double distance=spot.pos.distanceTo(mc.player.position());if(distance>render||!cfg.hotspotShowAll&&spot!=nearest)continue;int color=color(spot.type);if(cfg.hotspotShowOutline&&spot.radius>0)circle(ctx,spot.pos,spot.radius,color,false);if(cfg.hotspotShowSurface&&spot.radius>0){int rings=Math.clamp(cfg.hotspotSurfaceRings,1,12);for(int i=1;i<=rings;i++)circle(ctx,spot.pos,spot.radius*i/(rings+1),color,true);}if(cfg.hotspotShowNearest&&spot==nearest)ctx.highlight(new AABB(spot.pos.x-.35,spot.pos.y-.35,spot.pos.z-.35,spot.pos.x+.35,spot.pos.y+.35,spot.pos.z+.35),color,cfg.hotspotThroughWalls);String label=cfg.hotspotShowPerk&&!spot.perk.isBlank()?spot.perk:"HOTSPOT";if(cfg.hotspotShowDistance)label+=" "+Math.round(distance)+"m";ctx.label(spot.pos.add(0,.7,0),label,color,cfg.hotspotThroughWalls);}if(radarTarget!=null){double distance=playerDistance(radarTarget);int color=cfg.hotspotRadarColor;if(cfg.hotspotRadarLine)ctx.line(mc.player.getEyePosition(),radarTarget,color,cfg.hotspotThroughWalls);if(cfg.hotspotRadarBox)ctx.highlight(new AABB(radarTarget.x-.4,radarTarget.y-.4,radarTarget.z-.4,radarTarget.x+.4,radarTarget.y+.4,radarTarget.z+.4),color,cfg.hotspotThroughWalls);if(cfg.hotspotRadarBeam)ctx.beam(radarTarget.x,radarTarget.y,radarTarget.z,color,12,cfg.hotspotThroughWalls);if(cfg.hotspotRadarLabel)ctx.label(radarTarget.add(0,.8,0),"RADAR HOTSPOT"+(cfg.hotspotRadarDistance?" "+Math.round(distance)+"m":""),color,cfg.hotspotThroughWalls);}}
    private static void circle(WorldRenderer.Ctx ctx,Vec3 center,double radius,int color,boolean faint){int segments=Math.clamp(cfg.hotspotCircleSegments,12,128),c=faint?((Math.min(0x55,color>>>24)<<24)|(color&0xFFFFFF)):color;Vec3 previous=center.add(radius,0,0);for(int i=1;i<=segments;i++){double angle=Math.PI*2*i/segments;Vec3 next=center.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);ctx.line(previous,next,c,cfg.hotspotThroughWalls);previous=next;}}
    private static int color(Type type){return switch(type){case SEA_CREATURE->cfg.hotspotSeaCreatureColor;case FISHING_SPEED->cfg.hotspotFishingSpeedColor;case DOUBLE_HOOK->cfg.hotspotDoubleHookColor;case TREASURE->cfg.hotspotTreasureColor;case TROPHY_FISH->cfg.hotspotTrophyColor;default->cfg.hotspotUnknownColor;};}

    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.hotspotRadarGuesser&&cfg.hotspotSuite&&ConstellationClient.loc().onHypixel()&&hotspotArea();}
    // ported from Feesh (Apache-2.0): utils/WorldUtils.kt
    private static boolean hotspotArea(){return switch(ConstellationClient.loc().area()){case HUB,BACKWATER_BAYOU,SPIDER_DEN,JERRY_WORKSHOP,LOTUS_ATOLL,PARK,CRIMSON_ISLE,TORRHUS_CANYON->true;default->false;};}
    private static boolean hasRod(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(int i=0;i<9;i++){ItemStack stack=mc.player.getInventory().getItem(i);if(stack.getItem() instanceof FishingRodItem)return true;}return false;}
    private static double playerDistance(Vec3 point){Minecraft mc=Minecraft.getInstance();return mc.player==null?Double.MAX_VALUE:mc.player.position().distanceTo(point);}
    private static String areaName(){String value=ConstellationClient.loc().area().name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder out=new StringBuilder();for(String word:value.split(" ")){if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void clearRadar(){radarTarget=null;radarTargetAt=0;RADAR_POINTS.clear();}
    private static void clearNamed(){SPOTS.clear();REMEMBERED.clear();nearest=null;hookSpot=null;pendingGone=null;}
    private static void reset(){wasEnabled=false;resetSession();}
    private static void resetSession(){SPOTS.clear();REMEMBERED.clear();RADAR_POINTS.clear();nearest=null;hookSpot=null;pendingGone=null;scanTicks=0;radarUseAt=0;radarParticleAt=0;radarTargetAt=0;radarTarget=null;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hotspots").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetSession();local("Hotspots and radar target cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("share").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("channel",StringArgumentType.word()).executes(c->shareCommand(StringArgumentType.getString(c,"channel"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(10,256)).executes(c->{cfg.hotspotRenderRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("segments").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(12,128)).executes(c->{cfg.hotspotCircleSegments=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->{String text=StringArgumentType.getString(c,"text").trim();if(text.isEmpty()||text.length()>220){local("Template must be 1-220 characters.");return 0;}cfg.hotspotShareTemplate=text;save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local(SPOTS.size()+" hotspots tracked; nearest "+(nearest==null?"none":Math.round(playerDistance(nearest.pos))+"m")+"; radar target "+(radarTarget==null?"none":Math.round(playerDistance(radarTarget))+"m")+".");return 1;}
    private static int shareCommand(String channel){if(nearest==null){local("No nearby hotspot is tracked.");return 0;}if(channel.equalsIgnoreCase("party"))share(nearest,true);else if(channel.equalsIgnoreCase("all"))share(nearest,false);else{local("Channel must be party or all.");return 0;}return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"outline"->cfg.hotspotShowOutline=value;case"surface"->cfg.hotspotShowSurface=value;case"particles"->cfg.hotspotHideParticles=value;case"found"->cfg.hotspotFoundMessage=value;case"gone"->cfg.hotspotGoneAlert=value;case"autoshare"->cfg.hotspotAutoShare=value;case"radar"->cfg.hotspotRadarEnabled=value;case"line"->cfg.hotspotRadarLine=value;case"box"->cfg.hotspotRadarBox=value;case"beam"->cfg.hotspotRadarBeam=value;case"label"->cfg.hotspotRadarLabel=value;default->{local("Option must be outline, surface, particles, found, gone, autoshare, radar, line, box, beam, or label.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7d[Hotspot] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
