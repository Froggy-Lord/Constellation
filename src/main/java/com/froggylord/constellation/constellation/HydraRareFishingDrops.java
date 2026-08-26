package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HydraConfig;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): constants/RareDrops.kt
// ported from Feesh (Apache-2.0): events/publishers/RareDropsPublisher.kt
// ported from Feesh (Apache-2.0): features/alerts/RareDropAlert.kt
// ported from Feesh (Apache-2.0): features/chat/RareDropMessage.kt
public final class HydraRareFishingDrops {
    private record Drop(String key,String id,String priceId,String name,int color,int npc,boolean extreme,List<String> aliases) {}
    private static final Pattern BOOK=Pattern.compile("^(?:RARE|VERY RARE|CRAZY RARE|INSANE) DROP! Enchanted Book \\((.+?)\\)(?: \\([+]([\\d,]+)%? . Magic Find\\))?[!.]?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern RARE=Pattern.compile("^(?:RARE|VERY RARE|CRAZY RARE|INSANE) DROP! \\(?(.+?)\\)?(?: \\([+]([\\d,]+)%? . Magic Find\\))?[!.]?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PET=Pattern.compile("^PET DROP! (?:(LEGENDARY|EPIC|RARE|UNCOMMON|COMMON) )?(.+?)(?: \\(.+)?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PET_CATCH=Pattern.compile("^(?:\\S+ )?(?:GOOD|GREAT|OUTSTANDING) CATCH! You caught a \\[Lvl 1] (?:(LEGENDARY|EPIC|RARE|UNCOMMON|COMMON) )?(.+?)!$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY=Pattern.compile("^Party > (?:\\[[^]]+] )?([^:]+): --> (?:A|An) (.+?) has dropped(?: \\([^)]*\\))? <--$",Pattern.CASE_INSENSITIVE);
    private static final Pattern DYE=Pattern.compile("^WOW! (.+?) found (?:a|an) (.+? Dye)(?: #[0-9]+)?!.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PHOENIX=Pattern.compile("^Wow! (.+?) found a Phoenix pet!.*$",Pattern.CASE_INSENSITIVE);
    private static final List<Drop> DROPS=List.of(
        d("LUCKY_CLOVER_CORE","PET_ITEM_LUCKY_CLOVER_DROP","PET_ITEM_LUCKY_CLOVER_DROP","Lucky Clover Core",0xFFAA00AA,50000,false),
        d("DEEP_SEA_ORB","DEEP_SEA_ORB","DEEP_SEA_ORB","Deep Sea Orb",0xFFAA00AA,1,false),
        d("RADIOACTIVE_VIAL","RADIOACTIVE_VIAL","RADIOACTIVE_VIAL","Radioactive Vial",0xFFFF55FF,5000000,true),
        d("MAGMA_CORE","MAGMA_CORE","MAGMA_CORE","Magma Core",0xFF5555FF,200000,false),
        d("TIKI_MASK","TIKI_MASK","TIKI_MASK","Tiki Mask",0xFFFFAA00,1000000,true),
        d("TITANOBOA_SHED","TITANOBOA_SHED","TITANOBOA_SHED","Titanoboa Shed",0xFFFFAA00,500000,true),
        d("SNAKE_EYES","SNAKE_EYES","SNAKE_EYES","Snake Eyes",0xFFFFAA00,1000000,true),
        d("OCTOPUS_TENDRIL","OCTOPUS_TENDRIL","OCTOPUS_TENDRIL","Octopus Tendril",0xFFFFAA00,1000000,false),
        d("TROUBLED_BUBBLE","TROUBLED_BUBBLE","TROUBLED_BUBBLE","Troubled Bubble",0xFFFFAA00,1000000,false),
        d("SCUTTLER_SHELL","SCUTTLER_SHELL","SCUTTLER_SHELL","Scuttler Shell",0xFFFF55FF,1000000,false),
        d("BURNT_TEXTS","BURNT_TEXTS","BURNT_TEXTS","Burnt Texts",0xFFFFAA00,1000000,false),
        da("FLASH_1","ENCHANTMENT_ULTIMATE_FLASH_1","ENCHANTMENT_ULTIMATE_FLASH_1","Flash 1",0xFFFF55FF,0,false,"Flash I"),
        da("MAGMARIZER_6","ENCHANTMENT_MAGMARIZER_6","ENCHANTMENT_MAGMARIZER_6","Pyroclasm 6",0xFF5555FF,0,false,"Pyroclasm VI"),
        d("VIBRANT_CORAL","VIBRANT_CORAL","VIBRANT_CORAL","Vibrant Coral",0xFFFFAA00,1000000,false),
        d("TRUE_ICE","HILT_OF_TRUE_ICE","HILT_OF_TRUE_ICE","True Ice",0xFFFFAA00,0,false),
        d("WATER_HYACINTH","WATER_HYACINTH","WATER_HYACINTH","Water Hyacinth",0xFFFFAA00,1000000,false),
        d("DISTANT_ECHO","DISTANT_ECHO","DISTANT_ECHO","Distant Echo",0xFFFFAA00,1000000,false),
        d("REINFORCED_NETTING","REINFORCED_NETTING","REINFORCED_NETTING","Reinforced Netting",0xFFFFAA00,1000000,false),
        d("PRINCES_CROWN_JEWEL","PRINCE_CROWN_JEWEL","PRINCE_CROWN_JEWEL","Prince's Crown Jewel",0xFFFFAA00,1000000,true),
        d("FLYING_FISH_LEGENDARY","FLYING_FISH;4","FLYING_FISH_PET_LEGENDARY","Flying Fish (Legendary)",0xFFFFAA00,250000,false),
        d("MEGALODON_LEGENDARY","MEGALODON;4","MEGALODON_PET_LEGENDARY","Megalodon (Legendary)",0xFFFFAA00,2500000,false),
        d("MEGALODON_EPIC","MEGALODON;3","MEGALODON_PET_EPIC","Megalodon (Epic)",0xFFAA00AA,500000,false),
        d("SQUID_LEGENDARY","SQUID;4","SQUID_PET_LEGENDARY","Squid (Legendary)",0xFFFFAA00,500000,false),
        d("SQUID_EPIC","SQUID;3","SQUID_PET_EPIC","Squid (Epic)",0xFFAA00AA,200000,false),
        d("SQUID_RARE","SQUID;2","SQUID_PET_RARE","Squid (Rare)",0xFF5555FF,100000,false),
        d("SQUID_UNCOMMON","SQUID;1","SQUID_PET_UNCOMMON","Squid (Uncommon)",0xFF55FF55,500,false),
        d("SQUID_COMMON","SQUID;0","SQUID_PET_COMMON","Squid (Common)",0xFFFFFFFF,100,false),
        d("PHOENIX","PHOENIX;?","PHOENIX_PET_LEGENDARY","Phoenix",0xFFFF5555,0,true),
        d("CARMINE_DYE","DYE_CARMINE","DYE_CARMINE","Carmine Dye",0xFFAA0000,0,true),
        d("MIDNIGHT_DYE","DYE_MIDNIGHT","DYE_MIDNIGHT","Midnight Dye",0xFFAA00AA,0,true),
        d("AQUAMARINE_DYE","DYE_AQUAMARINE","DYE_AQUAMARINE","Aquamarine Dye",0xFF55FFFF,0,true),
        d("ICEBERG_DYE","DYE_ICEBERG","DYE_ICEBERG","Iceberg Dye",0xFF00AAAA,0,true),
        d("TREASURE_DYE","DYE_TREASURE","DYE_TREASURE","Treasure Dye",0xFFFFAA00,0,true),
        d("PERIWINKLE_DYE","DYE_PERIWINKLE","DYE_PERIWINKLE","Periwinkle Dye",0xFF00AAAA,0,true),
        d("BONE_DYE","DYE_BONE","DYE_BONE","Bone Dye",0xFFFFFFFF,0,true));
    private static final Map<String,Long> LAST=new HashMap<>();
    private static HydraConfig cfg;
    private static boolean initialized;

    private HydraRareFishingDrops() {}
    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(message);return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->LAST.clear());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->LAST.clear());}

    private static void onChat(Component component){if(!active())return;String text=clean(component.getString());if(text.isBlank())return;Matcher party=PARTY.matcher(text);if(party.matches()){if(!cfg.fishingRareDropParty)return;String sender=party.group(1).trim();Minecraft mc=Minecraft.getInstance();if(mc.player!=null&&sender.equalsIgnoreCase(mc.player.getName().getString()))return;Drop drop=find(party.group(2));if(drop!=null)show(drop,sender,false,null);return;}
        Minecraft mc=Minecraft.getInstance();String me=mc.player==null?"":mc.player.getName().getString();Matcher dye=DYE.matcher(text);if(dye.matches()){if(containsPlayer(dye.group(1),me)){Drop drop=find(dye.group(2));if(drop!=null)show(drop,me,true,null);}return;}Matcher phoenix=PHOENIX.matcher(text);if(phoenix.matches()){if(containsPlayer(phoenix.group(1),me)){Drop drop=find("Phoenix");if(drop!=null)show(drop,me,true,null);}return;}
        if(!cfg.fishingRareDropOwn)return;Matcher book=BOOK.matcher(text);if(book.matches()){Drop drop=find(book.group(1));if(drop!=null)show(drop,me,true,number(book.group(2)));return;}Matcher rare=RARE.matcher(text);if(rare.matches()){Drop drop=find(rare.group(1));if(drop!=null)show(drop,me,true,number(rare.group(2)));return;}Matcher pet=PET.matcher(text);if(pet.matches()){showPet(component,pet.group(1),pet.group(2),me);return;}Matcher catchPet=PET_CATCH.matcher(text);if(catchPet.matches())showPet(component,catchPet.group(1),catchPet.group(2),me);}

    private static void showPet(Component component,String explicit,String petName,String player){String rarity=explicit;if(rarity==null||rarity.isBlank())rarity=rarity(component,petName);if(rarity==null)return;Drop drop=find(petName+" ("+title(rarity)+")");if(drop!=null)show(drop,player,true,null);}
    private static String rarity(Component component,String name){final String[] out={null};component.visit((style,text)->{if(out[0]!=null||!clean(text).toLowerCase(Locale.ROOT).contains(clean(name).toLowerCase(Locale.ROOT))||style.getColor()==null)return java.util.Optional.empty();out[0]=switch(style.getColor().getValue()){case 0xFFAA00->"LEGENDARY";case 0xAA00AA->"EPIC";case 0x5555FF->"RARE";case 0x55FF55->"UNCOMMON";case 0xFFFFFF->"COMMON";default->null;};return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);return out[0];}

    private static void show(Drop drop,String player,boolean own,Integer magicFind){if(!selected(drop))return;long now=System.currentTimeMillis();String dedupe=own?"own:"+drop.key:"party:"+clean(player).toLowerCase(Locale.ROOT)+":"+drop.key;if(now-LAST.getOrDefault(dedupe,0L)<Math.clamp(cfg.fishingRareDropDedupeSeconds,1,30)*1000L)return;LAST.put(dedupe,now);double price=price(drop);Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;boolean showPrice=own?cfg.fishingRareDropShowPriceOwn:cfg.fishingRareDropShowPriceParty;String priceText=showPrice&&price>0?" (+"+HydraFishingProfitTracker.coins(price,true)+")":"";
        int color=cfg.fishingRareDropUseRarityColor?drop.color:cfg.fishingRareDropColor;
        if(cfg.fishingRareDropTitle){Component title=Component.literal(drop.name+priceText).withColor(color&0xFFFFFF);if(drop.extreme&&cfg.fishingRareDropExtremeDecoration)title=Component.literal("x").withStyle(ChatFormatting.GOLD,ChatFormatting.OBFUSCATED).append(Component.literal(" ")).append(title).append(Component.literal(" x").withStyle(ChatFormatting.GOLD,ChatFormatting.OBFUSCATED));mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(title);if(cfg.fishingRareDropSubtitlePlayer&&!player.isBlank())mc.gui.hud.setSubtitle(Component.literal(player));}
        if(cfg.fishingRareDropSound)mc.player.playSound(drop.extreme?SoundEvents.UI_TOAST_CHALLENGE_COMPLETE:SoundEvents.PLAYER_LEVELUP,.8f,1f);
        if(cfg.fishingRareDropLocalChat)local((own?"You":""+player)+" found "+drop.name+priceText+".");
        if(own){int count=increment(drop);if(cfg.fishingRareDropPartyMessage&&mc.player.connection!=null)mc.player.connection.sendCommand("pc "+message(drop,count,magicFind));HydraFishingProfitTracker.markRareDropAlert(drop.priceId);}
    }

    private static int increment(Drop drop){if(cfg.fishingRareDropCounts==null)cfg.fishingRareDropCounts=new HashMap<>();int count=cfg.fishingRareDropCounts.getOrDefault(drop.key,0)+1;cfg.fishingRareDropCounts.put(drop.key,count);ConstellationClient.saveConfig();return count;}
    private static String message(Drop drop,int count,Integer magicFind){String metadata="";var parts=new java.util.ArrayList<String>();if(cfg.fishingRareDropIncludeNumber)parts.add("#"+count);if(cfg.fishingRareDropIncludeMagicFind&&magicFind!=null)parts.add("+"+magicFind+" ✯ Magic Find");if(!parts.isEmpty())metadata=" ("+String.join(", ",parts)+")";String template=cfg.fishingRareDropPartyTemplate==null?"--> {article} {item} has dropped{metadata} <--":cfg.fishingRareDropPartyTemplate;String value=template.replace("{article}",article(drop.name)).replace("{item}",drop.name).replace("{metadata}",metadata).replace("{number}",Integer.toString(count)).replace("{magic-find}",magicFind==null?"":Integer.toString(magicFind));return value.length()>200?value.substring(0,200):value;}
    private static boolean selected(Drop drop){String selected=cfg.fishingRareDropTypes==null?"ALL":cfg.fishingRareDropTypes.toUpperCase(Locale.ROOT);if(selected.equals("ALL")||selected.contains("ALL,"))return true;for(String part:selected.split("[,;]"))if(part.trim().replace(' ','_').equals(drop.key))return true;return false;}
    private static double price(Drop drop){double value=switch(cfg.fishingRareDropPriceSource.toUpperCase(Locale.ROOT)){case"BUY","PURCHASE"->PriceProvider.purchaseValue(drop.priceId);case"NPC"->drop.npc;default->PriceProvider.sellValue(drop.priceId);};if(value<=0&& !cfg.fishingRareDropPriceSource.equalsIgnoreCase("NPC"))PriceProvider.warm(drop.priceId);return Math.max(0,value);}
    private static Drop find(String name){String clean=clean(name);for(Drop drop:DROPS){if(drop.name.equalsIgnoreCase(clean))return drop;for(String alias:drop.aliases)if(alias.equalsIgnoreCase(clean))return drop;}return null;}
    private static boolean containsPlayer(String text,String player){return !player.isBlank()&&clean(text).matches("(?i).*(?:^|[^A-Za-z0-9_])"+Pattern.quote(player)+"(?:$|[^A-Za-z0-9_]).*");}
    private static Integer number(String value){if(value==null)return null;try{return Integer.parseInt(value.replace(",",""));}catch(NumberFormatException ignored){return null;}}
    private static String article(String name){return name.matches("(?i)^[aeiou].*")?"An":"A";}
    private static String title(String value){String lower=value.toLowerCase(Locale.ROOT);return Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
    private static Drop d(String key,String id,String priceId,String name,int color,int npc,boolean extreme){return new Drop(key,id,priceId,name,color,npc,extreme,List.of());}
    private static Drop da(String key,String id,String priceId,String name,int color,int npc,boolean extreme,String...aliases){return new Drop(key,id,priceId,name,color,npc,extreme,List.of(aliases));}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.fishingRareDropSuite&&cfg.fishingRareDropAlerts&&ConstellationClient.loc().onHypixel();}
    private static String clean(String text){String out=ChatFormatting.stripFormatting(text);return out==null?"":out.trim();}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fishingdrops").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(c->listTypes())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{if(cfg.fishingRareDropCounts==null)cfg.fishingRareDropCounts=new HashMap<>();else cfg.fishingRareDropCounts.clear();save();local("Rare-drop counts reset.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dedupe").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds",IntegerArgumentType.integer(1,30)).executes(c->{cfg.fishingRareDropDedupeSeconds=IntegerArgumentType.getInteger(c,"seconds");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("argb",IntegerArgumentType.integer()).executes(c->{cfg.fishingRareDropColor=IntegerArgumentType.getInteger(c,"argb");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("price").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("source",StringArgumentType.word()).executes(c->priceSource(StringArgumentType.getString(c,"source"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("types").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("list",StringArgumentType.greedyString()).executes(c->types(StringArgumentType.getString(c,"list"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("template").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("text",StringArgumentType.greedyString()).executes(c->{cfg.fishingRareDropPartyTemplate=StringArgumentType.getString(c,"text");save();return status();}))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){int count=cfg.fishingRareDropCounts==null?0:cfg.fishingRareDropCounts.values().stream().mapToInt(Integer::intValue).sum();local("Alerts "+on(cfg.fishingRareDropAlerts)+", own "+on(cfg.fishingRareDropOwn)+", party "+on(cfg.fishingRareDropParty)+", sharing "+on(cfg.fishingRareDropPartyMessage)+", "+count+" drops counted.");return 1;}
    private static int priceSource(String source){String value=source.toUpperCase(Locale.ROOT);if(!List.of("SELL","BUY","PURCHASE","NPC").contains(value)){local("Price source must be sell, buy, or npc.");return 0;}cfg.fishingRareDropPriceSource=value;save();return status();}
    private static int types(String input){var keys=new java.util.LinkedHashSet<String>();for(String token:input.toUpperCase(Locale.ROOT).split("[,;]")){String key=token.trim().replace(' ','_');if(key.isEmpty())continue;if(key.equals("ALL")){keys.clear();keys.add("ALL");break;}if(DROPS.stream().noneMatch(drop->drop.key.equals(key))){local("Unknown drop type: "+key+". Use /fishingdrops list.");return 0;}keys.add(key);}if(keys.isEmpty()){local("Choose at least one drop type or ALL.");return 0;}cfg.fishingRareDropTypes=String.join(",",keys);save();return status();}
    private static int listTypes(){String all=String.join(", ",DROPS.stream().map(Drop::key).toList());int split=all.length()/2;int comma=all.indexOf(", ",split);if(comma<0)comma=all.length();local("Types: "+all.substring(0,comma));if(comma<all.length())local(all.substring(comma+2));return 1;}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.fishingRareDropAlerts=value;case"own"->cfg.fishingRareDropOwn=value;case"party"->cfg.fishingRareDropParty=value;case"title"->cfg.fishingRareDropTitle=value;case"subtitle"->cfg.fishingRareDropSubtitlePlayer=value;case"ownprice"->cfg.fishingRareDropShowPriceOwn=value;case"partyprice"->cfg.fishingRareDropShowPriceParty=value;case"sound"->cfg.fishingRareDropSound=value;case"chat"->cfg.fishingRareDropLocalChat=value;case"share"->cfg.fishingRareDropPartyMessage=value;case"number"->cfg.fishingRareDropIncludeNumber=value;case"magicfind"->cfg.fishingRareDropIncludeMagicFind=value;case"extreme"->cfg.fishingRareDropExtremeDecoration=value;case"raritycolor"->cfg.fishingRareDropUseRarityColor=value;default->{local("Unknown option. Use enabled, own, party, title, subtitle, ownprice, partyprice, sound, chat, share, number, magicfind, extreme, or raritycolor.");return 0;}}save();return status();}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Fishing Drops] §f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
