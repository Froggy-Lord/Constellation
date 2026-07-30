package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.render.WorldRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/rift/TheRift.java, EnigmaSouls.java, MirrorverseWaypoints.java, EffigyWaypoints.java
public final class AndromedaRiftCore {
    private record Point(String area,BlockPos pos,String name){}
    private static final Pattern TIME_CLOCK=Pattern.compile("(?:Rift\\s+)?Time(?: Left)?:?\\s*(?:(?:(\\d+)h\\s*)?(\\d+)m(?:\\s*(\\d+)s)?|(\\d+):([0-9]{2}))",Pattern.CASE_INSENSITIVE);
    private static final Pattern MOTES=Pattern.compile("Motes:?\\s*([\\d,]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern SOUL_TITLE=Pattern.compile("(?:✔ )?Enigma: (.+)");
    private static final Pattern GUIDE_AREA=Pattern.compile("To Rift Guide ➜ (.+)");
    private static final String SOUL_NEW="SOUL! You unlocked an Enigma Soul!";
    private static final String SOUL_OLD="You have already found that Enigma Soul!";
    private static final List<BlockPos> EFFIGIES=List.of(new BlockPos(150,79,95),new BlockPos(193,93,119),new BlockPos(235,110,147),new BlockPos(293,96,134),new BlockPos(262,99,94),new BlockPos(240,129,118));
    private static final List<Point> SOULS=new ArrayList<>();
    private static final Map<String,List<Point>> MIRROR=new LinkedHashMap<>();
    private static final Set<Integer> unbrokenEffigies=new LinkedHashSet<>();
    private static AndromedaConfig cfg;private static boolean initialized;private static int timeSeconds=-1,maxTimeSeconds,motes=-1,lastMotes=-1;private static long motesSession;private static boolean lowWarned;private static String lastArea="",lastGuideSignature="";
    private AndromedaRiftCore(){}

    public static void init(AndromedaConfig config){
        cfg=config;normalize();loadAssets();if(initialized)return;initialized=true;
        ConstellationClient.tick().every(5,"andromeda-rift-core",AndromedaRiftCore::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetWorld());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetWorld());
    }
    private static void normalize(){if(cfg.riftFoundSouls==null)cfg.riftFoundSouls=new LinkedHashMap<>();}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static String profile(){String key=LyraStorageValue.currentProfileKey();return key==null||key.isBlank()?"default":key.toLowerCase(Locale.ROOT);}
    private static Set<String> found(){return cfg.riftFoundSouls.computeIfAbsent(profile(),ignored->new LinkedHashSet<>());}
    private static String key(BlockPos pos){return pos.getX()+","+pos.getY()+","+pos.getZ();}
    private static void loadAssets(){if(!SOULS.isEmpty())return;loadSouls();loadMirror();}
    private static void loadSouls(){
        // data ported from SkyHanni Repo (MIT): constants/EnigmaSouls.json
        try(var stream=AndromedaRiftCore.class.getResourceAsStream("/assets/constellation/rift/enigma_souls_named.json")){
            if(stream==null)throw new IllegalStateException("missing named enigma soul data");
            JsonObject areas=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("areas");
            for(var area:areas.entrySet())for(var value:area.getValue().getAsJsonArray()){JsonObject point=value.getAsJsonObject();String[] xyz=point.get("position").getAsString().split(":");SOULS.add(new Point(area.getKey(),new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2])),point.get("name").getAsString()));}
            ConstellationClient.LOGGER.info("loaded {} Rift enigma soul waypoints",SOULS.size());
        }catch(Exception e){ConstellationClient.LOGGER.error("failed to load Rift enigma souls",e);}
    }
    private static void loadMirror(){
        try(var stream=AndromedaRiftCore.class.getResourceAsStream("/assets/constellation/rift/mirrorverse_waypoints.json")){
            if(stream==null)throw new IllegalStateException("missing mirrorverse data");
            JsonArray sections=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("sections");
            for(var section:sections){JsonObject object=section.getAsJsonObject();String name=object.get("name").getAsString();List<Point> points=new ArrayList<>();JsonArray array=object.getAsJsonArray("waypoints");for(int i=0;i<array.size();i++){JsonObject point=array.get(i).getAsJsonObject();points.add(new Point(name,new BlockPos(point.get("x").getAsInt(),point.get("y").getAsInt(),point.get("z").getAsInt()),name));}MIRROR.put(name,List.copyOf(points));}
            ConstellationClient.LOGGER.info("loaded {} Rift Mirrorverse waypoint sections",MIRROR.size());
        }catch(Exception e){ConstellationClient.LOGGER.error("failed to load Rift Mirrorverse paths",e);}
    }
    private static void tick(){
        if(!active()){if(timeSeconds>=0||motes>=0)resetVisit();return;}
        parseSidebar();syncSoulGuide();updateEffigies();warnLowTime();
    }
    private static void parseSidebar(){
        int newTime=-1,newMotes=-1;String area="";
        for(String line:ConstellationClient.loc().getSidebarLines()){
            String clean=clean(line);int parsed=parseTime(clean);if(parsed>=0)newTime=parsed;
            Matcher balance=MOTES.matcher(clean);if(balance.find())newMotes=num(balance.group(1).replace(",",""));
            if(clean.startsWith("Area:")||clean.startsWith("Zone:"))area=clean.substring(clean.indexOf(':')+1).trim();
        }
        if(newTime<0)for(String line:com.froggylord.constellation.data.TabList.lines()){int parsed=parseTime(clean(line));if(parsed>=0){newTime=parsed;break;}}
        if(newTime>=0){timeSeconds=newTime;maxTimeSeconds=Math.max(maxTimeSeconds,newTime);}
        if(newMotes>=0){if(lastMotes>=0&&newMotes>lastMotes)motesSession+=newMotes-lastMotes;lastMotes=newMotes;motes=newMotes;}
        if(!area.isBlank())lastArea=area;
    }
    private static void warnLowTime(){
        if(!cfg.riftLowTimeAlert||timeSeconds<0)return;int threshold=Math.clamp(cfg.riftLowTimeSeconds,10,600);if(timeSeconds>threshold){lowWarned=false;return;}if(lowWarned)return;lowWarned=true;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text="Rift time is low: "+duration(timeSeconds);if(cfg.riftLowTimeChat)local(text+".");if(cfg.riftLowTimeTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Rift time is low").withColor(cfg.riftTimeDangerColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(duration(timeSeconds)));}if(cfg.riftLowTimeSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,.7f);
    }
    private static void onChat(String message){if(!active())return;if(message.equals(SOUL_NEW)||message.equals(SOUL_OLD)){markClosest(true);AndromedaRiftNavigation.onSoulFound();}}
    private static void markClosest(boolean state){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;SOULS.stream().min(Comparator.comparingDouble(point->point.pos.distToCenterSqr(mc.player.position()))).filter(point->point.pos.distToCenterSqr(mc.player.position())<=16).ifPresent(point->{if(state)found().add(key(point.pos));else found().remove(key(point.pos));ConstellationClient.saveConfig();});}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.enigmaSoulWaypoints)drawSouls(ctx,mc);
        if(cfg.mirrorverseWaypoints)drawMirror(ctx,mc);
        if(cfg.effigyWaypoints)drawEffigies(ctx,mc);
    }
    private static void drawSouls(WorldRenderer.Ctx ctx,Minecraft mc){
        double max=Math.clamp(cfg.enigmaSoulRange,10,500),maxSq=max*max;List<Point> visible=SOULS.stream().filter(p->p.pos.distToCenterSqr(mc.player.position())<=maxSq).filter(p->!cfg.enigmaSoulCurrentAreaOnly||lastArea.isBlank()||areaMatches(p.area,lastArea)).filter(p->cfg.enigmaSoulShowFound||!found().contains(key(p.pos))).sorted(Comparator.comparingDouble(p->p.pos.distToCenterSqr(mc.player.position()))).toList();if(cfg.enigmaSoulNearestOnly&&!visible.isEmpty())visible=List.of(visible.getFirst());
        for(Point point:visible){boolean collected=found().contains(key(point.pos));int color=collected?cfg.enigmaSoulFoundColor:cfg.enigmaSoulMissingColor;Vec3 center=Vec3.atCenterOf(point.pos);if(cfg.enigmaSoulBox)ctx.highlight(new AABB(point.pos),color,cfg.enigmaSoulThroughWalls);if(cfg.enigmaSoulBeam)ctx.beam(center.x,center.y,center.z,color,Math.clamp(cfg.enigmaSoulBeamHeight,2,100),cfg.enigmaSoulThroughWalls);if(cfg.enigmaSoulLine)ctx.line(mc.player.position().add(0,1,0),center,color,cfg.enigmaSoulThroughWalls);if(cfg.enigmaSoulLabel){String label=point.name+" Soul"+(cfg.enigmaSoulAreaInLabel?" · "+point.area:"")+(collected?" · Found":"");if(cfg.enigmaSoulDistance)label+=" · "+Math.round(Math.sqrt(point.pos.distToCenterSqr(mc.player.position())))+"m";ctx.label(center.add(0,1.2,0),label,color,cfg.enigmaSoulThroughWalls);}}
    }

    // ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/EnigmaSoulWaypoints.kt
    private static void syncSoulGuide(){
        if(!cfg.enigmaSoulGuideSync)return;Minecraft mc=Minecraft.getInstance();if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!soulMenu(screen)){lastGuideSignature="";return;}
        String area=selectedGuideArea(screen),signature=area;Set<String> nextFound=new LinkedHashSet<>(),nextMissing=new LinkedHashSet<>();
        int size=Math.max(0,screen.getMenu().slots.size()-36);
        for(int i=0;i<size;i++){ItemStack stack=screen.getMenu().getSlot(i).getItem();Point point=point(stack,area);if(point==null)continue;boolean missing=lore(stack).stream().anyMatch(line->line.endsWith("Not completed yet!"));signature+="|"+point.name+":"+missing;if(missing)nextMissing.add(key(point.pos));else nextFound.add(key(point.pos));}
        if(signature.equals(lastGuideSignature)||nextFound.isEmpty()&&nextMissing.isEmpty())return;lastGuideSignature=signature;Set<String> stored=found();int before=stored.size();stored.removeAll(nextMissing);stored.addAll(nextFound);if(stored.size()!=before||!nextFound.isEmpty()){ConstellationClient.saveConfig();if(cfg.enigmaSoulGuideSyncChat)local("Rift Guide synced "+nextFound.size()+" found and "+nextMissing.size()+" missing souls"+(area.isBlank()?".":" in "+area+"."));}
    }

    public static boolean onSoulMenuClick(AbstractContainerScreen<?> screen,Slot slot,int button,ContainerInput input){
        if(!active()||!cfg.enigmaSoulMenuTracking||!soulMenu(screen)||slot==null||button!=1)return false;Point point=point(slot.getItem(),selectedGuideArea(screen));if(point==null)return false;int index=SOULS.indexOf(point);if(index<0)return false;AndromedaRiftNavigation.selectFromGuide(index);return true;
    }
    public static void drawSoulMenuSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||!cfg.enigmaSoulMenuHighlights||!soulMenu(screen)||slot==null)return;Point point=point(slot.getItem(),selectedGuideArea(screen));if(point==null)return;int index=SOULS.indexOf(point);boolean routed=index==AndromedaRiftNavigation.targetIndex();int color=routed?cfg.riftPathfinderColor:found().contains(key(point.pos))?cfg.enigmaSoulFoundColor:cfg.enigmaSoulMissingColor;color|=0xFF000000;graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.text(Minecraft.getInstance().font,routed?"R":found().contains(key(point.pos))?"F":"M",slot.x+10,slot.y+1,color,true);
    }
    public static List<Component> appendSoulMenuTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> current){
        if(!active()||!cfg.enigmaSoulMenuTracking||!soulMenu(screen))return current;Point point=point(stack,selectedGuideArea(screen));if(point==null)return current;ArrayList<Component> out=new ArrayList<>(current);out.add(Component.literal("§dRight-click to route to "+point.name+" Soul"));out.add(Component.literal("§7"+point.area+" · "+(found().contains(key(point.pos))?"Found":"Missing")));return out;
    }
    private static boolean soulMenu(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString()).contains("Enigma Souls");}
    private static String selectedGuideArea(AbstractContainerScreen<?> screen){if(screen.getMenu().slots.size()<=40)return"";for(String line:lore(screen.getMenu().getSlot(40).getItem())){Matcher matcher=GUIDE_AREA.matcher(line);if(matcher.matches())return matcher.group(1).trim();}return"";}
    private static Point point(ItemStack stack,String area){if(stack==null||stack.isEmpty())return null;Matcher matcher=SOUL_TITLE.matcher(clean(stack.getHoverName().getString()));if(!matcher.matches())return null;String name=matcher.group(1).trim();return SOULS.stream().filter(point->point.name.equalsIgnoreCase(name)).filter(point->area.isBlank()||areaMatches(point.area,area)).findFirst().orElse(null);}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack==null?null:stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static boolean areaMatches(String left,String right){return clean(left).replace("â","a").equalsIgnoreCase(clean(right).replace("â","a"))||clean(left).toLowerCase(Locale.ROOT).contains(clean(right).toLowerCase(Locale.ROOT))||clean(right).toLowerCase(Locale.ROOT).contains(clean(left).toLowerCase(Locale.ROOT));}
    private static void drawMirror(WorldRenderer.Ctx ctx,Minecraft mc){
        for(var entry:MIRROR.entrySet()){String section=entry.getKey();if(section.equals("Lava Path")&&!cfg.mirrorverseLavaPath||section.equals("Upside Down Parkour")&&!cfg.mirrorverseUpsideDown||section.equals("Turbulator Parkour")&&!cfg.mirrorverseTurbulator)continue;int color=section.equals("Lava Path")?cfg.mirrorverseLavaColor:section.equals("Upside Down Parkour")?cfg.mirrorverseUpsideColor:cfg.mirrorverseTurbulatorColor;double range=Math.clamp(cfg.mirrorverseRange,10,200),rangeSq=range*range;List<Point> points=entry.getValue().stream().filter(p->p.pos.distToCenterSqr(mc.player.position())<=rangeSq).sorted(Comparator.comparingDouble(p->p.pos.distToCenterSqr(mc.player.position()))).toList();for(Point point:points){if(cfg.mirrorverseBoxes)ctx.highlight(new AABB(point.pos),color,cfg.mirrorverseThroughWalls);if(cfg.mirrorverseLabels)ctx.label(Vec3.atCenterOf(point.pos).add(0,1,0),section,color,cfg.mirrorverseThroughWalls);}if(cfg.mirrorverseNearestLine&&!points.isEmpty())ctx.line(mc.player.position().add(0,1,0),Vec3.atCenterOf(points.getFirst().pos),color,cfg.mirrorverseThroughWalls);}
    }
    private static void drawEffigies(WorldRenderer.Ctx ctx,Minecraft mc){
        if(unbrokenEffigies.isEmpty())return;double range=Math.clamp(cfg.effigyRange,25,500),rangeSq=range*range;for(int index:unbrokenEffigies){BlockPos source=EFFIGIES.get(index),pos=cfg.effigyCompact?source.below(6):source;if(pos.distToCenterSqr(mc.player.position())>rangeSq)continue;Vec3 center=Vec3.atCenterOf(pos);if(cfg.effigyBox)ctx.highlight(new AABB(pos),cfg.effigyColor,cfg.effigyThroughWalls);if(cfg.effigyBeam)ctx.beam(center.x,center.y,center.z,cfg.effigyColor,Math.clamp(cfg.effigyBeamHeight,2,100),cfg.effigyThroughWalls);if(cfg.effigyLabel){String label="Unbroken Effigy "+(index+1);if(cfg.effigyDistance)label+=" "+Math.round(Math.sqrt(pos.distToCenterSqr(mc.player.position())))+"m";ctx.label(center.add(0,1.2,0),label,cfg.effigyColor,cfg.effigyThroughWalls);}}
    }
    private static void updateEffigies(){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;Set<Integer> next=new LinkedHashSet<>();
        try{var scoreboard=mc.level.getScoreboard();var objective=scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);if(objective==null)return;for(var holder:scoreboard.getTrackedPlayers()){if(!scoreboard.listPlayerScores(holder).containsKey(objective))continue;var team=scoreboard.getPlayersTeam(holder.getScoreboardName());if(team==null)continue;Component line=Component.empty().append(team.getPlayerPrefix()).append(team.getPlayerSuffix());if(!line.getString().contains("Effigies"))continue;List<Component> leaves=new ArrayList<>();flatten(line,leaves);int marker=0;for(Component leaf:leaves){var color=leaf.getStyle().getColor();if(color==null)continue;String text=leaf.getString();for(int i=0;i<text.length()&&marker<EFFIGIES.size();i++){char c=text.charAt(i);if(Character.isWhitespace(c)||Character.isLetterOrDigit(c)||c==':'||c=='/')continue;if(color.getValue()==0xAAAAAA)next.add(marker);marker++;}}}}catch(Exception ignored){return;}if(!next.isEmpty()||!unbrokenEffigies.isEmpty()){unbrokenEffigies.clear();unbrokenEffigies.addAll(next);}
    }
    private static void flatten(Component component,List<Component> out){if(component.getSiblings().isEmpty()){if(!component.getString().isBlank())out.add(component);return;}for(Component sibling:component.getSiblings())flatten(sibling,out);}

    public static boolean hudVisible(){return active()&&cfg.riftHud;}
    public static String hudTime(){return cfg.riftHudTime&&timeSeconds>=0?duration(timeSeconds):null;}
    public static int hudTimeColor(){return timeSeconds<0?cfg.riftTimeNormalColor:timeSeconds<=Math.clamp(cfg.riftLowTimeSeconds,10,600)?cfg.riftTimeDangerColor:timeSeconds<=300?cfg.riftTimeWarningColor:cfg.riftTimeNormalColor;}
    public static String hudMotes(){return cfg.riftHudMotes&&motes>=0?number(motes):null;}
    public static String hudSouls(){return cfg.riftHudSouls?found().size()+"/"+SOULS.size():null;}
    public static String hudEffigies(){return cfg.riftHudEffigies&&!unbrokenEffigies.isEmpty()?unbrokenEffigies.size()+" unbroken":null;}
    public static String hudArea(){return cfg.riftHudArea&&!lastArea.isBlank()?lastArea:null;}
    public static boolean isActive(){return active();}
    public static int soulCount(){return SOULS.size();}
    public static BlockPos soulPosition(int index){return index>=0&&index<SOULS.size()?SOULS.get(index).pos:null;}
    public static String soulName(int index){return index>=0&&index<SOULS.size()?SOULS.get(index).name+" Soul":"Enigma Soul "+(index+1);}
    public static String soulArea(int index){return index>=0&&index<SOULS.size()?SOULS.get(index).area:"";}
    public static int soulIndex(String query){String needle=clean(query).toLowerCase(Locale.ROOT).replace(" soul","").trim();for(int i=0;i<SOULS.size();i++){Point point=SOULS.get(i);if(point.name.toLowerCase(Locale.ROOT).equals(needle)||((point.area+" "+point.name).toLowerCase(Locale.ROOT).contains(needle)))return i;}return-1;}
    public static boolean soulFound(int index){BlockPos pos=soulPosition(index);return pos!=null&&found().contains(key(pos));}
    public static int closestMissingIndex(Vec3 player){int best=-1;double distance=Double.POSITIVE_INFINITY;for(int i=0;i<SOULS.size();i++){if(soulFound(i))continue;double current=SOULS.get(i).pos.distToCenterSqr(player);if(current<distance){distance=current;best=i;}}return best;}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("riftguide").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("souls")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("allfound").executes(c->{for(Point p:SOULS)found().add(key(p.pos));save();return status();}))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("allmissing").executes(c->{found().clear();save();return status();}))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("closestfound").executes(c->{markClosest(true);return status();}))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("closestmissing").executes(c->{markClosest(false);return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("souls").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(10,500)).executes(c->{cfg.enigmaSoulRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mirror").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(10,200)).executes(c->{cfg.mirrorverseRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("effigies").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(25,500)).executes(c->{cfg.effigyRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("beamheight")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("souls").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,100)).executes(c->{cfg.enigmaSoulBeamHeight=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("effigies").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(2,100)).executes(c->{cfg.effigyBeamHeight=IntegerArgumentType.getInteger(c,"blocks");save();return status();}))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lowtime").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(10,600)).executes(c->{cfg.riftLowTimeSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int option(String raw,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(raw.toLowerCase(Locale.ROOT)){case"hud"->cfg.riftHud=value;case"time"->cfg.riftHudTime=value;case"motes"->cfg.riftHudMotes=value;case"session"->cfg.riftHudMotesSession=value;case"hudsouls"->cfg.riftHudSouls=value;case"hudeffigies"->cfg.riftHudEffigies=value;case"hudarea"->cfg.riftHudArea=value;case"souls"->cfg.enigmaSoulWaypoints=value;case"found"->cfg.enigmaSoulShowFound=value;case"nearest"->cfg.enigmaSoulNearestOnly=value;case"soulbox"->cfg.enigmaSoulBox=value;case"soulbeam"->cfg.enigmaSoulBeam=value;case"soulline"->cfg.enigmaSoulLine=value;case"soullabel"->cfg.enigmaSoulLabel=value;case"souldistance"->cfg.enigmaSoulDistance=value;case"soulwalls"->cfg.enigmaSoulThroughWalls=value;case"soulsync"->cfg.enigmaSoulGuideSync=value;case"syncchat"->cfg.enigmaSoulGuideSyncChat=value;case"menutracking"->cfg.enigmaSoulMenuTracking=value;case"menuhighlights"->cfg.enigmaSoulMenuHighlights=value;case"currentarea"->cfg.enigmaSoulCurrentAreaOnly=value;case"arealabel"->cfg.enigmaSoulAreaInLabel=value;case"mirror"->cfg.mirrorverseWaypoints=value;case"lava"->cfg.mirrorverseLavaPath=value;case"upside"->cfg.mirrorverseUpsideDown=value;case"turbulator"->cfg.mirrorverseTurbulator=value;case"mirrorboxes"->cfg.mirrorverseBoxes=value;case"mirrorlabels"->cfg.mirrorverseLabels=value;case"mirrorline"->cfg.mirrorverseNearestLine=value;case"mirrorwalls"->cfg.mirrorverseThroughWalls=value;case"effigies"->cfg.effigyWaypoints=value;case"compact"->cfg.effigyCompact=value;case"effigybox"->cfg.effigyBox=value;case"effigybeam"->cfg.effigyBeam=value;case"effigylabel"->cfg.effigyLabel=value;case"effigydistance"->cfg.effigyDistance=value;case"effigywalls"->cfg.effigyThroughWalls=value;case"alert"->cfg.riftLowTimeAlert=value;case"chat"->cfg.riftLowTimeChat=value;case"title"->cfg.riftLowTimeTitle=value;case"sound"->cfg.riftLowTimeSound=value;default->{local("Unknown Rift option.");return 0;}}save();return status();}
    private static int color(String target,String raw){Integer value=parseColor(raw);if(value==null){local("Color must be ARGB hex, such as FF55FF55.");return 0;}switch(target.toLowerCase(Locale.ROOT)){case"soulmissing"->cfg.enigmaSoulMissingColor=value;case"soulfound"->cfg.enigmaSoulFoundColor=value;case"lava"->cfg.mirrorverseLavaColor=value;case"upside"->cfg.mirrorverseUpsideColor=value;case"turbulator"->cfg.mirrorverseTurbulatorColor=value;case"effigy"->cfg.effigyColor=value;case"time"->cfg.riftTimeNormalColor=value;case"warning"->cfg.riftTimeWarningColor=value;case"danger"->cfg.riftTimeDangerColor=value;default->{local("Unknown Rift color target.");return 0;}}save();return status();}
    private static int status(){local("Time "+(timeSeconds<0?"unknown":duration(timeSeconds))+", Motes "+(motes<0?"unknown":number(motes))+", Souls "+found().size()+"/"+SOULS.size()+", Effigies "+unbrokenEffigies.size()+".");return 1;}
    private static void resetVisit(){timeSeconds=-1;maxTimeSeconds=0;motes=-1;lastMotes=-1;motesSession=0;lowWarned=false;lastArea="";lastGuideSignature="";unbrokenEffigies.clear();}
    private static void resetWorld(){resetVisit();}
    private static void save(){ConstellationClient.saveConfig();}
    private static int num(String value){if(value==null||value.isBlank())return 0;try{return Integer.parseInt(value);}catch(Exception ignored){return 0;}}
    private static int parseTime(String text){Matcher matcher=TIME_CLOCK.matcher(text);if(!matcher.find())return-1;if(matcher.group(4)!=null)return num(matcher.group(4))*60+num(matcher.group(5));return num(matcher.group(1))*3600+num(matcher.group(2))*60+num(matcher.group(3));}
    private static Integer parseColor(String raw){try{String value=raw.trim().replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;return(int)parsed;}catch(Exception ignored){return null;}}
    private static String duration(int seconds){return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static String number(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Rift] §f"+text));}
}
