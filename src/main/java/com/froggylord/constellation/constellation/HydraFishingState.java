package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/{FishingApi,FishingHookDisplay,FishingBobberTimer,FishingBaitDisplay,FishingBaitWarnings}.kt
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/fishing/FishingHookDisplayHelper.java
public final class HydraFishingState {
    public record HookState(String hookText,boolean ready,long ageMillis,String liquid,double distance) {}
    public record BaitState(String id,String name,int amount) {}
    private static final Pattern HOOK=Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern BAIT=Pattern.compile("Bait Remaining: ([\\d,]+)");
    private static HydraConfig cfg;
    private static boolean initialized,hadBobber,wasHoldingRod,lowWarned;
    private static long castAt,liquidAt,lastRodAt;
    private static ArmorStand hookLabel;
    private static BaitState bait=new BaitState("","No Bait",0),previousBait;
    private static String profileKey="";
    private HydraFishingState() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-fishing-state",HydraFishingState::tick);ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());}

    private static void tick(){Minecraft mc=Minecraft.getInstance();String current=LyraStorageValue.currentProfileKey();if(!current.equals(profileKey)){profileKey=current;resetState();}if(!active()||mc.player==null||mc.level==null){resetState();return;}boolean holding=holdingRod(mc.player.getMainHandItem())||holdingRod(mc.player.getOffhandItem());if(holding)lastRodAt=System.currentTimeMillis();if(holding&&!wasHoldingRod)updateBait(mc,false);if(!holding&&wasHoldingRod){previousBait=null;lowWarned=false;}wasHoldingRod=holding;if(!holding){hadBobber=false;hookLabel=null;castAt=0;liquidAt=0;return;}
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen instanceof InventoryScreen)updateBait(mc,true);boolean bobber=mc.player.fishing!=null;if(bobber&&!hadBobber){castAt=System.currentTimeMillis();liquidAt=0;hookLabel=null;if(cfg.baitWarningsHud&&cfg.fishingBaitNoBaitWarning&&bait.amount==0)warning("No bait is used!","You are not using any fishing bait.");}
        if(!bobber&&hadBobber){castAt=0;liquidAt=0;hookLabel=null;}hadBobber=bobber;if(!bobber)return;var rod=mc.player.fishing;if(liquidAt==0&&(rod.isInWater()||rod.isInLava()))liquidAt=System.currentTimeMillis();resolveLabel(mc,rod);
    }

    private static void resolveLabel(Minecraft mc,Entity rod){if(hookLabel!=null&&hookLabel.isAlive()&&hookLabel.distanceToSqr(rod)<=0.01&&validLabel(hookLabel))return;hookLabel=null;double best=0.01;for(Entity entity:mc.level.entitiesForRendering()){if(!(entity instanceof ArmorStand stand)||!validLabel(stand))continue;double distance=stand.distanceToSqr(rod);if(distance<=best){best=distance;hookLabel=stand;}}}
    private static boolean validLabel(ArmorStand stand){if(!stand.isAlive()||!stand.hasCustomName())return false;String name=clean(stand.getName().getString());return name.equals("!!!")||HOOK.matcher(name).matches();}

    private static void updateBait(Minecraft mc,boolean warn){ItemStack stack=mc.player.getInventory().getItem(8);BaitState next=readBait(stack);if(next.equals(bait))return;previousBait=bait;boolean changed=!Objects.equals(bait.id,next.id);bait=next;if(changed||next.amount>cfg.fishingBaitLowThreshold)lowWarned=false;}
    private static BaitState readBait(ItemStack stack){if(stack==null||stack.isEmpty())return new BaitState("","No Bait",0);ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return new BaitState("","No Bait",0);for(Component line:lore.lines()){Matcher matcher=BAIT.matcher(clean(line.getString()));if(!matcher.find())continue;try{int amount=Integer.parseInt(matcher.group(1).replace(",",""));String id=LyraTooltips.marketId(stack);String name=clean(stack.getHoverName().getString());return new BaitState(id,name.isBlank()?"Fishing Bait":name,Math.max(0,amount));}catch(NumberFormatException ignored){return new BaitState("","No Bait",0);}}return new BaitState("","No Bait",0);}

    private static void warning(String title,String chat){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.fishingBaitWarningTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(title).withColor(cfg.fishingBaitEmptyColor&0xFFFFFF));}if(cfg.fishingBaitWarningChat)local(chat);if(cfg.fishingBaitWarningSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),0.7f,0.7f);}
    public static HookState state(){Minecraft mc=Minecraft.getInstance();if(!hookVisible()||mc.player==null)return null;long now=System.currentTimeMillis(),start=cfg.fishingHookAgeStartsInLiquid?liquidAt:castAt;String text=hookLabel==null?"Waiting":clean(hookLabel.getName().getString());boolean ready=text.equals("!!!");String liquid=mc.player.fishing.isInLava()?"Lava":mc.player.fishing.isInWater()?"Water":"Air";return new HookState(text,ready,start<=0?0:now-start,liquid,mc.player.distanceTo(mc.player.fishing));}
    public static BaitState bait(){return bait;}
    public static boolean hookVisible(){Minecraft mc=Minecraft.getInstance();return active()&&cfg.fishingRodTimerHud&&cfg.fishingHookDisplay&&mc.player!=null&&mc.player.fishing!=null&&(hookLabel!=null||cfg.fishingHookShowBobberAge);}
    public static boolean baitVisible(){return active()&&cfg.baitDisplay&&cfg.fishingBaitDisplay&&System.currentTimeMillis()-lastRodAt<=Math.max(1,cfg.fishingStateRecentSeconds)*1000L;}
    public static boolean shouldHideHookLabel(Entity entity){return cfg!=null&&cfg.enabled&&cfg.fishingStateSuite&&cfg.fishingRodTimerHud&&cfg.fishingHookDisplay&&cfg.fishingHookHideWorldLabel&&entity==hookLabel&&hookVisible();}
    public static HydraConfig config(){return cfg;}
    private static boolean holdingRod(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof FishingRodItem;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.fishingStateSuite&&ConstellationClient.loc().onHypixel()&&ConstellationClient.loc().area()!=com.froggylord.constellation.core.LocationManager.SkyblockArea.KUUDRA;}
    private static String clean(String value){String out=ChatFormatting.stripFormatting(value);return out==null?"":out.trim();}
    private static void reset(){profileKey="";resetState();}
    private static void resetState(){bait=new BaitState("","No Bait",0);previousBait=null;wasHoldingRod=false;lastRodAt=0;lowWarned=false;resetTransient();}
    private static void resetTransient(){hadBobber=false;hookLabel=null;castAt=0;liquidAt=0;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fishingstate")
            .executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("low")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,1000))
                    .executes(c->{cfg.fishingBaitLowThreshold=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("readytext")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString())
                    .executes(c->{cfg.fishingHookReadyText=StringArgumentType.getString(c,"text");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Hook display "+on(cfg.fishingHookDisplay)+", bait display "+on(cfg.fishingBaitDisplay)+", no-bait warning "+on(cfg.fishingBaitNoBaitWarning)+", low threshold "+cfg.fishingBaitLowThreshold+".");return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"hook"->cfg.fishingHookDisplay=value;case"bait"->cfg.fishingBaitDisplay=value;case"nobait"->cfg.fishingBaitNoBaitWarning=value;case"change"->cfg.fishingBaitChangeWarning=value;case"low"->cfg.fishingBaitLowWarning=value;case"age"->cfg.fishingHookShowBobberAge=value;case"hideworld"->cfg.fishingHookHideWorldLabel=value;default->{local("Option must be hook, bait, nobait, change, low, age, or hideworld.");return 0;}}save();return status();}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Fishing] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
