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

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/inventory/plots/GardenNextPlotPrice.kt
public final class HerculesPlotPrices {
    private record Material(String name,String id,int amount,double price){}
    private record Plot(int slot,String name,List<Material> materials,double total,boolean affordable){}
    private record Parsed(String name,int amount){}

    private static final Pattern AFTER=Pattern.compile("(?i)^(.+?)\\s+x\\s*([0-9,]+)$");
    private static final Pattern BEFORE=Pattern.compile("(?i)^([0-9,]+)x\\s+(.+)$");
    private static final Map<String,String> IDS=Map.ofEntries(
        Map.entry("compost","COMPOST"),Map.entry("bundle of compost","COMPOST_BUNDLE"),
        Map.entry("jacob's ticket","JACOBS_TICKET"),Map.entry("garden experience","GARDEN_EXP"));
    private static HerculesConfig cfg;
    private static List<Plot> plots=List.of();
    private static String fingerprint="";

    private HerculesPlotPrices(){}

    public static void init(HerculesConfig config){
        cfg=config;
        ConstellationClient.tick().every(100,"hercules-plot-prices",()->{BazaarApi.ensureFresh();for(Plot plot:plots)for(Material material:plot.materials)PriceProvider.warm(material.id);fingerprint="";});
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(!active()||slot==null||!title(screen).equals("Configure Plots"))return;
        if(slot.index==0){refresh(screen);if(cfg.plotPricePanel)drawPanel(graphics,screen);}
        Plot plot=plots.stream().filter(value->value.slot==slot.index).findFirst().orElse(null);
        if(plot==null)return;
        int color=plot.affordable&&cfg.plotPriceHighlightAffordable?cfg.plotPriceAffordableColor
            :plots.getFirst().slot==plot.slot&&cfg.plotPriceHighlightCheapest?cfg.plotPriceCheapestColor:0;
        if(color!=0){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,color);border(graphics,slot,color|0xFF000000);}
    }

    private static void refresh(AbstractContainerScreen<?> screen){
        String next=screen.getMenu().slots.stream().map(slot->slot.index+":"+name(slot.getItem())+":"+lore(slot.getItem())).toList().toString();
        if(next.equals(fingerprint))return;fingerprint=next;
        List<Plot> found=new ArrayList<>();
        for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();String name=name(stack);
            if(stack.isEmpty()||!name.toLowerCase(Locale.ROOT).startsWith("plot"))continue;
            List<Material> materials=materials(stack);if(materials.isEmpty()||materials.stream().anyMatch(value->value.price<=0))continue;
            double total=materials.stream().mapToDouble(value->value.price).sum();
            found.add(new Plot(slot.index,name,List.copyOf(materials),total,materials.stream().allMatch(HerculesPlotPrices::owned)));
        }
        found.sort(Comparator.comparingDouble(Plot::total).thenComparing(Plot::name));
        plots=List.copyOf(found);
    }

    private static List<Material> materials(ItemStack stack){
        List<Material> out=new ArrayList<>();boolean cost=false;
        for(String line:lore(stack)){
            String value=line;
            if(value.toLowerCase(Locale.ROOT).startsWith("cost")){
                cost=true;int colon=value.indexOf(':');if(colon>=0&&colon+1<value.length())value=value.substring(colon+1).trim();else continue;
            }else if(!cost)continue;
            if(value.isBlank()||value.equalsIgnoreCase("Click to purchase!")||value.equalsIgnoreCase("Click to unlock!"))break;
            Parsed parsed=parse(value);if(parsed==null)continue;
            String id=itemId(parsed.name);double unit=PriceProvider.purchaseValue(id);
            if(unit<=0)PriceProvider.warm(id);
            out.add(new Material(parsed.name,id,parsed.amount,unit*parsed.amount));
        }
        return out;
    }

    private static void drawPanel(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen){
        ContainerScreenAccessor accessor=(ContainerScreenAccessor)screen;int screenLeft=accessor.constellation$left(),imageWidth=accessor.constellation$imageWidth();
        int leftSpace=Math.max(0,screenLeft-4),rightSpace=Math.max(0,screen.width-screenLeft-imageWidth-4);boolean useRight=rightSpace>=leftSpace;
        int width=Math.min(184,useRight?rightSpace:leftSpace);if(width<56)return;
        int capacity=Math.max(1,(screen.height-24)/11),extra=cfg.plotPriceShowVisibleTotal?1:0;
        int count=Math.min(Math.min(Math.clamp(cfg.plotPriceMaxRows,1,20),plots.size()),Math.max(0,capacity-extra));
        int height=20+Math.max(1,count+extra)*11,x=useRight?imageWidth+4:-width-4;
        int y=Math.clamp(0,2-accessor.constellation$top(),Math.max(2-accessor.constellation$top(),screen.height-accessor.constellation$top()-height-2));
        graphics.fill(x,y,x+width,y+height,cfg.plotPricePanelColor);
        var font=Minecraft.getInstance().font;graphics.text(font,ConstellationUi.fit(font,"Locked Plot Prices",width-12),x+6,y+5,0xFFFFFF55,true);
        int line=y+17;
        if(plots.isEmpty()){graphics.text(font,ConstellationUi.fit(font,"Waiting for complete prices",width-12),x+6,line,0xFFFFAA00,false);return;}
        for(Plot plot:plots.subList(0,count)){
            int color=plot.affordable?0xFF55FF55:0xFFFFFFFF;
            graphics.text(font,ConstellationUi.fit(font,plot.name+"  "+coins(plot.total),width-12),x+6,line,color,false);line+=11;
        }
        if(cfg.plotPriceShowVisibleTotal)graphics.text(font,ConstellationUi.fit(font,"Visible total: "+coins(plots.stream().mapToDouble(Plot::total).sum()),width-12),x+6,line,0xFFFFAA00,false);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> input){
        if(!active()||!title(screen).equals("Configure Plots")||stack==null||stack.isEmpty()||!name(stack).toLowerCase(Locale.ROOT).startsWith("plot"))return input;
        Plot plot=plots.stream().filter(value->value.name.equals(name(stack))).findFirst().orElse(null);if(plot==null)return input;
        List<Component> out=new ArrayList<>(input);
        if(cfg.plotPriceInlineMaterials){
            boolean cost=false;
            for(int i=0;i<out.size();i++){
                String line=clean(out.get(i).getString());
                if(line.toLowerCase(Locale.ROOT).startsWith("cost")){cost=true;continue;}
                if(!cost||line.isBlank())continue;
                Parsed parsed=parse(line);if(parsed==null)continue;
                Material material=plot.materials.stream().filter(value->value.name.equalsIgnoreCase(parsed.name)&&value.amount==parsed.amount).findFirst().orElse(null);
                if(material!=null)out.set(i,out.get(i).copy().append(Component.literal(" §7(§6"+coins(material.price)+"§7)")));
            }
        }
        out.add(Component.empty());
        if(cfg.plotPriceShowTotal)out.add(Component.literal("§7Total plot cost: §6"+coins(plot.total)+" coins"));
        if(cfg.plotPriceShowAffordable)out.add(Component.literal(plot.affordable?"§aYou have the required materials.":"§eMore materials are required."));
        if(cfg.plotPriceShowOwned)for(Material material:plot.materials)out.add(Component.literal("§7"+material.name+": §f"+format(ownedCount(material.id))+"§7/§f"+format(material.amount)));
        return out;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("plotprices")
            .executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,20))
                    .executes(c->{cfg.plotPriceMaxRows=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word())
                        .executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }

    private static int option(String name,String raw){
        Boolean value=bool(raw);if(value==null){local("Use on or off.");return 0;}
        switch(name.toLowerCase(Locale.ROOT)){
            case"enabled"->cfg.plotPriceHelper=value;case"inline"->cfg.plotPriceInlineMaterials=value;
            case"total"->cfg.plotPriceShowTotal=value;case"panel"->cfg.plotPricePanel=value;
            case"visibletotal"->cfg.plotPriceShowVisibleTotal=value;case"owned"->cfg.plotPriceShowOwned=value;
            case"affordable"->cfg.plotPriceShowAffordable=value;case"cheapest"->cfg.plotPriceHighlightCheapest=value;
            case"affordablehighlight"->cfg.plotPriceHighlightAffordable=value;default->{local("Unknown plot-price option.");return 0;}
        }
        fingerprint="";save();return status();
    }
    private static int status(){local("Plot Prices "+on(cfg.plotPriceHelper)+", "+plots.size()+" fully priced locked plots"+(plots.isEmpty()?".":", cheapest "+plots.getFirst().name+" at "+coins(plots.getFirst().total)+"."));return 1;}

    private static Parsed parse(String value){Matcher after=AFTER.matcher(value);if(after.matches())return new Parsed(after.group(1).trim(),(int)number(after.group(2),0));Matcher before=BEFORE.matcher(value);if(before.matches())return new Parsed(before.group(2).trim(),(int)number(before.group(1),0));return null;}
    private static String itemId(String name){String special=IDS.get(name.toLowerCase(Locale.ROOT));return special!=null?special:name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");}
    private static boolean owned(Material material){return ownedCount(material.id)>=material.amount;}
    private static int ownedCount(String id){Minecraft mc=Minecraft.getInstance();int count=0;if(mc.player!=null)for(ItemStack stack:mc.player.getInventory())if(LyraTooltips.marketId(stack).equals(id))count+=stack.getCount();Integer sacks=HerculesVisitorHelper.observedSackCount(id);return count+Math.max(0,sacks==null?0:sacks);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.plotPriceHelper&&ConstellationClient.loc().area()==LocationManager.SkyblockArea.GARDEN;}
    private static String title(AbstractContainerScreen<?> screen){return clean(screen.getTitle().getString());}
    private static String name(ItemStack stack){return stack==null||stack.isEmpty()?"":clean(stack.getHoverName().getString());}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return List.of();return lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String value){String result=ChatFormatting.stripFormatting(value);return result==null?"":result.trim();}
    private static long number(String value,long fallback){try{return Long.parseLong(value.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String format(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static String coins(double value){if(value>=1e9)return String.format(Locale.ROOT,"%.2fb",value/1e9);if(value>=1e6)return String.format(Locale.ROOT,"%.2fm",value/1e6);if(value>=1e3)return String.format(Locale.ROOT,"%.1fk",value/1e3);return String.format(Locale.ROOT,"%.0f",value);}
    private static String shortText(String value,int max){return value.length()>max?value.substring(0,max-3)+"...":value;}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Plot Prices] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
    private static void border(GuiGraphicsExtractor graphics,Slot slot,int color){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+1,color);graphics.fill(slot.x,slot.y+15,slot.x+16,slot.y+16,color);graphics.fill(slot.x,slot.y,slot.x+1,slot.y+16,color);graphics.fill(slot.x+15,slot.y,slot.x+16,slot.y+16,color);}
}
