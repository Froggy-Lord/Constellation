package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/dwarven/PickobulusHelper.java
public final class AquilaPickobulus {
    public enum Drop {
        MINESHAFT_PITY("Mineshaft Pity"),
        HARDSTONE("Hardstone"),
        ICE("Ice"),
        TUNGSTEN("Tungsten"),
        UMBER("Umber"),
        MITHRIL("Mithril"),
        TITANIUM("Titanium"),
        GEMSTONES("Gemstones"),
        MITHRIL_POWDER("Mithril Powder");

        private final String display;
        Drop(String display) { this.display = display; }
        public String display() { return display; }
    }

    public record State(boolean visible, String error, int totalBlocks, Map<Drop, Integer> drops) {}

    private static final Set<Block> CONVERT_TO_BEDROCK = Set.of(
        Blocks.STONE, Blocks.COBBLESTONE, Blocks.POLISHED_DIORITE,
        Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
        Blocks.DYED_TERRACOTTA.cyan(), Blocks.WOOL.lightBlue(), Blocks.WOOL.gray(),
        Blocks.LAPIS_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK,
        Blocks.EMERALD_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.COAL_BLOCK, Blocks.QUARTZ_BLOCK,
        Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.NETHERRACK, Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.END_STONE
    );
    private static final Set<Block> STAINED_GLASS = Set.of(Stream.concat(
        Blocks.STAINED_GLASS.asList().stream(), Blocks.STAINED_GLASS_PANE.asList().stream()).toArray(Block[]::new));
    private static final BlockState[][][] BLOCKS = new BlockState[8][8][8];
    private static final Set<BlockPos> BREAK_BLOCKS = new HashSet<>();
    private static final EnumMap<Drop, Integer> DROPS = new EnumMap<>(Drop.class);
    private static AquilaConfig cfg;
    private static boolean initialized;
    private static State state = new State(false, "", 0, Map.of());

    private AquilaPickobulus() {}

    public static void init(AquilaConfig config) {
        cfg = config;
        if (initialized) return;
        initialized = true;
        ConstellationClient.tick().every(1, "aquila-pickobulus", AquilaPickobulus::update);
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> reset());
    }

    private static boolean miningArea() {
        SkyblockArea area = ConstellationClient.loc().area();
        return area == SkyblockArea.GOLD_MINE || area == SkyblockArea.DEEP_CAVERNS || area == SkyblockArea.DWARVEN_MINES
            || area == SkyblockArea.CRYSTAL_HOLLOWS || area == SkyblockArea.GLACITE_TUNNELS
            || area == SkyblockArea.GLACITE_MINESHAFT;
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && cfg.pickobulusSuite && miningArea();
    }

    private static void update() {
        BREAK_BLOCKS.clear();
        DROPS.clear();
        if (!active()) {
            state = new State(false, "", 0, Map.of());
            return;
        }
        String cooldown = cooldown();
        if (cooldown != null && !cooldown.equals("Available")) {
            state = new State(!cfg.pickobulusHideHudOnCooldown, "On cooldown: " + cooldown, 0, Map.of());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            state = new State(false, "", 0, Map.of());
            return;
        }
        if (!hasAbility(mc.player.getMainHandItem())) {
            state = new State(false, "Hold a Pickobulus tool", 0, Map.of());
            return;
        }
        Vec3 start = mc.player.getEyePosition(1).add(0, .53625, 0);
        BlockHitResult hit = mc.level.clip(new ClipContext(start,
            start.add(mc.player.getViewVector(1).scale(Math.clamp(cfg.pickobulusRange, 5, 30))),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            state = new State(true, "Not looking at a block", 0, Map.of());
            return;
        }
        calculate(hit.getBlockPos(), ConstellationClient.loc().area(), mc);
        state = new State(true, "", BREAK_BLOCKS.size(), Map.copyOf(DROPS));
    }

    private static String cooldown() {
        for (String line : TabList.lines()) {
            String clean = clean(line).trim();
            if (clean.startsWith("Pickobulus: ")) return clean.substring(12).trim();
        }
        return null;
    }

    private static boolean hasAbility(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            String text = clean(line.getString());
            if (text.contains("Ability: Pickobulus") || text.contains("Pickobulus") && text.contains("RIGHT CLICK")) return true;
        }
        return false;
    }

    private static void calculate(BlockPos target, SkyblockArea area, Minecraft mc) {
        BlockPos.MutableBlockPos mutable = target.mutable().move(-4, -4, -4);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 8; z++) {
                    BLOCKS[x][y][z] = mc.level.getBlockState(mutable);
                    mutable.move(Direction.SOUTH);
                }
                mutable.move(0, 1, -8);
            }
            mutable.move(1, -8, 0);
        }
        for (int x = 1; x < 7; x++) for (int y = 1; y < 7; y++) for (int z = 1; z < 7; z++) {
            BlockState block = BLOCKS[x][y][z];
            if (block.isAir() || block.is(Blocks.BEDROCK) || !exposed(x, y, z)) continue;
            if (area == SkyblockArea.GLACITE_TUNNELS) tunnels(target, block, x, y, z);
            else if (area == SkyblockArea.GLACITE_MINESHAFT) mineshaft(target, block, x, y, z);
            else if (area == SkyblockArea.CRYSTAL_HOLLOWS) hollows(target, block, x, y, z);
            else convert(target, block, x, y, z);
        }
    }

    private static boolean exposed(int x, int y, int z) {
        return BLOCKS[x - 1][y][z].isAir() || BLOCKS[x + 1][y][z].isAir()
            || BLOCKS[x][y - 1][z].isAir() || BLOCKS[x][y + 1][z].isAir()
            || BLOCKS[x][y][z - 1].isAir() || BLOCKS[x][y][z + 1].isAir();
    }

    private static void convert(BlockPos target, BlockState block, int x, int y, int z) {
        if (CONVERT_TO_BEDROCK.contains(block.getBlock()) || STAINED_GLASS.contains(block.getBlock()))
            add(target, x, y, z, false);
    }

    private static void hollows(BlockPos target, BlockState block, int x, int y, int z) {
        if (STAINED_GLASS.contains(block.getBlock())) drop(Drop.GEMSTONES, 1);
        else if (block.is(Blocks.PRISMARINE) || block.is(Blocks.PRISMARINE_BRICKS) || block.is(Blocks.DARK_PRISMARINE))
            drop(Drop.MITHRIL_POWDER, 3);
        else if (block.is(Blocks.WOOL.lightBlue())) drop(Drop.MITHRIL_POWDER, 5);
        else if (block.is(Blocks.WOOL.gray()) || block.is(Blocks.DYED_TERRACOTTA.cyan())) drop(Drop.MITHRIL_POWDER, 1);
        add(target, x, y, z, true);
    }

    private static void tunnels(BlockPos target, BlockState block, int x, int y, int z) {
        boolean breakable = true;
        if (block.is(Blocks.PACKED_ICE)) {
            drop(Drop.MINESHAFT_PITY, 2); drop(Drop.ICE, 1);
        } else if (STAINED_GLASS.contains(block.getBlock())) {
            drop(Drop.MINESHAFT_PITY, 2); drop(Drop.GEMSTONES, 1);
        } else if (block.is(Blocks.POLISHED_DIORITE)) {
            drop(Drop.MINESHAFT_PITY, 4); drop(Drop.TITANIUM, 1);
        } else if (block.is(Blocks.INFESTED_STONE) || block.is(Blocks.CARPET.lightGray())) drop(Drop.HARDSTONE, 1);
        else if (block.is(Blocks.PRISMARINE) || block.is(Blocks.PRISMARINE_BRICKS) || block.is(Blocks.DARK_PRISMARINE)) {
            drop(Drop.MINESHAFT_PITY, 1); drop(Drop.MITHRIL, 1); drop(Drop.MITHRIL_POWDER, 3);
        } else if (block.is(Blocks.WOOL.lightBlue())) {
            drop(Drop.MINESHAFT_PITY, 1); drop(Drop.MITHRIL, 1); drop(Drop.MITHRIL_POWDER, 5);
        } else if (block.is(Blocks.WOOL.gray()) || block.is(Blocks.DYED_TERRACOTTA.cyan())) {
            drop(Drop.MINESHAFT_PITY, 1); drop(Drop.MITHRIL, 1); drop(Drop.MITHRIL_POWDER, 1);
        } else if (block.is(Blocks.DYED_TERRACOTTA.brown()) || block.is(Blocks.TERRACOTTA) || block.is(Blocks.SMOOTH_RED_SANDSTONE)) {
            drop(Drop.MINESHAFT_PITY, 2); drop(Drop.UMBER, 1);
        } else if (block.is(Blocks.INFESTED_COBBLESTONE) || block.is(Blocks.CLAY)) {
            drop(Drop.MINESHAFT_PITY, 2); drop(Drop.TUNGSTEN, 1);
        } else breakable = false;
        if (breakable) add(target, x, y, z, block.is(Blocks.PACKED_ICE) || STAINED_GLASS.contains(block.getBlock()));
    }

    private static void mineshaft(BlockPos target, BlockState block, int x, int y, int z) {
        if (block.is(Blocks.STONE)) drop(Drop.HARDSTONE, 1);
        add(target, x, y, z, true);
    }

    private static void add(BlockPos target, int x, int y, int z, boolean becomesAir) {
        if (becomesAir) BLOCKS[x][y][z] = Blocks.AIR.defaultBlockState();
        BREAK_BLOCKS.add(target.offset(x - 4, y - 4, z - 4));
    }

    private static void drop(Drop drop, int amount) {
        DROPS.merge(drop, amount, Integer::sum);
    }

    public static void draw(WorldRenderer.Ctx ctx) {
        if (!active() || !cfg.pickobulusPreview || !state.visible() || !state.error().isEmpty()) return;
        for (BlockPos pos : BREAK_BLOCKS) ctx.outline(new net.minecraft.world.phys.AABB(pos), cfg.pickobulusColor, cfg.pickobulusThroughWalls, 2);
    }

    public static State state() { return state; }
    public static AquilaConfig config() { return cfg; }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pickobulushelper")
            .executes(context -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("range")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("blocks", IntegerArgumentType.integer(5, 30))
                    .executes(context -> {
                        cfg.pickobulusRange = IntegerArgumentType.getInteger(context, "blocks");
                        save();
                        return status();
                    })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("color")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                    .executes(context -> color(StringArgumentType.getString(context, "argb")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> option(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int status() {
        local("Preview " + on(cfg.pickobulusPreview) + ", HUD " + on(cfg.pickobulusHud) + ", "
            + state.totalBlocks() + " predicted blocks" + (state.error().isEmpty() ? "." : "; " + state.error() + "."));
        return 1;
    }

    private static int option(String name, String raw) {
        Boolean value = parse(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.pickobulusSuite = value;
            case "preview" -> cfg.pickobulusPreview = value;
            case "hud" -> cfg.pickobulusHud = value;
            case "hidecooldown" -> cfg.pickobulusHideHudOnCooldown = value;
            case "total" -> cfg.pickobulusShowTotalBlocks = value;
            case "pity" -> cfg.pickobulusShowMineshaftPity = value;
            case "drops" -> cfg.pickobulusShowDrops = value;
            case "powder" -> cfg.pickobulusShowPowder = value;
            case "errors" -> cfg.pickobulusShowErrors = value;
            case "throughwalls" -> cfg.pickobulusThroughWalls = value;
            default -> { local("Option must be enabled, preview, hud, hidecooldown, total, pity, drops, powder, errors, or throughwalls."); return 0; }
        }
        save();
        return status();
    }

    private static int color(String raw) {
        try {
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.length() != 8) throw new NumberFormatException();
            cfg.pickobulusColor = (int) Long.parseLong(value, 16);
            save();
            return status();
        } catch (NumberFormatException exception) {
            local("Color must be an eight-digit ARGB hex value.");
            return 0;
        }
    }

    private static void reset() {
        BREAK_BLOCKS.clear();
        DROPS.clear();
        state = new State(false, "", 0, Map.of());
    }

    private static void save() { ConstellationClient.saveConfig(); }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Pickobulus] §f" + text));
    }
    private static String clean(String text) { return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim(); }
    private static Boolean parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
}
