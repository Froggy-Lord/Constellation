package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/ClearTreeLogs.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/MuteTreeSounds.kt
public final class ArtemisTreeCleanup {
    private static final Set<UUID> HIDDEN=new HashSet<>();
    private static ArtemisConfig cfg;
    private static boolean initialized;
    private static Object levelIdentity;
    private static long muted;

    private ArtemisTreeCleanup(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }

    public static boolean shouldHide(Entity entity){
        if(!cleanActive()||!(entity instanceof Display.BlockDisplay display))return false;
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;HIDDEN.clear();}
        var render=display.blockRenderState();if(render==null)return false;
        BlockState state=render.blockState();boolean hide=
            (cfg.treeCleanStrippedSpruceWood&&state.equals(Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState()))
            ||(cfg.treeCleanMangroveWood&&state.equals(Blocks.MANGROVE_WOOD.defaultBlockState()))
            ||(cfg.treeCleanMangroveLeaves&&state.equals(Blocks.MANGROVE_LEAVES.defaultBlockState()))
            ||(cfg.treeCleanAzaleaLeaves&&state.equals(Blocks.AZALEA_LEAVES.defaultBlockState()));
        if(hide)HIDDEN.add(entity.getUUID());return hide;
    }

    public static boolean shouldMute(String path){
        if(cfg==null||!cfg.enabled||!cfg.treeCleanup||!cfg.treeMuteBreaking||!ConstellationClient.loc().onHypixel()
            ||!"entity.creaking.death".equals(path))return false;
        return ConstellationClient.loc().area()==SkyblockArea.GALATEA?cfg.treeMuteBreakingOnGalatea:cfg.treeMuteBreakingOutsideGalatea;
    }

    public static void recordMuted(){muted++;}
    private static boolean cleanActive(){
        return cfg!=null&&cfg.enabled&&cfg.treeCleanup&&cfg.treeCleanView&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;
    }
    private static void reset(){levelIdentity=null;HIDDEN.clear();muted=0;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Tree Cleanup] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("treecleanup").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetstats").executes(c->{HIDDEN.clear();muted=0;return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Cleanup "+on(cfg.treeCleanup)+", clean view "+on(cfg.treeCleanView)+", breaking sound outside Galatea "+on(cfg.treeMuteBreakingOutsideGalatea)+", on Galatea "+on(cfg.treeMuteBreakingOnGalatea)+"; "+HIDDEN.size()+" displays seen and "+muted+" sounds muted this session.");return 1;}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){
        case"enabled"->cfg.treeCleanup=value;case"cleanview"->cfg.treeCleanView=value;case"sprucewood"->cfg.treeCleanStrippedSpruceWood=value;case"mangrovewood"->cfg.treeCleanMangroveWood=value;case"mangroveleaves"->cfg.treeCleanMangroveLeaves=value;case"azalealeaves"->cfg.treeCleanAzaleaLeaves=value;case"mutesound"->cfg.treeMuteBreaking=value;case"muteoutside"->cfg.treeMuteBreakingOutsideGalatea=value;case"mutegalatea"->cfg.treeMuteBreakingOnGalatea=value;default->{local("Unknown tree-cleanup option.");return 0;}
    }save();if(!cfg.treeCleanup||!cfg.treeCleanView)HIDDEN.clear();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
}
