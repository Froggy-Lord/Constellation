package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

import static com.froggylord.constellation.core.LocationManager.SkyblockArea;

// ported from SkyHanni (LGPL-3.0-or-later): data/hotx/HotmData.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/hotx/HotxHandler.kt
// ported from SkyHanni (LGPL-3.0-or-later): data/hotx/CurrencyPerHotxPerk.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/HotxFeatures.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/PowderPerHotmPerk.kt
public final class AquilaHotmHelper {
    public record Summary(long mithril, long gemstone, long glacite, int tokens,
                          long mithrilSpent, long gemstoneSpent, long glaciteSpent,
                          int enabled, int unlocked, int maxed, String skyMall) {}
    private enum Powder { NONE, MITHRIL, GEMSTONE, GLACITE }
    private enum Perk {
        MINING_SPEED("Mining Speed",50,3.0,Powder.MITHRIL),MINING_FORTUNE("Mining Fortune",50,3.05,Powder.MITHRIL),
        TITANIUM_INSANIUM("Titanium Insanium",50,3.1,Powder.MITHRIL),LUCK_OF_THE_CAVE("Luck of the Cave",45,3.07,Powder.MITHRIL),
        EFFICIENT_MINER("Efficient Miner",100,2.6,Powder.MITHRIL),QUICK_FORGE("Quick Forge",20,3.2,Powder.MITHRIL),
        OLD_SCHOOL("Old-School",20,4.0,Powder.GEMSTONE),PROFESSIONAL("Professional",140,2.3,Powder.GEMSTONE),
        MOLE("Mole",200,2.17883,Powder.GEMSTONE),GEM_LOVER("Gem Lover",20,4.0,Powder.GEMSTONE),
        SEASONED_MINEMAN("Seasoned Mineman",100,2.3,Powder.GEMSTONE),FORTUNATE_MINEMAN("Fortunate Mineman",50,3.2,Powder.GEMSTONE),
        BLOCKHEAD("Blockhead",20,4.0,Powder.GEMSTONE),KEEP_IT_COOL("Keep It Cool",50,3.07,Powder.GEMSTONE),
        LONESOME_MINER("Lonesome Miner",45,3.07,Powder.GEMSTONE),GREAT_EXPLORER("Great Explorer",20,4.0,Powder.GEMSTONE),
        POWDER_BUFF("Powder Buff",50,3.2,Powder.GEMSTONE),SPEEDY_MINEMAN("Speedy Mineman",50,3.2,Powder.GEMSTONE),
        SUBTERRANEAN_FISHER("Subterranean Fisher",40,3.07,Powder.GEMSTONE),
        SKY_MALL("Sky Mall",1,0,Powder.NONE),PRECISION_MINING("Precision Mining",1,0,Powder.NONE),
        FRONT_LOADED("Front Loaded",1,0,Powder.NONE),DAILY_GRIND("Daily Grind",1,0,Powder.NONE),
        DAILY_POWDER("Daily Powder",1,0,Powder.NONE),PICKOBULUS("Pickobulus",3,0,Powder.NONE),
        MINING_SPEED_BOOST("Mining Speed Boost",3,0,Powder.NONE),MANIAC_MINER("Maniac Miner",3,0,Powder.NONE),
        SHEER_FORCE("Sheer Force",3,0,Powder.NONE),TUNNEL_VISION("Tunnel Vision",3,0,Powder.NONE),
        CORE_OF_THE_MOUNTAIN("Core of the Mountain",10,0,Powder.NONE),
        NO_STONE_UNTURNED("No Stone Unturned",50,3.05,Powder.GLACITE),STRONG_ARM("Strong Arm",100,2.3,Powder.GLACITE),
        STEADY_HAND("Steady Hand",100,2.6,Powder.GLACITE),WARM_HEART("Warm Heart",50,3.1,Powder.GLACITE),
        SURVEYOR("Surveyor",20,4.0,Powder.GLACITE),METAL_HEAD("Metal Head",20,4.0,Powder.GLACITE),
        RAGS_TO_RICHES("Rags to Riches",50,3.05,Powder.GLACITE),EAGER_ADVENTURER("Eager Adventurer",100,2.3,Powder.GLACITE),
        CRYSTALLINE("Crystalline",50,3.3,Powder.GLACITE),GIFTS_FROM_THE_DEPARTED("Gifts from the Departed",100,2.45,Powder.GLACITE),
        MINING_MASTER("Mining Master",10,-5.0,Powder.GLACITE),DEAD_MANS_CHEST("Dead Man's Chest",50,3.2,Powder.GLACITE),
        VANGUARD_SEEKER("Vanguard Seeker",50,3.1,Powder.GLACITE),MINESHAFT_MAYHEM("Mineshaft Mayhem",1,0,Powder.NONE),
        GEMSTONE_INFUSION("Gemstone Infusion",1,0,Powder.NONE),MINERS_BLESSING("Miner's Blessing",1,0,Powder.NONE);
        final String name;final int max;final double exponent;final Powder powder;
        Perk(String name,int max,double exponent,Powder powder){this.name=name;this.max=max;this.exponent=exponent;this.powder=powder;}
        static Perk from(String name){for(Perk perk:values())if(perk.name.equalsIgnoreCase(name))return perk;return null;}
    }
    private record State(int slot,int level,boolean enabled,boolean unlocked) {}
    private static final Pattern LEVEL=Pattern.compile(".*Level (\\d+)(?:/(\\d+))?.*",Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKENS=Pattern.compile("^Token of the Mountain: ([\\d,]+)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern POWDER=Pattern.compile("^(Mithril|Gemstone|Glacite) Powder: ([\\d,]+)$",Pattern.CASE_INSENSITIVE);
    private static final EnumMap<Perk,State> STATES=new EnumMap<>(Perk.class);
    private static AquilaConfig cfg;
    private static boolean initialized;
    private static String signature="";
    private static long mithril,gemstone,glacite;
    private static int tokens;
    private static String skyMall="";
    private static boolean waitingForSkyMall;

    private AquilaHotmHelper() {}

    public static void init(AquilaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(2,"aquila-hotm-helper",AquilaHotmHelper::tick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(clean(message.getString()));return true;});
    }

    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!active(screen)){clear();return;}
        String next=screen.getMenu().slots.stream().limit(54).map(slot->slot.index+":"+slot.getItem().getHoverName().getString()+":"+slot.getItem().getCount()+":"+slot.getItem().hasFoil()+":"+lore(slot.getItem())).reduce("",String::concat);
        if(next.equals(signature))return;signature=next;read(screen);
    }

    private static void read(AbstractContainerScreen<?> screen){
        STATES.clear();mithril=0;gemstone=0;glacite=0;tokens=0;skyMall="";
        for(Slot slot:screen.getMenu().slots){
            ItemStack stack=slot.getItem();if(stack.isEmpty())continue;
            String name=clean(stack.getHoverName().getString());List<String> lines=lore(stack);
            if(name.equals("Heart of the Mountain")){readHeart(lines);continue;}
            Perk perk=Perk.from(name);if(perk==null)continue;
            int level=0;boolean sawLevel=false,enabled=false,locked=false;
            for(String line:lines){
                Matcher levelMatcher=LEVEL.matcher(line);if(levelMatcher.matches()){level=Math.max(level,(int)number(levelMatcher.group(1)));sawLevel=true;}
                enabled|=line.equalsIgnoreCase("ENABLED")||line.equalsIgnoreCase("SELECTED");
                locked|=line.startsWith("Requires")||line.endsWith("Mountain!")||line.equalsIgnoreCase("Click to unlock!");
            }
            boolean unlocked=!locked&&(level>0||enabled||lines.stream().anyMatch(line->line.equalsIgnoreCase("DISABLED")||line.equalsIgnoreCase("Click to select!")));
            if(unlocked&&!sawLevel)level=perk.max;
            STATES.put(perk,new State(slot.index,Math.clamp(level,0,perk.max),enabled,unlocked));
            if(perk==Perk.SKY_MALL&&enabled){skyMall=readSkyMall(lines);rememberSkyMall();}
        }
    }

    private static void readHeart(List<String> lines){
        for(String line:lines){
            Matcher token=TOKENS.matcher(line);if(token.matches()){tokens=(int)number(token.group(1));continue;}
            Matcher powder=POWDER.matcher(line);if(!powder.matches())continue;
            long value=number(powder.group(2));
            switch(powder.group(1).toLowerCase(Locale.ROOT)){case"mithril"->mithril=value;case"gemstone"->gemstone=value;case"glacite"->glacite=value;default->{}}
        }
    }

    private static String readSkyMall(List<String> lines){
        for(String line:lines){
            if(line.contains("+100")&&line.contains("Mining Speed"))return"+100 Mining Speed";
            if(line.contains("+50")&&line.contains("Mining Fortune"))return"+50 Mining Fortune";
            if(line.contains("+15%")&&line.contains("Powder"))return"+15% Powder";
            if(line.contains("-20%")&&line.contains("cooldown"))return"-20% ability cooldown";
            if(line.contains("10x")&&line.contains("Goblin"))return"10x Golden/Diamond Goblins";
            if(line.contains("5x")&&line.contains("Titanium"))return"5x Titanium";
        }
        return"Unknown";
    }

    public static void drawSlot(GuiGraphicsExtractor graphics,AbstractContainerScreen<?> screen,Slot slot){
        if(slot==null||!active(screen))return;
        State state=state(slot.index);
        if(state!=null){
            if(cfg.hotmHighlightEnabledPerks)graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,!state.unlocked?cfg.hotmLockedColor:state.enabled?cfg.hotmEnabledColor:cfg.hotmDisabledColor);
            Perk perk=perk(slot.index);
            if(cfg.hotmLevelStackSize&&perk!=null&&state.level>0&&state.level<perk.max)
                graphics.text(Minecraft.getInstance().font,Integer.toString(state.level),slot.x+1,slot.y+9,cfg.hotmLevelTextColor,true);
            return;
        }
        if(cfg.hotmTokenStackSize&&clean(slot.getItem().getHoverName().getString()).equals("Heart of the Mountain")&&tokens>0)
            graphics.text(Minecraft.getInstance().font,Integer.toString(tokens),slot.x+1,slot.y+9,cfg.hotmTokenTextColor,true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> screen,ItemStack stack,List<Component> current){
        if(!active(screen)||stack==null||current==null)return current;
        Perk perk=Perk.from(clean(stack.getHoverName().getString()));State state=perk==null?null:STATES.get(perk);
        if(perk==null||state==null||perk.powder==Powder.NONE)return current;
        List<Component> out=new ArrayList<>(current);long spent=total(perk,state.level),max=total(perk,perk.max);
        if(cfg.hotmPowderSpent&&!(cfg.hotmHideMaxedTooltipDetails&&state.level>=perk.max))
            out.add(Math.min(2,out.size()),Component.literal(spentLine(perk.powder,spent,max,state.level>=perk.max)));
        if(cfg.hotmPowderFor10Levels&&shift()&&state.level<perk.max){
            int levels=Math.min(10,perk.max-state.level);long needed=total(perk,state.level+levels)-spent;
            out.add(Component.literal("§7Powder for "+levels+" "+(levels==1?"level":"levels")+": §e"+format(needed)));
        }
        if(cfg.hotmCurrentPowder&&state.unlocked&&state.level<perk.max){
            long available=available(perk.powder),next=cost(perk,state.level+1);
            out.add(Component.literal("§7You have "+powderColor(perk.powder)+format(available)+" "+powderName(perk.powder)+" Powder"));
            out.add(Component.literal(available>=next?"§aEnough for the next level":"§c"+format(next-available)+" more needed for the next level"));
        }
        return out;
    }

    private static String spentLine(Powder powder,long spent,long max,boolean maxed){
        double percent=max<=0?100:spent*100.0/max;String design=cfg.hotmPowderSpentDesign.toUpperCase(Locale.ROOT);
        String value=switch(design){case"NUMBER"->format(spent)+(maxed?"":" / "+format(max));case"PERCENTAGE"->decimal(percent)+"%";default->format(maxed?max:spent)+(maxed?"":" / "+format(max)+" ("+decimal(percent)+"%)");};
        return"§7"+powderName(powder)+" Powder spent: §e"+value+(maxed?" §7(§aMax level§7)":"");
    }

    public static Summary summary(){
        long ms=0,gs=0,cs=0;int enabled=0,unlocked=0,maxed=0;
        for(var entry:STATES.entrySet()){Perk perk=entry.getKey();State state=entry.getValue();long spent=total(perk,state.level);switch(perk.powder){case MITHRIL->ms+=spent;case GEMSTONE->gs+=spent;case GLACITE->cs+=spent;default->{}}
            if(state.enabled)enabled++;if(state.unlocked)unlocked++;if(state.level>=perk.max)maxed++;}
        String current=skyMall.isBlank()?savedSkyMall():skyMall;
        return new Summary(mithril,gemstone,glacite,tokens,ms,gs,cs,enabled,unlocked,maxed,current);
    }
    public static boolean visible(){return cfg!=null&&cfg.enabled&&cfg.hotmHelper&&cfg.hotmHelperHud&&(menuOpen()||skyMallVisible());}
    public static boolean menuOpen(){Minecraft mc=Minecraft.getInstance();return mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&active(screen);}
    public static AquilaConfig config(){return cfg;}

    private static long total(Perk perk,int level){long sum=0;for(int i=2;i<=Math.min(level,perk.max);i++)sum+=cost(perk,i);return sum;}
    private static long cost(Perk perk,int level){if(perk.exponent==0)return 0;double base=perk==Perk.MINING_MASTER?level+7.0:level+1.0;double exponent=Math.abs(perk.exponent);return(long)Math.pow(base,exponent);}
    private static long available(Powder powder){return switch(powder){case MITHRIL->mithril;case GEMSTONE->gemstone;case GLACITE->glacite;default->0;};}
    private static String powderName(Powder powder){return switch(powder){case MITHRIL->"Mithril";case GEMSTONE->"Gemstone";case GLACITE->"Glacite";default->"";};}
    private static String powderColor(Powder powder){return switch(powder){case MITHRIL->"§2";case GEMSTONE->"§d";case GLACITE->"§b";default->"§f";};}
    private static State state(int slot){return STATES.values().stream().filter(value->value.slot==slot).findFirst().orElse(null);}
    private static Perk perk(int slot){return STATES.entrySet().stream().filter(entry->entry.getValue().slot==slot).map(java.util.Map.Entry::getKey).findFirst().orElse(null);}
    private static boolean active(AbstractContainerScreen<?> screen){return cfg!=null&&cfg.enabled&&cfg.hotmHelper&&ConstellationClient.loc().onHypixel()&&clean(screen.getTitle().getString()).equals("Heart of the Mountain");}
    private static boolean shift(){var window=Minecraft.getInstance().getWindow();return InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);}
    private static List<String> lore(ItemStack stack){ItemLore lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(line->clean(line.getString())).toList();}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static long number(String raw){try{return Long.parseLong(raw.replace(",",""));}catch(Exception ignored){return 0;}}
    private static String format(long value){return NumberFormat.getIntegerInstance(Locale.US).format(value);}
    private static String decimal(double value){return value==Math.rint(value)?Long.toString(Math.round(value)):String.format(Locale.US,"%.2f",value);}
    private static void clear(){signature="";STATES.clear();mithril=0;gemstone=0;glacite=0;tokens=0;skyMall="";}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§3[HOTM] §f"+text));}

    private static void onChat(String message){
        if(cfg==null||!cfg.enabled||!cfg.hotmHelper||!ConstellationClient.loc().onHypixel())return;
        if(message.equals("New day! Your Sky Mall buff changed!")){waitingForSkyMall=true;return;}
        if(!waitingForSkyMall||!message.startsWith("New buff: "))return;
        waitingForSkyMall=false;String parsed=readSkyMall(List.of(message.substring(10)));
        if(parsed.equals("Unknown"))return;skyMall=parsed;rememberSkyMall();
    }
    private static void rememberSkyMall(){
        if(skyMall.isBlank()||skyMall.equals("Unknown"))return;
        if(cfg.hotmSkyMallPerProfile==null)cfg.hotmSkyMallPerProfile=new java.util.HashMap<>();
        String key=profile();if(!skyMall.equals(cfg.hotmSkyMallPerProfile.put(key,skyMall)))save();
    }
    private static String savedSkyMall(){if(cfg.hotmSkyMallPerProfile==null)return"";return cfg.hotmSkyMallPerProfile.getOrDefault(profile(),"");}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null||value.isBlank()?"unknown":value.toLowerCase(Locale.ROOT);}
    private static boolean skyMallVisible(){
        if(!cfg.hotmHudSkyMall||savedSkyMall().isBlank())return false;
        return switch(cfg.hotmSkyMallDisplay.toUpperCase(Locale.ROOT)){case"EVERYWHERE"->true;case"MINING_ONLY"->inMiningArea();default->false;};
    }
    private static boolean inMiningArea(){SkyblockArea area=ConstellationClient.loc().area();return area==SkyblockArea.DWARVEN_MINES||area==SkyblockArea.CRYSTAL_HOLLOWS||area==SkyblockArea.GLACITE_TUNNELS||area==SkyblockArea.GLACITE_MINESHAFT;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hotmhelper").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("design").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).executes(c->design(StringArgumentType.getString(c,"type")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("skymall").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("visibility",StringArgumentType.word()).executes(c->skyMallMode(StringArgumentType.getString(c,"visibility")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("type",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("argb",StringArgumentType.word()).executes(c->color(StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){Summary state=summary();local("helper "+on(cfg.hotmHelper)+", "+state.unlocked+" perks read, "+format(state.mithril)+" Mithril, "+format(state.gemstone)+" Gemstone, "+format(state.glacite)+" Glacite and "+state.tokens+" tokens.");return 1;}
    private static int design(String raw){String value=raw.toUpperCase(Locale.ROOT).replace('-','_');if(!value.equals("NUMBER")&&!value.equals("PERCENTAGE")&&!value.equals("NUMBER_AND_PERCENTAGE")){local("Design must be number, percentage, or number_and_percentage.");return 0;}cfg.hotmPowderSpentDesign=value;save();return status();}
    private static int skyMallMode(String raw){String value=raw.toUpperCase(Locale.ROOT).replace('-','_');if(!value.equals("OFF")&&!value.equals("MINING_ONLY")&&!value.equals("EVERYWHERE")){local("Sky Mall visibility must be off, mining_only, or everywhere.");return 0;}cfg.hotmSkyMallDisplay=value;save();return status();}
    private static int color(String type,String raw){Integer value=parseColor(raw);if(value==null){local("Color must be RRGGBB or AARRGGBB.");return 0;}switch(type.toLowerCase(Locale.ROOT)){case"enabled"->cfg.hotmEnabledColor=value;case"disabled"->cfg.hotmDisabledColor=value;case"locked"->cfg.hotmLockedColor=value;case"level"->cfg.hotmLevelTextColor=value;case"token"->cfg.hotmTokenTextColor=value;default->{local("Color type must be enabled, disabled, locked, level, or token.");return 0;}}save();return status();}
    private static Integer parseColor(String raw){try{String value=raw.replace("#","");if(value.length()==6)value="FF"+value;if(value.length()!=8)return null;return(int)Long.parseLong(value,16);}catch(Exception ignored){return null;}}
    private static int option(String name,String raw){Boolean value=parse(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){
        case"enabled"->cfg.hotmHelper=value;case"highlight"->cfg.hotmHighlightEnabledPerks=value;case"levels","levelstack"->cfg.hotmLevelStackSize=value;case"tokens","tokenstack"->cfg.hotmTokenStackSize=value;
        case"spent"->cfg.hotmPowderSpent=value;case"tenlevels","ten"->cfg.hotmPowderFor10Levels=value;case"current"->cfg.hotmCurrentPowder=value;case"hud"->cfg.hotmHelperHud=value;
        case"hudpowder"->cfg.hotmHudPowder=value;case"hudtokens"->cfg.hotmHudTokens=value;case"hudspent"->cfg.hotmHudSpent=value;case"hudperks"->cfg.hotmHudPerks=value;
        case"hudmaxed"->cfg.hotmHudMaxed=value;case"skymall"->cfg.hotmHudSkyMall=value;case"hidemaxed"->cfg.hotmHideMaxedTooltipDetails=value;default->{local("Unknown HOTM option.");return 0;}}
        save();return status();}
    private static Boolean parse(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
}
