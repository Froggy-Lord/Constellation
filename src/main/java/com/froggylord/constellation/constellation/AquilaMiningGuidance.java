package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CommissionLabels.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/MiningLocationLabel.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/Fetchur.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/Puzzler.java
// ported from SkyOcean (MIT): features/mining/PuzzlerSolver.kt
public final class AquilaMiningGuidance {
    public record DailyState(String fetchurRequest,String fetchurAnswer,long fetchurAt,BlockPos puzzler,long puzzlerAt) {}
    private record Place(String name,BlockPos pos,Kind kind) {}
    private enum Kind { DWARVEN,TITANIUM,GLACITE,BASE,EMISSARY,PUZZLER }
    private static final String FETCHUR_PREFIX="[NPC] Fetchur: ";
    private static final String PUZZLER_PREFIX="[NPC] Puzzler: ";
    private static final Map<String,String> FETCHUR=new LinkedHashMap<>();
    private static final List<Place> DWARVEN=List.of(
        new Place("Lava Springs",new BlockPos(60,197,-15),Kind.DWARVEN),new Place("Cliffside Veins",new BlockPos(40,128,40),Kind.DWARVEN),
        new Place("Rampart's Quarry",new BlockPos(-100,150,-20),Kind.DWARVEN),new Place("Upper Mines",new BlockPos(-130,174,-50),Kind.DWARVEN),
        new Place("Royal Mines",new BlockPos(130,154,30),Kind.DWARVEN),new Place("Glacite Walker",new BlockPos(0,128,150),Kind.DWARVEN));
    private static final List<Place> EMISSARIES=List.of(
        new Place("Emissary",new BlockPos(58,198,-8),Kind.EMISSARY),new Place("Emissary",new BlockPos(42,134,22),Kind.EMISSARY),
        new Place("Emissary",new BlockPos(-72,153,-10),Kind.EMISSARY),new Place("Emissary",new BlockPos(-132,174,-50),Kind.EMISSARY),
        new Place("Emissary",new BlockPos(171,150,31),Kind.EMISSARY),new Place("Emissary",new BlockPos(-37,200,-131),Kind.EMISSARY),
        new Place("Emissary",new BlockPos(89,198,-92),Kind.EMISSARY));
    private static final Map<String,List<BlockPos>> GLACITE=Map.of(
        "Aquamarine",List.of(new BlockPos(20,136,370),new BlockPos(-14,132,386),new BlockPos(6,137,411),new BlockPos(50,117,302)),
        "Onyx",List.of(new BlockPos(4,127,307),new BlockPos(-3,139,434),new BlockPos(77,118,411),new BlockPos(-68,130,404)),
        "Peridot",List.of(new BlockPos(66,144,284),new BlockPos(94,154,284),new BlockPos(-62,147,303),new BlockPos(-77,119,283),new BlockPos(87,122,394),new BlockPos(-73,122,456)),
        "Citrine",List.of(new BlockPos(-86,143,261),new BlockPos(74,150,327),new BlockPos(63,137,343),new BlockPos(38,119,386),new BlockPos(55,150,400),new BlockPos(-45,127,415),new BlockPos(-60,144,424),new BlockPos(-54,132,410)));
    private static AquilaConfig cfg;
    private static String fetchurRequest="",fetchurAnswer="";
    private static long fetchurAt,puzzlerAt;
    private static BlockPos puzzler;
    private static Object levelIdentity;
    private static String profileKey="";

    static {
        FETCHUR.put("yellow and see through","Yellow Stained Glass");FETCHUR.put("circular and sometimes moves","Compass");FETCHUR.put("expensive minerals","Mithril");
        FETCHUR.put("useful during celebrations","Firework Rocket");FETCHUR.put("hot and gives energy","Cheap, Decent, or Black Coffee");FETCHUR.put("tall and can be opened","Any Wooden Door or Iron Door");
        FETCHUR.put("brown and fluffy","Rabbit's Foot");FETCHUR.put("explosive but more than usual","Superboom TNT");FETCHUR.put("wearable and grows","Pumpkin");
        FETCHUR.put("shiny and makes sparks","Flint and Steel");FETCHUR.put("green and some dudes trade stuff for it","Emerald");FETCHUR.put("red and soft","Red Wool");
    }
    private AquilaMiningGuidance() {}
    public static void init(AquilaConfig config){cfg=config;ConstellationClient.tick().every(20,"aquila-mining-guidance",AquilaMiningGuidance::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay||onChat(clean(message.getString())));ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}
    private static void tick(){Minecraft mc=Minecraft.getInstance();String current=LyraStorageValue.currentProfileKey();if(!current.equals(profileKey)){profileKey=current;resetState();levelIdentity=mc.level;return;}if(mc.level!=levelIdentity){levelIdentity=mc.level;puzzler=null;puzzlerAt=0;}if(ConstellationClient.loc().area()!=SkyblockArea.DWARVEN_MINES){puzzler=null;puzzlerAt=0;}}
    private static boolean onChat(String message){if(cfg==null||!cfg.enabled||!cfg.miningGuidanceSuite||ConstellationClient.loc().area()!=SkyblockArea.DWARVEN_MINES)return true;if(message.startsWith(FETCHUR_PREFIX)&&cfg.fetchurSolver&&cfg.fetchurAnswer){String request=message.substring(FETCHUR_PREFIX.length()).toLowerCase(Locale.ROOT).replaceFirst("^(?:its|theyre)\\s+","").trim();String answer=FETCHUR.get(request);if(answer!=null){fetchurRequest=request;fetchurAnswer=answer;fetchurAt=System.currentTimeMillis();local("Fetchur wants: "+answer+".");return !cfg.fetchurReplaceRiddle;}}if(message.startsWith(PUZZLER_PREFIX)&&cfg.puzzlerSolver){String path=message.substring(PUZZLER_PREFIX.length()).trim();BlockPos solved=solve(path);if(solved!=null){puzzler=solved;puzzlerAt=System.currentTimeMillis();local("Puzzler target: "+solved.getX()+", "+solved.getY()+", "+solved.getZ()+".");}}return true;}
    private static BlockPos solve(String path){if(path.codePointCount(0,path.length())!=10)return null;int x=181,z=135;for(int i=0;i<path.length();){int cp=path.codePointAt(i);i+=Character.charCount(cp);if(cp==0x25B2)z++;else if(cp==0x25BC)z--;else if(cp==0x25C0)x++;else if(cp==0x25B6)x--;else return null;}return new BlockPos(x,195,z);}
    public static void draw(WorldRenderer.Ctx ctx){if(cfg==null||!cfg.enabled||!cfg.miningGuidanceSuite)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;for(Place p:waypoints(mc))draw(ctx,p,mc);if(ConstellationClient.loc().area()==SkyblockArea.DWARVEN_MINES&&cfg.puzzlerSolver&&cfg.puzzlerWorldHighlight&&puzzler!=null&&fresh(puzzlerAt)){Place p=new Place("Puzzler Target",puzzler,Kind.PUZZLER);draw(ctx,p,mc);}}
    private static List<Place> waypoints(Minecraft mc){if(!cfg.commissionWaypoints)return List.of();SkyblockArea area=ConstellationClient.loc().area();List<Place> out=new ArrayList<>();List<AquilaMiningProgress.Commission> commissions=AquilaMiningProgress.commissions();if(area==SkyblockArea.DWARVEN_MINES&&cfg.commissionDwarvenWaypoints){for(var c:commissions){for(Place p:DWARVEN)if(c.name().contains(p.name()))out.add(new Place(p.name(),p.pos(),c.name().contains("Titanium")?Kind.TITANIUM:Kind.DWARVEN));}if(cfg.commissionShowEmissaries&&commissions.stream().anyMatch(AquilaMiningProgress.Commission::done)&&(!cfg.commissionHideEmissariesWithPigeon||!hasPigeon()))out.addAll(EMISSARIES);}if(area==SkyblockArea.GLACITE_TUNNELS&&cfg.commissionGlaciteWaypoints){for(var c:commissions)for(var e:GLACITE.entrySet())if(c.name().contains(e.getKey())){List<BlockPos> positions=e.getValue();if(cfg.commissionOnlyNearestGemstone)positions.stream().min(Comparator.comparingDouble(p->p.distToCenterSqr(mc.player.position()))).ifPresent(p->out.add(new Place(e.getKey(),p,Kind.GLACITE)));else for(BlockPos p:positions)out.add(new Place(e.getKey(),p,Kind.GLACITE));}if(cfg.commissionShowBaseCamp)out.add(new Place("Base Camp",new BlockPos(-7,126,229),Kind.BASE));}return out;}
    private static void draw(WorldRenderer.Ctx ctx,Place p,Minecraft mc){Vec3 center=Vec3.atCenterOf(p.pos());int color=color(p.kind());boolean puzzle=p.kind()==Kind.PUZZLER;if((puzzle&&cfg.puzzlerShowBox)||(!puzzle&&cfg.commissionWaypointBox))ctx.highlight(new AABB(p.pos()),color,puzzle?cfg.puzzlerThroughWalls:cfg.commissionWaypointThroughWalls);if((puzzle&&cfg.puzzlerShowBeam)||(!puzzle&&cfg.commissionWaypointBeam))ctx.beam(center.x,center.y,center.z,color,cfg.miningGuidanceBeamHeight,puzzle?cfg.puzzlerThroughWalls:cfg.commissionWaypointThroughWalls);if((puzzle&&cfg.puzzlerShowLabel)||(!puzzle&&cfg.commissionWaypointLabel)){String label=p.name();if(!puzzle&&cfg.commissionWaypointDistance)label+=" "+Math.round(Math.sqrt(p.pos().distToCenterSqr(mc.player.position())))+"m";ctx.label(center.add(0,1.2,0),label,color,puzzle?cfg.puzzlerThroughWalls:cfg.commissionWaypointThroughWalls);}}
    private static boolean hasPigeon(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(var stack:mc.player.getInventory())if("ROYAL_PIGEON".equals(LyraTooltips.marketId(stack)))return true;return false;}
    public static DailyState state(){if(cfg==null||!cfg.enabled||!cfg.miningGuidanceSuite)return null;boolean dwarven=ConstellationClient.loc().area()==SkyblockArea.DWARVEN_MINES;boolean fetchur=cfg.fetchurSolver&&fresh(fetchurAt)&&(dwarven||cfg.miningDailyPersistFetchurOutsideDwarven);return new DailyState(fetchur?fetchurRequest:"",fetchur?fetchurAnswer:"",fetchurAt,dwarven&&cfg.puzzlerSolver&&fresh(puzzlerAt)?puzzler:null,puzzlerAt);}
    public static AquilaConfig config(){return cfg;}
    private static boolean fresh(long at){return at>0&&System.currentTimeMillis()-at<=Math.max(1,cfg.miningDailyDurationSeconds)*1000L;}
    private static int color(Kind kind){return switch(kind){case DWARVEN->cfg.miningDwarvenColor;case TITANIUM->cfg.miningTitaniumColor;case GLACITE->cfg.miningGlaciteColor;case BASE->cfg.miningBaseCampColor;case EMISSARY->cfg.miningEmissaryColor;case PUZZLER->cfg.miningPuzzlerColor;};}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningguidance").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{reset();local("Mining guidance cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(10,3600)).executes(c->{cfg.miningDailyDurationSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))));d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningguidancecolor").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->setColor(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))));}
    private static int status(){local("Commission waypoints "+(cfg.commissionWaypoints?"on":"off")+", Fetchur "+(fetchurAnswer.isEmpty()?"unknown":fetchurAnswer)+", Puzzler "+(puzzler==null?"unknown":puzzler.toShortString())+".");return 1;}
    private static int setColor(String target,String value){Integer color=parseColor(value);if(color==null){local("Color must be an eight-digit ARGB hex value.");return 0;}switch(target.toLowerCase(Locale.ROOT)){case"dwarven"->cfg.miningDwarvenColor=color;case"titanium"->cfg.miningTitaniumColor=color;case"glacite"->cfg.miningGlaciteColor=color;case"base"->cfg.miningBaseCampColor=color;case"emissary"->cfg.miningEmissaryColor=color;case"puzzler"->cfg.miningPuzzlerColor=color;default->{local("Target must be dwarven, titanium, glacite, base, emissary, or puzzler.");return 0;}}save();return 1;}
    private static Integer parseColor(String text){String s=text.startsWith("#")?text.substring(1):text;if(s.length()!=8)return null;try{return(int)Long.parseLong(s,16);}catch(Exception ignored){return null;}}
    private static String clean(String raw){String s=ChatFormatting.stripFormatting(raw);return s==null?"":s.trim();}
    private static void reset(){levelIdentity=null;profileKey="";resetState();}
    private static void resetState(){fetchurRequest="";fetchurAnswer="";fetchurAt=0;puzzler=null;puzzlerAt=0;}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Mining] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
