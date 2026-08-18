package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CommissionLabels.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/CommsWidget.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CrystalsHudWidget.java
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/MineshaftPityDisplay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/glacitemineshaft/MineshaftCaveInTimer.kt
// ported from SkyOcean (MIT): features/mining/mineshaft/PityMessage.kt
public final class AquilaMiningProgress {
    public record Commission(String name, String progress, boolean done) {}
    public record Crystal(String name, String state, String location, boolean found) {}
    public record Pity(int current, int maximum, long lastFoundMillis) {}
    public record ShaftTimer(long elapsedMillis, long entranceRemainingMillis, int cold, long coldRemainingMillis) {}

    private static final Pattern COMMISSION = Pattern.compile("^(?<name>.+?):\\s*(?<progress>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PITY = Pattern.compile("^Glacite Mineshafts:\\s*(?<current>[0-9,]+)\\s*/\\s*(?<max>[0-9,]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLD = Pattern.compile("^Cold:?\\s*-?(?<cold>[0-9]{1,3})(?:%|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRYSTAL = Pattern.compile("^(?<name>Jade|Amber|Sapphire|Topaz|Amethyst):\\s*(?<state>.+)$", Pattern.CASE_INSENSITIVE);
    private static final String SHAFT_FOUND = "WOW! You found a Glacite Mineshaft portal!";
    private static AquilaConfig cfg;
    private static List<Commission> commissions = List.of();
    private static List<Crystal> crystals = List.of();
    private static int pityCurrent = -1;
    private static int pityMaximum = 2000;
    private static long lastFound;
    private static long shaftEntered;
    private static long firstColdAt;
    private static int currentCold;
    private static int lastColdValue = -1;
    private static int totalColdGained;
    private static SkyblockArea lastArea = SkyblockArea.UNKNOWN;
    private static String profileKey = "";

    private AquilaMiningProgress() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        ConstellationClient.tick().every(20, "aquila-mining-progress", AquilaMiningProgress::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
            return true;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void tick() {
        String currentProfile = LyraStorageValue.currentProfileKey();
        if (!currentProfile.equals(profileKey)) {
            profileKey = currentProfile;
            pityCurrent = -1;
            pityMaximum = Math.max(1, cfg.miningMineshaftPityMaximum);
            lastFound = 0;
            clearShaft();
        }
        SkyblockArea area = ConstellationClient.loc().area();
        if (area != lastArea) {
            if (area == SkyblockArea.GLACITE_MINESHAFT) startShaft();
            else if (lastArea == SkyblockArea.GLACITE_MINESHAFT) clearShaft();
            lastArea = area;
        }
        List<String> tab = TabList.lines();
        commissions = parseCommissions(tab);
        crystals = parseCrystals(tab);
        boolean pitySeen = false;
        for (String line : tab) {
            Matcher pity = PITY.matcher(line.trim());
            if (pity.matches()) {
                pitySeen = true;
                pityCurrent = number(pity.group("current"), -1);
                pityMaximum = number(pity.group("max"), Math.max(1, cfg.miningMineshaftPityMaximum));
            }
        }
        if (pityArea() && !pitySeen) pityCurrent = -1;
        int cold = readCold();
        if (area == SkyblockArea.GLACITE_MINESHAFT && cold != currentCold) {
            if (firstColdAt == 0 && cold > 0) { firstColdAt = System.currentTimeMillis(); lastColdValue = cold; }
            else if (lastColdValue >= 0 && cold > lastColdValue) totalColdGained += cold - lastColdValue;
            lastColdValue = cold;
            currentCold = cold;
        }
    }

    private static List<Commission> parseCommissions(List<String> tab) {
        List<Commission> out = new ArrayList<>(); boolean section=false;
        for(String line:tab){String s=line.trim();if(s.startsWith("Commissions")){section=true;continue;}if(!section)continue;Matcher m=COMMISSION.matcher(s);if(!m.matches()){if(!out.isEmpty())break;continue;}String progress=m.group("progress");out.add(new Commission(m.group("name").trim(),progress,progress.equalsIgnoreCase("DONE")));}
        return List.copyOf(out);
    }

    private static List<Crystal> parseCrystals(List<String> tab) {
        List<Crystal> out=new ArrayList<>();boolean section=false;
        for(String line:tab){String s=line.trim();if(s.equalsIgnoreCase("Crystals:")){section=true;continue;}if(!section)continue;Matcher m=CRYSTAL.matcher(s);if(!m.matches()){if(!out.isEmpty())break;continue;}String raw=m.group("state").trim();boolean found=!raw.toLowerCase(Locale.ROOT).contains("not found");String state=raw.replaceAll("[^A-Za-z0-9 .%/\\-]","").trim();out.add(new Crystal(title(m.group("name"))+" Crystal",state,location(m.group("name")),found));}
        return List.copyOf(out);
    }

    private static void onChat(String message) {
        if (!message.contains(SHAFT_FOUND)) return;
        lastFound=System.currentTimeMillis();
        if(cfg.miningPityModifyFoundChat&&pityCurrent>=0)local("Mineshaft found at " + pityCurrent + "/" + pityMaximum + " pity. Tab values can lag by about three seconds.");
    }

    public static List<Commission> commissions(){return miningArea()?commissions:List.of();}
    public static List<Crystal> crystals(){return ConstellationClient.loc().area()==SkyblockArea.CRYSTAL_HOLLOWS?crystals:List.of();}
    public static Pity pity(){if(!pityArea()||pityCurrent<0)return null;return new Pity(pityCurrent,pityMaximum,lastFound);}
    public static ShaftTimer timer(){if(ConstellationClient.loc().area()!=SkyblockArea.GLACITE_MINESHAFT||shaftEntered==0)return null;long now=System.currentTimeMillis(),elapsed=now-shaftEntered,entrance=Math.max(0,cfg.miningMineshaftEntranceSeconds*1000L-elapsed);long coldLeft=-1;if(firstColdAt>0&&totalColdGained>0){double rate=totalColdGained/Math.max(1.0,(now-firstColdAt)/1000.0);coldLeft=Math.round((100-currentCold)/rate*1000.0);}return new ShaftTimer(elapsed,entrance,currentCold,coldLeft);}
    public static AquilaConfig config(){return cfg;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningprogress").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{lastFound=0;clearShaft();local("Mining session state reset.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pitymax").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(1,10000)).executes(c->{cfg.miningMineshaftPityMaximum=IntegerArgumentType.getInteger(c,"value");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("entranceseconds").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,600)).executes(c->{cfg.miningMineshaftEntranceSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningprogresscolor")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word())
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word())
                    .executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("miningprogressthresholds")
            .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("caution",IntegerArgumentType.integer(1,599))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("warning",IntegerArgumentType.integer(0,598))
                    .executes(c->thresholds(IntegerArgumentType.getInteger(c,"caution"),IntegerArgumentType.getInteger(c,"warning"))))));
    }

    private static int status(){local("Commissions " + commissions.size()+", crystals "+crystals.size()+", pity "+(pityCurrent<0?"unknown":pityCurrent+"/"+pityMaximum)+", mineshaft timer "+(shaftEntered==0?"inactive":time(System.currentTimeMillis()-shaftEntered))+".");return 1;}
    private static int thresholds(int caution,int warning){if(warning>=caution){local("Warning must be lower than caution.");return 0;}cfg.miningTimerCautionSeconds=caution;cfg.miningTimerWarningSeconds=warning;save();local("Mineshaft thresholds set to "+caution+" and "+warning+" seconds.");return 1;}
    private static int color(String target,String value){Integer parsed=parseColor(value);if(parsed==null){local("Color must be an eight-digit ARGB hex value.");return 0;}switch(target.toLowerCase(Locale.ROOT)){case"pity"->cfg.miningPityColor=parsed;case"good"->cfg.miningGoodColor=parsed;case"warning"->cfg.miningWarningColor=parsed;case"danger"->cfg.miningDangerColor=parsed;default->{local("Color target must be pity, good, warning, or danger.");return 0;}}save();local("Mining color updated.");return 1;}
    private static Integer parseColor(String text){String s=text.startsWith("#")?text.substring(1):text;if(s.length()!=8)return null;try{return(int)Long.parseLong(s,16);}catch(NumberFormatException ignored){return null;}}
    private static int readCold(){for(String line:ConstellationClient.loc().getSidebarLines()){Matcher m=COLD.matcher(line.trim());if(m.find())return Math.clamp(number(m.group("cold"),0),0,100);}return 0;}
    private static boolean miningArea(){SkyblockArea a=ConstellationClient.loc().area();return a==SkyblockArea.DWARVEN_MINES||a==SkyblockArea.GLACITE_TUNNELS||a==SkyblockArea.GLACITE_MINESHAFT;}
    private static boolean pityArea(){SkyblockArea a=ConstellationClient.loc().area();return a==SkyblockArea.DWARVEN_MINES||a==SkyblockArea.GLACITE_TUNNELS;}
    private static void startShaft(){shaftEntered=System.currentTimeMillis();firstColdAt=0;currentCold=readCold();lastColdValue=currentCold;totalColdGained=0;}
    private static void clearShaft(){shaftEntered=0;firstColdAt=0;currentCold=0;lastColdValue=-1;totalColdGained=0;}
    private static void reset(){commissions=List.of();crystals=List.of();pityCurrent=-1;pityMaximum=Math.max(1,cfg==null?2000:cfg.miningMineshaftPityMaximum);lastFound=0;lastArea=SkyblockArea.UNKNOWN;profileKey="";clearShaft();}
    private static int number(String s,int fallback){try{return Integer.parseInt(s.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String clean(String s){String value=ChatFormatting.stripFormatting(s);return value==null?"":value.trim();}
    private static String title(String s){return s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1).toLowerCase(Locale.ROOT);}
    private static String location(String crystal){return switch(crystal.toLowerCase(Locale.ROOT)){case"jade"->"Mines of Divan";case"amber"->"Goblin Queen's Den";case"sapphire"->"Lost Precursor City";case"topaz"->"Khazad-dum";case"amethyst"->"Jungle Temple";default->"Unknown";};}
    public static String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private static void local(String message){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[Mining] §f"+message));}
    private static void save(){ConstellationClient.saveConfig();}
}
