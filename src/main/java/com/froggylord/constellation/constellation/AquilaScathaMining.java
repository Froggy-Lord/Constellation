package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from NoFrills (GPL-3.0): features/mining/ScathaMining.java
// ported from SkyOcean (MIT): features/mining/scathas/Scathas.kt
// ported from SkyOcean (MIT): features/mining/scathas/SpawnedWorm.kt
public final class AquilaScathaMining {
    public enum WormType { WORM, SCATHA }
    public record State(long cooldownMillis,WormType current,long sessionSpawns,long sessionWorms,long sessionScathas,long totalSpawns,long totalWorms,long totalScathas,long dryStreak,long petDrops,Map<String,Long> rarities,long sessionMillis) {}
    private static final class Data {long spawns,worms,scathas,dryStreak,petDrops;Map<String,Long> rarities=new HashMap<>();}
    private static final class Store {Map<String,Data> profiles=new HashMap<>();}
    private static final Pattern WORM=Pattern.compile("^\\[Lv5] Worm .*\\u2764$");
    private static final Pattern SCATHA=Pattern.compile("^\\[Lv10] Scatha .*\\u2764$");
    private static final Pattern PET=Pattern.compile("^PET DROP! Scatha(?: \\(\\+(?<mf>\\d+).? Magic Find\\))?$",Pattern.CASE_INSENSITIVE);
    private static final String SPAWN="You hear the sound of something approaching...";
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("constellation-scatha-mining.json");
    private static final Data SESSION_TOTAL=new Data();
    private static final Data SESSION=new Data();
    private static final Map<String,Data> PROFILES=new HashMap<>();
    private static AquilaConfig cfg;
    private static ArmorStand current;
    private static WormType currentType;
    private static String profileKey="";
    private static boolean initialized,dirty,readyAlerted,wasPersistent;
    private static long spawnMessageAt,cooldownUntil,sessionStartedAt,lastSave,lastAliveSound;

    private AquilaScathaMining() {}

    public static void init(AquilaConfig config) {
        cfg=config;
        wasPersistent=cfg.scathaPersistProfiles;
        if(wasPersistent)load();
        if(initialized)return;
        initialized=true;
        ConstellationClient.tick().every(1,"aquila-scatha",AquilaScathaMining::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay||onChat(message));
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetConnection());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetConnection());
    }

    private static void tick() {
        if(cfg==null)return;
        if(cfg.scathaPersistProfiles!=wasPersistent){wasPersistent=cfg.scathaPersistProfiles;if(wasPersistent)load();}
        String profile=profile();
        if(!profile.equals(profileKey)){flush(true);profileKey=profile;resetSession();}
        if(!active()){current=null;currentType=null;spawnMessageAt=0;cooldownUntil=0;readyAlerted=false;flush(false);return;}
        long now=System.currentTimeMillis();
        if(cooldownUntil>0&&now>=cooldownUntil&&!readyAlerted){readyAlerted=true;cooldownReady();}
        if(current!=null&&(!current.isAlive()||current.isRemoved())){current=null;currentType=null;}
        findWorm(now);
        if(current!=null&&cfg.scathaAliveSound&&now-lastAliveSound>=250){
            Minecraft mc=Minecraft.getInstance();
            if(mc.player!=null)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.25f,1.5f);
            lastAliveSound=now;
        }
        flush(false);
    }

    private static void findWorm(long now) {
        if(current!=null||spawnMessageAt==0||now-spawnMessageAt>Math.clamp(cfg.scathaPairingSeconds,2,20)*1000L)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return;
        double range2=Math.pow(Math.clamp(cfg.scathaScanRange,4,64),2);
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof ArmorStand stand)||!stand.hasCustomName()||stand.distanceToSqr(mc.player)>range2)continue;
            WormType type=type(clean(stand.getName().getString()));
            if(type==null||!local(stand,mc.player.blockPosition().getX(),mc.player.blockPosition().getY(),mc.player.blockPosition().getZ()))continue;
            current=stand;currentType=type;spawnMessageAt=0;recordSpawn(type);spawnAlert(type);return;
        }
    }

    private static boolean local(ArmorStand stand,int x,int y,int z){
        int horizontal=Math.clamp(cfg.scathaLocalAxisRange,1,8),vertical=Math.clamp(cfg.scathaLocalVerticalRange,1,12);
        return Math.abs(stand.getBlockY()-y)<=vertical&&(Math.abs(stand.getBlockX()-x)<=horizontal||Math.abs(stand.getBlockZ()-z)<=horizontal);
    }

    private static boolean onChat(Component message) {
        if(!configured()||!ConstellationClient.loc().onHypixel())return true;
        String clean=clean(message.getString());
        if(active()&&clean.equals(SPAWN)){
            long now=System.currentTimeMillis();spawnMessageAt=now;cooldownUntil=now+Math.clamp(cfg.scathaCooldownSeconds,20,60)*1000L;readyAlerted=false;current=null;currentType=null;
            return true;
        }
        Matcher pet=PET.matcher(clean);
        if(!pet.matches())return true;
        return !petDrop(message,pet.group("mf"));
    }

    private static void recordSpawn(WormType type) {
        if(sessionStartedAt==0)sessionStartedAt=System.currentTimeMillis();
        if(cfg.scathaCounter){addSpawn(SESSION,type);addSpawn(SESSION_TOTAL,type);if(cfg.scathaPersistProfiles&&!profileKey.isBlank()){addSpawn(data(),type);dirty=true;}}
    }

    private static void addSpawn(Data data,WormType type){data.spawns++;data.dryStreak++;if(type==WormType.SCATHA)data.scathas++;else data.worms++;}

    private static boolean petDrop(Component component,String mf) {
        String rarity=rarity(component);
        if(cfg.scathaCounter){addPet(SESSION,rarity);addPet(SESSION_TOTAL,rarity);if(cfg.scathaPersistProfiles&&!profileKey.isBlank()){addPet(data(),rarity);dirty=true;}}
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return false;
        boolean replace=cfg.scathaReplacePetMessage&&!rarity.equals("UNKNOWN");
        if(replace){
            String suffix=mf==null?"":" (+"+mf+" Magic Find)";
            mc.player.sendSystemMessage(Component.literal("PET DROP! "+rarity+" Scatha"+suffix).withColor(rarityColor(rarity)));
        }
        if(cfg.scathaPetDropAlert){
            if(cfg.scathaPetDropChat)local("Scatha Pet drop: "+rarity+(mf==null?".":" with "+mf+" Magic Find."));
            if(cfg.scathaPetDropTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Scatha Pet!").withColor(cfg.scathaPetColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(rarity).withColor(rarityColor(rarity)));}
            if(cfg.scathaPetDropSound)mc.player.playSound(SoundEvents.ANVIL_LAND,1f,2f);
        }
        flush(true);
        return replace;
    }

    private static void addPet(Data data,String rarity){data.petDrops++;data.dryStreak=0;data.rarities.merge(rarity,1L,Long::sum);}

    private static void spawnAlert(WormType type) {
        if(!cfg.scathaAlert)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        boolean scatha=type==WormType.SCATHA;
        if(cfg.scathaSpawnChat)local((scatha?"Scatha":"Worm")+" spawned.");
        if(scatha?cfg.scathaScathaTitle:cfg.scathaWormTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(scatha?"Scatha":"Worm").withColor((scatha?cfg.scathaScathaColor:cfg.scathaWormColor)&0xFFFFFF));if(scatha)mc.gui.hud.setSubtitle(Component.literal("Rare spawn"));}
        if(cfg.scathaSpawnSound)mc.player.playSound(scatha?SoundEvents.NOTE_BLOCK_PLING.value():SoundEvents.NOTE_BLOCK_BASS.value(),1f,scatha?1f:.5f);
    }

    private static void cooldownReady() {
        if(!cfg.scathaCooldownAlert)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.scathaCooldownChat)local("Worm spawn cooldown ended.");
        if(cfg.scathaCooldownTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Scatha cooldown ready").withColor(cfg.scathaReadyColor&0xFFFFFF));}
        if(cfg.scathaCooldownSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),1f,2f);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if(!active()||!cfg.scathaWorldHighlight||current==null||currentType==null||!current.isAlive())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        int color=currentType==WormType.SCATHA?cfg.scathaScathaColor:cfg.scathaWormColor;
        if(cfg.scathaWorldBox)ctx.highlight(current.getBoundingBox().inflate(.12),color,cfg.scathaWorldThroughWalls);
        if(cfg.scathaWorldBeam)ctx.beam(current.getX(),current.getY(),current.getZ(),color,Math.clamp(cfg.scathaBeamHeight,4,32),cfg.scathaWorldThroughWalls);
        if(cfg.scathaWorldLine)ctx.line(mc.player.getEyePosition(),current.position().add(0,current.getBbHeight()/2,0),color,cfg.scathaWorldThroughWalls);
        if(cfg.scathaWorldLabel){String text=currentType==WormType.SCATHA?"Scatha":"Worm";if(cfg.scathaWorldDistance)text+=" "+Math.round(mc.player.distanceTo(current))+"m";ctx.label(current.position().add(0,current.getBbHeight()+.5,0),text,color,cfg.scathaWorldThroughWalls);}
    }

    public static State state() {
        if(!configured())return null;
        Data total=cfg.scathaPersistProfiles&&!profileKey.isBlank()?data():SESSION_TOTAL;
        long remaining=Math.max(0,cooldownUntil-System.currentTimeMillis());
        long sessionMillis=sessionStartedAt==0?0:System.currentTimeMillis()-sessionStartedAt;
        return new State(remaining,currentType,SESSION.spawns,SESSION.worms,SESSION.scathas,total.spawns,total.worms,total.scathas,total.dryStreak,total.petDrops,Map.copyOf(total.rarities),sessionMillis);
    }

    public static boolean visible(){State state=state();return active()&&cfg.scathaHud&&state!=null&&(state.cooldownMillis>0||state.current!=null||state.sessionSpawns>0||state.totalSpawns>0);}
    public static AquilaConfig config(){return cfg;}
    private static boolean active(){return configured()&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()==SkyblockArea.CRYSTAL_HOLLOWS;}
    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.scathaMiningSuite&&(cfg.scathaAlert||cfg.scathaCounter);}
    private static WormType type(String name){if(SCATHA.matcher(name).matches())return WormType.SCATHA;if(WORM.matcher(name).matches())return WormType.WORM;return null;}
    private static String rarity(Component component){final String[] out={"UNKNOWN"};component.visit((style,text)->{if(!clean(text).equalsIgnoreCase("Scatha")||style.getColor()==null)return java.util.Optional.empty();out[0]=switch(style.getColor().getValue()){case 0xFFAA00->"LEGENDARY";case 0xAA00AA->"EPIC";case 0x5555FF->"RARE";case 0x55FF55->"UNCOMMON";case 0xFFFFFF->"COMMON";default->"UNKNOWN";};return java.util.Optional.empty();},Style.EMPTY);return out[0];}
    private static int rarityColor(String rarity){return switch(rarity){case"LEGENDARY"->0xFFAA00;case"EPIC"->0xAA00AA;case"RARE"->0x5555FF;case"UNCOMMON"->0x55FF55;case"COMMON"->0xFFFFFF;default->0xAAAAAA;};}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null?"":value.trim();}
    private static Data data(){return PROFILES.computeIfAbsent(profileKey.isBlank()?"unknown":profileKey,k->new Data());}
    private static void resetSession(){clear(SESSION);sessionStartedAt=0;current=null;currentType=null;spawnMessageAt=0;cooldownUntil=0;readyAlerted=false;}
    private static void resetConnection(){flush(true);profileKey="";resetSession();}
    private static void clear(Data data){data.spawns=data.worms=data.scathas=data.dryStreak=data.petDrops=0;data.rarities.clear();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scatha").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->reset(false)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearprofile").executes(c->reset(true)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cooldown").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(20,60)).executes(c->{cfg.scathaCooldownSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(4,64)).executes(c->{cfg.scathaScanRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int status(){State s=state();if(s==null){local("Scatha mining is disabled.");return 1;}local("Session "+s.sessionSpawns+" spawns, total "+s.totalSpawns+", Scathas "+s.totalScathas+", dry streak "+s.dryStreak+", pet drops "+s.petDrops+".");return 1;}
    private static int reset(boolean profile){if(profile&&cfg.scathaPersistProfiles&&!profileKey.isBlank()){PROFILES.remove(profileKey);dirty=true;flush(true);local("Current profile Scatha history cleared.");}else{resetSession();local("Scatha mining session reset.");}return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.scathaMiningSuite=v;case"hud"->cfg.scathaHud=v;case"spawnchat"->cfg.scathaSpawnChat=v;case"worms"->cfg.scathaWormTitle=v;case"scathas"->cfg.scathaScathaTitle=v;case"spawnsound"->cfg.scathaSpawnSound=v;case"alivesound"->cfg.scathaAliveSound=v;case"cooldown"->cfg.scathaCooldownAlert=v;case"pet"->cfg.scathaPetDropAlert=v;case"replacepet"->cfg.scathaReplacePetMessage=v;case"highlight"->cfg.scathaWorldHighlight=v;case"box"->cfg.scathaWorldBox=v;case"label"->cfg.scathaWorldLabel=v;case"beam"->cfg.scathaWorldBeam=v;case"line"->cfg.scathaWorldLine=v;case"walls"->cfg.scathaWorldThroughWalls=v;case"persist"->cfg.scathaPersistProfiles=v;default->{local("Unknown Scatha option.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a76[Scatha] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
    private static void load(){try{if(!Files.exists(FILE))return;Store store=GSON.fromJson(Files.readString(FILE,StandardCharsets.UTF_8),Store.class);if(store==null||store.profiles==null)return;PROFILES.clear();for(var entry:store.profiles.entrySet()){Data data=entry.getValue();if(data==null)continue;if(data.rarities==null)data.rarities=new HashMap<>();PROFILES.put(entry.getKey(),data);}}catch(Exception e){ConstellationClient.LOGGER.warn("could not load Scatha mining history",e);}}
    private static void flush(boolean force){if(!dirty||!cfg.scathaPersistProfiles||!force&&System.currentTimeMillis()-lastSave<1000)return;lastSave=System.currentTimeMillis();try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");Store store=new Store();store.profiles.putAll(PROFILES);Files.writeString(temp,GSON.toJson(store),StandardCharsets.UTF_8);try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception ignored){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}dirty=false;}catch(Exception e){ConstellationClient.LOGGER.warn("could not save Scatha mining history",e);}}
}
