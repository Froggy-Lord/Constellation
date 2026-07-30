package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/mountaintop/SunGeckoHelper.kt, TimiteHelper.kt, TimiteTracker.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/mountaintop/EnigmaRoseFlowerpot.kt, UbikReminder.kt, UbikQuickClose.kt
public final class AndromedaMountaintop {
    public record HudRow(String label,String value,int color){}
    private record Timite(BlockPos pos,long started){}
    private static final Set<String> AREAS=Set.of("Continuum","The Mountaintop","Trial Grounds","Time-Torn Isles","Wizardman Bureau","Wizard Brawl","Walk of Fame","Time Chamber");
    private static final Set<String> TIMITE_ITEMS=Set.of("ANTI_SENTIENT_PICKAXE","EON_PICKAXE","CHRONO_PICKAXE","TIME_GUN");
    private static final List<String> MODIFIERS=List.of("Revival","Combo Manic","Time Sliced","Buffantics","Collective","Brand New Dance","Culmination");
    private static final Map<String,Integer> MODIFIER_SLOTS=Map.of("Revival",19,"Combo Manic",20,"Time Sliced",21,"Buffantics",22,"Collective",23,"Brand New Dance",24,"Culmination",25);
    private static final Pattern COMBO=Pattern.compile("x(\\d+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALTH=Pattern.compile("Sun Gecko.*?([\\d,]+)\\s*/\\s*([\\d,]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern UBIK=Pattern.compile("SPLIT! You need to wait (.+) before you can play again\\.",Pattern.CASE_INSENSITIVE);
    private static final AABB ROSE_AREA=new AABB(25,165,90,52,185,120);
    private static final BlockPos ROSE_DROP=new BlockPos(40,161,116);
    private static final Map<BlockPos,Timite> TIMITES=new LinkedHashMap<>();
    private static final Map<String,Integer> INVENTORY=new HashMap<>();
    private static final Map<String,Long> SESSION=new LinkedHashMap<>();
    private static final Set<String> modifiers=new LinkedHashSet<>();
    private static AndromedaConfig cfg;private static boolean initialized,inventoryReady,scanningModifiers,firstPhase=true,doubleShot,ubikInside;
    private static long modifierScanAt,lastComboHit,evolutionStarted;private static int combo=1,comboHits,totalHits=10,health=250,maxHealth=250;private static BlockPos currentPos;private static BlockState currentState;private static Object levelIdentity;
    private AndromedaMountaintop(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(cfg.mountainTimiteTotals==null)cfg.mountainTimiteTotals=new LinkedHashMap<>();if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"andromeda-mountaintop",AndromedaMountaintop::tick);
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{observeTimeGun(player.getItemInHand(hand),hit.getBlockPos(),level.getBlockState(hit.getBlockPos()));return InteractionResult.PASS;});
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{onMessage(message,overlay);return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        if(!active()||!inMountain()){inventoryReady=false;TIMITES.clear();ubikInside=false;return;}
        if(System.currentTimeMillis()-modifierScanAt>3000)scanningModifiers=false;
        scanSun(mc);scanModifierMenu(mc);scanInventory(mc);scanUbik(mc);
        if(mc.level.getGameTime()%20==0)scanTimite(mc);
        if(cfg.mountainUbikReminder&&cfg.mountainUbikReadyAt>0&&cfg.mountainUbikReadyAt<=System.currentTimeMillis()){cfg.mountainUbikReadyAt=0;save();if(mc.player!=null){if(cfg.mountainUbikChat)local("Ubik's Cube is ready.");if(cfg.mountainUbikSound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.9f,1.2f);}}
    }
    private static void onMessage(Component message,boolean overlay){
        if(!active())return;String clean=clean(message.getString());if(overlay){if(inMountain()&&cfg.mountainSunGecko)parseCombo(clean);return;}
        Matcher ubik=UBIK.matcher(clean);if(cfg.mountainUbikReminder&&ubik.matches()){cfg.mountainUbikReadyAt=System.currentTimeMillis()+parseDuration(ubik.group(1))*1000L;save();}
        if(clean.contains("ACTIVE MODIFIERS!")){modifiers.clear();scanningModifiers=true;modifierScanAt=System.currentTimeMillis();}
        else if(scanningModifiers){for(String modifier:MODIFIERS)if(clean.contains(modifier))modifiers.add(modifier);}
    }
    private static void parseCombo(String text){
        Matcher matcher=COMBO.matcher(text);if(!matcher.find())return;int old=comboHits;combo=number(matcher.group(1));comboHits=count(text,'\u2B1B');totalHits=10-(modifiers.contains("Culmination")?1:0);if(modifiers.contains("Time Sliced")&&scoreboardBigDamage()>150)totalHits--;if(comboHits==9&&totalHits==8)comboHits=1;if(comboHits!=old)lastComboHit=System.currentTimeMillis();
    }
    private static void scanSun(Minecraft mc){
        if(!cfg.mountainSunGecko||!inTimeChamber()||mc.level==null)return;LivingEntity best=null;boolean foundHealth=false;
        for(Entity entity:mc.level.entitiesForRendering()){String name=clean(entity.getName().getString());Matcher parsed=HEALTH.matcher(name);boolean healthLabel=parsed.find();if(healthLabel){foundHealth=true;int next=number(parsed.group(1).replace(",","")),nextMax=number(parsed.group(2).replace(",",""));if(next>health){firstPhase=false;modifiers.add("Revival");}health=next;maxHealth=nextMax;}if(entity instanceof LivingEntity living&&living.isAlive()&&name.contains("Sun Gecko")&&!name.contains("?")&&!healthLabel&&(best==null||living.distanceToSqr(mc.player)<best.distanceToSqr(mc.player)))best=living;}
        if(!foundHealth&&best!=null){int next=Math.round(best.getHealth()),nextMax=Math.round(best.getMaxHealth());if(next>health){firstPhase=false;modifiers.add("Revival");}health=next;maxHealth=nextMax;}
        for(String line:ConstellationClient.loc().getSidebarLines()){Matcher parsed=HEALTH.matcher(clean(line));if(parsed.find()){int next=number(parsed.group(1).replace(",","")),nextMax=number(parsed.group(2).replace(",",""));if(next>health){firstPhase=false;modifiers.add("Revival");}health=next;maxHealth=nextMax;}}
    }
    private static void scanModifierMenu(Minecraft mc){
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!clean(screen.getTitle().getString()).equals("Modifiers"))return;modifiers.clear();
        for(var entry:MODIFIER_SLOTS.entrySet())if(screen.getMenu().slots.size()>entry.getValue()&&screen.getMenu().getSlot(entry.getValue()).getItem().is(Blocks.STAINED_GLASS_PANE.lime().asItem()))modifiers.add(entry.getKey());
    }
    private static void observeTimeGun(ItemStack held,BlockPos pos,BlockState state){
        if(!active()||!inMountain()||!cfg.mountainTimite||!cfg.mountainTimiteEvolution||!itemId(held).equals("TIME_GUN")||!isShootable(state))return;long now=System.currentTimeMillis();
        doubleShot=pos.equals(currentPos)&&currentState!=null&&!currentState.equals(state);if(doubleShot)TIMITES.put(pos.immutable(),new Timite(pos.immutable(),now));currentPos=pos.immutable();currentState=state;evolutionStarted=now;
    }
    private static void scanTimite(Minecraft mc){
        if(!cfg.mountainTimite||!cfg.mountainTimiteExpiry||mc.level==null||mc.player==null)return;int range=Math.clamp(cfg.mountainTimiteScanRange,5,25);BlockPos center=mc.player.blockPosition();long now=System.currentTimeMillis();
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-range,-range,-range),center.offset(range,range,range))){BlockState state=mc.level.getBlockState(pos);if(isTracked(state))TIMITES.putIfAbsent(pos.immutable(),new Timite(pos.immutable(),now));}
        TIMITES.entrySet().removeIf(entry->{BlockState state=mc.level.getBlockState(entry.getKey());return state.isAir()||state.is(Blocks.STAINED_GLASS_PANE.lightBlue())||now-entry.getValue().started>Math.clamp(cfg.mountainTimiteExpirySeconds,10,60)*1000L+2000;});
    }
    private static void scanInventory(Minecraft mc){
        if(mc.player==null)return;Map<String,Integer> current=new HashMap<>();for(ItemStack stack:mc.player.getInventory()){String id=itemId(stack);if(id.equals("TIMITE")||id.equals("YOUNGITE")||id.equals("OBSOLITE"))current.merge(id,stack.getCount(),Integer::sum);}
        if(inventoryReady)for(var entry:current.entrySet()){int delta=entry.getValue()-INVENTORY.getOrDefault(entry.getKey(),0);if(delta>0){SESSION.merge(entry.getKey(),(long)delta,Long::sum);cfg.mountainTimiteTotals.merge(entry.getKey(),(long)delta,Long::sum);save();}}
        INVENTORY.clear();INVENTORY.putAll(current);inventoryReady=true;
    }
    private static void scanUbik(Minecraft mc){boolean inside=mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&clean(screen.getTitle().getString()).equals("Split or Steal");if(cfg.mountainUbikReminder&&inside&&!ubikInside){cfg.mountainUbikReadyAt=System.currentTimeMillis()+7_200_000L;save();}ubikInside=inside;}
    public static boolean shouldQuickClose(AbstractContainerScreen<?> screen){return active()&&inMountain()&&cfg.mountainUbikQuickClose&&clean(screen.getTitle().getString()).equals("Split or Steal")&&screen.getMenu().slots.size()>4&&!screen.getMenu().getSlot(4).getItem().is(Items.CLOCK);}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!inMountain())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;drawSun(ctx,mc);drawTimite(ctx);drawRose(ctx,mc);
    }
    private static void drawSun(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.mountainSunGecko||!inTimeChamber()||mc.level==null)return;double range=Math.clamp(cfg.mountainSunGeckoRange,10,150),sq=range*range;
        for(Entity entity:mc.level.entitiesForRendering()){String name=clean(entity.getName().getString());if(!name.contains("Sun Gecko")||entity.distanceToSqr(mc.player)>sq)continue;boolean clone=name.contains("?");if(clone&&!cfg.mountainSunGeckoClones||!clone&&!cfg.mountainSunGeckoReal)continue;int color=clone?cfg.mountainSunGeckoCloneColor:cfg.mountainSunGeckoRealColor;if(cfg.mountainSunGeckoBox)ctx.highlight(entity.getBoundingBox().inflate(.1),color,cfg.mountainSunGeckoThroughWalls);if(cfg.mountainSunGeckoLabel)ctx.label(entity.position().add(0,entity.getBbHeight()+.4,0),clone?"Sun Gecko clone":"Sun Gecko",color,cfg.mountainSunGeckoThroughWalls);}
    }
    private static void drawTimite(WorldRenderer.Ctx ctx){
        if(!cfg.mountainTimite||!cfg.mountainTimiteExpiry)return;long now=System.currentTimeMillis(),expiry=Math.clamp(cfg.mountainTimiteExpirySeconds,10,60)*1000L,warning=Math.clamp(cfg.mountainTimiteWarningSeconds,1,15)*1000L;
        for(Timite timite:TIMITES.values()){long left=timite.started+expiry-now;if(left<=0||left>warning)continue;Vec3 center=Vec3.atCenterOf(timite.pos);if(cfg.mountainTimiteExpiryBox)ctx.highlight(new AABB(timite.pos),cfg.mountainTimiteColor,cfg.mountainTimiteThroughWalls);if(cfg.mountainTimiteExpiryLabel)ctx.label(center.add(0,1,0),formatMillis(left),cfg.mountainTimiteColor,cfg.mountainTimiteThroughWalls);}
    }
    private static void drawRose(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.mountainRoseFlowerpot||!ROSE_AREA.contains(mc.player.position()))return;Vec3 center=Vec3.atCenterOf(ROSE_DROP);if(cfg.mountainRoseBox)ctx.highlight(new AABB(ROSE_DROP),cfg.mountainRoseColor,cfg.mountainRoseThroughWalls);if(cfg.mountainRoseBeam)ctx.beam(center.x,center.y,center.z,cfg.mountainRoseColor,12,cfg.mountainRoseThroughWalls);if(cfg.mountainRoseLabel)ctx.label(center.add(0,1,0),"Drop",cfg.mountainRoseColor,cfg.mountainRoseThroughWalls);
    }

    public static List<HudRow> hudRows(String mode){
        if(!active()||!inMountain())return List.of();return switch(mode){case"sun"->sunRows();case"timite"->timiteRows();case"ubik"->ubikRows();default->List.of();};
    }
    private static List<HudRow> sunRows(){if(!cfg.mountainSunGecko||!inTimeChamber())return List.of();List<HudRow> rows=new ArrayList<>();int shownHealth=health+(modifiers.contains("Revival")&&firstPhase?maxHealth:0),shownMax=maxHealth*(modifiers.contains("Revival")?2:1);rows.add(new HudRow("Health",shownHealth+"/"+shownMax,shownHealth<=shownMax/2?0xFFFFFF55:0xFF55FF55));rows.add(new HudRow("Combo",comboHits+"/"+totalHits+" x"+combo,0xFFFFFF55));long expiry=Math.clamp(cfg.mountainSunGeckoComboMillis,3000,7000)+(modifiers.contains("Collective")?modifiers.size()*200L:0),left=Math.max(0,lastComboHit+expiry-System.currentTimeMillis());rows.add(new HudRow("Combo timer",formatMillis(left),left<1000?0xFFFF5555:0xFF55FF55));if(cfg.mountainSunGeckoModifiers)for(String modifier:modifiers)rows.add(new HudRow("Modifier",modifier,0xFFFFAA00));return rows;}
    private static List<HudRow> timiteRows(){if(!cfg.mountainTimiteTracker||cfg.mountainTimiteTrackerHolding&&!holdingTimiteTool())return List.of();List<HudRow> rows=new ArrayList<>();Map<String,Long> values=cfg.mountainTimiteTotals;if(cfg.mountainTimiteTrackerItems)for(String id:List.of("TIMITE","YOUNGITE","OBSOLITE"))rows.add(new HudRow(title(id),number(values.getOrDefault(id,0L)),0xFF55FFFF));if(cfg.mountainTimiteTrackerTime)rows.add(new HudRow("Time",formatDuration(values.getOrDefault("TIMITE",0L)*2),0xFF55FF55));if(cfg.mountainTimiteTrackerProfit)rows.add(new HudRow("Profit",number(values.getOrDefault("TIMITE",0L)*100+values.getOrDefault("YOUNGITE",0L)*50+values.getOrDefault("OBSOLITE",0L)*200)+" Motes",0xFFFF55FF));if(cfg.mountainTimiteTrackerCraftable){long craft=Math.min(values.getOrDefault("YOUNGITE",0L)/32,Math.min(values.getOrDefault("TIMITE",0L)/32,values.getOrDefault("OBSOLITE",0L)/16));rows.add(new HudRow("Highlite",craft+" ("+number(craft*25_000)+" Motes)",0xFFFFAA00));}return rows;}
    private static List<HudRow> ubikRows(){if(!cfg.mountainUbikHud||cfg.mountainUbikReadyAt<0)return List.of();boolean ready=cfg.mountainUbikReadyAt==0||cfg.mountainUbikReadyAt<=System.currentTimeMillis();if(cfg.mountainUbikOnlyReady&&!ready)return List.of();return List.of(new HudRow(ready?"Status":"Ready in",ready?"Ready":formatDuration((cfg.mountainUbikReadyAt-System.currentTimeMillis()+999)/1000),ready?0xFF55FF55:0xFFFFFF55));}
    public static String evolutionHud(){if(!active()||!inMountain()||!cfg.mountainTimite||!cfg.mountainTimiteEvolution||evolutionStarted==0||!holdingTimeGun())return null;long duration=doubleShot?1800:2000,left=evolutionStarted+duration-System.currentTimeMillis();return left>0?formatMillis(left):null;}
    private static boolean holdingTimiteTool(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&TIMITE_ITEMS.contains(itemId(mc.player.getMainHandItem()));}
    private static boolean holdingTimeGun(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&itemId(mc.player.getMainHandItem()).equals("TIME_GUN");}
    private static boolean isShootable(BlockState state){return state.is(Blocks.STAINED_GLASS_PANE.blue())||state.is(Blocks.STAINED_GLASS_PANE.lightBlue());}
    private static boolean isTracked(BlockState state){return state.is(Blocks.STAINED_GLASS_PANE.blue())||state.is(Blocks.STAINED_GLASS_PANE.cyan());}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean inMountain(){return AREAS.contains(AndromedaRiftCore.currentArea());}
    private static boolean inTimeChamber(){return AndromedaRiftCore.currentArea().equals("Time Chamber");}
    private static int scoreboardBigDamage(){for(String line:ConstellationClient.loc().getSidebarLines()){String clean=clean(line);if(clean.startsWith("Big damage in:"))return (int)parseDuration(clean.substring(clean.indexOf(':')+1));}return 0;}
    private static String itemId(ItemStack stack){if(stack==null||stack.isEmpty())return"";CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static long parseDuration(String text){long seconds=0;Matcher matcher=Pattern.compile("(\\d+)\\s*([hms])",Pattern.CASE_INSENSITIVE).matcher(text);while(matcher.find()){long value=Long.parseLong(matcher.group(1));seconds+=switch(matcher.group(2).toLowerCase(Locale.ROOT)){case"h"->value*3600;case"m"->value*60;default->value;};}return seconds;}
    private static int count(String text,char value){int out=0;for(int i=0;i<text.length();i++)if(text.charAt(i)==value)out++;return out;}
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static String number(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String title(String id){return id.substring(0,1)+id.substring(1).toLowerCase(Locale.ROOT);}
    private static String formatMillis(long millis){return String.format(Locale.ROOT,"%.2fs",Math.max(0,millis)/1000.0);}
    private static String formatDuration(long seconds){seconds=Math.max(0,seconds);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){TIMITES.clear();INVENTORY.clear();SESSION.clear();modifiers.clear();inventoryReady=scanningModifiers=firstPhase=doubleShot=ubikInside=false;firstPhase=true;modifierScanAt=lastComboHit=evolutionStarted=0;combo=1;comboHits=0;totalHits=10;health=maxHealth=250;currentPos=null;currentState=null;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Mountaintop] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mountaintop").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetTransient();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resettracker").executes(c->{cfg.mountainTimiteTotals.clear();save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->numberOption(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Sun Gecko "+(cfg.mountainSunGecko?"on":"off")+", Timite "+(cfg.mountainTimite?"on":"off")+", tracked "+TIMITES.size()+", tracker "+(cfg.mountainTimiteTracker?"on":"off")+", Ubik "+ubikStatus()+".");return 1;}
    private static String ubikStatus(){return cfg.mountainUbikReadyAt<0?"unknown":cfg.mountainUbikReadyAt==0?"ready":formatDuration((cfg.mountainUbikReadyAt-System.currentTimeMillis()+999)/1000);}
    private static int numberOption(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"sunrange"->cfg.mountainSunGeckoRange=Math.clamp(value,10,150);case"combo"->cfg.mountainSunGeckoComboMillis=Math.clamp(value,3000,7000);case"scanrange"->cfg.mountainTimiteScanRange=Math.clamp(value,5,25);case"expiry"->cfg.mountainTimiteExpirySeconds=Math.clamp(value,10,60);case"warning"->cfg.mountainTimiteWarningSeconds=Math.clamp(value,1,15);default->{local("Unknown Mountaintop number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"sun"->cfg.mountainSunGecko=value;case"modifiers"->cfg.mountainSunGeckoModifiers=value;case"real"->cfg.mountainSunGeckoReal=value;case"clones"->cfg.mountainSunGeckoClones=value;case"timite"->cfg.mountainTimite=value;case"evolution"->cfg.mountainTimiteEvolution=value;case"expiry"->cfg.mountainTimiteExpiry=value;case"tracker"->cfg.mountainTimiteTracker=value;case"holding"->cfg.mountainTimiteTrackerHolding=value;case"flowerpot"->cfg.mountainRoseFlowerpot=value;case"ubikreminder"->cfg.mountainUbikReminder=value;case"ubikhud"->cfg.mountainUbikHud=value;case"ubikready"->cfg.mountainUbikOnlyReady=value;case"quickclose"->cfg.mountainUbikQuickClose=value;default->{local("Unknown Mountaintop option.");return 0;}}save();return status();}
}
