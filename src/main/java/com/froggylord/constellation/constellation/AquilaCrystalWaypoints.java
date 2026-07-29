package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/WishingCompassSolver.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CrystalsLocationsManager.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/MiningLocationLabel.java
public final class AquilaCrystalWaypoints {
    public enum Type {
        UNKNOWN("Unknown"),JUNGLE_TEMPLE("Jungle Temple"),MINES_OF_DIVAN("Mines of Divan"),
        GOBLIN_QUEENS_DEN("Goblin Queen's Den"),LOST_PRECURSOR_CITY("Lost Precursor City"),
        KHAZAD_DUM("Khazad-dum"),FAIRY_GROTTO("Fairy Grotto"),DRAGONS_LAIR("Dragon's Lair"),
        CORLEONE("Corleone"),KING_YOLKAR("King Yolkar"),ODAWA("Odawa"),
        KEY_GUARDIAN("Key Guardian"),XALX("Xalx");
        final String name;Type(String name){this.name=name;}public String display(){return name;}
    }
    public enum SolverState { READY, FIRST_TRAIL, MOVE_FOR_SECOND, SECOND_TRAIL, SOLVED }
    private enum Zone { NUCLEUS,JUNGLE,MITHRIL,GOBLIN,PRECURSOR,MAGMA }
    public record Waypoint(Type type,BlockPos pos,String source,long addedAt) {}
    public record State(SolverState solver,int firstParticles,int secondParticles,int required,Map<Type,Waypoint> waypoints,Waypoint nearest,double nearestDistance) {}
    private static final Pattern COORDS=Pattern.compile("\\Dx?(\\d{3})(?=[, ]),? ?y?(\\d{2,3})(?=[, ]),? ?z?(\\d{3})\\D?(?!\\d)",Pattern.CASE_INSENSITIVE);
    private static final Map<Type,Waypoint> WAYPOINTS=new LinkedHashMap<>();
    private static final Map<Zone,AABB> ZONES=Map.of(
        Zone.NUCLEUS,new AABB(462,63,461,564,181,565),Zone.JUNGLE,new AABB(201,63,201,513,189,513),
        Zone.MITHRIL,new AABB(512,63,201,824,189,513),Zone.GOBLIN,new AABB(201,63,512,513,189,824),
        Zone.PRECURSOR,new AABB(512,63,512,824,189,824),Zone.MAGMA,new AABB(201,30,201,824,64,824));
    private static final Vec3 TEMPLE_DOOR_OFFSET=new Vec3(-57,36,-21);
    private static final String COMPASS="WISHING_COMPASS";
    private static AquilaConfig cfg;
    private static SolverState solver=SolverState.READY;
    private static Vec3 startOne=Vec3.ZERO,startTwo=Vec3.ZERO,directionOne=Vec3.ZERO,directionTwo=Vec3.ZERO,lastParticle=Vec3.ZERO;
    private static int particlesOne,particlesTwo;
    private static long lastParticleAt;
    private static boolean initialized;

    private AquilaCrystalWaypoints() {}

    public static void init(AquilaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof ClientboundLevelParticlesPacket p)particle(p);});
        UseItemCallback.EVENT.register((player,level,hand)->use(player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player,level,hand,hit)->use(player.getItemInHand(hand)));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(message.getString());return true;});
        ConstellationClient.tick().every(20,"aquila-crystal-waypoints",AquilaCrystalWaypoints::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetAll());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetAll());
    }

    private static InteractionResult use(ItemStack stack){
        if(!active()||!cfg.crystalWaypointsCompassSolver||!COMPASS.equals(LyraTooltips.marketId(stack)))return InteractionResult.PASS;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return InteractionResult.PASS;
        Vec3 eye=mc.player.position().add(0,1.62,0);Zone zone=zone(eye);long now=System.currentTimeMillis();
        switch(solver){
            case READY,SOLVED->{
                if(zone==Zone.NUCLEUS){local("Use the Wishing Compass outside the Crystal Nucleus.");return cfg.crystalWaypointsCompassGuard?InteractionResult.FAIL:InteractionResult.PASS;}
                startFirst(eye);return InteractionResult.PASS;
            }
            case MOVE_FOR_SECOND->{
                if(startOne.closerThan(eye,Math.clamp(cfg.crystalWaypointsUseDistance,4,32))){local("Move at least "+cfg.crystalWaypointsUseDistance+" blocks before the second use.");return cfg.crystalWaypointsCompassGuard?InteractionResult.FAIL:InteractionResult.PASS;}
                if(zone!=zone(startOne)){local("Zone changed; the new compass trail is now the first reading.");startFirst(eye);return InteractionResult.PASS;}
                startSecond(eye);return InteractionResult.PASS;
            }
            case FIRST_TRAIL,SECOND_TRAIL->{
                if(now-lastParticleAt<Math.clamp(cfg.crystalWaypointsParticleDelayMillis,100,2000)){local("Wait for the current compass trail to finish.");return cfg.crystalWaypointsCompassGuard?InteractionResult.FAIL:InteractionResult.PASS;}
                local("The last compass trail was incomplete; starting again.");startFirst(eye);return InteractionResult.PASS;
            }
        }
        return InteractionResult.PASS;
    }

    private static void startFirst(Vec3 eye){solver=SolverState.FIRST_TRAIL;startOne=eye;directionOne=Vec3.ZERO;particlesOne=0;lastParticle=eye;lastParticleAt=System.currentTimeMillis();}
    private static void startSecond(Vec3 eye){solver=SolverState.SECOND_TRAIL;startTwo=eye;directionTwo=Vec3.ZERO;particlesTwo=0;lastParticle=eye;lastParticleAt=System.currentTimeMillis();}

    private static void particle(ClientboundLevelParticlesPacket packet){
        if(!active()||!cfg.crystalWaypointsCompassSolver||packet.getParticle().getType()!=ParticleTypes.HAPPY_VILLAGER)return;
        if(solver!=SolverState.FIRST_TRAIL&&solver!=SolverState.SECOND_TRAIL)return;
        Vec3 point=new Vec3(packet.getX(),packet.getY(),packet.getZ());
        if(point.distanceTo(lastParticle)>Math.clamp(cfg.crystalWaypointsParticleGapTenths,2,20)/10.0)return;
        lastParticle=point;lastParticleAt=System.currentTimeMillis();int required=Math.clamp(cfg.crystalWaypointsParticlesPerLine,8,60);
        if(solver==SolverState.FIRST_TRAIL){
            Vec3 delta=point.subtract(startOne);if(delta.lengthSqr()==0)return;directionOne=directionOne.add(delta.normalize().scale(1.0/required));
            if(++particlesOne>=required){solver=SolverState.MOVE_FOR_SECOND;if(cfg.crystalWaypointsCompassChat)local("First compass trail captured. Move "+cfg.crystalWaypointsUseDistance+" blocks and use it again.");}
        }else{
            Vec3 delta=point.subtract(startTwo);if(delta.lengthSqr()==0)return;directionTwo=directionTwo.add(delta.normalize().scale(1.0/required));
            if(++particlesTwo>=required)solve();
        }
    }

    private static void solve(){
        Vec3 target=intersection(startOne,directionOne,startTwo,directionTwo,Math.clamp(cfg.crystalWaypointsIntersectionToleranceTenths,5,200)/10.0);
        if(target==null){local("The compass trails did not produce a reliable intersection.");resetSolver();return;}
        Zone source=zone(startOne);Type type=targetType(source);
        AABB allowed=ZONES.get(source);if(allowed==null||!allowed.inflate(100,0,100).contains(target))type=Type.UNKNOWN;
        if(type==Type.JUNGLE_TEMPLE)target=target.add(TEMPLE_DOOR_OFFSET);
        BlockPos pos=BlockPos.containing(target);
        if(!inHollows(pos)){local("The solved point was outside Crystal Hollows bounds.");resetSolver();return;}
        add(type,pos,"compass",cfg.crystalWaypointsCompassChat);solver=SolverState.SOLVED;
        Minecraft mc=Minecraft.getInstance();if(mc.player!=null){
            if(cfg.crystalWaypointsCompassTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(type.display()).withColor(color(type)&0xFFFFFF));}
            if(cfg.crystalWaypointsCompassSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.3f);
        }
    }

    static Vec3 intersection(Vec3 p1,Vec3 d1,Vec3 p2,Vec3 d2,double tolerance){
        double a=d1.dot(d1),b=d1.dot(d2),c=d2.dot(d2);Vec3 w=p1.subtract(p2);double d=d1.dot(w),e=d2.dot(w),den=a*c-b*b;
        if(a<1e-8||c<1e-8||Math.abs(den)<1e-8)return null;
        double s=(b*e-c*d)/den,t=(a*e-b*d)/den;if(s<0||t<0)return null;
        Vec3 one=p1.add(d1.scale(s)),two=p2.add(d2.scale(t));if(one.distanceTo(two)>=tolerance)return null;
        Vec3 result=one.add(two).scale(.5);return finite(result)?result:null;
    }

    private static void chat(String raw){
        if(!active())return;String text=strip(raw);
        if(text.trim().equals("The Wishing Compass can't seem to locate anything!")){resetSolver();return;}
        if(cfg.crystalWaypointsFindInChat&&text.contains(":")){
            String body=text.substring(text.indexOf(':')+1);Matcher matcher=COORDS.matcher(body);
            if(matcher.find()){BlockPos pos=new BlockPos(parse(matcher.group(1)),parse(matcher.group(2)),parse(matcher.group(3)));if(inHollows(pos)){Type type=typeFromText(body);if(type!=null&&!WAYPOINTS.containsKey(type))add(type,pos,"chat",true);}}
        }
        Type linked=linked(text);if(linked!=null&&!WAYPOINTS.containsKey(linked)){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)add(linked,mc.player.blockPosition(),"area",false);}
    }

    private static void tick(){
        if(!active()){if(!WAYPOINTS.isEmpty()||solver!=SolverState.READY)resetAll();return;}
        if(cfg.crystalWaypointsFindFromArea){
            String joined=String.join(" ",ConstellationClient.loc().getSidebarLines());Type type=typeFromText(joined);
            if(type!=null&&type!=Type.UNKNOWN&&!WAYPOINTS.containsKey(type)){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)add(type,mc.player.blockPosition(),"area",false);}
        }
        if(cfg.crystalWaypointsAutoRemoveReached){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)WAYPOINTS.entrySet().removeIf(e->e.getValue().pos.distToCenterSqr(mc.player.position())<=Math.pow(Math.clamp(cfg.crystalWaypointsReachDistance,1,20),2));}
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||WAYPOINTS.isEmpty())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        List<Waypoint> list=new ArrayList<>(WAYPOINTS.values());list.removeIf(w->w.pos.distToCenterSqr(mc.player.position())>Math.pow(Math.clamp(cfg.crystalWaypointsRenderRange,32,2000),2));
        list.sort(Comparator.comparingDouble(w->w.pos.distToCenterSqr(mc.player.position())));if(cfg.crystalWaypointsOnlyNearest&&list.size()>1)list=list.subList(0,1);
        for(Waypoint waypoint:list){Vec3 center=Vec3.atCenterOf(waypoint.pos);int color=color(waypoint.type);double distance=Math.sqrt(waypoint.pos.distToCenterSqr(mc.player.position()));
            if(cfg.crystalWaypointsShowBox)ctx.highlight(new AABB(waypoint.pos),color,cfg.crystalWaypointsThroughWalls);
            if(cfg.crystalWaypointsShowBeam)ctx.beam(center.x,waypoint.pos.getY(),center.z,color,Math.clamp(cfg.crystalWaypointsBeamHeight,4,32),cfg.crystalWaypointsThroughWalls);
            if(cfg.crystalWaypointsShowLine)ctx.line(mc.player.getEyePosition(),center,color,cfg.crystalWaypointsThroughWalls);
            if(cfg.crystalWaypointsShowLabel){String label=waypoint.type.display();if(cfg.crystalWaypointsShowDistance)label+=" "+Math.round(distance)+"m";ctx.label(center.add(0,2,0),label,color,cfg.crystalWaypointsThroughWalls);}
        }
    }

    public static State state(){if(!active())return null;Minecraft mc=Minecraft.getInstance();Waypoint nearest=null;double distance=0;if(mc.player!=null&&!WAYPOINTS.isEmpty()){nearest=WAYPOINTS.values().stream().min(Comparator.comparingDouble(w->w.pos.distToCenterSqr(mc.player.position()))).orElse(null);if(nearest!=null)distance=Math.sqrt(nearest.pos.distToCenterSqr(mc.player.position()));}return new State(solver,particlesOne,particlesTwo,Math.clamp(cfg.crystalWaypointsParticlesPerLine,8,60),Map.copyOf(WAYPOINTS),nearest,distance);}
    public static boolean visible(){State state=state();return cfg.crystalWaypointsHud&&state!=null&&(state.solver!=SolverState.READY||!state.waypoints.isEmpty());}
    public static AquilaConfig config(){return cfg;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.wishingCompassHelper&&cfg.crystalWaypointsSuite&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()==SkyblockArea.CRYSTAL_HOLLOWS;}
    private static boolean inHollows(BlockPos pos){return pos.getX()>=202&&pos.getX()<=823&&pos.getZ()>=202&&pos.getZ()<=823&&pos.getY()>=31&&pos.getY()<=188;}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static int parse(String raw){try{return Integer.parseInt(raw);}catch(Exception ignored){return 0;}}
    private static Zone zone(Vec3 pos){return ZONES.entrySet().stream().filter(e->e.getValue().contains(pos)).map(Map.Entry::getKey).findFirst().orElse(Zone.NUCLEUS);}
    private static Type targetType(Zone zone){if(zoneComplete(zone))return Type.UNKNOWN;return switch(zone){case JUNGLE->hasItem("JUNGLE_KEY")?Type.JUNGLE_TEMPLE:Type.ODAWA;case MITHRIL->Type.MINES_OF_DIVAN;case GOBLIN->hasEffect("King's Scent")?Type.GOBLIN_QUEENS_DEN:Type.KING_YOLKAR;case PRECURSOR->Type.LOST_PRECURSOR_CITY;case MAGMA->Type.KHAZAD_DUM;default->Type.UNKNOWN;};}
    private static boolean zoneComplete(Zone zone){String crystal=switch(zone){case JUNGLE->"Amethyst";case MITHRIL->"Jade";case GOBLIN->"Amber";case PRECURSOR->"Sapphire";case MAGMA->"Topaz";default->"";};if(crystal.isEmpty())return false;return TabList.lines().stream().anyMatch(s->s.startsWith(crystal+":")&&!s.contains("Not Found"));}
    private static boolean hasEffect(String name){return TabList.lines().stream().anyMatch(s->s.startsWith(name));}
    private static boolean hasItem(String id){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(ItemStack stack:mc.player.getInventory().getNonEquipmentItems())if(id.equals(LyraTooltips.marketId(stack)))return true;return false;}
    private static Type linked(String text){String spacer="                                ";if(text.startsWith("[NPC] Kalhuiki Door Guardian:"))return Type.JUNGLE_TEMPLE;if(text.startsWith(spacer+"Jade Crystal"))return Type.MINES_OF_DIVAN;if(text.startsWith(spacer+"Amber Crystal"))return Type.GOBLIN_QUEENS_DEN;if(text.startsWith(spacer+"Sapphire Crystal"))return Type.LOST_PRECURSOR_CITY;if(text.startsWith(spacer+"Topaz Crystal"))return Type.KHAZAD_DUM;if(text.startsWith("[NPC] Golden Dragon:"))return Type.DRAGONS_LAIR;if(text.startsWith("[NPC] King Yolkar:"))return Type.KING_YOLKAR;if(text.startsWith("[NPC] Odawa:"))return Type.ODAWA;if(text.startsWith("[NPC] Xalx:"))return Type.XALX;return null;}
    private static Type typeFromText(String raw){String text=raw.toLowerCase(Locale.ROOT);for(Type type:Type.values()){String name=type.display().toLowerCase(Locale.ROOT);if(text.contains(name))return type;}if(text.contains("divan"))return Type.MINES_OF_DIVAN;if(text.contains("precursor city"))return Type.LOST_PRECURSOR_CITY;if(text.contains("goblin queen"))return Type.GOBLIN_QUEENS_DEN;if(text.contains("khazad"))return Type.KHAZAD_DUM;if(text.contains("grotto"))return Type.FAIRY_GROTTO;if(text.contains("dragon"))return Type.DRAGONS_LAIR;if(text.contains("yolkar"))return Type.KING_YOLKAR;if(text.contains("key guardian"))return Type.KEY_GUARDIAN;return null;}
    private static String strip(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value;}
    private static int color(Type type){return switch(type){case UNKNOWN->cfg.crystalWaypointUnknownColor;case JUNGLE_TEMPLE->cfg.crystalWaypointJungleColor;case MINES_OF_DIVAN->cfg.crystalWaypointDivanColor;case GOBLIN_QUEENS_DEN->cfg.crystalWaypointGoblinColor;case LOST_PRECURSOR_CITY->cfg.crystalWaypointCityColor;case KHAZAD_DUM->cfg.crystalWaypointKhazadColor;case FAIRY_GROTTO->cfg.crystalWaypointGrottoColor;case DRAGONS_LAIR->cfg.crystalWaypointDragonColor;case CORLEONE->cfg.crystalWaypointCorleoneColor;case KING_YOLKAR->cfg.crystalWaypointYolkarColor;case ODAWA->cfg.crystalWaypointOdawaColor;case KEY_GUARDIAN->cfg.crystalWaypointKeyGuardianColor;case XALX->cfg.crystalWaypointXalxColor;};}
    private static void add(Type type,BlockPos pos,String source,boolean announce){WAYPOINTS.put(type,new Waypoint(type,pos,source,System.currentTimeMillis()));if(announce)local("Added "+type.display()+" at "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+".");}
    private static void resetSolver(){solver=SolverState.READY;startOne=startTwo=directionOne=directionTwo=lastParticle=Vec3.ZERO;particlesOne=particlesTwo=0;lastParticleAt=0;}
    private static void resetAll(){WAYPOINTS.clear();resetSolver();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("crystalwaypoints").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c->list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{WAYPOINTS.clear();local("Crystal waypoints cleared.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetsolver").executes(c->{resetSolver();local("Wishing Compass solver reset.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->remove(StringArgumentType.getString(c,"name")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("share").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->share(StringArgumentType.getString(c,"name")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("x",IntegerArgumentType.integer(202,823)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("y",IntegerArgumentType.integer(31,188)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("z",IntegerArgumentType.integer(202,823)).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->addCommand(IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z"),StringArgumentType.getString(c,"name"))))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tuning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("particles",IntegerArgumentType.integer(8,60)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("distance",IntegerArgumentType.integer(4,32)).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("toleranceTenths",IntegerArgumentType.integer(5,200)).executes(c->{cfg.crystalWaypointsParticlesPerLine=IntegerArgumentType.getInteger(c,"particles");cfg.crystalWaypointsUseDistance=IntegerArgumentType.getInteger(c,"distance");cfg.crystalWaypointsIntersectionToleranceTenths=IntegerArgumentType.getInteger(c,"toleranceTenths");save();return status();}))))));
    }
    private static int status(){State s=state();local(s==null?"Crystal waypoints are unavailable.":"Solver "+s.solver.name().toLowerCase(Locale.ROOT)+", "+s.waypoints.size()+" waypoint"+(s.waypoints.size()==1?"":"s")+".");return 1;}
    private static int list(){if(WAYPOINTS.isEmpty()){local("No active Crystal Hollows waypoints.");return 1;}for(Waypoint w:WAYPOINTS.values())local(w.type.display()+": "+w.pos.getX()+" "+w.pos.getY()+" "+w.pos.getZ()+" ("+w.source+")");return 1;}
    private static Type findType(String raw){String value=raw.trim().replace('_',' ');for(Type type:Type.values())if(type.name().equalsIgnoreCase(raw)||type.display().equalsIgnoreCase(value))return type;return null;}
    private static int addCommand(int x,int y,int z,String raw){Type type=findType(raw);if(type==null){local("Unknown Crystal Hollows location name.");return 0;}add(type,new BlockPos(x,y,z),"manual",true);return 1;}
    private static int remove(String raw){Type type=findType(raw);if(type==null||WAYPOINTS.remove(type)==null){local("No matching waypoint was active.");return 0;}local(type.display()+" removed.");return 1;}
    private static int share(String raw){Type type=findType(raw);Waypoint waypoint=type==null?null:WAYPOINTS.get(type);if(waypoint==null){local("No matching waypoint was active.");return 0;}if(!cfg.crystalWaypointsPartyShare){local("Party waypoint sharing is disabled.");return 0;}if(type==Type.FAIRY_GROTTO&&!cfg.crystalWaypointsShareFairyGrotto){local("Fairy Grotto sharing is disabled.");return 0;}Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.player.connection==null)return 0;mc.player.connection.sendCommand("pc "+type.display()+": "+waypoint.pos.getX()+", "+waypoint.pos.getY()+", "+waypoint.pos.getZ());return 1;}
    private static int color(String raw,String hex){Type type=findType(raw);if(type==null){local("Unknown Crystal Hollows location name.");return 0;}String clean=hex.replace("#","");if(clean.length()==6)clean="FF"+clean;long value;try{if(clean.length()!=8)throw new IllegalArgumentException();value=Long.parseLong(clean,16);}catch(Exception e){local("Color must be six-digit RGB or eight-digit ARGB hex.");return 0;}int color=(int)value;switch(type){case UNKNOWN->cfg.crystalWaypointUnknownColor=color;case JUNGLE_TEMPLE->cfg.crystalWaypointJungleColor=color;case MINES_OF_DIVAN->cfg.crystalWaypointDivanColor=color;case GOBLIN_QUEENS_DEN->cfg.crystalWaypointGoblinColor=color;case LOST_PRECURSOR_CITY->cfg.crystalWaypointCityColor=color;case KHAZAD_DUM->cfg.crystalWaypointKhazadColor=color;case FAIRY_GROTTO->cfg.crystalWaypointGrottoColor=color;case DRAGONS_LAIR->cfg.crystalWaypointDragonColor=color;case CORLEONE->cfg.crystalWaypointCorleoneColor=color;case KING_YOLKAR->cfg.crystalWaypointYolkarColor=color;case ODAWA->cfg.crystalWaypointOdawaColor=color;case KEY_GUARDIAN->cfg.crystalWaypointKeyGuardianColor=color;case XALX->cfg.crystalWaypointXalxColor=color;}save();local(type.display()+" color updated.");return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.crystalWaypointsSuite=v;case"hud"->cfg.crystalWaypointsHud=v;case"chat"->cfg.crystalWaypointsFindInChat=v;case"area"->cfg.crystalWaypointsFindFromArea=v;case"solver"->cfg.crystalWaypointsCompassSolver=v;case"guard"->cfg.crystalWaypointsCompassGuard=v;case"box"->cfg.crystalWaypointsShowBox=v;case"beam"->cfg.crystalWaypointsShowBeam=v;case"line"->cfg.crystalWaypointsShowLine=v;case"label"->cfg.crystalWaypointsShowLabel=v;case"distance"->cfg.crystalWaypointsShowDistance=v;case"walls"->cfg.crystalWaypointsThroughWalls=v;case"nearest"->cfg.crystalWaypointsOnlyNearest=v;case"autoremove"->cfg.crystalWaypointsAutoRemoveReached=v;case"party"->cfg.crystalWaypointsPartyShare=v;case"grotto"->cfg.crystalWaypointsShareFairyGrotto=v;default->{local("Unknown crystal-waypoint option.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7b[Crystal Waypoints] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
