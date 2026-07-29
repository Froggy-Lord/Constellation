package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CarpetHighlighter.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/CrystalsChestHighlighter.java
// ported from SkyHanni (LGPL-3.0-or-later): features/mining/powdertracker/PowderChestTimer.kt
// ported from SkyHanni (LGPL-3.0-or-later): config/features/mining/nucleus/PowderChestTimerConfig.kt
public final class AquilaMiningHighlights {
    private static final String CHEST_SPAWN = "You uncovered a treasure chest!";
    private static final Set<BlockPos> CARPETS = new HashSet<>();
    private static final Map<BlockPos, Long> CHESTS = new HashMap<>();
    private static final Map<Vec3, Long> PARTICLES = new HashMap<>();
    private static final Map<BlockPos, Integer> CURRENT_LOCKS = new HashMap<>();
    private static final Map<BlockPos, Integer> NEEDED_LOCKS = new HashMap<>();
    private static final Map<BlockPos, Long> MINED_BLOCKS = new HashMap<>();
    private static final Pattern GREAT_EXPLORER = Pattern.compile("^(?:(?:Level\\s*)?(?<current>\\d+)\\s*/\\s*20|Level\\s*(?<level>\\d+)|MAX(?:ED)?)$",Pattern.CASE_INSENSITIVE);
    private static AquilaConfig cfg;
    private static boolean initialized;
    private static int waitingForChest;
    private static long chestMessageAt;
    private static Object levelIdentity;
    private static int carpetTick;
    private static long lastDiscoverySound;

    private AquilaMiningHighlights() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) onChat(clean(message.getString()));
            return true;
        });
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof BlockStateUpdate update) onBlock(update);
            else if (packet instanceof ClientboundLevelParticlesPacket particles) onParticle(particles);
            else if (packet instanceof ClientboundSoundPacket sound) onSound(sound);
        });
        ConstellationClient.tick().every(1, "aquila-mining-highlights", AquilaMiningHighlights::tick);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.miningHighlightsSuite;
    }

    private static boolean dwarven() {
        return active() && ConstellationClient.loc().area() == SkyblockArea.DWARVEN_MINES;
    }

    private static boolean hollows() {
        return active() && ConstellationClient.loc().area() == SkyblockArea.CRYSTAL_HOLLOWS;
    }
    private static boolean chestTracking(){return hollows()&&(cfg.treasureChestEsp||cfg.powderChestTimer);}

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) {
            reset();
            levelIdentity = mc.level;
        }
        if (!dwarven()) {
            CARPETS.clear();
            carpetTick = 0;
        } else if (cfg.dwarvenCarpetHighlighter && mc.level != null && mc.player != null
            && ++carpetTick >= Math.clamp(cfg.dwarvenCarpetScanTicks, 5, 100)) {
            carpetTick = 0;
            scanCarpets(mc);
        }
        if (!hollows()) {
            clearChests();
            return;
        }
        learnGreatExplorer(mc);
        long now = System.currentTimeMillis();
        if (waitingForChest > 0 && now - chestMessageAt > Math.clamp(cfg.treasureChestAssociationSeconds, 1, 15) * 1000L)
            waitingForChest = 0;
        PARTICLES.entrySet().removeIf(entry -> now - entry.getValue() > Math.clamp(cfg.treasureChestParticleMillis, 50, 1000));
        MINED_BLOCKS.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
        CHESTS.entrySet().removeIf(entry -> {
            boolean gone = now >= entry.getValue() || mc.level == null || !mc.level.getBlockState(entry.getKey()).is(Blocks.CHEST);
            if (gone) clearProgress(entry.getKey());
            return gone;
        });
    }

    private static void scanCarpets(Minecraft mc) {
        int radius = Math.clamp(cfg.dwarvenCarpetScanRadius, 4, 24);
        for (BlockPos mutable : BlockPos.withinManhattan(mc.player.blockPosition(), radius, radius, radius))
            if (isOreCarpet(mc, mutable)) CARPETS.add(mutable.immutable());
        CARPETS.removeIf(pos -> !isOreCarpet(mc, pos)
            || pos.distToCenterSqr(mc.player.position()) > Math.pow(radius * 4.0, 2));
    }

    private static boolean isOreCarpet(Minecraft mc, BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        return (state.is(Blocks.CARPET.gray()) || state.is(Blocks.CARPET.lightBlue()) || state.is(Blocks.CARPET.lightGray()))
            && mc.level.getBlockState(pos.below()).is(Blocks.SEA_LANTERN);
    }

    private static void onChat(String message) {
        if (!chestTracking() || !message.equals(CHEST_SPAWN)) return;
        waitingForChest++;
        chestMessageAt = System.currentTimeMillis();
    }

    private static void onBlock(BlockStateUpdate update) {
        Minecraft mc = Minecraft.getInstance();
        if (!chestTracking() || mc.player == null) return;
        BlockPos pos = update.pos().immutable();
        long now=System.currentTimeMillis();
        if(update.oldState().is(Blocks.STONE)&&update.newState().isAir())MINED_BLOCKS.put(pos,now);
        boolean newChest=!update.oldState().is(Blocks.CHEST)&&update.newState().is(Blocks.CHEST);
        boolean mined=MINED_BLOCKS.remove(pos)!=null;
        boolean nearbyPlayer=mc.level.players().stream().anyMatch(player->player!=mc.player&&player.distanceToSqr(Vec3.atCenterOf(pos))<625);
        boolean possibleFalsePositive=nearbyPlayer||!mined&&update.oldState().isAir();
        boolean licensedSignal=!possibleFalsePositive||now-lastDiscoverySound<=200;
        if ((waitingForChest > 0 || licensedSignal) && newChest
            && pos.distToCenterSqr(mc.player.position()) <= Math.pow(Math.clamp(cfg.treasureChestAssociationRange, 3, 20), 2)) {
            CHESTS.put(pos, now + Math.clamp(cfg.powderChestDurationSeconds, 30, 90) * 1000L);
            CURRENT_LOCKS.put(pos, 0);
            if(waitingForChest>0)waitingForChest--;
        } else if (!update.newState().is(Blocks.CHEST) && CHESTS.remove(pos) != null) clearProgress(pos);
    }

    private static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (hollows() && cfg.treasureChestEsp && cfg.treasureChestLockSpot
            && packet.getParticle().getType() == ParticleTypes.CRIT)
            PARTICLES.put(new Vec3(packet.getX(), packet.getY(), packet.getZ()), System.currentTimeMillis());
    }

    private static void onSound(ClientboundSoundPacket packet) {
        var id = packet.getSound().value().location();
        if(!chestTracking())return;
        if(id.equals(SoundEvents.PLAYER_LEVELUP.location())&&packet.getPitch()==1f&&packet.getVolume()==1f)lastDiscoverySound=System.currentTimeMillis();
        if(CHESTS.isEmpty())return;
        BlockPos target = targetedChest();
        if (id.equals(SoundEvents.EXPERIENCE_ORB_PICKUP.location()) && packet.getPitch() == 1f && target != null) {
            CURRENT_LOCKS.merge(target, 1, Integer::sum);
            PARTICLES.clear();
        } else if (id.equals(SoundEvents.VILLAGER_NO.location())) {
            if (target != null) CURRENT_LOCKS.put(target, 0);
            PARTICLES.clear();
        } else if (id.equals(SoundEvents.CHEST_OPEN.location()) && target != null) {
            NEEDED_LOCKS.put(target, Math.min(CURRENT_LOCKS.getOrDefault(target, 0), 5));
            CURRENT_LOCKS.put(target, 0);
            PARTICLES.clear();
            CHESTS.remove(target);
            clearProgress(target);
        }
    }

    public static boolean shouldCancel(ClientboundSoundPacket packet){
        if(!hollows()||cfg==null||!cfg.powderChestTimer)return false;
        var id=packet.getSound().value().location();
        return cfg.powderChestMuteDiscover&&id.equals(SoundEvents.PLAYER_LEVELUP.location())&&packet.getPitch()==1f&&packet.getVolume()==1f
            ||cfg.powderChestMuteOpen&&id.equals(SoundEvents.CHEST_OPEN.location())&&packet.getPitch()==1f&&packet.getVolume()==1f;
    }

    private static BlockPos targetedChest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult hit && CHESTS.containsKey(hit.getBlockPos())) return hit.getBlockPos();
        return null;
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active()) return;
        if (dwarven() && cfg.dwarvenCarpetHighlighter)
            for (BlockPos pos : CARPETS)
                ctx.box(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + .0625, pos.getZ() + 1),
                    cfg.dwarvenCarpetColor, cfg.dwarvenCarpetThroughWalls);
        if (!chestTracking()) return;
        long now=System.currentTimeMillis();
        for (BlockPos pos : CHESTS.keySet()) {
            Vec3 center = Vec3.atCenterOf(pos).subtract(0, .0625, 0);
            if (cfg.treasureChestEsp&&cfg.treasureChestOutline)
                ctx.outline(AABB.ofSize(center, .885, .885, .885), cfg.treasureChestColor, cfg.treasureChestThroughWalls, 3);
            if(timerEnabled()&&cfg.powderChestHighlight)ctx.box(AABB.ofSize(center,.94,.94,.94),timerColor(CHESTS.get(pos)-now),cfg.powderChestThroughWalls);
            if(timerEnabled()&&cfg.powderChestDrawTimer){double y=playerBelow(pos)?1.25:-.25;ctx.label(Vec3.atLowerCornerOf(pos).add(.5,y,.5),formatRemaining(CHESTS.get(pos)-now),timerColor(CHESTS.get(pos)-now),cfg.powderChestThroughWalls);}
        }
        drawTimerLines(ctx);
        if(!cfg.treasureChestEsp)return;
        BlockPos target = targetedChest();
        if (target == null) return;
        Vec3 center = Vec3.atCenterOf(target);
        if (cfg.treasureChestLockSpot) drawLockSpot(ctx, center);
        int needed = NEEDED_LOCKS.getOrDefault(target, 0);
        if (cfg.treasureChestLockProgress && needed > 0) {
            int current = Math.min(CURRENT_LOCKS.getOrDefault(target, 0), needed);
            ctx.label(center.add(0, .75, 0), current + "/" + needed, cfg.treasureChestColor, true);
        }
    }

    private static void drawTimerLines(WorldRenderer.Ctx ctx){
        if(!timerEnabled()||!cfg.powderChestDrawLine||CHESTS.isEmpty())return;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        List<Map.Entry<BlockPos,Long>> entries=new ArrayList<>(CHESTS.entrySet());
        Comparator<Map.Entry<BlockPos,Long>> comparator=cfg.powderChestLineMode.equalsIgnoreCase("NEAREST")
            ?Comparator.comparingDouble(e->e.getKey().distToCenterSqr(mc.player.position())):Comparator.comparingLong(Map.Entry::getValue);
        entries.sort(comparator);int count=Math.min(entries.size(),Math.clamp(cfg.powderChestLineCount,1,30));Vec3 previous=mc.player.getEyePosition();
        for(int i=0;i<count;i++){var entry=entries.get(i);Vec3 next=Vec3.atCenterOf(entry.getKey());ctx.line(previous,next,timerColor(entry.getValue()-System.currentTimeMillis()),cfg.powderChestThroughWalls);previous=next;}
    }

    public record PowderState(int count,long oldestMillis,double nearestDistance,long nearestMillis){}
    public static PowderState powderState(){
        if(!timerEnabled()||CHESTS.isEmpty())return null;Minecraft mc=Minecraft.getInstance();if(mc.player==null)return null;long now=System.currentTimeMillis();
        long oldest=CHESTS.values().stream().mapToLong(v->v).min().orElse(now)-now;var nearest=CHESTS.entrySet().stream().min(Comparator.comparingDouble(e->e.getKey().distToCenterSqr(mc.player.position()))).orElse(null);
        return nearest==null?null:new PowderState(CHESTS.size(),Math.max(0,oldest),Math.sqrt(nearest.getKey().distToCenterSqr(mc.player.position())),Math.max(0,nearest.getValue()-now));
    }
    public static boolean powderVisible(){return powderState()!=null;}
    public static AquilaConfig config(){return cfg;}
    public static String formatRemaining(long millis){long tenths=Math.max(0,(millis+99)/100);return String.format(Locale.ROOT,"%d.%ds",tenths/10,tenths%10);}
    public static int powderColor(long millis){return timerColor(millis);}
    private static int timerColor(long millis){if(cfg.powderChestStaticColor)return cfg.powderChestStaticArgb;double ratio=Math.clamp(millis/(Math.clamp(cfg.powderChestDurationSeconds,30,90)*1000.0),0,1);if(ratio>.5)return blend(cfg.powderChestCautionArgb,cfg.powderChestGoodArgb,(ratio-.5)*2);return blend(cfg.powderChestDangerArgb,cfg.powderChestCautionArgb,ratio*2);}
    private static int blend(int a,int b,double amount){amount=Math.clamp(amount,0,1);int aa=(int)(((a>>>24)&255)*(1-amount)+((b>>>24)&255)*amount),r=(int)(((a>>>16)&255)*(1-amount)+((b>>>16)&255)*amount),g=(int)(((a>>>8)&255)*(1-amount)+((b>>>8)&255)*amount),bl=(int)((a&255)*(1-amount)+(b&255)*amount);return aa<<24|r<<16|g<<8|bl;}
    private static boolean playerBelow(BlockPos pos){Minecraft mc=Minecraft.getInstance();return mc.player!=null&&pos.getY()<=mc.player.getY();}
    private static boolean timerEnabled(){return hollows()&&cfg.powderChestTimer&&(!cfg.powderChestOnlyMaxGreatExplorer||cfg.powderChestGreatExplorerLevel>=20);}
    private static void learnGreatExplorer(Minecraft mc){
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)||!clean(screen.getTitle().getString()).equals("Heart of the Mountain"))return;
        for(ItemStack stack:screen.getMenu().getItems()){if(stack.isEmpty()||!clean(stack.getHoverName().getString()).equals("Great Explorer"))continue;for(Component line:stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,mc.player,net.minecraft.world.item.TooltipFlag.NORMAL)){Matcher matcher=GREAT_EXPLORER.matcher(clean(line.getString()));if(!matcher.find())continue;int level=matcher.group().toUpperCase(Locale.ROOT).startsWith("MAX")?20:Integer.parseInt(matcher.group("current")!=null?matcher.group("current"):matcher.group("level"));if(level!=cfg.powderChestGreatExplorerLevel){cfg.powderChestGreatExplorerLevel=Math.clamp(level,0,20);save();}return;}}
    }

    private static void drawLockSpot(WorldRenderer.Ctx ctx, Vec3 chest) {
        Vec3 total = Vec3.ZERO;
        int count = 0;
        for (Vec3 particle : PARTICLES.keySet()) {
            if (!particle.closerThan(chest, .8)) continue;
            total = total.add(particle);
            count++;
        }
        if (count == 0) return;
        Vec3 spot = total.scale(1.0 / count);
        ctx.box(AABB.ofSize(spot, .1, .1, .1), cfg.treasureChestColor, true);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mininghighlights")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> {
                CARPETS.clear();
                clearChests();
                return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("carpetradius")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(4, 24))
                    .executes(context -> {
                        cfg.dwarvenCarpetScanRadius = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("carpetticks")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(5, 100))
                    .executes(context -> {
                        cfg.dwarvenCarpetScanTicks = IntegerArgumentType.getInteger(context, "ticks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chestrange")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(3, 20))
                    .executes(context -> {
                        cfg.treasureChestAssociationRange = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("association")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 15))
                    .executes(context -> {
                        cfg.treasureChestAssociationSeconds = IntegerArgumentType.getInteger(context, "seconds");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("particlems")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("milliseconds", IntegerArgumentType.integer(50, 1000))
                    .executes(context -> {
                        cfg.treasureChestParticleMillis = IntegerArgumentType.getInteger(context, "milliseconds");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("timer")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(30, 90))
                    .executes(context -> {cfg.powderChestDurationSeconds=IntegerArgumentType.getInteger(context,"seconds");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("linecount")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 30))
                    .executes(context -> {cfg.powderChestLineCount=IntegerArgumentType.getInteger(context,"count");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("linemode")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(context -> lineMode(StringArgumentType.getString(context,"mode")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("explorer")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("level", IntegerArgumentType.integer(0, 20))
                    .executes(context -> {cfg.powderChestGreatExplorerLevel=IntegerArgumentType.getInteger(context,"level");save();return status();})))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("target", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(context -> color(StringArgumentType.getString(context, "target"), StringArgumentType.getString(context, "argb"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Carpets " + on(cfg.dwarvenCarpetHighlighter) + " with " + CARPETS.size() + " cached; chests "
            + on(cfg.treasureChestEsp) + " with " + CHESTS.size() + " active; timer "+on(cfg.powderChestTimer)+", lines "+cfg.powderChestLineMode.toLowerCase(Locale.ROOT)+".");
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) {
            local("State must be on or off.");
            return 0;
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.miningHighlightsSuite = value;
            case "carpets" -> cfg.dwarvenCarpetHighlighter = value;
            case "carpetwalls" -> cfg.dwarvenCarpetThroughWalls = value;
            case "chests" -> cfg.treasureChestEsp = value;
            case "outline" -> cfg.treasureChestOutline = value;
            case "lockspot" -> cfg.treasureChestLockSpot = value;
            case "progress" -> cfg.treasureChestLockProgress = value;
            case "chestwalls" -> cfg.treasureChestThroughWalls = value;
            case "timer" -> cfg.powderChestTimer=value;
            case "timerhud" -> cfg.powderChestTimerHud=value;
            case "timerhighlight" -> cfg.powderChestHighlight=value;
            case "timerlabel" -> cfg.powderChestDrawTimer=value;
            case "timerline" -> {cfg.powderChestDrawLine=value;if(value&&cfg.powderChestLineMode.equalsIgnoreCase("NONE"))cfg.powderChestLineMode="OLDEST";}
            case "timerwalls" -> cfg.powderChestThroughWalls=value;
            case "staticcolor" -> cfg.powderChestStaticColor=value;
            case "maxexplorer" -> cfg.powderChestOnlyMaxGreatExplorer=value;
            case "mutediscover" -> cfg.powderChestMuteDiscover=value;
            case "muteopen" -> cfg.powderChestMuteOpen=value;
            default -> {
                local("Unknown option. Use enabled, carpets, carpetwalls, chests, outline, lockspot, progress, chestwalls, timer, timerhud, timerhighlight, timerlabel, timerline, timerwalls, staticcolor, maxexplorer, mutediscover, or muteopen.");
                return 0;
            }
        }
        if (!cfg.dwarvenCarpetHighlighter) CARPETS.clear();
        if (!cfg.treasureChestEsp&&!cfg.powderChestTimer) clearChests();
        save();
        return status();
    }

    private static int color(String target, String raw) {
        Integer value;
        try {
            String clean = raw.startsWith("#") ? raw.substring(1) : raw;
            if (clean.length() != 8) throw new NumberFormatException();
            value = (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException exception) {
            local("Color must be an eight-digit ARGB hex value.");
            return 0;
        }
        if (target.equalsIgnoreCase("carpet")) cfg.dwarvenCarpetColor = value;
        else if (target.equalsIgnoreCase("chest")) cfg.treasureChestColor = value;
        else if(target.equalsIgnoreCase("timer"))cfg.powderChestStaticArgb=value;
        else if(target.equalsIgnoreCase("good"))cfg.powderChestGoodArgb=value;
        else if(target.equalsIgnoreCase("caution"))cfg.powderChestCautionArgb=value;
        else if(target.equalsIgnoreCase("danger"))cfg.powderChestDangerArgb=value;
        else {
            local("Color target must be carpet, chest, timer, good, caution, or danger.");
            return 0;
        }
        save();
        return status();
    }

    private static void clearProgress(BlockPos pos) {
        CURRENT_LOCKS.remove(pos);
        NEEDED_LOCKS.remove(pos);
    }

    private static void clearChests() {
        waitingForChest = 0;
        chestMessageAt = 0;
        CHESTS.clear();
        PARTICLES.clear();
        CURRENT_LOCKS.clear();
        NEEDED_LOCKS.clear();
        MINED_BLOCKS.clear();
        lastDiscoverySound=0;
    }

    private static void reset() {
        CARPETS.clear();
        clearChests();
        carpetTick = 0;
        levelIdentity = null;
    }

    private static void save() {
        ConstellationClient.saveConfig();
    }

    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Mining Highlights] §f" + text));
    }

    private static String clean(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim();
    }

    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }

    private static String on(boolean value) {
        return value ? "on" : "off";
    }
    private static int lineMode(String raw){String value=raw.toUpperCase(Locale.ROOT);if(!value.equals("OLDEST")&&!value.equals("NEAREST")&&!value.equals("NONE")){local("Line mode must be oldest, nearest, or none.");return 0;}cfg.powderChestLineMode=value;cfg.powderChestDrawLine=!value.equals("NONE");save();return status();}
}
