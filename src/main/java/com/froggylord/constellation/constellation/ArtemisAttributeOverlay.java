package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.ArtemisConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/AttributeShardOverlay.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/AttributeShardsData.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/AttributesShardsInventory.kt
public final class ArtemisAttributeOverlay {
    public record Row(String key,String name,int tier,int toNext,int toMax,int box,int needed,double unit,double price,boolean priced,boolean enabled){}
    public record State(List<Row> rows,int known,int unlocked,int maxed,int levels,int missingPrices,double total,boolean complete){}
    // ported from NotEnoughUpdates-REPO (MIT): constants/attribute_shards.json
    private static final Map<String,int[]> LEVELS=Map.of(
        "COMMON",new int[]{1,3,5,6,7,8,10,14,18,24},
        "UNCOMMON",new int[]{1,2,3,4,5,6,7,8,12,16},
        "RARE",new int[]{1,2,3,3,4,4,5,6,8,12},
        "EPIC",new int[]{1,1,2,2,3,3,4,4,5,7},
        "LEGENDARY",new int[]{1,1,1,2,2,2,3,3,4,5});
    private static final Pattern NAME=Pattern.compile("^(.+?)(?: ([IVX]+))?$");
    private static final Pattern SYPHON=Pattern.compile("^Syphon ([\\d,]+) shards? to (?:level up|unlock)!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern OWNED=Pattern.compile("^Owned: ([\\d,]+) Shards?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ENABLED=Pattern.compile("^Enabled: (Yes|No)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern RARITY=Pattern.compile("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY)(?: .*)?$",Pattern.CASE_INSENSITIVE);
    private static ArtemisConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static State state;
    private static Set<String> current=Set.of();
    private static boolean initialized;

    private ArtemisAttributeOverlay(){}

    public static void init(ArtemisConfig config){
        cfg=config;maps();if(initialized)return;initialized=true;
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container))return;
            String title=clean(container.getTitle().getString());
            if(!title.equals("Attribute Menu")&&!title.equals("Hunting Box"))return;
            screen=container;update(container);
            ScreenEvents.afterTick(opened).register(ignored->update(container));
            ScreenEvents.remove(opened).register(ignored->{if(screen==container){screen=null;current=Set.of();state=null;}});
        });
    }

    private static void update(AbstractContainerScreen<?> container){
        if(container!=screen||!active())return;
        boolean menu=title(container).equals("Attribute Menu"),box=title(container).equals("Hunting Box");
        Set<String> visible=new HashSet<>();boolean changed=false;
        for(Slot slot:container.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty())continue;
            Parsed parsed=parse(stack,box);if(parsed==null)continue;
            String key=profile()+"|"+ArtemisHuntingProfit.shardMarketId(parsed.name);
            visible.add(key);cfg.attributeOverlayNames.put(key,parsed.name);
            if(box&&parsed.owned>=0){changed|=!Objects.equals(cfg.attributeOverlayBox.put(key,parsed.owned),parsed.owned);}
            if(parsed.rarity!=null&&parsed.toNext>=0){
                int total=syphoned(parsed.tier,parsed.toNext,parsed.rarity);
                changed|=!Objects.equals(cfg.attributeOverlaySyphoned.put(key,total),total);
                changed|=!Objects.equals(cfg.attributeOverlayTier.put(key,parsed.tier),parsed.tier);
                changed|=!Objects.equals(cfg.attributeOverlayToNext.put(key,parsed.toNext),parsed.toNext);
                changed|=!Objects.equals(cfg.attributeOverlayRarity.put(key,parsed.rarity),parsed.rarity);
            }
            if(menu&&parsed.enabled!=null)changed|=!Objects.equals(cfg.attributeOverlayEnabled.put(key,parsed.enabled),parsed.enabled);
        }
        current=Set.copyOf(visible);if(changed)ConstellationClient.saveConfig();refresh();
    }

    private record Parsed(String name,int tier,int toNext,int owned,String rarity,Boolean enabled){}
    private static Parsed parse(ItemStack stack,boolean box){
        String raw=clean(stack.getHoverName().getString()).replaceFirst("(?i) Shard$","").trim();
        Matcher nm=NAME.matcher(raw);if(!nm.matches())return null;
        String name=nm.group(1).trim();int tier=roman(nm.group(2)),toNext=-1,owned=-1;String rarity=null;Boolean enabled=null;
        ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return null;
        for(Component component:lore.lines()){
            String line=clean(component.getString());Matcher match=SYPHON.matcher(line);
            if(match.matches())toNext=number(match.group(1),-1);
            match=OWNED.matcher(line);if(match.matches())owned=number(match.group(1),-1);
            match=ENABLED.matcher(line);if(match.matches())enabled=match.group(1).equalsIgnoreCase("Yes");
            match=RARITY.matcher(line);if(match.matches())rarity=match.group(1).toUpperCase(Locale.ROOT);
            if(box){Matcher loreName=Pattern.compile("^.+?(?: ([IVX]+))? \\([^)]*\\)$").matcher(line);if(loreName.matches()&&loreName.group(1)!=null)tier=roman(loreName.group(1));}
        }
        if(toNext<0&&tier==10)toNext=0;
        if(toNext<0&&owned<0&&enabled==null)return null;
        return new Parsed(name,tier,toNext,owned,rarity,enabled);
    }

    private static void refresh(){
        maps();String prefix=profile()+"|";List<Row> rows=new ArrayList<>();int known=0,unlocked=0,maxed=0,totalLevels=0,missing=0;double total=0;
        for(var entry:cfg.attributeOverlayNames.entrySet()){
            String key=entry.getKey();if(!key.startsWith(prefix))continue;if(cfg.attributeOverlayOnlyCurrentInventory&&!current.contains(key))continue;
            int tier=Math.clamp(cfg.attributeOverlayTier.getOrDefault(key,0),0,10);
            String rarity=cfg.attributeOverlayRarity.get(key),name=entry.getValue();int[] levels=LEVELS.get(rarity);if(levels==null)continue;
            known++;if(tier>0)unlocked++;if(tier==10)maxed++;totalLevels+=tier;
            if(cfg.attributeOverlayHideMaxed&&tier==10)continue;
            if(cfg.attributeOverlayOnlyNotUnlocked&&tier>0)continue;
            int syphoned=Math.max(0,cfg.attributeOverlaySyphoned.getOrDefault(key,0)),toNext=remainingNext(syphoned,levels),toMax=Math.max(0,sum(levels)-syphoned);
            int box=cfg.attributeOverlayIncludeHuntingBox?Math.max(0,cfg.attributeOverlayBox.getOrDefault(key,0)):0;
            int wanted=cfg.attributeOverlaySort.equalsIgnoreCase("PRICE_TO_NEXT")?Math.max(0,toNext-box):Math.max(0,toMax-box);
            String id=key.substring(prefix.length());double unit=cfg.attributeOverlayPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);
            if(unit<=0)PriceProvider.warm(id);boolean priced=unit>0;double price=unit*wanted;
            if(!priced&&wanted>0)missing++;if(priced)total+=price;
            boolean enabled=cfg.attributeOverlayEnabled.getOrDefault(key,false);
            rows.add(new Row(key,name,tier,toNext,toMax,box,wanted,unit,price,priced,enabled));
        }
        Comparator<Row> comparator=switch(cfg.attributeOverlaySort.toUpperCase(Locale.ROOT)){
            case"PRICE_TO_NEXT"->Comparator.comparingDouble(r->r.priced?r.price:Double.MAX_VALUE);
            case"TIER"->Comparator.comparingInt(Row::tier);
            case"NAME"->Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);
            default->Comparator.comparingDouble(r->r.priced?r.price:Double.MAX_VALUE);
        };
        rows.sort(comparator);state=new State(List.copyOf(rows),known,unlocked,maxed,totalLevels,missing,total,missing==0);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> container,Slot slot){
        if(!active(container)||slot==null||slot.getItem().isEmpty())return;Parsed parsed=parse(slot.getItem(),false);if(parsed==null)return;
        if(cfg.attributeOverlayHighlightDisabled&&Boolean.FALSE.equals(parsed.enabled))graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.attributeOverlayDisabledColor);
        if(cfg.attributeOverlayTierAsStackSize){
            String text=Integer.toString(parsed.tier);int color=tierColor(parsed.tier);Font font=Minecraft.getInstance().font;
            graphics.text(font,text,slot.x+16-font.width(text),slot.y+8,color,true);
        }
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> container,ItemStack stack,List<Component> original){
        if(!active(container)||!cfg.attributeOverlayTooltips||stack==null||stack.isEmpty())return original;
        Parsed parsed=parse(stack,false);if(parsed==null)return original;String key=profile()+"|"+ArtemisHuntingProfit.shardMarketId(parsed.name);
        Row row=state==null?null:state.rows.stream().filter(value->value.key.equals(key)).findFirst().orElse(null);if(row==null)return original;
        List<Component> out=new ArrayList<>(original);int at=Math.min(1,out.size());
        out.add(at++,Component.literal("§6Tier: §f"+row.tier+"/10"));
        out.add(at++,Component.literal("§6Needed: §f"+row.needed+" §7("+modeLabel()+")"));
        out.add(at++,Component.literal("§6Price: §f"+ArtemisHuntingProfit.coins(row.price,row.priced)));
        if(cfg.attributeOverlayIncludeHuntingBox)out.add(at,Component.literal("§6Hunting Box: §f"+row.box));
        return out;
    }

    public static State state(){return state;}public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){return active(screen)&&title(screen).equals("Attribute Menu")&&state!=null;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.attributeOverlay&&ConstellationClient.loc().onHypixel();}
    private static boolean active(AbstractContainerScreen<?> container){return active()&&container==screen&&title(container).equals("Attribute Menu");}
    private static String title(AbstractContainerScreen<?> container){return container==null?"":clean(container.getTitle().getString());}
    private static int syphoned(int tier,int toNext,String rarity){int[] levels=LEVELS.get(rarity);if(levels==null)return 0;return Math.max(0,sum(levels,Math.min(10,tier+1))-Math.max(0,toNext));}
    private static int remainingNext(int total,int[] levels){int cumulative=0;for(int amount:levels){cumulative+=amount;if(cumulative>total)return cumulative-total;}return 0;}
    private static int sum(int[] values){return sum(values,values.length);}private static int sum(int[] values,int limit){int out=0;for(int i=0;i<Math.min(limit,values.length);i++)out+=values[i];return out;}
    private static int roman(String value){if(value==null)return 0;int total=0,last=0;for(int i=value.length()-1;i>=0;i--){int now=switch(value.charAt(i)){case'I'->1;case'V'->5;case'X'->10;default->0;};total+=now<last?-now:now;last=now;}return Math.clamp(total,0,10);}
    private static int tierColor(int tier){return tier==10?0xFFFFAA00:tier>=6?0xFF55FF55:tier>=1?0xFFFFFF55:0xFFFF5555;}
    private static int number(String raw,int fallback){try{return Integer.parseInt(raw.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static String modeLabel(){return cfg.attributeOverlaySort.equalsIgnoreCase("PRICE_TO_NEXT")?"next tier":"max tier";}
    private static void maps(){if(cfg==null)return;if(cfg.attributeOverlaySyphoned==null)cfg.attributeOverlaySyphoned=new HashMap<>();if(cfg.attributeOverlayTier==null)cfg.attributeOverlayTier=new HashMap<>();if(cfg.attributeOverlayToNext==null)cfg.attributeOverlayToNext=new HashMap<>();if(cfg.attributeOverlayBox==null)cfg.attributeOverlayBox=new HashMap<>();if(cfg.attributeOverlayRarity==null)cfg.attributeOverlayRarity=new HashMap<>();if(cfg.attributeOverlayNames==null)cfg.attributeOverlayNames=new HashMap<>();if(cfg.attributeOverlayEnabled==null)cfg.attributeOverlayEnabled=new HashMap<>();}
    private static void save(){ConstellationClient.saveConfig();}private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Attributes] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("attributeoverlay").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,100)).executes(c->{cfg.attributeOverlayRows=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->price(StringArgumentType.getString(c,"source")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){refresh();local("Overlay "+on(cfg.attributeOverlay)+", "+(state==null?0:state.rows.size())+" rows, sorted by "+modeLabel()+", price "+cfg.attributeOverlayPriceSource.toLowerCase(Locale.ROOT)+".");return 1;}
    private static int sort(String raw){String value=raw.toUpperCase(Locale.ROOT).replace("-","_");if(value.equals("MAX")||value.equals("PRICE_TO_MAXED"))value="PRICE_TO_MAXED";else if(value.equals("NEXT")||value.equals("PRICE_TO_NEXT"))value="PRICE_TO_NEXT";else if(!value.equals("NAME")&&!value.equals("TIER")){local("Sort must be max, next, tier, or name.");return 0;}cfg.attributeOverlaySort=value;save();return status();}
    private static int price(String raw){if(raw.equalsIgnoreCase("sell")||raw.equalsIgnoreCase("order"))cfg.attributeOverlayPriceSource="SELL";else if(raw.equalsIgnoreCase("buy")||raw.equalsIgnoreCase("instant"))cfg.attributeOverlayPriceSource="BUY";else{local("Price source must be sell or buy.");return 0;}save();return status();}
    private static int option(String name,String raw){Boolean value=parseState(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.attributeOverlay=value;case"hud"->cfg.attributeOverlayHud=value;case"hidemaxed"->cfg.attributeOverlayHideMaxed=value;case"onlylocked"->cfg.attributeOverlayOnlyNotUnlocked=value;case"huntingbox"->cfg.attributeOverlayIncludeHuntingBox=value;case"current"->cfg.attributeOverlayOnlyCurrentInventory=value;case"tiertext"->cfg.attributeOverlayTierAsStackSize=value;case"disabled"->cfg.attributeOverlayHighlightDisabled=value;case"tooltips"->cfg.attributeOverlayTooltips=value;case"tier"->cfg.attributeOverlayShowTier=value;case"needed"->cfg.attributeOverlayShowNeeded=value;case"price"->cfg.attributeOverlayShowPrice=value;case"box"->cfg.attributeOverlayShowBox=value;case"summary"->cfg.attributeOverlayShowSummary=value;case"hideunknown"->cfg.attributeOverlayHideUnknownPrice=value;default->{local("Unknown attribute-overlay option.");return 0;}}save();refresh();return status();}
    private static Boolean parseState(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}private static String on(boolean value){return value?"on":"off";}
}
