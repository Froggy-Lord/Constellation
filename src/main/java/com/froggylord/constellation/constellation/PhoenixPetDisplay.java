package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.PhoenixConfig;
import com.froggylord.constellation.config.PhoenixConfig.PetDisplayData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Devonian (GPL-3.0-only): features/misc/PetDisplay.kt, ShowSelectedPet.kt, tooltip/PetXP.kt
// cross-checked with NoammAddons (CC0-1.0): features/impl/visual/PetDisplay.kt
// cross-checked with Skyblocker (LGPL-3.0-or-later): skyblock/PetCache.java
public final class PhoenixPetDisplay {
    private static final Pattern PETS=Pattern.compile("^(?:\\((\\d+)/(\\d+)\\) )?Pets$");
    private static final Pattern NAME=Pattern.compile("^(?:\\u2B50 )?\\[Lvl (\\d+)](?: \\[([\\d,.]+)[^]]*])? (.+?)( \\u2726)?$");
    private static final Pattern AUTOPET=Pattern.compile("^Autopet equipped your \\[Lvl (\\d+)](?: \\[([\\d,.]+)[^]]*])? (.+?)( \\u2726)?! VIEW RULE$");
    private static final Pattern SUMMON=Pattern.compile("^You summoned your (.+?)( \\u2726)?!$");
    private static final Pattern DESPAWN=Pattern.compile("^You despawned your .+?!$");
    private static final Pattern TAB=Pattern.compile("^\\[Lvl (\\d+)](?: \\[([\\d,.]+)[^]]*])? (.+?)( \\u2726)?$");
    private static final Pattern PROGRESS=Pattern.compile("(?i)^Progress to Level .+?:\\s*([\\d,.]+)%.*$");
    private static PhoenixConfig cfg;
    private static AbstractContainerScreen<?> petsScreen;
    private static ItemStack icon=ItemStack.EMPTY;
    private static int selectedSlot=-1;
    private static String loadedProfile="";
    private static double previousProgress=-1,percentPerHour;
    private static long previousProgressAt;
    private static boolean initialized;
    private static PetDisplayData preview;

    private PhoenixPetDisplay(){}

    public static void init(PhoenixConfig config){
        cfg=config;normalize();
        if(initialized)return;initialized=true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)chat(clean(message.getString()));return true;});
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
            if(!(screen instanceof AbstractContainerScreen<?> container)||!PETS.matcher(clean(container.getTitle().getString())).matches())return;
            petsScreen=container;selectedSlot=-1;
            ScreenEvents.afterTick(container).register(ignored->scan(container));
            ScreenEvents.afterExtract(container).register((ignored,graphics,mouseX,mouseY,delta)->drawSelected(container,graphics));
            ScreenEvents.remove(container).register(ignored->{if(petsScreen==container){petsScreen=null;selectedSlot=-1;}});
        });
        ItemTooltipCallback.EVENT.register((stack,context,flags,lines)->petTooltip(stack,lines));
        ConstellationClient.tick().every(20,"phoenix-pet-display",PhoenixPetDisplay::tick);
    }

    private static void tick(){
        String profile=profile();if(!profile.equals(loadedProfile)){loadedProfile=profile;icon=ItemStack.EMPTY;selectedSlot=-1;previousProgress=-1;previousProgressAt=0;percentPerHour=0;}
        if(!active())return;
        if(petsScreen!=null)scan(petsScreen);
        for(String line:com.froggylord.constellation.data.TabList.lines()){Matcher matcher=TAB.matcher(clean(line));if(!matcher.matches())continue;PetDisplayData current=current();if(current!=null&&sameName(current.name,matcher.group(3))&&current.level==number(matcher.group(1)))continue;learn(matcher.group(3),number(matcher.group(1)),number(matcher.group(2)),matcher.group(4)!=null,null,"Widget",null);break;}
    }

    private static void chat(String text){
        if(!active())return;
        Matcher auto=AUTOPET.matcher(text);if(auto.matches()){learn(auto.group(3),number(auto.group(1)),number(auto.group(2)),auto.group(4)!=null,null,"Autopet",null);if(cfg.petDisplayAutopetTitle&&(!cfg.petDisplayAutopetTitleDungeonOnly||ConstellationClient.loc().inDungeons()))showTitle(auto.group(3));return;}
        Matcher summon=SUMMON.matcher(text);if(summon.matches()){PetDisplayData old=find(summon.group(1));learn(summon.group(1),old==null?-1:old.level,old==null?-1:old.cosmeticLevel,summon.group(2)!=null,old,"Summon",null);return;}
        if(DESPAWN.matcher(text).matches())clearCurrent();
    }

    private static void scan(AbstractContainerScreen<?> screen){
        if(!active()||screen!=petsScreen||!PETS.matcher(clean(screen.getTitle().getString())).matches())return;
        int found=-1;for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty()||slot.index>=54||!pet(stack))continue;
            boolean selected=lore(stack).stream().anyMatch(line->clean(line).equals("Click to despawn!"));
            Matcher matcher=NAME.matcher(clean(stack.getHoverName().getString()));if(!matcher.matches())continue;
            PetDisplayData parsed=parse(stack,matcher,selected?"Pets menu":"Pet cache");
            remember(parsed);
            if(selected){found=slot.index;icon=stack.copy();setCurrent(parsed);}
        }selectedSlot=found;
    }

    private static PetDisplayData parse(ItemStack stack,Matcher matcher,String source){
        PetDisplayData data=new PetDisplayData();data.name=matcher.group(3).trim();data.level=number(matcher.group(1));data.cosmeticLevel=number(matcher.group(2));data.skinned=matcher.group(4)!=null;data.source=source;data.updatedAt=System.currentTimeMillis();
        try{JsonObject pet=JsonParser.parseString(extra(stack).getStringOr("petInfo","")).getAsJsonObject();if(pet.has("tier"))data.rarity=human(pet.get("tier").getAsString());if(pet.has("heldItem")&&!pet.get("heldItem").isJsonNull())data.heldItem=human(pet.get("heldItem").getAsString());if(pet.has("skin")&&!pet.get("skin").isJsonNull())data.skinned=true;if(pet.has("exp"))data.totalXp=pet.get("exp").getAsDouble();}catch(Exception ignored){}
        for(String line:lore(stack)){Matcher progress=PROGRESS.matcher(clean(line));if(progress.matches()){data.levelProgress=decimal(progress.group(1));break;}if(clean(line).equalsIgnoreCase("MAX LEVEL"))data.levelProgress=100;}
        return data;
    }

    private static void learn(String name,int level,int cosmetic,boolean skinned,PetDisplayData fallback,String source,ItemStack stack){
        PetDisplayData data=fallback==null?find(name):fallback;if(data==null)data=new PetDisplayData();data.name=name.trim();if(level>=0)data.level=level;if(cosmetic>=0)data.cosmeticLevel=cosmetic;data.skinned|=skinned;data.source=source;data.updatedAt=System.currentTimeMillis();if(stack!=null)icon=stack.copy();remember(data);setCurrent(data);
    }

    private static void setCurrent(PetDisplayData data){
        if(data==null||profile().isBlank())return;
        PetDisplayData old=current();if(old!=null&&sameName(old.name,data.name)&&data.levelProgress>=0)sample(data.levelProgress);
        cfg.activePetsByProfile.put(profile(),copy(data));if(cfg.petDisplayPersistProfiles)ConstellationClient.saveConfig();
    }
    private static void remember(PetDisplayData data){if(data==null||data.name.isBlank())return;CACHE.put(key(data.name),copy(data));}
    private static final Map<String,PetDisplayData> CACHE=new java.util.LinkedHashMap<>();
    private static PetDisplayData find(String name){PetDisplayData data=CACHE.get(key(name));if(data!=null)return copy(data);PetDisplayData current=current();return current!=null&&sameName(current.name,name)?copy(current):null;}
    private static PetDisplayData current(){if(preview!=null)return preview;return cfg==null||profile().isBlank()?null:cfg.activePetsByProfile.get(profile());}
    private static void clearCurrent(){preview=null;if(!profile().isBlank()){cfg.activePetsByProfile.remove(profile());if(cfg.petDisplayPersistProfiles)ConstellationClient.saveConfig();}icon=ItemStack.EMPTY;selectedSlot=-1;previousProgress=-1;percentPerHour=0;}

    private static void sample(double progress){long now=System.currentTimeMillis();if(previousProgress>=0&&progress>previousProgress&&now-previousProgressAt>=1000){percentPerHour=(progress-previousProgress)*3_600_000d/(now-previousProgressAt);}previousProgress=progress;previousProgressAt=now;}
    public static PetDisplayData state(){return current();}
    public static ItemStack icon(){return icon;}
    public static double percentPerHour(){return percentPerHour;}
    public static String eta(){PetDisplayData data=current();if(data==null||data.levelProgress<0||percentPerHour<=0)return"";long seconds=Math.round((100-data.levelProgress)/percentPerHour*3600);return duration(seconds);}
    public static boolean visible(){return active()&&cfg.petDisplayHud&&current()!=null;}
    public static PhoenixConfig config(){return cfg;}

    private static void drawSelected(AbstractContainerScreen<?> screen,net.minecraft.client.gui.GuiGraphicsExtractor graphics){
        if(!active()||!cfg.petDisplayHighlightSelected||screen!=petsScreen||selectedSlot<0)return;
        for(Slot slot:screen.getMenu().slots)if(slot.index==selectedSlot){graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,cfg.petDisplayHighlightColor);break;}
    }
    private static void petTooltip(ItemStack stack,List<Component> lines){if(!active()||!cfg.petDisplayTooltipTotalXp||!pet(stack))return;try{double xp=JsonParser.parseString(extra(stack).getStringOr("petInfo","")).getAsJsonObject().get("exp").getAsDouble();lines.add(Component.literal("Total Pet XP: \u00a76"+format(xp)));}catch(Exception ignored){}}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("petdisplay").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("preview").executes(c->preview()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("confirm").executes(c->{clearCurrent();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("target",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int preview(){Minecraft mc=Minecraft.getInstance();if(!mc.hasSingleplayerServer()){local("Preview is only available in a local world.");return 0;}if(preview!=null){clearCurrent();local("Preview hidden.");return 1;}PetDisplayData data=new PetDisplayData();data.name="Golden Dragon";data.level=200;data.cosmeticLevel=200;data.rarity="LEGENDARY";data.heldItem="Minos Relic";data.skinned=true;data.levelProgress=73.4;data.source="Preview";data.updatedAt=System.currentTimeMillis();preview=data;icon=new ItemStack(Items.PLAYER_HEAD);local("Preview shown. Run /petdisplay preview again to hide it.");return 1;}
    private static int status(){PetDisplayData data=current();local(data==null?"No active pet is known for this profile.":(data.level>=0?"Level "+data.level+" ":"")+data.name+(data.rarity.isBlank()?"":" ("+data.rarity+")")+", source "+data.source+".");return 1;}
    private static int option(String name,String raw){Boolean value=bool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.petDisplay=value;case"hud"->cfg.petDisplayHud=value;case"icon"->cfg.petDisplayIcon=value;case"level"->cfg.petDisplayLevel=value;case"cosmetic"->cfg.petDisplayCosmeticLevel=value;case"skin"->cfg.petDisplaySkin=value;case"rarity"->cfg.petDisplayRarity=value;case"item"->cfg.petDisplayHeldItem=value;case"xp"->cfg.petDisplayXp=value;case"rate"->cfg.petDisplayRate=value;case"eta"->cfg.petDisplayEta=value;case"source"->cfg.petDisplaySource=value;case"persist"->cfg.petDisplayPersistProfiles=value;case"highlight"->cfg.petDisplayHighlightSelected=value;case"tooltip"->cfg.petDisplayTooltipTotalXp=value;case"autopettitle"->cfg.petDisplayAutopetTitle=value;case"dungeononly"->cfg.petDisplayAutopetTitleDungeonOnly=value;default->{local("Unknown Pet Display option.");return 0;}}ConstellationClient.saveConfig();return status();}
    private static int color(String target,String raw){try{String value=raw.replaceFirst("^(?:#|0[xX])","");long parsed=Long.parseUnsignedLong(value,16);if(value.length()<=6)parsed|=0xFF000000L;switch(target.toLowerCase(Locale.ROOT)){case"name"->cfg.petDisplayNameColor=(int)parsed;case"info"->cfg.petDisplayInfoColor=(int)parsed;case"progress"->cfg.petDisplayProgressColor=(int)parsed;case"highlight"->cfg.petDisplayHighlightColor=(int)parsed;default->{local("Color target must be name, info, progress or highlight.");return 0;}}ConstellationClient.saveConfig();return status();}catch(Exception ignored){local("Color must be ARGB hex.");return 0;}}

    private static String profile(){Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return"";for(PlayerInfo info:mc.getConnection().getOnlinePlayers()){Component display=info.getTabListDisplayName();if(display==null)continue;String line=clean(display.getString());if(line.startsWith("Profile: ")){String value=line.substring(9).replaceAll("[^A-Za-z0-9_-]","");if(!value.isBlank())return mc.getUser().getProfileId()+"/"+value.toLowerCase(Locale.ROOT);}}return"";}
    private static boolean active(){return cfg!=null&&cfg.enabled&&cfg.petDisplay&&(ConstellationClient.loc().onHypixel()||preview!=null&&Minecraft.getInstance().hasSingleplayerServer());}
    private static boolean pet(ItemStack stack){return extra(stack).getStringOr("id","").equals("PET");}
    private static CompoundTag extra(ItemStack stack){CustomData data=stack.get(DataComponents.CUSTOM_DATA);return data==null?new CompoundTag():data.copyTag().getCompoundOrEmpty("ExtraAttributes");}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(Component::getString).toList();}
    private static String clean(String value){String out=ChatFormatting.stripFormatting(value);return out==null?"":out.replaceAll("\\s+"," ").trim();}
    private static String key(String name){return clean(name).toLowerCase(Locale.ROOT);}
    private static boolean sameName(String a,String b){return key(a).equals(key(b));}
    private static int number(String value){if(value==null||value.isBlank())return-1;try{return Integer.parseInt(value.replace(",",""));}catch(Exception ignored){return-1;}}
    private static double decimal(String value){try{return Double.parseDouble(value.replace(",",""));}catch(Exception ignored){return-1;}}
    private static String human(String value){StringBuilder out=new StringBuilder();for(String part:value.toLowerCase(Locale.ROOT).split("_")){if(part.isBlank())continue;if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}return out.toString();}
    private static String format(double value){if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fm",value/1_000_000);if(value>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1_000);return String.format(Locale.ROOT,"%.0f",value);}
    private static String duration(long seconds){if(seconds>=86400)return seconds/86400+"d "+seconds%86400/3600+"h";if(seconds>=3600)return seconds/3600+"h "+seconds%3600/60+"m";return seconds/60+"m "+seconds%60+"s";}
    private static Boolean bool(String value){return switch(value.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static PetDisplayData copy(PetDisplayData from){PetDisplayData to=new PetDisplayData();to.name=from.name;to.level=from.level;to.cosmeticLevel=from.cosmeticLevel;to.rarity=from.rarity;to.heldItem=from.heldItem;to.skinned=from.skinned;to.totalXp=from.totalXp;to.levelProgress=from.levelProgress;to.source=from.source;to.updatedAt=from.updatedAt;return to;}
    private static void normalize(){if(cfg.activePetsByProfile==null)cfg.activePetsByProfile=new java.util.LinkedHashMap<>();}
    private static void showTitle(String value){Minecraft mc=Minecraft.getInstance();if(mc.player!=null){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(value).withColor(cfg.petDisplayNameColor&0xFFFFFF));}}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a76[Pet Display] \u00a7f"+text));}
}
