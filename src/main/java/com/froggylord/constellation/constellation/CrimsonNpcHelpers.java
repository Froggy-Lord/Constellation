package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/nether/SirihHelper.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/nether/AvoriusHelper.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/nether/PabloHelper.kt
public final class CrimsonNpcHelpers {
    private enum Faction { BARBARIAN, MAGE, UNKNOWN }
    private static final Pattern SIRIH=Pattern.compile("^\\[NPC] Sirih:(?: ✆)? Oink[.?]$",Pattern.CASE_INSENSITIVE);
    private static final Pattern AVORIUS=Pattern.compile("^\\[NPC] Avorius: (?:I am quite thirsty all the time, it's a rare condition\\.|There is no sunlight either, it would be quite accommodating for a Vampire\\.|Why are you looking at me that way\\? I am not a Vampire, you are!)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PABLO=Pattern.compile("^\\[NPC] Pablo: (?:✆ )?(?:Are you available\\? I desperately need an? |Bring me that |Could you bring me an? |I really need an? )(?<flower>[\\w ]+?)(?: today\\.| as soon as you can!|\\?| today, do you have one you could spare\\?)$",Pattern.CASE_INSENSITIVE);
    private static DracoConfig cfg;private static long sirihAt,avoriusAt,pabloAt;
    private CrimsonNpcHelpers(){}

    public static void init(DracoConfig config){cfg=config;ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});}
    private static void onChat(String line){
        if(!active())return;long now=System.currentTimeMillis();Faction faction=faction();
        if(cfg.crimsonSirihHelper&&(!cfg.crimsonNpcRequireFaction||faction==Faction.BARBARIAN)&&now-sirihAt>=Math.clamp(cfg.crimsonSirihCooldownSeconds,5,600)*1000L&&SIRIH.matcher(line).matches()){if(offer("Sirih","Sulphur","SULPHUR_ORE","sulphur ore",false))sirihAt=now;return;}
        if(cfg.crimsonAvoriusHelper&&(!cfg.crimsonNpcRequireFaction||faction==Faction.MAGE)&&now-avoriusAt>=Math.clamp(cfg.crimsonAvoriusCooldownSeconds,5,600)*1000L&&AVORIUS.matcher(line).matches()){if(offer("Avorius","Cup of Blood","CUP_OF_BLOOD","cup of blood",false))avoriusAt=now;return;}
        Matcher pablo=PABLO.matcher(line);if(cfg.crimsonPabloHelper&&now-pabloAt>=Math.clamp(cfg.crimsonPabloCooldownSeconds,5,900)*1000L&&pablo.matches()){String flower=pablo.group("flower").trim();if(offer("Pablo",flower,flower.toUpperCase(Locale.ROOT).replace(' ','_'),flower.toLowerCase(Locale.ROOT),false))pabloAt=now;}
    }
    private static boolean offer(String npc,String item,String id,String query,boolean preview){if(!preview&&cfg.crimsonNpcCheckInventory&&has(id,item))return false;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;String action=cfg.crimsonNpcClickableGfs?cfg.crimsonNpcActionText:"";String template=cfg.crimsonNpcMessage.replace("{npc}",npc).replace("{item}",cfg.crimsonNpcShowItemName?item:"the requested item");int marker=template.indexOf("{action}");String before=marker<0?template:template.substring(0,marker),after=marker<0?"":template.substring(marker+8);Component message=Component.literal("§5[Crimson] §f"+before);if(marker>=0&&cfg.crimsonNpcClickableGfs&&!action.isBlank())message=message.copy().append(Component.literal("§a"+action).withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/gfs "+query+" 1")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Request 1 "+item+" from sacks")))));message=message.copy().append(Component.literal("§f"+after));mc.player.sendSystemMessage(message);return true;}
    private static boolean has(String id,String display){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return false;for(int slot=0;slot<36;slot++){ItemStack stack=mc.player.getInventory().getItem(slot);if(stack.isEmpty())continue;if(itemId(stack).equalsIgnoreCase(id)||clean(stack.getHoverName().getString()).equalsIgnoreCase(display))return true;}return false;}
    private static String itemId(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.getStringOr("id","");}
    private static Faction faction(){for(String line:TabList.lines()){String lower=clean(line).toLowerCase(Locale.ROOT);if(lower.contains("barbarian reputation"))return Faction.BARBARIAN;if(lower.contains("mage reputation"))return Faction.MAGE;}return Faction.UNKNOWN;}
    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("crimsonnpc").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("test").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("npc",StringArgumentType.word()).executes(c->test(StringArgumentType.getString(c,"npc"))))).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("value",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"value")))))));}
    private static int status(){local("Faction: "+faction().name().toLowerCase(Locale.ROOT)+" | Sirih "+on(cfg.crimsonSirihHelper)+" | Avorius "+on(cfg.crimsonAvoriusHelper)+" | Pablo "+on(cfg.crimsonPabloHelper)+" | inventory check "+on(cfg.crimsonNpcCheckInventory)+".");return 1;}
    private static int test(String npc){switch(npc.toLowerCase(Locale.ROOT)){case"sirih"->offer("Sirih","Sulphur","__TEST__","sulphur ore",true);case"avorius"->offer("Avorius","Cup of Blood","__TEST__","cup of blood",true);case"pablo"->offer("Pablo","Enchanted Dandelion","__TEST__","enchanted dandelion",true);default->{local("NPC must be sirih, avorius, or pablo.");return 0;}}return 1;}
    private static int option(String name,String value){boolean enabled=value.equalsIgnoreCase("on")||value.equalsIgnoreCase("true");if(!enabled&&!value.equalsIgnoreCase("off")&&!value.equalsIgnoreCase("false")){local("Value must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"sirih"->cfg.crimsonSirihHelper=enabled;case"avorius"->cfg.crimsonAvoriusHelper=enabled;case"pablo"->cfg.crimsonPabloHelper=enabled;case"faction"->cfg.crimsonNpcRequireFaction=enabled;case"inventory"->cfg.crimsonNpcCheckInventory=enabled;case"clickable"->cfg.crimsonNpcClickableGfs=enabled;default->{local("Option must be sirih, avorius, pablo, faction, inventory, or clickable.");return 0;}}ConstellationClient.saveConfig();return status();}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.CRIMSON_ISLE;}
    private static String on(boolean value){return value?"on":"off";}
    private static String clean(String value){return value.replaceAll("§[0-9A-FK-ORa-fk-or]","").trim();}
    private static void local(String value){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Crimson] §f"+value));}
}
