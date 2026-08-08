package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/pests/PesthunterProfit.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/garden/pests/PesthunterShopConfig.kt
public final class HerculesPesthunterShop {
    private record Cost(int pests,double materials,boolean complete){}
    private record Offer(int slot,String name,String id,int amount,int pests,double itemPrice,double materials,double profit,double perPest){}

    private static final Pattern PESTS=Pattern.compile("(?i)^([0-9,]+) Pests$");
    private static final Pattern AMOUNT_AFTER=Pattern.compile("(?i)^(.+?)\\s+x\\s*([0-9,]+)$");
    private static final Pattern AMOUNT_BEFORE=Pattern.compile("(?i)^([0-9,]+)x\\s+(.+)$");
    private static final Map<String,String> SPECIAL_IDS=Map.ofEntries(
        Map.entry("tasty cheese","CHEESE_FUEL"),Map.entry("plant matter","PLANT_MATTER"),
        Map.entry("dung","DUNG"),Map.entry("larva","LARVA"),Map.entry("honey jar","HONEY_JAR"),
        Map.entry("book of stats","BOOK_OF_STATS"),Map.entry("jacob's ticket","JACOBS_TICKET"));
    private static HerculesConfig cfg;
    private static List<Offer> offers=List.of();
    private static String fingerprint="";

    private HerculesPesthunterShop(){}

    public static void init(HerculesConfig config){
        cfg=config;
        ConstellationClient.tick().every(100,"hercules-pesthunter-prices",()->{BazaarApi.ensureFresh();for(Offer offer:offers)PriceProvider.warm(offer.id);fingerprint="";});
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||slot==null||!title(screen).equals("Pesthunter's Wares"))return;
        if(slot.index==0){refresh(screen);drawPanel(graphics,screen);}
        if(cfg.pesthunterHighlightBest&&!offers.isEmpty()&&slot.index==offers.getFirst().slot){
            graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.pesthunterBestColor);
            border(graphics,slot,0xFF55FF55);
        }
    }

    private static void refresh(AbstractContainerScreen<?> screen){
        String next=screen.getMenu().slots.stream().map(slot->slot.index+":"+LyraTooltips.marketId(slot.getItem())+":"+slot.getItem().getCount()+":"+lore(slot.getItem())).toList().toString();
        if(next.equals(fingerprint))return;fingerprint=next;
        List<Offer> found=new ArrayList<>();
        for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty()||slot.index==49)continue;
            String name=normalizedName(stack),id=LyraTooltips.marketId(stack);
            if(name.isBlank()||id.isBlank()||name.equals("Close")||name.equals("Pesthunter's Wares"))continue;
            Cost cost=cost(stack);if(cost.pests<=0||!cost.complete)continue;
            int amount=outputAmount(stack),itemCount=Math.max(1,amount);
            double price=PriceProvider.sellValue(id)*itemCount;
            if(price<=0){PriceProvider.warm(id);continue;}
            double profit=price-cost.materials;
            if(cfg.pesthunterOnlyPositive&&profit<=0)continue;
            found.add(new Offer(slot.index,name,id,itemCount,cost.pests,price,cost.materials,profit,profit/cost.pests));
        }
        found.sort(Comparator.comparingDouble(Offer::perPest).reversed().thenComparing(Offer::name));
        int max=Math.clamp(cfg.pesthunterMaxRows,1,20);
        offers=List.copyOf(found.subList(0,Math.min(max,found.size())));
    }

    private static Cost cost(ItemStack stack){
        boolean reading=false,complete=true;int pests=0;double materials=0;
        for(String line:lore(stack)){
            if(line.equalsIgnoreCase("Cost")){reading=true;continue;}
            if(!reading)continue;
            if(line.isBlank()||line.equalsIgnoreCase("Click to trade!"))break;
            Matcher pest=PESTS.matcher(line);
            if(pest.matches()){pests+=(int)number(pest.group(1),0);continue;}
            Parsed parsed=parse(line);if(parsed==null){complete=false;continue;}
            String id=itemId(parsed.name);double price=PriceProvider.purchaseValue(id);
            if(id.isBlank()||price<=0){if(!id.isBlank())PriceProvider.warm(id);complete=false;continue;}
            materials+=price*parsed.amount;
        }
        return new Cost(pests,materials,complete);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen){
        ContainerScreenAccessor accessor=(ContainerScreenAccessor)screen;int screenLeft=accessor.constellation$left(),imageWidth=accessor.constellation$imageWidth();
        int leftSpace=Math.max(0,screenLeft-4),rightSpace=Math.max(0,screen.width-screenLeft-imageWidth-4);boolean useRight=rightSpace>=leftSpace;
        int width=Math.min(190,useRight?rightSpace:leftSpace);if(width<56)return;
        int shown=Math.min(offers.size(),Math.max(1,(screen.height-24)/11)),height=20+Math.max(1,shown)*11;
        int x=useRight?imageWidth+4:-width-4,y=Math.clamp(0,2-accessor.constellation$top(),Math.max(2-accessor.constellation$top(),screen.height-accessor.constellation$top()-height-2));
        graphics.fill(x,y,x+width,y+height,cfg.pesthunterPanelColor);
        var font=Minecraft.getInstance().font;graphics.text(font,ConstellationUi.fit(font,"Profit / Pest",width-12),x+6,y+5,0xFFFFFF55,true);
        if(offers.isEmpty())graphics.text(font,ConstellationUi.fit(font,"Loading prices",width-12),x+6,y+17,0xFFFFAA00,false);
        int line=y+17;
        for(Offer offer:offers.subList(0,shown)){
            graphics.text(font,ConstellationUi.fit(font,offer.name+"  "+coins(offer.perPest),width-12),x+6,line,offer.profit>=0?0xFF55FF55:0xFFFF5555,false);
            line+=11;
        }
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> input){
        if(!active()||!title(screen).equals("Pesthunter's Wares")||stack==null||stack.isEmpty())return input;
        String id=LyraTooltips.marketId(stack),name=normalizedName(stack);
        Offer offer=offers.stream().filter(value->value.id.equals(id)&&value.name.equals(name)).findFirst().orElse(null);
        if(offer==null)return input;
        List<Component> out=new ArrayList<>(input);
        out.add(Component.empty());
        out.add(Component.literal("§ePesthunter Value"));
        if(cfg.pesthunterShowItemPrice)out.add(Component.literal("§7Estimated item value: §6"+coins(offer.itemPrice)+" coins"));
        if(cfg.pesthunterShowMaterialCost)out.add(Component.literal("§7Material cost: §6"+coins(offer.materials)+" coins"));
        if(cfg.pesthunterShowTradeProfit)out.add(Component.literal("§7Profit per trade: "+color(offer.profit)+coins(offer.profit)+" coins"));
        if(cfg.pesthunterShowPestCost)out.add(Component.literal("§7Pests required: §2"+format(offer.pests)));
        if(cfg.pesthunterShowProfitPerPest)out.add(Component.literal("§7Profit per Pest: "+color(offer.perPest)+coins(offer.perPest)+" coins"));
        return out;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pestshop")
            .executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,20))
                    .executes(c->{cfg.pesthunterMaxRows=IntegerArgumentType.getInteger(c,"amount");fingerprint="";save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int option(String name,String raw){
        Boolean value=bool(raw);if(value==null){local("Use on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.pesthunterProfit=value;case"highlight"->cfg.pesthunterHighlightBest=value;
            case"positive"->cfg.pesthunterOnlyPositive=value;case"itemprice"->cfg.pesthunterShowItemPrice=value;
            case"materials"->cfg.pesthunterShowMaterialCost=value;case"profit"->cfg.pesthunterShowTradeProfit=value;
            case"pests"->cfg.pesthunterShowPestCost=value;case"perpest"->cfg.pesthunterShowProfitPerPest=value;
            default->{local("Unknown Pesthunter option.");return 0;}
        }
        fingerprint="";save();return status();
    }
    private static int status(){local("Pesthunter Profit "+on(cfg.pesthunterProfit)+", "+offers.size()+" fully priced offers, "+(offers.isEmpty()?"no best offer":offers.getFirst().name+" at "+coins(offers.getFirst().perPest)+" per Pest")+".");return 1;}

    private record Parsed(String name,int amount){}
    private static Parsed parse(String line){Matcher after=AMOUNT_AFTER.matcher(line);if(after.matches())return new Parsed(after.group(1).trim(),(int)number(after.group(2),0));Matcher before=AMOUNT_BEFORE.matcher(line);if(before.matches())return new Parsed(before.group(2).trim(),(int)number(before.group(1),0));return new Parsed(line.trim(),1);}
    private static String itemId(String name){String lower=name.toLowerCase(Locale.ROOT);String special=SPECIAL_IDS.get(lower);if(special!=null)return special;return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static int outputAmount(ItemStack stack){int amount=Math.max(1,stack.getCount());Matcher matcher=AMOUNT_AFTER.matcher(clean(stack.getHoverName().getString()));return matcher.matches()?Math.max(amount,(int)number(matcher.group(2),1)):amount;}
    private static String normalizedName(ItemStack stack){String name=clean(stack.getHoverName().getString()).replace("[Lvl 100]","[Lvl 1]");Matcher matcher=AMOUNT_AFTER.matcher(name);return matcher.matches()?matcher.group(1).trim():name;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.pesthunterProfit&&ConstellationClient.loc().area()==LocationManager.SkyblockArea.GARDEN;}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String value){String result=ChatFormatting.stripFormatting(value);return result==null?"":result.trim();}
    private static long number(String value,long fallback){try{return Long.parseLong(value.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String format(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String coins(double value){double abs=Math.abs(value);String sign=value<0?"-":"";if(abs>=1e9)return sign+String.format(Locale.ROOT,"%.2fb",abs/1e9);if(abs>=1e6)return sign+String.format(Locale.ROOT,"%.2fm",abs/1e6);if(abs>=1e3)return sign+String.format(Locale.ROOT,"%.1fk",abs/1e3);return sign+String.format(Locale.ROOT,"%.0f",abs);}
    private static String color(double value){return value>=0?"§a":"§c";}
    private static String shortText(String value,int max){return value.length()>max?value.substring(0,max-3)+"...":value;}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Pesthunter] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
    private static void border(GuiGraphicsExtractor graphics,Slot slot,int color){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.fill(slot.x,slot.y,slot.x+1,slot.y+16,color);graphics.fill(slot.x+15,slot.y,slot.x+16,slot.y+16,color);}
}
