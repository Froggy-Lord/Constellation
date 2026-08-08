package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.NeuRepoLoader;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.ui.ConstellationUi;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/accessories/AccessoriesHelper.java, AccessoriesHelperWidget.java, AccessoriesContainerSolver.java
// catalogue transport ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/info/DataTooltipInfo.java, TooltipInfoType.java
public final class LyraAccessoryHelper {
    private static final Pattern TITLE=Pattern.compile("^Accessory Bag(?: \\((\\d+)/(\\d+)\\))?$");
    private static final String ENDPOINT="https://hysky.de/api/accessories";
    private static final Path CACHE=Path.of("config","constellation-accessories.json");
    private static final long REFRESH=86_400_000L;
    private static final Map<String,Integer> MP=Map.of("COMMON",3,"UNCOMMON",5,"RARE",8,"EPIC",12,"LEGENDARY",16,"MYTHIC",22,"DIVINE",28,"SPECIAL",3,"VERY_SPECIAL",5);
    private static final AtomicBoolean FETCHING=new AtomicBoolean();
    private static final Map<String,Accessory> CATALOGUE=new LinkedHashMap<>();
    private static final Map<String,String> NAMES=new HashMap<>();
    private static final Map<String,Integer> ITEM_MP=new HashMap<>();
    private static final Map<String,Double> PRICES=new HashMap<>();
    private static LyraConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static int page=1,pages=1,panelPage;
    private static String hoveredFamily="";
    private static long loadedAt;
    private static int enrichIndex,priceIndex;
    private static boolean initialized;

    private LyraAccessoryHelper(){}

    public static void init(LyraConfig config){
        cfg=config;normalize();loadCache();
        if(initialized)return;initialized=true;
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container))return;
            Matcher matcher=TITLE.matcher(clean(container.getTitle().getString()));if(!matcher.matches())return;
            screen=container;page=matcher.group(1)==null?1:number(matcher.group(1));pages=matcher.group(2)==null?1:number(matcher.group(2));panelPage=0;
            ScreenEvents.afterTick(opened).register(ignored->scan(container));
            ScreenEvents.afterExtract(opened).register((ignored,graphics,mouseX,mouseY,delta)->drawPanel(container,graphics,mouseX,mouseY));
            ScreenEvents.remove(opened).register(ignored->{if(screen==container){screen=null;hoveredFamily="";}});
        });
        ItemTooltipCallback.EVENT.register((stack,context,flags,lines)->tooltip(stack,lines));
        ConstellationClient.tick().every(1200,"lyra-accessory-catalogue",LyraAccessoryHelper::refreshIfNeeded);
        ConstellationClient.tick().every(20,"lyra-accessory-metadata",LyraAccessoryHelper::enrichOne);
        refreshIfNeeded();
    }

    private static void scan(AbstractContainerScreen<?> container){
        if(!active()||container!=screen)return;
        Matcher matcher=TITLE.matcher(clean(container.getTitle().getString()));if(!matcher.matches())return;
        page=matcher.group(1)==null?1:number(matcher.group(1));pages=matcher.group(2)==null?1:number(matcher.group(2));
        Set<String> ids=new LinkedHashSet<>(),recomb=new LinkedHashSet<>();
        Minecraft mc=Minecraft.getInstance();
        for(Slot slot:container.getMenu().slots){
            if(slot.getItem().isEmpty()||mc.player!=null&&slot.container==mc.player.getInventory())continue;
            String id=LyraTooltips.marketId(slot.getItem());if(id.isBlank()||!accessory(slot.getItem(),id))continue;
            ids.add(id);if(recombobulated(slot.getItem()))recomb.add(id);
        }
        String profile=profile();Map<Integer,Set<String>> byPage=cfg.accessoryPagesByProfile.computeIfAbsent(profile,ignored->new LinkedHashMap<>());
        Set<String> old=byPage.put(page,ids);
        Set<String> allRecomb=cfg.recombobulatedAccessoriesByProfile.computeIfAbsent(profile,ignored->new LinkedHashSet<>());
        boolean changed=!ids.equals(old);if(cfg.accessoryTrackRecombobulated){Set<String> previous=new HashSet<>(allRecomb);allRecomb.removeAll(old==null?Set.of():old);allRecomb.addAll(recomb);changed|=!previous.equals(allRecomb);}
        if(changed&&cfg.accessoryPersistProfiles)ConstellationClient.saveConfig();
    }

    private static boolean accessory(ItemStack stack,String id){
        if(CATALOGUE.containsKey(id))return true;
        String name=clean(stack.getHoverName().getString()).toUpperCase(Locale.ROOT);
        return name.contains("TALISMAN")||name.contains("RING")||name.contains("ARTIFACT")||name.contains("RELIC")||lastLore(stack).matches("(?i).*(ACCESSORY|HATCCESSORY).*");
    }

    private static void tooltip(ItemStack stack,List<Component> lines){
        if(!active()||!cfg.accessoryTooltip||stack==null||stack.isEmpty())return;
        String id=LyraTooltips.marketId(stack);Accessory accessory=CATALOGUE.get(id);if(accessory==null)return;
        Report report=report(accessory);if(report.type==Type.INELIGIBLE)return;
        if(report.type==Type.OWNED&&!cfg.accessoryShowOwned||report.type==Type.DOWNGRADE&&!cfg.accessoryShowDowngrades)return;
        String suffix=report.highestTier>1?" §7("+report.ownedTier+"->"+accessory.tier+"/"+report.highestTier+")":"";
        int color=switch(report.type){case MISSING->cfg.accessoryMissingColor;case UPGRADE->cfg.accessoryUpgradeColor;case DOWNGRADE,OWNED->cfg.accessoryOwnedColor;default->0xFFAAAAAA;};
        String label=switch(report.type){case MISSING->"Missing accessory";case UPGRADE->"Accessory upgrade";case DOWNGRADE->"Better tier owned";case OWNED->"Highest tier owned";default->"";};
        lines.add(Component.literal(label+suffix).withColor(color&0xFFFFFF));
        if(cfg.accessoryShowMp){int gain=mp(accessory)-mp(report.owned);if(gain>0)lines.add(Component.literal("Magical Power gain: §b+"+gain));}
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> container,Slot slot){
        if(!active()||!cfg.accessoryHighlightOwnedFamily||container!=screen||slot==null||slot.getItem().isEmpty()||hoveredFamily.isBlank())return;
        Accessory acc=CATALOGUE.get(LyraTooltips.marketId(slot.getItem()));if(acc!=null&&acc.family.equals(hoveredFamily))graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.accessoryHighlightColor);
    }

    private static void drawPanel(AbstractContainerScreen<?> container,GuiGraphicsExtractor g,int mouseX,int mouseY){
        if(!active()||!cfg.accessoryBagPanel||container!=screen||!cfg.missingAccessoryHelper)return;
        List<Entry> entries=entries();
        ContainerScreenAccessor accessor=(ContainerScreenAccessor)container;
        int left=accessor.constellation$left(),containerRight=left+accessor.constellation$imageWidth();
        int leftSpace=Math.max(0,left-4),rightSpace=Math.max(0,container.width-containerRight-4);
        boolean useRight=rightSpace>=leftSpace;int panelWidth=Math.min(190,useRight?rightSpace:leftSpace);if(panelWidth<56)return;
        int x=useRight?containerRight+4:4,y=Math.clamp(accessor.constellation$top(),2,Math.max(2,container.height-20));
        int heightRows=Math.max(1,(container.height-y-20)/11),rows=Math.min(Math.clamp(cfg.accessoryPanelRows,3,18),heightRows);
        int maxPage=Math.max(1,(entries.size()+rows-1)/rows);panelPage=Math.clamp(panelPage,0,maxPage-1);
        int drawnRows=Math.min(rows,Math.max(0,entries.size()-panelPage*rows)),panelHeight=18+drawnRows*11;
        g.fill(x,y,x+panelWidth,y+panelHeight,0xD0101018);g.fill(x,y,x+2,y+panelHeight,0xFF55AAFF);
        Font font=Minecraft.getInstance().font;
        g.text(font,ConstellationUi.fit(font,"Accessory Helper  "+(panelPage+1)+"/"+maxPage,panelWidth-10),x+6,y+5,0xFF55FFFF,true);
        hoveredFamily="";
        for(int i=0;i<drawnRows;i++){int index=panelPage*rows+i;Entry entry=entries.get(index);int ry=y+18+i*11;boolean hover=mouseX>=x&&mouseX<x+panelWidth&&mouseY>=ry&&mouseY<ry+11;if(hover){g.fill(x+2,ry,x+panelWidth,ry+11,0x4055AAFF);hoveredFamily=entry.accessory.family;}
            String right="";if(cfg.accessoryShowMp)right+="+"+entry.mp+" MP";if(cfg.accessoryShowPrice&&entry.price>0)right+=(right.isBlank()?"":" | ")+coins(entry.price);
            int color=entry.type==Type.MISSING?cfg.accessoryMissingColor:cfg.accessoryUpgradeColor;String name=display(entry.accessory.id);
            if(panelWidth>=130&&!right.isBlank()){int rightWidth=font.width(right);g.text(font,ConstellationUi.fit(font,name,Math.max(16,panelWidth-rightWidth-15)),x+6,ry+1,color,true);g.text(font,right,x+panelWidth-rightWidth-4,ry+1,0xFFAAAAAA,true);}
            else g.text(font,ConstellationUi.fit(font,name,panelWidth-10),x+6,ry+1,color,true);
        }
    }

    private static List<Entry> entries(){
        String search=cfg.accessorySearch.toLowerCase(Locale.ROOT);List<Entry> out=new ArrayList<>();
        Set<String> represented=new HashSet<>();
        for(Accessory acc:CATALOGUE.values()){
            Report report=report(acc);if(report.type!=Type.MISSING&&report.type!=Type.UPGRADE)continue;
            if(cfg.accessoryHighestTierOnly&&acc.tier<highestTier(acc.family)||!represented.add(acc.family))continue;
            if(report.type==Type.MISSING&&!cfg.accessoryShowMissing||report.type==Type.UPGRADE&&!cfg.accessoryShowUpgrades)continue;
            String filter=cfg.accessoryFilter.toUpperCase(Locale.ROOT);if(filter.equals("MISSING")&&report.type!=Type.MISSING||filter.equals("UPGRADES")&&report.type!=Type.UPGRADE)continue;
            if(!search.isBlank()&&!display(acc.id).toLowerCase(Locale.ROOT).contains(search)&&!acc.id.toLowerCase(Locale.ROOT).contains(search))continue;
            int gain=Math.max(0,mp(acc)-mp(report.owned));if(gain<=0)continue;double price=PRICES.getOrDefault(acc.id,0d);out.add(new Entry(acc,report.type,gain,price,price>0?price/gain:Double.MAX_VALUE));
        }
        Comparator<Entry> comparator=cfg.accessorySortPricePerMp?Comparator.comparingDouble(Entry::pricePerMp):Comparator.comparing(e->display(e.accessory.id));
        out.sort(comparator.thenComparing(e->e.accessory.id));return out;
    }

    private static Report report(Accessory target){
        if(target.origin.equals("RIFT"))return new Report(Type.INELIGIBLE,0,target.tier,null);
        Accessory owned=null,highest=null;for(Accessory acc:CATALOGUE.values())if(acc.family.equals(target.family)){if(highest==null||acc.tier>highest.tier)highest=acc;if(owned().contains(acc.id)&&(owned==null||acc.tier>owned.tier))owned=acc;}
        int high=highest==null?target.tier:highest.tier,have=owned==null?0:owned.tier;
        Type type=owned==null?Type.MISSING:target.tier>have?Type.UPGRADE:have>target.tier?Type.DOWNGRADE:target.tier==high?Type.OWNED:Type.DOWNGRADE;
        return new Report(type,have,high,owned);
    }

    private static int highestTier(String family){int highest=0;for(Accessory acc:CATALOGUE.values())if(acc.family.equals(family))highest=Math.max(highest,acc.tier);return highest;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("accessoryhelper").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refresh").executes(c->{fetch(true);local("Refreshing accessory catalogue.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next").executes(c->{panelPage++;return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("previous").executes(c->{panelPage=Math.max(0,panelPage-1);return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("confirm").executes(c->clear())))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("filter").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->filter(StringArgumentType.getString(c,"mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("search").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->search(StringArgumentType.getString(c,"text")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(3,18)).executes(c->{cfg.accessoryPanelRows=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("kind",StringArgumentType.word()).then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"kind"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int status(){local("Accessory helper: "+owned().size()+" owned | "+CATALOGUE.size()+" catalogue | bag "+scannedPages()+"/"+pages+" pages | "+entries().size()+" suggestions");return 1;}
    private static int filter(String mode){mode=mode.toUpperCase(Locale.ROOT);if(!Set.of("ALL","MISSING","UPGRADES").contains(mode)){local("Filter must be all, missing, or upgrades.");return 0;}cfg.accessoryFilter=mode;panelPage=0;save();return status();}
    private static int search(String text){cfg.accessorySearch=text.equalsIgnoreCase("clear")?"":text.trim();panelPage=0;save();return status();}
    private static int clear(){cfg.accessoryPagesByProfile.remove(profile());cfg.recombobulatedAccessoriesByProfile.remove(profile());save();return status();}
    private static int option(String name,String raw){Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.accessoryDisplay=value;case"helper"->cfg.missingAccessoryHelper=value;case"tooltip"->cfg.accessoryTooltip=value;case"panel"->cfg.accessoryBagPanel=value;case"highest"->cfg.accessoryHighestTierOnly=value;case"missing"->cfg.accessoryShowMissing=value;case"upgrades"->cfg.accessoryShowUpgrades=value;case"downgrades"->cfg.accessoryShowDowngrades=value;case"owned"->cfg.accessoryShowOwned=value;case"pricepermp"->cfg.accessorySortPricePerMp=value;case"price"->cfg.accessoryShowPrice=value;case"mp"->cfg.accessoryShowMp=value;case"highlight"->cfg.accessoryHighlightOwnedFamily=value;case"recomb"->cfg.accessoryTrackRecombobulated=value;case"persist"->cfg.accessoryPersistProfiles=value;default->{local("Unknown Accessory Helper option.");return 0;}}save();return status();}
    private static int color(String kind,String raw){Integer value=parseColor(raw);if(value==null){local("Color must be six or eight hexadecimal digits.");return 0;}switch(kind.toLowerCase(Locale.ROOT)){case"missing"->cfg.accessoryMissingColor=value;case"upgrade"->cfg.accessoryUpgradeColor=value;case"owned"->cfg.accessoryOwnedColor=value;case"highlight"->cfg.accessoryHighlightColor=value;default->{local("Color kind must be missing, upgrade, owned, or highlight.");return 0;}}save();return status();}

    private static void refreshIfNeeded(){if(!active())return;if(CATALOGUE.isEmpty()||System.currentTimeMillis()-loadedAt>REFRESH)fetch(false);}
    private static void enrichOne(){
        if(!active()||CATALOGUE.isEmpty())return;
        List<Accessory> all=List.copyOf(CATALOGUE.values());if(enrichIndex>=all.size())enrichIndex=0;
        Accessory accessory=all.get(enrichIndex);JsonObject item=NeuRepoLoader.get(accessory.id);
        if(item!=null){NAMES.put(accessory.id,clean(item.has("displayname")?item.get("displayname").getAsString():accessory.id).replaceAll("\\[[^]]+]","").trim());ITEM_MP.put(accessory.id,readMp(accessory,item));PriceProvider.warm(accessory.id);enrichIndex++;}
        if(!ITEM_MP.isEmpty()){List<String> enriched=List.copyOf(ITEM_MP.keySet());if(priceIndex>=enriched.size())priceIndex=0;String id=enriched.get(priceIndex++);double price=PriceProvider.purchaseValue(id);if(price>0)PRICES.put(id,price);}
    }
    private static void fetch(boolean forced){if(!forced&&!CATALOGUE.isEmpty()&&System.currentTimeMillis()-loadedAt<REFRESH||!FETCHING.compareAndSet(false,true))return;Thread thread=new Thread(()->{try{HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();HttpRequest request=HttpRequest.newBuilder(URI.create(ENDPOINT)).timeout(Duration.ofSeconds(12)).header("User-Agent","Constellation/0.9").GET().build();HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()==200&&parse(response.body())){Files.createDirectories(CACHE.getParent());try(Writer writer=Files.newBufferedWriter(CACHE)){writer.write(response.body());}loadedAt=System.currentTimeMillis();}}catch(Exception ignored){}finally{FETCHING.set(false);}},"constellation-accessories");thread.setDaemon(true);thread.start();}
    private static void loadCache(){if(!Files.exists(CACHE))return;try(Reader reader=Files.newBufferedReader(CACHE)){if(parse(JsonParser.parseReader(reader).toString()))loadedAt=Files.getLastModifiedTime(CACHE).toMillis();}catch(Exception ignored){}}
    private static boolean parse(String json){try{JsonObject root=JsonParser.parseString(json).getAsJsonObject();Map<String,Accessory> next=new LinkedHashMap<>();for(var entry:root.entrySet()){JsonObject object=entry.getValue().getAsJsonObject();String id=entry.getKey().toUpperCase(Locale.ROOT),family=object.has("family")?object.get("family").getAsString():id,origin=object.has("origin")?object.get("origin").getAsString():"";int tier=object.has("tier")?object.get("tier").getAsInt():0;next.put(id,new Accessory(id,family,tier,origin,object.has("enrichable")&&object.get("enrichable").getAsBoolean(),!object.has("recombobulatable")||object.get("recombobulatable").getAsBoolean()));}if(next.size()<50)return false;synchronized(CATALOGUE){CATALOGUE.clear();CATALOGUE.putAll(next);}return true;}catch(Exception ignored){return false;}}

    private static Set<String> owned(){Map<Integer,Set<String>> map=cfg.accessoryPagesByProfile.get(profile());if(map==null)return Set.of();Set<String> out=new HashSet<>();map.values().forEach(out::addAll);return out;}
    private static int scannedPages(){Map<Integer,Set<String>> map=cfg.accessoryPagesByProfile.get(profile());return map==null?0:map.size();}
    private static int mp(Accessory acc){return acc==null?0:ITEM_MP.getOrDefault(acc.id,0);}
    private static int readMp(Accessory acc,JsonObject item){String rarity="";if(item.has("lore"))for(JsonElement line:item.getAsJsonArray("lore")){String clean=clean(line.getAsString()).toUpperCase(Locale.ROOT);for(String key:MP.keySet())if(clean.contains(key+" ACCESSORY")||clean.contains(key+" HATCCESSORY"))rarity=key;}int value=MP.getOrDefault(rarity,0);return acc.id.equals("HEGEMONY_ARTIFACT")?value*2:value;}
    private static String display(String id){return NAMES.getOrDefault(id,human(id));}
    private static String human(String id){StringBuilder out=new StringBuilder();for(String part:id.toLowerCase(Locale.ROOT).split("_"))if(!part.isBlank())out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');return out.toString().trim();}
    private static String lastLore(ItemStack stack){var lore=stack.get(DataComponents.LORE);return lore==null||lore.lines().isEmpty()?"":clean(lore.lines().getLast().getString());}
    private static boolean recombobulated(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return false;CompoundTag extra=data.copyTag().getCompoundOrEmpty("ExtraAttributes");return extra.getIntOr("rarity_upgrades",0)>0;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.accessoryDisplay&&ConstellationClient.loc().onHypixel();}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static void normalize(){if(cfg.accessoryPagesByProfile==null)cfg.accessoryPagesByProfile=new LinkedHashMap<>();if(cfg.recombobulatedAccessoriesByProfile==null)cfg.recombobulatedAccessoriesByProfile=new LinkedHashMap<>();}
    private static String clean(String value){String stripped=ChatFormatting.stripFormatting(value);return stripped==null?"":stripped.trim();}
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception ignored){return 1;}}
    private static String coins(double value){if(value>=1e9)return String.format(Locale.ROOT,"%.1fB",value/1e9);if(value>=1e6)return String.format(Locale.ROOT,"%.1fM",value/1e6);if(value>=1e3)return String.format(Locale.ROOT,"%.0fk",value/1e3);return String.format(Locale.ROOT,"%.0f",value);}
    private static Boolean bool(String raw){if(raw.equalsIgnoreCase("on")||raw.equalsIgnoreCase("true"))return true;if(raw.equalsIgnoreCase("off")||raw.equalsIgnoreCase("false"))return false;return null;}
    private static Integer parseColor(String raw){String value=raw.startsWith("#")?raw.substring(1):raw;if(!value.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))return null;try{return(int)Long.parseLong(value.length()==6?"FF"+value:value,16);}catch(Exception ignored){return null;}}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal(text));}
    private enum Type{MISSING,UPGRADE,DOWNGRADE,OWNED,INELIGIBLE}
    private record Accessory(String id,String family,int tier,String origin,boolean enrichable,boolean recombobulatable){}
    private record Report(Type type,int ownedTier,int highestTier,Accessory owned){}
    private record Entry(Accessory accessory,Type type,int mp,double price,double pricePerMp){}
}
