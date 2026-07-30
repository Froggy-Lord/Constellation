package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/chocolatefactory/stray/CFStrayTimer.kt
public final class AurigaStrayTimer {
    private static final Pattern MEAL=Pattern.compile("^HOPPITY'S HUNT You found a Chocolate [\\p{L}]+ Egg(?: .*)?!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern HITMAN=Pattern.compile("^HOPPITY'S HUNT You found a Hitman Egg!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern VISITOR=Pattern.compile("^\\[NPC] Hoppity: Simply exquisite.*$",Pattern.CASE_INSENSITIVE);
    // ported from SkyHanni Repo (MIT): constants/HoppityEggLocations.json destructive_slots
    private static final Set<Integer> DESTRUCTIVE=Set.of(47,48,49,50,51,53);

    private static AurigaConfig cfg;
    private static long remaining;
    private static long lastTick;
    private static long lastDing;
    private static boolean wasFactory;
    private static Set<String> caughtBaseline=Set.of();
    private static long lastBlockNotice;

    private AurigaStrayTimer(){}

    public static void init(AurigaConfig config){
        cfg=config;normalize();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(message.getString());return true;});
        ConstellationClient.tick().every(1,"auriga-stray-timer",AurigaStrayTimer::tick);
    }

    private static void chat(String formatted){
        if(!active())return;String text=clean(formatted);
        if(MEAL.matcher(text).matches()||HITMAN.matcher(text).matches()||VISITOR.matcher(text).matches())arm();
    }

    private static void arm(){
        remaining=Math.clamp(cfg.chocolateFactoryStrayTimerSeconds,5,120)*1000L;
        lastTick=0;caughtBaseline=currentCaught();
    }

    private static void tick(){
        if(!active()){reset();return;}
        boolean factory=factoryScreen();long now=System.currentTimeMillis();
        if(remaining<=0){wasFactory=factory;lastTick=0;return;}
        if(!factory){
            if(wasFactory)remaining=Math.clamp(cfg.chocolateFactoryStrayTimerSeconds,5,120)*1000L;
            wasFactory=false;lastTick=0;return;
        }
        if(!wasFactory){wasFactory=true;lastTick=now;caughtBaseline=currentCaught();return;}
        Set<String> caught=currentCaught();
        if(caught.stream().anyMatch(value->!caughtBaseline.contains(value))){remaining=0;lastTick=0;caughtBaseline=caught;return;}
        caughtBaseline=caught;
        if(lastTick>0)remaining=Math.max(0,remaining-(now-lastTick));lastTick=now;
        int ding=Math.clamp(cfg.chocolateFactoryStrayTimerDingSeconds,0,30);
        if(cfg.chocolateFactoryStrayTimerDing&&ding>0&&remaining>0&&remaining<=ding*1000L&&now-lastDing>=1000){
            lastDing=now;Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.8f,1.2f);
        }
    }

    private static Set<String> currentCaught(){
        Minecraft mc=Minecraft.getInstance();if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!title(screen).equals("Chocolate Factory"))return Set.of();
        HashSet<String> out=new HashSet<>();for(Slot slot:screen.getMenu().slots){if(slot.getItem().isEmpty())continue;String name=clean(slot.getItem().getHoverName().getString());if(name.endsWith(" CAUGHT!"))out.add(slot.index+"|"+name);}return Set.copyOf(out);
    }

    public static boolean visible(){return active()&&factoryScreen()&&remaining>0;}
    public static long remaining(){return remaining;}
    public static int color(){return remaining<=Math.clamp(cfg.chocolateFactoryStrayTimerDingSeconds,0,30)*1000L?cfg.chocolateFactoryStrayTimerDangerColor:cfg.chocolateFactoryStrayTimerColor;}
    public static String formatted(){double seconds=remaining/1000.0;return cfg.chocolateFactoryStrayTimerShowHundredths?String.format(Locale.US,"%.2fs",seconds):String.format(Locale.US,"%.1fs",seconds);}

    public static boolean shouldBlockClick(AbstractContainerScreen<?> screen,Slot slot){
        if(!blocking(screen)||!cfg.chocolateFactoryStrayTimerBlockDestructiveSlots||slot==null||!DESTRUCTIVE.contains(slot.index)||bypass())return false;
        blocked();return true;
    }
    public static boolean shouldBlockClose(AbstractContainerScreen<?> screen){
        if(!blocking(screen)||bypass())return false;blocked();return true;
    }
    private static boolean blocking(AbstractContainerScreen<?> screen){return cfg!=null&&cfg.chocolateFactoryStrayTimerBlockClosing&&visible()&&screen!=null&&title(screen).equals("Chocolate Factory");}
    private static boolean bypass(){if(!cfg.chocolateFactoryStrayTimerShiftBypass)return false;var window=Minecraft.getInstance().getWindow();return InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);}
    private static void blocked(){long now=System.currentTimeMillis();if(now-lastBlockNotice<500)return;lastBlockNotice=now;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Stray timer kept open").withColor(cfg.chocolateFactoryStrayTimerDangerColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(cfg.chocolateFactoryStrayTimerShiftBypass?"Hold Shift to bypass":formatted()+" remaining"));mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(),.8f,.7f);}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("straytimer")
            .executes(context->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(context->manualReset()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("seconds")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(5,120))
                    .executes(context->seconds(IntegerArgumentType.getInteger(context,"seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dingseconds")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,30))
                    .executes(context->ding(IntegerArgumentType.getInteger(context,"seconds")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(context->option(StringArgumentType.getString(context,"name"),StringArgumentType.getString(context,"state")))))));
    }

    private static int status(){local((visible()?formatted()+" remaining":"inactive")+", close protection "+on(cfg.chocolateFactoryStrayTimerBlockClosing)+", ding "+on(cfg.chocolateFactoryStrayTimerDing)+".");return 1;}
    private static int manualReset(){reset();local("Timer cleared.");return 1;}
    private static int seconds(int value){cfg.chocolateFactoryStrayTimerSeconds=value;if(remaining>0)remaining=value*1000L;save();return status();}
    private static int ding(int value){cfg.chocolateFactoryStrayTimerDingSeconds=value;save();return status();}
    private static int option(String name,String raw){
        Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.chocolateFactoryStrayTimer=value;case"hud"->cfg.chocolateFactoryStrayTimerHud=value;
            case"ding"->cfg.chocolateFactoryStrayTimerDing=value;case"blockclose"->cfg.chocolateFactoryStrayTimerBlockClosing=value;
            case"blockslots"->cfg.chocolateFactoryStrayTimerBlockDestructiveSlots=value;case"shift"->cfg.chocolateFactoryStrayTimerShiftBypass=value;
            case"hundredths"->cfg.chocolateFactoryStrayTimerShowHundredths=value;default->{local("Unknown Stray Timer option.");return 0;}
        }save();return status();
    }

    private static void reset(){remaining=0;lastTick=0;wasFactory=false;caughtBaseline=Set.of();}
    private static boolean factoryScreen(){Minecraft mc=Minecraft.getInstance();return mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&title(screen).equals("Chocolate Factory");}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.replaceAll("\\s+"," ").strip();}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.chocolateFactoryStrayTimer&&ConstellationClient.loc().onHypixel();}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void normalize(){cfg.chocolateFactoryStrayTimerSeconds=Math.clamp(cfg.chocolateFactoryStrayTimerSeconds,5,120);cfg.chocolateFactoryStrayTimerDingSeconds=Math.clamp(cfg.chocolateFactoryStrayTimerDingSeconds,0,30);}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a76[Stray Timer] \u00a7f"+text));}
}
