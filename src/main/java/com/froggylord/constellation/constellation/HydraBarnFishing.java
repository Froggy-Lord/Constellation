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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// ported from Feesh (Apache-2.0): features/overlays/BarnFishingTimer.kt
public final class HydraBarnFishing {
    public record State(int count,long elapsedMillis,int threshold,boolean countWarning,boolean timeWarning,String area) {}
    private static final String PERSONAL_CAP="There is not enough space for another Sea Creature! Kill some to make space for new ones!";
    private static final Set<String> EXTRA_NAMES=Set.of("Mithril Grubber","Jawbus Follower");
    private static HydraConfig cfg;
    private static boolean initialized,countLatched,timeLatched,wasEnabled;
    private static int count,scanTicks,emptyScans;
    private static long startedAt,lastAlertAt;
    private HydraBarnFishing() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-barn-fishing",HydraBarnFishing::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void tick(){boolean enabled=enabled();if(!enabled){if(wasEnabled)resetSession();wasEnabled=false;return;}wasEnabled=true;if(++scanTicks<Math.clamp(cfg.barnTimerScanTicks,1,100))return;scanTicks=0;scan();alerts();}

    // ported from Feesh (Apache-2.0): features/overlays/BarnFishingTimer.kt
    private static void scan(){Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;Set<UUID> seen=new HashSet<>();int found=0;List<String> names=HydraSeaCreatureTracker.knownCreatureNames();for(var entity:mc.level.entitiesForRendering()){if(!(entity instanceof ArmorStand stand)||stand.getCustomName()==null||!seen.add(stand.getUUID()))continue;String name=clean(stand.getCustomName().getString());if(!name.contains("[Lv")||!name.contains("\u2764")||name.contains("Vanquisher"))continue;boolean known=names.stream().anyMatch(name::contains)||EXTRA_NAMES.stream().anyMatch(name::contains);if(!known)continue;found+=name.contains("Rider of the Deep")?2:1;}if(found==0&&count>0){emptyScans++;if(emptyScans<Math.clamp(cfg.barnTimerEmptyConfirmScans,1,10))return;}else emptyScans=0;if(count==0&&found>0)startedAt=System.currentTimeMillis();if(found==0){startedAt=0;countLatched=false;timeLatched=false;}count=found;}

    // ported from Feesh (Apache-2.0): features/overlays/BarnFishingTimer.kt
    private static void alerts(){if(!alertContext())return;long now=System.currentTimeMillis();int threshold=threshold();long time=Math.clamp(cfg.barnTimerMinutes,1,60)*60_000L;if(cfg.barnTimerCountAlert&&count>=threshold&&!countLatched){if(alert("Kill sea creatures",threshold+"+ mobs"))countLatched=true;}else if(count<threshold)countLatched=false;if(cfg.barnTimerTimeAlert&&startedAt>0&&now-startedAt>=time&&!timeLatched){if(alert("Kill sea creatures",cfg.barnTimerMinutes+"+ minutes"))timeLatched=true;}else if(startedAt==0||now-startedAt<time)timeLatched=false;}

    private static void onChat(String message){if(enabled()&&cfg.barnTimerPersonalCapAlert&&PERSONAL_CAP.equals(message))alert("Kill sea creatures","Personal cap reached");}
    private static boolean alert(String title,String detail){Minecraft mc=Minecraft.getInstance();long now=System.currentTimeMillis();if(mc.player==null||now-lastAlertAt<Math.clamp(cfg.barnTimerAlertCooldownSeconds,0,60)*1000L)return false;lastAlertAt=now;if(cfg.barnTimerAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(title).withColor(cfg.barnTimerAlertColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(detail));}if(cfg.barnTimerAlertChat)mc.player.sendSystemMessage(Component.literal("\u00a7c[Barn Fishing] \u00a7f"+title+": "+detail));if(cfg.barnTimerAlertSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),0.9f,1.1f);return true;}

    public static State state(){if(!visible())return null;long elapsed=startedAt==0?0:System.currentTimeMillis()-startedAt;int threshold=threshold();return new State(count,elapsed,threshold,count>=threshold,startedAt>0&&elapsed>=Math.clamp(cfg.barnTimerMinutes,1,60)*60_000L,areaName());}
    public static boolean visible(){return enabled()&&count>0&&startedAt>0&&alertContext();}
    public static HydraConfig config(){return cfg;}
    public static String time(long millis){long seconds=Math.max(0,millis/1000);return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

    private static boolean alertContext(){return fishingArea()&&(!cfg.barnTimerRequireRod||hasRod())&&(!cfg.barnTimerIgnoreTrophyArmor||!trophyArmor());}
    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.barnTimer&&cfg.barnTimerSuite&&ConstellationClient.loc().onHypixel()&&fishingArea();}
    private static boolean fishingArea(){if(!ConstellationClient.loc().onHypixel())return false;return switch(ConstellationClient.loc().area()){case THE_RIFT,GARDEN,KUUDRA,CATACOMBS,MASTER_MODE,DUNGEON_HUB,THE_END,GLACITE_MINESHAFT->false;default->true;};}
    private static boolean hasRod(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(Items.FISHING_ROD))return true;return false;}
    // ported from Feesh (Apache-2.0): utils/PlayerUtils.kt
    private static boolean trophyArmor(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){ItemStack item=mc.player.getItemBySlot(slot);if(item.isEmpty())return false;String name=clean(item.getHoverName().getString()).toLowerCase(Locale.ROOT);if(!name.contains("hunter")&&!name.contains("froggles")&&!name.contains("red sweater"))return false;}return true;}
    private static int threshold(){return Math.clamp(switch(ConstellationClient.loc().area()){case HUB->cfg.barnTimerHubThreshold;case CRIMSON_ISLE->cfg.barnTimerCrimsonThreshold;case CRYSTAL_HOLLOWS->cfg.barnTimerCrystalHollowsThreshold;case GALATEA->cfg.barnTimerGalateaThreshold;default->cfg.barnTimerDefaultThreshold;},5,60);}
    private static String areaName(){String raw=ConstellationClient.loc().area().name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder out=new StringBuilder();for(String word:raw.split(" ")){if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.toString();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void reset(){wasEnabled=false;resetSession();}
    private static void resetSession(){count=0;scanTicks=0;emptyScans=0;startedAt=0;lastAlertAt=0;countLatched=false;timeLatched=false;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("barnfishing").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Stack timer reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("minutes").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(1,60)).executes(c->{cfg.barnTimerMinutes=IntegerArgumentType.getInteger(c,"minutes");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threshold").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("area",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(5,60)).executes(c->setThreshold(StringArgumentType.getString(c,"area"),IntegerArgumentType.getInteger(c,"count")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){State s=state();local(s==null?"No active sea-creature stack. Current "+areaName()+" threshold: "+threshold()+".":s.count()+" nearby, "+time(s.elapsedMillis())+" elapsed, threshold "+s.threshold()+".");return 1;}
    private static int setThreshold(String area,int value){switch(area.toLowerCase(Locale.ROOT)){case"hub"->cfg.barnTimerHubThreshold=value;case"crimson","crimsonisle"->cfg.barnTimerCrimsonThreshold=value;case"crystal","hollows","crystalhollows"->cfg.barnTimerCrystalHollowsThreshold=value;case"galatea"->cfg.barnTimerGalateaThreshold=value;case"default"->cfg.barnTimerDefaultThreshold=value;default->{local("Area must be hub, crimson, crystal, galatea, or default.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"hud"->cfg.barnTimerHud=value;case"cap"->cfg.barnTimerPersonalCapAlert=value;case"count"->cfg.barnTimerCountAlert=value;case"time"->cfg.barnTimerTimeAlert=value;case"title"->cfg.barnTimerAlertTitle=value;case"chat"->cfg.barnTimerAlertChat=value;case"sound"->cfg.barnTimerAlertSound=value;case"rod"->cfg.barnTimerRequireRod=value;case"trophy"->cfg.barnTimerIgnoreTrophyArmor=value;default->{local("Option must be hud, cap, count, time, title, chat, sound, rod, or trophy.");return 0;}}save();return status();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a73[Barn Fishing] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
