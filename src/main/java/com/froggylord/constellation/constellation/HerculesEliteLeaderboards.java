package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

// ported from SkyHanni (LGPL-3.0-or-later): api/EliteDevApi.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/garden/EliteFarmersLeaderboard.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/garden/FarmingWeightData.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/leaderboarddisplays/EliteLeaderboardDisplayBase.kt
public final class HerculesEliteLeaderboards {
    public enum Kind { WEIGHT, CROP, PEST }
    public enum State { WAITING, LOADING, READY, UNRANKED, UNAVAILABLE }
    public record Player(String name,double amount,int rank){}
    public record Snapshot(Kind kind,State state,String label,double amount,int rank,Player next,Player previous,
                           int goal,long fetchedAt,String error){}

    private record Profile(String id,double weight,Map<String,Long> crops,Map<String,Integer> pests){}
    private record Query(Kind kind,String suffix,String label,double localAmount,int goal){}

    private static final String API="https://api.eliteskyblock.com";
    private static final Gson GSON=new Gson();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final AtomicBoolean FETCHING=new AtomicBoolean();
    private static final Map<Kind,Snapshot> DATA=new EnumMap<>(Kind.class);
    private static HerculesConfig cfg;
    private static long nextFetch;
    private static String identity="";

    private HerculesEliteLeaderboards(){}

    public static void init(HerculesConfig config){
        cfg=config;
        for(Kind kind:Kind.values())DATA.put(kind,waiting(kind));
        HerculesGardenTracker.registerHarvestListener(h->{if(cfg.eliteCropAutoSelect)cfg.eliteSelectedCrop=h.crop().name();});
        ConstellationClient.tick().every(20,"hercules-elite-leaderboards",HerculesEliteLeaderboards::tick);
    }

    private static void tick(){
        if(!enabled())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        String current=mc.getUser().getProfileId().toString();
        if(!current.equals(identity)){identity=current;nextFetch=0;for(Kind k:Kind.values())DATA.put(k,waiting(k));}
        if(System.currentTimeMillis()>=nextFetch)refresh(false);
    }

    public static Snapshot snapshot(Kind kind){
        if(!enabled()||!kindEnabled(kind)||!visible(kind))return null;
        return DATA.getOrDefault(kind,waiting(kind));
    }

    public static void refresh(boolean forced){
        if(!enabled()||!FETCHING.compareAndSet(false,true))return;
        long now=System.currentTimeMillis();
        if(!forced&&now<nextFetch){FETCHING.set(false);return;}
        nextFetch=now+Math.max(2,cfg.eliteRefreshMinutes)*60_000L;
        for(Kind kind:Kind.values()){Snapshot old=DATA.get(kind);if(old==null||old.state()!=State.READY)DATA.put(kind,new Snapshot(kind,State.LOADING,label(kind),0,-1,null,null,goal(kind),0,""));}
        Thread thread=new Thread(()->{try{load();}catch(Exception e){fail(e.getMessage());}finally{FETCHING.set(false);}},"constellation-elite-leaderboards");
        thread.setDaemon(true);thread.start();
    }

    private static void load()throws Exception{
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)throw new IllegalStateException("Player unavailable");
        String uuid=mc.getUser().getProfileId().toString().replace("-","");
        Profile profile=profile(uuid);
        List<Query> queries=queries(profile);
        for(Query query:queries){
            if(!kindEnabled(query.kind()))continue;
            Snapshot fresh=leaderboard(uuid,profile.id(),query);
            Snapshot old=DATA.put(query.kind(),fresh);
            passMessage(old,fresh);
            rankChange(old,fresh);
        }
    }

    private static Profile profile(String uuid)throws Exception{
        JsonObject root=get(API+"/weight/"+uuid+"?collections=true");
        String selected=text(root,"selectedProfileId","");JsonArray profiles=array(root,"profiles");
        JsonObject chosen=null;
        for(JsonElement element:profiles){if(!element.isJsonObject())continue;JsonObject value=element.getAsJsonObject();if(selected.equals(text(value,"profileId",""))){chosen=value;break;}if(chosen==null)chosen=value;}
        if(chosen==null)throw new IllegalStateException("No Elite farming profile");
        return new Profile(text(chosen,"profileId",selected),number(chosen,"totalWeight",0),longMap(chosen,"crops"),intMap(chosen,"pests"));
    }

    private static List<Query> queries(Profile profile){
        List<Query> out=new ArrayList<>();
        out.add(new Query(Kind.WEIGHT,"farmingweight"+monthly(cfg.eliteMonthlyWeight),"Farming Weight",cfg.eliteMonthlyWeight?0:profile.weight(),cfg.eliteWeightRankGoal));
        HerculesGardenTracker.Crop crop=crop();String cropKey=cropName(crop);
        double cropAmount=cfg.eliteMonthlyCrop?0:Math.max(profile.crops().getOrDefault(crop.display(),0L),HerculesCropMilestones.collection(crop));
        out.add(new Query(Kind.CROP,cropKey+monthly(cfg.eliteMonthlyCrop),crop.display()+" Collection",cropAmount,cfg.eliteCropRankGoal));
        String pest=cfg.eliteSelectedPest==null?"ALL":cfg.eliteSelectedPest.trim();String suffix=pest.equalsIgnoreCase("ALL")?"pests":pestName(pest);
        double pestAmount=cfg.eliteMonthlyPest?0:pest.equalsIgnoreCase("ALL")?profile.pests().values().stream().mapToInt(Integer::intValue).sum():profile.pests().entrySet().stream().filter(e->normal(e.getKey()).equals(normal(pest))).mapToInt(Map.Entry::getValue).sum();
        out.add(new Query(Kind.PEST,suffix+monthly(cfg.eliteMonthlyPest),pest.equalsIgnoreCase("ALL")?"Pest Kills":pretty(pest)+" Kills",pestAmount,cfg.elitePestRankGoal));
        return out;
    }

    private static Snapshot leaderboard(String uuid,String profileId,Query query)throws Exception{
        int upcoming=Math.clamp(cfg.eliteUpcomingPlayers,1,100);int atRank=cfg.eliteUseRankGoals?Math.max(1,query.goal()):0;
        StringBuilder url=new StringBuilder(API).append("/leaderboard/").append(query.suffix()).append('/').append(uuid).append('/').append(profileId)
            .append("?upcoming=").append(upcoming).append("&previous=1");
        if(atRank>0)url.append("&atRank=").append(atRank);
        String mode=mode();if(!mode.isEmpty())url.append("&mode=").append(URLEncoder.encode(mode,StandardCharsets.UTF_8));
        JsonObject json=get(url.toString());if(bool(json,"disabled",false))return new Snapshot(query.kind(),State.UNAVAILABLE,query.label(),query.localAmount(),-1,null,null,query.goal(),System.currentTimeMillis(),"Leaderboard disabled");
        int rank=integer(json,"rank",-1);double apiAmount=number(json,"amount",query.localAmount());double amount=Math.max(query.localAmount(),apiAmount);
        if(rank<=0)return new Snapshot(query.kind(),State.UNRANKED,query.label(),amount,-1,null,null,query.goal(),System.currentTimeMillis(),"");
        Player next=null,previous=null;for(JsonElement element:array(json,"upcomingPlayers")){Player p=player(element);if(p!=null&&p.amount()>amount&&(next==null||p.amount()<next.amount()))next=p;}
        JsonArray before=array(json,"previous");if(!before.isEmpty())previous=player(before.get(0));
        return new Snapshot(query.kind(),State.READY,query.label(),amount,rank,next,previous,query.goal(),System.currentTimeMillis(),"");
    }

    private static JsonObject get(String url)throws Exception{
        int timeout=Math.clamp(cfg.eliteRequestTimeoutSeconds,5,30);
        HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout)).header("User-Agent","Constellation/0.9").GET().build();
        HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("Elite API returned "+response.statusCode());
        JsonElement parsed=GSON.fromJson(response.body(),JsonElement.class);if(parsed==null||!parsed.isJsonObject())throw new IllegalStateException("Invalid Elite API response");
        JsonObject root=parsed.getAsJsonObject();JsonElement data=root.get("data");return data!=null&&data.isJsonObject()?data.getAsJsonObject():root;
    }

    private static void fail(String message){String error=message==null||message.isBlank()?"Elite API unavailable":message;long now=System.currentTimeMillis();for(Kind kind:Kind.values()){Snapshot old=DATA.get(kind);if(old!=null&&old.state()==State.READY)continue;DATA.put(kind,new Snapshot(kind,State.UNAVAILABLE,label(kind),0,-1,null,null,goal(kind),now,error));}}
    private static void rankChange(Snapshot old,Snapshot fresh){if(!cfg.eliteOfflineRankChanges||old==null||old.rank()<=0||fresh.rank()<=0||old.rank()==fresh.rank())return;int diff=old.rank()-fresh.rank();local((diff>0?"Gained ":"Lost ")+Math.abs(diff)+(Math.abs(diff)==1?" place":" places")+" on the "+fresh.label()+" leaderboard (#"+old.rank()+" -> #"+fresh.rank()+").");}
    private static void passMessage(Snapshot old,Snapshot fresh){if(!cfg.elitePassMessages||old==null||old.state()!=State.READY||old.next()==null||fresh.amount()<old.next().amount())return;local("Passed "+old.next().name()+" on the "+fresh.label()+" leaderboard.");}
    public static double etaSeconds(Snapshot s){if(s==null||s.next()==null)return-1;double gap=s.next().amount()-s.amount();if(gap<=0)return 0;double rate=s.kind()==Kind.CROP?HerculesCropMilestones.collectionRate():s.kind()==Kind.WEIGHT?weightRate():0;return rate>0?gap/rate:-1;}
    private static double weightRate(){HerculesGardenTracker.Rates rates=HerculesGardenTracker.rates();if(rates==null)return 0;HerculesGardenTracker.Crop crop=crop();return rates.instantBps()/factor(crop);}
    private static double factor(HerculesGardenTracker.Crop c){return switch(c){case WHEAT->100000;case CARROT->300000;case POTATO->298328.17;case SUGAR_CANE->198885.45;case NETHER_WART->248606.81;case PUMPKIN->99236.12;case MELON->488435.88;case MUSHROOM->90944.27;case COCOA->276733.75;case CACTUS->178730.65;case SUNFLOWER,MOONFLOWER,WILD_ROSE->200000;};}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("elitefarmers").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refresh").executes(c->{nextFetch=0;refresh(true);local("Refreshing Elite leaderboards.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("crop").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("crop",StringArgumentType.word()).executes(c->select("crop",StringArgumentType.getString(c,"crop"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pest").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("pest",StringArgumentType.greedyString()).executes(c->select("pest",StringArgumentType.getString(c,"pest"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goal").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("rank",IntegerArgumentType.integer(1,1_000_000)).executes(c->goal(StringArgumentType.getString(c,"type"),IntegerArgumentType.getInteger(c,"rank")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("value",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"value")))))));}
    private static int status(){for(Kind kind:Kind.values()){Snapshot s=DATA.get(kind);local(kind+": "+(s==null?"waiting":s.state()+(s.rank()>0?", #"+s.rank():"")+", "+format(s.amount())));}return 1;}
    private static int select(String type,String value){if(type.equals("crop")){try{HerculesGardenTracker.Crop.valueOf(value.toUpperCase(Locale.ROOT));}catch(Exception e){local("Unknown crop.");return 0;}cfg.eliteSelectedCrop=value.toUpperCase(Locale.ROOT);cfg.eliteCropAutoSelect=false;}else cfg.eliteSelectedPest=value;saveRefresh();return status();}
    private static int goal(String type,int value){switch(type.toLowerCase(Locale.ROOT)){case"weight"->cfg.eliteWeightRankGoal=value;case"crop"->cfg.eliteCropRankGoal=value;case"pest"->cfg.elitePestRankGoal=value;default->{local("Type must be weight, crop, or pest.");return 0;}}saveRefresh();return status();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("Value must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.eliteLeaderboards=value;case"weight"->cfg.eliteWeightLeaderboard=value;case"crop"->cfg.eliteCropLeaderboard=value;case"pest"->cfg.elitePestLeaderboard=value;case"outside"->cfg.eliteShowOutsideGarden=value;case"rank"->cfg.eliteShowRank=value;case"overtake"->cfg.eliteShowOvertake=value;case"previous"->cfg.eliteShowPrevious=value;case"eta"->cfg.eliteShowEta=value;case"goals"->cfg.eliteUseRankGoals=value;case"messages"->cfg.elitePassMessages=value;case"rankchanges"->cfg.eliteOfflineRankChanges=value;default->{local("Unknown leaderboard option.");return 0;}}saveRefresh();return status();}
    private static void saveRefresh(){ConstellationClient.saveConfig();nextFetch=0;refresh(true);}

    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.eliteLeaderboards;}
    private static boolean kindEnabled(Kind k){return switch(k){case WEIGHT->cfg.eliteWeightLeaderboard;case CROP->cfg.eliteCropLeaderboard;case PEST->cfg.elitePestLeaderboard;};}
    private static boolean visible(Kind k){if(!cfg.eliteShowOutsideGarden&&ConstellationClient.loc().area()!=LocationManager.SkyblockArea.GARDEN)return false;if(k==Kind.CROP&&cfg.eliteCropHideWhenIdle&&HerculesGardenTracker.rates()==null)return false;return k!=Kind.PEST||!cfg.elitePestHideWhenInactive||HerculesPests.lastKillAt()>0&&System.currentTimeMillis()-HerculesPests.lastKillAt()<=Math.max(5,cfg.elitePestInactiveSeconds)*1000L;}
    private static Snapshot waiting(Kind k){return new Snapshot(k,State.WAITING,label(k),0,-1,null,null,goal(k),0,"");}
    private static String label(Kind k){return switch(k){case WEIGHT->"Farming Weight";case CROP->"Crop Collection";case PEST->"Pest Kills";};}
    private static int goal(Kind k){if(cfg==null)return 10000;return switch(k){case WEIGHT->cfg.eliteWeightRankGoal;case CROP->cfg.eliteCropRankGoal;case PEST->cfg.elitePestRankGoal;};}
    private static HerculesGardenTracker.Crop crop(){try{return HerculesGardenTracker.Crop.valueOf(cfg.eliteSelectedCrop.toUpperCase(Locale.ROOT));}catch(Exception e){return HerculesGardenTracker.Crop.WHEAT;}}
    private static String cropName(HerculesGardenTracker.Crop c){return switch(c){case NETHER_WART->"netherwart";case SUGAR_CANE->"sugarcane";case WILD_ROSE->"wildrose";default->c.name().toLowerCase(Locale.ROOT).replace("_","");};}
    private static String pestName(String s){String n=normal(s).toLowerCase(Locale.ROOT).replace('_','-');return switch(n){case"field-mouse"->"mouse";case"praying-mantis"->"mantis";default->n;};}
    private static String monthly(boolean value){return value?"-monthly":"";}
    private static String mode(){if(cfg.eliteGamemode==null)return"";return switch(cfg.eliteGamemode.toUpperCase(Locale.ROOT)){case"IRONMAN"->"ironman";case"STRANDED","ISLAND"->"island";default->"";};}
    private static Player player(JsonElement e){if(e==null||!e.isJsonObject())return null;JsonObject o=e.getAsJsonObject();return new Player(text(o,"ign","Unknown"),number(o,"amount",0),integer(o,"rank",-1));}
    private static Map<String,Long> longMap(JsonObject o,String key){Map<String,Long> out=new java.util.LinkedHashMap<>();JsonObject v=object(o,key);for(var e:v.entrySet())try{out.put(e.getKey(),e.getValue().getAsLong());}catch(Exception ignored){}return out;}
    private static Map<String,Integer> intMap(JsonObject o,String key){Map<String,Integer> out=new java.util.LinkedHashMap<>();JsonObject v=object(o,key);for(var e:v.entrySet())try{out.put(e.getKey(),e.getValue().getAsInt());}catch(Exception ignored){}return out;}
    private static JsonObject object(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonObject()?e.getAsJsonObject():new JsonObject();}
    private static JsonArray array(JsonObject o,String k){JsonElement e=o.get(k);return e!=null&&e.isJsonArray()?e.getAsJsonArray():new JsonArray();}
    private static String text(JsonObject o,String k,String d){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():d;}catch(Exception e){return d;}}
    private static double number(JsonObject o,String k,double d){try{return o.has(k)?o.get(k).getAsDouble():d;}catch(Exception e){return d;}}
    private static int integer(JsonObject o,String k,int d){try{return o.has(k)?o.get(k).getAsInt():d;}catch(Exception e){return d;}}
    private static boolean bool(JsonObject o,String k,boolean d){try{return o.has(k)?o.get(k).getAsBoolean():d;}catch(Exception e){return d;}}
    public static String format(double v){if(cfg!=null&&cfg.eliteCompactNumbers){if(v>=1_000_000_000)return decimal(v/1_000_000_000)+"b";if(v>=1_000_000)return decimal(v/1_000_000)+"m";if(v>=1_000)return decimal(v/1_000)+"k";}return String.format(Locale.ROOT,"%,."+Math.clamp(cfg==null?2:cfg.eliteAmountPrecision,0,4)+"f",v);}
    private static String decimal(double v){return String.format(Locale.ROOT,"%."+Math.clamp(cfg.eliteAmountPrecision,0,4)+"f",v).replaceAll("\\.?0+$","");}
    private static String normal(String s){return s==null?"":s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static String pretty(String s){StringBuilder b=new StringBuilder();for(String p:normal(s).split("_")){if(p.isEmpty())continue;if(!b.isEmpty())b.append(' ');b.append(p.charAt(0)).append(p.substring(1).toLowerCase(Locale.ROOT));}return b.toString();}
    private static void local(String s){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a72[Elite Farmers] \u00a7f"+s));}
}
