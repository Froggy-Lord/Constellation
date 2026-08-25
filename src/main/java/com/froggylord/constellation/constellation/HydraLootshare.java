package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): features/chat/LootshareMessage.kt and utils/KeybindUtils.kt
// ported from Feesh (Apache-2.0): features/alerts/LootshareAlert.kt
public final class HydraLootshare {
    private static final Pattern PARTY=Pattern.compile("^Party > (?:\\[[^]]+] )?(?<player>\\w{1,16})(?: [^: ]+)?: (?<message>.+)$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Long> LAST=new HashMap<>();
    private static HydraConfig cfg;
    private static KeyMapping key;
    private static boolean initialized;
    private static String recentSender="";
    private static long recentAt;

    private HydraLootshare() {}
    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;key=ConstellationClient.instance().keys().register("lootshare_party",InputConstants.UNKNOWN.getValue());ClientTickEvents.END_CLIENT_TICK.register(client->{while(key.consumeClick())send();});ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetTransient());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetTransient());}

    private static void onChat(String text){if(!active())return;Matcher match=PARTY.matcher(text);if(!match.matches()||!match.group("message").equalsIgnoreCase("Lootshare!"))return;Minecraft mc=Minecraft.getInstance();String sender=match.group("player");if(mc.player==null||sender.equalsIgnoreCase(mc.getGameProfile().name()))return;long now=System.currentTimeMillis(),window=Math.clamp(cfg.lootshareDedupeSeconds,0,30)*1000L;if(now-LAST.getOrDefault(sender.toLowerCase(Locale.ROOT),0L)<window)return;LAST.put(sender.toLowerCase(Locale.ROOT),now);recentSender=sender;recentAt=now;record(sender,now);if(cfg.lootshareAlert)alert(sender);}
    private static void alert(String sender){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text=cfg.lootshareAlertTemplate.replace("{player}",sender).replace('&','\u00a7');if(cfg.lootshareTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTimes(0,Math.clamp(cfg.lootshareTitleTicks,5,200),10);mc.gui.hud.setTitle(Component.literal(text).withColor(cfg.lootshareColor&0xFFFFFF));if(cfg.lootshareSubtitleSender)mc.gui.hud.setSubtitle(Component.literal(sender));}if(cfg.lootshareChat)local(sender+" called for lootshare.");if(cfg.lootshareSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.2f);}
    private static int send(){if(!active()||!cfg.lootshareKeybind){local("Lootshare keybind is disabled.");return 0;}if(cfg.lootshareFishingWorldOnly&&!fishingWorld()){local("Lootshare is limited to fishing worlds.");return 0;}if(!PartyMessages.canSendAnywhere("lootshare")){local("Lootshare party message is disabled by the master message settings.");return 0;}if(!PartyMessages.trySendAnywhere("lootshare",Map.of())){if(cfg.lootshareSendFeedback)local("Lootshare call is on cooldown.");return 0;}if(cfg.lootshareSendFeedback)local("Sent the lootshare call.");return 1;}
    private static void record(String sender,long now){cfg.lootshareLastSender=sender;cfg.lootshareLastAt=now;if(cfg.lootshareTrackHistory){if(cfg.lootshareSenderCounts==null)cfg.lootshareSenderCounts=new HashMap<>();cfg.lootshareSenderCounts.merge(sender.toLowerCase(Locale.ROOT),1,Integer::sum);}ConstellationClient.saveConfig();}
    public static String hudText(){if(!active()||!cfg.lootshareHud||recentSender.isEmpty())return null;long age=(System.currentTimeMillis()-recentAt)/1000;if(age>Math.clamp(cfg.lootshareHudSeconds,1,120))return null;String sender=cfg.lootshareHudShowSender?recentSender+" ":"";return "\u00a7a"+sender+"Lootshare"+(cfg.lootshareHudShowAge?" \u00a78("+age+"s)":"");}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lootshare").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->clear())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,120)).executes(c->number("duration",IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dedupe").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,30)).executes(c->number("dedupe",IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("titleticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("ticks",IntegerArgumentType.integer(5,200)).executes(c->number("title",IntegerArgumentType.getInteger(c,"ticks"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(StringArgumentType.getString(c,"text"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){int total=cfg.lootshareSenderCounts==null?0:cfg.lootshareSenderCounts.values().stream().mapToInt(Integer::intValue).sum();local("Suite "+on(cfg.lootshareSuite)+", keybind "+on(cfg.lootshareKeybind)+", alert "+on(cfg.lootshareAlert)+", HUD "+on(cfg.lootshareHud)+", calls recorded "+total+", last "+(cfg.lootshareLastSender.isEmpty()?"none":cfg.lootshareLastSender)+'.');return 1;}
    private static int clear(){if(cfg.lootshareSenderCounts==null)cfg.lootshareSenderCounts=new HashMap<>();else cfg.lootshareSenderCounts.clear();cfg.lootshareLastSender="";cfg.lootshareLastAt=0;resetTransient();ConstellationClient.saveConfig();local("Lootshare history cleared.");return 1;}
    private static int number(String field,int value){if(field.equals("duration"))cfg.lootshareHudSeconds=value;else if(field.equals("dedupe"))cfg.lootshareDedupeSeconds=value;else cfg.lootshareTitleTicks=value;ConstellationClient.saveConfig();return status();}
    private static int template(String value){String clean=value.replace('\n',' ').replace('\r',' ').trim();if(clean.isEmpty()||clean.length()>160){local("Template must contain 1-160 characters.");return 0;}cfg.lootshareAlertTemplate=clean;ConstellationClient.saveConfig();local("Alert template updated. Variable: {player}.");return 1;}
    private static int color(String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw.startsWith("0x")?raw.substring(2):raw;long parsed=Long.parseUnsignedLong(value,16);cfg.lootshareColor=value.length()<=6?(int)(0xFF000000L|parsed):(int)parsed;ConstellationClient.saveConfig();local("Color updated.");return 1;}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB.");return 0;}}
    private static int option(String name,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled","suite"->cfg.lootshareSuite=value;case"key","keybind"->cfg.lootshareKeybind=value;case"alert"->cfg.lootshareAlert=value;case"title"->cfg.lootshareTitle=value;case"sender","subtitle"->cfg.lootshareSubtitleSender=value;case"chat"->cfg.lootshareChat=value;case"sound"->cfg.lootshareSound=value;case"history"->cfg.lootshareTrackHistory=value;case"hud"->cfg.lootshareHud=value;case"hudsender"->cfg.lootshareHudShowSender=value;case"hudage"->cfg.lootshareHudShowAge=value;case"feedback"->cfg.lootshareSendFeedback=value;case"fishingworld"->cfg.lootshareFishingWorldOnly=value;default->{local("Unknown option.");return 0;}}ConstellationClient.saveConfig();return status();}
    private static boolean fishingWorld(){return switch(ConstellationClient.loc().area()){case THE_RIFT,GARDEN,KUUDRA,CATACOMBS,MASTER_MODE,DUNGEON_HUB,THE_END,GLACITE_MINESHAFT->false;default->true;};}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.lootshareSuite&&ConstellationClient.loc().onHypixel();}
    private static void resetTransient(){LAST.clear();recentSender="";recentAt=0;}
    private static String clean(String text){String value=ChatFormatting.stripFormatting(text);return value==null?text.trim():value.trim();}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"\u00a7aon":"\u00a7coff";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7a[Lootshare] \u00a7f"+text));}
}
