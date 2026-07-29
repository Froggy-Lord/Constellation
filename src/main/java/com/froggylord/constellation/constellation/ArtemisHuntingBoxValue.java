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

// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/HuntingBoxValue.kt
public final class ArtemisHuntingBoxValue {
    public record Row(int slot,String name,String id,long amount,double sellUnit,double buyUnit){
        public double sellTotal(){return sellUnit*amount;}public double buyTotal(){return buyUnit*amount;}
        public boolean sellPriced(){return sellUnit>0;}public boolean buyPriced(){return buyUnit>0;}
    }
    public record State(List<Row> rows,long totalShards,double totalSell,double totalBuy,
                        boolean sellComplete,boolean buyComplete,boolean error,long updatedAt){}
    private static final Pattern OWNED=Pattern.compile("^Owned: ([\\d,]+) Shards?$",Pattern.CASE_INSENSITIVE);
    private static ArtemisConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static State state;
    private static boolean initialized;

    private ArtemisHuntingBoxValue(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container)||!clean(container.getTitle().getString()).equals("Hunting Box"))return;
            screen=container;update(container);
            ScreenEvents.afterTick(opened).register(ignored->update(container));
            ScreenEvents.remove(opened).register(ignored->{if(screen==container){screen=null;state=null;}});
        });
    }

    private static void update(AbstractContainerScreen<?> container){
        if(container!=screen||!active())return;
        List<Row> all=new ArrayList<>();long amount=0;double sell=0,buy=0;boolean sellComplete=true,buyComplete=true;
        boolean populated=false;
        for(Slot slot:container.getMenu().slots){
            if(!validSlot(slot.index))continue;ItemStack stack=slot.getItem();if(stack.isEmpty())continue;populated=true;
            long owned=loreAmount(stack);if(owned<0)continue;
            if(cfg.huntingBoxValueHideZero&&owned==0)continue;
            String name=clean(stack.getHoverName().getString()).replaceFirst("(?i) Shard$","").trim();
            if(name.isBlank())continue;String id=ArtemisHuntingProfit.shardMarketId(name);
            double sellUnit=PriceProvider.sellValue(id),buyUnit=PriceProvider.purchaseValue(id);
            if(sellUnit<=0||buyUnit<=0)PriceProvider.warm(id);
            Row row=new Row(slot.index,name,id,owned,sellUnit,buyUnit);all.add(row);amount+=owned;
            sell+=row.sellTotal();buy+=row.buyTotal();sellComplete&=row.sellPriced();buyComplete&=row.buyPriced();
        }
        all.sort(comparator());
        state=new State(List.copyOf(all),amount,sell,buy,sellComplete,buyComplete,populated&&all.isEmpty(),System.currentTimeMillis());
    }

    private static Comparator<Row> comparator(){return switch(cfg.huntingBoxValueSort.toUpperCase(Locale.ROOT)){
        case"BUY_DESC"->Comparator.comparingDouble(Row::buyTotal).reversed();
        case"AMOUNT_DESC"->Comparator.comparingLong(Row::amount).reversed();
        case"NAME"->Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);
        default->Comparator.comparingDouble(Row::sellTotal).reversed();
    };}
    private static boolean validSlot(int slot){return slot>=9&&slot<=44&&slot%9!=0&&slot%9!=8;}
    private static long loreAmount(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return-1;for(Component line:lore.lines()){Matcher matcher=OWNED.matcher(clean(line.getString()));if(matcher.matches())try{return Long.parseLong(matcher.group(1).replace(",",""));}catch(Exception ignored){return-1;}}return-1;}

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> container,Slot slot){
        if(!active(container)||!cfg.huntingBoxValueHighlights||slot==null)return;
        Row row=find(slot.index);if(row==null)return;
        int color=!row.sellPriced()&&!cfg.huntingBoxValueHighlightThroughMissingPrice?cfg.huntingBoxValueMissingColor
            :row.sellTotal()>=Math.max(0,cfg.huntingBoxValueHighlightMillions)*1_000_000.0?cfg.huntingBoxValueHighColor:cfg.huntingBoxValueNormalColor;
        graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,color);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> container,ItemStack stack,List<Component> original){
        if(!active(container)||!cfg.huntingBoxValueTooltips||stack==null||stack.isEmpty())return original;
        String name=clean(stack.getHoverName().getString()).replaceFirst("(?i) Shard$","").trim();
        Row row=state==null?null:state.rows.stream().filter(value->value.name.equalsIgnoreCase(name)).findFirst().orElse(null);
        if(row==null)return original;
        List<Component> out=new ArrayList<>(original);int at=Math.min(1,out.size());
        out.add(at++,Component.literal("§6Instant sell: §f"+coins(row.sellTotal(),row.sellPriced())+" §7("+coins(row.sellUnit,row.sellPriced())+" each)"));
        out.add(at,Component.literal("§6Instant buy: §f"+coins(row.buyTotal(),row.buyPriced())+" §7("+coins(row.buyUnit,row.buyPriced())+" each)"));
        return out;
    }

    private static Row find(int slot){if(state==null)return null;for(Row row:state.rows)if(row.slot==slot)return row;return null;}
    public static State state(){return state;}
    public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){return active(screen)&&state!=null;}
    public static String coins(double value,boolean complete){return ArtemisHuntingProfit.coins(value,complete);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.huntingBoxValue&&ConstellationClient.loc().onHypixel();}
    private static boolean active(AbstractContainerScreen<?> container){return active()&&container!=null&&container==screen&&clean(container.getTitle().getString()).equals("Hunting Box");}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Hunting Box] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("huntingboxvalue").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,100)).executes(c->{cfg.huntingBoxValueRows=IntegerArgumentType.getInteger(c,"amount");save();if(screen!=null)update(screen);return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("threshold").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("millions",IntegerArgumentType.integer(0,1000)).executes(c->{cfg.huntingBoxValueHighlightMillions=IntegerArgumentType.getInteger(c,"millions");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){State value=state;local("Value "+(cfg.huntingBoxValue?"on":"off")+", "+(value==null?0:value.totalShards)+" shards, sell "+coins(value==null?0:value.totalSell,value==null||value.sellComplete)+", buy "+coins(value==null?0:value.totalBuy,value==null||value.buyComplete)+".");return 1;}
    private static int sort(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!List.of("SELL_DESC","BUY_DESC","AMOUNT_DESC","NAME").contains(value)){local("Sort must be sell_desc, buy_desc, amount_desc, or name.");return 0;}cfg.huntingBoxValueSort=value;save();if(screen!=null)update(screen);return status();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.huntingBoxValue=value;case"hud"->cfg.huntingBoxValueHud=value;case"rows"->cfg.huntingBoxValueShowRows=value;case"amount"->cfg.huntingBoxValueShowAmount=value;case"unit"->cfg.huntingBoxValueShowUnit=value;case"sell"->cfg.huntingBoxValueShowSell=value;case"buy"->cfg.huntingBoxValueShowBuy=value;case"totalshards"->cfg.huntingBoxValueShowTotalShards=value;case"totalsell"->cfg.huntingBoxValueShowTotalSell=value;case"totalbuy"->cfg.huntingBoxValueShowTotalBuy=value;case"hidezero"->cfg.huntingBoxValueHideZero=value;case"tooltips"->cfg.huntingBoxValueTooltips=value;case"highlights"->cfg.huntingBoxValueHighlights=value;case"missinghighlight"->cfg.huntingBoxValueHighlightThroughMissingPrice=value;default->{local("Unknown Hunting Box value option.");return 0;}}save();if(screen!=null)update(screen);return status();}
}
