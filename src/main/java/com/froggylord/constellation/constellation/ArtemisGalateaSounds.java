package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-only): skyblock/hunting/SilencePhantoms.java
// phantom sound list cross-ported from SkyOcean (MIT): features/foraging/galatea/MuteTheFuckingPhantoms.kt
// fusion filter ported from SkyHanni (LGPL-3.0-or-later): features/foraging/MuteFusionMachine.kt
public final class ArtemisGalateaSounds {
    private static ArtemisConfig cfg;
    private static long phantomMuted,fusionMuted;

    private ArtemisGalateaSounds(){}

    public static void init(ArtemisConfig config){cfg=config;}

    public static boolean shouldCancel(ClientboundSoundPacket packet){
        if(!active()||packet==null)return false;
        String path=packet.getSound().value().location().getPath();
        boolean phantom=phantom(path);
        if(phantom){phantomMuted++;return true;}
        if(fusion(path,packet.getVolume())){fusionMuted++;return true;}
        return false;
    }

    private static boolean phantom(String path){
        if(!cfg.galateaMutePhantoms||!path.startsWith("entity.phantom."))return false;
        return switch(path){
            case"entity.phantom.ambient"->cfg.galateaMutePhantomAmbient;
            case"entity.phantom.bite"->cfg.galateaMutePhantomBite;
            case"entity.phantom.death"->cfg.galateaMutePhantomDeath;
            case"entity.phantom.flap"->cfg.galateaMutePhantomFlap;
            case"entity.phantom.hurt"->cfg.galateaMutePhantomHurt;
            case"entity.phantom.swoop"->cfg.galateaMutePhantomSwoop;
            default->true;
        };
    }

    private static boolean fusion(String path,float volume){
        if(!cfg.galateaMuteFusionMachine||!path.equals("entity.firework_rocket.blast")&&!path.equals("entity.firework_rocket.blast_far"))return false;
        if(cfg.galateaMuteFusionAnyVolume)return true;
        float target=Math.clamp(cfg.galateaFusionVolumeHundredths,0,10000)/100f;
        float tolerance=Math.clamp(cfg.galateaFusionVolumeToleranceHundredths,0,1000)/100f;
        return Math.abs(volume-target)<=tolerance+1.0E-6f;
    }

    private static boolean active(){
        if(cfg==null||!cfg.enabled||!cfg.galateaSoundControl||!ConstellationClient.loc().onHypixel())return false;
        return !cfg.galateaSoundsGalateaOnly||ConstellationClient.loc().area()==SkyblockArea.GALATEA;
    }
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Galatea Sounds] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("galateasounds").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetstats").executes(c->{phantomMuted=fusionMuted=0;return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("volume").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("hundredths",IntegerArgumentType.integer(0,10000)).executes(c->{cfg.galateaFusionVolumeHundredths=IntegerArgumentType.getInteger(c,"hundredths");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tolerance").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("hundredths",IntegerArgumentType.integer(0,1000)).executes(c->{cfg.galateaFusionVolumeToleranceHundredths=IntegerArgumentType.getInteger(c,"hundredths");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Control "+(cfg.galateaSoundControl?"on":"off")+", phantoms "+(cfg.galateaMutePhantoms?"muted":"audible")+", Fusion "+(cfg.galateaMuteFusionMachine?"muted":"audible")+"; session "+phantomMuted+" phantom and "+fusionMuted+" Fusion sounds hidden.");return 1;}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.galateaSoundControl=value;case"galatea"->cfg.galateaSoundsGalateaOnly=value;case"phantoms"->cfg.galateaMutePhantoms=value;case"ambient"->cfg.galateaMutePhantomAmbient=value;case"bite"->cfg.galateaMutePhantomBite=value;case"death"->cfg.galateaMutePhantomDeath=value;case"flap"->cfg.galateaMutePhantomFlap=value;case"hurt"->cfg.galateaMutePhantomHurt=value;case"swoop"->cfg.galateaMutePhantomSwoop=value;case"fusion"->cfg.galateaMuteFusionMachine=value;case"anyvolume"->cfg.galateaMuteFusionAnyVolume=value;default->{local("Unknown Galatea sound option.");return 0;}}save();return status();}
}
