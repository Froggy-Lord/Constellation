package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.CygnusConfig;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.*;

public final class CygnusUniqueGifts {
    // ported from SkyHanni (LGPL-2.1): features/gifting/UniqueGiftingOpportunitiesFeatures.kt
    private static final Pattern GIVEN=Pattern.compile("^\\+1 Unique Gift given! To (.+)!$");
    // ported from SkyHanni (LGPL-2.1): features/gifting/UniqueGiftCounter.kt
    private static final Pattern AMOUNT=Pattern.compile("^Unique Players Gifted: ([\\d,]+)$");
    private static CygnusConfig cfg;
    private static boolean initialized,screenRead;
    private static String profile="";
    private CygnusUniqueGifts(){}

    public static void init(CygnusConfig config){
        cfg=config;normalize();
        if(initialized)return;
        initialized=true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->screenRead=false);
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->screenRead=false);
        ConstellationClient.tick().every(5,"cygnus-unique-gifts",CygnusUniqueGifts::tick);
    }
    private static void tick(){String p=profileKey();if(!p.equals(profile)){profile=p;screenRead=false;}readGenerow();}
    private static void onChat(String text){if(!active())return;Matcher m=GIVEN.matcher(text);if(!m.matches())return;mark(m.group(1),true);}
    private static void readGenerow(){
        Minecraft mc=Minecraft.getInstance();if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!clean(screen.getTitle().getString()).equals("Generow")){screenRead=false;return;}
        if(screenRead||screen.getMenu().slots.size()<=40)return;screenRead=true;ItemLore lore=screen.getMenu().getSlot(40).getItem().get(DataComponents.LORE);if(lore==null)return;
        for(Component line:lore.lines()){Matcher m=AMOUNT.matcher(clean(line.getString()));if(!m.matches())continue;int value=(int)number(m.group(1));int old=amount();cfg.uniqueGiftAmounts.put(profileKey(),Math.max(value,names().size()));if(value!=old){save();local("Synced unique gift count to "+value+".");}break;}
    }
    private static void mark(String raw,boolean alert){String name=raw.trim();if(name.isBlank())return;Set<String> names=names();boolean fresh=names.stream().noneMatch(n->n.equalsIgnoreCase(name));if(!fresh)return;names.add(name);int old=amount(),now=Math.max(old+1,names.size());cfg.uniqueGiftAmounts.put(profileKey(),now);save();if(alert&&milestones().contains(now))milestone(now);}
    private static void milestone(int amount){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text=amount+"/"+Math.max(1,cfg.uniqueGiftGoal)+" unique players gifted";if(cfg.uniqueGiftMilestoneChat)local(text+".");if(cfg.uniqueGiftMilestoneTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Unique Gift Milestone").withColor(cfg.uniqueGiftColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(text));}if(cfg.uniqueGiftMilestoneSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.25f);}

    public static String hudText(){if(!active()||cfg.uniqueGiftHoldingOnly&&!CygnusGifts.holdingGift())return null;int amount=amount(),goal=Math.max(1,cfg.uniqueGiftGoal);return"§7Unique Players §a"+amount+"/"+goal+(cfg.uniqueGiftShowRemaining&&amount<goal?" §e"+(goal-amount)+" remaining":"");}
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!cfg.giftingOpportunities||cfg.giftingOpportunitiesHoldingOnly&&!CygnusGifts.holdingGift())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getConnection()==null)return;double range=Math.clamp(cfg.giftingOpportunitiesRange,4,128);
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof Player player&&player!=mc.player&&player.isAlive()&&mc.player.distanceTo(player)<=range&&mc.getConnection().getPlayerInfo(player.getUUID())!=null){
            String name=player.getGameProfile().name();if(!allowed(name))continue;boolean gifted=contains(name);if(gifted&&!cfg.giftingOpportunitiesShowGifted)continue;int color=gifted?cfg.giftingAlreadyColor:cfg.giftingOpportunityColor;
            if(cfg.giftingOpportunitiesBox)ctx.highlight(player.getBoundingBox().inflate(.08),color,cfg.giftingOpportunitiesThroughWalls);
            if(cfg.giftingOpportunitiesLabel)ctx.label(player.position().add(0,player.getBbHeight()+.35,0),gifted?"Already gifted":"Not gifted",color,cfg.giftingOpportunitiesThroughWalls);
        }
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.winterGiftTracker&&ConstellationClient.loc().onHypixel();}
    private static Set<String> names(){normalize();return cfg.uniqueGiftPlayers.computeIfAbsent(profileKey(),k->new LinkedHashSet<>());}
    private static int amount(){return Math.max(cfg.uniqueGiftAmounts.getOrDefault(profileKey(),0),names().size());}
    private static boolean contains(String name){return names().stream().anyMatch(n->n.equalsIgnoreCase(name));}
    private static boolean allowed(String name){String lower=name.toLowerCase(Locale.ROOT);Set<String> include=csv(cfg.giftingOpportunityIncludes),exclude=csv(cfg.giftingOpportunityExcludes);return(include.isEmpty()||include.stream().anyMatch(lower::contains))&&exclude.stream().noneMatch(lower::contains);}
    private static Set<String> csv(String raw){Set<String> out=new LinkedHashSet<>();if(raw!=null)for(String p:raw.split(",")){String s=p.trim().toLowerCase(Locale.ROOT);if(!s.isBlank())out.add(s);}return out;}
    private static Set<Integer> milestones(){Set<Integer> out=new HashSet<>();if(cfg.uniqueGiftMilestones!=null)for(String p:cfg.uniqueGiftMilestones.split(","))try{out.add(Integer.parseInt(p.trim()));}catch(Exception ignored){}return out;}
    private static String profileKey(){String p=LyraStorageValue.currentProfileKey();return p==null||p.isBlank()?"unknown":p.toLowerCase(Locale.ROOT);}
    private static long number(String raw){try{return Long.parseLong(raw.replace(",",""));}catch(Exception e){return 0;}}
    private static String clean(String raw){String s=ChatFormatting.stripFormatting(raw);return s==null?"":s.trim();}
    private static void normalize(){if(cfg.uniqueGiftPlayers==null)cfg.uniqueGiftPlayers=new LinkedHashMap<>();if(cfg.uniqueGiftAmounts==null)cfg.uniqueGiftAmounts=new LinkedHashMap<>();}
    private static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("uniquegifts").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c->list())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{cfg.uniqueGiftPlayers.remove(profileKey());cfg.uniqueGiftAmounts.remove(profileKey());save();local("Current profile unique gift history reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearfilters").executes(c->{cfg.giftingOpportunityIncludes="";cfg.giftingOpportunityExcludes="";save();return status();})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mark").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("player",StringArgumentType.word()).executes(c->{mark(StringArgumentType.getString(c,"player"),false);return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("unmark").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("player",StringArgumentType.word()).executes(c->unmark(StringArgumentType.getString(c,"player"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("amount").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(0)).executes(c->{cfg.uniqueGiftAmounts.put(profileKey(),IntegerArgumentType.getInteger(c,"count"));save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goal").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,10000)).executes(c->{cfg.uniqueGiftGoal=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(4,128)).executes(c->{cfg.giftingOpportunitiesRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("milestones").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("counts",StringArgumentType.greedyString()).executes(c->{cfg.uniqueGiftMilestones=StringArgumentType.getString(c,"counts");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("include").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("players",StringArgumentType.greedyString()).executes(c->{cfg.giftingOpportunityIncludes=StringArgumentType.getString(c,"players");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("exclude").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("players",StringArgumentType.greedyString()).executes(c->{cfg.giftingOpportunityExcludes=StringArgumentType.getString(c,"players");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"argb")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local(amount()+"/"+Math.max(1,cfg.uniqueGiftGoal)+" unique players gifted; "+names().size()+" names recorded.");return 1;}
    private static int list(){if(names().isEmpty()){local("No recipient names recorded.");return 1;}local(String.join(", ",names().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));return 1;}
    private static int unmark(String name){boolean removed=names().removeIf(n->n.equalsIgnoreCase(name));if(removed){cfg.uniqueGiftAmounts.put(profileKey(),Math.max(names().size(),amount()-1));save();}return status();}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"counter"->cfg.uniqueGiftCounter=v;case"hud"->cfg.uniqueGiftHud=v;case"holding"->cfg.uniqueGiftHoldingOnly=v;case"remaining"->cfg.uniqueGiftShowRemaining=v;case"chat"->cfg.uniqueGiftMilestoneChat=v;case"title"->cfg.uniqueGiftMilestoneTitle=v;case"sound"->cfg.uniqueGiftMilestoneSound=v;case"opportunities"->cfg.giftingOpportunities=v;case"opportunityholding"->cfg.giftingOpportunitiesHoldingOnly=v;case"box"->cfg.giftingOpportunitiesBox=v;case"label"->cfg.giftingOpportunitiesLabel=v;case"gifted"->cfg.giftingOpportunitiesShowGifted=v;case"walls"->cfg.giftingOpportunitiesThroughWalls=v;default->{local("Unknown unique gift option.");return 0;}}save();return status();}
    private static int color(String type,String raw){try{long p=raw.startsWith("#")?Long.parseLong(raw.substring(1),16):Long.decode(raw);int c=(int)p;if((c>>>24)==0)c|=0xFF000000;switch(type.toLowerCase(Locale.ROOT)){case"counter"->cfg.uniqueGiftColor=c;case"available"->cfg.giftingOpportunityColor=c;case"gifted"->cfg.giftingAlreadyColor=c;default->{local("Color type must be counter, available, or gifted.");return 0;}}save();return status();}catch(NumberFormatException e){local("Color must be #RRGGBB, #AARRGGBB, or a number.");return 0;}}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Unique Gifts] §f"+text));}
}
