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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        PATTERNS.put("serverId",Pattern.compile("(?:\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+)?((?:mini|mega)[A-Za-z0-9]+)",Pattern.CASE_INSENSITIVE));
        PATTERNS.put("playerCount",Pattern.compile("(?:Players|Guests):\\s*([\\d,]+)",Pattern.CASE_INSENSITIVE));
        PATTERNS.put("playerMax",Pattern.compile("(?:Players|Guests):\\s*[\\d,]+\\s*/\\s*([\\d,]+)",Pattern.CASE_INSENSITIVE));
        dynamic("date","SkyBlock Date");dynamic("time","SkyBlock Time");dynamic("lobby","Lobby");dynamic("players","Players");dynamic("events","Events");dynamic("footer","Footer");
    }
    private ApolloCustomScoreboard(){}
    private static void add(String id,String label,String regex){LABELS.put(id,label);PATTERNS.put(id,Pattern.compile(regex,Pattern.CASE_INSENSITIVE));}
    private static void dynamic(String id,String label){LABELS.put(id,label);}

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
        Map<String,Line> values=new LinkedHashMap<>();Map<String,List<Line>> dynamic=dynamicLines();
        if(!cfg.customScoreboardUseCustomLines)return filtered(server);
        for(var entry:LABELS.entrySet()){
            if(dynamic.containsKey(entry.getKey()))continue;
            String value=cfg.customScoreboardCachedValues.getOrDefault(entry.getKey(),"");
            if(value.isBlank()&&cfg.customScoreboardHideEmptyLines)continue;
            values.put(entry.getKey(),new Line(entry.getKey(),entry.getValue(),value.isBlank()?"-":value));
        }
        Map<String,Line> byId=new LinkedHashMap<>();for(Line line:server)byId.put(line.id(),line);byId.putAll(values);
        List<Line> out=new ArrayList<>();Set<String> placed=new LinkedHashSet<>();
        for(String id:cfg.customScoreboardOrder){
            if(id==null||id.isBlank()||cfg.customScoreboardHidden.contains(id))continue;
            if(id.equals("server")){for(Line line:server)if(!cfg.customScoreboardHidden.contains(line.id())){out.add(line);placed.add(line.id());}placed.add(id);continue;}
            List<Line> group=dynamic.get(id);if(group!=null){out.addAll(group);placed.add(id);continue;}
            Line line=byId.get(id);if(line!=null){out.add(line);placed.add(id);}
        }
        for(Line line:byId.values())if(!placed.contains(line.id())&&!cfg.customScoreboardHidden.contains(line.id()))out.add(line);
        for(var entry:dynamic.entrySet())if(!placed.contains(entry.getKey())&&!cfg.customScoreboardHidden.contains(entry.getKey()))out.addAll(entry.getValue());
        return filtered(out).stream().limit(Math.clamp(cfg.customScoreboardMaxRows,5,60)).toList();
    }

    // ported from SkyHanni (LGPL-3.0-or-later): features/gui/customscoreboard/elements
    private static Map<String,List<Line>> dynamicLines(){
        Map<String,List<Line>> out=new LinkedHashMap<>();
        if(cfg.customScoreboardShowDate)out.put("date",List.of(new Line("date",null,skyblockDate())));
        if(cfg.customScoreboardShowTime)out.put("time",List.of(new Line("time",null,skyblockTime())));
        if(cfg.customScoreboardShowLobby){String server=cfg.customScoreboardCachedValues.getOrDefault("serverId","");String date=cfg.customScoreboardLobbyRealDate?LocalDate.now().format(realDateFormatter()):"";String value=(date+(date.isBlank()||server.isBlank()?"":" ")+server).trim();if(!value.isBlank()||!cfg.customScoreboardHideEmptyLines)out.put("lobby",List.of(new Line("lobby",null,value.isBlank()?"-":value)));}
        if(cfg.customScoreboardShowPlayers){Minecraft mc=Minecraft.getInstance();int players=parseNumber(cfg.customScoreboardCachedValues.get("playerCount"),mc.getConnection()==null?0:mc.getConnection().getOnlinePlayers().size());int learnedMax=parseNumber(cfg.customScoreboardCachedValues.get("playerMax"),cfg.customScoreboardMaxPlayers);String max=cfg.customScoreboardShowMaxPlayers&&learnedMax>0?"/"+learnedMax:"";out.put("players",List.of(new Line("players","Players",players+max)));}
        if(cfg.customScoreboardShowEvents){List<String> values=CygnusCalendar.scoreboardLines(cfg.customScoreboardShowAllEvents,cfg.customScoreboardEventRows,cfg.customScoreboardShowUpcomingEvents);if(!values.isEmpty()||!cfg.customScoreboardHideEmptyLines){List<Line> lines=new ArrayList<>();for(int i=0;i<values.size();i++)lines.add(new Line("events:"+i,null,(i==0?"Events: ":"- ")+values.get(i)));if(lines.isEmpty())lines.add(new Line("events",null,"Events: -"));out.put("events",List.copyOf(lines));}}
        if(cfg.customScoreboardShowMayor){List<String> values=CygnusMayor.scoreboardLines(cfg.customScoreboardMayorPerks,cfg.customScoreboardMayorExtra,cfg.customScoreboardMayorPerkRows);if(values.isEmpty()){String cached=cfg.customScoreboardCachedValues.getOrDefault("mayor","");if(!cached.isBlank())values=List.of(cached);}if(!values.isEmpty()||!cfg.customScoreboardHideEmptyLines){List<Line> lines=new ArrayList<>();for(int i=0;i<values.size();i++)lines.add(new Line("mayor:"+i,null,(i==0?"Mayor: ":"  ")+values.get(i)));if(lines.isEmpty())lines.add(new Line("mayor",null,"Mayor: -"));out.put("mayor",List.copyOf(lines));}}
        if(cfg.customScoreboardShowParty){List<String> values=partyLines();if(!values.isEmpty()||!cfg.customScoreboardHideEmptyLines){List<Line> lines=new ArrayList<>();for(int i=0;i<values.size();i++)lines.add(new Line("party:"+i,null,values.get(i)));if(lines.isEmpty())lines.add(new Line("party",null,"Party: -"));out.put("party",List.copyOf(lines));}}
        if(cfg.customScoreboardShowFooter&&!cfg.customScoreboardFooter.isBlank())out.put("footer",List.of(new Line("footer",null,cfg.customScoreboardFooter.replace("&&","§"))));
        return out;
    }
    private static List<String> partyLines(){List<String> out=new ArrayList<>();String leader=cfg.customScoreboardCachedValues.getOrDefault("party","");String members=cfg.customScoreboardCachedValues.getOrDefault("partyMembers","");if(leader.isBlank()&&members.isBlank())return out;out.add("Party");if(cfg.customScoreboardPartyLeader&&!leader.isBlank())out.add("- "+leader+" (Leader)");if(cfg.customScoreboardPartyMembers&&!members.isBlank()){int count=0;for(String part:members.split(",")){String name=part.trim();if(name.isBlank()||name.equalsIgnoreCase(leader)||count>=Math.clamp(cfg.customScoreboardPartyRows,1,25))continue;out.add("- "+name);count++;}}return out;}
    private static DateTimeFormatter realDateFormatter(){try{return DateTimeFormatter.ofPattern(cfg.customScoreboardLobbyDateFormat);}catch(Exception ignored){return DateTimeFormatter.ofPattern("MM/dd/yy");}}
    private static String skyblockDate(){SkyTime value=skyTime();String[] seasons={"Early Spring","Spring","Late Spring","Early Summer","Summer","Late Summer","Early Autumn","Autumn","Late Autumn","Early Winter","Winter","Late Winter"};return seasons[value.month-1]+" "+value.day+suffix(value.day)+(cfg.customScoreboardDateYear?" Y"+value.year:"");}
    private static String skyblockTime(){SkyTime value=skyTime();int minute=cfg.customScoreboardTimeExactMinutes?value.minute:value.minute/10*10;if(cfg.customScoreboardTime24Hour)return String.format(Locale.ROOT,"%02d:%02d",value.hour,minute);int hour=value.hour%12;if(hour==0)hour=12;return String.format(Locale.ROOT,"%d:%02d%s",hour,minute,value.hour<12?"am":"pm");}
    private record SkyTime(int year,int month,int day,int hour,int minute){}
    private static SkyTime skyTime(){final long epoch=1559829300000L,yearMs=124L*60*60*1000,monthMs=yearMs/12,dayMs=monthMs/31,hourMs=dayMs/24,minuteMs=hourMs/60;long value=Math.max(0,System.currentTimeMillis()-epoch);int year=(int)(value/yearMs)+1;value%=yearMs;int month=(int)(value/monthMs)+1;value%=monthMs;int day=(int)(value/dayMs)+1;value%=dayMs;int hour=(int)(value/hourMs);value%=hourMs;int minute=(int)(value/minuteMs);return new SkyTime(year,month,day,hour,minute);}
    private static String suffix(int value){if(value>=11&&value<=13)return"th";return switch(value%10){case 1->"st";case 2->"nd";case 3->"rd";default->"th";};}
    private static int parseNumber(String raw,int fallback){try{return Integer.parseInt(raw.replace(",",""));}catch(Exception ignored){return fallback;}}

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
        Matcher members=Pattern.compile("Party Members?:\\s*([^\\n]+)",Pattern.CASE_INSENSITIVE).matcher(text);if(members.find()){String value=clean(members.group(1)).replaceAll("\\[[^]]+]\\s*","");if(!value.isBlank()&&!value.equals(cfg.customScoreboardCachedValues.get("partyMembers"))){cfg.customScoreboardCachedValues.put("partyMembers",value);changed=true;}}
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
    public static void resetOrder(boolean save){cfg.customScoreboardOrder=new ArrayList<>(List.of("lobby","date","time","players","server","bank","sbLevel","magicPower","tuning","powerStone","gems","quiver","godPot","events","mayor","party","election","area","purse","bits","footer"));cfg.customScoreboardHidden.clear();if(save)save();}
    public static void open(){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.setScreenAndShow(new ScoreboardOrderScreen(mc.gui.screen()));}
    public static void save(){ConstellationClient.saveConfig();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("customscoreboard").executes(c->{open();return 1;})
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("menu").executes(c->{open();return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetOrder(true);return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("title").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->{cfg.customScoreboardTitle=StringArgumentType.getString(c,"text").trim();save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("footer").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->{cfg.customScoreboardFooter=StringArgumentType.getString(c,"text").trim();save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dateformat").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("pattern",StringArgumentType.greedyString()).executes(c->{String value=StringArgumentType.getString(c,"pattern").trim();try{DateTimeFormatter.ofPattern(value);cfg.customScoreboardLobbyDateFormat=value;save();return status();}catch(Exception e){local("Invalid date format pattern.");return 0;}})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("align").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("part",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("alignment",StringArgumentType.word()).executes(c->align(StringArgumentType.getString(c,"part"),StringArgumentType.getString(c,"alignment"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0,200)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("part",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"part"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("scoreboard "+on(cfg.customScoreboard)+", vanilla hidden "+on(cfg.customScoreboardHideVanilla)+", "+lines().size()+" visible lines.");return 1;}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.customScoreboard=value;case"vanilla"->cfg.customScoreboardHideVanilla=value;case"server"->cfg.customScoreboardShowServerLines=value;case"custom"->cfg.customScoreboardUseCustomLines=value;case"background"->cfg.customScoreboardBackground=value;case"outline"->cfg.customScoreboardOutline=value;case"shadow"->cfg.customScoreboardTextShadow=value;case"empty"->cfg.customScoreboardHideEmptyLines=value;case"consecutive"->cfg.customScoreboardHideConsecutiveEmptyLines=value;case"edges"->cfg.customScoreboardHideEdgeEmptyLines=value;case"irrelevant"->cfg.customScoreboardHideIrrelevantLines=value;case"customtitle"->cfg.customScoreboardUseCustomTitle=value;case"persist"->cfg.customScoreboardPersistValues=value;case"date"->cfg.customScoreboardShowDate=value;case"year"->cfg.customScoreboardDateYear=value;case"time"->cfg.customScoreboardShowTime=value;case"time24"->cfg.customScoreboardTime24Hour=value;case"exacttime"->cfg.customScoreboardTimeExactMinutes=value;case"lobby"->cfg.customScoreboardShowLobby=value;case"lobbydate"->cfg.customScoreboardLobbyRealDate=value;case"players"->cfg.customScoreboardShowPlayers=value;case"maxplayers"->cfg.customScoreboardShowMaxPlayers=value;case"events"->cfg.customScoreboardShowEvents=value;case"allevents"->cfg.customScoreboardShowAllEvents=value;case"upcoming"->cfg.customScoreboardShowUpcomingEvents=value;case"mayor"->cfg.customScoreboardShowMayor=value;case"mayorperks"->cfg.customScoreboardMayorPerks=value;case"mayorextra"->cfg.customScoreboardMayorExtra=value;case"party"->cfg.customScoreboardShowParty=value;case"partyleader"->cfg.customScoreboardPartyLeader=value;case"partymembers"->cfg.customScoreboardPartyMembers=value;case"footer"->cfg.customScoreboardShowFooter=value;default->{local("Unknown scoreboard option.");return 0;}}save();return status();}
    private static int align(String part,String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("LEFT")&&!value.equals("CENTER")&&!value.equals("RIGHT")){local("Alignment must be left, center, or right.");return 0;}if(part.equalsIgnoreCase("title"))cfg.customScoreboardTitleAlignment=value;else if(part.equalsIgnoreCase("text"))cfg.customScoreboardTextAlignment=value;else{local("Part must be title or text.");return 0;}save();return status();}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"spacing"->cfg.customScoreboardLineSpacing=Math.clamp(value,0,8);case"rows"->cfg.customScoreboardMaxRows=Math.clamp(value,5,60);case"maxplayers"->cfg.customScoreboardMaxPlayers=Math.clamp(value,0,200);case"eventrows"->cfg.customScoreboardEventRows=Math.clamp(value,1,8);case"mayorrows"->cfg.customScoreboardMayorPerkRows=Math.clamp(value,1,20);case"partyrows"->cfg.customScoreboardPartyRows=Math.clamp(value,1,25);default->{local("Number must be spacing, rows, maxplayers, eventrows, mayorrows, or partyrows.");return 0;}}save();return status();}
    private static int color(String part,String raw){Integer value=parseColor(raw);if(value==null){local("Color must be RRGGBB or AARRGGBB.");return 0;}switch(part.toLowerCase(Locale.ROOT)){case"background"->cfg.customScoreboardBackgroundColor=value;case"outline"->cfg.customScoreboardOutlineColor=value;case"title"->cfg.customScoreboardTitleColor=value;case"server"->cfg.customScoreboardServerColor=value;case"label"->cfg.customScoreboardLabelColor=value;case"value"->cfg.customScoreboardValueColor=value;default->{local("Color part must be background, outline, title, server, label, or value.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static Integer parseColor(String raw){try{String value=raw.replace("#","");if(value.length()==6)value="FF"+value;if(value.length()!=8)return null;return(int)Long.parseLong(value,16);}catch(Exception ignored){return null;}}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§b[Scoreboard] §f"+text));}
}
