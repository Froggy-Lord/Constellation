package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/westvillage/kloon/KloonHacking.kt, KloonTerminal.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/westvillage/VerminHighlighter.kt, VerminTracker.kt, RiftGunthersRace.kt
public final class AndromedaWestVillage {
    private record Terminal(String name,Vec3 pos){}
    private static final List<Terminal> TERMINALS=List.of(
        new Terminal("RED",new Vec3(-69,65,-63)),new Terminal("ORANGE",new Vec3(-44,71,-62)),
        new Terminal("YELLOW",new Vec3(-39,71,-95)),new Terminal("GREEN",new Vec3(-62,71,-83)),
        new Terminal("AQUA",new Vec3(-33,70,-134.5)),new Terminal("BLUE",new Vec3(-66.5,72,-119)),
        new Terminal("PURPLE",new Vec3(-89,73,-115)),new Terminal("PINK",new Vec3(-110,73,-107)));
    // data ported from SkyHanni Repo (MIT): constants/rift/RiftRace.json
    private static final List<Vec3> RACE=List.of(
        p(-72,70,-60),p(-67,74,-60),p(-67,74,-62),p(-62,76,-62),p(-62,77,-65),p(-62,77,-66),p(-64,77,-67),p(-65,78,-68),
        p(-67,79,-70),p(-67,77,-75),p(-67,77,-81),p(-66,78,-83),p(-67,79,-85),p(-67,81,-88),p(-68,79,-91),p(-70,79,-94),
        p(-73,78,-97),p(-74,77,-98),p(-74,76,-100),p(-73,77,-101),p(-71,78,-103),p(-70,79,-106),p(-68,77,-110),p(-65,77,-111),
        p(-62,80,-111),p(-57,80,-109),p(-56,78,-105),p(-55,79,-103),p(-54,80,-101),p(-53,81,-100),p(-52,82,-99),p(-50,82,-97),
        p(-47,82,-94),p(-44,83,-93),p(-41,83,-89),p(-39,81,-87),p(-38,82,-83),p(-41,83,-81),p(-39,76,-74),p(-41,75,-71),
        p(-44,75,-68),p(-44,77,-64),p(-47,78,-62),p(-49,80,-62),p(-52,78,-62),p(-54,78,-62),p(-57,79,-60),p(-60,80,-62),
        p(-63,75,-62),p(-67,74,-63),p(-72,74,-63),p(-75,75,-62));
    private static final Pattern COLOR=Pattern.compile("You've set the color of this terminal to (.*)!");
    private static final Pattern VACUUMED=Pattern.compile("You vacuumed a (Silverfish|Spider|Fly)!",Pattern.CASE_INSENSITIVE);
    private static final String FLY_HASH="7eefcb90c1030cbaff62790e690158fd18639cca5fe1f72442bd9efd5aaf422a";
    private static final String SPIDER_HASH="8fdf62d4e03ca594c8cd21dd175327f1cf77c4bc057a0959603d83a68ba27008";
    private static AndromedaConfig cfg;private static boolean initialized,racing;private static int raceIndex;private static String screenSignature="";
    private static final List<String> BUTTONS=new ArrayList<>();
    private AndromedaWestVillage(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(2,"andromeda-west-village",AndromedaWestVillage::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->!overlay?onChat(message.getString()):true);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static boolean onChat(String formatted){
        if(!active())return true;String message=clean(formatted);
        if(message.contains("RIFT RACING")&&message.contains("Race started!")){racing=true;raceIndex=0;}
        else if(message.contains("RIFT RACING")&&(message.contains("Race finished")||message.contains("Race cancelled"))){racing=false;raceIndex=0;}
        Matcher color=COLOR.matcher(message);if(color.matches()&&wearingVisor()){Terminal terminal=nearestTerminal(8);if(terminal!=null&&terminal.name.equalsIgnoreCase(color.group(1))&&cfg.westCompletedTerminals.add(terminal.name)){ConstellationClient.saveConfig();}}
        Matcher vermin=VACUUMED.matcher(message);if(vermin.matches()&&cfg.westVerminTracker){switch(vermin.group(1).toLowerCase(Locale.ROOT)){case"fly"->cfg.westVerminFlies++;case"spider"->cfg.westVerminSpiders++;default->cfg.westVerminSilverfish++;}ConstellationClient.saveConfig();return !cfg.westVerminHideChat;}
        return true;
    }
    private static void tick(){
        if(!active()){screenSignature="";BUTTONS.clear();if(racing){racing=false;raceIndex=0;}return;}
        readHacking();if(racing&&cfg.westRaceGuide){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;double range=Math.clamp(cfg.westRaceDetectionRangeTenths,10,100)/10.0;while(raceIndex<RACE.size()&&mc.player.position().distanceTo(RACE.get(raceIndex))<=range)raceIndex++;}
    }
    private static void readHacking(){
        Minecraft mc=Minecraft.getInstance();if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!cfg.westHackingSolver){screenSignature="";BUTTONS.clear();return;}
        String title=clean(screen.getTitle().getString());if(!title.equals("Hacking")&&!title.equals("Hacking (As seen on CSI)")){screenSignature="";BUTTONS.clear();return;}
        if(screen.getMenu().slots.size()<7)return;String signature=title;for(int i=2;i<=6;i++)signature+="|"+clean(screen.getMenu().getSlot(i).getItem().getHoverName().getString());
        if(signature.equals(screenSignature))return;screenSignature=signature;BUTTONS.clear();for(int i=2;i<=6;i++)BUTTONS.add(clean(screen.getMenu().getSlot(i).getItem().getHoverName().getString()));
    }
    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||slot==null)return;String title=clean(screen.getTitle().getString());
        if(cfg.westHackingSolver&&(title.equals("Hacking")||title.equals("Hacking (As seen on CSI)"))&&BUTTONS.size()==5){
            int row=slot.index/9;if(row<1||row>5)return;String wanted=BUTTONS.get(row-1),actual=clean(slot.getItem().getHoverName().getString());int color=0;
            if(slot.index==11+10*(row-1))color=actual.equals(wanted)?cfg.westHackingCorrectColor:cfg.westHackingWrongColor;
            else if(slot.index>row*9-1&&slot.index<row*9+9&&actual.equals(wanted))color=cfg.westHackingCandidateColor;
            if(color!=0)mark(graphics,slot,color);
        }else if(cfg.westHackingColorGuide&&title.equals("Hacked Terminal Color Picker")){
            Terminal target=nearestTerminal(8);if(target!=null&&lore(slot.getItem()).stream().anyMatch(line->line.toUpperCase(Locale.ROOT).contains(target.name)))mark(graphics,slot,cfg.westHackingCorrectColor);
        }
    }
    private static void mark(GuiGraphicsExtractor graphics,Slot slot,int color){color|=0xFF000000;graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,(0x50000000|(color&0xFFFFFF)));graphics.fill(slot.x,slot.y,slot.x+16,slot.y+2,color);graphics.fill(slot.x,slot.y+14,slot.x+16,slot.y+16,color);}
    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        drawTerminals(ctx,mc);drawVermin(ctx,mc);drawRace(ctx,mc);
    }
    private static void drawTerminals(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.westHackingWaypoints||cfg.westHackingRequireVisor&&!wearingVisor())return;double range=Math.clamp(cfg.westHackingRange,10,300),rangeSq=range*range;
        for(Terminal terminal:TERMINALS){if(cfg.westHackingHideCompleted&&cfg.westCompletedTerminals.contains(terminal.name)||terminal.pos.distanceToSqr(mc.player.position())>rangeSq)continue;AABB box=new AABB(terminal.pos.x-.5,terminal.pos.y,terminal.pos.z-.5,terminal.pos.x+.5,terminal.pos.y+1,terminal.pos.z+.5);if(cfg.westHackingBox)ctx.highlight(box,cfg.westHackingColor,cfg.westHackingThroughWalls);if(cfg.westHackingBeam)ctx.beam(terminal.pos.x,terminal.pos.y,terminal.pos.z,cfg.westHackingColor,Math.clamp(cfg.westHackingBeamHeight,2,50),cfg.westHackingThroughWalls);if(cfg.westHackingLabel){String text=terminal.name+" terminal";if(cfg.westHackingDistance)text+=" "+Math.round(terminal.pos.distanceTo(mc.player.position()))+"m";ctx.label(terminal.pos.add(0,1.4,0),text,cfg.westHackingColor,cfg.westHackingThroughWalls);}}
    }
    private static void drawVermin(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.westVerminHighlight||!inWest()||cfg.westVerminHighlightOnlyHoldingVacuum&&!holdingVacuum())return;double range=Math.clamp(cfg.westVerminRange,5,100),rangeSq=range*range;
        for(var entity:mc.level.entitiesForRendering()){if(entity.distanceToSqr(mc.player)>rangeSq||!vermin(entity))continue;AABB box=entity.getBoundingBox().inflate(.15);if(cfg.westVerminBox)ctx.highlight(box,cfg.westVerminColor,cfg.westVerminThroughWalls);if(cfg.westVerminLabel)ctx.label(entity.position().add(0,entity.getBbHeight()+.35,0),"Vermin",cfg.westVerminColor,cfg.westVerminThroughWalls);}
    }
    private static void drawRace(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.westRaceGuide||!racing||raceIndex>=RACE.size())return;int end=Math.min(RACE.size(),raceIndex+Math.clamp(cfg.westRaceLookAhead,1,30));
        for(int i=raceIndex;i<end;i++){Vec3 pos=RACE.get(i);int color=cfg.westRaceRainbow?java.awt.Color.HSBtoRGB((i%24)/24f,.8f,1f)|0xFF000000:cfg.westRaceColor;AABB box=new AABB(pos.x-.45,pos.y-.15,pos.z-.45,pos.x+.45,pos.y+.75,pos.z+.45);if(cfg.westRaceBox)ctx.highlight(box,color,cfg.westRaceThroughWalls);if(cfg.westRaceLine&&i==raceIndex)ctx.line(mc.player.position().add(0,1,0),pos,color,cfg.westRaceThroughWalls);if(cfg.westRaceLabel){String label="Race "+(i+1)+"/"+RACE.size();if(cfg.westRaceDistance)label+=" "+Math.round(pos.distanceTo(mc.player.position()))+"m";ctx.label(pos.add(0,1,0),label,color,cfg.westRaceThroughWalls);}}
    }
    private static boolean vermin(net.minecraft.world.entity.Entity entity){
        if(entity instanceof Silverfish fish)return Math.round(fish.getMaxHealth())==8;
        if(!(entity instanceof ArmorStand stand))return false;ItemStack head=stand.getItemBySlot(EquipmentSlot.HEAD);var profile=head.get(DataComponents.PROFILE);if(profile==null)return false;
        for(Property property:profile.partialProfile().properties().get("textures")){String hash=textureHash(property.value());if(hash.equals(FLY_HASH)||hash.equals(SPIDER_HASH))return true;}return false;
    }
    private static String textureHash(String value){try{String decoded=new String(Base64.getDecoder().decode(value),java.nio.charset.StandardCharsets.UTF_8);int at=decoded.indexOf("/texture/");if(at<0)return"";int start=at+9,end=decoded.indexOf('"',start);return end<0?decoded.substring(start):decoded.substring(start,end);}catch(Exception ignored){return"";}}
    private static boolean wearingVisor(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&LyraTooltips.marketId(mc.player.getItemBySlot(EquipmentSlot.HEAD)).equals("RETRO_ENCABULATING_VISOR");}
    private static boolean holdingVacuum(){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&LyraTooltips.marketId(mc.player.getMainHandItem()).equals("TURBOMAX_VACUUM");}
    private static boolean hasVacuum(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(ItemStack stack:mc.player.getInventory().getNonEquipmentItems())if(LyraTooltips.marketId(stack).equals("TURBOMAX_VACUUM"))return true;return holdingVacuum();}
    private static Terminal nearestTerminal(double max){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return null;Terminal best=null;double distance=max*max;for(Terminal terminal:TERMINALS){double next=terminal.pos.distanceToSqr(mc.player.position());if(next<distance){distance=next;best=terminal;}}return best;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean inWest(){String area=AndromedaRiftCore.currentArea();return area.equalsIgnoreCase("West Village")||area.equalsIgnoreCase("Infested House");}
    public static boolean verminHudVisible(){return active()&&cfg.westVerminTracker&&(cfg.westVerminShowOutside||inWest())&&(cfg.westVerminShowWithoutVacuum||hasVacuum());}
    public static String flies(){return String.format(Locale.ROOT,"%,d",cfg.westVerminFlies);}
    public static String spiders(){return String.format(Locale.ROOT,"%,d",cfg.westVerminSpiders);}
    public static String silverfish(){return String.format(Locale.ROOT,"%,d",cfg.westVerminSilverfish);}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack==null?null:stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static Vec3 p(double x,double y,double z){return new Vec3(x+.5,y,z+.5);}
    private static void reset(){racing=false;raceIndex=0;screenSignature="";BUTTONS.clear();}

    public static void registerCommands(CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("westvillage").executes(c->status())
            .then(LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("resetvermin").executes(c->{cfg.westVerminFlies=cfg.westVerminSpiders=cfg.westVerminSilverfish=0;save();return status();}))
            .then(LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("resetterminals").executes(c->{cfg.westCompletedTerminals.clear();save();return status();}))
            .then(LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Terminals "+cfg.westCompletedTerminals.size()+"/8, vermin "+(cfg.westVerminFlies+cfg.westVerminSpiders+cfg.westVerminSilverfish)+", race "+(racing?(raceIndex+1)+"/"+RACE.size():"idle")+".");return 1;}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"terminalrange"->cfg.westHackingRange=Math.clamp(value,10,300);case"beamheight"->cfg.westHackingBeamHeight=Math.clamp(value,2,50);case"verminrange"->cfg.westVerminRange=Math.clamp(value,5,100);case"lookahead"->cfg.westRaceLookAhead=Math.clamp(value,1,30);case"detection"->cfg.westRaceDetectionRangeTenths=Math.clamp(value,10,100);default->{local("Unknown West Village number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"solver"->cfg.westHackingSolver=value;case"colorguide"->cfg.westHackingColorGuide=value;case"terminals"->cfg.westHackingWaypoints=value;case"requirevisor"->cfg.westHackingRequireVisor=value;case"hidecompleted"->cfg.westHackingHideCompleted=value;case"tracker"->cfg.westVerminTracker=value;case"outside"->cfg.westVerminShowOutside=value;case"withoutvacuum"->cfg.westVerminShowWithoutVacuum=value;case"hidechat"->cfg.westVerminHideChat=value;case"highlight"->cfg.westVerminHighlight=value;case"holdingvacuum"->cfg.westVerminHighlightOnlyHoldingVacuum=value;case"race"->cfg.westRaceGuide=value;case"rainbow"->cfg.westRaceRainbow=value;default->{local("Unknown West Village option.");return 0;}}save();return status();}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[West Village] §f"+text));}
}
