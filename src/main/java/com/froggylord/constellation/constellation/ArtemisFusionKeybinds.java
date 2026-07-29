package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

// ported from NoFrills (GPL-3.0-only): features/hunting/FusionKeybinds.java
// duplicate-key handling ported from SkyHanni (LGPL-3.0-or-later): features/hunting/FusionKeybinds.kt
public final class ArtemisFusionKeybinds {
    private enum Action { REPEAT, CONFIRM, CANCEL }
    private static ArtemisConfig cfg;
    private static KeyMapping repeat,confirm,cancel;
    private static boolean initialized;
    private static long lastClick,lastWarning;
    private static int sessionRepeats,sessionConfirms,sessionCancels;

    private ArtemisFusionKeybinds(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        repeat=ConstellationClient.instance().keys().register("fusion_repeat",InputConstants.UNKNOWN.getValue());
        confirm=ConstellationClient.instance().keys().register("fusion_confirm",InputConstants.UNKNOWN.getValue());
        cancel=ConstellationClient.instance().keys().register("fusion_cancel",InputConstants.UNKNOWN.getValue());
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container)||menu(clean(container.getTitle().getString()))==null)return;
            ScreenKeyboardEvents.allowKeyPress(opened).register((ignored,event)->key(container,event));
            ScreenMouseEvents.allowMouseClick(opened).register((ignored,event)->mouse(container,event));
        });
    }

    private static boolean key(AbstractContainerScreen<?> screen,KeyEvent event){
        int matches=(repeat.matches(event)?1:0)+(confirm.matches(event)?1:0)+(cancel.matches(event)?1:0);
        if(matches==0)return true;
        if(matches>1||heldConflict(action(event))){
            warning("Fusion keybinds must be different and cannot be held together.");
            return !active()||!cfg.fusionKeybindConsume;
        }
        return perform(screen,action(event));
    }

    private static boolean mouse(AbstractContainerScreen<?> screen,MouseButtonEvent event){
        int matches=(repeat.matchesMouse(event)?1:0)+(confirm.matchesMouse(event)?1:0)+(cancel.matchesMouse(event)?1:0);
        if(matches==0)return true;
        if(matches>1||heldConflict(action(event))){
            warning("Fusion keybinds must be different and cannot be held together.");
            return !active()||!cfg.fusionKeybindConsume;
        }
        return perform(screen,action(event));
    }

    private static Action action(KeyEvent event){
        if(repeat.matches(event))return Action.REPEAT;
        if(confirm.matches(event))return Action.CONFIRM;
        return cancel.matches(event)?Action.CANCEL:null;
    }
    private static Action action(MouseButtonEvent event){
        if(repeat.matchesMouse(event))return Action.REPEAT;
        if(confirm.matchesMouse(event))return Action.CONFIRM;
        return cancel.matchesMouse(event)?Action.CANCEL:null;
    }

    private static boolean heldConflict(Action action){
        return action!=Action.REPEAT&&repeat.isDown()||action!=Action.CONFIRM&&confirm.isDown()||action!=Action.CANCEL&&cancel.isDown();
    }

    private static boolean perform(AbstractContainerScreen<?> screen,Action action){
        if(!active()||action==null)return true;
        if(action==Action.REPEAT&&!cfg.fusionKeybindRepeat||action==Action.CONFIRM&&!cfg.fusionKeybindConfirm||action==Action.CANCEL&&!cfg.fusionKeybindCancel)
            return !cfg.fusionKeybindConsume;
        String title=clean(screen.getTitle().getString());
        if(action==Action.REPEAT&&!title.equals("Fusion Box")||action!=Action.REPEAT&&!title.equals("Confirm Fusion"))
            return !cfg.fusionKeybindConsume;
        long now=System.currentTimeMillis(),cooldown=Math.clamp(cfg.fusionKeybindCooldownMillis,100,2000);
        if(now-lastClick<cooldown)return false;
        Slot target=find(screen,action);
        if(target==null){if(cfg.fusionKeybindFeedback)warning(buttonName(action)+" is not currently available.");return !cfg.fusionKeybindConsume;}
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.gameMode==null)return !cfg.fusionKeybindConsume;
        mc.gameMode.handleContainerInput(screen.getMenu().containerId,target.index,GLFW.GLFW_MOUSE_BUTTON_3,ContainerInput.CLONE,mc.player);
        lastClick=now;
        switch(action){case REPEAT->sessionRepeats++;case CONFIRM->sessionConfirms++;case CANCEL->sessionCancels++;}
        if(cfg.fusionKeybindFeedback)local(buttonName(action)+".");
        if(cfg.fusionKeybindSound)mc.player.playSound(action==Action.CANCEL?SoundEvents.UI_BUTTON_CLICK.value():SoundEvents.EXPERIENCE_ORB_PICKUP,.7f,action==Action.CANCEL?.8f:1.25f);
        return false;
    }

    private static Slot find(AbstractContainerScreen<?> screen,Action action){
        for(Slot slot:screen.getMenu().slots)if(valid(slot.getItem(),action))return slot;
        return null;
    }

    private static boolean valid(ItemStack stack,Action action){
        if(stack==null||stack.isEmpty())return false;
        String name=clean(stack.getHoverName().getString());
        return switch(action){
            case REPEAT->name.equals("Repeat Previous Fusion");
            case CONFIRM->stack.is(Items.DYED_TERRACOTTA.lime());
            case CANCEL->stack.is(Items.DYED_TERRACOTTA.red());
        };
    }

    private static String menu(String title){return title.equals("Fusion Box")||title.equals("Confirm Fusion")?title:null;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.fusionKeybinds&&ConstellationClient.loc().onHypixel();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static String buttonName(Action action){return switch(action){case REPEAT->"Repeated previous fusion";case CONFIRM->"Confirmed fusion";case CANCEL->"Cancelled fusion";};}
    private static void warning(String text){long now=System.currentTimeMillis();if(now-lastWarning<1000)return;lastWarning=now;if(cfg!=null&&cfg.fusionKeybindFeedback)local(text);}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§d[Fusion Keys] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fusionkeys").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetstats").executes(c->{sessionRepeats=sessionConfirms=sessionCancels=0;return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cooldown").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(100,2000)).executes(c->{cfg.fusionKeybindCooldownMillis=IntegerArgumentType.getInteger(c,"milliseconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Keys "+(cfg.fusionKeybinds?"on":"off")+", "+cfg.fusionKeybindCooldownMillis+"ms cooldown; session "+sessionRepeats+" repeat, "+sessionConfirms+" confirm, "+sessionCancels+" cancel.");local("Repeat, confirm and cancel bindings are configured in Minecraft Controls.");return 1;}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.fusionKeybinds=value;case"repeat"->cfg.fusionKeybindRepeat=value;case"confirm"->cfg.fusionKeybindConfirm=value;case"cancel"->cfg.fusionKeybindCancel=value;case"consume"->cfg.fusionKeybindConsume=value;case"feedback"->cfg.fusionKeybindFeedback=value;case"sound"->cfg.fusionKeybindSound=value;default->{local("Unknown Fusion key option.");return 0;}}save();return status();}
}
