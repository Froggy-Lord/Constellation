package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
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
import org.lwjgl.glfw.GLFW;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): data/hotx/HotfData.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/HotxFeatures.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/hotx/CurrencyPerHotxPerk.kt
// cross-checked against Skyblocker (LGPL-3.0-only): skyblock/item/slottext/adders/HotfPerkLevelAdder.java
public final class ArtemisHotf {
    public record Summary(long whispers,int tokens,long spent,int enabled,int unlocked,int maxed){}
    private enum Cost { NONE,SWEEP,FORTUNE,STRENGTH,SPEED,LUCK,DAILY,DEEP,EFFICIENT,FOREST,HUNTER,HALF,RICOCHET }
    private enum Perk {
        SWEEP("Sweep",50,Cost.SWEEP),FORAGING_FORTUNE("Foraging Fortune",50,Cost.FORTUNE),
        STRENGTH_BOOST("Strength Boost",50,Cost.STRENGTH),DAMAGE_BOOST("Damage Boost",2,Cost.NONE),
        SPEED_BOOST("Speed Boost",50,Cost.SPEED),AXE_TOSS("Axe Toss",2,Cost.NONE),
        LUCK_OF_THE_FOREST("Luck of the Forest",40,Cost.LUCK),DAILY_WISHES("Daily Wishes",100,Cost.DAILY),
        GIFTS_250("250 Gifts",40,Cost.LUCK),LOTTERY("Lottery",2,Cost.NONE),
        FORAGING_MADNESS("Foraging Madness",2,Cost.NONE),DEEP_WATERS("Deep Waters",50,Cost.DEEP),
        EFFICIENT_FORAGER("Efficient Forager",100,Cost.EFFICIENT),COLLECTOR("Collector",50,Cost.DEEP),
        EARLY_BIRD("Early Bird",2,Cost.NONE),PRECISION_CUTTING("Precision Cutting",2,Cost.NONE),
        MONSTER_HUNTER("Monster Hunter",2,Cost.NONE),TREE_WHISPERER("Tree Whisperer",2,Cost.NONE),
        HOMING_AXE("Homing Axe",2,Cost.NONE),FOREST_STRENGTH("Forest Strength",50,Cost.FOREST),
        HUNTERS_LUCK("Hunter's Luck",50,Cost.HUNTER),GALATEAS_MIGHT("Galatea's Might",50,Cost.HUNTER),
        ESSENCE_FORTUNE("Essence Fortune",50,Cost.HUNTER),FOREST_SPEED("Forest Speed",50,Cost.FOREST),
        MANIAC_SLICER("Maniac Slicer",2,Cost.NONE),HALF_EMPTY("Half Empty",25,Cost.HALF),
        RICOCHET("Ricochet",10,Cost.RICOCHET),HALF_FULL("Half Full",25,Cost.HALF),
        CENTER_OF_THE_FOREST("Center of the Forest",5,Cost.NONE);
        final String name;final int max;final Cost cost;Perk(String name,int max,Cost cost){this.name=name;this.max=max;this.cost=cost;}
        static Perk from(String name){for(Perk perk:values())if(perk.name.equalsIgnoreCase(name))return perk;return null;}
    }
    private record State(int slot,int level,boolean enabled,boolean unlocked){}
    private static final Pattern LEVEL=Pattern.compile("(?:.*)Level (\\d+)(?:/(\\d+))?.*",Pattern.CASE_INSENSITIVE);
    private static final Pattern WHISPERS=Pattern.compile("^Forest Whispers: ([\\d,]+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKENS=Pattern.compile("^Tokens of the Forest: (\\d+)$",Pattern.CASE_INSENSITIVE);
    private static final EnumMap<Perk,State> STATES=new EnumMap<>(Perk.class);
    private static ArtemisConfig cfg;
    private static boolean initialized;
    private static long whispers;
    private static int tokens;
    private static String signature="";

    private ArtemisHotf(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(2,"artemis-hotf",ArtemisHotf::tick);
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!active(screen)){clear();return;}
        String next=screen.getMenu().slots.stream().limit(54).map(slot->slot.index+":"+slot.getItem().getHoverName().getString()+":"+slot.getItem().getCount()+":"+slot.getItem().hasFoil()+":"+lore(slot.getItem())).reduce("",String::concat);
        if(next.equals(signature))return;signature=next;read(screen);
    }

    private static void read(AbstractContainerScreen<?> screen){
        STATES.clear();whispers=0;tokens=0;
        for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty())continue;
            String name=clean(stack.getHoverName().getString());List<String> lore=lore(stack);
            if(name.equals("Heart of the Forest")){for(String line:lore){Matcher w=WHISPERS.matcher(line);if(w.matches())whispers=number(w.group(1));Matcher t=TOKENS.matcher(line);if(t.matches())tokens=(int)number(t.group(1));}continue;}
            Perk perk=Perk.from(name);if(perk==null)continue;
            int level=0;boolean enabled=false,locked=false;
            for(String line:lore){
                Matcher matcher=LEVEL.matcher(line);if(matcher.matches())level=Math.max(level,(int)number(matcher.group(1)));
                enabled|=line.equalsIgnoreCase("ENABLED")||line.equalsIgnoreCase("SELECTED");
                locked|=line.startsWith("Requires ")||line.endsWith(" Forest!")||line.equalsIgnoreCase("Click to unlock!");
            }
            boolean unlocked=!locked&&(level>0||enabled||lore.stream().anyMatch(line->line.equalsIgnoreCase("DISABLED")));
            STATES.put(perk,new State(slot.index,Math.clamp(level,0,perk.max),enabled,unlocked));
        }
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(slot==null||!active(screen))return;
        State state=state(slot.index);
        if(state!=null){
            if(cfg.hotfHighlightEnabledPerks)graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,!state.unlocked?cfg.hotfLockedColor:state.enabled?cfg.hotfEnabledColor:cfg.hotfDisabledColor);
            Perk perk=perk(slot.index);
            if(cfg.hotfLevelStackSize&&perk!=null&&state.level>0&&state.level<perk.max)
                graphics.text(Minecraft.getInstance().font,Integer.toString(state.level),slot.x+1,slot.y+9,state.level>=perk.max?cfg.hotfMaxLevelTextColor:cfg.hotfLevelTextColor,true);
            return;
        }
        if(cfg.hotfTokenStackSize&&clean(slot.getItem().getHoverName().getString()).equals("Heart of the Forest")&&tokens>0)
            graphics.text(Minecraft.getInstance().font,Integer.toString(tokens),slot.x+1,slot.y+9,cfg.hotfTokenTextColor,true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> current){
        if(!active(screen)||stack==null||current==null)return current;
        Perk perk=Perk.from(clean(stack.getHoverName().getString()));State state=perk==null?null:STATES.get(perk);
        if(perk==null||state==null||perk.cost==Cost.NONE)return current;
        List<Component> out=new ArrayList<>(current);long spent=total(perk,state.level),max=total(perk,perk.max);
        if(cfg.hotfWhispersSpent&&!(cfg.hotfHideMaxedTooltipDetails&&state.level>=perk.max))out.add(Math.min(2,out.size()),Component.literal(spentLine(spent,max,state.level>=perk.max)));
        if(cfg.hotfWhispersFor10Levels&&shift()&&state.level<perk.max){
            int levels=Math.min(10,perk.max-state.level);long needed=total(perk,state.level+levels)-spent;
            out.add(Component.literal("§7Whispers for "+levels+" "+(levels==1?"level":"levels")+": §e"+format(needed)));
        }
        if(cfg.hotfCurrentWhispers&&state.unlocked&&state.level<perk.max){
            long next=cost(perk,state.level+1);
            out.add(Component.literal("§7You have §3"+format(whispers)+" Forest Whispers"));
            out.add(Component.literal(whispers>=next?"§aEnough for the next level":"§c"+format(next-whispers)+" more needed for the next level"));
        }
        return out;
    }

    private static String spentLine(long spent,long max,boolean maxed){
        double percent=max<=0?100:spent*100.0/max;String design=cfg.hotfWhispersSpentDesign.toUpperCase(Locale.ROOT);
        String value=switch(design){case"NUMBER"->format(spent)+(maxed?"":" / "+format(max));case"PERCENTAGE"->decimal(percent)+"%";default->format(maxed?max:spent)+(maxed?"":" / "+format(max)+" ("+decimal(percent)+"%)");};
        return"§7Whispers spent: §e"+value+(maxed?" §7(§aMax level§7)":"");
    }

    public static Summary summary(){
        long spent=0;int enabled=0,unlocked=0,maxed=0;
        for(var entry:STATES.entrySet()){State state=entry.getValue();spent+=total(entry.getKey(),state.level);if(state.enabled)enabled++;if(state.unlocked)unlocked++;if(state.level>=entry.getKey().max)maxed++;}
        return new Summary(whispers,tokens,spent,enabled,unlocked,maxed);
    }
    public static boolean visible(){Minecraft mc=Minecraft.getInstance();return cfg!=null&&cfg.enabled&&cfg.hotfHelper&&cfg.hotfHud&&mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&active(screen);}
    public static ArtemisConfig config(){return cfg;}

    private static State state(int slot){return STATES.values().stream().filter(state->state.slot==slot).findFirst().orElse(null);}
    private static Perk perk(int slot){return STATES.entrySet().stream().filter(entry->entry.getValue().slot==slot).map(java.util.Map.Entry::getKey).findFirst().orElse(null);}
    private static long total(Perk perk,int level){long sum=0;for(int i=2;i<=Math.min(level,perk.max);i++)sum+=cost(perk,i);return sum;}
    private static long cost(Perk perk,int level){
        double value=switch(perk.cost){case SWEEP->Math.pow(level+1,3);case FORTUNE->Math.pow(level+1,3.105);case STRENGTH,SPEED->Math.pow(level+1,3.1);case LUCK->Math.pow(level+1,3.07);case DAILY->200+level*18.0;case DEEP->Math.pow(level+1,2.9);case EFFICIENT->Math.pow(level+1,2.6);case FOREST->Math.pow(level+1,3.4);case HUNTER->Math.pow(level+1,3.2);case HALF->Math.pow(level+1,4.1);case RICOCHET->Math.pow(level+1,5.5);default->0;};return(long)value;
    }
    private static boolean shift(){var window=Minecraft.getInstance().getWindow();return InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);}
    private static boolean active(AbstractContainerScreen<?> screen){return cfg!=null&&cfg.enabled&&cfg.hotfHelper&&ConstellationClient.loc().onHypixel()&&clean(screen.getTitle().getString()).equals("Heart of the Forest");}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static long number(String raw){try{return Long.parseLong(raw.replace(",",""));}catch(Exception ignored){return 0;}}
    private static String format(long value){return NumberFormat.getIntegerInstance(Locale.US).format(value);}
    private static String decimal(double value){return value==Math.rint(value)?Long.toString(Math.round(value)):String.format(Locale.US,"%.2f",value);}
    private static void clear(){signature="";STATES.clear();whispers=0;tokens=0;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[HOTF] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hotfhelper").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("design").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).executes(c->design(StringArgumentType.getString(c,"type")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){Summary s=summary();local("HOTF helper "+on(cfg.hotfHelper)+", "+s.unlocked+" perks read, "+format(s.whispers)+" available Whispers and "+s.tokens+" tokens.");return 1;}
    private static int design(String raw){String value=raw.toUpperCase(Locale.ROOT).replace('-','_');if(!value.equals("NUMBER")&&!value.equals("PERCENTAGE")&&!value.equals("NUMBER_AND_PERCENTAGE")){local("Design must be number, percentage, or number_and_percentage.");return 0;}cfg.hotfWhispersSpentDesign=value;save();return status();}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"enabled"->cfg.hotfHelper=value;case"highlight"->cfg.hotfHighlightEnabledPerks=value;case"levels","levelstack"->cfg.hotfLevelStackSize=value;case"tokens","tokenstack"->cfg.hotfTokenStackSize=value;case"spent"->cfg.hotfWhispersSpent=value;case"tenlevels","ten"->cfg.hotfWhispersFor10Levels=value;case"current"->cfg.hotfCurrentWhispers=value;case"hud"->cfg.hotfHud=value;case"hudtokens"->cfg.hotfHudTokens=value;case"hudwhispers"->cfg.hotfHudWhispers=value;case"hudspent"->cfg.hotfHudSpent=value;case"hudperks"->cfg.hotfHudPerks=value;case"hudmaxed"->cfg.hotfHudMaxed=value;case"hidemaxed"->cfg.hotfHideMaxedTooltipDetails=value;default->{local("Unknown HOTF option.");return 0;}}save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
}
