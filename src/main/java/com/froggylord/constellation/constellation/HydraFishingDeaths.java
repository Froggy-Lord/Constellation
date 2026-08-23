package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): features/alerts/PlayerDeathAlert.kt
// ported from Feesh (Apache-2.0): features/chat/PlayerDeathMessage.kt
public final class HydraFishingDeaths {
    private static final Map<String,String> BOSSES=new LinkedHashMap<>();
    private static final Pattern OWN=Pattern.compile("^\\x{2620} You were killed by (Ragnarok|Thunder|Lord Jawbus|Jawbus Follower|Wiki Tiki|Wiki Tiki Laser Totem|Titanoboa|Nessie|Giant Isopod)\\.$");
    private static final Pattern PARTY=Pattern.compile("^Party > (?:\\[[^]]+] )?(?<player>\\w{1,16})(?: [^: ]+)?: (?<message>.+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_DEATH=Pattern.compile("^--> I was killed(?: by (?:Ragnarok|Thunder|Lord Jawbus|Jawbus Follower|Wiki Tiki|Wiki Tiki Laser Totem|Titanoboa|Nessie|Giant Isopod))?, please wait for me until I come back <--$");
    static {BOSSES.put("ragnarok","Ragnarok");BOSSES.put("thunder","Thunder");BOSSES.put("jawbus","Lord Jawbus");BOSSES.put("follower","Jawbus Follower");BOSSES.put("tiki","Wiki Tiki");BOSSES.put("laser","Wiki Tiki Laser Totem");BOSSES.put("titanoboa","Titanoboa");BOSSES.put("nessie","Nessie");BOSSES.put("isopod","Giant Isopod");}
    private static HydraConfig cfg;
    private static boolean initialized;
    private static long lastOwnAt,lastTeamAt;
    private static String lastBoss="",lastPlayer="";

    private HydraFishingDeaths() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ClientReceiveMessageEvents.ALLOW_GAME.register((component,overlay)->{if(!overlay)onChat(clean(component.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void onChat(String message){if(!active())return;Matcher own=OWN.matcher(message);if(own.matches()){onOwnDeath(own.group(1));return;}Matcher party=PARTY.matcher(message);if(party.matches()&&PARTY_DEATH.matcher(party.group("message")).matches())onTeamDeath(party.group("player"));}

    // ported from Feesh (Apache-2.0): features/alerts/PlayerDeathAlert.kt
    private static void onOwnDeath(String boss){if(!selected(boss))return;long now=System.currentTimeMillis();if(now-lastOwnAt<cooldown())return;lastOwnAt=now;lastBoss=boss;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.fishingDeathOwnAlert){String text=format(cfg.fishingDeathOwnTemplate,"",boss);if(cfg.fishingDeathTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(text).withColor(cfg.fishingDeathOwnColor&0xFFFFFF));}if(cfg.fishingDeathChat)local(text);if(cfg.fishingDeathSound)mc.player.playSound(SoundEvents.VILLAGER_DEATH,.9f,1f);}if(boss.equals("Nessie")&&cfg.fishingDeathNessieWarpButton)nessieButton();if(cfg.fishingDeathPartyMessage)PartyMessages.sendAnywhere("fishing-death",Map.of("boss",boss));}
    private static void onTeamDeath(String player){Minecraft mc=Minecraft.getInstance();if(mc.player==null||!cfg.fishingDeathTeammateAlert||player.equalsIgnoreCase(mc.getGameProfile().name()))return;long now=System.currentTimeMillis();if(now-lastTeamAt<cooldown()&&player.equalsIgnoreCase(lastPlayer))return;lastTeamAt=now;lastPlayer=player;String text=format(cfg.fishingDeathTeammateTemplate,player,"");if(cfg.fishingDeathTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(player+" was killed").withColor(cfg.fishingDeathTeammateColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal("Wait for them to come back"));}if(cfg.fishingDeathChat)local(text);if(cfg.fishingDeathSound)mc.player.playSound(SoundEvents.VILLAGER_DEATH,.9f,1f);}

    private static void nessieButton(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;Component button=Component.literal("\u00a75[Fishing Death] \u00a7fNessie killed you. ").append(Component.literal("\u00a7a[Warp to Murkwater Loch]").withStyle(style->style.withClickEvent(new ClickEvent.RunCommand("/warp murk")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Run /warp murk")))));mc.player.sendSystemMessage(button);}
    private static boolean selected(String boss){if(!cfg.fishingDeathBossFilter)return true;return cfg.fishingDeathBosses!=null&&cfg.fishingDeathBosses.stream().anyMatch(boss::equalsIgnoreCase);}
    private static long cooldown(){return Math.clamp(cfg.fishingDeathCooldownSeconds,0,30)*1000L;}
    private static String format(String template,String player,String boss){String value=template==null?"":template;return value.replace("{player}",player).replace("{boss}",boss);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.fishingDeathSuite&&ConstellationClient.loc().onHypixel();}
    private static String clean(String value){String plain=ChatFormatting.stripFormatting(value);return plain==null?"":plain.trim();}
    private static void reset(){lastOwnAt=0;lastTeamAt=0;lastBoss="";lastPlayer="";}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fishingdeath").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{reset();local("Fishing-death state cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cooldown").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(0,30)).executes(c->cooldown(IntegerArgumentType.getInteger(c,"seconds"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("boss").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->boss(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("owntemplate").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(true,StringArgumentType.getString(c,"text"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("teamtemplate").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->template(false,StringArgumentType.getString(c,"text"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Own alert "+on(cfg.fishingDeathOwnAlert)+", teammate alert "+on(cfg.fishingDeathTeammateAlert)+", party message "+on(cfg.fishingDeathPartyMessage)+", Nessie warp "+on(cfg.fishingDeathNessieWarpButton)+"; last boss "+(lastBoss.isEmpty()?"none":lastBoss)+".");return 1;}
    private static int cooldown(int seconds){cfg.fishingDeathCooldownSeconds=seconds;save();return status();}
    private static int boss(String key,String raw){String name=BOSSES.get(key.toLowerCase(Locale.ROOT));Boolean state=parse(raw);if(name==null){local("Boss must be ragnarok, thunder, jawbus, follower, tiki, laser, titanoboa, nessie, or isopod.");return 0;}if(state==null){local("State must be on or off.");return 0;}if(cfg.fishingDeathBosses==null)cfg.fishingDeathBosses=new ArrayList<>();cfg.fishingDeathBosses.removeIf(name::equalsIgnoreCase);if(state)cfg.fishingDeathBosses.add(name);save();return status();}
    private static int template(boolean own,String raw){String value=raw==null?"":raw.replace('\n',' ').replace('\r',' ').trim();if(value.isEmpty()||value.length()>160){local("Template must contain 1-160 characters.");return 0;}if(own)cfg.fishingDeathOwnTemplate=value;else cfg.fishingDeathTeammateTemplate=value;save();return status();}
    private static int color(String target,String raw){try{String value=raw.startsWith("#")?raw.substring(1):raw;if(value.length()==6)value="FF"+value;if(value.length()!=8)throw new NumberFormatException();int color=(int)Long.parseLong(value,16);if(target.equalsIgnoreCase("own"))cfg.fishingDeathOwnColor=color;else if(target.equalsIgnoreCase("team"))cfg.fishingDeathTeammateColor=color;else{local("Target must be own or team.");return 0;}save();return status();}catch(NumberFormatException ignored){local("Color must be RRGGBB or AARRGGBB hex.");return 0;}}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"own"->cfg.fishingDeathOwnAlert=value;case"team","teammate"->cfg.fishingDeathTeammateAlert=value;case"title"->cfg.fishingDeathTitle=value;case"chat"->cfg.fishingDeathChat=value;case"sound"->cfg.fishingDeathSound=value;case"party","message"->cfg.fishingDeathPartyMessage=value;case"nessie","warp"->cfg.fishingDeathNessieWarpButton=value;case"filter"->cfg.fishingDeathBossFilter=value;default->{local("Option must be own, teammate, title, chat, sound, party, nessie, or filter.");return 0;}}save();return status();}
    private static Boolean parse(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a75[Fishing Death] \u00a7f"+text));}
}
