package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.ArtemisConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArtemisFusion {
    public record Shard(String name,String id,int required,int owned,int amount,double value,boolean priced){}
    public record State(Shard first,Shard second,Shard output,boolean ready,double inputCost,double outputValue,
                        boolean complete,long pureReptiles,String lastResult,int lastAmount,long updatedAt){}
    // ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/FusionData.kt
    private static final int FIRST=12,SECOND=14,OUTPUT=31;
    private static final Pattern REQUIRED=Pattern.compile("^Required to fuse: ([\\d,]+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern OWNED=Pattern.compile("^Owned: ([\\d,]+) Shards?$",Pattern.CASE_INSENSITIVE);
    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/FusionDisplay.kt
    private static final Pattern RESULT=Pattern.compile("^FUSION! You obtained(?: an?| a)? (.+?) Shard(?: x([\\d,]+))?!(?: NEW!)?$",Pattern.CASE_INSENSITIVE);
    private static ArtemisConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static State state;
    private static boolean initialized,readyAlerted;
    private static String lastResult="",lastResultId="";
    private static int lastAmount;
    private static long sessionReptiles;

    private ArtemisFusion(){}

    public static void init(ArtemisConfig config){
        cfg=config;normalize();if(initialized)return;initialized=true;
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container)||!fusionTitle(clean(container.getTitle().getString())))return;
            screen=container;update(container);
            ScreenEvents.afterTick(opened).register(ignored->update(container));
            ScreenEvents.remove(opened).register(ignored->{if(screen==container){screen=null;state=null;readyAlerted=false;}});
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(clean(message.getString()));return true;});
    }

    private static void update(AbstractContainerScreen<?> container){
        if(container!=screen||!active())return;
        String title=clean(container.getTitle().getString());
        if(container.getMenu().slots.size()<=OUTPUT){
            state=!cfg.fusionShowLastResult||lastResult.isBlank()?null:lastState();return;
        }
        Shard first=read(container.getMenu().getSlot(FIRST).getItem(),true);
        Shard second=read(container.getMenu().getSlot(SECOND).getItem(),true);
        Shard output=read(container.getMenu().getSlot(OUTPUT).getItem(),false);
        if(first==null||second==null||output==null){
            state=title.equals("Fusion Box")&&cfg.fusionShowLastResult&&!lastResult.isBlank()?lastState():null;readyAlerted=false;return;
        }
        boolean ready=first.owned>=first.required&&second.owned>=second.required;
        double input=first.value+second.value,out=output.value;
        boolean complete=first.priced&&second.priced&&output.priced;
        state=new State(first,second,output,ready,input,out,complete,reptiles(),lastResult,lastAmount,System.currentTimeMillis());
        if(ready)readyAlert();else readyAlerted=false;
    }

    private static Shard read(ItemStack stack,boolean input){
        if(stack==null||stack.isEmpty())return null;
        String name=clean(stack.getHoverName().getString()).replaceFirst("(?i) Shard$","").trim();
        if(name.isBlank())return null;
        int required=input?loreNumber(stack,REQUIRED):-1,owned=loreNumber(stack,OWNED);
        if(input&&required<=0)return null;
        String id=ArtemisHuntingProfit.shardMarketId(name);
        int quantity=input?required:Math.max(1,stack.getCount());
        double price=price(id,input),value=price>0?price*quantity:0;
        if(price<=0)PriceProvider.warm(id);
        return new Shard(name,id,required,owned,quantity,value,price>0);
    }

    private static int loreNumber(ItemStack stack,Pattern pattern){
        ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return 0;
        for(Component component:lore.lines()){Matcher matcher=pattern.matcher(clean(component.getString()));if(matcher.matches())return number(matcher.group(1),0);}
        return 0;
    }

    private static void chat(String line){
        if(!active())return;
        if(line.startsWith("PURE REPTILE")){sessionReptiles++;if(cfg.fusionPersistentReptiles){cfg.fusionPureReptiles.merge(profile(),1L,Long::sum);save();}refreshResult();return;}
        Matcher matcher=RESULT.matcher(line);if(!matcher.matches())return;
        String result=matcher.group(1).trim();int amount=number(matcher.group(2),1);
        String id=ArtemisHuntingProfit.shardMarketId(result);
        if(!id.equals(lastResultId)){sessionReptiles=0;if(!cfg.fusionPersistentReptiles)readyAlerted=false;}
        lastResult=result;lastResultId=id;lastAmount=amount;refreshResult();
    }

    private static void refreshResult(){
        if(screen!=null)update(screen);else if(!lastResult.isBlank())state=lastState();
    }
    private static State lastState(){
        double price=price(lastResultId,false);if(price<=0&&!lastResultId.isBlank())PriceProvider.warm(lastResultId);
        Shard output=new Shard(lastResult,lastResultId,-1,-1,Math.max(1,lastAmount),price>0?price*Math.max(1,lastAmount):0,price>0);
        return new State(null,null,output,false,0,output.value,output.priced,reptiles(),lastResult,lastAmount,System.currentTimeMillis());
    }

    private static void readyAlert(){
        if(!cfg.fusionReadyAlert||readyAlerted)return;readyAlerted=true;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.fusionReadyChat)local("Fusion materials ready.");
        if(cfg.fusionReadyTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Fusion Ready").withColor(cfg.fusionReadyColor&0xFFFFFF));}
        if(cfg.fusionReadySound)mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.8f,1.25f);
    }

    public static State state(){return state;}
    public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){return active()&&state!=null&&screen!=null;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.fusionDisplay&&ConstellationClient.loc().onHypixel();}
    private static boolean fusionTitle(String title){return title.equals("Fusion Box")||title.equals("Shard Fusion")||title.equals("Confirm Fusion");}
    private static double price(String id,boolean input){if(id==null||id.isBlank())return 0;String source=input?cfg.fusionInputPriceSource:cfg.fusionOutputPriceSource;return source.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);}
    private static long reptiles(){return cfg.fusionPersistentReptiles?cfg.fusionPureReptiles.getOrDefault(profile(),0L):sessionReptiles;}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static int number(String raw,int fallback){if(raw==null)return fallback;try{return Integer.parseInt(raw.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static void normalize(){if(cfg.fusionPureReptiles==null)cfg.fusionPureReptiles=new HashMap<>();}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§d[Fusion] §f"+text));}
    public static String coins(double value,boolean complete){String text=Math.abs(value)>=1_000_000?String.format(Locale.ROOT,"%.2fm",value/1_000_000):Math.abs(value)>=1_000?String.format(Locale.ROOT,"%.1fk",value/1_000):String.format(Locale.ROOT,"%.0f",value);return text+(complete?"":" partial");}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fusionhud").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{lastResult=lastResultId="";lastAmount=0;sessionReptiles=0;state=null;readyAlerted=false;local("Fusion session reset.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearreptiles").executes(c->{sessionReptiles=0;cfg.fusionPureReptiles.remove(profile());save();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceSource(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"source"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Fusion HUD "+(cfg.fusionDisplay?"on":"off")+(state==null?".":"; "+(state.ready?"materials ready":"materials missing")+", "+reptiles()+" Pure Reptiles."));return 1;}
    private static int priceSource(String type,String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("PURCHASE")&&!value.equals("SELL")){local("Price source must be purchase or sell.");return 0;}if(type.equalsIgnoreCase("input"))cfg.fusionInputPriceSource=value;else if(type.equalsIgnoreCase("output"))cfg.fusionOutputPriceSource=value;else{local("Price type must be input or output.");return 0;}save();if(screen!=null)update(screen);return status();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.fusionDisplay=value;case"inputs"->cfg.fusionShowInputs=value;case"output"->cfg.fusionShowOutput=value;case"owned"->cfg.fusionShowOwned=value;case"required"->cfg.fusionShowRequired=value;case"cost"->cfg.fusionShowInputCost=value;case"value"->cfg.fusionShowOutputValue=value;case"net"->cfg.fusionShowNetValue=value;case"reptiles"->cfg.fusionShowPureReptiles=value;case"last"->cfg.fusionShowLastResult=value;case"missing"->cfg.fusionWarnMissing=value;case"alert"->cfg.fusionReadyAlert=value;case"chat"->cfg.fusionReadyChat=value;case"title"->cfg.fusionReadyTitle=value;case"sound"->cfg.fusionReadySound=value;case"persistent"->cfg.fusionPersistentReptiles=value;default->{local("Unknown Fusion HUD option.");return 0;}}save();return status();}
}
