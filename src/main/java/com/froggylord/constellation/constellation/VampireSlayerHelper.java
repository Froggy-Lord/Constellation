package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PerseusConfig;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/slayers/boss/vampire/ManiaIndicator.java, StakeIndicator.java, TwinClawsIndicator.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/rift/HealingMelonIndicator.java, skyblock/slayers/SlayerType.java
// ported from SkyHanni (LGPL-3.0-or-later): features/slayer/VampireSlayerFeatures.kt, features/combat/damageindicator/DamageIndicatorManager.kt
public final class VampireSlayerHelper {
    private enum Alert{NONE,HEAL,MANIA,TWINCLAWS,STAKE}
    private enum Owner{OWN,COOP,OTHER}
    private static final String ICHOR_HASH="c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422";
    private static final String SPRING_HASH="77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54";
    private static final int[] MAX_HEALTH={0,625,1100,1800,2400,3000};
    private static final Pattern HEALTH=Pattern.compile("([\\d,.]+[kKmMbB]?)\\s*\\u2764");
    private static final Set<Integer> TAGGED=new HashSet<>();
    private static PerseusConfig cfg;private static boolean initialized,maniaSafe;private static int ticks;private static long twinSeen=-1,lastAlertAt,lastWitherTick=Long.MIN_VALUE;private static Alert current=Alert.NONE;private static Object levelIdentity;
    private VampireSlayerHelper(){}

    public static void init(PerseusConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"perseus-vampire-helper",VampireSlayerHelper::tick);
        AttackEntityCallback.EVENT.register((player,level,hand,entity,hit)->{if(active())for(SlayerState.Boss boss:SlayerState.bosses())if(boss.type()==SlayerState.Type.VAMP&&boss.entity().getId()==entity.getId()&&!own(boss)){TAGGED.add(entity.getId());break;}return InteractionResult.PASS;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        if(!active()||mc.player==null||mc.level==null){current=Alert.NONE;return;}if(++ticks%Math.clamp(cfg.vampireUpdateTicks,1,20)!=0)return;
        TAGGED.removeIf(id->mc.level.getEntity(id)==null);SlayerState.Boss boss=alertBoss();boolean heal=cfg.vampireHealingMelon&&mc.player.getHealth()<=Math.clamp(cfg.vampireHealingMelonHearts,1,20)*2f;
        boolean stake=false,mania=false,twin=false;maniaSafe=false;
        if(boss!=null){List<ArmorStand> stands=stands(boss);stake=cfg.vampireStake&&(named(mc.level.getEntity(boss.nameStandId())).contains("҉")||health(boss)<=max(boss)*.2);mania=cfg.vampireMania&&stands.stream().anyMatch(value->named(value).contains("MANIA"));twin=cfg.vampireTwinclaws&&stands.stream().anyMatch(value->named(value).contains("TWINCLAWS"));maniaSafe=mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.DYED_TERRACOTTA.green());}
        if(twin){if(twinSeen<0)twinSeen=mc.level.getGameTime();twin=mc.level.getGameTime()-twinSeen>=Math.clamp(cfg.vampireTwinclawsDelayTicks,0,40);}else twinSeen=-1;
        Alert next=stake?Alert.STAKE:twin?Alert.TWINCLAWS:mania?Alert.MANIA:heal?Alert.HEAL:Alert.NONE;show(next,mc);
    }
    private static void show(Alert next,Minecraft mc){
        boolean changed=next!=current||next==Alert.MANIA&&maniaSafe!=lastSafe;current=next;lastSafe=maniaSafe;if(next==Alert.NONE||mc.player==null)return;
        String message=message(next);int color=color(next);boolean title=title(next),chat=chat(next),sound=sound(next);long now=mc.level==null?0:mc.level.getGameTime();
        if(title){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(message).withColor(color&0xFFFFFF));if(next==Alert.MANIA&&cfg.vampireManiaCountdown){SlayerState.Boss boss=alertBoss();String remaining=maniaTime(boss);if(!remaining.isBlank())mc.gui.hud.setSubtitle(Component.literal(remaining));}mc.gui.hud.setTimes(0,Math.clamp(cfg.vampireUpdateTicks,1,20)+2,0);}
        if(changed&&(lastAlertAt==0||now-lastAlertAt>=Math.clamp(cfg.vampireAlertRepeatTicks,0,200))){lastAlertAt=now;if(chat)local(message);if(sound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,next==Alert.TWINCLAWS?1.4f:.8f);}
    }
    private static boolean lastSafe;

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;double range=Math.clamp(cfg.vampireRange,5,64),sq=range*range;List<SlayerState.Boss> shown=new ArrayList<>();
        for(SlayerState.Boss boss:SlayerState.bosses()){if(boss.type()!=SlayerState.Type.VAMP||!boss.entity().isAlive()||boss.entity().distanceToSqr(mc.player)>sq)continue;Owner owner=owner(boss);if(!show(owner,boss))continue;shown.add(boss);double hp=health(boss),max=max(boss);boolean steak=hp<=max*.2;int color=steak&&cfg.vampireChangeColorWhenSteak?cfg.vampireSteakColor:owner==Owner.OWN?cfg.vampireOwnBossColor:owner==Owner.COOP?cfg.vampireCoopBossColor:cfg.vampireOtherBossColor;Entity entity=boss.entity();if(cfg.vampireBossBox)ctx.highlight(entity.getBoundingBox().inflate(.1),color,cfg.vampireBossThroughWalls);if(cfg.vampireBossLine)ctx.line(mc.player.getEyePosition(),entity.getBoundingBox().getCenter(),cfg.vampireLineColor,cfg.vampireBossThroughWalls,Math.clamp(cfg.vampireLineWidth,1,10));List<String> labels=labels(boss,hp,max);if(cfg.vampireBossLabel||!labels.isEmpty()){if(cfg.vampireBossLabel){String base=owner==Owner.OWN?"Your Bloodfiend":owner==Owner.COOP?"Co-op Bloodfiend":"Bloodfiend";if(cfg.vampireBossDistance)base+=" "+Math.round(entity.distanceTo(mc.player))+"m";labels.add(0,base);}Vec3 pos=entity.position().add(0,entity.getBbHeight()+.6+labels.size()*.25,0);for(int i=0;i<labels.size();i++)ctx.label(pos.add(0,-i*.25,0),labels.get(i),color,cfg.vampireBossThroughWalls);}}
        if(!cfg.vampireBloodIchor&&!cfg.vampireKillerSpring)return;for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.isAlive()&&stand.distanceToSqr(mc.player)<=sq){String hash=hash(stand);boolean ichor=hash.equals(ICHOR_HASH),spring=hash.equals(SPRING_HASH);if(!ichor&&!spring||ichor&&!cfg.vampireBloodIchor||spring&&!cfg.vampireKillerSpring)continue;int color=ichor?cfg.vampireBloodIchorColor:cfg.vampireKillerSpringColor;boolean box=ichor?cfg.vampireBloodIchorBox:cfg.vampireKillerSpringBox,label=ichor?cfg.vampireBloodIchorLabel:cfg.vampireKillerSpringLabel,line=ichor?cfg.vampireBloodIchorLine:cfg.vampireKillerSpringLine;if(box)ctx.highlight(stand.getBoundingBox().inflate(.15),color,cfg.vampireBossThroughWalls);Vec3 center=stand.getBoundingBox().getCenter();if(ichor&&cfg.vampireBloodIchorBeam)ctx.beam(center.x,center.y-2,center.z,color,8,cfg.vampireBossThroughWalls);if(label)ctx.label(center.add(0,.8,0),ichor?"Blood Ichor":"Killer Spring",color,cfg.vampireBossThroughWalls);if(line&&!shown.isEmpty()){SlayerState.Boss boss=shown.stream().min(Comparator.comparingDouble(value->value.entity().distanceToSqr(stand))).orElseThrow();ctx.line(boss.entity().getEyePosition(),center,color,cfg.vampireBossThroughWalls,3);}}
    }
    private static List<String> labels(SlayerState.Boss boss,double health,double max){List<String> lines=new ArrayList<>();if(cfg.vampireHealthPercentage)lines.add(String.format(Locale.ROOT,"%.1f%%",Math.max(0,health/max*100)));if(cfg.vampireHpTillSteak){double left=health-max*.2;lines.add(left>0&&left<300?"HP till Steak: "+compact(left):left<=0?"Steak!":"");}if(cfg.vampireManiaCountdown&&!maniaTime(boss).isBlank())lines.add("Mania Circles: "+maniaTime(boss));lines.removeIf(String::isBlank);return lines;}
    private static String maniaTime(SlayerState.Boss boss){if(boss==null||boss.entity().getVehicle()==null||boss.entity().getVehicle().tickCount<=40)return"";double seconds=Math.max(0,(520-boss.entity().getVehicle().tickCount)/20.0);return String.format(Locale.ROOT,"%.1fs",seconds);}
    private static SlayerState.Boss alertBoss(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return null;SlayerState.Boss fallback=null;for(SlayerState.Boss boss:SlayerState.bosses())if(boss.type()==SlayerState.Type.VAMP&&boss.entity().isAlive()){if(own(boss))return boss;if(fallback==null&&(!cfg.vampireOnlyOwnBoss||coop(boss)||TAGGED.contains(boss.entity().getId())))fallback=boss;}return fallback;}
    private static List<ArmorStand> stands(SlayerState.Boss boss){Minecraft mc=Minecraft.getInstance();return mc.level==null?List.of():mc.level.getEntitiesOfClass(ArmorStand.class,boss.entity().getBoundingBox().inflate(2.5),value->value.hasCustomName());}
    private static Owner owner(SlayerState.Boss boss){return own(boss)?Owner.OWN:coop(boss)?Owner.COOP:Owner.OTHER;}
    private static boolean show(Owner owner,SlayerState.Boss boss){return switch(owner){case OWN->cfg.vampireOwnBossHighlight;case COOP->cfg.vampireCoopBossHighlight;case OTHER->cfg.vampireOtherBossHighlight&&TAGGED.contains(boss.entity().getId());};}
    private static boolean own(SlayerState.Boss boss){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&boss.owner().equalsIgnoreCase(mc.player.getName().getString());}
    private static boolean coop(SlayerState.Boss boss){for(String value:cfg.vampireCoopMembers.split(","))if(!value.isBlank()&&boss.owner().equalsIgnoreCase(value.trim()))return true;return false;}
    private static double health(SlayerState.Boss boss){Minecraft mc=Minecraft.getInstance();if(mc.level!=null){Matcher matcher=HEALTH.matcher(named(mc.level.getEntity(boss.nameStandId())));if(matcher.find())return number(matcher.group(1));}return boss.entity() instanceof LivingEntity living?Math.max(0,living.getHealth()):max(boss);}
    private static double max(SlayerState.Boss boss){return MAX_HEALTH[Math.clamp(boss.tier(),1,5)];}
    private static double number(String value){try{String clean=value.replace(",","").toLowerCase(Locale.ROOT);double multiplier=clean.endsWith("k")?1_000:clean.endsWith("m")?1_000_000:clean.endsWith("b")?1_000_000_000:1;if(multiplier!=1)clean=clean.substring(0,clean.length()-1);return Double.parseDouble(clean)*multiplier;}catch(Exception ignored){return 0;}}
    private static String compact(double value){if(value>=1_000_000)return String.format(Locale.ROOT,"%.1fm",value/1_000_000);if(value>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1_000);return Integer.toString((int)Math.ceil(value));}
    private static String named(Entity entity){if(entity==null)return"";String clean=ChatFormatting.stripFormatting(entity.getName().getString());return clean==null?"":clean.trim();}
    private static String hash(ArmorStand stand){var profile=stand.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);if(profile==null)return"";for(Property property:profile.partialProfile().properties().get("textures"))try{String json=new String(Base64.getDecoder().decode(property.value()),StandardCharsets.UTF_8);String url=JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();return url.substring(url.lastIndexOf('/')+1);}catch(Exception ignored){}return"";}
    private static boolean active(){if(cfg==null||!cfg.enabled||!cfg.vampireHelper||ConstellationClient.loc().area()!=SkyblockArea.THE_RIFT)return false;String area=AndromedaRiftCore.currentArea();return area.equalsIgnoreCase("Stillgore Chateau")||area.equalsIgnoreCase("Stillgore Château")||area.equalsIgnoreCase("Oubliette");}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){TAGGED.clear();ticks=0;twinSeen=-1;lastAlertAt=0;lastWitherTick=Long.MIN_VALUE;current=Alert.NONE;lastSafe=false;}
    public static boolean shouldCancelSound(ClientboundSoundPacket packet){if(!active()||!cfg.vampireKillerSpringSoundSpamFix||!packet.getSound().value().location().equals(SoundEvents.WITHER_SPAWN.location()))return false;Minecraft mc=Minecraft.getInstance();long now=mc.level==null?Long.MIN_VALUE:mc.level.getGameTime();if(now==lastWitherTick)return true;lastWitherTick=now;return false;}
    private static String message(Alert alert){return switch(alert){case HEAL->cfg.vampireHealingMelonMessage;case STAKE->cfg.vampireStakeMessage;case TWINCLAWS->cfg.vampireTwinclawsMessage;case MANIA->cfg.vampireManiaMessage;default->"";};}
    private static int color(Alert alert){return switch(alert){case HEAL->cfg.vampireHealingMelonColor;case STAKE->cfg.vampireStakeColor;case TWINCLAWS->cfg.vampireTwinclawsColor;case MANIA->maniaSafe?cfg.vampireManiaSafeColor:cfg.vampireManiaDangerColor;default->0xFFFFFFFF;};}
    private static boolean title(Alert alert){return switch(alert){case HEAL->cfg.vampireHealingMelonTitle;case STAKE->cfg.vampireStakeTitle;case TWINCLAWS->cfg.vampireTwinclawsTitle;case MANIA->cfg.vampireManiaTitle;default->false;};}
    private static boolean chat(Alert alert){return switch(alert){case HEAL->cfg.vampireHealingMelonChat;case STAKE->cfg.vampireStakeChat;case TWINCLAWS->cfg.vampireTwinclawsChat;case MANIA->cfg.vampireManiaChat;default->false;};}
    private static boolean sound(Alert alert){return switch(alert){case HEAL->cfg.vampireHealingMelonSound;case STAKE->cfg.vampireStakeSound;case TWINCLAWS->cfg.vampireTwinclawsSound;case MANIA->cfg.vampireManiaSound;default->false;};}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Vampire] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("vampirehelper").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->numberOption(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("message").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->messageOption(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"text"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("coop").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("names",StringArgumentType.greedyString()).executes(c->coopOption(StringArgumentType.getString(c,"names")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->colorOption(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Healing "+on(cfg.vampireHealingMelon)+", Steak "+on(cfg.vampireStake)+", Twinclaws "+on(cfg.vampireTwinclaws)+", Mania "+on(cfg.vampireMania)+", Ichor "+on(cfg.vampireBloodIchor)+", Spring "+on(cfg.vampireKillerSpring)+".");return 1;}
    private static int numberOption(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"hearts"->cfg.vampireHealingMelonHearts=Math.clamp(value,1,20);case"delay"->cfg.vampireTwinclawsDelayTicks=Math.clamp(value,0,40);case"range"->cfg.vampireRange=Math.clamp(value,5,64);case"update"->cfg.vampireUpdateTicks=Math.clamp(value,1,20);case"repeat"->cfg.vampireAlertRepeatTicks=Math.clamp(value,0,200);case"linewidth"->cfg.vampireLineWidth=Math.clamp(value,1,10);default->{local("Unknown Vampire number.");return 0;}}save();return status();}
    private static int messageOption(String type,String text){if(text.isBlank()){local("Message cannot be blank.");return 0;}switch(type.toLowerCase(Locale.ROOT)){case"heal"->cfg.vampireHealingMelonMessage=text;case"steak"->cfg.vampireStakeMessage=text;case"twinclaws","ice"->cfg.vampireTwinclawsMessage=text;case"mania"->cfg.vampireManiaMessage=text;default->{local("Message type must be heal, steak, twinclaws, or mania.");return 0;}}save();return status();}
    private static int coopOption(String names){cfg.vampireCoopMembers=names.trim();save();local(cfg.vampireCoopMembers.isBlank()?"Co-op boss list cleared.":"Co-op boss list: "+cfg.vampireCoopMembers+".");return 1;}
    private static int colorOption(String name,String raw){try{String value=raw.replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;int color=(int)parsed;switch(name.toLowerCase(Locale.ROOT)){case"own"->cfg.vampireOwnBossColor=color;case"other"->cfg.vampireOtherBossColor=color;case"coop"->cfg.vampireCoopBossColor=color;case"steak"->cfg.vampireSteakColor=color;case"heal"->cfg.vampireHealingMelonColor=color;case"twinclaws","ice"->cfg.vampireTwinclawsColor=color;case"mania"->cfg.vampireManiaDangerColor=color;case"safe"->cfg.vampireManiaSafeColor=color;case"ichor"->cfg.vampireBloodIchorColor=color;case"spring"->cfg.vampireKillerSpringColor=color;case"line"->cfg.vampireLineColor=color;default->{local("Unknown Vampire color.");return 0;}}save();return status();}catch(Exception e){local("Color must be ARGB hex.");return 0;}}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.vampireHelper=value;case"ownonly"->cfg.vampireOnlyOwnBoss=value;case"own"->cfg.vampireOwnBossHighlight=value;case"other"->cfg.vampireOtherBossHighlight=value;case"coop"->cfg.vampireCoopBossHighlight=value;case"heal"->cfg.vampireHealingMelon=value;case"steak"->cfg.vampireStake=value;case"twinclaws","ice"->cfg.vampireTwinclaws=value;case"mania"->cfg.vampireMania=value;case"countdown"->cfg.vampireManiaCountdown=value;case"hptillsteak"->cfg.vampireHpTillSteak=value;case"percentage"->cfg.vampireHealthPercentage=value;case"box"->cfg.vampireBossBox=value;case"label"->cfg.vampireBossLabel=value;case"line"->cfg.vampireBossLine=value;case"ichor"->cfg.vampireBloodIchor=value;case"spring"->cfg.vampireKillerSpring=value;case"spamfix"->cfg.vampireKillerSpringSoundSpamFix=value;default->{local("Unknown Vampire option.");return 0;}}save();return status();}
    private static String on(boolean value){return value?"on":"off";}
}
