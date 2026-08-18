package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.AquilaConfig;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CorpseFinder.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CorpseType.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/profittrackers/corpse/CorpseProfitTracker.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/profittrackers/corpse/CorpseLoot.java
// ported from SkyOcean (MIT): features/mining/mineshaft/CorpseKeyAnnouncement.kt
public final class AquilaCorpseHelper {
    public enum Type {
        LAPIS("LAPIS_ARMOR_HELMET", "", "Lapis"), UMBER("ARMOR_OF_YOG_HELMET", "UMBER_KEY", "Umber"),
        TUNGSTEN("MINERAL_HELMET", "TUNGSTEN_KEY", "Tungsten"), VANGUARD("VANGUARD_HELMET", "SKELETON_KEY", "Vanguard");
        final String helmet, key, display;
        Type(String helmet,String key,String display){this.helmet=helmet;this.key=key;this.display=display;}
        public String display(){return display;}
    }
    public record KeyRow(Type type,int required,int inventory,int storage,Integer sacks,boolean storageComplete) { public int known(){return inventory+storage+(sacks==null?0:sacks);} public boolean complete(){return storageComplete&&sacks!=null;} }
    public record Stats(int found,int opened,double lastProfit,double totalProfit,boolean lastComplete,boolean sessionComplete){}
    private static final Pattern COORDS=Pattern.compile("x: (?<x>-?\\d+), y: (?<y>\\d+), z: (?<z>-?\\d+)");
    private static final Pattern LOOT_START=Pattern.compile("^\\s*(LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT!\\s*$");
    private static final Pattern REWARD=Pattern.compile("^\\s{4}(.*?) ?x?([\\d,]*)\\s*$");
    private static final Map<String,String> ITEM_IDS=new HashMap<>();
    private static final Set<String> PRICELESS=Set.of("GLACITE_POWDER","OPAL_CRYSTAL","ONYX_CRYSTAL","AQUAMARINE_CRYSTAL","PERIDOT_CRYSTAL","CITRINE_CRYSTAL","RUBY_CRYSTAL","JASPER_CRYSTAL");
    private static final Map<BlockPos,Corpse> CORPSES=new HashMap<>();
    private static final Map<Type,Integer> OPENED=new EnumMap<>(Type.class);
    private static AquilaConfig cfg;
    private static Object levelIdentity;
    private static int openedTotal;
    private static double totalProfit,lastProfit;
    private static boolean lastComplete;
    private static boolean sessionComplete = true;
    private static Loot currentLoot;
    private static long lastShare;
    private static final Deque<PendingShare> SHARE_QUEUE=new ArrayDeque<>();
    private static String profileKey="";

    static {
        put("Goblin Egg","GOBLIN_EGG");put("Green Goblin Egg","GOBLIN_EGG_GREEN");put("Blue Goblin Egg","GOBLIN_EGG_BLUE");put("Red Goblin Egg","GOBLIN_EGG_RED");put("Yellow Goblin Egg","GOBLIN_EGG_YELLOW");
        put("Enchanted Glacite","ENCHANTED_GLACITE");put("Enchanted Umber","ENCHANTED_UMBER");put("Enchanted Tungsten","ENCHANTED_TUNGSTEN");put("Refined Umber","REFINED_UMBER");put("Refined Tungsten","REFINED_TUNGSTEN");put("Refined Mithril","REFINED_MITHRIL");put("Refined Titanium","REFINED_TITANIUM");put("Mithril Plate","MITHRIL_PLATE");put("Umber Plate","UMBER_PLATE");put("Tungsten Plate","TUNGSTEN_PLATE");
        put("Skeleton Key","SKELETON_KEY");put("Tungsten Key","TUNGSTEN_KEY");put("Umber Key","UMBER_KEY");put("Glacite Amalgamation","GLACITE_AMALGAMATION");put("Bejeweled Handle","BEJEWELED_HANDLE");put("Glacite Jewel","GLACITE_JEWEL");put("Suspicious Scrap","SUSPICIOUS_SCRAP");put("Enchanted Book (Ice Cold I)","ENCHANTMENT_ICE_COLD_1");put("Dwarven O's Metallic Minis","DWARVEN_OS_METALLIC_MINIS");put("Frozen Scute","FROZEN_SCUTE");put("Shattered Locket","SHATTERED_PENDANT");put("Caged Wisp","CAGED_WISP");put("Frostbitten Dye","DYE_FROSTBITTEN");
        put("Glacite Powder","GLACITE_POWDER");put("Opal Crystal","OPAL_CRYSTAL");put("Onyx Crystal","ONYX_CRYSTAL");put("Aquamarine Crystal","AQUAMARINE_CRYSTAL");put("Peridot Crystal","PERIDOT_CRYSTAL");put("Citrine Crystal","CITRINE_CRYSTAL");put("Ruby Crystal","RUBY_CRYSTAL");put("Jasper Crystal","JASPER_CRYSTAL");
        put("Flawed Onyx Gemstone","FLAWED_ONYX_GEM");put("Fine Onyx Gemstone","FINE_ONYX_GEM");put("Flawless Onyx Gemstone","FLAWLESS_ONYX_GEM");put("Flawed Peridot Gemstone","FLAWED_PERIDOT_GEM");put("Fine Peridot Gemstone","FINE_PERIDOT_GEM");put("Flawless Peridot Gemstone","FLAWLESS_PERIDOT_GEM");put("Flawed Citrine Gemstone","FLAWED_CITRINE_GEM");put("Fine Citrine Gemstone","FINE_CITRINE_GEM");put("Flawless Citrine Gemstone","FLAWLESS_CITRINE_GEM");put("Flawed Aquamarine Gemstone","FLAWED_AQUAMARINE_GEM");put("Fine Aquamarine Gemstone","FINE_AQUAMARINE_GEM");put("Flawless Aquamarine Gemstone","FLAWLESS_AQUAMARINE_GEM");
    }
    private AquilaCorpseHelper(){}
    public static void init(AquilaConfig config){cfg=config;ConstellationClient.tick().every(5,"aquila-corpse-scan",AquilaCorpseHelper::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(message.getString());return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset(null));ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset(null));}
    private static void tick(){Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity)reset(mc.level);String currentProfile=LyraStorageValue.currentProfileKey();if(!currentProfile.equals(profileKey)){profileKey=currentProfile;resetSession();}drainShares();if(!active()||!cfg.corpseFinder||mc.player==null)return;double range2=cfg.corpseScanRange*cfg.corpseScanRange;for(var entity:mc.level.entitiesForRendering()){if(!(entity instanceof ArmorStand stand)||stand.distanceToSqr(mc.player)>range2||stand.hasCustomName()||stand.isInvisible()||stand.showBasePlate())continue;Type type=type(stand.getItemBySlot(EquipmentSlot.HEAD));if(type==null)continue;BlockPos pos=stand.blockPosition();Corpse corpse=CORPSES.get(pos);if(corpse==null||corpse.type!=type){corpse=new Corpse(stand,type);CORPSES.put(pos,corpse);}else corpse.entity=stand;if(!corpse.seen&&mc.player.hasLineOfSight(stand)){corpse.seen=true;found(corpse);}}}
    public static void draw(WorldRenderer.Ctx ctx){if(!active()||!cfg.corpseFinder)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;for(Corpse corpse:CORPSES.values()){if(corpse.opened||cfg.corpseOnlySeen&&!corpse.seen)continue;Vec3 pos=corpse.pos();int color=color(corpse.type);if(cfg.corpseShowBox)ctx.highlight(new AABB(pos.x-.45,pos.y-.9,pos.z-.45,pos.x+.45,pos.y+.9,pos.z+.45),color,cfg.corpseThroughWalls);if(cfg.corpseShowBeam)ctx.beam(pos.x,pos.y,pos.z,color,cfg.corpseBeamHeight,cfg.corpseThroughWalls);if(cfg.corpseShowLabel){String label=corpse.type.display+" Corpse";if(cfg.corpseShowDistance)label+=" "+Math.round(mc.player.distanceTo(corpse.entity))+"m";ctx.label(pos.add(0,1.3,0),label,color,cfg.corpseThroughWalls);}}}
    private static void onChat(String raw){if(!active())return;String message=ChatFormatting.stripFormatting(raw);if(message==null)return;if(cfg.corpseParsePartyCoordinates)parseCoords(message);Matcher start=LOOT_START.matcher(message);if(start.matches()){Type type=Type.valueOf(start.group(1));markNearestOpened(type);currentLoot=new Loot(type);return;}if(currentLoot==null)return;if(separator(message)){finishLoot();return;}if(message.trim().contains("HOTM Experience"))return;Matcher reward=REWARD.matcher(message);if(reward.matches()){String name=sanitizeReward(reward.group(1));int amount=parse(reward.group(2),1);currentLoot.add(name,amount);}}
    private static void found(Corpse corpse){if(cfg.corpseLocalFoundMessage)local("Found a "+corpse.type.display+" Corpse at "+coords(corpse.block.above())+". "+(cfg.corpseAutoShareParty?"Queued for party sharing.":"Use /corpses share nearest to share."));if(cfg.corpseAutoShareParty&&SHARE_QUEUE.stream().noneMatch(p->p.pos.equals(corpse.block.above())))SHARE_QUEUE.addLast(new PendingShare(corpse.block.above(),corpse.type));}
    private static void drainShares(){if(cfg==null||!cfg.corpseAutoShareParty||!cfg.corpseFinder){SHARE_QUEUE.clear();return;}if(!active()||SHARE_QUEUE.isEmpty()||System.currentTimeMillis()-lastShare<1200)return;PendingShare pending=SHARE_QUEUE.removeFirst();share(pending.pos,pending.type);lastShare=System.currentTimeMillis();}
    private static void markNearestOpened(Type type){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;CORPSES.values().stream().filter(c->c.type==type&&!c.opened).min(Comparator.comparingDouble(c->c.entity.distanceToSqr(mc.player))).ifPresent(c->c.opened=true);OPENED.merge(type,1,Integer::sum);openedTotal++;}
    private static void parseCoords(String message){Matcher m=COORDS.matcher(message);if(!m.find())return;BlockPos parsed=new BlockPos(parse(m.group("x"),0)-1,parse(m.group("y"),0),parse(m.group("z"),0)-1);Corpse exact=CORPSES.get(parsed.below());if(exact!=null)exact.seen=true;else CORPSES.values().stream().min(Comparator.comparingDouble(c->c.block.distSqr(parsed.below()))).filter(c->c.block.distSqr(parsed.below())<4).ifPresent(c->c.seen=true);}
    private static void finishLoot(){if(currentLoot==null)return;double key=currentLoot.type==Type.LAPIS?0:PriceProvider.purchaseValue(currentLoot.type.key);if(key<=0&&currentLoot.type!=Type.LAPIS){PriceProvider.warm(currentLoot.type.key);currentLoot.complete=false;}currentLoot.profit-=key;lastProfit=currentLoot.profit;lastComplete=currentLoot.complete;sessionComplete&=currentLoot.complete;totalProfit+=currentLoot.profit;if(cfg.corpseProfitChat)local("Corpse profit: "+(currentLoot.complete?coins(currentLoot.profit):coins(currentLoot.profit)+" partial")+".");currentLoot=null;}
    public static List<KeyRow> keyRows(){if(!active())return List.of();List<KeyRow> out=new ArrayList<>();for(Type type:Type.values()){if(type==Type.LAPIS)continue;int required=(int)CORPSES.values().stream().filter(c->c.type==type&&!c.opened).count();if(required>0){var storage=LyraStorageValue.cachedItemCount(type.key);out.add(new KeyRow(type,required,inventory(type.key),storage.amount(),HerculesVisitorHelper.observedSackCount(type.key),storage.complete()));}}return out;}
    public static Stats stats(){return active()?new Stats(CORPSES.size(),openedTotal,lastProfit,totalProfit,lastComplete,sessionComplete):null;}
    public static AquilaConfig config(){return cfg;}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("corpses").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{reset(Minecraft.getInstance().level);local("Corpse session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("share").then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nearest").executes(c->shareNearest()))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(16,256)).executes(c->{cfg.corpseScanRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->setColor(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"argb")))))));}
    private static int status(){local("Corpses found "+CORPSES.size()+", opened "+openedTotal+", last profit "+(lastComplete?coins(lastProfit):coins(lastProfit)+" partial")+", total "+coins(totalProfit)+".");return 1;}
    private static int shareNearest(){Minecraft mc=Minecraft.getInstance();if(!active()||mc.player==null)return 0;Corpse corpse=CORPSES.values().stream().filter(c->!c.opened).min(Comparator.comparingDouble(c->c.entity.distanceToSqr(mc.player))).orElse(null);if(corpse==null){local("No unopened corpse is known.");return 0;}share(corpse.block.above(),corpse.type);return 1;}
    private static int setColor(String name,String text){Type type;try{type=Type.valueOf(name.toUpperCase(Locale.ROOT));}catch(Exception e){local("Type must be lapis, umber, tungsten, or vanguard.");return 0;}Integer value=parseColor(text);if(value==null){local("Color must be an eight-digit ARGB hex value.");return 0;}switch(type){case LAPIS->cfg.corpseLapisColor=value;case UMBER->cfg.corpseUmberColor=value;case TUNGSTEN->cfg.corpseTungstenColor=value;case VANGUARD->cfg.corpseVanguardColor=value;}save();return 1;}
    private static void share(BlockPos pos,Type type){Minecraft mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.connection!=null)mc.player.connection.sendCommand("pc "+coords(pos)+" | ("+type.display+" Corpse)");}
    private static String coords(BlockPos pos){return String.format(Locale.ROOT,"x: %d, y: %d, z: %d",pos.getX()+1,pos.getY(),pos.getZ()+1);}
    private static Type type(ItemStack helmet){String id=LyraTooltips.marketId(helmet);for(Type type:Type.values())if(type.helmet.equals(id))return type;return null;}
    private static int inventory(String id){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return 0;int total=0;for(ItemStack stack:mc.player.getInventory())if(id.equals(LyraTooltips.marketId(stack)))total+=stack.getCount();return total;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.corpseSuite&&ConstellationClient.loc().area()==SkyblockArea.GLACITE_MINESHAFT;}
    private static boolean separator(String message){String s=message.trim();if(s.length()<40)return false;int cp=s.codePointAt(0);return s.codePoints().allMatch(c->c==cp);}
    private static String sanitizeReward(String name){return name.replaceAll("^[^A-Za-z0-9+]+\\s*","").trim();}
    private static int color(Type t){return switch(t){case LAPIS->cfg.corpseLapisColor;case UMBER->cfg.corpseUmberColor;case TUNGSTEN->cfg.corpseTungstenColor;case VANGUARD->cfg.corpseVanguardColor;};}
    private static void reset(Object level){levelIdentity=level;profileKey="";resetSession();}
    private static void resetSession(){CORPSES.clear();OPENED.clear();SHARE_QUEUE.clear();openedTotal=0;totalProfit=0;lastProfit=0;lastComplete=false;sessionComplete=true;currentLoot=null;lastShare=0;}
    private static void put(String name,String id){ITEM_IDS.put(name,id);}
    private static int parse(String value,int fallback){if(value==null||value.isBlank())return fallback;try{return Integer.parseInt(value.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static Integer parseColor(String text){String s=text.startsWith("#")?text.substring(1):text;if(s.length()!=8)return null;try{return(int)Long.parseLong(s,16);}catch(Exception ignored){return null;}}
    private static String coins(double value){double a=Math.abs(value);String s=a>=1_000_000?String.format(Locale.ROOT,"%.2fM",a/1_000_000):a>=1_000?String.format(Locale.ROOT,"%.1fk",a/1_000):String.format(Locale.ROOT,"%.0f",a);return value<0?"-"+s:s;}
    private static void local(String message){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Corpses] §f"+message));}
    private static void save(){ConstellationClient.saveConfig();}
    private static final class Corpse{ArmorStand entity;final Type type;final BlockPos block;boolean seen,opened;Corpse(ArmorStand entity,Type type){this.entity=entity;this.type=type;this.block=entity.blockPosition();}Vec3 pos(){return entity.position();}}
    private static final class Loot{final Type type;double profit;boolean complete=true;Loot(Type type){this.type=type;}void add(String name,int amount){String id=ITEM_IDS.get(name);if(id==null){complete=false;return;}if(PRICELESS.contains(id))return;double value=PriceProvider.sellValue(id);if(value<=0){PriceProvider.warm(id);complete=false;}else profit+=value*amount;}}
    private record PendingShare(BlockPos pos,Type type){}
}
