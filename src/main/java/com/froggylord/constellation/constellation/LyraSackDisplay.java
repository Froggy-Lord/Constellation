package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.ui.ConstellationUi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-2.1): features/inventory/SackDisplay.kt
// ported from SkyHanni (LGPL-2.1): data/SackApi.kt
// ported from SkyHanni (LGPL-2.1): config/features/inventory/SackDisplayConfig.kt
public final class LyraSackDisplay {
    private record SackItem(Slot slot,ItemStack stack,String id,String name,long stored,long capacity,double price,boolean full) {}
    private record Panel(int x,int y,int width,int height,int rows,int pages) {}

    private static final Pattern TITLE=Pattern.compile("(?i)^(?:.* Sack|Enchanted .* Sack)$");
    private static final Pattern STORED=Pattern.compile("(?i)(?:Stored:\\s*)?(?<stored>[0-9.,kmb]+)\\s*/\\s*(?<total>[0-9.,kmb]+)");
    private static final Pattern GEM_QUALITY=Pattern.compile("(?i)(?<quality>Rough|Flawed|Fine|Amount):\\s*(?<stored>[0-9.,kmb]+)");
    private static final Pattern GEM_NAME=Pattern.compile("(?i)(?:Rough|Flawed|Fine)?\\s*(?<gem>[A-Za-z]+) Gemstones?");
    private static final Pattern GEM_FILTER=Pattern.compile("(?i)▶\\s*(Rough|Flawed|Fine)");
    private static final Set<String> SORTS=Set.of("STORED_DESC","STORED_ASC","PRICE_DESC","PRICE_ASC","NAME");
    private static final Set<String> FORMATS=Set.of("DEFAULT","FORMATTED","UNFORMATTED");
    private static final Set<String> PRICES=Set.of("BUY","SELL","NPC");
    private static LyraConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static List<SackItem> items=List.of();
    private static int page;
    private static long diagnosticUntil;
    private static boolean initialized;

    private LyraSackDisplay() {}

    public static void init(LyraConfig config){
        cfg=config;normalize();if(initialized)return;initialized=true;
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container)||!sack(container))return;
            screen=container;page=0;scan(container);
            ScreenEvents.afterTick(opened).register(ignored->scan(container));
            ScreenEvents.afterBackground(opened).register((ignored,g,mouseX,mouseY,delta)->draw(container,g,mouseX,mouseY));
            ScreenMouseEvents.allowMouseScroll(opened).register((ignored,x,y,horizontal,vertical)->scroll(container,x,y,vertical));
            ScreenEvents.remove(opened).register(ignored->{if(screen==container){screen=null;items=List.of();page=0;}});
        });
    }

    private static void scan(AbstractContainerScreen<?> container){
        if(container!=screen||!active())return;List<SackItem> next=new ArrayList<>();Minecraft mc=Minecraft.getInstance();
        String selectedGemQuality=gemstoneFilter(container);for(Slot slot:container.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty()||mc.player!=null&&slot.container==mc.player.getInventory())continue;
            List<String> lines=lore(stack);long stored=-1,capacity=-1;boolean runes=clean(container.getTitle().getString()).equalsIgnoreCase("Runes Sack");for(String line:lines){Matcher count=STORED.matcher(line);if(count.find()){if(stored<0){stored=0;capacity=0;}stored+=amount(count.group("stored"));capacity+=amount(count.group("total"));if(!runes)break;}}
            double gemPrice=-1;String gemType=gemType(stack);if(stored<0&&!gemType.isBlank()){long gems=0;double prices=0;boolean found=false;for(String line:lines){Matcher gem=GEM_QUALITY.matcher(line);if(!gem.find())continue;long count=amount(gem.group("stored"));String quality=gem.group("quality").equalsIgnoreCase("Amount")?(selectedGemQuality.isBlank()?filteredQuality(stack):selectedGemQuality):gem.group("quality").toUpperCase(Locale.ROOT);if(quality.isBlank())continue;String gemId=quality+"_"+gemType+"_GEM";gems+=count;prices+=price(gemId)*count;found=true;}if(found){stored=gems;capacity=0;gemPrice=prices;}}
            if(stored<0)continue;String id=LyraTooltips.marketId(stack);String name=clean(stack.getHoverName().getString());if(id.isBlank())id=name.toUpperCase(Locale.ROOT).replace(' ','_');
            double value=gemPrice>=0?gemPrice:price(id)*stored;next.add(new SackItem(slot,stack.copy(),id,name,stored,capacity,value,capacity>0&&stored>=capacity));
        }
        String search=cfg.sackSearch.trim().toLowerCase(Locale.ROOT);if(!search.isBlank())next.removeIf(item->!item.name.toLowerCase(Locale.ROOT).contains(search)&&!item.id.toLowerCase(Locale.ROOT).contains(search));
        if(!cfg.sackShowEmpty)next.removeIf(item->item.stored==0);next.sort(comparator());if(next.isEmpty()&&diagnostic())next.addAll(samples());items=List.copyOf(next);
    }

    private static Comparator<SackItem> comparator(){return switch(cfg.sackSort){case"STORED_ASC"->Comparator.comparingLong(SackItem::stored);case"PRICE_DESC"->Comparator.comparingDouble(SackItem::price).reversed();case"PRICE_ASC"->Comparator.comparingDouble(SackItem::price);case"NAME"->Comparator.comparing(item->item.name.toLowerCase(Locale.ROOT));default->Comparator.comparingLong(SackItem::stored).reversed();};}

    private static void draw(AbstractContainerScreen<?> container,GuiGraphicsExtractor g,int mouseX,int mouseY){
        if(container!=screen||!active()||!cfg.sackDisplay)return;Panel panel=panel(container);if(panel.width<70)return;int maxPage=Math.max(1,panel.pages);page=Math.clamp(page,0,maxPage-1);
        g.fill(panel.x,panel.y,panel.x+panel.width,panel.y+panel.height,cfg.sackBackground);g.fill(panel.x,panel.y,panel.x+2,panel.y+panel.height,cfg.sackAccentColor);
        Font font=Minecraft.getInstance().font;String title="Sack contents  "+(page+1)+"/"+maxPage;g.text(font,ConstellationUi.fit(font,title,panel.width-10),panel.x+6,panel.y+5,0xFFFFFFFF,true);
        int rowHeight=18+Math.clamp(cfg.sackRowSpacing,0,6),start=page*panel.rows,end=Math.min(items.size(),start+panel.rows);if(start>=end){g.text(font,items.isEmpty()?"No matching sack items":"No items on this page",panel.x+6,panel.y+19,0xFFAAAAAA,true);}else for(int index=start;index<end;index++){
            SackItem item=items.get(index);int row=index-start,ry=panel.y+18+row*rowHeight;boolean hover=mouseX>=panel.x+2&&mouseX<panel.x+panel.width&&mouseY>=ry&&mouseY<ry+rowHeight;
            if(hover&&cfg.sackHighlightHover)g.fill(panel.x+2,ry,panel.x+panel.width,ry+18,0x4055AAFF);int tx=panel.x+6;if(cfg.sackShowIcons){g.item(item.stack,tx,ry+1);tx+=19;}
            String amount=formatStored(item.stored)+(cfg.sackShowCapacity&&item.capacity>0?"/"+formatCapacity(item.capacity):"");String value=cfg.sackShowPrice&&item.price>0?formatPrice(item.price):"";int rightWidth=Math.max(font.width(amount),font.width(value));
            g.text(font,ConstellationUi.fit(font,item.name,Math.max(12,panel.x+panel.width-tx-rightWidth-8)),tx,ry+1,item.full?0xFFFF7777:0xFFFFFFFF,true);int amountX=cfg.sackAlignment.equals("LEFT")?tx:panel.x+panel.width-font.width(amount)-4;g.text(font,amount,amountX,ry+9,item.full?0xFFFF5555:0xFF55FFFF,true);if(!value.isBlank())g.text(font,value,panel.x+panel.width-font.width(value)-4,ry+9,0xFFFFAA00,false);
        }
        if(cfg.sackShowTotal){double total=items.stream().mapToDouble(SackItem::price).sum();String footer=items.size()+" items"+(cfg.sackShowPrice?" | "+formatPrice(total):"");g.text(font,ConstellationUi.fit(font,footer,panel.width-10),panel.x+6,panel.y+panel.height-11,0xFFAAAAAA,false);}
    }

    public static void drawSlot(GuiGraphicsExtractor g,AbstractContainerScreen<?> container,Slot slot){
        if(!active()||!cfg.sackHighlightFull||container!=screen||slot==null)return;for(SackItem item:items)if(item.slot==slot&&item.full){g.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.sackFullColor);return;}
    }

    private static boolean scroll(AbstractContainerScreen<?> container,double mouseX,double mouseY,double vertical){
        if(container!=screen||!active()||!cfg.sackDisplay||vertical==0)return true;Panel panel=panel(container);if(mouseX<panel.x||mouseX>=panel.x+panel.width||mouseY<panel.y||mouseY>=panel.y+panel.height||panel.pages<=1)return true;page=Math.clamp(page+(vertical<0?1:-1),0,panel.pages-1);return false;
    }

    private static Panel panel(AbstractContainerScreen<?> container){ContainerScreenAccessor a=(ContainerScreenAccessor)container;int left=a.constellation$left(),right=left+a.constellation$imageWidth();int leftSpace=Math.max(0,left-4),rightSpace=Math.max(0,container.width-right-4);boolean useRight=rightSpace>=leftSpace;int width=Math.min(Math.clamp(cfg.sackPanelWidth,90,300),useRight?rightSpace:leftSpace);int x=useRight?right+4:4,y=Math.clamp(a.constellation$top(),2,Math.max(2,container.height-24));int available=Math.max(36,container.height-y-2),rowHeight=18+Math.clamp(cfg.sackRowSpacing,0,6),rows=Math.min(Math.clamp(cfg.sackRows,1,45),Math.max(1,(available-30)/rowHeight));int pages=Math.max(1,(items.size()+rows-1)/rows),height=18+rows*rowHeight+(cfg.sackShowTotal?12:0);return new Panel(x,y,width,Math.min(height,available),rows,pages);}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sackdisplay").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").executes(c->test())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next").executes(c->{page++;return status();})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("previous").executes(c->{page=Math.max(0,page-1);return status();})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("count",IntegerArgumentType.integer(1,45)).executes(c->{cfg.sackRows=IntegerArgumentType.getInteger(c,"count");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("width").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("pixels",IntegerArgumentType.integer(90,300)).executes(c->{cfg.sackPanelWidth=IntegerArgumentType.getInteger(c,"pixels");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("format").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->format(StringArgumentType.getString(c,"mode"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceMode(StringArgumentType.getString(c,"source"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("search").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->{cfg.sackSearch=StringArgumentType.getString(c,"text").equalsIgnoreCase("clear")?"":StringArgumentType.getString(c,"text").trim();page=0;save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){local("Sack display "+on(cfg.sackDisplay)+", "+items.size()+" matching items, sort "+cfg.sackSort.toLowerCase(Locale.ROOT)+", price "+cfg.sackPriceSource.toLowerCase(Locale.ROOT)+".");return 1;}
    private static int test(){diagnosticUntil=System.currentTimeMillis()+15_000;local("Open any container in a local world within 15 seconds to preview the Sack display.");return 1;}
    private static int sort(String raw){String value=raw.toUpperCase(Locale.ROOT).replace('-','_');if(!SORTS.contains(value)){local("Sort must be stored_desc, stored_asc, price_desc, price_asc, or name.");return 0;}cfg.sackSort=value;page=0;save();return status();}
    private static int format(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!FORMATS.contains(value)){local("Format must be default, formatted, or unformatted.");return 0;}cfg.sackNumberFormat=value;save();return status();}
    private static int priceMode(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!PRICES.contains(value)){local("Price source must be buy, sell, or npc.");return 0;}cfg.sackPriceSource=value;save();return status();}
    private static int option(String name,String raw){Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.sackDisplay=value;case"full"->cfg.sackHighlightFull=value;case"empty"->cfg.sackShowEmpty=value;case"price"->cfg.sackShowPrice=value;case"total"->cfg.sackShowTotal=value;case"icons"->cfg.sackShowIcons=value;case"capacity"->cfg.sackShowCapacity=value;case"hover"->cfg.sackHighlightHover=value;case"local"->cfg.sackLocalWorlds=value;default->{local("Unknown Sack display option.");return 0;}}save();return status();}

    private static boolean sack(AbstractContainerScreen<?> container){return TITLE.matcher(clean(container.getTitle().getString())).matches()||diagnostic();}
    private static boolean active(){return cfg!=null&&cfg.enabled&&(ConstellationClient.loc().onHypixel()||cfg.sackLocalWorlds&&Minecraft.getInstance().hasSingleplayerServer()||diagnostic());}
    private static boolean diagnostic(){return System.currentTimeMillis()<diagnosticUntil;}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(Component::getString).map(LyraSackDisplay::clean).toList();}
    private static String gemType(ItemStack stack){Matcher matcher=GEM_NAME.matcher(clean(stack.getHoverName().getString()));return matcher.find()?matcher.group("gem").toUpperCase(Locale.ROOT):"";}
    private static String filteredQuality(ItemStack stack){String name=clean(stack.getHoverName().getString()).toUpperCase(Locale.ROOT);for(String quality:List.of("ROUGH","FLAWED","FINE"))if(name.startsWith(quality+" "))return quality;return"";}
    private static String gemstoneFilter(AbstractContainerScreen<?> container){if(!clean(container.getTitle().getString()).equalsIgnoreCase("Gemstones Sack")||container.getMenu().slots.size()<=41)return"";for(String line:lore(container.getMenu().getSlot(41).getItem())){Matcher matcher=GEM_FILTER.matcher(line);if(matcher.find())return matcher.group(1).toUpperCase(Locale.ROOT);}return"";}
    private static List<SackItem> samples(){return List.of(new SackItem(null,new ItemStack(Items.DIAMOND),"DIAMOND","Diamond",20_160,20_160,161_280,true),new SackItem(null,new ItemStack(Items.EMERALD),"EMERALD","Emerald",12_421,20_160,74_526,false),new SackItem(null,new ItemStack(Items.REDSTONE),"REDSTONE","Redstone",0,20_160,0,false),new SackItem(null,new ItemStack(Items.ENDER_PEARL),"ENDER_PEARL","Ender Pearl",7_840,20_160,23_520,false));}
    private static long amount(String raw){String value=raw.replace(",","").toLowerCase(Locale.ROOT);double factor=value.endsWith("b")?1e9:value.endsWith("m")?1e6:value.endsWith("k")?1e3:1;if(factor!=1)value=value.substring(0,value.length()-1);try{return Math.max(0,Math.round(Double.parseDouble(value)*factor));}catch(Exception ignored){return 0;}}
    private static double price(String id){return switch(cfg.sackPriceSource){case"SELL"->PriceProvider.sellValue(id);case"NPC"->PriceProvider.npcValue(id);default->PriceProvider.purchaseValue(id);};}
    private static String formatStored(long value){return cfg.sackNumberFormat.equals("FORMATTED")?compact(value):String.format(Locale.ROOT,"%,d",value);}
    private static String formatCapacity(long value){return cfg.sackNumberFormat.equals("UNFORMATTED")?String.format(Locale.ROOT,"%,d",value):compact(value);}
    private static String formatPrice(double value){return cfg.sackPriceFormat.equals("UNFORMATTED")?String.format(Locale.ROOT,"%,.0f",value):compact(value);}
    private static String compact(double value){double abs=Math.abs(value);if(abs>=1e9)return String.format(Locale.ROOT,"%.2fb",value/1e9);if(abs>=1e6)return String.format(Locale.ROOT,"%.2fm",value/1e6);if(abs>=1e3)return String.format(Locale.ROOT,"%.1fk",value/1e3);return String.format(Locale.ROOT,"%,.0f",value);}
    private static void normalize(){cfg.sackSort=cfg.sackSort==null||!SORTS.contains(cfg.sackSort.toUpperCase(Locale.ROOT))?"STORED_DESC":cfg.sackSort.toUpperCase(Locale.ROOT);cfg.sackNumberFormat=cfg.sackNumberFormat==null||!FORMATS.contains(cfg.sackNumberFormat.toUpperCase(Locale.ROOT))?"FORMATTED":cfg.sackNumberFormat.toUpperCase(Locale.ROOT);cfg.sackPriceSource=cfg.sackPriceSource==null||!PRICES.contains(cfg.sackPriceSource.toUpperCase(Locale.ROOT))?"BUY":cfg.sackPriceSource.toUpperCase(Locale.ROOT);cfg.sackPriceFormat=cfg.sackPriceFormat==null||!Set.of("FORMATTED","UNFORMATTED").contains(cfg.sackPriceFormat.toUpperCase(Locale.ROOT))?"FORMATTED":cfg.sackPriceFormat.toUpperCase(Locale.ROOT);}
    private static String clean(String text){String plain=ChatFormatting.stripFormatting(text);return plain==null?"":plain.trim().replaceAll("\\s+"," ");}
    private static Boolean bool(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String message){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Sacks] §f"+message));}
}
