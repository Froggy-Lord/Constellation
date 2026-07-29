package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/AgathaCouponProfit.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/CompactStarlynSisters.kt
public final class ArtemisStarlyn {
    public record Cost(String id,String name,int amount,double unit,double total,boolean priced){}
    public record Row(int slot,String id,String name,int coupons,double sell,double cost,double profit,double perCoupon,boolean complete,List<Cost> costs){}
    private static final Pattern BEFORE=Pattern.compile("^(?:([\\d,.]+[kKmM]?)x? )?(.+)$");
    private static final Pattern AFTER=Pattern.compile("^(.+?)(?: x([\\d,]+))?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern RESULT_START=Pattern.compile("^\\[NPC] (.+?): You reached the (\\w+) Bracket in my contest!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern POINTS=Pattern.compile("^\\[NPC] (.+?): You earned a total of ([\\d,]+) points!(?: That's a new PERSONAL BEST!)?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PREVIOUS=Pattern.compile("^\\[NPC] (.+?): Your previous Personal Best was ([\\d,]+)\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CLAIM=Pattern.compile("^\\[NPC] (.+?): Come see me at (.+?) to claim your rewards!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLECTION=Pattern.compile("^PERSONAL BEST: You increased your (\\w+) Collection by ([\\d,]+) during the contest! That's ([\\d,]+) more than your previous best!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SWEEP=Pattern.compile("^Your total .*Sweep is now increased by ([\\d.]+)%!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SISTER_PB=Pattern.compile("^\\[NPC] (.+?): PERSONAL BEST! You've surpassed your previous record of ([\\d,]+) (.+?) logs collected in my Contest!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern KEEP_UP=Pattern.compile("^\\[NPC] (.+?): Keep it up!$",Pattern.CASE_INSENSITIVE);
    private static ArtemisConfig cfg;
    private static boolean initialized,inResults,inCollection;
    private static String signature="",sister="",bracket="",collectionWood="";
    private static long points,previous,collectionAmount,collectionDifference;
    private static List<Row> rows=List.of();

    private ArtemisStarlyn(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(5,"artemis-starlyn",ArtemisStarlyn::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay||chat(message));
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetChat());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetChat());
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!shop(screen)){signature="";rows=List.of();return;}
        String next=screen.getMenu().slots.stream().limit(54).map(slot->slot.index+":"+slot.getItem().getHoverName().getString()+":"+lore(slot.getItem())).reduce("",String::concat);
        if(next.equals(signature))return;signature=next;parse(screen);
    }

    private static void parse(AbstractContainerScreen<?> screen){
        List<Row> parsed=new ArrayList<>();
        for(Slot slot:screen.getMenu().slots){
            if(slot.index<9||slot.index>44||slot.index%9==0||slot.index%9==8)continue;
            ItemStack stack=slot.getItem();if(stack.isEmpty())continue;
            List<String> lore=lore(stack);int costAt=lore.indexOf("Cost");if(costAt<0)continue;
            List<Cost> costs=new ArrayList<>();int coupons=0;double total=0;boolean complete=true;
            for(int i=costAt+1;i<lore.size()&&!lore.get(i).isBlank();i++){
                Pair pair=pair(lore.get(i));if(pair==null)continue;String id=pair.name.equalsIgnoreCase("Agatha Coupon")?"AGATHA_COUPON":normalize(pair.name);
                double unit=id.equals("AGATHA_COUPON")&&cfg.starlynCouponManualPrice>0?cfg.starlynCouponManualPrice:inputPrice(id);
                if(unit<=0){PriceProvider.warm(id);complete=false;}double value=unit*pair.amount;total+=value;
                if(id.equals("AGATHA_COUPON"))coupons+=pair.amount;
                costs.add(new Cost(id,pair.name,pair.amount,unit,value,unit>0));
            }
            if(coupons<=0)continue;String id=itemId(stack);if(id.isBlank())id=normalize(clean(stack.getHoverName().getString()));
            double sell=outputPrice(id);if(sell<=0){PriceProvider.warm(id);complete=false;}
            double profit=sell-total,per=profit/coupons;
            if((!cfg.starlynCouponShowNegative&&per<0)||(cfg.starlynCouponHideUnpriced&&!complete))continue;
            parsed.add(new Row(slot.index,id,clean(stack.getHoverName().getString()),coupons,sell,total,profit,per,complete,List.copyOf(costs)));
        }
        Comparator<Row> order=switch(cfg.starlynCouponSort.toUpperCase(Locale.ROOT)){case"PROFIT_ASC"->Comparator.comparingDouble(Row::perCoupon);case"TOTAL_DESC"->Comparator.comparingDouble(Row::profit).reversed();case"TOTAL_ASC"->Comparator.comparingDouble(Row::profit);case"COST_ASC"->Comparator.comparingDouble(Row::cost);case"COST_DESC"->Comparator.comparingDouble(Row::cost).reversed();case"NAME"->Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);default->Comparator.comparingDouble(Row::perCoupon).reversed();};
        parsed.sort(order);if(parsed.size()>Math.clamp(cfg.starlynCouponRows,1,50))parsed=new ArrayList<>(parsed.subList(0,Math.clamp(cfg.starlynCouponRows,1,50)));rows=List.copyOf(parsed);
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(slot==null||!shop(screen))return;Row row=bySlot(slot.index);if(row==null)return;
        int color=!row.complete?cfg.starlynCouponUnknownColor:row.perCoupon>=0?cfg.starlynCouponPositiveColor:cfg.starlynCouponNegativeColor;
        if(cfg.starlynCouponHighlightSlots)graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,color);
        if(cfg.starlynCouponSlotLabels)graphics.text(Minecraft.getInstance().font,shortCoins(row.perCoupon),slot.x+1,slot.y+1,0xFFFFFFFF,true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> current){
        if(!cfg.starlynCouponTooltips||!shop(screen)||stack==null)return current;
        Row row=rows.stream().filter(value->value.id.equals(itemId(stack))||value.name.equals(clean(stack.getHoverName().getString()))).findFirst().orElse(null);if(row==null)return current;
        List<Component> out=new ArrayList<>(current);out.add(Component.literal(""));out.add(Component.literal("§eCoupon Profit"));
        out.add(Component.literal("§7Sell value: §6"+coins(row.sell,row.complete)));
        out.add(Component.literal("§7Input cost: §6"+coins(row.cost,row.complete)));
        for(Cost cost:row.costs)out.add(Component.literal("§8- "+cost.amount+"x "+cost.name+": "+coins(cost.total,cost.priced)));
        out.add(Component.literal("§7Profit per sale: §6"+coins(row.profit,row.complete)));
        out.add(Component.literal("§7Profit per coupon: "+(row.perCoupon>=0?"§a":"§c")+coins(row.perCoupon,row.complete)));
        return out;
    }

    public static List<Row> rows(){return rows;}public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){Minecraft mc=Minecraft.getInstance();return cfg!=null&&cfg.enabled&&cfg.starlynCouponProfit&&cfg.starlynCouponProfitHud&&mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&shop(screen)&&!rows.isEmpty();}

    private static boolean chat(Component component){
        if(cfg==null||!cfg.enabled||ConstellationClient.loc().area()!=SkyblockArea.GALATEA)return true;
        String line=clean(component.getString());Matcher match;
        if(cfg.starlynCompactResults){
            match=RESULT_START.matcher(line);if(match.matches()){inResults=true;sister=match.group(1);bracket=match.group(2);points=previous=0;return false;}
            if(inResults&&(match=POINTS.matcher(line)).matches()){points=number(match.group(2));return false;}
            if(inResults&&(match=PREVIOUS.matcher(line)).matches()){previous=number(match.group(2));return false;}
            if(inResults&&(match=CLAIM.matcher(line)).matches()){sendResult(match.group(2));inResults=false;return false;}
        }
        if(cfg.starlynCompactPersonalBest){
            match=SISTER_PB.matcher(line);if(match.matches()){sendAction(match.group(1)+"'s Contest: new personal best over "+match.group(2)+" "+match.group(3)+" logs.");return false;}
            if(KEEP_UP.matcher(line).matches())return false;
            match=COLLECTION.matcher(line);if(match.matches()){inCollection=true;collectionWood=match.group(1);collectionAmount=number(match.group(2));collectionDifference=number(match.group(3));return false;}
            if(inCollection&&(match=SWEEP.matcher(line)).matches()){sendAction(collectionWood+" PB: "+format(collectionAmount)+" logs, +"+format(collectionDifference)+" over the previous best, and +"+match.group(1)+"% Sweep.");inCollection=false;return false;}
        }
        return true;
    }
    private static void sendResult(String location){String text=sister+"'s Contest: "+format(points)+" points in the "+bracket+" bracket"+(previous>0?"; previous best "+format(previous):"")+".";sendAction(text+" Claim rewards at "+location+".");}
    private static void sendAction(String text){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;Component message=Component.literal("§2[Starlyn] §f"+text+" ").append(Component.literal("§a[Open]").withStyle(style->style.withClickEvent(new ClickEvent.RunCommand("/starlynsisterlevels")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Open Starlyn Sisters")))));mc.player.sendSystemMessage(message);}
    private static void resetChat(){inResults=inCollection=false;sister=bracket=collectionWood="";points=previous=collectionAmount=collectionDifference=0;}

    private record Pair(String name,int amount){}
    private static Pair pair(String raw){String line=clean(raw);Matcher before=BEFORE.matcher(line);if(before.matches()&&before.group(1)!=null)return new Pair(before.group(2).trim(),amount(before.group(1)));Matcher after=AFTER.matcher(line);if(after.matches())return new Pair(after.group(1).trim(),after.group(2)==null?1:amount(after.group(2)));return null;}
    private static int amount(String raw){String clean=raw.replace(",","").toLowerCase(Locale.ROOT);double multiplier=clean.endsWith("m")?1_000_000:clean.endsWith("k")?1_000:1;if(multiplier>1)clean=clean.substring(0,clean.length()-1);try{return Math.max(1,(int)Math.round(Double.parseDouble(clean)*multiplier));}catch(Exception ignored){return 1;}}
    private static double inputPrice(String id){return cfg.starlynCouponInputPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);}
    private static double outputPrice(String id){return cfg.starlynCouponOutputPriceSource.equalsIgnoreCase("PURCHASE")?PriceProvider.purchaseValue(id):PriceProvider.sellValue(id);}
    private static Row bySlot(int slot){return rows.stream().filter(row->row.slot==slot).findFirst().orElse(null);}
    private static boolean shop(AbstractContainerScreen<?> screen){return cfg!=null&&cfg.enabled&&cfg.starlynCouponProfit&&ConstellationClient.loc().onHypixel()&&clean(screen.getTitle().getString()).equals("Agatha's Shop");}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String itemId(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static String normalize(String raw){return clean(raw).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static long number(String raw){try{return Long.parseLong(raw.replace(",",""));}catch(Exception ignored){return 0;}}
    private static String format(long value){return java.text.NumberFormat.getIntegerInstance(Locale.US).format(value);}
    public static String coins(double value,boolean complete){String out=Math.abs(value)>=1_000_000?String.format(Locale.ROOT,"%.2fm",value/1_000_000):Math.abs(value)>=1_000?String.format(Locale.ROOT,"%.1fk",value/1_000):String.format(Locale.ROOT,"%.0f",value);return out+(complete?"":" partial");}
    private static String shortCoins(double value){return Math.abs(value)>=1_000_000?String.format(Locale.ROOT,"%.1fm",value/1_000_000):Math.abs(value)>=1_000?String.format(Locale.ROOT,"%.0fk",value/1_000):String.format(Locale.ROOT,"%.0f",value);}
    private static void save(){ConstellationClient.saveConfig();}private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Starlyn] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("starlynhelper").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,50)).executes(c->{cfg.starlynCouponRows=IntegerArgumentType.getInteger(c,"amount");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("couponprice").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("coins",IntegerArgumentType.integer(0,1_000_000_000)).executes(c->{cfg.starlynCouponManualPrice=IntegerArgumentType.getInteger(c,"coins");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("side",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->price(StringArgumentType.getString(c,"side"),StringArgumentType.getString(c,"source")))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Coupon profit "+on(cfg.starlynCouponProfit)+", "+rows.size()+" offers read, compact results "+on(cfg.starlynCompactResults)+", compact personal bests "+on(cfg.starlynCompactPersonalBest)+".");return 1;}
    private static int price(String side,String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("PURCHASE")&&!value.equals("SELL")){local("Price source must be purchase or sell.");return 0;}if(side.equalsIgnoreCase("input"))cfg.starlynCouponInputPriceSource=value;else if(side.equalsIgnoreCase("output"))cfg.starlynCouponOutputPriceSource=value;else{local("Price side must be input or output.");return 0;}save();return status();}
    private static int sort(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!List.of("PROFIT_DESC","PROFIT_ASC","TOTAL_DESC","TOTAL_ASC","COST_DESC","COST_ASC","NAME").contains(value)){local("Unknown sort mode.");return 0;}cfg.starlynCouponSort=value;save();return status();}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled","profit"->cfg.starlynCouponProfit=value;case"hud"->cfg.starlynCouponProfitHud=value;case"highlight"->cfg.starlynCouponHighlightSlots=value;case"labels"->cfg.starlynCouponSlotLabels=value;case"tooltips"->cfg.starlynCouponTooltips=value;case"item"->cfg.starlynCouponShowItem=value;case"sell"->cfg.starlynCouponShowSell=value;case"cost"->cfg.starlynCouponShowCost=value;case"totalprofit"->cfg.starlynCouponShowProfit=value;case"coupons"->cfg.starlynCouponShowCouponCount=value;case"negative"->cfg.starlynCouponShowNegative=value;case"hideunpriced"->cfg.starlynCouponHideUnpriced=value;case"results"->cfg.starlynCompactResults=value;case"personalbest","pb"->cfg.starlynCompactPersonalBest=value;default->{local("Unknown Starlyn option.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}private static String on(boolean value){return value?"on":"off";}
}
