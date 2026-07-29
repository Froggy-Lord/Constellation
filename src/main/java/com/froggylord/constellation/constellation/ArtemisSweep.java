package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.ArtemisConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-only): skyblock/foraging/SweepOverlay.java
// ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/{SweepDetailsListener,SweepDetailsHudWidget}.java
// ported from SkyHanni (LGPL-3.0-or-later): features/foraging/CompactSweepDetails.kt
public final class ArtemisSweep {
    public record State(String tree,double toughness,double maxSweep,double finalSweep,double logs,boolean thrown,double throwPenalty,boolean stylePenalty,double stylePenaltyAmount,String correctStyle,long at){}
    private record Prediction(List<BlockPos> logs,BlockPos target,boolean thrown,float sweep,float toughness){}
    private static final Set<String> VALID_AXES=Set.of("JUNGLE_AXE","TREECAPITATOR_AXE","FIG_AXE","FIGSTONE_AXE","ROOKIE_AXE","PROMISING_AXE","SWEET_AXE","EFFICIENT_AXE");
    private static final Set<String> THROWABLE_AXES=Set.of("FIG_AXE","FIGSTONE_AXE","JUNGLE_AXE","TREECAPITATOR_AXE");
    private static final Map<Block,Float> TOUGHNESS=Map.of(Blocks.STRIPPED_SPRUCE_LOG,7f,Blocks.STRIPPED_SPRUCE_WOOD,7f,Blocks.MANGROVE_LOG,50f,Blocks.MANGROVE_WOOD,50f);
    private static final Pattern SWEEP_STAT=Pattern.compile("^Sweep:\\s*[^\\d]*([\\d,.]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern DETAILS=Pattern.compile("^Sweep Details: ([\\d,.]+).*?Sweep$",Pattern.CASE_INSENSITIVE);
    private static final Pattern TREE=Pattern.compile("^(.+?) Tree Toughness: ([\\d,.]+) ([\\d,.]+) Logs$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PENALTY=Pattern.compile("^(Axe throw|Wrong Style): -(\\d+(?:\\.\\d+)?)% Sweep ([\\d,.]+) Logs(?: (.+?))?!*$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Long> COOLDOWNS=new HashMap<>();
    private static ArtemisConfig cfg;
    private static boolean initialized,inside,missingNotice;
    private static double maxSweep=-1,finalSweep=-1,logs=-1,toughness=-1,throwPenalty=-1,stylePenalty=-1;
    private static String tree="",correctStyle="";
    private static long detailsAt;
    private static State state;

    private ArtemisSweep(){}

    public static void init(ArtemisConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay||chat(message));
        ConstellationClient.tick().every(1,"artemis-sweep-details",ArtemisSweep::expireDetails);
        UseItemCallback.EVENT.register((player,level,hand)->{
            String id=itemId(player.getItemInHand(hand));
            if(activeArea()&&THROWABLE_AXES.contains(id))COOLDOWNS.put(id,System.currentTimeMillis()+1000);
            return InteractionResult.PASS;
        });
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }

    public static void draw(WorldRenderer.Ctx ctx){
        if(!configured()||!cfg.sweepBlockOverlay||!activeArea())return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        ItemStack held=mc.player.getMainHandItem();String id=itemId(held);if(!VALID_AXES.contains(id))return;
        Prediction prediction=predict(mc,id);if(prediction==null)return;
        int color=prediction.thrown?cfg.sweepThrownColor:cfg.sweepOverlayColor;
        for(BlockPos pos:prediction.logs)ctx.box(new AABB(pos),color,cfg.sweepThroughWalls);
        Vec3 target=Vec3.atCenterOf(prediction.target);
        if(cfg.sweepShowTarget)ctx.outline(new AABB(prediction.target),cfg.sweepTargetColor,cfg.sweepThroughWalls);
        if(cfg.sweepShowCountLabel){
            String label=prediction.logs.size()+" logs";
            if(cfg.sweepShowToughness)label+=" | "+number(prediction.toughness)+" toughness";
            ctx.label(target.add(0,.8,0),label,cfg.sweepTargetColor,cfg.sweepThroughWalls);
        }
    }

    private static Prediction predict(Minecraft mc,String id){
        BlockHitResult hit=null;boolean thrown=false;
        if(mc.hitResult instanceof BlockHitResult block&&block.getType()==HitResult.Type.BLOCK&&isLog(mc.level.getBlockState(block.getBlockPos())))hit=block;
        else if(cfg.sweepThrownOverlay&&THROWABLE_AXES.contains(id)&&(!cfg.sweepRespectThrownCooldown||COOLDOWNS.getOrDefault(id,0L)<=System.currentTimeMillis())){
            Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getViewVector(1).scale(Math.clamp(cfg.sweepThrownRange,8,80)));
            HitResult result=mc.level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            if(result instanceof BlockHitResult block&&block.getType()==HitResult.Type.BLOCK&&isLog(mc.level.getBlockState(block.getBlockPos()))){hit=block;thrown=true;}
        }
        if(hit==null)return null;float sweep=sweepStat();if(sweep<=0)return null;
        BlockState target=mc.level.getBlockState(hit.getBlockPos());float toughness=TOUGHNESS.getOrDefault(target.getBlock(),1f);
        int maximum=maxLogs(sweep,toughness);if(thrown)maximum/=2;if(maximum<=0)return null;
        Set<BlockPos> visited=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();List<BlockPos> result=new ArrayList<>();
        queue.add(hit.getBlockPos());visited.add(hit.getBlockPos());
        while(!queue.isEmpty()&&result.size()<maximum){
            BlockPos pos=queue.removeFirst();if(!isLog(mc.level.getBlockState(pos)))continue;result.add(pos);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                if(x==0&&y==0&&z==0)continue;BlockPos next=pos.offset(x,y,z);
                if(visited.add(next)&&isLog(mc.level.getBlockState(next)))queue.addLast(next);
            }
        }
        return new Prediction(List.copyOf(result),hit.getBlockPos(),thrown,sweep,toughness);
    }

    private static int maxLogs(float sweep,float toughness){
        double amount=toughness<=0?sweep:.515+3.245*Math.log(sweep-Math.sqrt(toughness)+.646)-1.708*Math.log(toughness);
        if(!Double.isFinite(amount))return 0;return Math.max(0,(int)Math.ceil(Math.min(Math.clamp(cfg.sweepMaximumLogs,1,35),amount)));
    }

    private static float sweepStat(){
        for(String line:TabList.lines()){Matcher matcher=SWEEP_STAT.matcher(line);if(matcher.find())try{return Float.parseFloat(matcher.group(1).replace(",",""));}catch(Exception ignored){}}
        if(cfg.sweepMissingStatNotice&&!missingNotice&&(ConstellationClient.loc().area()==SkyblockArea.GALATEA||ConstellationClient.loc().area()==SkyblockArea.PARK)){
            missingNotice=true;local("Sweep stat missing. Add Sweep to the player list with /tablist.");
        }
        return 0;
    }

    private static boolean chat(Component component){
        if(!configured()||!detailsArea())return true;String line=clean(component.getString());Matcher matcher;
        if((matcher=DETAILS.matcher(line)).matches()){
            resetSequence();inside=true;detailsAt=System.currentTimeMillis();maxSweep=parseNumber(matcher.group(1));finalSweep=maxSweep;
            return !cfg.sweepDetailsCompactChat;
        }
        if(inside&&System.currentTimeMillis()-detailsAt>Math.clamp(cfg.sweepDetailsVisibleMillis,500,5000)){finish();return true;}
        if(!inside)return true;
        if((matcher=TREE.matcher(line)).matches()){
            tree=matcher.group(1);toughness=parseNumber(matcher.group(2));logs=parseNumber(matcher.group(3));detailsAt=System.currentTimeMillis();publish(false);
            return !cfg.sweepDetailsCompactChat;
        }
        if((matcher=PENALTY.matcher(line)).matches()){
            double percent=parseNumber(matcher.group(2));logs=parseNumber(matcher.group(3));finalSweep*=1-percent/100;detailsAt=System.currentTimeMillis();
            if(matcher.group(1).equalsIgnoreCase("Axe throw"))throwPenalty=percent;
            else{stylePenalty=percent;correctStyle=matcher.group(4)==null?"":matcher.group(4).trim();}
            publish(false);return !cfg.sweepDetailsCompactChat;
        }
        return true;
    }

    private static void publish(boolean penalty){
        state=new State(tree,toughness,maxSweep,finalSweep,logs,throwPenalty>=0,throwPenalty,stylePenalty>=0,stylePenalty,correctStyle,System.currentTimeMillis());
        if(cfg.sweepDetailsCompactChat&&penalty)sendCompact();
    }
    private static void expireDetails(){if(inside&&System.currentTimeMillis()-detailsAt>Math.clamp(cfg.sweepDetailsVisibleMillis,500,5000))finish();}
    private static void finish(){if(inside){publish(false);if(cfg.sweepDetailsCompactChat)sendCompact();}inside=false;}
    private static void sendCompact(){
        if(state==null)return;String text="Sweep "+number(state.maxSweep)+" -> "+number(state.logs)+" "+(state.tree.isBlank()?"":state.tree+" ")+"logs";
        List<String> details=new ArrayList<>();if(state.thrown)details.add("axe throw -"+number(state.throwPenalty)+"%");if(state.stylePenalty)details.add("wrong style -"+number(state.stylePenaltyAmount)+"%");if(!details.isEmpty())text+=" ("+String.join(", ",details)+")";if(!state.correctStyle.isBlank())text+="; "+state.correctStyle+".";
        local(text);inside=false;
    }

    public static State state(){
        if(!configured()||!detailsArea()||state==null)return null;
        return System.currentTimeMillis()-state.at<=Math.clamp(cfg.sweepDetailsVisibleMillis,500,5000)||cfg.sweepDetailsShowInactive?state:null;
    }
    public static boolean visible(){return cfg!=null&&cfg.enabled&&cfg.sweepHelper&&cfg.sweepDetailsHud&&state()!=null;}
    public static ArtemisConfig config(){return cfg;}
    private static boolean configured(){return cfg!=null&&cfg.enabled&&cfg.sweepHelper;}
    private static boolean activeArea(){return configured()&&switch(ConstellationClient.loc().area()){case GALATEA,PARK,HUB,PRIVATE_ISLAND->true;default->false;};}
    private static boolean detailsArea(){return configured()&&switch(ConstellationClient.loc().area()){case GALATEA,PARK,HUB,GARDEN->true;default->false;};}
    private static boolean isLog(BlockState state){
        return switch(ConstellationClient.loc().area()){
            case GALATEA->state.is(Blocks.STRIPPED_SPRUCE_LOG)||state.is(Blocks.STRIPPED_SPRUCE_WOOD)||state.is(Blocks.MANGROVE_LOG)||state.is(Blocks.MANGROVE_WOOD);
            case HUB->state.is(Blocks.OAK_LOG)||state.is(Blocks.OAK_WOOD);
            default->state.is(BlockTags.LOGS);
        };
    }
    private static String itemId(ItemStack stack){if(stack==null||stack.isEmpty())return"";CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return"";CompoundTag root=data.copyTag(),legacy=root.getCompoundOrEmpty("ExtraAttributes");return(legacy.isEmpty()?root:legacy).getStringOr("id","").toUpperCase(Locale.ROOT);}
    private static String clean(String raw){String value=ChatFormatting.stripFormatting(raw);return value==null?"":value.trim().replaceAll("\\s+"," ");}
    private static double parseNumber(String raw){try{return Double.parseDouble(raw.replace(",",""));}catch(Exception ignored){return-1;}}
    private static String number(double value){if(value<0)return"Unknown";return value==Math.rint(value)?Long.toString((long)value):String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");}
    private static void reset(){COOLDOWNS.clear();missingNotice=false;resetSequence();state=null;}
    private static void resetSequence(){inside=false;maxSweep=finalSweep=logs=toughness=throwPenalty=stylePenalty=-1;tree=correctStyle="";detailsAt=0;}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§6[Sweep] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource>d){
        d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sweephelper").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(c->{reset();local("Sweep state cleared.");return 1;}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("maxlogs").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("amount",IntegerArgumentType.integer(1,35)).executes(c->{cfg.sweepMaximumLogs=IntegerArgumentType.getInteger(c,"amount");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("throwrange").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("blocks",IntegerArgumentType.integer(8,80)).executes(c->{cfg.sweepThrownRange=IntegerArgumentType.getInteger(c,"blocks");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("duration").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("milliseconds",IntegerArgumentType.integer(500,5000)).executes(c->{cfg.sweepDetailsVisibleMillis=IntegerArgumentType.getInteger(c,"milliseconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Helper "+on(cfg.sweepHelper)+", overlay "+on(cfg.sweepBlockOverlay)+", details HUD "+on(cfg.sweepDetailsHud)+", compact chat "+on(cfg.sweepDetailsCompactChat)+".");return 1;}
    private static int option(String name,String raw){Boolean value=parseBool(raw);if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){
        case"enabled"->cfg.sweepHelper=value;case"overlay"->cfg.sweepBlockOverlay=value;case"thrown"->cfg.sweepThrownOverlay=value;case"cooldown"->cfg.sweepRespectThrownCooldown=value;case"target"->cfg.sweepShowTarget=value;case"count"->cfg.sweepShowCountLabel=value;case"toughness"->cfg.sweepShowToughness=value;case"throughwalls"->cfg.sweepThroughWalls=value;case"missingnotice"->cfg.sweepMissingStatNotice=value;case"hud"->cfg.sweepDetailsHud=value;case"compactchat"->cfg.sweepDetailsCompactChat=value;case"tree"->cfg.sweepDetailsShowTree=value;case"detailtoughness"->cfg.sweepDetailsShowToughness=value;case"sweep"->cfg.sweepDetailsShowSweep=value;case"logs"->cfg.sweepDetailsShowLogs=value;case"penalty"->cfg.sweepDetailsShowPenalty=value;case"correctstyle"->cfg.sweepDetailsShowCorrectStyle=value;case"inactive"->cfg.sweepDetailsShowInactive=value;default->{local("Unknown Sweep option.");return 0;}
    }save();if(!cfg.sweepHelper)reset();return status();}
    private static Boolean parseBool(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};}
    private static String on(boolean value){return value?"on":"off";}
}
