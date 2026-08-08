package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.ui.SpeedPresetScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/speedpreset/SpeedPresets.java
public final class PhoenixSpeedPresets {
    private static final Pattern ALIAS=Pattern.compile("^setmaxspeed\\s+([A-Za-z]\\w*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME=Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,15}$");
    private static final List<String> DIRECT=List.of("default","crops","cocoa","mushroom","cane","squash","cactus");
    private static PhoenixConfig cfg;private static boolean initialized;private static KeyMapping menu,next,previous;private static final Map<String,KeyMapping> direct=new LinkedHashMap<>();
    private static String lastPreset="";private static int lastSpeed=-1;private static long lastUseAt;
    private PhoenixSpeedPresets(){}

    public static void init(PhoenixConfig config){
        cfg=config;normalize();if(initialized)return;initialized=true;
        menu=ConstellationClient.instance().keys().register("speed_preset_menu",com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue());
        next=ConstellationClient.instance().keys().register("speed_preset_next",com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue());
        previous=ConstellationClient.instance().keys().register("speed_preset_previous",com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue());
        for(String name:DIRECT)direct.put(name,ConstellationClient.instance().keys().register("speed_preset_"+name,com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue()));
        ClientTickEvents.END_CLIENT_TICK.register(PhoenixSpeedPresets::tick);
        ClientSendMessageEvents.MODIFY_COMMAND.register(PhoenixSpeedPresets::rewrite);
    }
    private static void tick(Minecraft mc){if(mc.gui.screen()!=null)return;while(menu.consumeClick())open();while(next.consumeClick())cycle(1);while(previous.consumeClick())cycle(-1);for(var entry:direct.entrySet())while(entry.getValue().consumeClick())use(entry.getKey());}
    private static String rewrite(String command){if(!enabled()||!ConstellationClient.loc().onHypixel())return command;Matcher matcher=ALIAS.matcher(command);if(!matcher.matches())return command;Integer speed=presets().get(key(matcher.group(1)));return speed==null?command:"setmaxspeed "+speed;}

    public static Map<String,Integer> presets(){normalize();return cfg.speedPresetProfiles.computeIfAbsent(profile(),ignored->defaults());}
    public static String profile(){if(cfg.speedPresetsAutoProfile){String value=LyraStorageValue.currentProfileKey();if(value!=null&&!value.isBlank())return value.toLowerCase(Locale.ROOT);}return cfg.speedPresetsSelectedProfile.isBlank()?"default":key(cfg.speedPresetsSelectedProfile);}
    public static String lastPreset(){return lastPreset;}public static int lastSpeed(){return lastSpeed;}
    public static long cooldownLeft(){return Math.max(0,Math.clamp(cfg.speedPresetsCooldownMillis,100,5000)-(System.currentTimeMillis()-lastUseAt));}
    public static boolean recentlyUsed(){return System.currentTimeMillis()-lastUseAt<Math.clamp(cfg.speedPresetsHudSeconds,1,30)*1000L;}
    public static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.speedPresets;}
    public static PhoenixConfig config(){return cfg;}
    public static boolean validName(String name){return name!=null&&NAME.matcher(name).matches();}
    public static void open(){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.execute(()->mc.setScreenAndShow(new SpeedPresetScreen(mc.gui.screen())));}
    public static boolean set(String name,int speed){if(!validName(name)||speed<0||speed>500)return false;presets().put(key(name),speed);save();return true;}
    public static boolean remove(String name){if(presets().remove(key(name))==null)return false;save();return true;}
    public static void reset(){cfg.speedPresetProfiles.put(profile(),defaults());save();}
    public static boolean use(String name){
        if(!enabled()){local("Speed presets are disabled.");return false;}if(!ConstellationClient.loc().onHypixel()){local("Speed presets only work on Hypixel SkyBlock.");return false;}
        String id=key(name);Integer speed=presets().get(id);if(speed==null){local("Unknown preset "+name+".");return false;}long now=System.currentTimeMillis();if(now-lastUseAt<Math.clamp(cfg.speedPresetsCooldownMillis,100,5000))return false;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;lastUseAt=now;lastPreset=id;lastSpeed=speed;mc.player.connection.sendCommand("setmaxspeed "+speed);feedback(id,speed);return true;
    }
    public static boolean signAlias(String value){return enabled()&&ConstellationClient.loc().onHypixel()&&presets().containsKey(key(value));}
    public static String signValue(String value){Integer speed=presets().get(key(value));return speed==null?value:Integer.toString(speed);}
    public static String signPreview(String value){Integer speed=presets().get(key(value));return speed==null?"":value+" -> "+speed;}
    private static void cycle(int direction){List<String> names=new ArrayList<>(presets().keySet());if(names.isEmpty()){local("No speed presets are saved.");return;}int index=lastPreset.isBlank()?-1:names.indexOf(lastPreset);index=Math.floorMod(index+direction,names.size());use(names.get(index));}
    private static void feedback(String name,int speed){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text="Speed preset "+name+": "+speed;if(cfg.speedPresetsFeedbackChat)mc.player.sendSystemMessage(Component.literal("§b[Speed] §f"+text));if(cfg.speedPresetsFeedbackActionbar)mc.gui.hud.setOverlayMessage(Component.literal(text),false);if(cfg.speedPresetsFeedbackSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.6f,1.25f);}
    private static LinkedHashMap<String,Integer> defaults(){LinkedHashMap<String,Integer> map=new LinkedHashMap<>();map.put("default",100);map.put("crops",93);map.put("cocoa",155);map.put("mushroom",233);map.put("cane",327);map.put("squash",327);map.put("cactus",464);return map;}
    private static void normalize(){if(cfg.speedPresetProfiles==null)cfg.speedPresetProfiles=new LinkedHashMap<>();if(cfg.speedPresetsSelectedProfile==null||cfg.speedPresetsSelectedProfile.isBlank())cfg.speedPresetsSelectedProfile="default";cfg.speedPresetProfiles.computeIfAbsent(profile(),ignored->defaults());}
    private static String key(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    public static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("speedpreset").executes(c->{open();return 1;})
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("menu").executes(c->{open();return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c->list()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next").executes(c->{cycle(1);return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("previous").executes(c->{cycle(-1);return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{reset();return list();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("use").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).executes(c->use(StringArgumentType.getString(c,"name"))?1:0)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).executes(c->remove(StringArgumentType.getString(c,"name"))?list():0)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("speed",IntegerArgumentType.integer(0,500)).executes(c->set(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"speed"))?list():0))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("profile").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).executes(c->selectProfile(StringArgumentType.getString(c,"name")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cooldown").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(100,5000)).executes(c->{cfg.speedPresetsCooldownMillis=IntegerArgumentType.getInteger(c,"milliseconds");save();return list();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hudseconds").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,30)).executes(c->{cfg.speedPresetsHudSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return list();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int list(){local("Profile "+profile()+": "+presets().entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(", ")));return 1;}
    private static int selectProfile(String raw){if(!validName(raw)){local("Profile names must start with a letter and use letters, numbers, or underscores.");return 0;}cfg.speedPresetsSelectedProfile=key(raw);cfg.speedPresetProfiles.computeIfAbsent(profile(),ignored->defaults());save();return list();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.speedPresets=value;case"autoprofile"->cfg.speedPresetsAutoProfile=value;case"chat"->cfg.speedPresetsFeedbackChat=value;case"actionbar"->cfg.speedPresetsFeedbackActionbar=value;case"sound"->cfg.speedPresetsFeedbackSound=value;case"hud"->cfg.speedPresetsHud=value;case"recenthud"->cfg.speedPresetsHudRecentOnly=value;case"hudname"->cfg.speedPresetsHudName=value;case"hudspeed"->cfg.speedPresetsHudSpeed=value;case"hudprofile"->cfg.speedPresetsHudProfile=value;default->{local("Unknown option. Use enabled, autoprofile, chat, actionbar, sound, hud, recenthud, hudname, hudspeed, or hudprofile.");return 0;}}save();return list();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§b[Speed] §f"+text));}
}
