package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.CygnusConfig;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class CygnusCarnival {
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/carnival/CatchAFish.java
    private static final AABB FISH_AREA = new AABB(-87, 65, 25, -68, 85, 53);
    private static final String FISH_TEXTURE = "360f4f99c78adddeab276ebdf6ab9a0fcffabd49f0b543e1981ac7bd3d55b18a";
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/carnival/ZombieShootout.java
    private static final AABB SHOOTOUT_AREA = new AABB(-103, 70, 43, -99, 76, 46);
    private static final BlockPos[] LAMPS = {
        new BlockPos(-96,76,61),new BlockPos(-99,77,62),new BlockPos(-102,75,62),
        new BlockPos(-106,77,61),new BlockPos(-109,75,60),new BlockPos(-112,76,58),
        new BlockPos(-115,77,55),new BlockPos(-117,76,52),new BlockPos(-118,76,49),
        new BlockPos(-119,75,45),new BlockPos(-119,77,42),new BlockPos(-118,76,39)
    };
    private enum Force { AUTO, OFF, FISH, ZOMBIE }
    private static CygnusConfig cfg;
    private static Force force = Force.AUTO;
    private static boolean initialized, fishSeen;
    private static int fishCount, zombieCount, litCount;
    private CygnusCarnival() {}

    public static void init(CygnusConfig config) {
        cfg=config;
        if(initialized)return;
        initialized=true;
        ConstellationClient.tick().every(5,"cygnus-carnival",CygnusCarnival::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }

    private static void tick() {
        Minecraft mc=Minecraft.getInstance();
        fishCount=zombieCount=litCount=0;
        if(!enabled()||mc.player==null||mc.level==null){fishSeen=false;return;}
        boolean fish=fishActive(mc), zombie=zombieActive(mc);
        if(fish) for(var entity:mc.level.entitiesForRendering()) if(entity instanceof ArmorStand stand&&goldenFish(stand)) fishCount++;
        if(zombie) {
            for(var entity:mc.level.entitiesForRendering()) if(entity instanceof Zombie z&&zombieColor(z)!=0) zombieCount++;
            for(BlockPos pos:LAMPS) {
                var state=mc.level.getBlockState(pos);
                if(state.is(Blocks.REDSTONE_LAMP)&&state.hasProperty(BlockStateProperties.LIT)&&state.getValue(BlockStateProperties.LIT))litCount++;
            }
        }
        if(fishCount>0&&!fishSeen&&cfg.carnivalFishSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,0.7f,1.35f);
        fishSeen=fishCount>0;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        Minecraft mc=Minecraft.getInstance();
        if(!enabled()||mc.player==null||mc.level==null)return;
        if(fishActive(mc)&&cfg.carnivalCatchFish) {
            for(var entity:mc.level.entitiesForRendering()) if(entity instanceof ArmorStand stand&&goldenFish(stand)&&mc.player.distanceTo(stand)<=Math.clamp(cfg.carnivalFishRange,8,128)) {
                if(cfg.carnivalFishBox)ctx.highlight(stand.getBoundingBox().inflate(.12),cfg.carnivalFishColor,cfg.carnivalFishThroughWalls);
                if(cfg.carnivalFishBeam)ctx.beam(stand.getX(),stand.getY(),stand.getZ(),cfg.carnivalFishColor,8,cfg.carnivalFishThroughWalls);
                if(cfg.carnivalFishLabel)ctx.label(stand.position().add(0,2.4,0),"Golden Fish",cfg.carnivalFishColor,cfg.carnivalFishThroughWalls);
            }
        }
        if(zombieActive(mc)&&cfg.carnivalZombieShootout) {
            for(var entity:mc.level.entitiesForRendering()) if(entity instanceof Zombie z&&mc.player.distanceTo(z)<=Math.clamp(cfg.carnivalZombieRange,16,160)) {
                int color=zombieColor(z); if(color==0)continue;
                if(cfg.carnivalZombieBox)ctx.highlight(z.getBoundingBox().inflate(.08),color,cfg.carnivalZombieThroughWalls);
                if(cfg.carnivalZombieLabel)ctx.label(z.position().add(0,z.getBbHeight()+.35,0),weaponName(z),color,cfg.carnivalZombieThroughWalls);
            }
            if(cfg.carnivalLampOutline)for(BlockPos pos:LAMPS) {
                var state=mc.level.getBlockState(pos);
                if(state.is(Blocks.REDSTONE_LAMP)&&state.hasProperty(BlockStateProperties.LIT)&&state.getValue(BlockStateProperties.LIT)) {
                    ctx.outline(new AABB(pos),cfg.carnivalLampColor,cfg.carnivalLampThroughWalls,5);
                    if(cfg.carnivalLampLabel)ctx.label(new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+1.3,pos.getZ()+.5),"Target",cfg.carnivalLampColor,cfg.carnivalLampThroughWalls);
                }
            }
        }
    }

    public static String hudText() {
        if(!enabled())return null;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return null;
        if(fishActive(mc)&&cfg.carnivalCatchFish)return "§6Catch a Fish §f"+fishCount+" target"+(fishCount==1?"":"s");
        if(zombieActive(mc)&&cfg.carnivalZombieShootout)return "§cZombie Shootout §f"+zombieCount+" zombies §7| §f"+litCount+" lamps";
        return null;
    }

    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.carnivalHelper&&ConstellationClient.loc().onHypixel()&&force!=Force.OFF;}
    private static boolean hub(){return ConstellationClient.loc().area()==SkyblockArea.HUB;}
    private static boolean fishActive(Minecraft mc){return force==Force.FISH||(force==Force.AUTO&&hub()&&FISH_AREA.contains(mc.player.position()));}
    private static boolean zombieActive(Minecraft mc){return force==Force.ZOMBIE||(force==Force.AUTO&&hub()&&SHOOTOUT_AREA.contains(mc.player.position()));}
    private static boolean goldenFish(ArmorStand stand){ItemStack helmet=stand.getItemBySlot(EquipmentSlot.HEAD);if(!helmet.is(Items.PLAYER_HEAD))return false;var profile=helmet.get(DataComponents.PROFILE);if(profile==null)return false;for(Property p:profile.partialProfile().properties().get("textures"))if(p!=null&&FISH_TEXTURE.equals(textureHash(p.value())))return true;return false;}
    private static String textureHash(String encoded){try{String json=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);String url=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();return url.substring(url.lastIndexOf('/')+1);}catch(Exception ignored){return"";}}
    private static int zombieColor(Zombie z){ItemStack chest=z.getItemBySlot(EquipmentSlot.CHEST);if(chest.is(Items.DIAMOND_CHESTPLATE))return cfg.carnivalDiamondColor;if(chest.is(Items.GOLDEN_CHESTPLATE))return cfg.carnivalGoldColor;if(chest.is(Items.IRON_CHESTPLATE))return cfg.carnivalIronColor;if(chest.is(Items.LEATHER_CHESTPLATE))return cfg.carnivalWoodColor;return 0;}
    private static String weaponName(Zombie z){ItemStack chest=z.getItemBySlot(EquipmentSlot.CHEST);if(chest.is(Items.DIAMOND_CHESTPLATE))return"Diamond";if(chest.is(Items.GOLDEN_CHESTPLATE))return"Gold";if(chest.is(Items.IRON_CHESTPLATE))return"Iron";return"Wood";}
    private static void reset(){force=Force.AUTO;fishSeen=false;fishCount=zombieCount=litCount=0;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("carnivalhelper").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("force").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->force(StringArgumentType.getString(c,"mode"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(8,160)).executes(c->{int n=IntegerArgumentType.getInteger(c,"blocks");cfg.carnivalFishRange=n;cfg.carnivalZombieRange=n;save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Mode "+force.name().toLowerCase(Locale.ROOT)+", fish targets "+fishCount+", zombies "+zombieCount+", lit lamps "+litCount+".");return 1;}
    private static int force(String raw){try{force=Force.valueOf(raw.toUpperCase(Locale.ROOT));return status();}catch(Exception e){local("Mode must be auto, off, fish, or zombie.");return 0;}}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.carnivalHelper=v;case"fish"->cfg.carnivalCatchFish=v;case"fishbox"->cfg.carnivalFishBox=v;case"fishlabel"->cfg.carnivalFishLabel=v;case"fishbeam"->cfg.carnivalFishBeam=v;case"fishsound"->cfg.carnivalFishSound=v;case"zombies"->cfg.carnivalZombieShootout=v;case"zombiebox"->cfg.carnivalZombieBox=v;case"zombielabel"->cfg.carnivalZombieLabel=v;case"lamps"->cfg.carnivalLampOutline=v;case"lamplabel"->cfg.carnivalLampLabel=v;case"hud"->cfg.carnivalHud=v;default->{local("Unknown option.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Carnival] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
