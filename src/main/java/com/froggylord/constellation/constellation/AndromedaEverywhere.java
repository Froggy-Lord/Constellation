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
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/CruxTalismanDisplay.kt, PunchcardHighlight.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/HighlightRiftGuide.kt, RiftHorsezookaHider.kt
public final class AndromedaEverywhere {
    public record HudRow(String label,String value,int color){}
    private record Crux(String tier,String name,String progress,int current,int required,boolean maxed){}
    private static final Pattern CRUX=Pattern.compile(".*?([IV1-4-]+)\\s+(\\w+)\\s*:\\s*(MAXED|(\\d+)\\s*/\\s*(\\d+)).*",Pattern.CASE_INSENSITIVE);
    private static final Pattern PUNCHED=Pattern.compile("PUNCHCARD!\\s+You punched\\s+.*?\\b(\\w+)\\s+and both regained\\s+\\+25.*Rift Time!",Pattern.CASE_INSENSITIVE);
    private static final List<Crux> CRUXES=new ArrayList<>();
    private static final List<String> BONUSES=new ArrayList<>();
    private static final Set<String> QUEUED=new LinkedHashSet<>();
    private static AndromedaConfig cfg;private static boolean initialized,hasArtifact;private static long queueAt,lastMissingWarning;private static Object levelIdentity;
    private AndromedaEverywhere(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(cfg.riftPunchedPlayers==null)cfg.riftPunchedPlayers=new LinkedHashSet<>();if(initialized)return;initialized=true;
        ConstellationClient.tick().every(20,"andromeda-everywhere",AndromedaEverywhere::tick);
        AttackEntityCallback.EVENT.register((player,level,hand,entity,hit)->{if(active()&&(cfg.riftPunchHighlight||cfg.riftPunchHud)&&(!cfg.riftPunchRequireArtifact||hasArtifact)&&entity instanceof RemotePlayer remote&&real(remote)){String name=remote.getGameProfile().name();if(!contains(name)){QUEUED.add(name);queueAt=System.currentTimeMillis();}}return InteractionResult.PASS;});
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetSession();}
        if(!active()){CRUXES.clear();BONUSES.clear();return;}scanInventory(mc);
        if(System.currentTimeMillis()-queueAt>1000)QUEUED.clear();
        if((cfg.riftPunchHighlight||cfg.riftPunchHud)&&cfg.riftPunchRequireArtifact&&!hasArtifact&&cfg.riftPunchMissingWarning&&System.currentTimeMillis()-lastMissingWarning>30_000){lastMissingWarning=System.currentTimeMillis();local("Punchcard Artifact not found; Punchcard tracking is paused.");}
    }
    private static void scanInventory(Minecraft mc){
        if(mc.player==null)return;CRUXES.clear();BONUSES.clear();hasArtifact=false;
        for(ItemStack stack:mc.player.getInventory()){String id=LyraTooltips.marketId(stack).toUpperCase(Locale.ROOT);if(id.equals("PUNCHCARD_ARTIFACT"))hasArtifact=true;if(!id.startsWith("CRUX_TALISMAN"))continue;boolean bonus=false;for(String line:lore(stack)){String clean=clean(line);Matcher matcher=CRUX.matcher(clean);if(matcher.matches()){boolean max=matcher.group(3).equalsIgnoreCase("MAXED");int current=max?1:number(matcher.group(4)),required=max?1:number(matcher.group(5));CRUXES.add(new Crux(matcher.group(1).replace("-","0"),word(matcher.group(2)),max?"MAXED":current+"/"+required,current,Math.max(1,required),max));}if(clean.startsWith("Total Bonuses")){bonus=true;continue;}if(bonus){if(clean.isBlank())bonus=false;else BONUSES.add(clean);}}
        }
    }
    private static void onChat(String message){
        if(!active()||QUEUED.isEmpty())return;String queued=QUEUED.iterator().next();Matcher punched=PUNCHED.matcher(message);
        if(message.startsWith("PUNCHCARD!")&&message.contains("both regained")&&message.toLowerCase(Locale.ROOT).contains(queued.toLowerCase(Locale.ROOT)))mark(queued);
        else if(punched.matches()){String found=punched.group(1);if(found.equalsIgnoreCase(queued))mark(found);}
        else if(message.startsWith("AWKWARD!")&&message.contains("already been punched")||message.startsWith("UH OH!")&&message.contains("limit of 20"))mark(queued);
    }
    private static void mark(String name){cfg.riftPunchedPlayers.add(name);QUEUED.removeIf(value->value.equalsIgnoreCase(name));ConstellationClient.saveConfig();}
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!cfg.riftPunchHighlight||cfg.riftPunchRequireArtifact&&!hasArtifact)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;double range=Math.clamp(cfg.riftPunchRange,10,150),sq=range*range;
        for(Entity entity:mc.level.entitiesForRendering())if(entity instanceof RemotePlayer player&&real(player)&&player.distanceToSqr(mc.player)<=sq){boolean punched=contains(player.getGameProfile().name()),show=cfg.riftPunchReverseHighlight?punched:!punched;if(!show)continue;if(cfg.riftPunchBox)ctx.highlight(player.getBoundingBox().inflate(.1),cfg.riftPunchColor,cfg.riftPunchThroughWalls);if(cfg.riftPunchLabel){String label=cfg.riftPunchReverseHighlight?"Punched":"Not punched";if(cfg.riftPunchDistance)label+=" "+Math.round(player.distanceTo(mc.player))+"m";ctx.label(player.position().add(0,player.getBbHeight()+.4,0),label,cfg.riftPunchColor,cfg.riftPunchThroughWalls);}}
    }
    private static boolean contains(String name){return cfg.riftPunchedPlayers.stream().anyMatch(value->value.equalsIgnoreCase(name));}
    private static boolean real(RemotePlayer player){Minecraft mc=Minecraft.getInstance();return mc.getConnection()!=null&&mc.getConnection().getPlayerInfo(player.getUUID())!=null;}
    public static boolean shouldHide(Entity entity){if(!(entity instanceof Horse)||!active()||!cfg.riftHorsezookaHider)return false;Minecraft mc=Minecraft.getInstance();return mc.player!=null&&LyraTooltips.marketId(mc.player.getMainHandItem()).equalsIgnoreCase("HORSEZOOKA");}

    public static void drawGuideSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||!cfg.riftGuideHighlights||slot==null||!guide(screen)||!missing(slot.getItem()))return;int color=cfg.riftGuideMissingColor|0xFF000000;graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.fill(slot.x,slot.y,slot.x+1,slot.y+16,color);graphics.fill(slot.x+15,slot.y,slot.x+16,slot.y+16,color);if(cfg.riftGuideHighlightText)graphics.text(Minecraft.getInstance().font,"M",slot.x+9,slot.y+1,color,true);
    }
    private static boolean guide(AbstractContainerScreen<?> screen){if(screen.getMenu().slots.size()<=40)return false;List<String> lore=lore(screen.getMenu().getSlot(40).getItem());return lore.size()==1&&clean(lore.getFirst()).startsWith("To Rift Guide");}
    private static boolean missing(ItemStack stack){List<String> lines=lore(stack);return!lines.isEmpty()&&clean(lines.getLast()).endsWith("Not completed yet!");}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack==null?null:stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(Component::getString).toList();}

    public static List<HudRow> hudRows(String mode){
        if(!active())return List.of();if(mode.equals("punch")){if(!cfg.riftPunchHud||cfg.riftPunchRequireArtifact&&!hasArtifact)return List.of();int amount=cfg.riftPunchedPlayers.size(),limit=Math.clamp(cfg.riftPunchLimit,1,100);String value=cfg.riftPunchRemaining?Integer.toString(Math.max(0,limit-amount)):amount+"/"+limit;return List.of(new HudRow(cfg.riftPunchCompact?"":"Players",value,cfg.riftPunchColor));}
        if(!cfg.riftCruxDisplay||AndromedaRiftCore.currentArea().equalsIgnoreCase("Mirrorverse")||CRUXES.isEmpty())return List.of();List<HudRow> rows=new ArrayList<>();boolean maxed=CRUXES.stream().allMatch(Crux::maxed);double percent=CRUXES.stream().mapToDouble(value->value.maxed?1:(double)value.current/value.required).average().orElse(0)*100;if(cfg.riftCruxShowPercent)rows.add(new HudRow("Progress",maxed&&cfg.riftCruxCompactMaxed?"MAXED":String.format(Locale.ROOT,"%.1f%%",percent),maxed?cfg.riftCruxMaxedColor:cfg.riftCruxProgressColor));if(!(maxed&&cfg.riftCruxCompactMaxed))for(Crux crux:CRUXES)rows.add(new HudRow(crux.name+(cfg.riftCruxShowTier?" "+crux.tier:""),crux.progress,crux.maxed?cfg.riftCruxMaxedColor:cfg.riftCruxProgressColor));if(cfg.riftCruxBonuses)for(String bonus:BONUSES)rows.add(new HudRow("Bonus",bonus,0xFFFFFFFF));return rows;
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static String word(String value){return value.substring(0,1).toUpperCase(Locale.ROOT)+value.substring(1).toLowerCase(Locale.ROOT);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;resetSession();}
    private static void resetSession(){QUEUED.clear();cfg.riftPunchedPlayers.clear();queueAt=lastMissingWarning=0;CRUXES.clear();BONUSES.clear();hasArtifact=false;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Rift] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("riftprogress").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetpunchcard").executes(c->{cfg.riftPunchedPlayers.clear();save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(1)).executes(c->numberOption(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Crux "+(cfg.riftCruxDisplay?"on":"off")+" with "+CRUXES.size()+" rows, Punchcard "+cfg.riftPunchedPlayers.size()+"/"+Math.clamp(cfg.riftPunchLimit,1,100)+", Guide "+(cfg.riftGuideHighlights?"on":"off")+", Horsezooka "+(cfg.riftHorsezookaHider?"on":"off")+".");return 1;}
    private static int numberOption(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"punchrange"->cfg.riftPunchRange=Math.clamp(value,10,150);case"punchlimit"->cfg.riftPunchLimit=Math.clamp(value,1,100);default->{local("Unknown Rift progress number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"crux"->cfg.riftCruxDisplay=value;case"cruxcompact"->cfg.riftCruxCompactMaxed=value;case"bonuses"->cfg.riftCruxBonuses=value;case"punchhighlight"->cfg.riftPunchHighlight=value;case"punchhud"->cfg.riftPunchHud=value;case"punchremaining"->cfg.riftPunchRemaining=value;case"punchreverse"->cfg.riftPunchReverseHighlight=value;case"artifact"->cfg.riftPunchRequireArtifact=value;case"guide"->cfg.riftGuideHighlights=value;case"horsezooka"->cfg.riftHorsezookaHider=value;default->{local("Unknown Rift progress option.");return 0;}}save();return status();}
}
