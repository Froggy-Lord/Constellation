package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ApolloConfig;
import com.froggylord.constellation.ui.ScoreboardOrderScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

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

// ported from CryptKit (GPL-3.0-only): gui/CryptScoreboard.java
// ported from CryptKit (GPL-3.0-only): feature/SidebarData.java
// filtering cross-checked with SkyHanni (LGPL-3.0-or-later): features/gui/customscoreboard/CustomScoreboard.kt
public final class ApolloCustomScoreboard {
    public record Line(String id,String label,String value) {}
    private static final Map<String,String> LABELS=new LinkedHashMap<>();
    private static final Map<String,Pattern> PATTERNS=new LinkedHashMap<>();
    private static ApolloConfig cfg;
    private static int ticks;
    static {
        add("purse","Purse","(?:Purse|Piggy):\\s*([^\\n]+)");
        add("bank","Bank","Bank:\\s*([^\\n]+)");
        add("bits","Bits","Bits:\\s*([^\\n]+)");
        add("sbLevel","SB Level","SB Level:\\s*\\[?(\\d+[^\\n]*)");
        add("magicPower","Magical Power","Magical Power:\\s*([^\\n]+)");
        add("tuning","Tuning","Tunings?:\\s*([^\\n]+)");
        add("powerStone","Power","(?:Selected Power|Power Stone):\\s*([^\\n]+)");
        add("gems","Gems","Gems:\\s*([^\\n]+)");
        add("quiver","Quiver","Quiver:\\s*([^\\n]+)");
        add("godPot","God Pot","God Pot(?:ion)?[^\\n]*?((?:\\d+[dhms:]?\\s*)+)");
        add("mayor","Mayor","(?:Mayor|Election Winner):\\s*([^\\n]+)");
        add("party","Party Leader","Party Leader:\\s*([^\\n]+)");
        add("area","Area","Area:\\s*([^\\n]+)");
        add("election","Election","Election:\\s*([^\\n]+)");
    }
    private ApolloCustomScoreboard(){}
    private static void add(String id,String label,String regex){LABELS.put(id,label);PATTERNS.put(id,Pattern.compile(regex,Pattern.CASE_INSENSITIVE));}

    public static void init(ApolloConfig config){
        cfg=config;normalize();
        ClientTickEvents.END_CLIENT_TICK.register(mc->{if(mc.player==null||++ticks%10!=0)return;scan(tabText(mc));scan(sidebarText(mc));if(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)scan(menuText(screen));});
    }

    public static boolean active(){return cfg!=null&&cfg.enabled&&cfg.customScoreboard&&ConstellationClient.loc().onHypixel();}
    public static boolean hideVanilla(){return active()&&cfg.customScoreboardHideVanilla;}
    public static ApolloConfig config(){return cfg;}
    public static Map<String,String> labels(){return Map.copyOf(LABELS);}

    public static List<Line> lines(){
        if(cfg==null)return List.of();normalize();
        List<Line> server=cfg.customScoreboardShowServerLines?serverLines():List.of();
        Map<String,Line> values=new LinkedHashMap<>();
        if(!cfg.customScoreboardUseCustomLines)return filtered(server);
        for(var entry:LABELS.entrySet()){
            String value=cfg.customScoreboardCachedValues.getOrDefault(entry.getKey(),"");
            if(value.isBlank()&&cfg.customScoreboardHideEmptyLines)continue;
            values.put(entry.getKey(),new Line(entry.getKey(),entry.getValue(),value.isBlank()?"-":value));
        }
        Map<String,Line> byId=new LinkedHashMap<>();for(Line line:server)byId.put(line.id(),line);byId.putAll(values);
        List<Line> out=new ArrayList<>();Set<String> placed=new LinkedHashSet<>();
        for(String id:cfg.customScoreboardOrder){
            if(id==null||id.isBlank()||cfg.customScoreboardHidden.contains(id))continue;
            if(id.equals("server")){for(Line line:server)if(!cfg.customScoreboardHidden.contains(line.id())){out.add(line);placed.add(line.id());}placed.add(id);continue;}
            Line line=byId.get(id);if(line!=null){out.add(line);placed.add(id);}
        }
        for(Line line:byId.values())if(!placed.contains(line.id())&&!cfg.customScoreboardHidden.contains(line.id()))out.add(line);
        return filtered(out).stream().limit(Math.clamp(cfg.customScoreboardMaxRows,5,60)).toList();
    }

    private static List<Line> filtered(List<Line> input){
        ArrayList<Line> out=new ArrayList<>();boolean blank=false;
        for(Line line:input){
            String value=line.value()==null?"":line.value().trim();
            if(cfg.customScoreboardHideIrrelevantLines&&line.label()==null&&duplicateCustom(value))continue;
            boolean empty=value.isBlank();
            if(empty&&cfg.customScoreboardHideConsecutiveEmptyLines&&blank)continue;
            out.add(line);blank=empty;
        }
        if(cfg.customScoreboardHideEdgeEmptyLines){while(!out.isEmpty()&&out.get(0).value().isBlank())out.remove(0);while(!out.isEmpty()&&out.get(out.size()-1).value().isBlank())out.remove(out.size()-1);}
        return List.copyOf(out);
    }
    private static boolean duplicateCustom(String value){
        if(!cfg.customScoreboardUseCustomLines)return false;
        String clean=clean(value).toLowerCase(Locale.ROOT);
        for(String label:LABELS.values())if(clean.startsWith(label.toLowerCase(Locale.ROOT)+":"))return true;
        return false;
    }

    public static String title(){
        if(cfg.customScoreboardUseCustomTitle&&!cfg.customScoreboardTitle.isBlank())return cfg.customScoreboardTitle;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return"SKYBLOCK";Objective objective=mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective==null?"SKYBLOCK":clean(objective.getDisplayName().getString());
    }

    private static List<Line> serverLines(){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return List.of();Scoreboard board=mc.level.getScoreboard();Objective objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);if(objective==null)return List.of();
        List<PlayerScoreEntry> entries=new ArrayList<>(board.listPlayerScores(objective));entries.removeIf(PlayerScoreEntry::isHidden);entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());if(entries.size()>15)entries=entries.subList(0,15);
        ArrayList<Line> out=new ArrayList<>();Set<String> used=new LinkedHashSet<>();
        for(PlayerScoreEntry entry:entries){PlayerTeam team=board.getPlayersTeam(entry.owner());String text=clean(PlayerTeam.formatNameForTeam(team,entry.ownerName()).getString());if(text.isBlank()){out.add(new Line(unique("srv:blank",used),null,""));continue;}out.add(new Line(unique("srv:"+key(text),used),null,text));}
        return out;
    }
    private static String unique(String base,Set<String> used){String value=base;while(!used.add(value))value+="_";return value;}
    private static String key(String text){String value=text.replaceAll("[0-9,.:%/]","").replaceAll("[^a-zA-Z ]","").trim().toLowerCase(Locale.ROOT);return value.isEmpty()?"line":value.replace(' ','_');}

    private static void scan(String text){
        if(text.isBlank())return;boolean changed=false;
        for(var entry:PATTERNS.entrySet()){Matcher matcher=entry.getValue().matcher(text);if(!matcher.find())continue;String value=clean(matcher.group(1));if(value.isBlank()||value.equals(cfg.customScoreboardCachedValues.get(entry.getKey())))continue;cfg.customScoreboardCachedValues.put(entry.getKey(),value);changed=true;}
        Matcher mayor=Pattern.compile("^([A-Z][a-z]{2,}):\\s*\\|+",Pattern.MULTILINE).matcher(text);if(mayor.find()&&!mayor.group(1).equals(cfg.customScoreboardCachedValues.get("mayor"))){cfg.customScoreboardCachedValues.put("mayor",mayor.group(1));changed=true;}
        if(changed&&cfg.customScoreboardPersistValues)ConstellationClient.saveConfig();
    }
    private static String tabText(Minecraft mc){if(mc.getConnection()==null)return"";StringBuilder out=new StringBuilder();for(PlayerInfo info:mc.getConnection().getOnlinePlayers()){Component display=info.getTabListDisplayName();if(display!=null)out.append(clean(display.getString())).append('\n');}return out.toString();}
    private static String sidebarText(Minecraft mc){StringBuilder out=new StringBuilder();for(Line line:serverLines())out.append(line.value()).append('\n');return out.toString();}
    private static String menuText(AbstractContainerScreen<?> screen){StringBuilder out=new StringBuilder(clean(screen.getTitle().getString())).append('\n');int count=Math.max(0,screen.getMenu().slots.size()-36);for(int i=0;i<count;i++){ItemStack stack=screen.getMenu().slots.get(i).getItem();if(stack.isEmpty())continue;Component name=stack.get(DataComponents.CUSTOM_NAME);if(name==null)name=stack.get(DataComponents.ITEM_NAME);if(name!=null)out.append(clean(name.getString())).append('\n');ItemLore lore=stack.get(DataComponents.LORE);if(lore!=null)for(Component line:lore.lines())out.append(clean(line.getString())).append('\n');}return out.toString();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}

    private static void normalize(){
        if(cfg.customScoreboardOrder==null)cfg.customScoreboardOrder=new ArrayList<>();if(cfg.customScoreboardHidden==null)cfg.customScoreboardHidden=new LinkedHashSet<>();if(cfg.customScoreboardCachedValues==null)cfg.customScoreboardCachedValues=new LinkedHashMap<>();
        if(cfg.customScoreboardOrder.isEmpty())resetOrder(false);
    }
    public static void resetOrder(boolean save){cfg.customScoreboardOrder=new ArrayList<>(List.of("server","bank","sbLevel","magicPower","tuning","powerStone","gems","quiver","godPot","mayor","party","election","area","purse","bits"));cfg.customScoreboardHidden.clear();if(save)save();}
    public static void open(){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.setScreenAndShow(new ScoreboardOrderScreen(mc.gui.screen()));}
    public static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("customscoreboard").executes(c->{open();return 1;})
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("menu").executes(c->{open();return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetOrder(true);return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("title").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->{cfg.customScoreboardTitle=StringArgumentType.getString(c,"text").trim();save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("align").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("part",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("alignment",StringArgumentType.word()).executes(c->align(StringArgumentType.getString(c,"part"),StringArgumentType.getString(c,"alignment"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0,60)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("part",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"part"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("scoreboard "+on(cfg.customScoreboard)+", vanilla hidden "+on(cfg.customScoreboardHideVanilla)+", "+lines().size()+" visible lines.");return 1;}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.customScoreboard=value;case"vanilla"->cfg.customScoreboardHideVanilla=value;case"server"->cfg.customScoreboardShowServerLines=value;case"custom"->cfg.customScoreboardUseCustomLines=value;case"background"->cfg.customScoreboardBackground=value;case"outline"->cfg.customScoreboardOutline=value;case"shadow"->cfg.customScoreboardTextShadow=value;case"empty"->cfg.customScoreboardHideEmptyLines=value;case"consecutive"->cfg.customScoreboardHideConsecutiveEmptyLines=value;case"edges"->cfg.customScoreboardHideEdgeEmptyLines=value;case"irrelevant"->cfg.customScoreboardHideIrrelevantLines=value;case"customtitle"->cfg.customScoreboardUseCustomTitle=value;case"persist"->cfg.customScoreboardPersistValues=value;default->{local("Unknown scoreboard option.");return 0;}}save();return status();}
    private static int align(String part,String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("LEFT")&&!value.equals("CENTER")&&!value.equals("RIGHT")){local("Alignment must be left, center, or right.");return 0;}if(part.equalsIgnoreCase("title"))cfg.customScoreboardTitleAlignment=value;else if(part.equalsIgnoreCase("text"))cfg.customScoreboardTextAlignment=value;else{local("Part must be title or text.");return 0;}save();return status();}
    private static int number(String name,int value){if(name.equalsIgnoreCase("spacing"))cfg.customScoreboardLineSpacing=Math.clamp(value,0,8);else if(name.equalsIgnoreCase("rows"))cfg.customScoreboardMaxRows=Math.clamp(value,5,60);else{local("Number must be spacing or rows.");return 0;}save();return status();}
    private static int color(String part,String raw){Integer value=parseColor(raw);if(value==null){local("Color must be RRGGBB or AARRGGBB.");return 0;}switch(part.toLowerCase(Locale.ROOT)){case"background"->cfg.customScoreboardBackgroundColor=value;case"outline"->cfg.customScoreboardOutlineColor=value;case"title"->cfg.customScoreboardTitleColor=value;case"server"->cfg.customScoreboardServerColor=value;case"label"->cfg.customScoreboardLabelColor=value;case"value"->cfg.customScoreboardValueColor=value;default->{local("Color part must be background, outline, title, server, label, or value.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static Integer parseColor(String raw){try{String value=raw.replace("#","");if(value.length()==6)value="FF"+value;if(value.length()!=8)return null;return(int)Long.parseLong(value,16);}catch(Exception ignored){return null;}}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§b[Scoreboard] §f"+text));}
}
