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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ported from Feesh (Apache-2.0): features/alerts/FishingBagDisabledAlert.kt
// ported from Feesh (Apache-2.0): utils/BaitUtils.kt
// ported from Feesh (Apache-2.0): features/alerts/BaitAlert.kt
// ported from Feesh (Apache-2.0): features/alerts/NonFishingArmorAlert.kt
// ported from Feesh (Apache-2.0): features/alerts/ChumBucketAutoPickupAlert.kt
public final class HydraFishingSafety {
    private static final String BAG_DISABLED="Use Baits From Bag is now disabled!",BAG_ENABLED="Use Baits From Bag is now enabled!",CHUM_GONE="Automatically picked up the Chum Bucket you left back there!";
    private static final List<String> SPECIAL_ARMOR=List.of("hunter","squid hat","froggles","red sweater");
    private static final List<String> FISHING_STATS=List.of("Sea Creature Chance:","Fishing Speed:","Treasure Chance:","Trophy Chance:");
    private static final Map<String,Long> BAIT_PAIRS=new HashMap<>(),BAIT_LOW=new HashMap<>();
    private static HydraConfig cfg;
    private static boolean initialized,wasEnabled,bagAlerted;
    private static int scanTicks,bagTicks;
    private static long lastSubmerged,lastArmorAlert;
    private static Object level;
    private static String profileKey="";
    private static Boolean pendingBagState;
    private static HydraFishingState.BaitState lastBait;
    private HydraFishingSafety() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(1,"hydra-fishing-safety",HydraFishingSafety::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetWorld());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetWorld());}
    private static void tick(){Minecraft mc=Minecraft.getInstance();boolean enabled=active();if(!enabled||mc.player==null||mc.level==null){if(wasEnabled)resetTransient();wasEnabled=false;return;}wasEnabled=true;String currentProfile=profile();if(level!=mc.level||!currentProfile.equals(profileKey)){level=mc.level;profileKey=currentProfile;resetTransient();}migratePendingBag();boolean submerged=submerged(mc);if(submerged)lastSubmerged=System.currentTimeMillis();if(++bagTicks>=Math.clamp(cfg.fishingBagScanTicks,5,100)){bagTicks=0;readBagScreen(mc);}if(++scanTicks<Math.clamp(cfg.fishingSafetyScanTicks,1,100))return;scanTicks=0;checkBag(mc,submerged);checkArmor(mc,submerged);checkBait(mc);cleanCaches();}

    // ported from Feesh (Apache-2.0): features/alerts/FishingBagDisabledAlert.kt
    private static void onChat(String message){if(!suiteActive())return;if(message.equals(BAG_DISABLED)){setBag(false);return;}if(message.equals(BAG_ENABLED)){setBag(true);return;}if(message.equals(CHUM_GONE)&&cfg.fishingChumPickupAlert)alert("Chum Bucket is gone","Your Chum or Chumcap Bucket was automatically picked up.",cfg.fishingChumPickupTitle,cfg.fishingChumPickupChat,cfg.fishingChumPickupSound,cfg.fishingChumPickupColor);}
    private static void readBagScreen(Minecraft mc){if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!clean(screen.getTitle().getString()).contains("Fishing Bag")||screen.getMenu().slots.size()<=49)return;ItemStack stack=screen.getMenu().getSlot(49).getItem();if(!clean(stack.getHoverName().getString()).equals("Use Baits From Bag"))return;ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return;boolean found=false;for(Component line:lore.lines())if(clean(line.getString()).contains("Click to disable!")){found=true;break;}setBag(found);}
    private static void setBag(boolean enabled){String key=profile();if(key.isEmpty()){pendingBagState=enabled;if(!enabled)bagAlerted=false;return;}pendingBagState=null;storeBag(key,enabled);}
    private static void migratePendingBag(){if(pendingBagState==null||profileKey.isEmpty())return;boolean value=pendingBagState;pendingBagState=null;storeBag(profileKey,value);}
    private static void storeBag(String key,boolean enabled){Boolean old=cfg.fishingBagEnabledProfiles.put(key,enabled);if(!enabled)bagAlerted=false;if(old==null||old!=enabled)ConstellationClient.saveConfig();}
    private static void checkBag(Minecraft mc,boolean submerged){if(!cfg.fishingBagDisabledAlert||bagAlerted||cfg.fishingBagRequireSubmergedHook&&!submerged||!hasRod(mc)||bagScreen(mc))return;Boolean enabled=cfg.fishingBagEnabledProfiles.get(profile());if(enabled==null||enabled)return;bagAlerted=true;Component detail=Component.literal("\u00a7fUsing baits from Fishing Bag is disabled.");if(cfg.fishingBagClickableOpen)detail=detail.copy().append(Component.literal(" \u00a7e[Open Fishing Bag]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/fb")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open Fishing Bag")))));alert("Enable fishing bag!",detail,cfg.fishingBagAlertTitle,cfg.fishingBagAlertChat,cfg.fishingBagAlertSound,cfg.fishingBagAlertColor);}

    // ported from Feesh (Apache-2.0): features/alerts/NonFishingArmorAlert.kt
    private static void checkArmor(Minecraft mc,boolean submerged){if(!cfg.fishingArmorAlert||cfg.fishingArmorRequireRod&&!hasRod(mc)||cfg.fishingArmorRequireSubmergedHook&&!submerged||fishingPieces(mc)>=Math.clamp(cfg.fishingArmorMinimumPieces,1,4))return;long now=System.currentTimeMillis();if(now-lastArmorAlert<Math.clamp(cfg.fishingArmorCooldownSeconds,1,120)*1000L)return;lastArmorAlert=now;alert("Equip fishing armor!","Fewer than "+Math.clamp(cfg.fishingArmorMinimumPieces,1,4)+" fishing armor pieces are equipped.",cfg.fishingArmorAlertTitle,cfg.fishingArmorAlertChat,cfg.fishingArmorAlertSound,cfg.fishingArmorAlertColor);}
    private static int fishingPieces(Minecraft mc){int count=0;for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))if(fishingArmor(mc.player.getItemBySlot(slot)))count++;return count;}
    private static boolean fishingArmor(ItemStack stack){if(stack==null||stack.isEmpty())return false;String name=clean(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);if(SPECIAL_ARMOR.stream().anyMatch(name::contains))return true;ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return false;for(Component component:lore.lines()){String line=clean(component.getString());if(FISHING_STATS.stream().anyMatch(line::startsWith))return true;}return false;}

    // ported from Feesh (Apache-2.0): utils/BaitUtils.kt
    // ported from Feesh (Apache-2.0): features/alerts/BaitAlert.kt
    private static void checkBait(Minecraft mc){HydraFishingState.BaitState current=HydraFishingState.bait();if(current.id().isEmpty())return;if(lastBait==null||lastBait.id().isEmpty()){lastBait=current;return;}if(lastBait.id().equals(current.id())&&current.amount()==lastBait.amount()+1)return;long now=System.currentTimeMillis(),recent=Math.clamp(cfg.fishingBaitRecentHookMinutes,1,30)*60_000L;boolean context=(!cfg.fishingBaitAlertsRequireRecentHook||now-lastSubmerged<=recent)&&(!cfg.fishingBaitAlertsIgnoreBagScreen||!bagScreen(mc));boolean changed=!lastBait.id().equals(current.id());if(context&&cfg.fishingBaitChangeWarning&&changed){String pair=lastBait.id().compareTo(current.id())<=0?lastBait.id()+"|"+current.id():current.id()+"|"+lastBait.id();long cooldown=Math.clamp(cfg.fishingBaitChangeCooldownSeconds,1,600)*1000L;if(now-BAIT_PAIRS.getOrDefault(pair,0L)>=cooldown){BAIT_PAIRS.put(pair,now);alert("Bait changed",lastBait.name()+" -> "+current.name(),cfg.fishingBaitWarningTitle&&cfg.fishingBaitChangeTitle,cfg.fishingBaitWarningChat&&cfg.fishingBaitChangeChat,cfg.fishingBaitWarningSound&&cfg.fishingBaitChangeSound,cfg.fishingBaitAlertColor);}}if(lastBait.id().equals(current.id())&&current.amount()>lastBait.amount()+1||current.amount()>cfg.fishingBaitLowThreshold)BAIT_LOW.remove(current.id());if(context&&cfg.fishingBaitLowWarning&&current.amount()>0&&current.amount()<=Math.max(1,cfg.fishingBaitLowThreshold)){long cooldown=Math.clamp(cfg.fishingBaitLowCooldownSeconds,1,900)*1000L;if(now-BAIT_LOW.getOrDefault(current.id(),0L)>=cooldown){BAIT_LOW.put(current.id(),now);baitLow(current);}}lastBait=current;}
    private static void baitLow(HydraFishingState.BaitState bait){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;alert("Out of bait soon","You are almost out of "+bait.name()+".",cfg.fishingBaitWarningTitle&&cfg.fishingBaitLowTitle,cfg.fishingBaitWarningChat&&cfg.fishingBaitLowChat,cfg.fishingBaitWarningSound&&cfg.fishingBaitLowSound,cfg.fishingBaitAlertColor);if((cfg.fishingBaitClickableRecipe||cfg.fishingBaitClickableBazaar)&&!bait.name().contains("Obfuscated")){Component actions=Component.empty();if(cfg.fishingBaitClickableRecipe)actions=actions.copy().append(Component.literal("\u00a7e[Supercraft]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/recipe "+bait.name())).withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the recipe for "+bait.name())))));if(cfg.fishingBaitClickableBazaar)actions=actions.copy().append(Component.literal(cfg.fishingBaitClickableRecipe?" \u00a77or \u00a76[Buy on BZ]":"\u00a76[Buy on BZ]").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/bz "+bait.name())).withHoverEvent(new HoverEvent.ShowText(Component.literal("Open Bazaar for "+bait.name())))));mc.player.sendSystemMessage(actions);}}
    private static void cleanCaches(){long now=System.currentTimeMillis();BAIT_PAIRS.entrySet().removeIf(e->now-e.getValue()>=Math.clamp(cfg.fishingBaitChangeCooldownSeconds,1,600)*1000L);BAIT_LOW.entrySet().removeIf(e->now-e.getValue()>=Math.clamp(cfg.fishingBaitLowCooldownSeconds,1,900)*1000L);}

    private static void alert(String title,String detail,boolean showTitle,boolean chat,boolean sound,int color){alert(title,Component.literal(detail),showTitle,chat,sound,color);}
    private static void alert(String title,Component detail,boolean showTitle,boolean chat,boolean sound,int color){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(showTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(title).withColor(color&0xFFFFFF));}if(chat)mc.player.sendSystemMessage(Component.literal("\u00a73[Fishing Safety] ").append(detail));if(sound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,.9f);}
    private static boolean submerged(Minecraft mc){return mc.player!=null&&mc.player.fishing!=null&&(mc.player.fishing.isInWater()||mc.player.fishing.isInLava());}
    private static boolean hasRod(Minecraft mc){if(mc.player==null)return false;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).getItem() instanceof FishingRodItem)return true;return false;}
    private static boolean bagScreen(Minecraft mc){return mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&clean(screen.getTitle().getString()).contains("Fishing Bag");}
    private static boolean fishingArea(){return switch(ConstellationClient.loc().area()){case UNKNOWN,THE_RIFT,GARDEN,KUUDRA,CATACOMBS,MASTER_MODE,DUNGEON_HUB,THE_END,GLACITE_MINESHAFT->false;default->true;};}
    private static boolean suiteActive(){return cfg!=null&&cfg.enabled&&cfg.fishingSafetySuite&&ConstellationClient.loc().onHypixel();}
    private static boolean active(){return suiteActive()&&fishingArea();}
    private static String profile(){String key=LyraStorageValue.currentProfileKey();return key==null?"":key.trim();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void resetWorld(){level=null;profileKey="";pendingBagState=null;wasEnabled=false;resetTransient();}
    private static void resetTransient(){scanTicks=0;bagTicks=0;bagAlerted=false;lastSubmerged=0;lastArmorAlert=0;lastBait=null;BAIT_PAIRS.clear();BAIT_LOW.clear();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fishingsafety").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bagstate").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->bagState(StringArgumentType.getString(c,"state"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("armorpieces").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,4)).executes(c->{cfg.fishingArmorMinimumPieces=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){Boolean bag=cfg.fishingBagEnabledProfiles.get(profile());local("Fishing Bag "+(bag==null?"unknown":bag?"enabled":"disabled")+", armor alert "+on(cfg.fishingArmorAlert)+", bait change "+on(cfg.fishingBaitChangeWarning)+", low bait "+on(cfg.fishingBaitLowWarning)+".");return 1;}
    private static int bagState(String raw){String key=profile();if(key.isEmpty()){local("Fishing profile is not available yet.");return 0;}if(raw.equalsIgnoreCase("unknown")){cfg.fishingBagEnabledProfiles.remove(key);save();return status();}Boolean value=parse(raw);if(value==null){local("Bag state must be enabled, disabled, or unknown.");return 0;}setBag(value);return status();}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"bag"->cfg.fishingBagDisabledAlert=value;case"armor"->cfg.fishingArmorAlert=value;case"baitchange"->cfg.fishingBaitChangeWarning=value;case"baitlow"->cfg.fishingBaitLowWarning=value;case"chum"->cfg.fishingChumPickupAlert=value;case"recipe"->cfg.fishingBaitClickableRecipe=value;case"bazaar"->cfg.fishingBaitClickableBazaar=value;case"recenthook"->cfg.fishingBaitAlertsRequireRecentHook=value;default->{local("Option must be bag, armor, baitchange, baitlow, chum, recipe, bazaar, or recenthook.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1","enabled"->true;case"off","false","no","0","disabled"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a73[Fishing Safety] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
