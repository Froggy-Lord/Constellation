package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/motes/MotesSession.kt, ShowMotesNpcSellPrice.kt, RiftMotesOrb.kt
// McGrubber detection ported from Skyblocker (LGPL-3.0-or-later): skyblock/rift/McGrubberUpdater.java
public final class AndromedaMotes {
    private record Price(String name,double base){}
    private static final class Orb{Vec3 pos;int count;long started,last;boolean valid,picked;Orb(Vec3 pos,long now){this.pos=pos;started=last=now;}}
    private static final Pattern LIFETIME=Pattern.compile("\\s*Lifetime Motes:\\s*([\\d,.]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern BURGERS=Pattern.compile(".*You have (\\d+) Grubber Stacks.*",Pattern.CASE_INSENSITIVE);
    private static final Pattern PICKUP=Pattern.compile("ORB!\\s*Picked up\\s*\\+.* Motes.*",Pattern.CASE_INSENSITIVE);
    private static final Pattern ORB_MOTES=Pattern.compile("ORB! Picked up \\+([\\d,]+) Motes, recovered \\+2ф Rift Time!",Pattern.CASE_INSENSITIVE);
    private static final Pattern MOTES_PRICE=Pattern.compile("([\\d,.]+) Motes",Pattern.CASE_INSENSITIVE);
    private static final Pattern BURGER_PROGRESS=Pattern.compile("Total Progress:\\s*(\\d+)%",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Price> PRICES=new LinkedHashMap<>();
    private static final List<Orb> ORBS=new ArrayList<>();
    private static AndromedaConfig cfg;private static boolean initialized,wasActive;private static long initial=-1,lifetime=-1,enteredAt,storageValue;private static int storageStacks,storageItems;private static String storageSignature="",burgerSignature="",burgerSource="manual";
    private AndromedaMotes(){}

    public static void init(AndromedaConfig config){
        cfg=config;loadPrices();if(initialized)return;initialized=true;
        ConstellationClient.tick().every(5,"andromeda-motes",AndromedaMotes::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ItemTooltipCallback.EVENT.register((stack,context,flags,lines)->appendTooltip(stack,lines));
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void loadPrices(){
        if(!PRICES.isEmpty())return;
        // data ported from Hypixel public SkyBlock item resources: v2/resources/skyblock/items
        try(var stream=AndromedaMotes.class.getResourceAsStream("/assets/constellation/rift/motes_prices.json")){
            if(stream==null)throw new IllegalStateException("missing Motes prices");
            JsonObject items=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
            for(var entry:items.entrySet()){JsonObject item=entry.getValue().getAsJsonObject();PRICES.put(entry.getKey(),new Price(item.get("name").getAsString(),item.get("price").getAsDouble()));}
            ConstellationClient.LOGGER.info("loaded {} Rift Motes NPC prices",PRICES.size());
        }catch(Exception e){ConstellationClient.LOGGER.error("failed to load Rift Motes prices",e);}
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static void tick(){
        detectBurgerMenu();
        boolean active=active();
        if(!active){if(wasActive)finishVisit();wasActive=false;clearOrbs();clearStorage();return;}
        wasActive=true;if(cfg.motesSessionTracking)readLifetime();readStorage();expireOrbs();
    }
    private static void readLifetime(){
        long amount=-1;for(String line:com.froggylord.constellation.data.TabList.lines()){Matcher matcher=LIFETIME.matcher(clean(line));if(matcher.matches()){amount=number(matcher.group(1));break;}}
        if(amount<0)return;if(initial<0){initial=amount;enteredAt=System.currentTimeMillis();}lifetime=amount;
    }
    private static void finishVisit(){
        if(cfg.motesSessionSummary&&initial>=0&&lifetime>initial){long gained=lifetime-initial,elapsed=Math.max(1,System.currentTimeMillis()-enteredAt),rate=gained*3_600_000L/elapsed;if(gained>=Math.clamp(cfg.motesSessionSummaryMinimum,1,1_000_000_000))local("Gained "+format(gained)+" Motes in "+duration(elapsed)+" ("+format(rate)+"/h).");}
        initial=-1;lifetime=-1;enteredAt=0;
    }
    private static void onChat(String message){
        if(!active())return;Matcher burger=BURGERS.matcher(message);if(cfg.motesLearnBurgerStacks&&cfg.motesBurgerDetectConsumptionChat&&burger.matches())learnBurger(Integer.parseInt(burger.group(1)),"consumption");
        Matcher orbMotes=ORB_MOTES.matcher(message);if(cfg.motesLearnBurgerStacks&&cfg.motesBurgerDetectOrbPickup&&orbMotes.matches()){long motes=number(orbMotes.group(1));if(motes>=5&&(motes-5)%60==0)learnBurger((int)((motes-5)/60),"orb pickup");}
        if(PICKUP.matcher(message).matches()&&!ORBS.isEmpty()){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)ORBS.stream().min(Comparator.comparingDouble(orb->orb.pos.distanceToSqr(mc.player.position()))).ifPresent(orb->orb.picked=true);}
    }
    private static void detectBurgerMenu(){
        if(cfg==null||!cfg.enabled||!cfg.motesLearnBurgerStacks||!ConstellationClient.loc().onHypixel())return;Minecraft mc=Minecraft.getInstance();if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||mc.player==null){burgerSignature="";return;}String title=clean(screen.getTitle().getString());boolean grubber=active()&&cfg.motesBurgerDetectGrubberMenu&&title.equals("Motes Grubber"),consumables=!active()&&cfg.motesBurgerDetectConsumablesMenu&&title.equals("Miscellaneous ➜ Consumable Items");if(!grubber&&!consumables){burgerSignature="";return;}String signature=title+screen.getMenu().slots.stream().map(slot->LyraTooltips.marketId(slot.getItem())+":"+lore(slot.getItem())).toList();if(signature.equals(burgerSignature))return;burgerSignature=signature;
        if(grubber)for(Slot slot:screen.getMenu().slots){if(slot.container!=mc.player.getInventory())continue;ItemStack stack=slot.getItem();Price base=PRICES.get(LyraTooltips.marketId(stack));if(base==null||base.base<=0)continue;Matcher price=find(lore(stack),MOTES_PRICE);if(price==null)continue;double shown=decimal(price.group(1)),raw=20*shown/base.base-20;int stacks=(int)Math.round(raw);if(stacks>=0&&stacks<=5&&Math.abs(raw-stacks)<.15){learnBurger(stacks,"Motes Grubber");return;}}
        if(consumables)for(Slot slot:screen.getMenu().slots){ItemStack stack=slot.getItem();if(!LyraTooltips.marketId(stack).equals("MCGRUBBER_BURGER"))continue;Matcher progress=find(lore(stack),BURGER_PROGRESS);if(progress!=null){learnBurger(Math.clamp(Integer.parseInt(progress.group(1))/20,0,5),"Consumable Items");return;}}
    }
    private static void learnBurger(int value,String source){value=Math.clamp(value,0,5);burgerSource=source;if(cfg.motesBurgerStacks==value)return;cfg.motesBurgerStacks=value;save();if(cfg.motesBurgerLearnChat)local("McGrubber stacks set to "+value+" from "+source+".");}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack==null?null:stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(Component::getString).map(AndromedaMotes::clean).toList();}
    private static Matcher find(List<String> lines,Pattern pattern){for(String line:lines){Matcher matcher=pattern.matcher(line);if(matcher.find())return matcher;}return null;}
    private static double decimal(String value){try{return Double.parseDouble(value.replace(",",""));}catch(Exception ignored){return-1;}}

    public static boolean onParticle(ClientboundLevelParticlesPacket packet){
        if(!active()||!cfg.motesOrbEnabled||packet.getParticle().getType()!=ParticleTypes.ENTITY_EFFECT)return false;
        long now=System.currentTimeMillis();Vec3 location=new Vec3(packet.getX()-.5,packet.getY(),packet.getZ()-.5);Orb orb=ORBS.stream().filter(current->current.pos.distanceTo(location)<Math.clamp(cfg.motesOrbGroupRangeTenths,10,60)/10.0).min(Comparator.comparingDouble(current->current.pos.distanceToSqr(location))).orElse(null);
        if(orb==null){orb=new Orb(location,now);ORBS.add(orb);}orb.pos=location;orb.last=now;orb.count++;orb.picked=false;validate(orb,now);return cfg.motesOrbHideParticles&&orb.valid;
    }
    private static void validate(Orb orb,long now){long age=now-orb.started;if(age<Math.clamp(cfg.motesOrbValidationMillis,250,2000))return;double rate=orb.count*1000.0/age;orb.valid=rate>=Math.clamp(cfg.motesOrbMinParticlesPerSecond,20,120)&&rate<=Math.clamp(cfg.motesOrbMaxParticlesPerSecond,30,180);}
    private static void expireOrbs(){long now=System.currentTimeMillis();for(Orb orb:ORBS){validate(orb,now);if(orb.valid&&now-orb.last>Math.clamp(cfg.motesOrbPickupDelayMillis,100,1000))orb.picked=true;}ORBS.removeIf(orb->now-orb.last>Math.clamp(cfg.motesOrbExpiryMillis,500,5000));}
    private static void clearOrbs(){ORBS.clear();}
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!cfg.motesOrbEnabled)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;double range=Math.clamp(cfg.motesOrbRange,5,100),rangeSq=range*range,size=.5+(Math.clamp(cfg.motesOrbSize,1,5)-5)*.1;
        for(Orb orb:ORBS){if(!orb.valid||orb.pos.distanceToSqr(mc.player.position())>rangeSq)continue;Vec3 center=orb.pos.add(0,.5,0);int color=orb.picked?cfg.motesOrbPickedColor:cfg.motesOrbActiveColor;AABB box=new AABB(center.x-size,center.y-size,center.z-size,center.x+size,center.y+size,center.z+size);if(cfg.motesOrbBox)ctx.highlight(box,color,cfg.motesOrbThroughWalls);if(cfg.motesOrbBeam)ctx.beam(center.x,center.y,center.z,color,Math.clamp(cfg.motesOrbBeamHeight,2,50),cfg.motesOrbThroughWalls);if(cfg.motesOrbLine)ctx.line(mc.player.position().add(0,1,0),center,color,cfg.motesOrbThroughWalls);if(cfg.motesOrbLabel){String label=orb.picked?"Motes Orb · Picked up":"Motes Orb";if(cfg.motesOrbDistance)label+=" · "+Math.round(Math.sqrt(orb.pos.distanceToSqr(mc.player.position())))+"m";ctx.label(center.add(0,size+.5,0),label,color,cfg.motesOrbThroughWalls);}}
    }

    private static void appendTooltip(ItemStack stack,List<Component> lines){
        if(!active()||!cfg.motesNpcTooltip)return;long value=value(stack);if(value<0)return;String text="NPC Motes: §d"+format(value);if(cfg.motesTooltipBurgerStacks&&cfg.motesBurgerStacks>0)text+=" §7("+cfg.motesBurgerStacks+" McGrubber)";if(cfg.motesTooltipStackBreakdown&&stack.getCount()>1)text+=" §8("+stack.getCount()+"x "+format(value/stack.getCount())+")";lines.add(Component.literal(text));
    }
    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||!cfg.motesStorageSlotMarkers||!storageScreen(screen)||slot==null)return;long value=value(slot.getItem());if(value<Math.clamp(cfg.motesStorageHighlightMinimum,0,1_000_000_000))return;int color=cfg.motesStorageHighlightColor|0xFF000000;graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.text(Minecraft.getInstance().font,"M",slot.x+10,slot.y+1,color,true);
    }
    private static void readStorage(){
        Minecraft mc=Minecraft.getInstance();if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!storageScreen(screen)){clearStorage();return;}String signature=screen.getMenu().slots.stream().limit(Math.max(0,screen.getMenu().slots.size()-36)).map(slot->LyraTooltips.marketId(slot.getItem())+":"+slot.getItem().getCount()).toList().toString();if(signature.equals(storageSignature))return;storageSignature=signature;long total=0;int stacks=0,items=0;int size=Math.max(0,screen.getMenu().slots.size()-36);for(int i=0;i<size;i++){ItemStack stack=screen.getMenu().getSlot(i).getItem();long value=value(stack);if(value<0)continue;total+=value;stacks++;items+=stack.getCount();}storageValue=total;storageStacks=stacks;storageItems=items;
    }
    private static boolean storageScreen(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString()).contains("Rift Storage");}
    private static void clearStorage(){storageSignature="";storageValue=0;storageStacks=0;storageItems=0;}
    private static long value(ItemStack stack){
        if(stack==null||stack.isEmpty())return-1;CustomData data=stack.get(DataComponents.CUSTOM_DATA);CompoundTag root=data==null?new CompoundTag():data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;if(extra.getBooleanOr("rift_transferred",false))return-1;Price price=PRICES.get(LyraTooltips.marketId(stack));if(price==null)return-1;double bonus=1+Math.clamp(cfg.motesBurgerStacks,0,5)*.05;return Math.round(price.base*stack.getCount()*bonus);
    }
    public static String hudLifetime(){return cfg.motesHudLifetime&&lifetime>=0?format(lifetime):null;}
    public static String hudGained(){long gained=gained();return cfg.motesHudGained&&cfg.riftHudMotesSession&&gained>=0?format(gained):null;}
    public static String hudRate(){long rate=rate();return cfg.motesHudRate&&rate>=0?format(rate)+"/h":null;}
    public static String hudDuration(){return cfg.motesHudDuration&&enteredAt>0?duration(System.currentTimeMillis()-enteredAt):null;}
    public static String hudStorage(){return cfg.motesStorageValue&&storageSignature.length()>0?format(storageValue):null;}
    public static String hudStorageItems(){return cfg.motesStorageValue&&cfg.motesStorageItemCount&&storageSignature.length()>0?storageItems+" items · "+storageStacks+" stacks":null;}
    public static boolean storageVisible(){return active()&&cfg.motesStorageValue&&!storageSignature.isEmpty();}
    private static long gained(){return initial>=0&&lifetime>=initial?lifetime-initial:-1;}
    private static long rate(){long gained=gained(),elapsed=enteredAt<=0?0:System.currentTimeMillis()-enteredAt;return gained<0||elapsed<1000?-1:gained*3_600_000L/elapsed;}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("riftmotes").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{initial=lifetime>=0?lifetime:-1;enteredAt=System.currentTimeMillis();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("burgers").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("stacks",IntegerArgumentType.integer(0,5)).executes(c->{cfg.motesBurgerStacks=IntegerArgumentType.getInteger(c,"stacks");burgerSource="manual";save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->numberOption(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Lifetime "+(lifetime<0?"unknown":format(lifetime))+", gained "+(gained()<0?"unknown":format(gained()))+", rate "+(rate()<0?"unknown":format(rate())+"/h")+", prices "+PRICES.size()+", orbs "+ORBS.stream().filter(orb->orb.valid).count()+", McGrubber "+cfg.motesBurgerStacks+" ("+burgerSource+").");return 1;}
    private static int numberOption(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"summaryminimum"->cfg.motesSessionSummaryMinimum=Math.clamp(value,1,1_000_000_000);case"highlightminimum"->cfg.motesStorageHighlightMinimum=Math.clamp(value,0,1_000_000_000);case"orbrange"->cfg.motesOrbRange=Math.clamp(value,5,100);case"orbsize"->cfg.motesOrbSize=Math.clamp(value,1,5);case"validation"->cfg.motesOrbValidationMillis=Math.clamp(value,250,2000);case"expiry"->cfg.motesOrbExpiryMillis=Math.clamp(value,500,5000);case"pickupdelay"->cfg.motesOrbPickupDelayMillis=Math.clamp(value,100,1000);case"minpps"->cfg.motesOrbMinParticlesPerSecond=Math.clamp(value,20,120);case"maxpps"->cfg.motesOrbMaxParticlesPerSecond=Math.clamp(value,30,180);case"groupsize"->cfg.motesOrbGroupRangeTenths=Math.clamp(value,10,60);case"beamheight"->cfg.motesOrbBeamHeight=Math.clamp(value,2,50);default->{local("Unknown Motes number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"tracking"->cfg.motesSessionTracking=value;case"summary"->cfg.motesSessionSummary=value;case"lifetime"->cfg.motesHudLifetime=value;case"gained"->cfg.motesHudGained=value;case"rate"->cfg.motesHudRate=value;case"duration"->cfg.motesHudDuration=value;case"tooltip"->cfg.motesNpcTooltip=value;case"stackbreakdown"->cfg.motesTooltipStackBreakdown=value;case"burgertext"->cfg.motesTooltipBurgerStacks=value;case"learnburgers"->cfg.motesLearnBurgerStacks=value;case"burgerchat"->cfg.motesBurgerLearnChat=value;case"grubbermenu"->cfg.motesBurgerDetectGrubberMenu=value;case"consumables"->cfg.motesBurgerDetectConsumablesMenu=value;case"orblearn"->cfg.motesBurgerDetectOrbPickup=value;case"consumption"->cfg.motesBurgerDetectConsumptionChat=value;case"storage"->cfg.motesStorageValue=value;case"storageitems"->cfg.motesStorageItemCount=value;case"storagemarkers"->cfg.motesStorageSlotMarkers=value;case"orbs"->cfg.motesOrbEnabled=value;case"hideparticles"->cfg.motesOrbHideParticles=value;case"orbbox"->cfg.motesOrbBox=value;case"orbbeam"->cfg.motesOrbBeam=value;case"orbline"->cfg.motesOrbLine=value;case"orblabel"->cfg.motesOrbLabel=value;case"orbdistance"->cfg.motesOrbDistance=value;case"orbwalls"->cfg.motesOrbThroughWalls=value;default->{local("Unknown Motes option.");return 0;}}save();return status();}
    private static int color(String target,String raw){try{String value=raw.trim().replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;switch(target.toLowerCase(Locale.ROOT)){case"active"->cfg.motesOrbActiveColor=(int)parsed;case"picked"->cfg.motesOrbPickedColor=(int)parsed;case"storage"->cfg.motesStorageHighlightColor=(int)parsed;default->{local("Color target must be active, picked, or storage.");return 0;}}save();return status();}catch(Exception e){local("Color must be ARGB hex, such as FFFF55FF.");return 0;}}
    private static void reset(){wasActive=false;initial=-1;lifetime=-1;enteredAt=0;burgerSignature="";burgerSource="manual";clearOrbs();clearStorage();}
    private static void save(){ConstellationClient.saveConfig();}
    private static long number(String value){try{return Long.parseLong(value.replace(",","").replace(".",""));}catch(Exception ignored){return-1;}}
    private static String format(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String duration(long millis){long seconds=Math.max(0,millis/1000);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Motes] §f"+text));}
}
