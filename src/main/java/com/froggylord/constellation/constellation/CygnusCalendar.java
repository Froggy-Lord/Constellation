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

public final class CygnusCalendar {
    public record Event(long start,int duration,String name,String location,List<String> extras) {
        long end(){return start+duration;}
    }
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/events/EventNotifications.java
    private static final URI CALENDAR=URI.create("https://hysky.de/api/calendar");
    private static final Path CACHE=FabricLoader.getInstance().getConfigDir().resolve("constellation-event-calendar.json");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"constellation-calendar");t.setDaemon(true);return t;});
    private static final HttpClient HTTP=HttpClient.newBuilder().executor(IO).connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final List<Event> EVENTS=new CopyOnWriteArrayList<>();
    private static final Set<String> NOTIFIED=ConcurrentHashMap.newKeySet();
    private static CygnusConfig cfg;
    private static boolean initialized,fetching;
    private static long lastTick,lastFetch,lastSuccess;
    private static String fetchError="";
    private CygnusCalendar(){}

    public static void init(CygnusConfig config){
        cfg=config;
        if(initialized)return;
        initialized=true;
        loadCache();
        ConstellationClient.tick().every(20,"cygnus-calendar",CygnusCalendar::tick);
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->refresh(false));
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->NOTIFIED.clear());
        refresh(false);
    }

    private static void tick(){
        long now=System.currentTimeMillis()/1000;
        if(!enabled()){lastTick=now;return;}
        if(lastFetch==0||System.currentTimeMillis()-lastFetch>Math.clamp(cfg.eventCalendarRefreshMinutes,5,240)*60_000L)refresh(false);
        EVENTS.removeIf(e->e.end()<now-60);
        Set<String> keep=new HashSet<>();
        for(Event event:EVENTS) {
            if(event.end()<now||!included(event.name))continue;
            for(int lead:reminders()) {
                String key=event.name+"@"+event.start+"@"+lead;keep.add(key);
                if(lastTick>0&&lastTick+lead<event.start&&now+lead>=event.start&&NOTIFIED.add(key))notifyEvent(event,lead);
            }
        }
        NOTIFIED.retainAll(keep);
        lastTick=now;
    }

    public static String hudText(){
        if(!enabled())return null;
        long now=System.currentTimeMillis()/1000;
        List<String> rows=new ArrayList<>();
        if(cfg.eventCalendarShowSkyblockDate&&cfg.calendarHud)rows.add(skyblockDate(System.currentTimeMillis()));
        int limit=Math.clamp(cfg.eventCalendarRows,1,8);
        for(Event event:EVENTS) {
            if(rows.size()-(cfg.eventCalendarShowSkyblockDate&&cfg.calendarHud?1:0)>=limit)break;
            if(!cfg.eventNotificationHud)break;
            if(event.end()<now||!included(event.name))continue;
            boolean active=event.start<=now;
            if(active&&!cfg.eventCalendarShowActive)continue;
            String prefix=active?"§a":"§6";
            String time=active?"ends "+format(event.end()-now):"in "+format(event.start-now);
            String location=cfg.eventCalendarShowLocation&&!event.location.isBlank()?" §7"+cleanLocation(event.location):"";
            rows.add(prefix+event.name+" §f"+time+location);
        }
        if(cfg.eventCalendarShowFetchState&&(fetching||EVENTS.isEmpty()||!fetchError.isBlank()))rows.add(fetching?"§7Updating calendar...":EVENTS.isEmpty()?"§cCalendar unavailable":"§eCached calendar");
        return rows.isEmpty()?null:String.join(" | ",rows);
    }

    // ported from SkyHanni (LGPL-2.1): utils/SkyBlockTime.kt
    private static String skyblockDate(long millis){
        final long epoch=1559829300000L,yearMs=124L*60*60*1000,monthMs=yearMs/12,dayMs=monthMs/31;
        long value=Math.max(0,millis-epoch);long year=value/yearMs;value%=yearMs;int month=(int)(value/monthMs)+1;value%=monthMs;int day=(int)(value/dayMs)+1;
        String[] seasons={"Early Spring","Spring","Late Spring","Early Summer","Summer","Late Summer","Early Autumn","Autumn","Late Autumn","Early Winter","Winter","Late Winter"};
        return "§b"+seasons[Math.clamp(month-1,0,11)]+" "+day+(cfg.eventCalendarShowYear?" §7Y"+year:"");
    }

    private static void notifyEvent(Event event,int lead){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        String when=lead<=0?"now":"in "+format(lead);
        if(cfg.eventCalendarChat)mc.player.sendSystemMessage(Component.literal("§6[Events] §f"+event.name+" "+when+(cfg.eventCalendarShowLocation&&!event.location.isBlank()?" §7"+cleanLocation(event.location):"")));
        if(cfg.eventCalendarTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(event.name).withColor(cfg.eventCalendarColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(when));}
        if(cfg.eventCalendarSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.8f,lead<=0?1.4f:1f);
    }

    private static void refresh(boolean force){
        if(fetching||!force&&lastFetch>0&&System.currentTimeMillis()-lastFetch<60_000)return;
        fetching=true;lastFetch=System.currentTimeMillis();
        HttpRequest request=HttpRequest.newBuilder(CALENDAR).timeout(Duration.ofSeconds(12)).header("User-Agent","Constellation/0.9.691").GET().build();
        HTTP.sendAsync(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response,error)->{
            fetching=false;
            if(error!=null||response==null||response.statusCode()!=200){fetchError=error==null?"HTTP "+(response==null?"unknown":response.statusCode()):error.getClass().getSimpleName();return;}
            try{
                List<Event> parsed=parse(response.body());
                if(parsed.isEmpty())throw new IllegalStateException("empty calendar");
                EVENTS.clear();EVENTS.addAll(parsed);lastSuccess=System.currentTimeMillis();fetchError="";
                try{Files.writeString(CACHE,response.body(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){}
            }catch(Exception e){fetchError=e.getClass().getSimpleName();}
        });
    }

    private static List<Event> parse(String raw){
        long now=System.currentTimeMillis()/1000;List<Event> out=new ArrayList<>();
        JsonArray array=JsonParser.parseString(raw).getAsJsonArray();
        for(JsonElement element:array){
            if(!element.isJsonObject())continue;JsonObject o=element.getAsJsonObject();
            long start=number(o,"timestamp",0);int duration=(int)number(o,"duration",0);String name=string(o,"event");String location=string(o,"location");
            if(start<=0||duration<0||name.isBlank()||start+duration<now-60)continue;
            List<String> extras=new ArrayList<>();JsonElement x=o.get("extras");if(x!=null&&x.isJsonArray())for(JsonElement e:x.getAsJsonArray())if(e.isJsonPrimitive())extras.add(e.getAsString());
            out.add(new Event(start,duration,name,location,List.copyOf(extras)));
        }
        out.sort(Comparator.comparingLong(Event::start).thenComparing(Event::name));return out;
    }
    private static void loadCache(){try{String raw=Files.readString(CACHE,StandardCharsets.UTF_8);EVENTS.addAll(parse(raw));lastSuccess=Files.getLastModifiedTime(CACHE).toMillis();}catch(Exception ignored){}}
    private static long number(JsonObject o,String key,long fallback){try{return o.get(key).getAsLong();}catch(Exception e){return fallback;}}
    private static String string(JsonObject o,String key){try{return o.get(key).getAsString();}catch(Exception e){return"";}}
    private static boolean enabled(){return cfg!=null&&cfg.enabled&&cfg.eventCalendar&&(!cfg.eventCalendarOnlySkyblock||ConstellationClient.loc().onHypixel());}
    private static boolean included(String name){String n=name.toLowerCase(Locale.ROOT);Set<String> includes=csv(cfg.eventCalendarIncludes),excludes=csv(cfg.eventCalendarExcludes);if(!includes.isEmpty()&&includes.stream().noneMatch(n::contains))return false;return excludes.stream().noneMatch(n::contains);}
    private static Set<String> csv(String raw){Set<String> out=new LinkedHashSet<>();if(raw!=null)for(String part:raw.split(",")){String value=part.trim().toLowerCase(Locale.ROOT);if(!value.isEmpty())out.add(value);}return out;}
    private static int[] reminders(){return Arrays.stream(Optional.ofNullable(cfg.eventCalendarReminderSeconds).orElse("").split(",")).map(String::trim).filter(s->!s.isEmpty()).mapToInt(s->{try{return Math.clamp(Integer.parseInt(s),0,86400);}catch(Exception e){return-1;}}).filter(v->v>=0).distinct().sorted().toArray();}
    private static String cleanLocation(String raw){String s=raw.startsWith("/")?raw.substring(1):raw;return s.replace("warp ","").replace('_',' ');}
    private static String format(long seconds){seconds=Math.max(0,seconds);if(seconds>=86400)return seconds/86400+"d "+seconds/3600%24+"h";if(seconds>=3600)return seconds/3600+"h "+seconds/60%60+"m";if(seconds>=60)return seconds/60+"m "+seconds%60+"s";return seconds+"s";}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("events").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refresh").executes(c->{refresh(true);local("Calendar refresh started.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearfilters").executes(c->{cfg.eventCalendarIncludes="";cfg.eventCalendarExcludes="";save();return status();})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,8)).executes(c->{cfg.eventCalendarRows=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refreshminutes").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes",IntegerArgumentType.integer(5,240)).executes(c->{cfg.eventCalendarRefreshMinutes=IntegerArgumentType.getInteger(c,"minutes");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"argb"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reminders").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("seconds",StringArgumentType.greedyString()).executes(c->{cfg.eventCalendarReminderSeconds=StringArgumentType.getString(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("include").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("filters",StringArgumentType.greedyString()).executes(c->{cfg.eventCalendarIncludes=StringArgumentType.getString(c,"filters");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("exclude").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("filters",StringArgumentType.greedyString()).executes(c->{cfg.eventCalendarExcludes=StringArgumentType.getString(c,"filters");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){Event next=EVENTS.stream().filter(e->e.end()>=System.currentTimeMillis()/1000&&included(e.name)).findFirst().orElse(null);local("Loaded "+EVENTS.size()+" events"+(next==null?".":", next "+next.name+" "+(next.start<=System.currentTimeMillis()/1000?"active":"in "+format(next.start-System.currentTimeMillis()/1000)))+(fetchError.isBlank()?".":"; using cache after "+fetchError+"."));return 1;}
    private static int option(String name,String raw){Boolean v=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(v==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.eventCalendar=v;case"hud"->cfg.eventCalendarHud=v;case"active"->cfg.eventCalendarShowActive=v;case"location"->cfg.eventCalendarShowLocation=v;case"date"->cfg.eventCalendarShowSkyblockDate=v;case"year"->cfg.eventCalendarShowYear=v;case"chat"->cfg.eventCalendarChat=v;case"title"->cfg.eventCalendarTitle=v;case"sound"->cfg.eventCalendarSound=v;case"skyblock"->cfg.eventCalendarOnlySkyblock=v;case"fetchstate"->cfg.eventCalendarShowFetchState=v;default->{local("Unknown event option.");return 0;}}save();return status();}
    private static int color(String raw){try{long parsed=raw.startsWith("#")?Long.parseLong(raw.substring(1),16):Long.decode(raw);int color=(int)parsed;if((color>>>24)==0)color|=0xFF000000;cfg.eventCalendarColor=color;save();return status();}catch(NumberFormatException e){local("Color must be #RRGGBB, #AARRGGBB, or a number.");return 0;}}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Events] §f"+text));}
}
