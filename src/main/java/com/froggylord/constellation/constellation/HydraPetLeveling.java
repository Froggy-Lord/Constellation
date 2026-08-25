package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.AuctionApi;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.HydraConfig;
import com.mojang.brigadier.CommandDispatcher;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Feesh (Apache-2.0): events/publishers/PetLevelUpPublisher.kt
// ported from Feesh (Apache-2.0): features/alerts/PetLevelUpAlert.kt
// ported from Feesh (Apache-2.0): features/commands/PetLevelUpPricesCommand.kt
// pet id construction ported from Feesh (Apache-2.0): utils/ItemUtils.kt
public final class HydraPetLeveling {
    public record State(String pet,int level,int rarity,double basePrice,double maxPrice,double profit,boolean baseKnown,boolean maxKnown,boolean alpha,long at) {public boolean profitKnown(){return baseKnown&&maxKnown;}}
    private record Pending(State state,String baseId,String maxId,long deadline) {}
    private record PetInfo(String name,int rarity,int multiplier) {}
    private record PriceRow(PetInfo pet,Double base,Double max,Double profit,Double coinsPerXp) {}
    private static final Pattern LEVEL_UP=Pattern.compile("^Your (.+?) leveled up to level (100|200)!$");
    private static final double MAX_XP=25_353_230.0;
    private static final List<PetInfo> FISHING_PETS=List.of(new PetInfo("Blue Whale",4,1),new PetInfo("Flying Fish",4,1),new PetInfo("Flying Fish",5,1),new PetInfo("Baby Yeti",4,1),new PetInfo("Baby Yeti",5,1),new PetInfo("Penguin",4,1),new PetInfo("Spinosaurus",4,1),new PetInfo("Megalodon",4,1),new PetInfo("Ammonite",4,1),new PetInfo("Squid",4,1),new PetInfo("Dolphin",4,1),new PetInfo("Reindeer",4,2),new PetInfo("Hermit Crab",4,1),new PetInfo("Hermit Crab",5,1),new PetInfo("Seal",4,1));
    private static HydraConfig cfg;
    private static boolean initialized,wasEnabled,priceTablePending;
    private static State last;
    private static Pending pending;
    private static long priceTableDeadline;
    private static String profileKey="";
    private static State pendingHistory;
    private HydraPetLeveling() {}

    public static void init(HydraConfig config){cfg=config;if(initialized)return;initialized=true;ConstellationClient.tick().every(5,"hydra-pet-level",HydraPetLeveling::tick);ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)onChat(message);return true;});ClientPlayConnectionEvents.JOIN.register((a,b,c)->resetConnection());ClientPlayConnectionEvents.DISCONNECT.register((a,b)->resetConnection());}
    private static void tick(){boolean configured=configured();if(!configured){if(wasEnabled)resetTransient();wasEnabled=false;return;}wasEnabled=true;if(!active())return;String profile=profile();if(!profile.isEmpty()&&!profile.equals(profileKey)){if(!profileKey.isEmpty()){pending=null;last=null;pendingHistory=null;}profileKey=profile;}if(pendingHistory!=null&&!profileKey.isEmpty()){storeHistory(pendingHistory,profileKey);pendingHistory=null;}resolvePending();if(priceTablePending)resolvePriceTable();}

    // ported from Feesh (Apache-2.0): events/publishers/PetLevelUpPublisher.kt
    private static void onChat(Component component){if(!active())return;String plain=clean(component.getString());Matcher matcher=LEVEL_UP.matcher(plain);if(!matcher.matches())return;String pet=matcher.group(1).trim();int level=Integer.parseInt(matcher.group(2));if(pet.isEmpty()||(level==100&&!cfg.petLevelAlert100)||(level==200&&!cfg.petLevelAlert200))return;int color=nameColor(component,pet),rarity=rarity(color);State state=new State(pet,level,rarity,0,0,0,false,false,onAlpha(),System.currentTimeMillis());last=state;showAlert(state,color);if(cfg.petLevelHistory){String profile=profile().isEmpty()?profileKey:profile();if(profile.isEmpty())pendingHistory=state;else storeHistory(state,profile);}if(cfg.petLevelPrice&&rarity>=0){String base=petId(pet,rarity),max=base+"+"+level;PriceProvider.warm(base);PriceProvider.warm(max);pending=new Pending(state,base,max,System.currentTimeMillis()+Math.clamp(cfg.petLevelPriceWaitSeconds,1,60)*1000L);resolvePending();}}
    private static int nameColor(Component component,String pet){final int[] out={-1};component.visit((style,text)->{if(out[0]>=0||style.getColor()==null||!clean(text).contains(pet))return java.util.Optional.empty();out[0]=style.getColor().getValue();return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);return out[0];}
    private static int rarity(int color){return switch(color){case 0xFFFFFF->0;case 0x55FF55->1;case 0x5555FF->2;case 0xAA00AA->3;case 0xFFAA00->4;case 0xFF55FF->5;case 0x55FFFF->6;default->-1;};}
    private static String petId(String pet,int rarity){return pet.trim().replace(' ','_').toUpperCase(Locale.ROOT)+";"+rarity;}

    // ported from Feesh (Apache-2.0): features/alerts/PetLevelUpAlert.kt
    private static void showAlert(State state,int color){Minecraft mc=Minecraft.getInstance();if(mc.player==null||!cfg.petLevelAlert)return;String message=format(cfg.petLevelAlertTemplate,state);if(cfg.petLevelAlertTitle){mc.gui.hud.resetTitleTimes();mc.gui.hud.setTitle(Component.literal(state.pet+" is maxed").withColor((color<0?cfg.petLevelAlertColor:0xFF000000|color)&0xFFFFFF));mc.gui.hud.setSubtitle(Component.literal("Level "+state.level));}if(cfg.petLevelAlertChat)local(message);if(cfg.petLevelAlertSound)mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(),.9f,1.1f);}
    private static void resolvePending(){if(pending==null)return;Double max=AuctionApi.getLbin(pending.maxId),base=AuctionApi.getLbin(pending.baseId);long now=System.currentTimeMillis();boolean maxKnown=max!=null&&max>0,baseKnown=base!=null&&base>0;if((!maxKnown||!baseKnown)&&now<pending.deadline)return;if(!maxKnown){pending=null;return;}double baseValue=baseKnown?base:0,profit=baseKnown?max-baseValue:0;State priced=new State(pending.state.pet,pending.state.level,pending.state.rarity,baseValue,max,profit,baseKnown,true,pending.state.alpha,pending.state.at);last=priced;pending=null;if(cfg.petLevelPriceChat&&(!priced.profitKnown()||cfg.petLevelPriceShowNegative||profit>=0))local(priceMessage(priced));}
    private static String priceMessage(State state){if(cfg.petLevelPriceShowBase&&cfg.petLevelPriceShowMaxed&&cfg.petLevelPriceShowProfit)return format(cfg.petLevelPriceTemplate,state);List<String> parts=new ArrayList<>();if(cfg.petLevelPriceShowBase)parts.add("level 1 "+(state.baseKnown?coins(state.basePrice):"unknown"));if(cfg.petLevelPriceShowMaxed)parts.add("maxed "+(state.maxKnown?coins(state.maxPrice):"unknown"));if(cfg.petLevelPriceShowProfit)parts.add("leveling profit "+(state.profitKnown()?coins(state.profit):"unknown"));return state.pet+(parts.isEmpty()?" reached level "+state.level:"; "+String.join(", ",parts)+".");}
    private static String format(String template,State state){String value=template==null?"":template;return value.replace("{pet}",state.pet).replace("{level}",Integer.toString(state.level)).replace("{rarity}",rarityName(state.rarity)).replace("{base}",state.baseKnown?coins(state.basePrice):"unknown").replace("{maxed}",state.maxKnown?coins(state.maxPrice):"unknown").replace("{profit}",state.profitKnown()?coins(state.profit):"unknown");}

    // ported from Feesh (Apache-2.0): features/commands/PetLevelUpPricesCommand.kt
    private static void requestPrices(){if(!active()){local("You must be on Hypixel SkyBlock.");return;}for(PetInfo pet:FISHING_PETS){String base=petId(pet.name,pet.rarity);PriceProvider.warm(base);PriceProvider.warm(base+"+100");}priceTablePending=true;priceTableDeadline=System.currentTimeMillis()+Math.clamp(cfg.petLevelPriceTableWaitSeconds,30,300)*1000L;local("Loading fishing pet level-up prices...");resolvePriceTable();}
    private static void resolvePriceTable(){List<PriceRow> rows=new ArrayList<>();boolean fetching=false;for(PetInfo pet:FISHING_PETS){String id=petId(pet.name,pet.rarity),maxId=id+"+100";Double base=validPrice(AuctionApi.getLbin(id)),max=validPrice(AuctionApi.getLbin(maxId));fetching|=AuctionApi.isFetching(id)||AuctionApi.isFetching(maxId);Double profit=base==null||max==null?null:max-base;rows.add(new PriceRow(pet,base,max,profit,profit==null?null:profit/MAX_XP*pet.multiplier));}if(fetching&&System.currentTimeMillis()<priceTableDeadline)return;priceTablePending=false;rows.sort(Comparator.comparing((PriceRow r)->r.coinsPerXp==null?Double.NEGATIVE_INFINITY:r.coinsPerXp).reversed());local("Fishing pet level-up prices:");for(PriceRow row:rows){String rarity=rarityName(row.pet.rarity),profit=row.profit==null?"N/A":coins(row.profit),base=row.base==null?"N/A":coins(row.base),max=row.max==null?"N/A":coins(row.max),per=row.coinsPerXp==null?"N/A":String.format(Locale.ROOT,"%.2f",row.coinsPerXp);local(row.pet.name+" ("+rarity+"): "+profit+" ("+base+" -> "+max+") | "+per+" coins/XP");}}
    private static Double validPrice(Double value){return value!=null&&value>0?value:null;}

    private static void storeHistory(State state,String profile){if(cfg.petLevelHistoryExcludeAlpha&&state.alpha)return;String key=profile+"|"+state.pet.toUpperCase(Locale.ROOT).replace(' ','_')+"|"+state.rarity+"|"+state.level;cfg.petLevelHistoryCounts.merge(key,1,Integer::sum);cfg.petLevelHistoryTimes.put(key,state.at);ConstellationClient.saveConfig();}
    public static State state(){return visible()?last:null;}
    public static boolean visible(){return active()&&cfg.petLevelHud&&last!=null&&System.currentTimeMillis()-last.at<=Math.clamp(cfg.petLevelHudSeconds,5,300)*1000L;}
    public static HydraConfig config(){return cfg;}
    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.petLevelSuite;}
    private static boolean active(){return configured()&&ConstellationClient.loc().onHypixel();}
    private static boolean onAlpha(){return ConstellationClient.loc().getSidebarLines().stream().anyMatch(s->s.toLowerCase(Locale.ROOT).contains("alpha.hypixel.net"));}
    private static String profile(){String value=LyraStorageValue.currentProfileKey();return value==null?"":value.trim();}
    private static String rarityName(int rarity){return switch(rarity){case 0->"Common";case 1->"Uncommon";case 2->"Rare";case 3->"Epic";case 4->"Legendary";case 5->"Mythic";case 6->"Divine";default->"Unknown";};}
    public static String coins(double value){double abs=Math.abs(value);String suffix="";double shown=abs;if(abs>=1_000_000_000){shown=abs/1_000_000_000;suffix="b";}else if(abs>=1_000_000){shown=abs/1_000_000;suffix="m";}else if(abs>=1_000){shown=abs/1_000;suffix="k";}String number=shown>=100||suffix.isEmpty()?String.format(Locale.ROOT,"%.0f",shown):shown>=10?String.format(Locale.ROOT,"%.1f",shown):String.format(Locale.ROOT,"%.2f",shown);return(value<0?"-":"")+number+suffix;}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim();}
    private static void resetConnection(){profileKey="";wasEnabled=false;pendingHistory=null;resetTransient();}
    private static void resetTransient(){last=null;pending=null;priceTablePending=false;priceTableDeadline=0;}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> d){d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("petlevel").executes(c->status()).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("prices").executes(c->{requestPrices();return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{resetTransient();local("Pet-level display cleared.");return 1;})).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resethistory").executes(c->resetHistory())).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));}
    private static int status(){int count=historyCount();local("Recorded "+count+" max-level pet event"+(count==1?"":"s")+" for this profile; alert "+on(cfg.petLevelAlert)+", prices "+on(cfg.petLevelPrice)+", HUD "+on(cfg.petLevelHud)+".");return 1;}
    private static int historyCount(){String prefix=profile()+"|";return cfg.petLevelHistoryCounts.entrySet().stream().filter(e->e.getKey().startsWith(prefix)).mapToInt(java.util.Map.Entry::getValue).sum();}
    private static int resetHistory(){String profile=profile();if(profile.isEmpty()){local("SkyBlock profile is not available yet.");return 0;}String prefix=profile+"|";cfg.petLevelHistoryCounts.keySet().removeIf(k->k.startsWith(prefix));cfg.petLevelHistoryTimes.keySet().removeIf(k->k.startsWith(prefix));save();return status();}
    private static int option(String name,String raw){Boolean value=switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"alert"->cfg.petLevelAlert=value;case"price"->cfg.petLevelPrice=value;case"hud"->cfg.petLevelHud=value;case"history"->cfg.petLevelHistory=value;case"level100"->cfg.petLevelAlert100=value;case"level200"->cfg.petLevelAlert200=value;case"negative"->cfg.petLevelPriceShowNegative=value;default->{local("Option must be alert, price, hud, history, level100, level200, or negative.");return 0;}}save();return status();}
    private static String on(boolean value){return value?"on":"off";}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("\u00a75[Pet Level] \u00a7f"+text));}
    private static void save(){ConstellationClient.saveConfig();}
}
