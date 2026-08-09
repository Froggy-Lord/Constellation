package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/nether/ashfang/AshfangManager.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/nether/ashfang/AshfangHighlights.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/nether/ashfang/AshfangHider.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/nether/ashfang/AshfangFreezeCooldown.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/nether/ashfang/AshfangNextResetCooldown.kt
public final class AshfangHelper {
    private enum BlazeType { FOLLOWER, UNDERLING, ACOLYTE }
    private record Mark(ArmorStand stand,Vec3 pos){}
    private record BlazeMark(Blaze blaze,BlazeType type,String label){}

    private static final String SOUL="8287b397daf9516a0bd76f5f1b7bf979515df3d5d833e0635fa68b37ee082212";
    private static final String ORB="6de3564d5f2056a7e17e96419218b4213848a2f4d2b5fe035b7460c986e6f48c";
    private static final Pattern FREEZE=Pattern.compile("^Ashfang Follower's Cryogenic Blast hit you for .* damage!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALTH=Pattern.compile(".*?(?<current>[0-9,.]+)(?<unit>[kKmMbB]?)\s*/\s*(?<max>[0-9,.]+)(?<maxUnit>[kKmMbB]?).*");
    private static final Pattern DAMAGE=Pattern.compile("^(?:[0-9,.]+[kKmMbB]?|[✧✯✽✷✹✺✵✶]+)$");
    private static final List<Mark> SOULS=new ArrayList<>(),ORBS=new ArrayList<>();
    private static final List<BlazeMark> BLAZES=new ArrayList<>();
    private static final Set<UUID> KNOWN_WAVE_MOBS=new HashSet<>();
    private static DracoConfig cfg;
    private static boolean active;
    private static Vec3 bossPos;
    private static long bossSeenAt,lastSpawnAt,frozenUntil,readyUntil;
    private static Object levelIdentity;

    private AshfangHelper(){}

    public static void init(DracoConfig config){
        cfg=config;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(clean(message.getString()));return true;});
        ConstellationClient.tick().every(2,"draco-ashfang",AshfangHelper::tick);
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){levelIdentity=mc.level;reset();}
        if(!enabled()||mc.level==null||mc.player==null){clearTransient();return;}
        long now=System.currentTimeMillis();double range=Math.clamp(cfg.ashfangDetectionRange,32,192);double rangeSq=range*range;
        List<ArmorStand> stands=new ArrayList<>();List<Blaze> blazes=new ArrayList<>();Vec3 foundBoss=null;boolean foundEncounter=false;
        for(Entity entity:mc.level.entitiesForRendering()){
            if(entity.distanceToSqr(mc.player)>rangeSq||!entity.isAlive())continue;
            if(entity instanceof ArmorStand stand){stands.add(stand);String name=clean(name(stand));if(name.equals("Ashfang")||name.startsWith("Ashfang ")&&!name.contains("Follower")&&!name.contains("Underling")&&!name.contains("Acolyte")){foundBoss=stand.position();foundEncounter=true;}}
            else if(entity instanceof Blaze blaze)blazes.add(blaze);
        }
        if(foundBoss!=null){bossPos=foundBoss;bossSeenAt=now;active=true;}
        else if(active&&now-bossSeenAt>3000){active=false;bossPos=null;}
        if(!active){SOULS.clear();ORBS.clear();BLAZES.clear();return;}
        SOULS.clear();ORBS.clear();BLAZES.clear();Set<UUID> currentWaveMobs=new HashSet<>();boolean newWaveMob=false;
        for(ArmorStand stand:stands){
            String name=clean(name(stand));BlazeType type=type(name);
            if(type!=null){currentWaveMobs.add(stand.getUUID());if(!KNOWN_WAVE_MOBS.contains(stand.getUUID()))newWaveMob=true;Blaze nearest=blazes.stream().filter(b->b.distanceToSqr(stand)<16).min(Comparator.comparingDouble(b->b.distanceToSqr(stand))).orElse(null);if(nearest!=null&&BLAZES.stream().noneMatch(mark->mark.blaze()==nearest))BLAZES.add(new BlazeMark(nearest,type,name));continue;}
            String hash=hash(stand.getItemBySlot(EquipmentSlot.HEAD));if(hash.equals(SOUL))SOULS.add(new Mark(stand,stand.position()));else if(hash.equals(ORB))ORBS.add(new Mark(stand,stand.position()));
        }
        KNOWN_WAVE_MOBS.retainAll(currentWaveMobs);KNOWN_WAVE_MOBS.addAll(currentWaveMobs);
        if(newWaveMob&&(lastSpawnAt==0||now-lastSpawnAt>10_000)){lastSpawnAt=now;readyUntil=0;}
    }

    private static void chat(String message){
        if(!active||!cfg.ashfangFreezeTimer||!FREEZE.matcher(message).matches())return;
        frozenUntil=System.currentTimeMillis()+Math.clamp(cfg.ashfangFreezeTenths,5,100)*100L;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.ashfangFreezeChat)mc.player.sendSystemMessage(Component.literal("\u00a7c[Ashfang] \u00a7fAbilities frozen for "+formatMillis(frozenUntil-System.currentTimeMillis())+"."));
        if(cfg.ashfangFreezeTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTimes(0,30,5);mc.gui.hud.setTitle(Component.literal("Abilities frozen").withColor(0xFF5555));mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(),.7f,.7f);}
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active||!enabled())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;double range=Math.clamp(cfg.ashfangObjectRenderRange,8,128),rangeSq=range*range;
        if(cfg.ashfangBlazingSouls)for(Mark mark:SOULS)if(mark.pos().distanceToSqr(mc.player.position())<=rangeSq)drawMark(ctx,mark.pos().add(0,1,0),"Blazing Soul",cfg.ashfangBlazingSoulColor,cfg.ashfangBlazingSoulBox,cfg.ashfangBlazingSoulBeam,cfg.ashfangBlazingSoulLabel,.7);
        if(cfg.ashfangGravityOrbs)for(Mark mark:ORBS)if(mark.pos().distanceToSqr(mc.player.position())<=rangeSq)drawMark(ctx,mark.pos().add(0,-1,0),"Gravity Orb",cfg.ashfangGravityOrbColor,cfg.ashfangGravityOrbBox,cfg.ashfangGravityOrbBeam,cfg.ashfangGravityOrbLabel,3.5);
        if(cfg.ashfangHighlightBlazes)for(BlazeMark mark:BLAZES){int color=color(mark.type());AABB box=mark.blaze().getBoundingBox().inflate(.12);ctx.highlight(box,color,cfg.ashfangThroughWalls);if(cfg.ashfangBlazeLabels)ctx.label(mark.blaze().position().add(0,2.2,0),pretty(mark.type()),color,cfg.ashfangThroughWalls);}
    }

    private static void drawMark(WorldRenderer.Ctx ctx,Vec3 center,String label,int color,boolean box,boolean beam,boolean text,double radius){AABB bounds=new AABB(center.x-radius,center.y-radius*.4,center.z-radius,center.x+radius,center.y+radius*.8,center.z+radius);if(box)ctx.highlight(bounds,color,cfg.ashfangThroughWalls);if(beam)ctx.beam(center.x,center.y,center.z,color,Math.clamp((int)Math.ceil(radius*3),3,20),cfg.ashfangThroughWalls);if(text)ctx.label(center.add(0,radius*.9+.7,0),label,color,cfg.ashfangThroughWalls);}

    public static boolean shouldHideParticle(ClientboundLevelParticlesPacket packet){if(!active||!enabled()||!cfg.ashfangHideParticles||bossPos==null)return false;double range=Math.clamp(cfg.ashfangParticleHideRange,8,96);return bossPos.distanceToSqr(packet.getX(),packet.getY(),packet.getZ())<=range*range;}
    public static boolean shouldHide(Entity entity){
        if(!active||!enabled()||!(entity instanceof ArmorStand stand)||bossPos==null||stand.position().distanceToSqr(bossPos)>Math.pow(Math.clamp(cfg.ashfangDetectionRange,32,192),2))return false;
        if(cfg.ashfangHideGlowstoneStands&&equipmentNamed(stand,"Glowstone"))return true;
        String name=clean(name(stand));if(cfg.ashfangHideDamageSplash&&DAMAGE.matcher(name).matches())return true;
        return cfg.ashfangHideFullNames&&type(name)!=null&&fullHealth(name);
    }

    public static String freezeHudText(){if(!enabled()||!active||frozenUntil<=System.currentTimeMillis())return null;return "\u00a7cFreeze \u00a7a"+formatMillis(frozenUntil-System.currentTimeMillis());}
    public static String resetHudText(){if(!enabled()||!active||lastSpawnAt==0)return null;long remaining=lastSpawnAt+Math.clamp(cfg.ashfangResetTenths,100,900)*100L-System.currentTimeMillis();if(remaining<=0){if(readyUntil==0)readyUntil=System.currentTimeMillis()+Math.clamp(cfg.ashfangReadyHoldSeconds,0,30)*1000L;if(!cfg.ashfangTimerShowReady||System.currentTimeMillis()>readyUntil)return null;return "\u00a7cNext reset \u00a7aNow";}return "\u00a7cNext reset \u00a7b"+formatMillis(remaining);}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ashfang").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{reset();local("Encounter state reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(32,192)).executes(c->{cfg.ashfangDetectionRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Helper "+on(enabled())+", encounter "+on(active)+", "+BLAZES.size()+" blazes, "+SOULS.size()+" souls, "+ORBS.size()+" orbs.");local("Freeze "+(frozenUntil>System.currentTimeMillis()?formatMillis(frozenUntil-System.currentTimeMillis()):"ready")+", reset "+(resetHudText()==null?"waiting":clean(resetHudText()))+".");return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.ashfangHelper=v;case"orbs"->cfg.ashfangGravityOrbs=v;case"souls"->cfg.ashfangBlazingSouls=v;case"blazes"->cfg.ashfangHighlightBlazes=v;case"particles"->cfg.ashfangHideParticles=v;case"names"->cfg.ashfangHideFullNames=v;case"damage"->cfg.ashfangHideDamageSplash=v;case"freeze"->cfg.ashfangFreezeTimer=v;case"reset"->cfg.ashfangNextResetTimer=v;case"throughwalls"->cfg.ashfangThroughWalls=v;default->{local("Unknown Ashfang option.");return 0;}}save();return status();}

    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.ashfangHelper&&ConstellationClient.loc().area()==SkyblockArea.CRIMSON_ISLE;}
    private static BlazeType type(String name){String n=name.toLowerCase(Locale.ROOT);if(n.contains("ashfang follower"))return BlazeType.FOLLOWER;if(n.contains("ashfang underling"))return BlazeType.UNDERLING;if(n.contains("ashfang acolyte"))return BlazeType.ACOLYTE;return null;}
    private static int color(BlazeType type){return switch(type){case FOLLOWER->cfg.ashfangFollowerColor;case UNDERLING->cfg.ashfangUnderlingColor;case ACOLYTE->cfg.ashfangAcolyteColor;};}
    private static String pretty(BlazeType type){return switch(type){case FOLLOWER->"Follower";case UNDERLING->"Underling";case ACOLYTE->"Acolyte";};}
    private static boolean fullHealth(String name){Matcher m=HEALTH.matcher(name);if(!m.matches())return false;return Math.abs(amount(m.group("current"),m.group("unit"))-amount(m.group("max"),m.group("maxUnit")))<1;}
    private static double amount(String raw,String unit){try{double v=Double.parseDouble(raw.replace(",",""));return v*switch(unit.toLowerCase(Locale.ROOT)){case"k"->1_000;case"m"->1_000_000;case"b"->1_000_000_000;default->1;};}catch(Exception e){return-1;}}
    private static boolean equipmentNamed(ArmorStand stand,String wanted){for(EquipmentSlot slot:EquipmentSlot.values()){ItemStack stack=stand.getItemBySlot(slot);if(!stack.isEmpty()&&clean(stack.getHoverName().getString()).equalsIgnoreCase(wanted))return true;}return false;}
    private static String hash(ItemStack stack){if(stack==null||stack.isEmpty())return"";var profile=stack.get(DataComponents.PROFILE);if(profile==null)return"";for(Property property:profile.partialProfile().properties().get("textures")){try{String json=new String(Base64.getDecoder().decode(property.value()),StandardCharsets.UTF_8);String url=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();return url.substring(url.lastIndexOf('/')+1);}catch(Exception ignored){}}return"";}
    private static String name(Entity entity){Component name=entity.getCustomName();return name==null?"":name.getString();}
    private static String formatMillis(long ms){int decimals=Math.clamp(cfg.ashfangTimerDecimals,0,2);double seconds=Math.max(0,ms)/1000.0;return String.format(Locale.ROOT,"%."+decimals+"fs",seconds);}
    private static String clean(String value){String s=ChatFormatting.stripFormatting(value==null?"":value);return s==null?"":s.trim();}
    private static String on(boolean value){return value?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7c[Ashfang] \u00a7f"+text));}
    private static void clearTransient(){active=false;bossPos=null;SOULS.clear();ORBS.clear();BLAZES.clear();KNOWN_WAVE_MOBS.clear();}
    private static void reset(){clearTransient();bossSeenAt=0;lastSpawnAt=0;frozenUntil=0;readyUntil=0;}
}
