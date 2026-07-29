package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestSpawn.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestFinder.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PestSpawnTimer.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenPlotApi.kt
// ported from Devonian (GPL-3.0): features/garden/PestKillsTracker.kt
// ported from Devonian (GPL-3.0): features/garden/PestDropTracker.kt
public final class HerculesPests {
    public record State(int alive, List<Integer> plots, Map<Integer,Integer> plotCounts, String cooldown,
                        boolean ready, boolean maxPests, long lastSpawnAt, long averageSpawnMillis) {}
    public record Stats(long sessionKills, long lifetimeKills, long sessionDrops, long lifetimeDrops,
                        double sessionProfit, double lifetimeProfit, long elapsedMillis, String lastPest, String lastDrop) {}

    private static final Pattern ONE = Pattern.compile("^\\w+! A \\S+ Pest has appeared in (?:Plot - )?(?<plot>.*)!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern MANY = Pattern.compile("^\\w+! (?<amount>\\d+) \\S+ Pests? have spawned in (?:Plot - )?(?<plot>.*)!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFLINE = Pattern.compile("^\\w+! While you were offline, \\S+ Pests? spawned in Plots (?<plots>.*)!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern KILL = Pattern.compile("^You received (?<amount>[0-9,]+)x (?<item>.*) for killing an? (?<pest>.*)!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_PESTS = Pattern.compile("^There are not any Pests on your Garden right now! Keep farming!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIVE = Pattern.compile("^\\s*Alive: (?<amount>[0-9,]+).*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PLOTS = Pattern.compile("^\\s*Plots: (?<plots>[0-9, ]+).*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern COOLDOWN = Pattern.compile("^\\s*Cooldown: (?:(?<time>\\d{1,2}[ms](?: \\d{1,2}s?)?)|(?<ready>READY)|(?<max>MAX PESTS)).*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_TOTAL = Pattern.compile("The Garden.*x(?<amount>\\d+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_PLOT = Pattern.compile("Plot\\s*-\\s*(?<plot>.+?)\\s+.*x(?<amount>\\d+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_CURRENT = Pattern.compile("^Plot\\s*-\\s*(?<plot>.+?)$",Pattern.CASE_INSENSITIVE);
    private static final int[][] PLOT_IDS={{21,13,9,14,22},{15,5,1,6,16},{10,2,0,3,11},{17,7,4,8,18},{23,19,12,20,24}};
    private static final Map<String,String> ITEM_IDS=itemIds();
    private static HerculesConfig cfg;
    private static final Map<Integer,Integer> plotCounts=new LinkedHashMap<>();
    private static final Deque<Long> spawnIntervals=new ArrayDeque<>();
    private static final Map<String,Long> sessionKills=new LinkedHashMap<>(),sessionDrops=new LinkedHashMap<>();
    private static final Map<String,Long> pendingPrices=new LinkedHashMap<>();
    private static int alive;
    private static boolean ready,maxPests,warned;
    private static String cooldown="Unknown",lastPest="",lastDrop="";
    private static long cooldownEnd,lastSpawnAt,lastHeldAt,sessionStarted,lastSaveAt;
    private static double sessionProfit;
    private static Object levelIdentity;

    private HerculesPests(){}

    public static void init(HerculesConfig config){
        cfg=config;
        ClientReceiveMessageEvents.GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));});
        ConstellationClient.tick().every(10,"hercules-pests",HerculesPests::tick);
    }

    private static void onChat(String line){
        if(!active()||line.isBlank())return;
        Matcher m=ONE.matcher(line);if(m.matches()){spawn(1,List.of(m.group("plot")));return;}
        m=MANY.matcher(line);if(m.matches()){spawn(number(m.group("amount")),List.of(m.group("plot")));return;}
        m=OFFLINE.matcher(line);if(m.matches()){spawn(0,splitPlots(m.group("plots")));return;}
        m=KILL.matcher(line);if(m.matches()){kill(number(m.group("amount")),m.group("item"),m.group("pest"));return;}
        if(NO_PESTS.matcher(line).matches()){alive=0;plotCounts.clear();if(cfg.pestNoPestsTitle)title("No pests!",0xFFFF55);}
    }

    private static void spawn(int amount,List<String> names){
        long now=System.currentTimeMillis();
        if(lastSpawnAt>0&&now-lastSpawnAt<=Math.max(1,cfg.pestTimerAverageTimeoutSeconds)*1000L){spawnIntervals.addLast(now-lastSpawnAt);while(spawnIntervals.size()>20)spawnIntervals.removeFirst();}
        lastSpawnAt=now;warned=false;ready=false;maxPests=false;
        if(cfg.pestTimerCustomCooldown)cooldownEnd=now+Math.max(1,cfg.pestTimerCustomCooldownSeconds)*1000L;
        if(amount>0)alive=Math.min(Math.max(1,cfg.pestMaxCount),alive+amount);
        for(String name:names){Integer id=plotId(name);if(id!=null)plotCounts.merge(id,Math.max(1,amount),Integer::sum);}
        if(amount<=0)return;
        String plot=names.isEmpty()?"unknown plot":names.getFirst();
        String text=cfg.pestSpawnTemplate.replace("{amount}",Integer.toString(amount)).replace("{plot}",plot)
            .replace("{pest-word}",amount==1?"pest":"pests");
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.pestSpawnTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTimes(0,Math.clamp(cfg.pestSpawnTitleTicks,10,300),10);mc.gui.hud.setTitle(Component.literal(text).withColor(0x55FF55));}
        if(cfg.pestSpawnChat&&cfg.pestCompactSpawnChat)local(text+".");
        if(cfg.pestSpawnSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.8f,1.25f);
    }

    private static void kill(int amount,String item,String pest){
        if(sessionStarted==0)sessionStarted=System.currentTimeMillis();
        lastPest=clean(pest);lastDrop=clean(item);alive=Math.max(0,alive-1);
        decrementCurrentPlot();
        add(sessionKills,lastPest,1);add(cfg.pestLifetimeKills,lastPest,1);
        add(sessionDrops,lastDrop,amount);add(cfg.pestLifetimeDrops,lastDrop,amount);
        String id=ITEM_IDS.getOrDefault(normal(lastDrop),normal(lastDrop));double price=PriceProvider.sellValue(id);
        if(price<=0){PriceProvider.warm(id);pendingPrices.merge(id,(long)amount,Long::sum);}else addProfit(price*amount);
        saveSoon();
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetSession();}
        if(!active())return;
        long now=System.currentTimeMillis();if(holdingRelevant())lastHeldAt=now;
        readData();
        reconcilePrices();
        if(cfg.pestTimerCustomCooldown&&lastSpawnAt>0&&!ready&&!maxPests)cooldownEnd=lastSpawnAt+Math.max(1,cfg.pestTimerCustomCooldownSeconds)*1000L;
        if(cfg.pestTimerWarning&&!warned&&!maxPests&&cooldownEnd>0&&cooldownEnd-now<=Math.max(0,cfg.pestTimerWarningSeconds)*1000L){warned=true;warning();}
        if(cfg.pestStatsPersistent&&now-lastSaveAt>30000&&(!sessionKills.isEmpty()||!sessionDrops.isEmpty()))saveSoon();
    }

    private static void readData(){
        List<String> tab=TabList.lines();
        for(String line:tab){Matcher m=ALIVE.matcher(line);if(m.matches()){alive=number(m.group("amount"));continue;}m=PLOTS.matcher(line);if(m.matches())syncPlots(m.group("plots"));m=COOLDOWN.matcher(line);if(m.matches()){ready=m.group("ready")!=null;maxPests=m.group("max")!=null;cooldown=ready?"Ready":maxPests?"Max pests":m.group("time");if(!ready&&!maxPests){long duration=parseDuration(cooldown);if(duration>=0)cooldownEnd=System.currentTimeMillis()+duration;}else cooldownEnd=0;}}
        for(String line:ConstellationClient.loc().getSidebarLines()){String s=clean(line);Matcher m=SCORE_TOTAL.matcher(s);if(m.find())alive=number(m.group("amount"));m=SCORE_PLOT.matcher(s);if(m.find()){learnCurrentPlot(m.group("plot"));Integer id=plotId(m.group("plot"));if(id!=null)plotCounts.put(id,number(m.group("amount")));continue;}m=SCORE_CURRENT.matcher(s);if(m.matches())learnCurrentPlot(m.group("plot"));}
        if(alive==0)plotCounts.clear();
    }

    private static void syncPlots(String text){Set<Integer> found=new LinkedHashSet<>();for(String part:text.split(",")){Integer id=plotId(part);if(id!=null)found.add(id);}plotCounts.keySet().removeIf(id->!found.contains(id));for(int id:found)plotCounts.putIfAbsent(id,1);}
    public static State state(){if(!active())return null;return new State(alive,List.copyOf(plotCounts.keySet()),Map.copyOf(plotCounts),cooldown,ready,maxPests,lastSpawnAt,average());}
    public static Stats stats(){if(cfg==null||!cfg.enabled||!cfg.pestCore||cfg.pestStatsOnlyInGarden&&!inGarden())return null;long lifeKills=cfg.pestLifetimeKills.values().stream().mapToLong(Long::longValue).sum(),lifeDrops=cfg.pestLifetimeDrops.values().stream().mapToLong(Long::longValue).sum();return new Stats(total(sessionKills),lifeKills,total(sessionDrops),lifeDrops,sessionProfit,cfg.pestLifetimeProfit,sessionStarted==0?0:System.currentTimeMillis()-sessionStarted,lastPest,lastDrop);}
    public static HerculesConfig config(){return cfg;}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!cfg.pestFinderWorld||!showFinder())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;Integer current=plotAt(mc.player.getX(),mc.player.getZ());
        for(var entry:plotCounts.entrySet()){int id=entry.getKey(),amount=entry.getValue();Vec3 middle=middle(id);if(middle==null)continue;boolean here=Objects.equals(id,current);int color=here&&cfg.pestFinderCurrentPlotRed?cfg.pestFinderCurrentColor:cfg.pestFinderBorderColor;
            if(cfg.pestFinderBorders)ctx.outline(new AABB(middle.x-48,0,middle.z-48,middle.x+48,256,middle.z+48),color,cfg.pestFinderThroughWalls,3f);
            if(cfg.pestFinderNames)ctx.label(new Vec3(middle.x,Math.max(12,mc.player.getY()+1),middle.z),amount+(amount==1?" pest":" pests")+" in Plot "+id,cfg.pestFinderLabelColor,cfg.pestFinderThroughWalls);
        }
    }

    private static boolean showFinder(){return !cfg.pestFinderOnlyWithVacuum||holdingVacuum()||cfg.pestFinderKeepAfterHeld&&System.currentTimeMillis()-lastHeldAt<=Math.max(0,cfg.pestFinderHoldSeconds)*1000L;}
    public static boolean showTimer(){return active()&&(!cfg.pestTimerOnlyWithTool||holdingRelevant());}
    private static boolean holdingRelevant(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;String id=LyraTooltips.marketId(mc.player.getMainHandItem());if(id==null)return false;id=id.toUpperCase(Locale.ROOT);return id.contains("VACUUM")||id.contains("LASSO")||id.contains("HOE")||id.contains("DICER")||id.contains("CUTTER")||id.contains("CHOPPER")||id.contains("FARMING");}
    private static boolean holdingVacuum(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;return isVacuum(mc.player.getMainHandItem());}
    private static boolean isVacuum(ItemStack stack){String id=LyraTooltips.marketId(stack);return id!=null&&(id.contains("VACUUM")||id.contains("LASSO"));}
    private static void warning(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;String text=cooldownEnd<=System.currentTimeMillis()?"Pest cooldown is ready!":"Pest cooldown expires soon!";if(cfg.pestTimerWarningChat)local(text);if(cfg.pestTimerWarningTitle)title(text,0xFF5555);if(cfg.pestTimerWarningSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,.8f);if(cfg.pestTimerRepeatWarning&&cooldownEnd>System.currentTimeMillis())warned=false;}
    private static void title(String text,int color){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(text).withColor(color&0xFFFFFF));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pests").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetSession();local("Pest session reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetall").executes(c->{resetSession();cfg.pestLifetimeKills.clear();cfg.pestLifetimeDrops.clear();cfg.pestLifetimeProfit=0;saveSoon();local("All pest statistics reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){State s=state();Stats t=stats();local(s==null?"Pest helper inactive.":"Pests: "+s.alive()+"/"+cfg.pestMaxCount+", plots "+(s.plots().isEmpty()?"none":s.plots())+", cooldown "+s.cooldown()+".");if(t!=null)local("Session: "+t.sessionKills()+" kills, "+t.sessionDrops()+" drops, "+coins(t.sessionProfit())+" profit.");return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.pestCore=value;case"spawn"->cfg.pestSpawnTitle=value;case"finder"->cfg.pestFinderHud=value;case"world"->cfg.pestFinderWorld=value;case"borders"->cfg.pestFinderBorders=value;case"names"->cfg.pestFinderNames=value;case"timer"->cfg.pestTimerHud=value;case"warning"->cfg.pestTimerWarning=value;case"stats"->cfg.pestStatsHud=value;case"profit"->cfg.pestStatsShowProfit=value;default->{local("Unknown pest option.");return 0;}}ConstellationClient.saveConfig();local("Pest option updated.");return 1;}

    private static void decrementCurrentPlot(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;Integer id=plotAt(mc.player.getX(),mc.player.getZ());if(id==null)return;plotCounts.computeIfPresent(id,(k,v)->v<=1?null:v-1);}
    static Integer plotAt(double x,double z){int col=(int)Math.floor((x+240)/96),row=(int)Math.floor((z+240)/96);return row>=0&&row<5&&col>=0&&col<5?PLOT_IDS[row][col]:null;}
    private static Vec3 middle(int id){for(int row=0;row<5;row++)for(int col=0;col<5;col++)if(PLOT_IDS[row][col]==id)return new Vec3((col-2)*96,10,(row-2)*96);return null;}
    static Integer plotId(String text){String s=clean(text).replace("Plot -","").trim();if(s.equalsIgnoreCase("The Barn")||s.equalsIgnoreCase("Barn"))return 0;if(cfg.pestPlotNames!=null){Integer known=cfg.pestPlotNames.get(s.toLowerCase(Locale.ROOT));if(known!=null)return known;}Matcher m=Pattern.compile("(?:^|\\D)(\\d{1,2})(?:\\D|$)").matcher(s);if(!m.find())return null;int id=number(m.group(1));return id>=0&&id<=24?id:null;}
    private static List<String> splitPlots(String text){return Arrays.stream(text.replace(" and ",", ").split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();}
    private static long parseDuration(String text){if(text==null)return-1;long total=0;Matcher m=Pattern.compile("(\\d+)m").matcher(text);if(m.find())total+=number(m.group(1))*60_000L;m=Pattern.compile("(\\d+)s").matcher(text);if(m.find())total+=number(m.group(1))*1000L;return total;}
    private static long average(){return spawnIntervals.isEmpty()?0:Math.round(spawnIntervals.stream().mapToLong(Long::longValue).average().orElse(0));}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.pestCore&&inGarden();}
    private static boolean inGarden(){return ConstellationClient.loc().area()== LocationManager.SkyblockArea.GARDEN;}
    private static String clean(String s){String out=ChatFormatting.stripFormatting(s);return out==null?"":out.trim();}
    private static int number(String s){try{return Integer.parseInt(s.replace(",",""));}catch(Exception e){return 0;}}
    private static String normal(String s){return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static void add(Map<String,Long> map,String key,long amount){map.merge(key,amount,Long::sum);}
    private static void addProfit(double value){sessionProfit+=value;cfg.pestLifetimeProfit+=value;}
    private static void reconcilePrices(){if(pendingPrices.isEmpty())return;boolean changed=false;for(var it=pendingPrices.entrySet().iterator();it.hasNext();){var entry=it.next();double price=PriceProvider.sellValue(entry.getKey());if(price<=0)continue;addProfit(price*entry.getValue());it.remove();changed=true;}if(changed)saveSoon();}
    private static void learnCurrentPlot(String name){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;Integer id=plotAt(mc.player.getX(),mc.player.getZ());if(id==null)return;String key=clean(name).replace("Plot -","").trim().toLowerCase(Locale.ROOT);if(key.isEmpty())return;if(cfg.pestPlotNames==null)cfg.pestPlotNames=new HashMap<>();Integer old=cfg.pestPlotNames.put(key,id);if(!Objects.equals(old,id))saveSoon();}
    private static long total(Map<String,Long> map){return map.values().stream().mapToLong(Long::longValue).sum();}
    private static String coins(double value){if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fm",value/1_000_000);if(value>=1000)return String.format(Locale.ROOT,"%.1fk",value/1000);return String.format(Locale.ROOT,"%.0f",value);}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a72[Pests] \u00a7f"+text));}
    private static void saveSoon(){lastSaveAt=System.currentTimeMillis();if(cfg.pestStatsPersistent)ConstellationClient.saveConfig();}
    private static void resetSession(){alive=0;plotCounts.clear();spawnIntervals.clear();sessionKills.clear();sessionDrops.clear();pendingPrices.clear();sessionProfit=0;sessionStarted=0;lastSpawnAt=0;cooldownEnd=0;cooldown="Unknown";ready=false;maxPests=false;warned=false;lastPest="";lastDrop="";}
    private static Map<String,String> itemIds(){Map<String,String> m=new HashMap<>();String[][] rows={{"Enchanted Potato","ENCHANTED_POTATO"},{"Enchanted Carrot","ENCHANTED_CARROT"},{"Enchanted Wheat","ENCHANTED_BREAD"},{"Enchanted Hay Bale","ENCHANTED_HAY_BLOCK"},{"Enchanted Cactus Green","ENCHANTED_CACTUS_GREEN"},{"Enchanted Sugar","ENCHANTED_SUGAR"},{"Enchanted Sugar Cane","ENCHANTED_SUGAR_CANE"},{"Enchanted Cocoa Beans","ENCHANTED_COCOA"},{"Enchanted Cocoa Bean","ENCHANTED_COCOA"},{"Enchanted Cookie","ENCHANTED_COOKIE"},{"Enchanted Pumpkin","ENCHANTED_PUMPKIN"},{"Polished Pumpkin","POLISHED_PUMPKIN"},{"Enchanted Red Mushroom","ENCHANTED_RED_MUSHROOM"},{"Enchanted Brown Mushroom","ENCHANTED_BROWN_MUSHROOM"},{"Enchanted Red Mushroom Block","ENCHANTED_HUGE_MUSHROOM_2"},{"Enchanted Brown Mushroom Block","ENCHANTED_HUGE_MUSHROOM_1"},{"Enchanted Melon Slice","ENCHANTED_MELON"},{"Enchanted Melon","ENCHANTED_MELON_BLOCK"},{"Enchanted Baked Potato","ENCHANTED_BAKED_POTATO"},{"Enchanted Golden Carrot","ENCHANTED_GOLDEN_CARROT"},{"Enchanted Cactus","ENCHANTED_CACTUS"},{"Mutant Nether Wart","MUTANT_NETHER_STALK"},{"Enchanted Nether Wart","ENCHANTED_NETHER_STALK"},{"Enchanted Sunflower","ENCHANTED_SUNFLOWER"},{"Enchanted Moonflower","ENCHANTED_MOONFLOWER"},{"Enchanted Wild Rose","ENCHANTED_WILD_ROSE"},{"Compacted Sunflower","COMPACTED_SUNFLOWER"},{"Compacted Moonflower","COMPACTED_MOONFLOWER"},{"Compacted Wild Rose","COMPACTED_WILD_ROSE"}};for(String[] row:rows)m.put(normal(row[0]),row[1]);return Map.copyOf(m);}
}
