package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/nether/CrimsonMinibossRespawnTimer.kt
public final class CrimsonMinibossTimers {
    private static final Pattern SPAWN=Pattern.compile("^BEWARE - (.+) Is Spawning\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern DOWN=Pattern.compile("^(.+) DOWN!$",Pattern.CASE_INSENSITIVE);
    private enum Boss {
        BLADESOUL("Bladesoul",new AABB(-330,80,-545,-257,107,-486)),
        MAGE_OUTLAW("Mage Outlaw",new AABB(-200,98,-878,-162,116,-843)),
        BARBARIAN_DUKE("Barbarian Duke X",new AABB(-550,101,-918,-522,131,-890)),
        ASHFANG("Ashfang",new AABB(-507,131,-1035,-462,155,-955)),
        MAGMA_BOSS("Magma Boss",new AABB(-442,59,-851,-318,90,-751));
        final String name;final AABB area;final Vec3 center;
        long spawnAt,possibleStart,possibleEnd,lastAreaSeen;Boolean alive,beacon;boolean alerted;
        Boss(String name,AABB area){this.name=name;this.area=area;this.center=area.getCenter();}
        void clear(){spawnAt=possibleStart=possibleEnd=lastAreaSeen=0;alive=beacon=null;alerted=false;}
    }
    private static DracoConfig cfg;private static Object levelIdentity;
    private CrimsonMinibossTimers(){}

    public static void init(DracoConfig config){
        cfg=config;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(clean(message.getString()));return true;});
        ConstellationClient.tick().every(20,"draco-crimson-minibosses",CrimsonMinibossTimers::tick);
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){levelIdentity=mc.level;clear();}
        if(!active()||mc.player==null||mc.level==null)return;
        long now=System.currentTimeMillis();
        for(Boss boss:Boss.values())if(boss.lastAreaSeen>0&&now-boss.lastAreaSeen>120_000)boss.clear();
        Boss boss=Arrays.stream(Boss.values()).filter(b->b.area.contains(mc.player.position())).findFirst().orElse(null);
        if(boss==null)return;boss.lastAreaSeen=now;
        if(known(boss,now))return;
        boolean found=false;
        for(Entity entity:mc.level.entitiesForRendering())if(entity.isAlive()&&entity.getCustomName()!=null&&boss.area.contains(entity.position())&&clean(entity.getCustomName().getString()).equalsIgnoreCase(boss.name)){found=true;break;}
        if(found){boss.alive=true;boss.beacon=null;boss.possibleStart=boss.possibleEnd=0;return;}
        boss.alive=false;
        boolean beacon=hasBeacon(boss);
        if(Boolean.TRUE.equals(boss.beacon)&&!beacon){boss.beacon=false;boss.possibleStart=boss.possibleEnd=0;boss.spawnAt=now+60_000;boss.alerted=false;return;}
        if(boss.possibleStart>0)return;
        if(beacon&&boss.beacon==null){boss.beacon=true;boss.possibleStart=now+60_000;boss.possibleEnd=now+120_000;}
        else if(!beacon&&boss.beacon==null){boss.beacon=false;boss.possibleStart=now;boss.possibleEnd=now+60_000;}
    }
    private static boolean hasBeacon(Boss boss){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return false;
        int minX=(int)Math.floor(boss.area.minX),maxX=(int)Math.ceil(boss.area.maxX),minY=(int)Math.floor(boss.area.minY),maxY=(int)Math.ceil(boss.area.maxY),minZ=(int)Math.floor(boss.area.minZ),maxZ=(int)Math.ceil(boss.area.maxZ);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){p.set(x,y,z);if(mc.level.getBlockState(p).is(Blocks.BEACON))return true;}
        return false;
    }
    private static void chat(String message){
        if(!active())return;Matcher down=DOWN.matcher(message),spawn=SPAWN.matcher(message);long now=System.currentTimeMillis();
        if(down.matches()){Boss boss=find(down.group(1));if(boss!=null){boss.spawnAt=now+Math.clamp(cfg.crimsonMinibossRespawnSeconds,30,300)*1000L;boss.alive=false;boss.beacon=null;boss.possibleStart=boss.possibleEnd=0;boss.alerted=false;}}
        else if(spawn.matches()){Boss boss=find(spawn.group(1));if(boss!=null){boss.spawnAt=0;boss.alive=true;boss.beacon=null;boss.possibleStart=boss.possibleEnd=0;boss.alerted=true;}}
    }
    public static String hudText(){
        if(!active())return null;long now=System.currentTimeMillis();StringBuilder out=new StringBuilder();
        Arrays.stream(Boss.values()).sorted(Comparator.comparingLong(b->sortTime(b,now))).forEach(b->{String state=state(b,now);if(state==null)return;if(!out.isEmpty())out.append(" | ");out.append(b.name).append(": ").append(state);alert(b,now);});
        return out.isEmpty()?null:out.toString();
    }
    private static String state(Boss b,long now){
        if(Boolean.TRUE.equals(b.alive)||spawnedFromTimer(b,now))return cfg.crimsonMinibossShowAlive?"Alive":null;
        if(known(b,now)){long left=b.spawnAt-now;if(left<=Math.clamp(cfg.crimsonMinibossSoonSeconds,1,30)*1000L)return "Soon";return format(left);}
        if(b.possibleStart>0){String start=b.possibleStart<=now?"now":format(b.possibleStart-now);String end=b.possibleEnd<=now?"now":format(b.possibleEnd-now);return "~"+start+" - "+end;}
        return cfg.crimsonMinibossShowUnknown?"Unknown":null;
    }
    private static void alert(Boss b,long now){if(!cfg.crimsonMinibossSoonAlert||b.alerted||!known(b,now)||b.spawnAt-now>Math.clamp(cfg.crimsonMinibossSoonSeconds,1,30)*1000L)return;b.alerted=true;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.crimsonMinibossSoonChat)local(b.name+" is spawning soon.");else mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),1,1);}
    public static void draw(WorldRenderer.Ctx ctx){if(!active()||(!cfg.crimsonMinibossWorldLabels&&!cfg.crimsonMinibossWorldBeams))return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;long now=System.currentTimeMillis(),rangeSq=(long)Math.clamp(cfg.crimsonMinibossWorldRange,32,512)*Math.clamp(cfg.crimsonMinibossWorldRange,32,512);for(Boss b:Boss.values()){String state=state(b,now);if(state==null||mc.player.position().distanceToSqr(b.center)>rangeSq)continue;if(cfg.crimsonMinibossWorldBeams)ctx.beam(b.center.x,b.center.y,b.center.z,cfg.crimsonMinibossColor,18,cfg.crimsonMinibossThroughWalls);if(cfg.crimsonMinibossWorldLabels)ctx.label(b.center.add(0,2,0),b.name+" - "+state,cfg.crimsonMinibossColor,cfg.crimsonMinibossThroughWalls);}}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("crimsonbosses").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{clear();local("Timers reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mark").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("boss",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->mark(StringArgumentType.getString(c,"boss"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){String text=hudText();local(text==null?"No timers available.":text);return 1;}
    private static int mark(String name,String state){Boss b=find(name.replace('_',' '));if(b==null){local("Unknown boss. Use bladesoul, outlaw, duke, ashfang, or magma.");return 0;}long now=System.currentTimeMillis();if(state.equalsIgnoreCase("dead")){b.spawnAt=now+Math.clamp(cfg.crimsonMinibossRespawnSeconds,30,300)*1000L;b.alive=false;b.alerted=false;}else if(state.equalsIgnoreCase("alive")){b.spawnAt=0;b.alive=true;b.alerted=true;}else{local("State must be alive or dead.");return 0;}b.possibleStart=b.possibleEnd=0;b.beacon=null;local(b.name+" marked "+state.toLowerCase(Locale.ROOT)+".");return 1;}
    private static Boss find(String value){String v=clean(value).replace(" X","").toLowerCase(Locale.ROOT);return Arrays.stream(Boss.values()).filter(b->b.name.replace(" X","").toLowerCase(Locale.ROOT).equals(v)||b.name.toLowerCase(Locale.ROOT).contains(v)).findFirst().orElse(null);}
    private static boolean known(Boss b,long now){return b.spawnAt>0&&now-b.spawnAt<125_000;}
    private static boolean spawnedFromTimer(Boss b,long now){return b.spawnAt>0&&now>=b.spawnAt&&now-b.spawnAt<=20_000;}
    private static long sortTime(Boss b,long now){if(known(b,now))return Math.max(0,b.spawnAt-now);if(Boolean.TRUE.equals(b.alive))return Long.MAX_VALUE-1;return Long.MAX_VALUE;}
    private static String format(long ms){long seconds=Math.max(0,(ms+999)/1000);return seconds>=60?(seconds/60)+":"+String.format(Locale.ROOT,"%02d",seconds%60):seconds+"s";}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.crimsonMinibossTimers&&ConstellationClient.loc().area()==SkyblockArea.CRIMSON_ISLE;}
    private static String clean(String value){return value.replaceAll("§[0-9A-FK-ORa-fk-or]","").trim();}
    private static void clear(){for(Boss b:Boss.values())b.clear();}
    private static void local(String value){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Crimson] §f"+value));}
}
