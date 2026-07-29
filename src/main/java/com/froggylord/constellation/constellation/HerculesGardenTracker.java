package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.LocationManager;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenOptimalSpeed.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenOptimalAngles.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/GardenYawAndPitch.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/farming/GardenCropSpeed.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/tracker/GardenBpsTracker.kt
// ported from SkyHanni (LGPL-3.0-or-later): features/garden/contest/FarmingContestApi.kt
public final class HerculesGardenTracker {
    public enum Crop {
        WHEAT("Wheat"), CARROT("Carrot"), POTATO("Potato"), NETHER_WART("Nether Wart"),
        PUMPKIN("Pumpkin"), MELON("Melon"), COCOA("Cocoa Beans"), SUGAR_CANE("Sugar Cane"),
        CACTUS("Cactus"), MUSHROOM("Mushroom"), SUNFLOWER("Sunflower"), MOONFLOWER("Moonflower"), WILD_ROSE("Wild Rose");
        private final String display;
        Crop(String display) { this.display = display; }
        public String display() { return display; }
    }

    public record Control(String crop, int speed, int targetSpeed, boolean anglesVisible, float yaw, float pitch,
                          float targetYaw, float targetPitch, float yawDifference, float pitchDifference) {}
    public record Rates(String crop, double instantBps, double averageBps, long blocks, long elapsedMillis) {}
    public record Contest(String crop, long collected, double cropsPerSecond, long elapsedMillis, long projectedTotal) {}

    private static final Pattern COLLECTED = Pattern.compile("^Collected\\s+(?<amount>[0-9][0-9,]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMER = Pattern.compile("(?:(?<minutes>[0-9]{1,2})m(?:in)?(?:utes?)?\\s*)?(?<seconds>[0-9]{1,2})s(?:ec)?(?:onds?)?|(?<clockMinutes>[0-9]{1,2}):(?<clockSeconds>[0-9]{2})", Pattern.CASE_INSENSITIVE);
    private static HerculesConfig cfg;
    private static final Deque<Integer> samples = new ArrayDeque<>();
    private static Crop lastCrop;
    private static long sessionBlocks;
    private static long sessionStarted;
    private static long lastBreak;
    private static int breaksThisSecond;
    private static long lastSample;
    private static float lastYaw;
    private static float lastPitch;
    private static long lastAngleChange;
    private static boolean contestActive;
    private static Crop contestCrop;
    private static long contestStarted;
    private static long contestCollected;
    private static long contestBaseline;
    private static long contestDetected;
    private static long contestRemaining = -1;
    private static long contestLastSeen;
    private static Object levelIdentity;
    private static final Map<BlockPos, Long> localAttacks = new HashMap<>();

    private HerculesGardenTracker() {}

    public static void init(HerculesConfig config) {
        cfg = config;
        ConstellationClient.instance().packets().register(packet -> {
            if (packet instanceof BlockStateUpdate update) onBlock(update);
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (cfg.enabled && inGarden() && crop(level.getBlockState(pos)) != null)
                localAttacks.put(pos.immutable(), System.currentTimeMillis());
            return InteractionResult.PASS;
        });
        ConstellationClient.tick().every(1, "hercules-garden-control", HerculesGardenTracker::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) reset(mc.level);
        long now = System.currentTimeMillis();
        localAttacks.entrySet().removeIf(entry -> now - entry.getValue() > 1500);
        if (now - lastSample >= 1000) {
            int seconds = Math.max(1, (int) ((now - lastSample) / 1000));
            for (int i = 1; i < seconds; i++) addSample(0);
            addSample(breaksThisSecond);
            breaksThisSecond = 0;
            lastSample = now;
        }
        if (mc.player != null) {
            float yaw = normalizeYaw(mc.player.getYRot());
            float pitch = mc.player.getXRot();
            if (yaw != lastYaw || pitch != lastPitch) lastAngleChange = now;
            lastYaw = yaw;
            lastPitch = pitch;
        }
        readContest(now);
    }

    private static void onBlock(BlockStateUpdate update) {
        if (!inGarden()) return;
        Crop crop = crop(update.oldState());
        if (crop == null || !wasHarvest(update.oldState(), update.newState())) return;
        long now = System.currentTimeMillis();
        Long attacked = localAttacks.remove(update.pos());
        if (attacked == null || now - attacked > 1500) return;
        if (lastBreak > 0 && now - lastBreak > Math.max(1, cfg.farmingResetAfterSeconds) * 1000L) {
            samples.clear();
            sessionBlocks = 0;
            sessionStarted = now;
            breaksThisSecond = 0;
        }
        if (sessionStarted == 0) sessionStarted = now;
        lastBreak = now;
        lastCrop = crop;
        sessionBlocks++;
        breaksThisSecond++;
    }

    private static void addSample(int value) {
        samples.addLast(value);
        while (samples.size() > 12) samples.removeFirst();
    }

    private static void readContest(long now) {
        if (!inGarden() && !cfg.jacobShowOutsideGarden) { clearContest(); return; }
        List<String> lines = ConstellationClient.loc().getSidebarLines();
        int header = -1;
        for (int i = 0; i < lines.size(); i++) if (clean(lines.get(i)).equalsIgnoreCase("Jacob's Contest")) { header = i; break; }
        if (header < 0) {
            if (contestActive && now - contestLastSeen > 2500) clearContest();
            return;
        }
        Crop found = null;
        long amount = -1;
        long remainingMillis = -1;
        for (int i = header + 1; i < Math.min(lines.size(), header + 4); i++) {
            String line = clean(lines.get(i));
            if (found == null) found = cropByText(line);
            Matcher collected = COLLECTED.matcher(line);
            if (collected.matches()) amount = parseLong(collected.group("amount"), -1);
            Matcher timer = TIMER.matcher(line);
            if (timer.find()) {
                int minutes = parseInt(timer.group("minutes"), parseInt(timer.group("clockMinutes"), 0));
                int seconds = parseInt(timer.group("seconds"), parseInt(timer.group("clockSeconds"), 0));
                remainingMillis = (minutes * 60L + seconds) * 1000L;
            }
        }
        if (found == null || amount < 0) return;
        if (!contestActive || found != contestCrop || amount < contestCollected && remainingMillis > contestRemaining) {
            contestStarted = remainingMillis >= 0 ? now - Math.max(0, cfg.jacobContestMinutes * 60_000L - remainingMillis) : now;
            contestCollected = Math.max(0, amount);
            contestBaseline = contestCollected;
            contestDetected = now;
        } else if (amount >= 0) contestCollected = Math.max(contestCollected, amount);
        contestRemaining = remainingMillis;
        contestCrop = found;
        contestActive = true;
        contestLastSeen = now;
    }

    public static Control control() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (!inGarden() && !cfg.farmingShowOutsideGarden)) return null;
        Crop crop = cropInHand(mc.player.getMainHandItem());
        if (crop == null) crop = lastCrop;
        if (crop == null && cfg.farmingRequireTool) return null;
        boolean anglesVisible = cfg.farmingIgnoreAngleTimeout || System.currentTimeMillis() - lastAngleChange <= Math.max(1, cfg.farmingAngleTimeoutSeconds) * 1000L;
        int speed = (int) Math.floor(mc.player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 1000.0);
        float yaw = normalizeYaw(mc.player.getYRot());
        float pitch = mc.player.getXRot();
        int targetSpeed = crop == null ? 0 : targetSpeed(crop);
        float targetYaw = crop == null ? yaw : targetYaw(crop);
        float targetPitch = crop == null ? pitch : targetPitch(crop);
        return new Control(crop == null ? "Unknown" : crop.display(), speed, targetSpeed, anglesVisible, yaw, pitch,
            targetYaw, targetPitch, Math.abs(normalizeYaw(yaw - targetYaw)), Math.abs(pitch - targetPitch));
    }

    public static Rates rates() {
        if (!inGarden() || sessionStarted == 0) return null;
        long now = System.currentTimeMillis();
        if (lastBreak > 0 && now - lastBreak > Math.max(1, cfg.farmingResetAfterSeconds) * 1000L) return null;
        double instant = recentAverage(5);
        double average = sessionBlocks / Math.max(1.0, (now - sessionStarted) / 1000.0);
        return new Rates(lastCrop == null ? "Unknown" : lastCrop.display(), Math.min(20, instant),
            Math.min(20, average), sessionBlocks, now - sessionStarted);
    }

    public static Contest contest() {
        if (!contestActive || contestCrop == null) return null;
        long now = System.currentTimeMillis();
        long elapsed = Math.max(1, now - contestStarted);
        double rate = contestRemaining >= 0 && elapsed > 1000
            ? contestCollected / (elapsed / 1000.0)
            : (contestCollected - contestBaseline) / Math.max(1.0, (now - contestDetected) / 1000.0);
        long duration = Math.max(1, cfg.jacobContestMinutes) * 60_000L;
        long remaining = contestRemaining >= 0 ? contestRemaining : Math.max(0, duration - elapsed);
        return new Contest(contestCrop.display(), contestCollected, rate, elapsed, contestCollected + Math.round(rate * remaining / 1000.0));
    }

    public static boolean speedGood(Control c) { return c != null && Math.abs(c.speed() - c.targetSpeed()) <= Math.max(0, cfg.farmingSpeedTolerance); }
    public static boolean anglesGood(Control c) { return c != null && c.anglesVisible() && c.yawDifference() <= cfg.farmingAngleToleranceHundredths / 100f && c.pitchDifference() <= cfg.farmingAngleToleranceHundredths / 100f; }
    public static HerculesConfig config() { return cfg; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("gardencontrol")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c -> { reset(Minecraft.getInstance().level); local("Garden farming session reset."); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("speed").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("crop", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("value", IntegerArgumentType.integer(1, 500))
                .executes(c -> setSpeed(StringArgumentType.getString(c, "crop"), IntegerArgumentType.getInteger(c, "value"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("angle").then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("crop", StringArgumentType.word()).then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("yaw", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<FabricClientCommandSource,String>argument("pitch", StringArgumentType.word()).executes(c -> setAngle(StringArgumentType.getString(c, "crop"),
                    StringArgumentType.getString(c, "yaw"), StringArgumentType.getString(c, "pitch")))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resetseconds").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("seconds", IntegerArgumentType.integer(1, 60)).executes(c -> {
                cfg.farmingResetAfterSeconds = IntegerArgumentType.getInteger(c, "seconds"); save(); return status(); })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("contestminutes").then(RequiredArgumentBuilder.<FabricClientCommandSource,Integer>argument("minutes", IntegerArgumentType.integer(1, 60)).executes(c -> {
                cfg.jacobContestMinutes = IntegerArgumentType.getInteger(c, "minutes"); save(); return status(); }))));
    }

    private static int status() {
        Control c = control(); Rates r = rates(); Contest j = contest();
        local("Garden control: " + (c == null ? "inactive" : c.crop() + ", speed " + c.speed() + "/" + c.targetSpeed()
            + ", yaw " + decimal(c.yaw(), 2) + "/" + decimal(c.targetYaw(), 2) + ", pitch " + decimal(c.pitch(), 2) + "/" + decimal(c.targetPitch(), 2)) + ".");
        local("Rates: " + (r == null ? "inactive" : decimal(r.instantBps(), cfg.farmingBpsPrecision) + " BPS, " + r.blocks() + " blocks")
            + "; contest: " + (j == null ? "inactive" : j.crop() + " " + j.collected()) + ".");
        return 1;
    }

    private static int setSpeed(String name, int value) {
        Crop crop = cropByName(name); if (crop == null) { local("Unknown crop."); return 0; }
        switch (crop) {
            case WHEAT -> cfg.speedWheat=value; case CARROT -> cfg.speedCarrot=value; case POTATO -> cfg.speedPotato=value;
            case NETHER_WART -> cfg.speedNetherWart=value; case PUMPKIN -> cfg.speedPumpkin=value; case MELON -> cfg.speedMelon=value;
            case COCOA -> cfg.speedCocoa=value; case SUGAR_CANE -> cfg.speedSugarCane=value; case CACTUS -> cfg.speedCactus=value;
            case MUSHROOM -> cfg.speedMushroom=value; case SUNFLOWER -> cfg.speedSunflower=value; case MOONFLOWER -> cfg.speedMoonflower=value;
            case WILD_ROSE -> cfg.speedWildRose=value;
        }
        save(); local(crop.display() + " target speed set to " + value + "."); return 1;
    }

    private static int setAngle(String name, String yawText, String pitchText) {
        Crop crop = cropByName(name); if (crop == null) { local("Unknown crop."); return 0; }
        float yaw, pitch; try { yaw=Float.parseFloat(yawText); pitch=Float.parseFloat(pitchText); } catch (NumberFormatException e) { local("Yaw and pitch must be numbers."); return 0; }
        yaw=normalizeYaw(yaw); pitch=Math.clamp(pitch,-90,90);
        switch (crop) {
            case WHEAT -> {cfg.yawWheat=yaw;cfg.pitchWheat=pitch;} case CARROT -> {cfg.yawCarrot=yaw;cfg.pitchCarrot=pitch;}
            case POTATO -> {cfg.yawPotato=yaw;cfg.pitchPotato=pitch;} case NETHER_WART -> {cfg.yawNetherWart=yaw;cfg.pitchNetherWart=pitch;}
            case PUMPKIN -> {cfg.yawPumpkin=yaw;cfg.pitchPumpkin=pitch;} case MELON -> {cfg.yawMelon=yaw;cfg.pitchMelon=pitch;}
            case COCOA -> {cfg.yawCocoa=yaw;cfg.pitchCocoa=pitch;} case SUGAR_CANE -> {cfg.yawSugarCane=yaw;cfg.pitchSugarCane=pitch;}
            case CACTUS -> {cfg.yawCactus=yaw;cfg.pitchCactus=pitch;} case MUSHROOM -> {cfg.yawMushroom=yaw;cfg.pitchMushroom=pitch;}
            case SUNFLOWER -> {cfg.yawSunflower=yaw;cfg.pitchSunflower=pitch;} case MOONFLOWER -> {cfg.yawMoonflower=yaw;cfg.pitchMoonflower=pitch;}
            case WILD_ROSE -> {cfg.yawWildRose=yaw;cfg.pitchWildRose=pitch;}
        }
        save(); local(crop.display() + " target angle set to " + decimal(yaw,2) + "/" + decimal(pitch,2) + "."); return 1;
    }

    private static Crop crop(BlockState state) {
        if (state.is(Blocks.WHEAT)) return Crop.WHEAT; if (state.is(Blocks.CARROTS)) return Crop.CARROT;
        if (state.is(Blocks.POTATOES)) return Crop.POTATO; if (state.is(Blocks.NETHER_WART)) return Crop.NETHER_WART;
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN)) return Crop.PUMPKIN; if (state.is(Blocks.MELON)) return Crop.MELON;
        if (state.is(Blocks.COCOA)) return Crop.COCOA; if (state.is(Blocks.SUGAR_CANE)) return Crop.SUGAR_CANE;
        if (state.is(Blocks.CACTUS)) return Crop.CACTUS; if (state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)
            || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)) return Crop.MUSHROOM;
        if (state.is(Blocks.ROSE_BUSH)) return Crop.WILD_ROSE; if (state.is(Blocks.SUNFLOWER)) return timeFlower(); return null;
    }

    private static boolean wasHarvest(BlockState oldState, BlockState newState) {
        if (newState.isAir()) return true;
        Crop oldCrop = crop(oldState), newCrop = crop(newState);
        if (oldCrop == null || oldCrop != newCrop) return false;
        if (oldState.hasProperty(BlockStateProperties.AGE_7) && newState.hasProperty(BlockStateProperties.AGE_7))
            return oldState.getValue(BlockStateProperties.AGE_7) == 7 && newState.getValue(BlockStateProperties.AGE_7) < 7;
        if (oldState.hasProperty(BlockStateProperties.AGE_3) && newState.hasProperty(BlockStateProperties.AGE_3))
            return oldState.getValue(BlockStateProperties.AGE_3) == 3 && newState.getValue(BlockStateProperties.AGE_3) < 3;
        if (oldState.hasProperty(BlockStateProperties.AGE_2) && newState.hasProperty(BlockStateProperties.AGE_2))
            return oldState.getValue(BlockStateProperties.AGE_2) == 2 && newState.getValue(BlockStateProperties.AGE_2) < 2;
        return false;
    }

    static Crop cropInHand(ItemStack stack) {
        String id = LyraTooltips.marketId(stack); if (id == null) return null; id=id.toUpperCase(Locale.ROOT);
        if (id.startsWith("THEORETICAL_HOE_WHEAT")) return Crop.WHEAT; if (id.startsWith("THEORETICAL_HOE_CARROT")) return Crop.CARROT;
        if (id.startsWith("THEORETICAL_HOE_POTATO")) return Crop.POTATO; if (id.startsWith("THEORETICAL_HOE_WARTS")) return Crop.NETHER_WART;
        if (id.startsWith("PUMPKIN_DICER")) return Crop.PUMPKIN; if (id.startsWith("MELON_DICER")) return Crop.MELON;
        if (id.startsWith("COCO_CHOPPER")) return Crop.COCOA; if (id.startsWith("THEORETICAL_HOE_CANE")) return Crop.SUGAR_CANE;
        if (id.startsWith("CACTUS_KNIFE")) return Crop.CACTUS; if (id.startsWith("FUNGI_CUTTER")) return Crop.MUSHROOM;
        if (id.startsWith("THEORETICAL_HOE_SUNFLOWER")) return timeFlower(); if (id.startsWith("THEORETICAL_HOE_WILD_ROSE")) return Crop.WILD_ROSE;
        return null;
    }

    private static Crop cropByText(String text) { for (Crop c:Crop.values()) if (text.toLowerCase(Locale.ROOT).contains(c.display().toLowerCase(Locale.ROOT))) return c; return null; }
    private static Crop cropByName(String text) { String key=text.replace("_","").replace("-","").toLowerCase(Locale.ROOT); for(Crop c:Crop.values())if(c.name().replace("_","").toLowerCase(Locale.ROOT).equals(key)||c.display().replace(" ","").toLowerCase(Locale.ROOT).equals(key))return c;return null; }
    private static Crop timeFlower(){Minecraft mc=Minecraft.getInstance();long time=mc.level==null?0:mc.level.getGameTime()%24000;return time>=12000?Crop.MOONFLOWER:Crop.SUNFLOWER;}
    private static boolean inGarden(){return ConstellationClient.loc().area()== LocationManager.SkyblockArea.GARDEN;}
    private static String clean(String s){return ChatFormatting.stripFormatting(s).trim();}
    private static long parseLong(String text,long fallback){if(text==null)return fallback;try{return Long.parseLong(text.replace(",",""));}catch(NumberFormatException ignored){return fallback;}}
    private static int parseInt(String text,int fallback){if(text==null)return fallback;try{return Integer.parseInt(text);}catch(NumberFormatException ignored){return fallback;}}
    private static double recentAverage(int count){if(samples.isEmpty())return breaksThisSecond;int skip=Math.max(0,samples.size()-count),i=0,n=0,total=0;for(int v:samples){if(i++<skip)continue;total+=v;n++;}return n==0?0:(double)total/n;}
    private static float normalizeYaw(float yaw){float v=yaw%360;if(v>180)v-=360;if(v<=-180)v+=360;return v;}
    private static String decimal(double value,int precision){return String.format(Locale.ROOT,"%."+Math.clamp(precision,0,6)+"f",value);}
    private static void clearContest(){contestActive=false;contestCrop=null;contestStarted=0;contestCollected=0;contestBaseline=0;contestDetected=0;contestRemaining=-1;contestLastSeen=0;}
    private static void reset(Object level){levelIdentity=level;samples.clear();localAttacks.clear();lastCrop=null;sessionBlocks=0;sessionStarted=0;lastBreak=0;breaksThisSecond=0;lastSample=System.currentTimeMillis();lastAngleChange=lastSample;clearContest();}
    private static int targetSpeed(Crop c){return switch(c){case WHEAT->cfg.speedWheat;case CARROT->cfg.speedCarrot;case POTATO->cfg.speedPotato;case NETHER_WART->cfg.speedNetherWart;case PUMPKIN->cfg.speedPumpkin;case MELON->cfg.speedMelon;case COCOA->cfg.speedCocoa;case SUGAR_CANE->cfg.speedSugarCane;case CACTUS->cfg.speedCactus;case MUSHROOM->cfg.speedMushroom;case SUNFLOWER->cfg.speedSunflower;case MOONFLOWER->cfg.speedMoonflower;case WILD_ROSE->cfg.speedWildRose;};}
    private static float targetYaw(Crop c){return switch(c){case WHEAT->cfg.yawWheat;case CARROT->cfg.yawCarrot;case POTATO->cfg.yawPotato;case NETHER_WART->cfg.yawNetherWart;case PUMPKIN->cfg.yawPumpkin;case MELON->cfg.yawMelon;case COCOA->cfg.yawCocoa;case SUGAR_CANE->cfg.yawSugarCane;case CACTUS->cfg.yawCactus;case MUSHROOM->cfg.yawMushroom;case SUNFLOWER->cfg.yawSunflower;case MOONFLOWER->cfg.yawMoonflower;case WILD_ROSE->cfg.yawWildRose;};}
    private static float targetPitch(Crop c){return switch(c){case WHEAT->cfg.pitchWheat;case CARROT->cfg.pitchCarrot;case POTATO->cfg.pitchPotato;case NETHER_WART->cfg.pitchNetherWart;case PUMPKIN->cfg.pitchPumpkin;case MELON->cfg.pitchMelon;case COCOA->cfg.pitchCocoa;case SUGAR_CANE->cfg.pitchSugarCane;case CACTUS->cfg.pitchCactus;case MUSHROOM->cfg.pitchMushroom;case SUNFLOWER->cfg.pitchSunflower;case MOONFLOWER->cfg.pitchMoonflower;case WILD_ROSE->cfg.pitchWildRose;};}
    private static void local(String message){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("§2[Garden] §f"+message));}
    private static void save(){ConstellationClient.saveConfig();}
}
