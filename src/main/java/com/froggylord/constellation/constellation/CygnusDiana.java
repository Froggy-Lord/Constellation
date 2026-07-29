package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.CygnusConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CygnusDiana {
    private enum BurrowType { START, MOB, TREASURE }
    private record Burrow(BurrowType type, BlockPos pos, long seenAt) {}
    // ported from Devonian (GPL-3.0): features/diana/DianaMobTracker.kt
    private static final Pattern MOB = Pattern.compile("^(?:Woah|Yikes|Oi|Danger|Good Grief|Uh oh|Oh)! You dug out a? ?([\\w ]+)!$");
    private static final Set<String> MOBS = Set.of("Minos Hunter","Gaia Construct","Stranded Nymph","Siamese Lynxes","Cretan Bull","Harpy","Minotaur","Minos Champion","Minos Inquisitor","Sphinx","King Minos");
    // ported from Devonian (GPL-3.0): features/diana/DianaDropTracker.kt
    private static final Pattern DROP = Pattern.compile("^RARE DROP! (?:You dug out a )?([-() \\w]+?)(?: \\(\\+\\d+ .* Magic Find\\))?!?$");
    private static final Pattern COINS = Pattern.compile("^Wow! You dug out ([\\d,.]+) coins!$");
    private static final Set<String> DROPS = Set.of("Hilt of Revelations","Mythos Fragment","Dwarf Turtle Shelmet","Daedalus Stick","Griffin Feather","Enchanted Book (Chimera 1)","Cretan Urn","Washed-up Souvenir","Antique Remedies","Brain Food","Shimmering Wool","Fateful Stinger","Crown of Greed","Minos Relic","Crochet Tiger Plushie","Braided Griffin Feather");
    private static final Pattern INQUIS_COORDS = Pattern.compile("at Coords (-?\\d+) (-?\\d+) (-?\\d+)");
    private static final Pattern CHAIN = Pattern.compile("(?:dug out a Griffin Burrow|finished the Griffin burrow chain)!? \\((\\d+)/(\\d+)\\)");
    private static final List<Burrow> BURROWS = new ArrayList<>();
    private static CygnusConfig cfg;
    private static boolean initialized;
    private static Vec3 inquisitor;
    private static long inquisitorAt;
    private static long lastInquisitorAlert;
    private CygnusDiana() {}

    public static void init(CygnusConfig config) {
        cfg=config;
        maps();
        if(initialized)return;
        initialized=true;
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof ClientboundLevelParticlesPacket p)onParticle(p);});
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetWorld());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetWorld());
        ConstellationClient.tick().every(20,"cygnus-diana",CygnusDiana::tick);
    }

    private static void tick() {
        long now=System.currentTimeMillis();
        BURROWS.removeIf(b->now-b.seenAt>Math.clamp(cfg.dianaBurrowLifetimeSeconds,30,1800)*1000L);
        if(inquisitorAt>0&&now-inquisitorAt>Math.clamp(cfg.dianaInquisitorWaypointSeconds,10,600)*1000L){inquisitor=null;inquisitorAt=0;}
        if(!active())resetWorld();
    }

    // ported from Devonian (GPL-3.0): features/diana/BurrowWaypoint.kt
    private static void onParticle(ClientboundLevelParticlesPacket p) {
        if(!active()||!cfg.dianaBurrowWaypoints||p.getYDist()!=.1f)return;
        BurrowType type=null;
        if(p.getParticle().getType()==ParticleTypes.ENCHANTED_HIT&&p.getCount()==4&&p.getXDist()==.5f&&p.getZDist()==.5f)type=BurrowType.START;
        else if(p.getParticle().getType()==ParticleTypes.CRIT&&p.getCount()==3&&p.getXDist()==.5f&&p.getZDist()==.5f)type=BurrowType.MOB;
        else if(p.getParticle().getType()==ParticleTypes.DRIPPING_LAVA&&p.getCount()==2&&p.getXDist()==.35f&&p.getZDist()==.35f)type=BurrowType.TREASURE;
        if(type==null)return;
        BlockPos pos=new BlockPos((int)Math.floor(p.getX()),(int)Math.floor(p.getY())-1,(int)Math.floor(p.getZ()));
        BurrowType found=type;
        BURROWS.removeIf(b->b.pos.distManhattan(pos)<=1);
        BURROWS.add(new Burrow(found,pos,System.currentTimeMillis()));
    }

    private static void onChat(String text) {
        if(!active())return;
        Matcher mob=MOB.matcher(text);
        if(cfg.dianaMobTracker&&mob.matches()&&MOBS.contains(mob.group(1))) {
            add(cfg.dianaMobCounts,mob.group(1),1);
            if("Minos Inquisitor".equals(mob.group(1))) inquisitorAlert(text);
            save();
            return;
        }
        Matcher coords=INQUIS_COORDS.matcher(text);
        if(cfg.dianaInquisitorAlert&&text.contains("Inquisitor")&&coords.find()) {
            inquisitor=new Vec3(Integer.parseInt(coords.group(1))+.5,Integer.parseInt(coords.group(2)),Integer.parseInt(coords.group(3))+.5);
            inquisitorAt=System.currentTimeMillis();
            boolean relayed=text.contains("Party >")||text.contains("Guild >")||text.contains("From ")||text.contains("To ");
            inquisitorAlert(!relayed);
        }
        Matcher coins=COINS.matcher(text);
        if(cfg.dianaDropTracker&&coins.matches()){try{cfg.dianaCoinsDug+=(long)Double.parseDouble(coins.group(1).replace(",",""));save();}catch(NumberFormatException ignored){}return;}
        Matcher drop=DROP.matcher(text);
        if(cfg.dianaDropTracker&&drop.matches()&&DROPS.contains(drop.group(1))) {
            add(cfg.dianaDropCounts,drop.group(1),1);
            rareDropAlert(drop.group(1));
            save();
            return;
        }
        Matcher chain=CHAIN.matcher(text);
        if(chain.find()) {
            removeNearestBurrow();
            if(chain.group(1).equals(chain.group(2)))BURROWS.clear();
        }
    }

    private static void inquisitorAlert(String source) { inquisitorAlert(true); }
    private static void inquisitorAlert(boolean allowShare) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        long now=System.currentTimeMillis();if(now-lastInquisitorAlert<1500)return;lastInquisitorAlert=now;
        if(cfg.dianaInquisitorAlert&&cfg.dianaInquisitorTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Minos Inquisitor").withColor(cfg.dianaInquisitorColor&0xFFFFFF));}
        if(cfg.dianaInquisitorAlert&&cfg.dianaInquisitorSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,1,1);
        if(allowShare&&cfg.dianaInquisitorShare&&inquisitor!=null&&mc.getConnection()!=null)mc.getConnection().sendCommand("pc Inquisitor at "+(int)inquisitor.x+" "+(int)inquisitor.y+" "+(int)inquisitor.z);
    }
    private static void rareDropAlert(String name){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.dianaRareDropTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(name).withColor(0xFFAA00));}if(cfg.dianaRareDropSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.25f);}
    private static void removeNearestBurrow(){Minecraft mc=Minecraft.getInstance();if(mc.player==null||BURROWS.isEmpty())return;Burrow nearest=Collections.min(BURROWS,Comparator.comparingDouble(b->b.pos.distToCenterSqr(mc.player.position())));if(nearest.pos.distToCenterSqr(mc.player.position())<=100)BURROWS.remove(nearest);}

    public static void draw(WorldRenderer.Ctx ctx) {
        if(!active())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        double range=Math.clamp(cfg.dianaBurrowRange,32,1024);
        if(cfg.dianaBurrowWaypoints)for(Burrow b:List.copyOf(BURROWS)) {
            Vec3 center=new Vec3(b.pos.getX()+.5,b.pos.getY()+1,b.pos.getZ()+.5);
            double distance=mc.player.position().distanceTo(center);if(distance>range)continue;
            int color=color(b.type);boolean walls=cfg.dianaBurrowThroughWalls;
            if(cfg.dianaBurrowBox)ctx.highlight(new AABB(b.pos),color,walls);
            if(cfg.dianaBurrowBeam)ctx.beam(center.x,b.pos.getY()+1,center.z,color,8,walls);
            if(cfg.dianaBurrowLabel)ctx.label(center.add(0,1.2,0),label(b.type)+(cfg.dianaBurrowDistance?" "+Math.round(distance)+"m":""),color,walls);
        }
        if(cfg.dianaInquisitorWaypoint&&inquisitor!=null) {
            ctx.beam(inquisitor.x,inquisitor.y,inquisitor.z,cfg.dianaInquisitorColor,12,true);
            ctx.label(inquisitor.add(0,2,0),"Minos Inquisitor "+Math.round(mc.player.position().distanceTo(inquisitor))+"m",cfg.dianaInquisitorColor,true);
        }
    }

    public static String mobHud(){if(!active()||!cfg.dianaMobTracker)return null;maps();return cfg.dianaMobHudAll?summary(cfg.dianaMobCounts,11):"§6Inquisitors §f"+cfg.dianaMobCounts.getOrDefault("Minos Inquisitor",0)+" §5Champions §f"+cfg.dianaMobCounts.getOrDefault("Minos Champion",0)+" §6Minotaurs §f"+cfg.dianaMobCounts.getOrDefault("Minotaur",0);}
    public static String dropHud(){if(!active()||!cfg.dianaDropTracker)return null;maps();return cfg.dianaDropHudAll?summary(cfg.dianaDropCounts,16):"§6Chimera §f"+cfg.dianaDropCounts.getOrDefault("Enchanted Book (Chimera 1)",0)+" §6Sticks §f"+cfg.dianaDropCounts.getOrDefault("Daedalus Stick",0)+" §9Feathers §f"+cfg.dianaDropCounts.getOrDefault("Griffin Feather",0)+" §6Coins §f"+compact(cfg.dianaCoinsDug);}
    private static String summary(Map<String,Integer> map,int limit){return map.entrySet().stream().filter(e->e.getValue()>0).sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(limit).map(e->shortName(e.getKey())+" "+e.getValue()).reduce((a,b)->a+" | "+b).orElse("No tracked entries");}
    private static String shortName(String s){return s.replace("Enchanted Book (Chimera 1)","Chimera").replace("Minos ","");}
    private static String compact(long n){if(n<1000)return Long.toString(n);if(n<1_000_000)return String.format(Locale.ROOT,"%.1fk",n/1000d);return String.format(Locale.ROOT,"%.2fM",n/1_000_000d);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()==SkyblockArea.HUB;}
    private static int color(BurrowType t){return switch(t){case START->cfg.dianaBurrowStartColor;case MOB->cfg.dianaBurrowMobColor;case TREASURE->cfg.dianaBurrowTreasureColor;};}
    private static String label(BurrowType t){return switch(t){case START->"Start Burrow";case MOB->"Mob Burrow";case TREASURE->"Treasure Burrow";};}
    private static void add(Map<String,Integer> map,String key,int amount){maps();map.merge(key,amount,Integer::sum);}
    private static void maps(){if(cfg==null)return;if(cfg.dianaMobCounts==null)cfg.dianaMobCounts=new LinkedHashMap<>();if(cfg.dianaDropCounts==null)cfg.dianaDropCounts=new LinkedHashMap<>();}
    private static void resetWorld(){BURROWS.clear();inquisitor=null;inquisitorAt=0;lastInquisitorAlert=0;}
    private static String clean(String raw){String s=ChatFormatting.stripFormatting(raw);return s==null?"":s.trim();}
    private static void save(){if(cfg.dianaPersistentStats)ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("diana").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearwaypoints").executes(c->{resetWorld();local("World waypoints cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetmobs").executes(c->{maps();cfg.dianaMobCounts.clear();save();local("Mob counts reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetdrops").executes(c->{maps();cfg.dianaDropCounts.clear();cfg.dianaCoinsDug=0;save();local("Drop counts reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(32,1024)).executes(c->{cfg.dianaBurrowRange=IntegerArgumentType.getInteger(c,"blocks");ConstellationClient.saveConfig();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lifetime").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(30,1800)).executes(c->{cfg.dianaBurrowLifetimeSeconds=IntegerArgumentType.getInteger(c,"seconds");ConstellationClient.saveConfig();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->colorOption(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"argb")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){maps();local("Burrows "+BURROWS.size()+", mobs "+cfg.dianaMobCounts.values().stream().mapToInt(Integer::intValue).sum()+", drops "+cfg.dianaDropCounts.values().stream().mapToInt(Integer::intValue).sum()+", coins "+cfg.dianaCoinsDug+".");return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"burrows"->cfg.dianaBurrowWaypoints=v;case"box"->cfg.dianaBurrowBox=v;case"beam"->cfg.dianaBurrowBeam=v;case"label"->cfg.dianaBurrowLabel=v;case"distance"->cfg.dianaBurrowDistance=v;case"mobhud"->cfg.dianaMobHud=v;case"allmobs"->cfg.dianaMobHudAll=v;case"drophud"->cfg.dianaDropHud=v;case"alldrops"->cfg.dianaDropHudAll=v;case"droptitle"->cfg.dianaRareDropTitle=v;case"dropsound"->cfg.dianaRareDropSound=v;case"inquisitor"->cfg.dianaInquisitorAlert=v;case"share"->cfg.dianaInquisitorShare=v;case"persistent"->cfg.dianaPersistentStats=v;default->{local("Unknown Diana option.");return 0;}}ConstellationClient.saveConfig();return status();}
    private static int colorOption(String type,String raw){try{long parsed=raw.startsWith("#")?Long.parseLong(raw.substring(1),16):Long.decode(raw);int color=(int)parsed;if((color>>>24)==0)color|=0xFF000000;switch(type.toLowerCase(Locale.ROOT)){case"start"->cfg.dianaBurrowStartColor=color;case"mob"->cfg.dianaBurrowMobColor=color;case"treasure"->cfg.dianaBurrowTreasureColor=color;case"inquisitor"->cfg.dianaInquisitorColor=color;default->{local("Color type must be start, mob, treasure, or inquisitor.");return 0;}}ConstellationClient.saveConfig();return status();}catch(NumberFormatException e){local("Color must be #RRGGBB, #AARRGGBB, or a number.");return 0;}}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Diana] §f"+text));}
}
