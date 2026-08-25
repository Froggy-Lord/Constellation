package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
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
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/trophy/GoldenFishTimer.kt
public final class HydraGoldenFish {
    public record State(boolean active,boolean ready,int interactions,long despawnMillis,long lastSpawnAgo,long lastRodAgo,boolean rodValid,long spawnMillis,boolean available,long availableFor,double chance) {}
    private static final String SPAWN="You spot a Golden Fish surface from beneath the lava!";
    private static final String INTERACT="The Golden Fish escapes your hook but looks weakened.";
    private static final String WEAK="The Golden Fish is weak!";
    private static final String DESPAWN="The Golden Fish swims back beneath the lava...";
    private static final String TEXTURE="120cf3c0a40fc67e0e5fe0c46b0ae409ac71030a7656da17b11ed001645888fe";
    private static final Set<String> LAVA_RODS=Set.of("STARTER_LAVA_ROD","INFERNO_ROD","MAGMA_ROD","HELLFIRE_ROD","POLISHED_TOPAZ_ROD");
    private static final long DESPAWN_MS=60_000,ROD_MS=180_000,ENTITY_WINDOW_MS=10_000;
    private static HydraConfig cfg;
    private static boolean initialized,hadBobber,goingDownInit=true,goingDownPost,rodWarned,ready,wasEnabled;
    private static long lastRodAt,lastSpawnAt,spawnBase,despawnAt,spawnChatAt,entitySeenAt;
    private static int interactions;
    private static ArmorStand possibleEntity,confirmedEntity;
    private static final Map<ArmorStand,Long> CANDIDATES=new HashMap<>();
    private static String profileKey="";
    private HydraGoldenFish() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-golden-fish",HydraGoldenFish::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void tick(){Minecraft mc=Minecraft.getInstance();String current=LyraStorageValue.currentProfileKey();if(!current.equals(profileKey)){profileKey=current;resetSession();}boolean enabled=enabled();if(!enabled||mc.player==null||mc.level==null){if(wasEnabled)resetSession();wasEnabled=false;return;}wasEnabled=true;long now=System.currentTimeMillis();boolean bobber=mc.player.fishing!=null;if(bobber&&!hadBobber){goingDownInit=true;goingDownPost=false;}hadBobber=bobber;if(bobber){var hook=mc.player.fishing;if(hook.isInLava()&&hook.tickCount>=5){double y=hook.getDeltaMovement().y;if(y>0&&goingDownInit)goingDownInit=false;else if(y<0&&!goingDownInit&&!goingDownPost){goingDownPost=true;validThrow(now);}}}
        if(lastRodAt>0&&now-lastRodAt>ROD_MS){lastRodAt=0;spawnBase=0;rodWarned=false;}else if(lastRodAt>0&&cfg.goldenFishRodWarning&&!rodWarned&&ROD_MS-(now-lastRodAt)<=Math.clamp(cfg.goldenFishRodWarningSeconds,1,60)*1000L){rodWarned=true;rodWarning();}
        if(despawnAt>0&&now>=despawnAt)finish(now);scanEntity(mc,now);if(spawnChatAt>0&&now-spawnChatAt>ENTITY_WINDOW_MS){spawnChatAt=0;possibleEntity=null;}}

    private static void validThrow(long now){lastRodAt=now;rodWarned=false;if(spawnBase==0)spawnBase=now;}
    private static void scanEntity(Minecraft mc,long now){CANDIDATES.entrySet().removeIf(e->!e.getKey().isAlive()||now-e.getValue()>120_000);for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&golden(stand))CANDIDATES.putIfAbsent(stand,now);if(confirmedEntity!=null&&!confirmedEntity.isAlive())confirmedEntity=null;if(spawnChatAt==0||now-spawnChatAt>ENTITY_WINDOW_MS||confirmedEntity!=null)return;double best=Double.MAX_VALUE;ArmorStand found=null;long seen=0;var origin=mc.player.fishing==null?mc.player:mc.player.fishing;for(var entry:CANDIDATES.entrySet()){if(Math.abs(entry.getValue()-spawnChatAt)>ENTITY_WINDOW_MS)continue;double distance=entry.getKey().distanceToSqr(origin);if(distance<best){best=distance;found=entry.getKey();seen=entry.getValue();}}if(found!=null){possibleEntity=found;entitySeenAt=seen;confirmEntity(now);}}
    private static void confirmEntity(long now){if(possibleEntity==null||spawnChatAt==0||Math.abs(entitySeenAt-spawnChatAt)>ENTITY_WINDOW_MS)return;confirmedEntity=possibleEntity;possibleEntity=null;lastSpawnAt=now;despawnAt=now+DESPAWN_MS;interactions=0;ready=false;spawnChatAt=0;spawnAlert();}

    private static void onChat(String message){if(!enabled())return;long now=System.currentTimeMillis();if(message.equals(SPAWN)){spawnChatAt=now;confirmEntity(now);return;}if(message.equals(INTERACT)){if(despawnAt>0){despawnAt=now+DESPAWN_MS;interactions++;}return;}if(message.equals(WEAK)){if(despawnAt>0){despawnAt=now+DESPAWN_MS;ready=true;}return;}if(message.equals(DESPAWN)||message.contains("TROPHY FISH! You caught a Golden Fish "))finish(now);}
    private static void finish(long now){despawnAt=0;spawnChatAt=0;interactions=0;ready=false;possibleEntity=null;confirmedEntity=null;spawnBase=now;}
    private static void spawnAlert(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.goldenFishSpawnTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Golden Fish spawned!").withColor(cfg.goldenFishNormalColor&0xFFFFFF));}if(cfg.goldenFishSpawnSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,0.7f,1.4f);}
    private static void rodWarning(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.goldenFishRodWarningTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Throw your lava rod!").withColor(cfg.goldenFishWarningColor&0xFFFFFF));}if(cfg.goldenFishRodWarningSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),0.8f,0.7f);}

    public static State state(){if(!visible())return null;long now=System.currentTimeMillis(),minimum=minimum(),maximum=maximum(),availableAt=spawnBase==0?0:spawnBase+minimum;boolean available=availableAt>0&&now>=availableAt;long sinceAvailable=available?now-availableAt:0;double chance=available?Math.clamp(sinceAvailable/(double)Math.max(1,maximum-minimum),0,1):0;return new State(despawnAt>now,ready,interactions,Math.max(0,despawnAt-now),lastSpawnAt==0?-1:now-lastSpawnAt,lastRodAt==0?-1:now-lastRodAt,lastRodAt>0&&now-lastRodAt<=ROD_MS,spawnBase==0?-1:Math.max(0,availableAt-now),available,sinceAvailable,chance);}
    public static boolean visible(){return enabled()&&hasLavaRod();}
    public static HydraConfig config(){return cfg;}
    public static String time(long millis){long seconds=Math.max(0,millis/1000);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    public static void draw(WorldRenderer.Ctx ctx){if(!visible()||confirmedEntity==null||!confirmedEntity.isAlive())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.player.distanceTo(confirmedEntity)>Math.clamp(cfg.goldenFishRange,8,128))return;int color=ready&&cfg.goldenFishHighlightReady?cfg.goldenFishReadyColor:cfg.goldenFishNormalColor;boolean walls=cfg.goldenFishThroughWalls;if(cfg.goldenFishWorldBox)ctx.box(confirmedEntity.getBoundingBox().inflate(.15),color,walls);if(cfg.goldenFishWorldLabel){var state=state();String label="Golden Fish"+(cfg.goldenFishShowInteractions?" "+interactions+"/3":"");ctx.label(confirmedEntity.position().add(0,2.5,0),label,color,walls);if(cfg.goldenFishShowDespawn&&state!=null)ctx.label(confirmedEntity.position().add(0,2.2,0),time(state.despawnMillis()),cfg.goldenFishTimerColor,walls);if(ready)ctx.label(confirmedEntity.position().add(0,2.8,0),"PULL",cfg.goldenFishReadyColor,walls);}}

    private static long minimum(){return Math.max(60_000,480_000-Math.clamp(cfg.goldenFishGoldfinLevel,0,10)*30_000L);}
    private static long maximum(){return Math.max(minimum()+1,720_000-Math.clamp(cfg.goldenFishGoldfinLevel,0,10)*30_000L);}
    private static boolean hasLavaRod(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(ItemStack stack:mc.player.getInventory())if(LAVA_RODS.contains(LyraTooltips.marketId(stack)))return true;return LAVA_RODS.contains(LyraTooltips.marketId(mc.player.getOffhandItem()));}
    private static boolean enabled(){if(cfg==null||!cfg.enabled||!cfg.goldenFishTimer||!cfg.goldenFishSuite||!ConstellationClient.loc().onHypixel())return false;return cfg.goldenFishAllowOutsideCrimson||ConstellationClient.loc().area()==SkyblockArea.CRIMSON_ISLE;}
    private static boolean golden(ArmorStand stand){if(!stand.isAlive())return false;ItemStack helmet=stand.getItemBySlot(EquipmentSlot.HEAD);if(!helmet.is(Items.PLAYER_HEAD))return false;var profile=helmet.get(DataComponents.PROFILE);if(profile==null)return false;for(Property property:profile.partialProfile().properties().get("textures"))if(property!=null&&TEXTURE.equals(textureHash(property.value())))return true;return false;}
    private static String textureHash(String encoded){try{String json=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);String url=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();int slash=url.lastIndexOf('/');return slash<0?"":url.substring(slash+1);}catch(Exception ignored){return"";}}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void reset(){profileKey="";resetSession();}
    private static void resetSession(){hadBobber=false;goingDownInit=true;goingDownPost=false;rodWarned=false;ready=false;lastRodAt=0;lastSpawnAt=0;spawnBase=0;despawnAt=0;spawnChatAt=0;entitySeenAt=0;interactions=0;possibleEntity=null;confirmedEntity=null;CANDIDATES.clear();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goldenfish").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Golden Fish session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goldfin").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("level",IntegerArgumentType.integer(0,10)).executes(c->{cfg.goldenFishGoldfinLevel=IntegerArgumentType.getInteger(c,"level");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("warning").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,60)).executes(c->{cfg.goldenFishRodWarningSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){State state=state();local(state==null?"Golden Fish helper is inactive.":state.active?"Golden Fish active, "+state.interactions+"/3 interactions, "+time(state.despawnMillis)+" remaining.":state.spawnMillis<0?"Spawn window unknown; cast a lava rod.":state.available?"Golden Fish available for "+time(state.availableFor)+", chance "+String.format(Locale.ROOT,"%.1f%%",state.chance*100)+".":"Golden Fish can spawn in "+time(state.spawnMillis)+".");return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"hud"->cfg.goldenFishHud=value;case"label"->cfg.goldenFishWorldLabel=value;case"box"->cfg.goldenFishWorldBox=value;case"highlight"->cfg.goldenFishHighlightReady=value;case"spawnalert"->cfg.goldenFishSpawnTitle=value;case"rodwarning"->cfg.goldenFishRodWarning=value;default->{local("Option must be hud, label, box, highlight, spawnalert, or rodwarning.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Golden Fish] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
