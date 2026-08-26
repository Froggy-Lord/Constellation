package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): events/publishers/TrophyDiscoveredPublisher.kt
// ported from Feesh (Apache-2.0): features/alerts/TrophyFrogDiscoveredAlert.kt and TrophyFishDiscoveredAlert.kt
// ported from Feesh (Apache-2.0): features/chat/TrophyFrogDiscoveredMessage.kt and TrophyFishDiscoveredMessage.kt
public final class HydraTrophyDiscoveries {
    private enum Type { FROG("Frog", "trophy-frog-discovery"), FISH("Trophy Fish", "trophy-fish-discovery"); final String name,id; Type(String name,String id){this.name=name;this.id=id;} }
    private record Discovery(Type type,String details,String name,String grade,long at) {}
    private static final Pattern DISCOVERY=Pattern.compile("^NEW DISCOVERY: (.+?)$");
    private static final Pattern GRADE=Pattern.compile("^(.+?) (BRONZE|SILVER|GOLD|DIAMOND)$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Long> LAST=new HashMap<>();
    private static HydraConfig cfg;
    private static boolean initialized;
    private static Discovery recent;

    private HydraTrophyDiscoveries() {}
    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetTransient());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetTransient());}

    private static void onChat(String text){if(!active())return;Matcher match=DISCOVERY.matcher(text);if(!match.matches())return;String details=match.group(1).trim();if(details.isEmpty())return;boolean obfuscated=details.contains("Obfuscated-1")||details.contains("Obfuscated 1");SkyblockArea area=ConstellationClient.loc().area();Type type;if(area==SkyblockArea.LOTUS_ATOLL&&!obfuscated)type=Type.FROG;else if(obfuscated||area==SkyblockArea.CRIMSON_ISLE)type=Type.FISH;else return;if(type==Type.FROG&&!cfg.trophyFrogDiscovery||type==Type.FISH&&!cfg.trophyFishDiscovery)return;long now=System.currentTimeMillis();String key=type.name()+":"+details.toLowerCase(Locale.ROOT);long cooldown=Math.clamp(cfg.trophyDiscoveryDedupeSeconds,0,60)*1000L;if(now-LAST.getOrDefault(key,0L)<cooldown)return;LAST.put(key,now);String name=details,grade="";Matcher gradeMatch=GRADE.matcher(details);if(gradeMatch.matches()){name=gradeMatch.group(1);grade=gradeMatch.group(2).toUpperCase(Locale.ROOT);}Discovery discovery=new Discovery(type,details,name,grade,now);recent=discovery;record(discovery);alert(discovery);if(type==Type.FROG&&cfg.trophyFrogAutoShare||type==Type.FISH&&cfg.trophyFishAutoShare)share(discovery);}

    private static void alert(Discovery d){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;int color=color(d);if(cfg.trophyDiscoveryTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTimes(0,Math.clamp(cfg.trophyDiscoveryTitleTicks,5,200),10);mc.gui.hud.setTitle(Component.literal(d.details).withColor(color&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(d.type==Type.FROG?"FROG DISCOVERED!":"TROPHY FISH DISCOVERED!").withColor(0x55FF55));}String local=format(cfg.trophyDiscoveryLocalTemplate,d);if(cfg.trophyDiscoveryChat)mc.player.sendSystemMessage(Component.literal("\u00a7b[Trophy Discovery] \u00a7f"+local).append(shareButton(d)));if(cfg.trophyDiscoverySound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.7f,1.3f);}
    private static Component shareButton(Discovery d){String text=PartyMessages.preview(d.type.id,variables(d));if(!cfg.trophyDiscoveryClickShare||!PartyMessages.canSendAnywhere(d.type.id)||text.isEmpty())return Component.empty();String command="/pc "+text;return Component.literal(" \u00a7a[Share]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Share this discovery to party chat"))));}
    private static void share(Discovery d){PartyMessages.sendAnywhere(d.type.id,variables(d),d.details);}
    private static Map<String,Object> variables(Discovery d){return Map.of("details",d.details,"name",d.name,"grade",d.grade,"type",d.type.name);}
    private static void record(Discovery d){cfg.trophyDiscoveryLastType=d.type.name;cfg.trophyDiscoveryLastDetails=d.details;cfg.trophyDiscoveryLastAt=d.at;if(cfg.trophyDiscoveryPersistCounts){if(cfg.trophyDiscoveryCounts==null)cfg.trophyDiscoveryCounts=new HashMap<>();cfg.trophyDiscoveryCounts.merge(d.type.name,1,Integer::sum);}ConstellationClient.saveConfig();}

    public static String hudText(){if(!active()||!cfg.trophyDiscoveryHud||recent==null)return null;long age=(System.currentTimeMillis()-recent.at)/1000;if(age>Math.clamp(cfg.trophyDiscoveryHudSeconds,1,300))return null;String prefix=cfg.trophyDiscoveryHudShowType?recent.type.name+": ":"";return "\u00a7f"+prefix+recent.details+(cfg.trophyDiscoveryHudShowAge?" \u00a78("+age+"s)":"");}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("trophydiscovery").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->clear())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,300)).executes(c->number("duration",IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dedupe").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,60)).executes(c->number("dedupe",IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("titleticks").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("ticks",IntegerArgumentType.integer(5,200)).executes(c->number("title",IntegerArgumentType.getInteger(c,"ticks"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(StringArgumentType.getString(c,"text"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"argb")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){int frogs=cfg.trophyDiscoveryCounts==null?0:cfg.trophyDiscoveryCounts.getOrDefault(Type.FROG.name,0),fish=cfg.trophyDiscoveryCounts==null?0:cfg.trophyDiscoveryCounts.getOrDefault(Type.FISH.name,0);local("Suite "+on(cfg.trophyDiscoverySuite)+", frogs "+on(cfg.trophyFrogDiscovery)+" ("+frogs+"), fish "+on(cfg.trophyFishDiscovery)+" ("+fish+"), HUD "+on(cfg.trophyDiscoveryHud)+'.');return 1;}
    private static int clear(){if(cfg.trophyDiscoveryCounts==null)cfg.trophyDiscoveryCounts=new HashMap<>();else cfg.trophyDiscoveryCounts.clear();cfg.trophyDiscoveryLastType="";cfg.trophyDiscoveryLastDetails="";cfg.trophyDiscoveryLastAt=0;resetTransient();ConstellationClient.saveConfig();local("Discovery history cleared.");return 1;}
    private static int number(String field,int value){if(field.equals("duration"))cfg.trophyDiscoveryHudSeconds=value;else if(field.equals("dedupe"))cfg.trophyDiscoveryDedupeSeconds=value;else cfg.trophyDiscoveryTitleTicks=value;ConstellationClient.saveConfig();local("Timing updated.");return 1;}
    private static int template(String value){cfg.trophyDiscoveryLocalTemplate=value;ConstellationClient.saveConfig();local("Local template updated. Variables: {type}, {details}, {name}, {grade}.");return 1;}
    private static int color(String type,String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw.startsWith("0x")?raw.substring(2):raw;long parsed=Long.parseUnsignedLong(value,16);int argb=value.length()<=6?(int)(0xFF000000L|parsed):(int)parsed;if(type.equalsIgnoreCase("frog"))cfg.trophyFrogColor=argb;else if(type.equalsIgnoreCase("fish"))cfg.trophyFishColor=argb;else{local("Type must be frog or fish.");return 0;}ConstellationClient.saveConfig();local("Color updated.");return 1;}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB.");return 0;}}
    private static int option(String name,String state){Boolean value=parse(state);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled","suite"->cfg.trophyDiscoverySuite=value;case"frog"->cfg.trophyFrogDiscovery=value;case"fish"->cfg.trophyFishDiscovery=value;case"title"->cfg.trophyDiscoveryTitle=value;case"chat"->cfg.trophyDiscoveryChat=value;case"sound"->cfg.trophyDiscoverySound=value;case"frogshare"->cfg.trophyFrogAutoShare=value;case"fishshare"->cfg.trophyFishAutoShare=value;case"clickshare"->cfg.trophyDiscoveryClickShare=value;case"hud"->cfg.trophyDiscoveryHud=value;case"hudtype"->cfg.trophyDiscoveryHudShowType=value;case"hudage"->cfg.trophyDiscoveryHudShowAge=value;case"counts"->cfg.trophyDiscoveryPersistCounts=value;default->{local("Unknown option.");return 0;}}ConstellationClient.saveConfig();local("Option updated.");return 1;}
    private static int color(Discovery d){if(d.grade.equals("BRONZE"))return 0xFFAA5500;if(d.grade.equals("SILVER"))return 0xFFAAAAAA;if(d.grade.equals("GOLD"))return 0xFFFFAA00;if(d.grade.equals("DIAMOND"))return 0xFF55FFFF;return d.type==Type.FROG?cfg.trophyFrogColor:cfg.trophyFishColor;}
    private static String format(String template,Discovery d){return template.replace("{type}",d.type.name).replace("{details}",d.details).replace("{name}",d.name).replace("{grade}",d.grade).replace('&','\u00a7');}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.trophyDiscoverySuite&&ConstellationClient.loc().onHypixel();}
    private static void resetTransient(){LAST.clear();recent=null;}
    private static String clean(String text){String value=ChatFormatting.stripFormatting(text);return value==null?text:value;}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"\u00a7aon":"\u00a7coff";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a7b[Trophy Discovery] \u00a7f"+text));}
}
