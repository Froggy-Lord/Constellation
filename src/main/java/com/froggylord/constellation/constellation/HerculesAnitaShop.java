package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.BazaarApi;
import com.froggylord.constellation.api.PriceProvider;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/AnitaMedalProfit.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/inventory/AnitaExtraFarmingFortune.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/jsonobjects/repo/AnitaUpgradeCostsJson.kt
public final class HerculesAnitaShop {
    private record Cost(int bronze,int tickets,double additional,boolean complete){}
    private record Offer(int slot,String name,String id,int amount,int bronze,int tickets,double sale,double additional,double profit,double perBronze){}
    private record TierCost(int gold,int tickets){}

    private static final Pattern MEDAL=Pattern.compile("(?i)^(Gold|Silver|Bronze) medals?(?:\\s*x)?\\s*([0-9,]+)?$");
    private static final Pattern TICKET=Pattern.compile("(?i)^Jacob'?s Ticket(?:s)?(?:\\s*x)?\\s*([0-9,]+)?$");
    private static final Pattern MATERIAL=Pattern.compile("(?i)^(.+?)(?:\\s*x\\s*([0-9,]+))?$");
    private static final Pattern OUTPUT_AMOUNT=Pattern.compile("(?i)^(.+?)\\s+x([0-9,]+)$");
    private static final Pattern TIER=Pattern.compile("(?i)(?:Current Tier\\s*:\\s*|Tier\\s+)([0-9]{1,2})(?:/15)?|You have\\s*\\+?([0-9]{1,2})\\s+Farming Fortune");
    private static final List<TierCost> TIERS=List.of(
        new TierCost(1,0),new TierCost(1,50),new TierCost(1,50),new TierCost(2,100),new TierCost(2,100),
        new TierCost(3,150),new TierCost(3,150),new TierCost(4,200),new TierCost(4,200),new TierCost(5,250),
        new TierCost(6,300),new TierCost(7,350),new TierCost(8,400),new TierCost(9,450),new TierCost(10,1000));
    private static final Map<String,String> MATERIAL_IDS=Map.of(
        "jacob's ticket","JACOBS_TICKET","jacobs ticket","JACOBS_TICKET");
    private static HerculesConfig cfg;
    private static List<Offer> offers=List.of();
    private static String fingerprint="";

    private HerculesAnitaShop(){}

    public static void init(HerculesConfig config){
        cfg=config;
        if(cfg.anitaFortuneTiers==null)cfg.anitaFortuneTiers=new HashMap<>();
        ConstellationClient.tick().every(100,"hercules-anita-prices",()->{BazaarApi.ensureFresh();for(Offer offer:offers)PriceProvider.warm(offer.id);fingerprint="";});
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||slot==null||!title(screen).equals("Anita"))return;
        if(slot.index==0&&cfg.anitaMedalProfit){
            refresh(screen);
            drawPanel(graphics,screen);
        }
        if(cfg.anitaHighlightBest&&!offers.isEmpty()&&slot.index==offers.getFirst().slot){
            graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.anitaBestColor);
            border(graphics,slot,0xFF55FF55);
        }
    }

    private static void refresh(AbstractContainerScreen<?> screen){
        String next=screen.getMenu().slots.stream().map(s->s.index+":"+id(s.getItem())+":"+s.getItem().getCount()+":"+lore(s.getItem())).toList().toString();
        if(next.equals(fingerprint))return;
        fingerprint=next;
        List<Offer> found=new ArrayList<>();
        for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();
            if(stack.isEmpty())continue;
            String name=clean(stack.getHoverName().getString()),id=id(stack);
            if(name.isBlank()||id.isBlank()||name.equals("Close")||name.equals("Unique Gold Medals")||name.equals("Medal Trades"))continue;
            Cost cost=cost(stack);
            if(cost.bronze<=0||!cost.complete)continue;
            int amount=Math.max(1,stack.getCount());
            Matcher output=OUTPUT_AMOUNT.matcher(name);
            if(output.matches()){name=output.group(1).trim();amount=Math.max(amount,number(output.group(2),1));}
            double sale=PriceProvider.sellValue(id)*amount;
            if(sale<=0){PriceProvider.warm(id);continue;}
            double additional=cost.additional+cost.tickets*PriceProvider.purchaseValue("JACOBS_TICKET");
            if(cost.tickets>0&&PriceProvider.purchaseValue("JACOBS_TICKET")<=0)continue;
            double profit=sale-additional;
            if(cfg.anitaOnlyPositive&&profit<=0)continue;
            found.add(new Offer(slot.index,name,id,amount,cost.bronze,cost.tickets,sale,additional,profit,profit/cost.bronze));
        }
        found.sort(Comparator.comparingDouble(Offer::perBronze).reversed().thenComparing(Offer::name));
        int max=Math.clamp(cfg.anitaMaxRows,1,20);
        offers=List.copyOf(found.subList(0,Math.min(max,found.size())));
    }

    private static Cost cost(ItemStack stack){
        boolean reading=false,complete=true;int bronze=0,tickets=0;double additional=0;
        for(String line:lore(stack)){
            if(line.equalsIgnoreCase("Cost")){reading=true;continue;}
            if(!reading)continue;
            if(line.isBlank()||line.equalsIgnoreCase("Click to trade!"))break;
            Matcher medal=MEDAL.matcher(line);
            if(medal.matches()){
                int amount=number(medal.group(2),1);
                bronze+=amount*switch(medal.group(1).toLowerCase(Locale.ROOT)){case"gold"->8;case"silver"->2;default->1;};
                continue;
            }
            Matcher ticket=TICKET.matcher(line);
            if(ticket.matches()){tickets+=number(ticket.group(1),1);continue;}
            Matcher material=MATERIAL.matcher(line);
            if(!material.matches()){complete=false;continue;}
            String materialId=MATERIAL_IDS.get(material.group(1).toLowerCase(Locale.ROOT));
            if(materialId==null){complete=false;continue;}
            double price=PriceProvider.purchaseValue(materialId);
            if(price<=0){complete=false;continue;}
            additional+=price*number(material.group(2),1);
        }
        return new Cost(bronze,tickets,additional,complete);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen){
        ContainerScreenAccessor accessor=(ContainerScreenAccessor)screen;int screenLeft=accessor.constellation$left(),imageWidth=accessor.constellation$imageWidth();
        int leftSpace=Math.max(0,screenLeft-4),rightSpace=Math.max(0,screen.width-screenLeft-imageWidth-4);boolean useRight=rightSpace>=leftSpace;
        int width=Math.min(196,useRight?rightSpace:leftSpace);if(width<56)return;
        int shown=Math.min(offers.size(),Math.max(1,(screen.height-24)/11)),height=20+Math.max(1,shown)*11;
        int x=useRight?imageWidth+4:-width-4,y=Math.clamp(0,2-accessor.constellation$top(),Math.max(2-accessor.constellation$top(),screen.height-accessor.constellation$top()-height-2));
        graphics.fill(x,y,x+width,y+height,cfg.anitaPanelColor);
        var font=Minecraft.getInstance().font;graphics.text(font,ConstellationUi.fit(font,"Profit per Bronze Medal",width-12),x+6,y+5,0xFFFFFF55,true);
        if(offers.isEmpty())graphics.text(font,ConstellationUi.fit(font,"Waiting for item prices",width-12),x+6,y+17,0xFFFFAA00,false);
        int line=y+17;
        for(Offer offer:offers.subList(0,shown)){
            String text=shortName(offer.name)+"  "+coins(offer.perBronze);
            graphics.text(font,ConstellationUi.fit(font,text,width-12),x+6,line,offer.profit>=0?0xFF55FF55:0xFFFF5555,false);
            line+=11;
        }
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> input){
        if(!active()||!title(screen).equals("Anita")||stack==null||stack.isEmpty())return input;
        List<Component> out=offerTooltip(stack,input);
        if(!cfg.anitaExtraFarmingFortune||!clean(stack.getHoverName().getString()).contains("Extra Farming Fortune"))return out;
        List<String> raw=lore(stack);
        int tier=learnTier(raw);
        if(tier<0)return out;
        int nextTickets=tier<TIERS.size()?displayedTickets(raw):-1;
        double multiplier=1;
        if(tier<TIERS.size()&&TIERS.get(tier).tickets>0&&nextTickets>=0)multiplier=(double)nextTickets/TIERS.get(tier).tickets;
        int gold=0,tickets=0;
        for(int i=tier;i<TIERS.size();i++){gold+=TIERS.get(i).gold;tickets+=TIERS.get(i).tickets;}
        tickets=(int)Math.round(tickets*multiplier);
        if(out==input)out=new ArrayList<>(input);
        out.add(Component.empty());
        if(cfg.anitaFortuneShowCurrentTier)out.add(Component.literal("§7Current Tier: §e"+tier+"/"+TIERS.size()));
        if(cfg.anitaFortuneShowRemaining){
            out.add(Component.literal("§7Cost to max out"));
            out.add(Component.literal("§6Gold Medals: §8x"+format(gold)));
            out.add(Component.literal("§aJacob's Tickets: §8x"+format(tickets)));
        }
        if(cfg.anitaFortuneShowTicketValue){
            double value=tickets*PriceProvider.purchaseValue("JACOBS_TICKET");
            if(value>0)out.add(Component.literal("  §7Ticket value: §6"+coins(value)+" coins"));
        }
        return out;
    }

    private static List<Component> offerTooltip(ItemStack stack,List<Component> input){
        if(!cfg.anitaMedalProfit)return input;
        String stackId=id(stack),name=normalizedName(stack);
        Offer offer=offers.stream().filter(value->value.id.equals(stackId)&&value.name.equals(name)).findFirst().orElse(null);
        if(offer==null)return input;
        List<Component> out=new ArrayList<>(input);
        out.add(Component.empty());
        out.add(Component.literal("§eAnita Medal Value"));
        if(cfg.anitaShowSalePrice)out.add(Component.literal("§7Estimated sale: §6"+coins(offer.sale)+" coins"));
        if(cfg.anitaShowAdditionalCost)out.add(Component.literal("§7Non-medal cost: §6"+coins(offer.additional)+" coins"));
        if(cfg.anitaShowTradeProfit)out.add(Component.literal("§7Profit per trade: "+(offer.profit>=0?"§a":"§c")+coins(offer.profit)+" coins"));
        if(cfg.anitaShowBronzeCost)out.add(Component.literal("§7Bronze-equivalent medals: §c"+format(offer.bronze)));
        out.add(Component.literal("§7Profit per Bronze Medal: "+(offer.perBronze>=0?"§a":"§c")+coins(offer.perBronze)+" coins"));
        return out;
    }

    private static int learnTier(List<String> lines){
        for(String line:lines){
            Matcher matcher=TIER.matcher(line);
            if(!matcher.find())continue;
            boolean fortune=matcher.group(1)==null;
            int tier=number(fortune?matcher.group(2):matcher.group(1),-1);
            if(fortune&&tier>TIERS.size()&&tier%4==0)tier/=4;
            if(tier<0||tier>TIERS.size())continue;
            String key=profile();
            if(!Objects.equals(cfg.anitaFortuneTiers.put(key,tier),tier))ConstellationClient.saveConfig();
            return tier;
        }
        return cfg.anitaFortuneTiers.getOrDefault(profile(),-1);
    }

    private static int displayedTickets(List<String> lines){for(String line:lines){Matcher m=TICKET.matcher(line);if(m.matches())return number(m.group(1),-1);}return-1;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("anitahelper")
            .executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tier")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("level",IntegerArgumentType.integer(0,15))
                    .executes(c->{cfg.anitaFortuneTiers.put(profile(),IntegerArgumentType.getInteger(c,"level"));save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .executes(c->{cfg.anitaFortuneTiers.remove(profile());save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,20))
                    .executes(c->{cfg.anitaMaxRows=IntegerArgumentType.getInteger(c,"amount");fingerprint="";save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int option(String name,String raw){
        Boolean value=bool(raw);if(value==null){local("Use on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.anitaHelper=value;case"profit"->cfg.anitaMedalProfit=value;case"fortune"->cfg.anitaExtraFarmingFortune=value;
            case"highlight"->cfg.anitaHighlightBest=value;case"positive"->cfg.anitaOnlyPositive=value;
            case"sale"->cfg.anitaShowSalePrice=value;case"materials"->cfg.anitaShowAdditionalCost=value;
            case"tradeprofit"->cfg.anitaShowTradeProfit=value;case"bronze"->cfg.anitaShowBronzeCost=value;
            case"tier"->cfg.anitaFortuneShowCurrentTier=value;case"remaining"->cfg.anitaFortuneShowRemaining=value;
            case"ticketvalue"->cfg.anitaFortuneShowTicketValue=value;default->{local("Unknown Anita option.");return 0;}
        }
        fingerprint="";save();return status();
    }
    private static int status(){local("Anita Helper "+on(cfg.anitaHelper)+", medal profit "+on(cfg.anitaMedalProfit)+", Fortune tier "+cfg.anitaFortuneTiers.getOrDefault(profile(),-1)+"/15.");return 1;}

    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.anitaHelper;}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(c->clean(c.getString())).toList();}
    private static String id(ItemStack stack){if(stack==null||stack.isEmpty())return"";CustomData data=stack.get(DataComponents.CUSTOM_DATA);CompoundTag root=data==null?new CompoundTag():data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static String normalizedName(ItemStack stack){String name=clean(stack.getHoverName().getString());Matcher matcher=OUTPUT_AMOUNT.matcher(name);return matcher.matches()?matcher.group(1).trim():name;}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static String clean(String value){String result=ChatFormatting.stripFormatting(value);return result==null?"":result.trim();}
    private static int number(String value,int fallback){if(value==null||value.isBlank())return fallback;try{return Integer.parseInt(value.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String format(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String coins(double value){double abs=Math.abs(value);String sign=value<0?"-":"";if(abs>=1e9)return sign+String.format(Locale.ROOT,"%.2fb",abs/1e9);if(abs>=1e6)return sign+String.format(Locale.ROOT,"%.2fm",abs/1e6);if(abs>=1e3)return sign+String.format(Locale.ROOT,"%.1fk",abs/1e3);return sign+String.format(Locale.ROOT,"%.0f",abs);}
    private static String shortName(String value){return value.length()>21?value.substring(0,20)+"...":value;}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Anita] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
    private static void border(GuiGraphicsExtractor graphics,Slot slot,int color){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.fill(slot.x,slot.y,slot.x+1,slot.y+16,color);graphics.fill(slot.x+15,slot.y,slot.x+16,slot.y+16,color);}
}
