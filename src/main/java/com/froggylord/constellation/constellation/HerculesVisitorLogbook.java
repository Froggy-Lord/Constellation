package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.ui.ConstellationUi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/inventory/LogBookStats.kt
public final class HerculesVisitorLogbook {
    public record Entry(String name,long visited,long accepted,int page){
        long denied(){return Math.max(0,visited-accepted);}
        double rate(){return visited<=0?0:accepted*100.0/visited;}
    }
    public record Row(String label,String value,int color){}

    private static final Pattern VISITED=Pattern.compile("(?i)^Times Visited:\\s*([0-9,.]+)$");
    private static final Pattern ACCEPTED=Pattern.compile("(?i)^Offers Accepted:\\s*([0-9,.]+)$");
    private static final Pattern PAGE=Pattern.compile("(?i).*Page\\s+([0-9]+)(?:\\s+(?:of|/)\\s*([0-9]+))?.*");
    private static final Pattern QUEUE=Pattern.compile("(?i)^Visitors?:\\s*\\(?([0-9]+)(?:/[0-9]+)?\\)?.*$");
    private static HerculesConfig cfg;
    private static final Map<String,Entry> SESSION=new LinkedHashMap<>();
    private static final Set<Integer> PAGES=new TreeSet<>();
    private static int knownTotalPages;
    private static String fingerprint="",sessionProfile="";
    private static long lastRead;

    private HerculesVisitorLogbook(){}

    public static void init(HerculesConfig config){
        cfg=config;
        if(cfg.visitorLogbookEntries==null)cfg.visitorLogbookEntries=new HashMap<>();
        if(cfg.visitorLogbookPages==null)cfg.visitorLogbookPages=new HashMap<>();
        ConstellationClient.tick().every(20,"hercules-visitor-logbook",HerculesVisitorLogbook::profileCheck);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||slot==null||!title(screen).equals("Visitor's Logbook"))return;
        if(slot.index==0){read(screen);drawPanel(graphics,screen);}
    }

    private static void read(AbstractContainerScreen<?> screen){
        profileCheck();
        long now=System.currentTimeMillis();if(now-lastRead<250)return;lastRead=now;
        String next=screen.getMenu().slots.stream().map(slot->slot.index+":"+name(slot.getItem())+":"+lore(slot.getItem())).toList().toString();
        if(next.equals(fingerprint))return;
        fingerprint=next;
        int page=currentPage(screen),total=totalPages(screen,page);
        if(page>0)PAGES.add(page);
        knownTotalPages=Math.max(knownTotalPages,total);
        boolean changed=false;
        for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty())continue;
            long visited=-1,accepted=-1;
            for(String line:lore(stack)){
                Matcher a=VISITED.matcher(line),b=ACCEPTED.matcher(line);
                if(a.matches())visited=number(a.group(1),-1);
                if(b.matches())accepted=number(b.group(1),-1);
            }
            if(visited<0||accepted<0)continue;
            Entry entry=new Entry(name(stack),visited,accepted,page);
            if(entry.name.isBlank())continue;
            Entry old=SESSION.put(entry.name,entry);
            if(!entry.equals(old))changed=true;
            if(cfg.visitorLogbookPersistent){
                String value=entry.visited+","+entry.accepted+","+entry.page;
                if(!Objects.equals(cfg.visitorLogbookEntries.put(key(entry.name),value),value))changed=true;
            }
        }
        if(cfg.visitorLogbookPersistent){
            String pageValue=pagesValue();
            if(!Objects.equals(cfg.visitorLogbookPages.put(profile(),pageValue),pageValue))changed=true;
        }
        if(changed)ConstellationClient.saveConfig();
    }

    private static int currentPage(AbstractContainerScreen<?> screen){
        int fromNext=-1,fromPrevious=-1;
        for(Slot slot:screen.getMenu().slots){
            String item=name(slot.getItem());
            if(!item.equalsIgnoreCase("Next Page")&&!item.equalsIgnoreCase("Previous Page"))continue;
            for(String line:lore(slot.getItem())){
                Matcher matcher=PAGE.matcher(line);if(!matcher.matches())continue;
                int target=(int)number(matcher.group(1),-1);
                if(item.equalsIgnoreCase("Next Page"))fromNext=target-1;else fromPrevious=target+1;
            }
        }
        if(fromNext>0)return fromNext;if(fromPrevious>0)return fromPrevious;return 1;
    }

    private static int totalPages(AbstractContainerScreen<?> screen,int current){
        int explicit=0;boolean hasNext=false;
        for(Slot slot:screen.getMenu().slots){
            if(name(slot.getItem()).equalsIgnoreCase("Next Page"))hasNext=true;
            for(String line:lore(slot.getItem())){
                Matcher matcher=PAGE.matcher(line);if(!matcher.matches()||matcher.group(2)==null)continue;
                explicit=Math.max(explicit,(int)number(matcher.group(2),0));
            }
        }
        return explicit>0?explicit:hasNext?0:current;
    }

    public static List<Row> rows(){
        List<Entry> entries=entries();
        long visited=entries.stream().mapToLong(Entry::visited).sum();
        long accepted=entries.stream().mapToLong(Entry::accepted).sum();
        int queue=currentQueue();
        long denied=Math.max(0,visited-accepted-queue);
        List<Row> rows=new ArrayList<>();
        if(cfg.visitorLogbookShowVisited)rows.add(new Row("Times Visited",format(visited),0xFF55FFFF));
        if(cfg.visitorLogbookShowAccepted)rows.add(new Row("Accepted",format(accepted),0xFF55FF55));
        if(cfg.visitorLogbookShowDenied)rows.add(new Row("Denied",format(denied),0xFFFF5555));
        if(cfg.visitorLogbookShowAcceptanceRate)rows.add(new Row("Acceptance",visited<=0?"0%":decimal(accepted*100.0/visited)+"%",0xFFFFFF55));
        if(cfg.visitorLogbookShowCurrentQueue)rows.add(new Row("Waiting now",format(queue),0xFFFFAA00));
        if(cfg.visitorLogbookShowPages){
            String value=PAGES.size()+(knownTotalPages>0?"/"+knownTotalPages:"")+" pages";
            rows.add(new Row("Captured",value,knownTotalPages>0&&PAGES.size()>=knownTotalPages?0xFF55FF55:0xFFFFAA00));
        }
        if(cfg.visitorLogbookShowTopVisitors&&!entries.isEmpty()){
            Comparator<Entry> comparator=cfg.visitorLogbookSortDenied
                ?Comparator.comparingLong(Entry::denied).reversed().thenComparing(Entry::name)
                :Comparator.comparingLong(Entry::visited).reversed().thenComparing(Entry::name);
            entries.sort(comparator);
            for(Entry entry:entries.subList(0,Math.min(Math.clamp(cfg.visitorLogbookTopRows,0,20),entries.size())))
                rows.add(new Row(entry.name,cfg.visitorLogbookSortDenied?format(entry.denied())+" denied":format(entry.visited())+" visits",0xFFFFFFFF));
        }
        return List.copyOf(rows);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen){
        List<Row> rows=rows();
        ContainerScreenAccessor accessor=(ContainerScreenAccessor)screen;int screenLeft=accessor.constellation$left(),imageWidth=accessor.constellation$imageWidth();
        int leftSpace=Math.max(0,screenLeft-4),rightSpace=Math.max(0,screen.width-screenLeft-imageWidth-4);boolean useRight=rightSpace>=leftSpace;
        int width=Math.min(174,useRight?rightSpace:leftSpace);if(width<56)return;
        int shown=Math.min(rows.size(),Math.max(1,(screen.height-24)/11)),height=20+Math.max(1,shown)*11;
        int x=useRight?imageWidth+4:-width-4,y=Math.clamp(0,2-accessor.constellation$top(),Math.max(2-accessor.constellation$top(),screen.height-accessor.constellation$top()-height-2));
        graphics.fill(x,y,x+width,y+height,cfg.visitorLogbookPanelColor);
        var font=Minecraft.getInstance().font;graphics.text(font,ConstellationUi.fit(font,"Visitor Logbook",width-12),x+6,y+5,0xFFFFAA00,true);
        if(rows.isEmpty())graphics.text(font,ConstellationUi.fit(font,"Open a Logbook page",width-12),x+6,y+17,0xFFFFAA00,false);
        int line=y+17;
        for(Row row:rows.subList(0,shown)){graphics.text(font,ConstellationUi.fit(font,row.label+": "+row.value,width-12),x+6,line,row.color,false);line+=11;}
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> input){
        if(!active()||!cfg.visitorLogbookVisitorTooltips||!title(screen).equals("Visitor's Logbook")||stack==null||stack.isEmpty())return input;
        Entry entry=entry(name(stack));if(entry==null)return input;
        List<Component> out=new ArrayList<>(input);
        out.add(Component.empty());
        out.add(Component.literal("§6Logbook Summary"));
        out.add(Component.literal("§7Accepted: §a"+format(entry.accepted)));
        out.add(Component.literal("§7Denied: §c"+format(entry.denied())));
        out.add(Component.literal("§7Acceptance: §e"+decimal(entry.rate())+"%"));
        return out;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("visitorlog")
            .executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset")
                .executes(c->{clear(false);save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetall")
                .executes(c->{clear(true);save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(0,20))
                    .executes(c->{cfg.visitorLogbookTopRows=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int option(String name,String raw){
        Boolean value=bool(raw);if(value==null){local("Use on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.visitorLogbookStats=value;case"persistent"->cfg.visitorLogbookPersistent=value;
            case"visited"->cfg.visitorLogbookShowVisited=value;case"accepted"->cfg.visitorLogbookShowAccepted=value;
            case"denied"->cfg.visitorLogbookShowDenied=value;case"rate"->cfg.visitorLogbookShowAcceptanceRate=value;
            case"queue"->cfg.visitorLogbookShowCurrentQueue=value;case"pages"->cfg.visitorLogbookShowPages=value;
            case"top"->cfg.visitorLogbookShowTopVisitors=value;case"tooltips"->cfg.visitorLogbookVisitorTooltips=value;
            case"sortdenied"->cfg.visitorLogbookSortDenied=value;default->{local("Unknown Visitor Logbook option.");return 0;}
        }
        save();return status();
    }

    private static int status(){List<Entry> entries=entries();local("Visitor Logbook "+on(cfg.visitorLogbookStats)+", "+entries.size()+" visitors, "+PAGES.size()+(knownTotalPages>0?"/"+knownTotalPages:"")+" pages captured.");return 1;}
    private static void profileCheck(){String profile=profile();if(profile.equals(sessionProfile))return;SESSION.clear();PAGES.clear();knownTotalPages=0;fingerprint="";lastRead=0;sessionProfile=profile;restore();}
    private static void restore(){
        if(!cfg.visitorLogbookPersistent)return;
        String prefix=profile()+"|";
        for(var value:cfg.visitorLogbookEntries.entrySet()){
            if(!value.getKey().startsWith(prefix))continue;
            String[] parts=value.getValue().split(",",-1);if(parts.length!=3)continue;
            String name=value.getKey().substring(prefix.length());
            Entry entry=new Entry(name,number(parts[0],-1),number(parts[1],-1),(int)number(parts[2],0));
            if(entry.visited>=0&&entry.accepted>=0)SESSION.put(name,entry);
        }
        String pages=cfg.visitorLogbookPages.get(profile());
        if(pages!=null&&!pages.isBlank()){String[] sides=pages.split("/",2);for(String raw:sides[0].split(",")){int page=(int)number(raw,-1);if(page>0)PAGES.add(page);}if(sides.length>1)knownTotalPages=(int)number(sides[1],0);}
    }
    private static void clear(boolean all){
        if(all){cfg.visitorLogbookEntries.clear();cfg.visitorLogbookPages.clear();}
        else{String prefix=profile()+"|";cfg.visitorLogbookEntries.keySet().removeIf(key->key.startsWith(prefix));cfg.visitorLogbookPages.remove(profile());}
        SESSION.clear();PAGES.clear();knownTotalPages=0;fingerprint="";lastRead=0;
    }
    private static String pagesValue(){return PAGES.stream().map(String::valueOf).reduce((a,b)->a+","+b).orElse("")+"/"+knownTotalPages;}
    private static List<Entry> entries(){profileCheck();return new ArrayList<>(SESSION.values());}
    private static Entry entry(String name){profileCheck();return SESSION.get(name);}
    private static int currentQueue(){
        Minecraft mc=Minecraft.getInstance();if(mc.getConnection()==null)return 0;
        for(PlayerInfo info:mc.getConnection().getOnlinePlayers()){
            Component display=info.getTabListDisplayName();String line=clean((display==null?info.getProfile().name():display.getString()));
            Matcher matcher=QUEUE.matcher(line);if(matcher.matches())return (int)number(matcher.group(1),0);
        }
        return 0;
    }
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.visitorLogbookStats&&ConstellationClient.loc().onHypixel();}
    private static String key(String name){return profile()+"|"+name;}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static String name(ItemStack stack){return stack==null||stack.isEmpty()?"":clean(stack.getHoverName().getString());}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String value){String result=ChatFormatting.stripFormatting(value);return result==null?"":result.trim();}
    private static long number(String value,long fallback){if(value==null||value.isBlank())return fallback;try{return (long)Double.parseDouble(value.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String format(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String decimal(double value){return String.format(Locale.ROOT,"%.1f",value);}
    private static String shortText(String value,int max){return value.length()>max?value.substring(0,max-3)+"...":value;}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Visitor Log] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
