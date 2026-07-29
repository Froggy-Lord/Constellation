package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/TreeProgressDisplay.kt
// ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/TreeBreakProgressHud.java
public final class ArtemisTreeProgress {
    public record State(String type,int percent,double distance,boolean own,int contributors,long updatedAt){}
    private static final Pattern TREE=Pattern.compile("^(FIG|MANGROVE) TREE (\\d{1,3})%$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTRIBUTORS=Pattern.compile("(\\d+) players?",Pattern.CASE_INSENSITIVE);
    private static ArtemisConfig cfg;
    private static State state;
    private static Object levelIdentity;
    private static boolean initialized;
    private static UUID alertedTree;

    private ArtemisTreeProgress(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
        ConstellationClient.tick().every(1,"artemis-tree-progress",ArtemisTreeProgress::tick);
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity){levelIdentity=mc.level;clear();}
        if(!active()||mc.player==null||mc.level==null){clear();return;}
        if(cfg.treeProgressOnlyHoldingAxe&&!isAxe(mc.player.getMainHandItem())){hide();return;}
        double range=Math.clamp(cfg.treeProgressScanRange,16,256),range2=range*range;
        List<ArmorStand> stands=new ArrayList<>(),trees=new ArrayList<>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand stand&&stand.hasCustomName()&&stand.distanceToSqr(mc.player)<=range2){
            stands.add(stand);if(TREE.matcher(clean(stand.getName().getString())).matches())trees.add(stand);
        }
        ArmorStand selected=trees.stream().filter(tree->!cfg.treeProgressOnlyOwn||ownership(tree,stands,mc.player.getName().getString()).own)
            .min(Comparator.comparingDouble(tree->tree.distanceToSqr(mc.player))).orElse(null);
        if(selected==null){hide();return;}
        Matcher matcher=TREE.matcher(clean(selected.getName().getString()));if(!matcher.matches()){hide();return;}
        Ownership ownership=ownership(selected,stands,mc.player.getName().getString());
        int percent=Math.clamp(number(matcher.group(2)),0,100);
        state=new State(title(matcher.group(1))+" Tree",percent,Math.sqrt(selected.distanceToSqr(mc.player)),ownership.own,ownership.contributors,System.currentTimeMillis());
        int threshold=Math.clamp(cfg.treeProgressAlertPercent,1,100);
        if(percent>=threshold&&cfg.treeProgressCompletionAlert){
            if(!selected.getUUID().equals(alertedTree))alert();
            alertedTree=selected.getUUID();
        }else alertedTree=null;
    }

    private record Ownership(boolean own,int contributors){}
    private static Ownership ownership(ArmorStand tree,List<ArmorStand> stands,String player){
        boolean own=false;int contributors=0;
        for(ArmorStand stand:stands){
            if(Math.abs(stand.getX()-tree.getX())>=.1||Math.abs(stand.getY()-tree.getY())>=2||Math.abs(stand.getZ()-tree.getZ())>=.1)continue;
            String name=clean(stand.getName().getString());if(name.contains(player))own=true;
            Matcher count=CONTRIBUTORS.matcher(name);if(count.find())contributors=Math.max(contributors,number(count.group(1)));
        }
        return new Ownership(own||contributors>0,contributors);
    }

    private static void alert(){
        if(!cfg.treeProgressCompletionAlert)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||state==null)return;
        if(cfg.treeProgressCompletionChat)local(state.type+" reached "+state.percent+"%.");
        if(cfg.treeProgressCompletionTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(state.type+" "+state.percent+"%").withColor(cfg.treeProgressCompleteColor&0xFFFFFF));}
        if(cfg.treeProgressCompletionSound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.8f,1.25f);
    }

    public static State state(){return state;}public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){return active()&&state!=null;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.treeProgress&&ConstellationClient.loc().area()==SkyblockArea.GALATEA;}
    private static void reset(){levelIdentity=null;clear();}private static void hide(){state=null;}private static void clear(){state=null;alertedTree=null;}
    private static boolean isAxe(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;String id=id(stack),name=clean(stack.getHoverName().getString());
        if(id.endsWith("_AXE")||id.contains("_AXE_")||name.matches("(?i).*\\bAxe\\b.*"))return true;
        ItemLore lore=stack.get(DataComponents.LORE);if(lore!=null)for(Component line:lore.lines())if(clean(line.getString()).matches("(?i).*(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL) AXE.*"))return true;
        return false;
    }
    private static String id(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static String title(String value){String text=value.toLowerCase(Locale.ROOT);return Character.toUpperCase(text.charAt(0))+text.substring(1);}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static void save(){ConstellationClient.saveConfig();}private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Tree] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("treeprogress").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{clear();local("Tree progress reset.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(16,256)).executes(c->{cfg.treeProgressScanRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("alert").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("percent",IntegerArgumentType.integer(1,100)).executes(c->{cfg.treeProgressAlertPercent=IntegerArgumentType.getInteger(c,"percent");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Tree progress "+on(cfg.treeProgress)+", axe-only "+on(cfg.treeProgressOnlyHoldingAxe)+", own-only "+on(cfg.treeProgressOnlyOwn)+(state==null?".":"; "+state.type+" "+state.percent+"%."));return 1;}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.treeProgress=value;case"hud"->cfg.treeProgressHud=value;case"axe"->cfg.treeProgressOnlyHoldingAxe=value;case"own"->cfg.treeProgressOnlyOwn=value;case"compact"->cfg.treeProgressCompact=value;case"type"->cfg.treeProgressShowType=value;case"percent"->cfg.treeProgressShowPercent=value;case"bar"->cfg.treeProgressShowBar=value;case"distance"->cfg.treeProgressShowDistance=value;case"contributors"->cfg.treeProgressShowContributors=value;case"alert"->cfg.treeProgressCompletionAlert=value;case"chat"->cfg.treeProgressCompletionChat=value;case"title"->cfg.treeProgressCompletionTitle=value;case"sound"->cfg.treeProgressCompletionSound=value;default->{local("Unknown tree-progress option.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}private static String on(boolean value){return value?"on":"off";}
}
