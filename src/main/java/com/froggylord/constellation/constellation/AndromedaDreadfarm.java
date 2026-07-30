package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/dreadfarm/RiftAgaricusCap.kt, VoltHighlighter.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/dreadfarm/RiftWiltedBerberisHelper.kt, WoodenButtonsHelper.kt
public final class AndromedaDreadfarm {
    private enum VoltState{NONE,FRIENDLY,HOSTILE,CHARGING}
    private record ButtonSpot(String name,Vec3 center,List<BlockPos> buttons){}
    private static final class Berberis{Vec3 current,previous;long last;boolean moving=true;double y;Berberis(Vec3 pos,long now){current=pos;last=now;}}
    private static final class Sequence{final Vec3 center;final int expected;final List<BlockPos> points=new ArrayList<>();int index;boolean valid,away;long lastParticle;Sequence(Vec3 center,int expected){this.center=center;this.expected=expected;}}
    private static final Pattern BUTTON_HIT=Pattern.compile("You have hit (\\d+)/56 of the wooden buttons!");
    private static final String VOLT_CHARGING="e1b1bfb536b6416b26a283d2d8ad4b1771bbe5b76896e2727d5e833bc894982d";
    private static final String VOLT_FRIENDLY="7aea6251e58e3d20552a035d424561eaee083feacd1e6cb3c2a5cf945ce6d76e";
    private static final String VOLT_HOSTILE="8f9ede970d40c5b245cbd651349ee166f695d250345f8f0e63fcb10cfb5a2b78";
    private static final List<Vec3> FIELD_CENTERS=List.of(new Vec3(-29,71,-179),new Vec3(-65,72,-184),new Vec3(-79,72,-162),new Vec3(-69,71,-134),new Vec3(-33,70,-140),new Vec3(-49,69,-123));
    private static final int[] FIELD_COUNTS={12,15,19,21,36,8};
    private static final List<ButtonSpot> BUTTON_SPOTS=new ArrayList<>();
    private static final List<Berberis> BERBERIS=new ArrayList<>();
    private static final Map<Vec3,Sequence> SEQUENCES=new LinkedHashMap<>();
    private static final Map<Integer,VoltState> VOLT_STATES=new HashMap<>();
    private static final Map<Integer,Long> VOLT_CHARGING_SINCE=new HashMap<>();
    private static AndromedaConfig cfg;private static boolean initialized;private static BlockPos agaricus;private static long agaricusStarted;private static BlockPos lastPoweredButton;private static Object levelIdentity;
    private AndromedaDreadfarm(){}

    public static void init(AndromedaConfig config){
        cfg=config;loadButtons();if(initialized)return;initialized=true;
        for(int i=0;i<FIELD_CENTERS.size();i++)SEQUENCES.put(FIELD_CENTERS.get(i),new Sequence(FIELD_CENTERS.get(i),FIELD_COUNTS[i]));
        ConstellationClient.tick().every(1,"andromeda-dreadfarm",AndromedaDreadfarm::tick);
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof BlockStateUpdate update)onBlock(update);});
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void loadButtons(){
        if(!BUTTON_SPOTS.isEmpty())return;
        // data ported from SkyHanni Repo (MIT): constants/rift/RiftWoodenButtons.json
        try(var stream=AndromedaDreadfarm.class.getResourceAsStream("/assets/constellation/rift/rift_wooden_buttons.json")){
            if(stream==null)throw new IllegalStateException("missing Rift wooden-button data");JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("houses");
            for(var house:root.entrySet()){JsonArray spots=house.getValue().getAsJsonArray();for(int i=0;i<spots.size();i++){JsonObject spot=spots.get(i).getAsJsonObject();Vec3 center=vec(spot.get("position").getAsString());List<BlockPos> buttons=new ArrayList<>();for(var value:spot.getAsJsonArray("buttons"))buttons.add(block(value.getAsString()));BUTTON_SPOTS.add(new ButtonSpot(house.getKey(),center,List.copyOf(buttons)));}}
            ConstellationClient.LOGGER.info("loaded {} Rift wooden-button spots with {} buttons",BUTTON_SPOTS.size(),BUTTON_SPOTS.stream().mapToInt(s->s.buttons.size()).sum());
        }catch(Exception e){ConstellationClient.LOGGER.error("failed to load Rift wooden buttons",e);}
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        if(!active()){resetTransient();return;}updateAgaricus(mc);updateVolts(mc);expireBerberis();updateSequences(mc);
    }
    private static void updateAgaricus(Minecraft mc){
        if(!cfg.dreadAgaricusTimer||!inDreadOrWest()||!holdingWand()||!(mc.hitResult instanceof BlockHitResult hit)){agaricus=null;agaricusStarted=0;return;}
        BlockPos pos=hit.getBlockPos();var block=mc.level.getBlockState(pos).getBlock();
        if(block==Blocks.BROWN_MUSHROOM){if(!pos.equals(agaricus)){agaricus=pos.immutable();agaricusStarted=System.currentTimeMillis();}}
        else if(block==Blocks.RED_MUSHROOM&&pos.equals(agaricus))agaricusStarted=Long.MIN_VALUE;
        else{agaricus=null;agaricusStarted=0;}
    }
    private static void updateVolts(Minecraft mc){
        if(!inDread())return;double range=Math.clamp(cfg.dreadVoltScanRange,10,100),rangeSq=range*range;Map<Integer,VoltState> seen=new HashMap<>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.distanceToSqr(mc.player)<=rangeSq){VoltState state=volt(stand);if(state==VoltState.NONE)continue;seen.put(stand.getId(),state);if(state==VoltState.CHARGING&&VOLT_STATES.get(stand.getId())!=VoltState.CHARGING&&stand.distanceToSqr(mc.player)<=49)VOLT_CHARGING_SINCE.put(stand.getId(),System.currentTimeMillis());}
        VOLT_STATES.clear();VOLT_STATES.putAll(seen);VOLT_CHARGING_SINCE.keySet().retainAll(seen.keySet());
    }
    private static void expireBerberis(){long now=System.currentTimeMillis();BERBERIS.removeIf(value->now-value.last>500);}
    private static void updateSequences(Minecraft mc){for(Sequence sequence:SEQUENCES.values()){double distance=horizontal(sequence.center,mc.player.position());if(distance>20){sequence.away=true;continue;}if(sequence.away){sequence.away=false;validate(sequence,mc);}}}
    public static boolean onParticle(ClientboundLevelParticlesPacket packet){
        if(!active()||!inDread()||!cfg.dreadBerberis||!holdingWand())return false;Vec3 location=new Vec3(packet.getX(),packet.getY(),packet.getZ());long now=System.currentTimeMillis();
        Berberis nearest=BERBERIS.stream().filter(value->value.current.distanceToSqr(location)<8).min(Comparator.comparingDouble(value->value.current.distanceToSqr(location))).orElse(null);
        if(packet.getParticle().getType()==ParticleTypes.HAPPY_VILLAGER){learnSequence(location,now);return cfg.dreadBerberisHideParticles&&nearest!=null;}
        if(packet.getParticle().getType()!=ParticleTypes.FIREWORK)return cfg.dreadBerberisHideParticles&&nearest!=null;
        if(nearest==null)BERBERIS.add(new Berberis(location,now));else{boolean moving=nearest.current.distanceToSqr(location)>1.0E-6;if(moving){if(nearest.current.distanceTo(location)>3)nearest.previous=null;else if(!nearest.moving)nearest.previous=nearest.current;}else nearest.y=location.y-1;nearest.moving=moving;nearest.current=location;nearest.last=now;}
        return cfg.dreadBerberisHideParticles;
    }
    private static void learnSequence(Vec3 location,long now){
        if(!cfg.dreadBerberisSequence)return;Minecraft mc=Minecraft.getInstance();BlockPos pos=BlockPos.containing(location);if(mc.level.getBlockState(pos.below()).getBlock()!=Blocks.FARMLAND)return;
        Sequence sequence=SEQUENCES.values().stream().filter(s->horizontal(s.center,location)<50).min(Comparator.comparingDouble(s->horizontal(s.center,location))).orElse(null);if(sequence==null||sequence.valid)return;
        if(now-sequence.lastParticle>3000)sequence.points.clear();sequence.lastParticle=now;if(!sequence.points.isEmpty()&&sequence.points.getLast().equals(pos))return;sequence.points.add(pos.immutable());
        if(sequence.points.size()==sequence.expected){sequence.valid=true;sequence.index=0;local("Berberis respawn sequence learned for this field.");}
    }
    private static void onBlock(BlockStateUpdate update){
        if(!active())return;
        if(update.newState().getBlock() instanceof net.minecraft.world.level.block.ButtonBlock&&update.newState().getValue(net.minecraft.world.level.block.ButtonBlock.POWERED)){lastPoweredButton=update.pos();markButton(update.pos());}
        if(cfg.dreadBerberisSequence&&update.oldState().getBlock()==Blocks.DEAD_BUSH&&update.newState().isAir())for(Sequence sequence:SEQUENCES.values())if(sequence.valid&&!sequence.away&&sequence.index<sequence.points.size()&&sequence.points.get(sequence.index).equals(update.pos())){advance(sequence);break;}
    }
    private static void onChat(String message){
        if(!active())return;Matcher hit=BUTTON_HIT.matcher(message);if(hit.matches()){if(lastPoweredButton!=null)markButton(lastPoweredButton);else nearestLookedButton().ifPresent(AndromedaDreadfarm::markButton);}
        if(message.equals("You've hit all 56 wooden buttons!")){for(ButtonSpot spot:BUTTON_SPOTS)for(BlockPos pos:spot.buttons)cfg.dreadHitButtons.add(key(pos));save();}
    }
    private static void markButton(BlockPos pos){if(allButtons().stream().anyMatch(pos::equals)&&cfg.dreadHitButtons.add(key(pos)))save();}
    private static java.util.Optional<BlockPos> nearestLookedButton(){Minecraft mc=Minecraft.getInstance();if(mc.hitResult instanceof BlockHitResult hit){BlockPos pos=hit.getBlockPos();if(allButtons().contains(pos))return java.util.Optional.of(pos);}if(mc.player==null)return java.util.Optional.empty();return allButtons().stream().filter(pos->pos.distToCenterSqr(mc.player.position())<=25).min(Comparator.comparingDouble(pos->pos.distToCenterSqr(mc.player.position())));}
    private static List<BlockPos> allButtons(){return BUTTON_SPOTS.stream().flatMap(spot->spot.buttons.stream()).toList();}
    private static void advance(Sequence sequence){sequence.index++;if(sequence.index>=sequence.points.size()){sequence.index=0;if(!sequence.valid)sequence.points.clear();}}
    private static void validate(Sequence sequence,Minecraft mc){if(sequence.points.isEmpty())return;for(int i=0;i<sequence.points.size();i++){boolean expect=i>=sequence.index,isBush=mc.level.getBlockState(sequence.points.get(i)).getBlock()==Blocks.DEAD_BUSH;if(expect!=isBush){sequence.points.clear();sequence.index=0;sequence.valid=false;return;}}}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;drawAgaricus(ctx);drawVolts(ctx,mc);drawBerberis(ctx,mc);drawButtons(ctx,mc);
    }
    private static void drawAgaricus(WorldRenderer.Ctx ctx){
        if(agaricus==null||agaricusStarted==0)return;long remaining=agaricusStarted==Long.MIN_VALUE?0:Math.max(0,Math.clamp(cfg.dreadAgaricusMaturityMillis,1000,10000)-(System.currentTimeMillis()-agaricusStarted));int color=remaining==0?cfg.dreadAgaricusReadyColor:cfg.dreadAgaricusGrowingColor;String text=remaining==0?"Click!":cfg.dreadAgaricusCountdown?String.format(Locale.ROOT,"%.2fs",remaining/1000.0):String.format(Locale.ROOT,"%.2fs",(System.currentTimeMillis()-agaricusStarted)/1000.0);if(cfg.dreadAgaricusLabel)ctx.label(Vec3.atCenterOf(agaricus).add(0,.8,0),text,color,cfg.dreadAgaricusThroughWalls);
    }
    private static void drawVolts(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!inDread()||!cfg.dreadVoltRange&&!cfg.dreadVoltMood&&!cfg.dreadVoltWarning)return;for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand){VoltState state=VOLT_STATES.getOrDefault(stand.getId(),VoltState.NONE);if(state==VoltState.NONE)continue;int mood=switch(state){case FRIENDLY->cfg.dreadVoltFriendlyColor;case HOSTILE->cfg.dreadVoltHostileColor;case CHARGING->cfg.dreadVoltChargingColor;default->0;};if(cfg.dreadVoltMood)ctx.highlight(stand.getBoundingBox().inflate(.15),mood,cfg.dreadVoltThroughWalls);if(state!=VoltState.CHARGING)continue;double radius=Math.clamp(cfg.dreadVoltStrikeRangeTenths,10,150)/10.0;if(cfg.dreadVoltRange)ring(ctx,stand.position(),radius,cfg.dreadVoltRangeColor,cfg.dreadVoltThroughWalls);long started=VOLT_CHARGING_SINCE.getOrDefault(stand.getId(),System.currentTimeMillis()),remaining=Math.max(0,Math.clamp(cfg.dreadVoltChargeMillis,1000,30000)-(System.currentTimeMillis()-started));if(cfg.dreadVoltWarning&&cfg.dreadVoltLabel){String label="Lightning "+String.format(Locale.ROOT,"%.1fs",remaining/1000.0);if(cfg.dreadVoltDistance)label+=" "+Math.round(stand.distanceTo(mc.player))+"m";ctx.label(stand.position().add(0,2.5,0),label,cfg.dreadVoltChargingColor,cfg.dreadVoltThroughWalls);}}
    }
    private static void ring(WorldRenderer.Ctx ctx,Vec3 center,double radius,int color,boolean walls){int count=Math.clamp(cfg.dreadVoltRingSegments,12,96);for(int i=0;i<count;i++){double a=Math.PI*2*i/count,b=Math.PI*2*(i+1)/count;ctx.line(new Vec3(center.x+Math.cos(a)*radius,center.y+.05,center.z+Math.sin(a)*radius),new Vec3(center.x+Math.cos(b)*radius,center.y+.05,center.z+Math.sin(b)*radius),color,walls);}}
    private static void drawBerberis(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!inDread()||!cfg.dreadBerberis||!holdingWand()||cfg.dreadBerberisOnlyFarmland&&!onFarmland(mc))return;Sequence guide=SEQUENCES.values().stream().filter(s->s.valid&&!s.away&&!s.points.isEmpty()).min(Comparator.comparingDouble(s->horizontal(s.center,mc.player.position()))).orElse(null);
        if(cfg.dreadBerberisSequence&&guide!=null){drawSequence(ctx,guide);return;}double range=Math.clamp(cfg.dreadBerberisRange,5,50),rangeSq=range*range;for(Berberis value:BERBERIS){if(value.y==0||value.current.distanceToSqr(mc.player.position())>rangeSq)continue;Vec3 pos=new Vec3(value.current.x-.5,value.y,value.current.z-.5);int color=value.moving?cfg.dreadBerberisMovingColor:cfg.dreadBerberisStationaryColor;if(cfg.dreadBerberisBox)ctx.highlight(new AABB(pos.x+.1,pos.y-.1,pos.z+.1,pos.x+1.9,pos.y+1.9,pos.z+1.9),color,cfg.dreadBerberisThroughWalls);if(cfg.dreadBerberisLine&&value.previous!=null)ctx.line(new Vec3(value.previous.x,value.y+.5,value.previous.z),pos.add(.5,.5,.5),color,cfg.dreadBerberisThroughWalls);if(cfg.dreadBerberisLabel&&!value.moving)ctx.label(pos.add(.5,1.2,.5),"Wilted Berberis",color,cfg.dreadBerberisThroughWalls);}
    }
    private static void drawSequence(WorldRenderer.Ctx ctx,Sequence sequence){BlockPos current=sequence.points.get(sequence.index);drawTarget(ctx,current,cfg.dreadBerberisCurrentColor,"Wilted Berberis");if(sequence.index>0&&cfg.dreadBerberisLine)ctx.line(Vec3.atCenterOf(sequence.points.get(sequence.index-1)),Vec3.atCenterOf(current),cfg.dreadBerberisCurrentColor,cfg.dreadBerberisThroughWalls);if(sequence.index+1<sequence.points.size()){BlockPos next=sequence.points.get(sequence.index+1);drawTarget(ctx,next,cfg.dreadBerberisNextColor,null);if(cfg.dreadBerberisLine)ctx.line(Vec3.atCenterOf(current),Vec3.atCenterOf(next),cfg.dreadBerberisNextColor,cfg.dreadBerberisThroughWalls);if(sequence.index+2<sequence.points.size()){BlockPos third=sequence.points.get(sequence.index+2);drawTarget(ctx,third,cfg.dreadBerberisThirdColor,null);if(cfg.dreadBerberisLine)ctx.line(Vec3.atCenterOf(next),Vec3.atCenterOf(third),cfg.dreadBerberisThirdColor,cfg.dreadBerberisThroughWalls);}}}
    private static void drawTarget(WorldRenderer.Ctx ctx,BlockPos pos,int color,String label){if(cfg.dreadBerberisBox)ctx.highlight(new AABB(pos),color,cfg.dreadBerberisThroughWalls);if(label!=null&&cfg.dreadBerberisLabel)ctx.label(Vec3.atCenterOf(pos).add(0,.8,0),label,color,cfg.dreadBerberisThroughWalls);}
    private static void drawButtons(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.dreadButtons||cfg.dreadHitButtons.size()>=56||!inDreadOrWest())return;ButtonSpot spot=BUTTON_SPOTS.stream().filter(s->s.buttons.stream().anyMatch(p->!cfg.dreadHitButtons.contains(key(p)))).min(Comparator.comparingDouble(s->s.center.distanceToSqr(mc.player.position()))).orElse(null);if(spot==null)return;double distance=spot.center.distanceTo(mc.player.position());
        if(distance<=Math.clamp(cfg.dreadButtonsSpotRange,20,500)){if(cfg.dreadButtonsPath)ctx.line(mc.player.position().add(0,1,0),spot.center,cfg.dreadButtonsColor,cfg.dreadButtonsThroughWalls);if(cfg.dreadButtonsSpotBeam)ctx.beam(spot.center.x,spot.center.y,spot.center.z,cfg.dreadButtonsColor,10,cfg.dreadButtonsThroughWalls);if(cfg.dreadButtonsSpotLabel)ctx.label(spot.center.add(0,1,0),"Hit Buttons Here",cfg.dreadButtonsColor,cfg.dreadButtonsThroughWalls);}
        if(distance>Math.clamp(cfg.dreadButtonsRenderRange,5,40))return;int number=0;for(BlockPos pos:spot.buttons){number++;if(cfg.dreadHitButtons.contains(key(pos)))continue;if(cfg.dreadButtonsBoxes)ctx.highlight(new AABB(pos),cfg.dreadButtonsColor,cfg.dreadButtonsThroughWalls);if(cfg.dreadButtonsNumbers)ctx.label(Vec3.atCenterOf(pos),Integer.toString(number),cfg.dreadButtonsColor,cfg.dreadButtonsThroughWalls);}
    }
    public static boolean shouldCancelSound(ClientboundSoundPacket packet){if(cfg==null||!cfg.enabled||!cfg.dreadBerberisMuteOthers||!active()||!inDreadOrWest())return false;String path=packet.getSound().value().location().getPath();if(!path.equals("entity.donkey.death")&&!path.equals("entity.donkey.hurt"))return false;Minecraft mc=Minecraft.getInstance();return !(holdingWand()&&onFarmland(mc));}
    private static VoltState volt(ArmorStand stand){String hash=headHash(stand);return switch(hash){case VOLT_CHARGING->VoltState.CHARGING;case VOLT_FRIENDLY->VoltState.FRIENDLY;case VOLT_HOSTILE->VoltState.HOSTILE;default->VoltState.NONE;};}
    private static String headHash(ArmorStand stand){var profile=stand.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);if(profile==null)return"";for(Property property:profile.partialProfile().properties().get("textures")){String hash=textureHash(property.value());if(!hash.isEmpty())return hash;}return"";}
    private static String textureHash(String value){try{String decoded=new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);int at=decoded.indexOf("/texture/");if(at<0)return"";int start=at+9,end=decoded.indexOf('"',start);return end<0?decoded.substring(start):decoded.substring(start,end);}catch(Exception ignored){return"";}}
    private static boolean holdingWand(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&LyraTooltips.marketId(mc.player.getMainHandItem()).equals("FARMING_WAND");}
    private static boolean onFarmland(Minecraft mc){return mc.player!=null&&mc.level!=null&&mc.player.onGround()&&mc.level.getBlockState(mc.player.blockPosition().below()).getBlock()==Blocks.FARMLAND;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean inDread(){return AndromedaRiftCore.currentArea().equalsIgnoreCase("Dreadfarm");}
    private static boolean inDreadOrWest(){String area=AndromedaRiftCore.currentArea();return area.equalsIgnoreCase("Dreadfarm")||area.equalsIgnoreCase("West Village");}
    private static double horizontal(Vec3 a,Vec3 b){return Math.hypot(a.x-b.x,a.z-b.z);}
    private static String key(BlockPos pos){return pos.getX()+","+pos.getY()+","+pos.getZ();}
    private static Vec3 vec(String text){String[] p=text.split(":");return new Vec3(Double.parseDouble(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]));}
    private static BlockPos block(String text){Vec3 p=vec(text);return BlockPos.containing(p);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){agaricus=null;agaricusStarted=0;lastPoweredButton=null;BERBERIS.clear();VOLT_STATES.clear();VOLT_CHARGING_SINCE.clear();for(Sequence sequence:SEQUENCES.values()){sequence.points.clear();sequence.index=0;sequence.valid=false;sequence.away=false;sequence.lastParticle=0;}}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Dreadfarm] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dreadfarm").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetbuttons").executes(c->{cfg.dreadHitButtons.clear();save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetsequences").executes(c->{for(Sequence s:SEQUENCES.values()){s.points.clear();s.index=0;s.valid=false;}return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Agaricus "+(cfg.dreadAgaricusTimer?"on":"off")+", Volts "+(cfg.dreadVoltWarning||cfg.dreadVoltRange?"on":"off")+", Berberis "+(cfg.dreadBerberis?"on":"off")+", buttons "+cfg.dreadHitButtons.size()+"/56.");return 1;}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"maturity"->cfg.dreadAgaricusMaturityMillis=Math.clamp(value,1000,10000);case"voltrange"->cfg.dreadVoltScanRange=Math.clamp(value,10,100);case"strikerange"->cfg.dreadVoltStrikeRangeTenths=Math.clamp(value,10,150);case"charge"->cfg.dreadVoltChargeMillis=Math.clamp(value,1000,30000);case"segments"->cfg.dreadVoltRingSegments=Math.clamp(value,12,96);case"berberisrange"->cfg.dreadBerberisRange=Math.clamp(value,5,50);case"spotrange"->cfg.dreadButtonsSpotRange=Math.clamp(value,20,500);case"buttonrange"->cfg.dreadButtonsRenderRange=Math.clamp(value,5,40);default->{local("Unknown Dreadfarm number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"agaricus"->cfg.dreadAgaricusTimer=value;case"countdown"->cfg.dreadAgaricusCountdown=value;case"voltwarning"->cfg.dreadVoltWarning=value;case"voltrange"->cfg.dreadVoltRange=value;case"voltmood"->cfg.dreadVoltMood=value;case"berberis"->cfg.dreadBerberis=value;case"farmland"->cfg.dreadBerberisOnlyFarmland=value;case"hideparticles"->cfg.dreadBerberisHideParticles=value;case"mutesounds"->cfg.dreadBerberisMuteOthers=value;case"sequence"->cfg.dreadBerberisSequence=value;case"buttons"->cfg.dreadButtons=value;case"buttonpath"->cfg.dreadButtonsPath=value;default->{local("Unknown Dreadfarm option.");return 0;}}save();return status();}
}
