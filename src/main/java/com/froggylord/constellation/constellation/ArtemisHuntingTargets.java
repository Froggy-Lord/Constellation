package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ArtemisHuntingTargets {
    private enum Type{
        HIDEONLEAF("Hideonleaf"),INVISIBUG("Invisibug"),BIRRIES("Birries"),SHELLWISE("Shellwise"),CORALOT("Coralot");
        final String display;Type(String display){this.display=display;}
    }
    private record Target(Type type,UUID id,Entity entity,Vec3 position,long seenAt){}
    private static final Map<UUID,Target> TARGETS=new LinkedHashMap<>();
    private static final Map<Type,Long> ALERTS=new EnumMap<>(Type.class);
    private static ArtemisConfig cfg;
    private static boolean initialized;
    private static Object levelIdentity;

    private ArtemisHuntingTargets(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/InvisibugHighlighter.kt
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof ClientboundLevelParticlesPacket p)onParticle(p);});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
        ConstellationClient.tick().every(5,"artemis-hunting-targets",ArtemisHuntingTargets::scan);
    }

    private static void scan(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTargets();}
        if(!active()||mc.player==null||mc.level==null){resetTargets();return;}
        double scan=Math.clamp(cfg.huntingMobScanRange,8,128),scan2=scan*scan;
        Set<UUID> birries=birriesBases(mc,scan2);
        Set<UUID> present=new HashSet<>();
        for(Entity entity:mc.level.entitiesForRendering()){
            if(!entity.isAlive()||entity.distanceToSqr(mc.player)>scan2)continue;
            Type type=type(entity,birries);if(type==null||!enabled(type)||entity.distanceToSqr(mc.player)>range(type)*range(type))continue;
            present.add(entity.getUUID());Target old=TARGETS.get(entity.getUUID());
            TARGETS.put(entity.getUUID(),new Target(type,entity.getUUID(),entity,entity.position(),System.currentTimeMillis()));
            if(old==null)alert(type);
        }
        TARGETS.entrySet().removeIf(entry->{
            Target target=entry.getValue();
            if(target.type==Type.INVISIBUG)return target.entity==null||!target.entity.isAlive()||target.entity.level()!=mc.level;
            return !present.contains(entry.getKey());
        });
    }

    // ported from Skyblocker (LGPL-3.0-only): skyblock/entity/glow/adder/GalateaGlowAdder.java
    private static Type type(Entity entity,Set<UUID> birries){
        if(entity instanceof Shulker shulker&&shulker.getColor()==DyeColor.GREEN)return Type.HIDEONLEAF;
        if(entity instanceof Turtle)return Type.SHELLWISE;
        if(entity instanceof Axolotl)return Type.CORALOT;
        if(entity instanceof ArmorStand){
            Target existing=TARGETS.get(entity.getUUID());
            return existing!=null&&existing.type==Type.INVISIBUG?Type.INVISIBUG:null;
        }
        if(birries.contains(entity.getUUID()))return Type.BIRRIES;
        String name=clean(entity.getName().getString());
        if(name.equals("Birries")||name.contains(" Birries"))return Type.BIRRIES;
        return null;
    }

    private static Set<UUID> birriesBases(Minecraft mc,double scan2){
        List<ArmorStand> labels=new ArrayList<>();List<LivingEntity> bases=new ArrayList<>();
        for(Entity entity:mc.level.entitiesForRendering()){
            if(!entity.isAlive()||entity.distanceToSqr(mc.player)>scan2)continue;
            if(entity instanceof ArmorStand stand&&stand.hasCustomName()&&clean(stand.getName().getString()).contains("Birries"))labels.add(stand);
            else if(entity instanceof LivingEntity living&&!(entity instanceof ArmorStand)&&living!=mc.player)bases.add(living);
        }
        Set<UUID> out=new HashSet<>();
        for(ArmorStand label:labels)bases.stream().filter(base->base.distanceToSqr(label)<=9)
            .min(Comparator.comparingDouble(base->base.distanceToSqr(label))).ifPresent(base->out.add(base.getUUID()));
        return out;
    }

    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/InvisibugHighlighter.kt
    private static void onParticle(ClientboundLevelParticlesPacket packet){
        if(!active()||!cfg.huntingHighlightInvisibug||packet.getParticle().getType()!=ParticleTypes.CRIT)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        Vec3 point=new Vec3(packet.getX(),packet.getY(),packet.getZ());
        if(mc.player.distanceToSqr(point)>range(Type.INVISIBUG)*range(Type.INVISIBUG))return;
        for(Target target:TARGETS.values())if(target.type==Type.INVISIBUG&&target.position.distanceToSqr(point)<25)return;
        ArmorStand nearest=null;double best=25;
        AABB search=new AABB(point,point).inflate(5);
        for(ArmorStand stand:mc.level.getEntitiesOfClass(ArmorStand.class,search)){
            double distance=stand.distanceToSqr(point);
            if(distance<best&&defaultStand(stand)){best=distance;nearest=stand;}
        }
        if(nearest==null)return;
        TARGETS.put(nearest.getUUID(),new Target(Type.INVISIBUG,nearest.getUUID(),nearest,nearest.position(),System.currentTimeMillis()));
        alert(Type.INVISIBUG);
    }

    private static boolean defaultStand(ArmorStand stand){
        if(stand.hasCustomName())return false;
        for(EquipmentSlot slot:EquipmentSlot.values())if(!stand.getItemBySlot(slot).isEmpty())return false;
        return true;
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        for(Target target:List.copyOf(TARGETS.values())){
            Entity entity=target.entity;if(entity==null||!entity.isAlive()||!enabled(target.type))continue;
            double range=range(target.type);if(entity.distanceToSqr(mc.player)>range*range)continue;
            int color=color(target.type);boolean walls=cfg.huntingMobThroughWalls;
            AABB box=entity.getBoundingBox().inflate(.08);
            Vec3 anchor=entity.position().add(0,entity.getBbHeight()+.3,0);
            if(target.type==Type.INVISIBUG){box=box.move(-.4,.2,-.4).deflate(.12);anchor=entity.position().add(-.4,.8,-.4);}
            if(cfg.huntingMobBoxes)ctx.highlight(box,color,walls);
            if(cfg.huntingMobBeams)ctx.beam(anchor.x,entity.getY(),anchor.z,color,Math.clamp(cfg.huntingMobBeamHeight,1,64),walls);
            if(cfg.huntingMobLines)ctx.line(mc.player.getEyePosition(),anchor,color,walls);
            if(cfg.huntingMobLabels){
                String text=target.type.display;
                if(cfg.huntingMobDistances)text+=" "+Math.round(mc.player.distanceTo(entity))+"m";
                ctx.label(anchor,text,color,walls);
            }
        }
    }

    private static void alert(Type type){
        if(!cfg.huntingMobAlerts)return;
        long now=System.currentTimeMillis(),cooldown=Math.clamp(cfg.huntingMobAlertCooldownSeconds,1,60)*1000L;
        if(now-ALERTS.getOrDefault(type,0L)<cooldown)return;ALERTS.put(type,now);
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.huntingMobAlertChat)local(type.display+" found.");
        if(cfg.huntingMobAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(type.display).withColor(color(type)&0xFFFFFF));}
        if(cfg.huntingMobAlertSound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.8f,1.2f);
    }

    private static boolean enabled(Type type){return switch(type){case HIDEONLEAF->cfg.huntingHighlightHideonleaf;case INVISIBUG->cfg.huntingHighlightInvisibug;case BIRRIES->cfg.huntingHighlightBirries;case SHELLWISE->cfg.huntingHighlightShellwise;case CORALOT->cfg.huntingHighlightCoralot;};}
    private static double range(Type type){return Math.clamp(switch(type){case HIDEONLEAF->cfg.huntingHideonleafRange;case INVISIBUG->cfg.huntingInvisibugRange;case BIRRIES->cfg.huntingBirriesRange;case SHELLWISE->cfg.huntingShellwiseRange;case CORALOT->cfg.huntingCoralotRange;},4,128);}
    private static int color(Type type){return switch(type){case HIDEONLEAF->cfg.huntingHideonleafColor;case INVISIBUG->cfg.huntingInvisibugColor;case BIRRIES->cfg.huntingBirriesColor;case SHELLWISE->cfg.huntingShellwiseColor;case CORALOT->cfg.huntingCoralotColor;};}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.huntingMobHighlights&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;}
    private static void reset(){levelIdentity=null;resetTargets();}
    private static void resetTargets(){TARGETS.clear();ALERTS.clear();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Hunting] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("huntingmobs").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetTargets();local("Tracked hunting targets cleared.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(4,128)).executes(c->setRange(StringArgumentType.getString(c,"type"),IntegerArgumentType.getInteger(c,"blocks"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->setColor(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Hunting targets "+(cfg.huntingMobHighlights?"on":"off")+", "+TARGETS.size()+" currently tracked.");return 1;}
    private static Type parseType(String raw){try{return Type.valueOf(raw.toUpperCase(Locale.ROOT));}catch(Exception ignored){return null;}}
    private static int setRange(String raw,int value){Type type=parseType(raw);if(type==null){local("Type must be hideonleaf, invisibug, birries, shellwise, or coralot.");return 0;}switch(type){case HIDEONLEAF->cfg.huntingHideonleafRange=value;case INVISIBUG->cfg.huntingInvisibugRange=value;case BIRRIES->cfg.huntingBirriesRange=value;case SHELLWISE->cfg.huntingShellwiseRange=value;case CORALOT->cfg.huntingCoralotRange=value;}save();return status();}
    private static int setColor(String raw,String argb){Type type=parseType(raw);int value=colorValue(argb);if(type==null||value==0){local("Use a valid target and RRGGBB or AARRGGBB color.");return 0;}switch(type){case HIDEONLEAF->cfg.huntingHideonleafColor=value;case INVISIBUG->cfg.huntingInvisibugColor=value;case BIRRIES->cfg.huntingBirriesColor=value;case SHELLWISE->cfg.huntingShellwiseColor=value;case CORALOT->cfg.huntingCoralotColor=value;}save();return status();}
    private static int colorValue(String raw){try{String text=raw.replace("#","").replace("0x","");long value=Long.parseUnsignedLong(text,16);if(text.length()==6)value|=0xFF000000L;return text.length()==6||text.length()==8?(int)value:0;}catch(Exception ignored){return 0;}}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.huntingMobHighlights=value;case"hideonleaf"->cfg.huntingHighlightHideonleaf=value;case"invisibug"->cfg.huntingHighlightInvisibug=value;case"birries"->cfg.huntingHighlightBirries=value;case"shellwise"->cfg.huntingHighlightShellwise=value;case"coralot"->cfg.huntingHighlightCoralot=value;case"box","boxes"->cfg.huntingMobBoxes=value;case"label","labels"->cfg.huntingMobLabels=value;case"beam","beams"->cfg.huntingMobBeams=value;case"line","lines"->cfg.huntingMobLines=value;case"distance"->cfg.huntingMobDistances=value;case"walls","throughwalls"->cfg.huntingMobThroughWalls=value;case"alerts"->cfg.huntingMobAlerts=value;case"chat"->cfg.huntingMobAlertChat=value;case"title"->cfg.huntingMobAlertTitle=value;case"sound"->cfg.huntingMobAlertSound=value;default->{local("Unknown hunting-target option.");return 0;}}save();return status();}
}
