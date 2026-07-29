package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.CygnusConfig;
import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class CygnusMayor {
    public record Perk(String name,String description,boolean minister,boolean override){}
    public record Minister(String name,Perk perk){}
    public record Candidate(String name,long votes,List<Perk> perks){}
    public record State(String mayor,List<Perk> perks,Minister minister,int electionYear,List<Candidate> candidates,long updatedAt){}
    // ported from Devonian (GPL-3.0): api/MayorApi.kt
    private static final URI ELECTION=URI.create("https://api.hypixel.net/v2/resources/skyblock/election");
    // ported from Skyblocker (LGPL-3.0-or-later): utils/mayor/MayorUtils.java
    private static final URI OVERRIDES=URI.create("https://hysky.de/api/mayorperkoverrides");
    private static final Path CACHE=FabricLoader.getInstance().getConfigDir().resolve("constellation-mayor.json");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"constellation-mayor");t.setDaemon(true);return t;});
    private static final HttpClient HTTP=HttpClient.newBuilder().executor(IO).connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final List<OverridePerk> ACTIVE_OVERRIDES=new CopyOnWriteArrayList<>();
    private record OverridePerk(String name,long from,long to){}
    private static volatile State state;
    private static CygnusConfig cfg;
    private static boolean initialized,fetching,liveLoaded,overridesLoaded;
    private static long lastFetch,lastSuccess;
    private static String error="";
    private CygnusMayor(){}

    public static void init(CygnusConfig config){
        cfg=config;
        if(initialized)return;
        initialized=true;
        loadCache();
        ConstellationClient.tick().every(20,"cygnus-mayor",CygnusMayor::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->refresh(false));
        refresh(false);
    }
    private static void tick(){if(!enabled())return;if(lastFetch==0||System.currentTimeMillis()-lastFetch>Math.clamp(cfg.mayorRefreshMinutes,5,240)*60_000L)refresh(false);}

    public static String hudText(){
        if(!enabled())return null;State s=state;
        if(s==null)return cfg.mayorShowFetchState?(fetching?"§7Loading mayor...":"§cMayor unavailable"):null;
        List<String> rows=new ArrayList<>();rows.add("§6Mayor §f"+s.mayor);
        if(cfg.mayorShowMinister&&s.minister!=null&&!s.minister.name.isBlank())rows.add("§eMinister §f"+s.minister.name+(s.minister.perk==null?"":" §7"+s.minister.perk.name));
        if(cfg.mayorShowPerks&&cfg.mayorPerksDisplay){
            activePerks().stream().filter(p->perkIncluded(p.name)).limit(Math.clamp(cfg.mayorHudPerkLimit,1,20)).forEach(p->rows.add("§a"+p.name+(cfg.mayorShowPerkDescriptions&&!p.description.isBlank()?" §7"+plain(p.description):"")));
        }
        if(cfg.mayorShowElectionLeader&&cfg.mayorElectionHud&&!s.candidates.isEmpty()){
            Candidate leader=s.candidates.stream().max(Comparator.comparingLong(Candidate::votes)).orElse(null);
            if(leader!=null)rows.add("§dElection §f"+leader.name+(cfg.mayorShowElectionVotes?" §7"+compact(leader.votes)+" votes":""));
        }
        if(cfg.mayorShowFetchState&&!error.isBlank())rows.add("§eCached mayor data");
        return String.join(" | ",rows);
    }

    public static boolean known(){return state!=null;}
    public static boolean perkStateComplete(){return liveLoaded&&overridesLoaded;}
    public static boolean hasPerk(String name){return activePerks().stream().anyMatch(p->p.name.equalsIgnoreCase(name));}
    public static List<Perk> activePerks(){
        State s=state;if(s==null)return List.of();List<Perk> out=new ArrayList<>(s.perks);
        if(s.minister!=null&&s.minister.perk!=null&&!s.minister.perk.name.isBlank())out.add(s.minister.perk);
        long now=System.currentTimeMillis();for(OverridePerk p:ACTIVE_OVERRIDES)if(now>=p.from&&now<=p.to)out.add(new Perk(p.name,"Temporary active perk",false,true));
        LinkedHashMap<String,Perk> unique=new LinkedHashMap<>();for(Perk p:out)unique.putIfAbsent(p.name.toLowerCase(Locale.ROOT),p);return List.copyOf(unique.values());
    }

    private static void refresh(boolean force){
        if(fetching||!force&&lastFetch>0&&System.currentTimeMillis()-lastFetch<60_000)return;
        fetching=true;lastFetch=System.currentTimeMillis();
        HttpRequest election=HttpRequest.newBuilder(ELECTION).timeout(Duration.ofSeconds(12)).header("User-Agent","Constellation/0.9.692").GET().build();
        HttpRequest overrides=HttpRequest.newBuilder(OVERRIDES).timeout(Duration.ofSeconds(12)).header("User-Agent","Constellation/0.9.692").GET().build();
        CompletableFuture<HttpResponse<String>> a=HTTP.sendAsync(election,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<String>> b=HTTP.sendAsync(overrides,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).exceptionally(x->null);
        a.thenCombine(b,(er,or)->{
            if(er==null||er.statusCode()!=200)throw new CompletionException(new IllegalStateException("election HTTP "+(er==null?"unknown":er.statusCode())));
            State parsed=parseElection(er.body());
            if(or!=null&&or.statusCode()==200){parseOverrides(or.body());overridesLoaded=true;}else overridesLoaded=false;
            try{Files.writeString(CACHE,er.body(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){}
            return parsed;
        }).whenComplete((parsed,failure)->{
            fetching=false;
            if(failure!=null){error=failure.getCause()==null?failure.getClass().getSimpleName():failure.getCause().getClass().getSimpleName();return;}
            State old=state;state=parsed;lastSuccess=System.currentTimeMillis();error="";
            if(liveLoaded&&old!=null&&!old.mayor.equalsIgnoreCase(parsed.mayor))notifyChange(old.mayor,parsed.mayor);
            liveLoaded=true;
        });
    }

    private static State parseElection(String raw){
        JsonObject root=JsonParser.parseString(raw).getAsJsonObject();if(!root.get("success").getAsBoolean())throw new IllegalStateException("unsuccessful election response");
        JsonObject mayor=root.getAsJsonObject("mayor");if(mayor==null)throw new IllegalStateException("missing mayor");
        String name=string(mayor,"name");if(name.isBlank())throw new IllegalStateException("blank mayor");
        List<Perk> perks=parsePerks(mayor.getAsJsonArray("perks"),false);
        Minister minister=null;JsonObject m=mayor.getAsJsonObject("minister");if(m!=null){JsonObject p=m.getAsJsonObject("perk");minister=new Minister(string(m,"name"),p==null?null:new Perk(string(p,"name"),string(p,"description"),true,false));}
        int year=0;List<Candidate> candidates=new ArrayList<>();JsonObject current=root.getAsJsonObject("current");
        if(current!=null){year=(int)number(current,"year",0);JsonArray array=current.getAsJsonArray("candidates");if(array!=null)for(JsonElement e:array)if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();candidates.add(new Candidate(string(c,"name"),number(c,"votes",0),parsePerks(c.getAsJsonArray("perks"),false)));}}
        candidates.sort(Comparator.comparingLong(Candidate::votes).reversed());return new State(name,List.copyOf(perks),minister,year,List.copyOf(candidates),number(root,"lastUpdated",System.currentTimeMillis()));
    }
    private static List<Perk> parsePerks(JsonArray array,boolean minister){List<Perk> out=new ArrayList<>();if(array!=null)for(JsonElement e:array)if(e.isJsonObject()){JsonObject p=e.getAsJsonObject();String name=string(p,"name");if(!name.isBlank())out.add(new Perk(name,string(p,"description"),minister,false));}return out;}
    private static void parseOverrides(String raw){List<OverridePerk> out=new ArrayList<>();JsonArray array=JsonParser.parseString(raw).getAsJsonArray();for(JsonElement e:array)if(e.isJsonObject()){JsonObject o=e.getAsJsonObject();String name=string(o,"perk");long from=number(o,"from",0),to=number(o,"to",0);if(!name.isBlank()&&to>=from)out.add(new OverridePerk(name,from,to));}ACTIVE_OVERRIDES.clear();ACTIVE_OVERRIDES.addAll(out);}
    private static void loadCache(){try{state=parseElection(Files.readString(CACHE,StandardCharsets.UTF_8));lastSuccess=Files.getLastModifiedTime(CACHE).toMillis();}catch(Exception ignored){}}
    private static void notifyChange(String old,String current){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;if(cfg.mayorChangeChat)mc.player.sendSystemMessage(Component.literal("§6[Mayor] §f"+current+" replaced "+old+"."));if(cfg.mayorChangeTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Mayor "+current).withColor(cfg.mayorColor&0xFFFFFF));}if(cfg.mayorChangeSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.2f);}
    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.mayorState&&(!cfg.mayorOnlySkyblock||ConstellationClient.loc().onHypixel());}
    private static boolean perkIncluded(String name){String lower=name.toLowerCase(Locale.ROOT);Set<String> include=csv(cfg.mayorPerkIncludes),exclude=csv(cfg.mayorPerkExcludes);return(include.isEmpty()||include.stream().anyMatch(lower::contains))&&exclude.stream().noneMatch(lower::contains);}
    private static Set<String> csv(String raw){Set<String> out=new LinkedHashSet<>();if(raw!=null)for(String part:raw.split(",")){String v=part.trim().toLowerCase(Locale.ROOT);if(!v.isBlank())out.add(v);}return out;}
    private static String plain(String raw){return raw.replaceAll("§.","").replaceAll("\\s+"," ").trim();}
    private static String compact(long n){if(n<1000)return Long.toString(n);if(n<1_000_000)return String.format(Locale.ROOT,"%.1fk",n/1000d);return String.format(Locale.ROOT,"%.2fM",n/1_000_000d);}
    private static String string(JsonObject o,String key){try{return o.get(key).getAsString();}catch(Exception e){return"";}}
    private static long number(JsonObject o,String key,long fallback){try{return o.get(key).getAsLong();}catch(Exception e){return fallback;}}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mayor").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("perks").executes(c->listPerks())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("election").executes(c->listElection())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refresh").executes(c->{refresh(true);local("Mayor refresh started.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearfilters").executes(c->{cfg.mayorPerkIncludes="";cfg.mayorPerkExcludes="";save();return status();})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("limit").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,20)).executes(c->{cfg.mayorHudPerkLimit=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refreshminutes").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(5,240)).executes(c->{cfg.mayorRefreshMinutes=IntegerArgumentType.getInteger(c,"minutes");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("include").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("filters",StringArgumentType.greedyString()).executes(c->{cfg.mayorPerkIncludes=StringArgumentType.getString(c,"filters");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("exclude").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("filters",StringArgumentType.greedyString()).executes(c->{cfg.mayorPerkExcludes=StringArgumentType.getString(c,"filters");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){State s=state;if(s==null){local("Mayor unavailable"+(error.isBlank()?".":" after "+error+"."));return 0;}local("Mayor "+s.mayor+(s.minister==null?"":", minister "+s.minister.name)+", "+activePerks().size()+" active perks, election year "+s.electionYear+".");return 1;}
    private static int listPerks(){if(state==null)return status();for(Perk p:activePerks())local(p.name+(p.minister?" [Minister]":"")+(p.override?" [Temporary]":"")+" - "+plain(p.description));return 1;}
    private static int listElection(){if(state==null)return status();for(Candidate c:state.candidates)local(c.name+": "+c.votes+" votes, "+c.perks.size()+" perks");return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.mayorState=v;case"hud"->cfg.mayorHud=v;case"minister"->cfg.mayorShowMinister=v;case"perks"->cfg.mayorShowPerks=v;case"descriptions"->cfg.mayorShowPerkDescriptions=v;case"election"->cfg.mayorShowElectionLeader=v;case"votes"->cfg.mayorShowElectionVotes=v;case"chat"->cfg.mayorChangeChat=v;case"title"->cfg.mayorChangeTitle=v;case"sound"->cfg.mayorChangeSound=v;case"skyblock"->cfg.mayorOnlySkyblock=v;case"fetchstate"->cfg.mayorShowFetchState=v;default->{local("Unknown mayor option.");return 0;}}save();return status();}
    private static int color(String raw){try{long p=raw.startsWith("#")?Long.parseLong(raw.substring(1),16):Long.decode(raw);int c=(int)p;if((c>>>24)==0)c|=0xFF000000;cfg.mayorColor=c;save();return status();}catch(NumberFormatException e){local("Color must be #RRGGBB, #AARRGGBB, or a number.");return 0;}}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Mayor] §f"+text));}
}
