package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// ported from SkyHanni (LGPL-3.0-or-later): features/fishing/ShowFishingItemName.kt
// lifecycle cross-checked with Feesh (Apache-2.0): events/publishers/ItemEntityPublisher.kt
public final class HydraFishedItemNames {
    private record Label(String text,int color,long seenAt) {}
    private static final Map<ItemEntity,Label> LABELS = new HashMap<>();
    private static HydraConfig cfg;
    private static Object levelKey;
    private static boolean initialized,hadBobber,wasActive;
    private static long lastReelAt;

    private HydraFishedItemNames() {}

    public static void init(HydraConfig config) {
        cfg=config;
        if(initialized)return;
        initialized=true;
        ConstellationClient.tick().every(1,"hydra-fished-item-names",HydraFishedItemNames::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }

    private static void tick() {
        Minecraft mc=Minecraft.getInstance();
        boolean active=active();
        if(!active||mc.player==null||mc.level==null){if(wasActive)resetState();wasActive=false;return;}
        wasActive=true;
        if(levelKey!=mc.level){reset();levelKey=mc.level;}
        boolean bobber=mc.player.fishing!=null;
        if(hadBobber&&!bobber)lastReelAt=System.currentTimeMillis();
        hadBobber=bobber;
        long now=System.currentTimeMillis();
        long duration=Math.clamp(cfg.fishedItemDurationMillis,100,5000);
        Iterator<Map.Entry<ItemEntity,Label>> iterator=LABELS.entrySet().iterator();
        while(iterator.hasNext()){var entry=iterator.next();if(!entry.getKey().isAlive()||now-entry.getValue().seenAt>duration)iterator.remove();}
        if(cfg.fishedItemRequireRecentReel&&now-lastReelAt>Math.clamp(cfg.fishedItemReelWindowMillis,250,5000))return;
        double range=Math.clamp(cfg.fishedItemRange,3,40),rangeSqr=range*range;
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof ItemEntity item)||!item.isAlive()||item.distanceToSqr(mc.player)>rangeSqr)continue;
            ItemStack stack=item.getItem();if(stack==null||stack.isEmpty())continue;
            boolean bait=stack.getCount()==1&&isBait(stack);
            if(bait&&!cfg.fishedItemShowBaits)continue;
            String name=clean(stack.getHoverName().getString());if(name.isBlank())continue;
            StringBuilder text=new StringBuilder();
            if(cfg.fishedItemShowPrefix)text.append(bait?"- ":"+ ");
            if(cfg.fishedItemShowAmount&&stack.getCount()!=1)text.append('x').append(stack.getCount()).append(' ');
            text.append(name);
            int itemColor=cfg.fishedItemColor;
            if(cfg.fishedItemUseItemColor&&stack.getHoverName().getStyle().getColor()!=null)itemColor=0xFF000000|stack.getHoverName().getStyle().getColor().getValue();
            LABELS.put(item,new Label(text.toString(),bait?cfg.fishedBaitColor:itemColor,now));
        }
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        Minecraft mc=Minecraft.getInstance();if(!active()||mc.player==null)return;
        double range=Math.clamp(cfg.fishedItemRange,3,40),rangeSqr=range*range;
        long now=System.currentTimeMillis(),duration=Math.clamp(cfg.fishedItemDurationMillis,100,5000);
        double height=Math.clamp(cfg.fishedItemHeightTenths,0,30)/10.0;
        for(var entry:LABELS.entrySet()){
            ItemEntity item=entry.getKey();Label label=entry.getValue();
            if(!item.isAlive()||now-label.seenAt>duration||item.distanceToSqr(mc.player)>rangeSqr)continue;
            ctx.label(item.position().add(0,height,0),label.text,label.color,cfg.fishedItemThroughWalls);
        }
    }

    private static boolean active(){if(cfg==null||!cfg.enabled||!cfg.fishedItemNameSuite||!cfg.fishedItemNames||!ConstellationClient.loc().onHypixel())return false;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;if(!cfg.fishedItemAllowInEnd&&ConstellationClient.loc().area()==SkyblockArea.THE_END)return false;return holdingRod(mc.player.getMainHandItem())||holdingRod(mc.player.getOffhandItem());}
    private static boolean holdingRod(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof FishingRodItem;}
    private static boolean isBait(ItemStack stack){String id=LyraTooltips.marketId(stack).toUpperCase(Locale.ROOT);return id.endsWith("_BAIT")||id.contains("FISH_BAIT")||id.equals("WHALE_BAIT")||id.equals("MINNOW_BAIT");}
    private static String clean(String text){String value=ChatFormatting.stripFormatting(text);return value==null?"":value.trim();}
    private static void reset(){levelKey=null;lastReelAt=0;resetState();}
    private static void resetState(){LABELS.clear();hadBobber=false;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fisheditems").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetState();local("World labels cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(3,40)).executes(c->{cfg.fishedItemRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(100,5000)).executes(c->{cfg.fishedItemDurationMillis=IntegerArgumentType.getInteger(c,"milliseconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reelwindow").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(250,5000)).executes(c->{cfg.fishedItemReelWindowMillis=IntegerArgumentType.getInteger(c,"milliseconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Labels "+on(cfg.fishedItemNames)+", bait "+on(cfg.fishedItemShowBaits)+", strict reel "+on(cfg.fishedItemRequireRecentReel)+", range "+cfg.fishedItemRange+", duration "+cfg.fishedItemDurationMillis+"ms.");return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.fishedItemNames=value;case"bait","baits"->cfg.fishedItemShowBaits=value;case"prefix"->cfg.fishedItemShowPrefix=value;case"amount"->cfg.fishedItemShowAmount=value;case"itemcolor"->cfg.fishedItemUseItemColor=value;case"walls"->cfg.fishedItemThroughWalls=value;case"strict","reel"->cfg.fishedItemRequireRecentReel=value;case"end"->cfg.fishedItemAllowInEnd=value;default->{local("Option must be enabled, bait, prefix, amount, itemcolor, walls, strict, or end.");return 0;}}save();return status();}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Fished Items] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
