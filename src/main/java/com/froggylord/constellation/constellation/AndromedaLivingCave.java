package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AndromedaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/livingcave/LivingCaveLivingMetalHelper.kt, LivingCaveDefenseBlocks.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/livingcave/LivingCaveSnakeFeatures.kt, snake/LivingCaveSnake.kt, LivingMetalSuitProgress.kt
public final class AndromedaLivingCave {
    public record SuitRow(String label,String value,int color){}
    private record MetalPair(BlockPos from,BlockPos to,long started){}
    private static final class Defense{final RemotePlayer entity;Vec3 pos;long expires;boolean hidden,placed;Defense(RemotePlayer entity,Vec3 pos,long expires){this.entity=entity;this.pos=pos;this.expires=expires;}}
    private enum SnakeState{SPAWNING,ACTIVE,BLOCKED,CALM}
    private static final class Snake{final List<BlockPos> blocks=new ArrayList<>();long lastAdd,lastRemove,invalidHeadSince;BlockPos lastBroken;SnakeState state=SnakeState.SPAWNING;Snake(BlockPos pos){blocks.add(pos);lastAdd=System.currentTimeMillis();}BlockPos head(){return blocks.getFirst();}BlockPos tail(){return blocks.getLast();}}
    private static final Set<String> PICKAXES=Set.of("SELF_RECURSIVE_PICKAXE","ANTI_SENTIENT_PICKAXE","EON_PICKAXE","CHRONO_PICKAXE");
    private static final Set<String> DEFENSE_NAMES=Set.of("Autonull","Autocap","Autochest","Autopants","Autoboots");
    private static final List<Defense> MOVING=new ArrayList<>(),STATIC=new ArrayList<>();
    private static final List<Snake> SNAKES=new ArrayList<>();
    private static final Map<BlockPos,Block> ORIGINAL=new HashMap<>();
    private static AndromedaConfig cfg;private static boolean initialized;private static BlockPos lastClicked,lastSnakeClicked;private static MetalPair metal;private static Snake selected;private static Object levelIdentity;
    private AndromedaLivingCave(){}

    public static void init(AndromedaConfig config){
        cfg=config;if(initialized)return;initialized=true;
        ConstellationClient.tick().every(1,"andromeda-living-cave",AndromedaLivingCave::tick);
        ConstellationClient.instance().packets().register(packet->{if(packet instanceof BlockStateUpdate update)onBlock(update);});
        AttackBlockCallback.EVENT.register((player,level,hand,pos,direction)->{if(active()&&inLiving()){lastSnakeClicked=pos.immutable();if(level.getBlockState(pos).getBlock()==Blocks.LAPIS_ORE||level.getBlockState(pos).getBlock()==Blocks.DEEPSLATE_LAPIS_ORE)lastClicked=pos.immutable();selectSnake(pos);}return InteractionResult.PASS;});
        ClientPlayConnectionEvents.JOIN.register((a,b,c)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a,b)->reset());
    }
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.level!=levelIdentity){levelIdentity=mc.level;resetTransient();}
        if(!active()||!inLiving()){resetTransient();return;}long now=System.currentTimeMillis();
        MOVING.removeIf(value->now>value.expires+2000||!value.entity.isAlive());STATIC.removeIf(value->!value.entity.isAlive());
        if(metal!=null&&now-metal.started>Math.clamp(cfg.livingMetalExpiryMillis,500,10000))metal=null;
        tickSnakes(mc,now);selectLookedSnake(mc);
    }
    private static void onBlock(BlockStateUpdate update){
        if(!active()||!inLiving())return;BlockPos pos=update.pos();Block old=update.oldState().getBlock(),next=update.newState().getBlock();
        if(cfg.livingMetalHelper){
            if(isLapisOre(old)&&metal!=null&&metal.to.equals(pos))metal=null;
            if(isLapisOre(next)&&lastClicked!=null&&Math.sqrt(lastClicked.distSqr(pos))<2&&pos.distToCenterSqr(Minecraft.getInstance().player.position())<49)metal=new MetalPair(lastClicked,pos,System.currentTimeMillis());
        }
        if(cfg.livingDefenseBlocks){
            if(old==Blocks.AIR&&(next==Blocks.DIAMOND_BLOCK||next.toString().contains("stained_glass"))){Defense moving=nearestDefense(MOVING,Vec3.atLowerCornerOf(pos),15);if(moving!=null){MOVING.remove(moving);moving.pos=Vec3.atLowerCornerOf(pos);moving.placed=true;STATIC.add(moving);}}
            if(next==Blocks.AIR){Defense placed=nearestDefense(STATIC,Vec3.atLowerCornerOf(pos),1.5);if(placed!=null)STATIC.remove(placed);}
        }
        if(cfg.livingSnakeHelper){
            if(next==Blocks.LAPIS_BLOCK){ORIGINAL.putIfAbsent(pos,old);addSnakeBlock(pos);}
            else if(ORIGINAL.get(pos)==next){ORIGINAL.remove(pos);removeSnakeBlock(pos);}
        }
    }
    public static boolean onParticle(ClientboundLevelParticlesPacket packet){
        if(!active()||!inLiving())return false;Vec3 pos=new Vec3(packet.getX(),packet.getY(),packet.getZ());boolean hide=false;
        if(cfg.livingMetalHelper&&cfg.livingMetalHideParticles&&metal!=null&&Vec3.atCenterOf(metal.to).distanceTo(pos)<3)hide=true;
        if(cfg.livingDefenseBlocks){
            boolean near=nearestDefense(STATIC,pos.add(-.5,0,-.5),3)!=null||nearestDefense(MOVING,pos.add(-.5,0,-.5),3)!=null;if(cfg.livingDefenseHideParticles&&near)hide=true;
            if(packet.getParticle().getType()==ParticleTypes.ENCHANTED_HIT){Vec3 corrected=pos.add(-.5,0,-.5);Defense prior=nearestDefense(MOVING,corrected,.5);RemotePlayer entity=prior==null?nearestDefenseMob(pos.add(-.5,-1.5,-.5)):prior.entity;if(entity!=null){if(prior!=null)prior.hidden=true;else MOVING.add(new Defense(entity,corrected,System.currentTimeMillis()+250));if(cfg.livingDefenseHideParticles)hide=true;}}
        }
        return hide;
    }
    private static RemotePlayer nearestDefenseMob(Vec3 pos){Minecraft mc=Minecraft.getInstance();if(mc.level==null)return null;return mc.level.getEntitiesOfClass(RemotePlayer.class,new AABB(pos,pos).inflate(2),player->DEFENSE_NAMES.contains(clean(player.getName().getString()))&&player.getHealth()<player.getMaxHealth()).stream().min(Comparator.comparingDouble(player->player.position().distanceToSqr(pos))).orElse(null);}
    private static Defense nearestDefense(List<Defense> list,Vec3 pos,double range){double max=range*range;return list.stream().filter(value->value.pos.distanceToSqr(pos)<max).min(Comparator.comparingDouble(value->value.pos.distanceToSqr(pos))).orElse(null);}
    public static void onTitle(Component title){if(active()&&cfg.livingMetalHelper&&clean(title.getString()).contains("Living Metal"))metal=null;}

    private static void addSnakeBlock(BlockPos pos){
        List<Snake> found=SNAKES.stream().filter(snake->Math.sqrt(snake.head().distSqr(pos))<1.74).sorted(Comparator.comparingDouble(snake->snake.head().distSqr(pos))).toList();Snake snake=found.stream().filter(value->value.state!=SnakeState.CALM).findFirst().orElse(found.isEmpty()?null:found.getFirst());if(snake==null){SNAKES.add(new Snake(pos));return;}if(snake.blocks.contains(pos))return;snake.blocks.addFirst(pos);snake.lastAdd=System.currentTimeMillis();snake.invalidHeadSince=0;
    }
    private static void removeSnakeBlock(BlockPos pos){
        for(Iterator<Snake> iterator=SNAKES.iterator();iterator.hasNext();){Snake snake=iterator.next();if(!snake.blocks.contains(pos))continue;if(pos.equals(snake.head())&&pos.equals(lastSnakeClicked)&&snake.blocks.size()>1)return;snake.blocks.remove(pos);if(snake.blocks.isEmpty()){iterator.remove();if(selected==snake)selected=null;return;}if(snake.state==SnakeState.SPAWNING)snake.state=SnakeState.ACTIVE;snake.lastRemove=System.currentTimeMillis();snake.lastBroken=pos;return;}
    }
    private static void tickSnakes(Minecraft mc,long now){
        for(Iterator<Snake> iterator=SNAKES.iterator();iterator.hasNext();){Snake snake=iterator.next();boolean invalidShape=snake.blocks.isEmpty();for(int i=1;i<snake.blocks.size()&&!invalidShape;i++)invalidShape=Math.sqrt(snake.blocks.get(i-1).distSqr(snake.blocks.get(i)))>3;if(invalidShape){iterator.remove();continue;}
            if(mc.level.getBlockState(snake.head()).getBlock()!=Blocks.LAPIS_BLOCK){if(snake.invalidHeadSince==0)snake.invalidHeadSince=now;if(now-snake.invalidHeadSince>1000){iterator.remove();continue;}}else snake.invalidHeadSince=0;
            if(snake.state==SnakeState.SPAWNING)continue;boolean blocked=snake.blocks.stream().anyMatch(pos->{for(Direction direction:Direction.values())if(mc.level.getBlockState(pos.relative(direction)).isAir())return false;return true;});snake.state=blocked?SnakeState.BLOCKED:now-snake.lastAdd>200?SnakeState.CALM:SnakeState.ACTIVE;
        }
        if(selected!=null&&!SNAKES.contains(selected))selected=null;
    }
    private static void selectSnake(BlockPos pos){selected=SNAKES.stream().filter(snake->snake.blocks.contains(pos)).findFirst().orElse(selected);}
    private static void selectLookedSnake(Minecraft mc){if(!(mc.hitResult instanceof BlockHitResult hit))return;Snake looked=SNAKES.stream().filter(snake->snake.blocks.contains(hit.getBlockPos())).findFirst().orElse(null);if(looked!=null)selected=looked;}

    public static void draw(WorldRenderer.Ctx ctx){
        if(!active()||!inLiving())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;drawMetal(ctx,mc);drawDefense(ctx,mc);drawSnakes(ctx,mc);
    }
    private static void drawMetal(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.livingMetalHelper||metal==null||metal.to.distToCenterSqr(mc.player.position())>Math.pow(Math.clamp(cfg.livingMetalRange,5,30),2))return;long age=System.currentTimeMillis()-metal.started;double t=Math.clamp(age/(double)Math.clamp(cfg.livingMetalAnimationMillis,100,2000),0,1);Vec3 from=Vec3.atCenterOf(metal.from),to=Vec3.atCenterOf(metal.to),animated=from.lerp(to,t);AABB box=new AABB(animated.x-.45,animated.y-.45,animated.z-.45,animated.x+.45,animated.y+.45,animated.z+.45);if(cfg.livingMetalBox)ctx.highlight(box,cfg.livingMetalColor,cfg.livingMetalThroughWalls);if(cfg.livingMetalLine)ctx.line(from,to,cfg.livingMetalColor,cfg.livingMetalThroughWalls);
    }
    private static void drawDefense(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.livingDefenseBlocks)return;double range=Math.clamp(cfg.livingDefenseRange,10,100),rangeSq=range*range;long now=System.currentTimeMillis();
        for(Defense block:MOVING){if(block.hidden||block.pos.distanceToSqr(mc.player.position())>rangeSq||now>block.expires)continue;AABB box=new AABB(block.pos.x,block.pos.y,block.pos.z,block.pos.x+1,block.pos.y+1,block.pos.z+1);if(cfg.livingDefenseMovingBox)ctx.highlight(box,cfg.livingDefenseColor,cfg.livingDefenseThroughWalls);if(cfg.livingDefenseCrosshairLine)ctx.line(mc.player.getEyePosition(),block.pos.add(.5,.5,.5),cfg.livingDefenseColor,false);}
        for(Defense block:STATIC){if(block.pos.distanceToSqr(mc.player.position())>rangeSq)continue;AABB box=new AABB(block.pos.x,block.pos.y,block.pos.z,block.pos.x+1,block.pos.y+1,block.pos.z+1);if(cfg.livingDefenseMovingBox)ctx.highlight(box,cfg.livingDefenseColor,cfg.livingDefenseThroughWalls);if(cfg.livingDefensePlayerLine)ctx.line(block.entity.position().add(0,.5,0),block.pos.add(.5,.5,.5),cfg.livingDefenseColor,cfg.livingDefenseThroughWalls);if(cfg.livingDefenseLabel)ctx.label(block.pos.add(.5,1.2,.5),"Break!",cfg.livingDefenseColor,false);}
    }
    private static void drawSnakes(WorldRenderer.Ctx ctx,Minecraft mc){
        if(!cfg.livingSnakeHelper)return;String id=LyraTooltips.marketId(mc.player.getMainHandItem());boolean calm=id.equals("FROZEN_WATER_PUNGI"),breaking=PICKAXES.contains(id);if(!calm&&!breaking)return;double range=Math.clamp(cfg.livingSnakeRange,10,100),rangeSq=range*range;
        for(Snake snake:SNAKES){if(snake.blocks.isEmpty()||snake.head().distToCenterSqr(mc.player.position())>rangeSq)continue;int color=snakeColor(snake.state);if(cfg.livingSnakeOutlineAll)for(BlockPos pos:snake.blocks)ctx.outline(new AABB(pos),color,false);BlockPos target=breaking&&snake.state==SnakeState.CALM&&snake.blocks.size()>1?snake.tail():snake.head();if(cfg.livingSnakeHighlightTarget)ctx.highlight(new AABB(target),color,cfg.livingSnakeThroughWalls);if(cfg.livingSnakeLabel&&snake==selected){String label=switch(snake.state){case SPAWNING->"Spawning snake";case ACTIVE->"Active snake";case BLOCKED->"Not touching air";case CALM->"Calm snake";};if(cfg.livingSnakeBlockCount)label+=" · "+snake.blocks.size()+" blocks";ctx.label(Vec3.atCenterOf(target).add(0,.8,0),label,color,cfg.livingSnakeThroughWalls);}}
    }
    private static int snakeColor(SnakeState state){return switch(state){case SPAWNING->cfg.livingSnakeSpawningColor;case ACTIVE->cfg.livingSnakeActiveColor;case BLOCKED->cfg.livingSnakeBlockedColor;case CALM->cfg.livingSnakeCalmColor;};}

    public static boolean suitVisible(){return active()&&cfg.livingMetalSuitHud&&!suitRows().isEmpty();}
    public static List<SuitRow> suitRows(){
        if(cfg==null||!cfg.livingMetalSuitHud)return List.of();Minecraft mc=Minecraft.getInstance();if(mc.player==null)return List.of();List<Map.Entry<String,Integer>> pieces=new ArrayList<>();
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){ItemStack stack=mc.player.getItemBySlot(slot);Integer progress=livingProgress(stack);if(progress!=null||clean(stack.getHoverName().getString()).contains("Living Metal"))pieces.add(Map.entry(pieceName(slot),progress==null?0:Math.clamp(progress,0,100)));}
        if(pieces.isEmpty())return List.of();double total=pieces.stream().mapToInt(Map.Entry::getValue).average().orElse(0);boolean maxed=pieces.size()==4&&pieces.stream().allMatch(entry->entry.getValue()>=100);List<SuitRow> rows=new ArrayList<>();rows.add(new SuitRow("Total",maxed?"MAXED":String.format(Locale.ROOT,"%.1f%%",total),0xFF55FF55));if(cfg.livingMetalSuitCompactMaxed&&maxed||!cfg.livingMetalSuitPieces)return List.copyOf(rows);
        for(var piece:pieces){int progress=piece.getValue();String value="";if(cfg.livingMetalSuitBars){int length=Math.clamp(cfg.livingMetalSuitBarLength,5,30),filled=(int)Math.floor(progress/100.0*length);value="|".repeat(filled)+"-".repeat(length-filled);}if(cfg.livingMetalSuitPercent)value+=(value.isEmpty()?"":" ")+progress+"%";rows.add(new SuitRow(piece.getKey(),value,progress>=100?0xFF55FF55:0xFF55FFFF));}return List.copyOf(rows);
    }
    private static Integer livingProgress(ItemStack stack){if(stack==null||stack.isEmpty())return null;CustomData data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return null;CompoundTag root=data.copyTag(),extra=root.getCompoundOrEmpty("ExtraAttributes");if(extra.isEmpty())extra=root;return extra.contains("lm_evo")?extra.getIntOr("lm_evo",0):null;}
    private static String pieceName(EquipmentSlot slot){return switch(slot){case HEAD->"Helmet";case CHEST->"Chestplate";case LEGS->"Leggings";case FEET->"Boots";default->slot.getName();};}
    private static boolean isLapisOre(Block block){return block==Blocks.LAPIS_ORE||block==Blocks.DEEPSLATE_LAPIS_ORE;}
    private static boolean active(){return cfg!=null&&cfg.enabled&&ConstellationClient.loc().area()==SkyblockArea.THE_RIFT;}
    private static boolean inLiving(){String area=AndromedaRiftCore.currentArea();return area.equalsIgnoreCase("Living Cave")||area.equalsIgnoreCase("Living Stillness");}
    private static String clean(String value){String clean=ChatFormatting.stripFormatting(value);return clean==null?"":clean.trim();}
    private static void reset(){levelIdentity=null;resetTransient();}
    private static void resetTransient(){lastClicked=null;lastSnakeClicked=null;metal=null;selected=null;MOVING.clear();STATIC.clear();SNAKES.clear();ORIGINAL.clear();}
    private static void save(){ConstellationClient.saveConfig();}
    private static void local(String text){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§5[Living Cave] §f"+text));}

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher){
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("livingcave").executes(c->status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c->status()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c->{resetTransient();return status();}))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("number").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value",IntegerArgumentType.integer(0)).executes(c->number(StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("name",StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("state",StringArgumentType.word()).executes(c->option(StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"state")))))));
    }
    private static int status(){local("Metal "+(cfg.livingMetalHelper?"on":"off")+", defense "+(cfg.livingDefenseBlocks?"on":"off")+", snakes "+SNAKES.size()+", suit HUD "+(cfg.livingMetalSuitHud?"on":"off")+".");return 1;}
    private static int number(String name,int value){switch(name.toLowerCase(Locale.ROOT)){case"metalrange"->cfg.livingMetalRange=Math.clamp(value,5,30);case"animation"->cfg.livingMetalAnimationMillis=Math.clamp(value,100,2000);case"expiry"->cfg.livingMetalExpiryMillis=Math.clamp(value,500,10000);case"defenserange"->cfg.livingDefenseRange=Math.clamp(value,10,100);case"snakerange"->cfg.livingSnakeRange=Math.clamp(value,10,100);case"barlength"->cfg.livingMetalSuitBarLength=Math.clamp(value,5,30);default->{local("Unknown Living Cave number.");return 0;}}save();return status();}
    private static int option(String name,String state){Boolean value=switch(state.toLowerCase(Locale.ROOT)){case"on","true","yes","1"->true;case"off","false","no","0"->false;default->null;};if(value==null){local("State must be on or off.");return 0;}switch(name.toLowerCase(Locale.ROOT)){case"metal"->cfg.livingMetalHelper=value;case"metalparticles"->cfg.livingMetalHideParticles=value;case"defense"->cfg.livingDefenseBlocks=value;case"defenseparticles"->cfg.livingDefenseHideParticles=value;case"snakes"->cfg.livingSnakeHelper=value;case"snakeoutline"->cfg.livingSnakeOutlineAll=value;case"suit"->cfg.livingMetalSuitHud=value;case"compact"->cfg.livingMetalSuitCompactMaxed=value;case"pieces"->cfg.livingMetalSuitPieces=value;case"bars"->cfg.livingMetalSuitBars=value;case"percent"->cfg.livingMetalSuitPercent=value;default->{local("Unknown Living Cave option.");return 0;}}save();return status();}
}
