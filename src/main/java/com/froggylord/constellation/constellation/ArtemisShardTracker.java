package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

// ported from NoFrills (GPL-3.0-only): features/hunting/ShardTracker.java
// ported from SkyHanni (LGPL-3.0-or-later): features/hunting/ShardTrackerDisplay.kt
public final class ArtemisShardTracker {
    public record Row(String key,String name,String source,long needed,long obtained,double remainingValue,boolean priced){
        public boolean complete(){return needed>0&&obtained>=needed;}
    }
    public record State(List<Row> rows,long needed,long obtained,boolean complete){}

    // ported from NoFrills (GPL-3.0-only): features/hunting/ShardTracker.java
    private static final Pattern CAUGHT=Pattern.compile("^You caught(?: (?:a|an))?(?: x([\\d,]+))? (.+?) Shards?!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOTSHARE=Pattern.compile("^LOOT SHARE You received (?:an?|([\\d,]+)) (.+?) Shards? for assisting .+!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARM=Pattern.compile("^(?:CHARM|NAGA|SALT) You charmed (?:a|an) (.+?) and captured(?: ([\\d,]+) Shards from it| its Shard)\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern FUSION=Pattern.compile("^FUSION! You obtained(?: an?| a)? (.+?) Shard(?: x([\\d,]+))?!(?: NEW!)?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ABSORBED=Pattern.compile("^You sent ([\\d,]+) (.+?) Shards? to your Hunting Box\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern OWNED=Pattern.compile("^Owned: ([\\d,]+) Shards?$",Pattern.CASE_INSENSITIVE);
    private static ArtemisConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static KeyMapping selectKey;
    private static State state;
    private static boolean initialized;
    private static long orderCounter;

    private ArtemisShardTracker(){}

    public static void init(ArtemisConfig config){
        cfg=config;normalize();if(initialized)return;initialized=true;
        selectKey=ConstellationClient.instance().keys().register("track_hovered_shard",InputConstants.UNKNOWN.getValue());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(clean(message.getString()));return true;});
        ScreenEvents.AFTER_INIT.register((client,opened,width,height)->{
            if(!(opened instanceof AbstractContainerScreen<?> container)||!shardMenu(clean(container.getTitle().getString())))return;
            screen=container;sync(container);
            ScreenEvents.afterTick(opened).register(ignored->sync(container));
            ScreenKeyboardEvents.allowKeyPress(opened).register((ignored,event)->select(container,event));
            ScreenEvents.remove(opened).register(ignored->{if(screen==container)screen=null;refresh();});
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(active())refresh();});
        refresh();
    }

    private static boolean select(AbstractContainerScreen<?> container,KeyEvent event){
        if(!active()||selectKey==null||!selectKey.matches(event))return true;
        Slot slot=((ContainerScreenAccessor)container).constellation$hoveredSlot();
        if(slot==null||slot.getItem().isEmpty())return false;
        String name=shardName(slot.getItem());if(name.isBlank()){local("The hovered item is not an attribute shard.");return false;}
        String key=findKey(name);if(key!=null)remove(key);else add(name,0,"Direct",0);
        return false;
    }

    private static void sync(AbstractContainerScreen<?> container){
        if(!active()||container!=screen)return;
        String title=clean(container.getTitle().getString());
        if(title.equals("Hunting Box")&&cfg.shardTrackerSyncBox){
            boolean changed=false;
            for(Slot slot:container.getMenu().slots){
                ItemStack stack=slot.getItem();if(stack.isEmpty())continue;
                int owned=loreNumber(stack,OWNED);if(owned<0)continue;
                String name=shardName(stack),key=findKey(name);if(key==null)continue;
                long current=cfg.shardTrackerObtained.getOrDefault(key,0L);
                if(current!=owned){cfg.shardTrackerObtained.put(key,(long)owned);changed=true;}
            }
            if(changed)save();
        }
        refresh();
    }

    private static void chat(String line){
        if(!active())return;
        Matcher m=CAUGHT.matcher(line);
        if(m.matches()){gain(m.group(2),number(m.group(1),1));return;}
        m=LOOTSHARE.matcher(line);
        if(m.matches()){gain(m.group(2),number(m.group(1),1));return;}
        m=CHARM.matcher(line);
        if(m.matches()){gain(m.group(1),number(m.group(2),1));return;}
        m=FUSION.matcher(line);
        if(m.matches()){gain(m.group(1),number(m.group(2),1));return;}
        m=ABSORBED.matcher(line);
        if(m.matches())gain(m.group(2),number(m.group(1),1));
    }

    private static void gain(String raw,int amount){
        if(amount<=0)return;String name=cleanName(raw),key=findKey(name);if(key==null)return;
        long before=cfg.shardTrackerObtained.getOrDefault(key,0L),after=before+amount,needed=cfg.shardTrackerNeeded.getOrDefault(key,0L);
        cfg.shardTrackerObtained.put(key,after);save();refresh();
        if(needed>0&&before<needed&&after>=needed)complete(cfg.shardTrackerNames.getOrDefault(key,name),after,needed);
        if(cfg.shardTrackerRemoveCompleted&&needed>0&&after>=needed)remove(key);
    }

    private static void complete(String name,long obtained,long needed){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        if(cfg.shardTrackerCompletionChat)local(name+" complete: "+obtained+"/"+needed+".");
        if(cfg.shardTrackerCompletionTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal("Shard Complete").withColor(cfg.shardTrackerCompleteColor&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal(name));}
        if(cfg.shardTrackerCompletionSound)mc.player.playSound(SoundEvents.PLAYER_LEVELUP,.8f,1.2f);
    }

    private static void refresh(){
        if(cfg==null){state=null;return;}
        String prefix=profile()+"|";List<Row> rows=new ArrayList<>();long neededTotal=0,obtainedTotal=0;
        boolean inFusion=screen!=null&&fusionTitle(clean(screen.getTitle().getString()));
        for(var entry:cfg.shardTrackerNeeded.entrySet()){
            if(!entry.getKey().startsWith(prefix))continue;
            String key=entry.getKey(),source=cfg.shardTrackerSources.getOrDefault(key,"Direct");
            if(cfg.shardTrackerFilterFusionOutside&&!inFusion&&(source.equalsIgnoreCase("Fuse")||source.equalsIgnoreCase("Cycle")))continue;
            if(cfg.shardTrackerFilterDirectInside&&inFusion&&(source.equalsIgnoreCase("Direct")||source.equalsIgnoreCase("Bazaar")))continue;
            long needed=Math.max(0,entry.getValue()),obtained=Math.max(0,cfg.shardTrackerObtained.getOrDefault(key,0L));
            String name=cfg.shardTrackerNames.getOrDefault(key,display(key.substring(prefix.length())));
            String id=ArtemisHuntingProfit.shardMarketId(name);double price=price(id);
            if(price<=0)PriceProvider.warm(id);
            rows.add(new Row(key,name,source,needed,obtained,price*Math.max(0,needed-obtained),price>0));
            if(needed>0){neededTotal+=needed;obtainedTotal+=Math.min(needed,obtained);}
        }
        Comparator<Row> comparator=switch(cfg.shardTrackerSort.toUpperCase(Locale.ROOT)){
            case"NAME"->Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);
            case"REMAINING"->Comparator.comparingLong((Row r)->Math.max(0,r.needed-r.obtained)).reversed();
            case"PROGRESS"->Comparator.comparingDouble(ArtemisShardTracker::progress).reversed();
            case"VALUE"->Comparator.comparingDouble(Row::remainingValue).reversed();
            default->Comparator.comparingLong(r->cfg.shardTrackerOrder.getOrDefault(r.key,Long.MAX_VALUE));
        };
        rows.sort(comparator);int limit=Math.clamp(cfg.shardTrackerRows,1,100);if(rows.size()>limit)rows=new ArrayList<>(rows.subList(0,limit));
        state=new State(List.copyOf(rows),neededTotal,obtainedTotal,neededTotal>0&&obtainedTotal>=neededTotal);
    }

    private static double progress(Row row){return row.needed<=0?0:Math.min(1,(double)row.obtained/row.needed);}
    private static double price(String id){return cfg.shardTrackerPriceSource.equalsIgnoreCase("SELL")?PriceProvider.sellValue(id):PriceProvider.purchaseValue(id);}
    private static int loreNumber(ItemStack stack,Pattern pattern){
        ItemLore lore=stack.get(DataComponents.LORE);if(lore==null)return-1;
        for(Component line:lore.lines()){Matcher m=pattern.matcher(clean(line.getString()));if(m.matches())return number(m.group(1),-1);}return-1;
    }
    private static String shardName(ItemStack stack){
        if(stack==null||stack.isEmpty())return"";
        String raw=clean(stack.getHoverName().getString()),name=cleanName(raw);
        ItemLore lore=stack.get(DataComponents.LORE);boolean shard=raw.toLowerCase(Locale.ROOT).endsWith(" shard");
        if(lore!=null)for(Component line:lore.lines()){String text=clean(line.getString());if(OWNED.matcher(text).matches()||text.startsWith("Source: ")&&text.contains(" Shard")){shard=true;break;}}
        return shard?name.replaceFirst("(?i) Shard$","").trim():"";
    }
    private static String findKey(String name){
        String normalized=normalizeName(name),prefix=profile()+"|";String fallback=null;
        for(var entry:cfg.shardTrackerNames.entrySet())if(entry.getKey().startsWith(prefix)&&normalizeName(entry.getValue()).equals(normalized)){
            if(cfg.shardTrackerNeeded.getOrDefault(entry.getKey(),0L)>cfg.shardTrackerObtained.getOrDefault(entry.getKey(),0L))return entry.getKey();
            fallback=entry.getKey();
        }
        return fallback;
    }
    private static String add(String raw,long needed,String source,long obtained){
        String name=cleanName(raw);if(name.isBlank())return null;
        String cleanSource=validSource(source);
        String key=profile()+"|"+ArtemisHuntingProfit.shardMarketId(name)+"|"+cleanSource.toUpperCase(Locale.ROOT);
        cfg.shardTrackerNames.put(key,name);cfg.shardTrackerNeeded.merge(key,Math.max(0,needed),Long::sum);
        cfg.shardTrackerObtained.putIfAbsent(key,Math.max(0,obtained));cfg.shardTrackerSources.put(key,cleanSource);
        cfg.shardTrackerOrder.putIfAbsent(key,++orderCounter);save();refresh();return key;
    }
    private static void remove(String key){
        cfg.shardTrackerNeeded.remove(key);cfg.shardTrackerObtained.remove(key);cfg.shardTrackerNames.remove(key);
        cfg.shardTrackerSources.remove(key);cfg.shardTrackerOrder.remove(key);save();refresh();
    }

    // ported from NoFrills (GPL-3.0-only): features/hunting/ShardTracker.java
    // compatible with SkyHanni (LGPL-3.0-or-later): features/hunting/ShardTrackerDisplay.kt
    private static int importClipboard(){
        try{
            String clipboard=Minecraft.getInstance().keyboardHandler.getClipboard();if(clipboard==null||clipboard.length()>1_500_000)throw new IllegalArgumentException();
            int colon=clipboard.indexOf(':');if(colon<0)throw new IllegalArgumentException();
            String prefix=clipboard.substring(0,colon);
            if(!prefix.startsWith("<NoFrillsRecipe>(V")&&!prefix.startsWith("<SkyHanniRecipe>(V"))throw new IllegalArgumentException();
            byte[] packed=Base64.getDecoder().decode(clipboard.substring(colon+1).trim());if(packed.length>1_000_000)throw new IllegalArgumentException();
            byte[] json;try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(packed))){json=gzip.readNBytes(4_000_001);}
            if(json.length>4_000_000)throw new IllegalArgumentException();
            JsonElement root=JsonParser.parseString(new String(json,StandardCharsets.UTF_8));if(!root.isJsonArray())throw new IllegalArgumentException();
            JsonArray array=root.getAsJsonArray();List<ImportRow> parsed=new ArrayList<>();if(array.size()>1000)throw new IllegalArgumentException();
            for(JsonElement element:array){
                if(!element.isJsonObject())continue;JsonObject object=element.getAsJsonObject();
                if(!object.has("name")||!object.has("needed"))continue;
                String name=cleanName(object.get("name").getAsString());long needed=object.get("needed").getAsLong();
                String source=object.has("source")&&!object.get("source").isJsonNull()?object.get("source").getAsString():"Direct";
                if(!name.isBlank()&&needed>=0&&needed<=1_000_000_000L)parsed.add(new ImportRow(name,needed,source));
            }
            if(parsed.isEmpty())throw new IllegalArgumentException();
            clearProfile(false);for(ImportRow row:parsed)add(row.name,row.needed,row.source,0);
            local("Imported "+parsed.size()+" shard goals from the clipboard.");return 1;
        }catch(Exception ignored){local("Clipboard does not contain a valid SkyShards recipe.");return 0;}
    }
    private record ImportRow(String name,long needed,String source){}

    public static State state(){return state;}
    public static ArtemisConfig config(){return cfg;}
    public static boolean visible(){return active()&&state!=null&&(!state.rows.isEmpty()||!cfg.shardTrackerHideEmpty);}
    public static String coins(double value,boolean complete){return ArtemisHuntingProfit.coins(value,complete);}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.shardTracker&&ConstellationClient.loc().onHypixel();}
    private static boolean shardMenu(String title){return title.equals("Hunting Box")||title.equals("Attribute Menu")||fusionTitle(title);}
    private static boolean fusionTitle(String title){return title.equals("Fusion Box")||title.equals("Shard Fusion")||title.equals("Confirm Fusion");}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static String normalizeName(String raw){return cleanName(raw).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private static String cleanName(String raw){return clean(raw).replaceFirst("(?i) Shards?$","").trim();}
    private static String display(String id){String text=id.replaceFirst("^SHARD_","").toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder out=new StringBuilder();for(String part:text.split(" "))if(!part.isBlank())out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');return out.toString().trim();}
    private static String validSource(String raw){for(String value:List.of("Direct","Fuse","Cycle","Bazaar"))if(value.equalsIgnoreCase(raw))return value;return"Direct";}
    private static int number(String raw,int fallback){if(raw==null)return fallback;try{return Integer.parseInt(raw.replace(",",""));}catch(Exception ignored){return fallback;}}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static void normalize(){
        if(cfg.shardTrackerNeeded==null)cfg.shardTrackerNeeded=new HashMap<>();if(cfg.shardTrackerObtained==null)cfg.shardTrackerObtained=new HashMap<>();
        if(cfg.shardTrackerNames==null)cfg.shardTrackerNames=new HashMap<>();if(cfg.shardTrackerSources==null)cfg.shardTrackerSources=new HashMap<>();
        if(cfg.shardTrackerOrder==null)cfg.shardTrackerOrder=new HashMap<>();
        orderCounter=cfg.shardTrackerOrder.values().stream().mapToLong(Long::longValue).max().orElse(0);
    }
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§d[Shards] §f"+text));}
    private static int status(){refresh();local("Tracker "+(cfg.shardTracker?"on":"off")+", "+(state==null?0:state.rows.size())+" shown, "+(state==null?0:state.obtained)+"/"+(state==null?0:state.needed)+" obtained.");return 1;}
    private static int clearProfile(boolean report){String prefix=profile()+"|";cfg.shardTrackerNeeded.keySet().removeIf(k->k.startsWith(prefix));cfg.shardTrackerObtained.keySet().removeIf(k->k.startsWith(prefix));cfg.shardTrackerNames.keySet().removeIf(k->k.startsWith(prefix));cfg.shardTrackerSources.keySet().removeIf(k->k.startsWith(prefix));cfg.shardTrackerOrder.keySet().removeIf(k->k.startsWith(prefix));save();refresh();if(report)local("Active profile shard goals cleared.");return 1;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("shardtracker").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("import").executes(c->importClipboard()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->clearProfile(true)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("needed",IntegerArgumentType.integer(0,1_000_000_000))
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->{String name=StringArgumentType.getString(c,"name");add(name,IntegerArgumentType.getInteger(c,"needed"),"Direct",0);local("Tracking "+cleanName(name)+".");return 1;}))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->{String key=findKey(StringArgumentType.getString(c,"name"));if(key==null){local("That shard is not tracked.");return 0;}remove(key);return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("obtained")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(0,1_000_000_000))
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.greedyString()).executes(c->{String key=findKey(StringArgumentType.getString(c,"name"));if(key==null){local("That shard is not tracked.");return 0;}cfg.shardTrackerObtained.put(key,(long)IntegerArgumentType.getInteger(c,"amount"));save();return status();}))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("rows").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,100)).executes(c->{cfg.shardTrackerRows=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sort").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("mode",StringArgumentType.word()).executes(c->sort(StringArgumentType.getString(c,"mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceSource(StringArgumentType.getString(c,"source")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int sort(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!List.of("IMPORT","NAME","REMAINING","PROGRESS","VALUE").contains(value)){local("Sort must be import, name, remaining, progress, or value.");return 0;}cfg.shardTrackerSort=value;save();return status();}
    private static int priceSource(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("PURCHASE")&&!value.equals("SELL")){local("Price source must be purchase or sell.");return 0;}cfg.shardTrackerPriceSource=value;save();return status();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.shardTracker=value;case"hud"->cfg.shardTrackerHud=value;case"hideempty"->cfg.shardTrackerHideEmpty=value;case"sync"->cfg.shardTrackerSyncBox=value;case"chat"->cfg.shardTrackerCompletionChat=value;case"title"->cfg.shardTrackerCompletionTitle=value;case"sound"->cfg.shardTrackerCompletionSound=value;case"filterfusion"->cfg.shardTrackerFilterFusionOutside=value;case"filterdirect"->cfg.shardTrackerFilterDirectInside=value;case"source"->cfg.shardTrackerShowSource=value;case"remaining"->cfg.shardTrackerShowRemaining=value;case"value"->cfg.shardTrackerShowValue=value;case"total"->cfg.shardTrackerShowTotal=value;case"removecomplete"->cfg.shardTrackerRemoveCompleted=value;default->{local("Unknown shard-tracker option.");return 0;}}save();return status();}
}
