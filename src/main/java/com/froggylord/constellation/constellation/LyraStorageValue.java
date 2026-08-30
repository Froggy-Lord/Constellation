package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.api.PriceProvider;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/BackpackPreview.java
// ported from Skyblocker (LGPL-3.0-or-later): skyblock/ChestValue.java
// ported from SkyHanni (LGPL-3.0-or-later): features/inventory/ChestValue.kt
public final class LyraStorageValue {
    private static final Pattern ENDER_CHEST = Pattern.compile("Ender Chest.*\\((\\d+)/\\d+\\)");
    private static final Pattern BACKPACK = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)");
    private static final int STORAGE_COUNT = 27;
    private static final Storage[] STORAGES = new Storage[STORAGE_COUNT];
    private static final Set<String> EXCLUDED_TITLES = Set.of("SkyBlock Menu", "Storage");
    private static final Pattern EXCLUDED_MENUS = Pattern.compile("(?i)(auction|bazaar|shop|trade|confirm|terminal|click in order|select all|starts with|change all to same|navigate the maze|experimentation|chronomatron|ultrasequencer|superpairs|museum|sack|stash|minion|croesus)");
    private static final Pattern STORAGE_MENU = Pattern.compile("(?i)(chest|large chest|personal vault|chest storage|ender chest.*\\(\\d+/\\d+\\)|backpack.*\\(slot #\\d+\\))");
    private static final Set<String> REWARD_CHESTS = Set.of("Wood", "Wood Chest", "Gold", "Gold Chest", "Diamond", "Diamond Chest", "Emerald", "Emerald Chest", "Obsidian", "Obsidian Chest", "Bedrock", "Bedrock Chest", "Free Chest", "Free Chest Chest", "Paid Chest", "Paid Chest Chest");
    private static final NumberFormat LONG_NUMBER = NumberFormat.getIntegerInstance(Locale.US);

    private static LyraConfig cfg;
    private static String loadedProfile = "";
    private static AbstractContainerScreen<?> openScreen;
    private static boolean manualValue;
    private static long lastValueAt;
    private static ValueResult value = ValueResult.EMPTY;
    private static final ThreadPoolExecutor STORAGE_WRITE = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), runnable -> { Thread thread = new Thread(runnable, "constellation-storage-io"); thread.setDaemon(true); return thread; },
        new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final ThreadPoolExecutor STORAGE_READ = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), runnable -> { Thread thread = new Thread(runnable, "constellation-storage-load"); thread.setDaemon(true); return thread; },
        new ThreadPoolExecutor.DiscardOldestPolicy());

    private LyraStorageValue() {}

    public static void init(LyraConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            refreshProfile();
            openScreen = container;
            manualValue = false;
            lastValueAt = 0;
            value = ValueResult.EMPTY;
            ScreenEvents.afterExtract(container).register((ignored, graphics, mouseX, mouseY, delta) -> draw(container, graphics, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(container).register((ignored, event) -> !click(container, event));
            ScreenEvents.remove(container).register(ignored -> close(container));
        });
    }

    private static boolean active() {
        return cfg != null && cfg.enabled && ConstellationClient.loc().onHypixel();
    }

    private static void close(AbstractContainerScreen<?> screen) {
        capture(screen);
        if (openScreen == screen) {
            openScreen = null;
            manualValue = false;
            value = ValueResult.EMPTY;
        }
    }

    private static void capture(AbstractContainerScreen<?> screen) {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/BackpackPreview.java updateStorage
        refreshProfile();
        if (!active() || !cfg.backpackPreview || loadedProfile.isBlank()) return;
        int index = storageIndex(screen.getTitle().getString());
        if (index < 0 || screen.getMenu().slots.isEmpty()) return;
        var container = screen.getMenu().slots.getFirst().container;
        List<ItemStack> items = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) items.add(container.getItem(i).copy());
        STORAGES[index] = new Storage(screen.getTitle().getString(), List.copyOf(items));
        if (cfg.backpackPreviewPersist && !loadedProfile.isBlank()) save();
    }

    private static int storageIndex(String title) {
        Matcher ender = ENDER_CHEST.matcher(title);
        if (ender.find()) { int page = parseInt(ender.group(1), 0); return page >= 1 && page <= 9 ? page - 1 : -1; }
        Matcher backpack = BACKPACK.matcher(title);
        if (backpack.find()) { int slot = parseInt(backpack.group(1), 0); return slot >= 1 && slot <= 18 ? slot + 8 : -1; }
        return -1;
    }

    private static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!active() || screen != openScreen) return;
        refreshProfile();
        if (valueAllowed(screen)) drawValue(screen, graphics, mouseX, mouseY);
    }

    public static boolean renderPreviewTooltip(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/BackpackPreview.java extractPreview
        refreshProfile();
        if (!active() || loadedProfile.isBlank() || !cfg.backpackPreview || !screen.getTitle().getString().equals("Storage")) return false;
        long window = Minecraft.getInstance().getWindow().handle();
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (cfg.backpackPreviewWithoutShift ? shift : !shift) return false;
        Slot hovered = ((ContainerScreenAccessor) screen).constellation$hoveredSlot();
        if (hovered == null || Minecraft.getInstance().player == null || hovered.container == Minecraft.getInstance().player.getInventory()) return false;
        int index = hovered.index;
        if (index >= 9 && index < 18) index -= 9;
        else if (index >= 27 && index < 45) index -= 18;
        else return false;
        if (index < 0 || index >= STORAGE_COUNT || STORAGES[index] == null) return false;
        Storage storage = STORAGES[index];
        int first = Math.min(9, storage.items.size());
        int slots = Math.max(0, storage.items.size() - first);
        int rows = Math.max(1, (slots + 8) / 9);
        float requestedScale = Math.clamp(cfg.backpackPreviewScalePercent, 50, 200) / 100f;
        int panelWidth = 176, panelHeight = 31 + rows * 18 + ((cfg.backpackPreviewShowCount || cfg.backpackPreviewShowValue) ? 12 : 0);
        float availableScale = Math.min((screen.width - 4f) / panelWidth, (screen.height - 4f) / panelHeight);
        float scale = Math.max(0.25f, Math.min(requestedScale, availableScale));
        int scaledWidth = Math.round(panelWidth * scale), scaledHeight = Math.round(panelHeight * scale);
        int x = mouseX + scaledWidth + 8 > screen.width ? mouseX - scaledWidth - 8 : mouseX + 8;
        x = Math.clamp(x, 0, Math.max(0, screen.width - scaledWidth));
        int y = Math.clamp(mouseY - 16, 0, Math.max(0, screen.height - scaledHeight));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.fill(0, 0, panelWidth, panelHeight, cfg.backpackPreviewBackground);
        graphics.fill(0, 0, panelWidth, 1, 0xFF7655A8);
        Font font = Minecraft.getInstance().font;
        graphics.text(font, storage.name, 8, 6, 0xFFFFFFFF, true);
        long total = 0;
        int count = 0;
        for (int i = first; i < storage.items.size(); i++) {
            ItemStack stack = storage.items.get(i);
            if (stack.isEmpty()) continue;
            int itemX = 8 + (i - first) % 9 * 18;
            int itemY = 18 + (i - first) / 9 * 18;
            graphics.item(stack, itemX, itemY);
            graphics.itemDecorations(font, stack, itemX, itemY);
            if (cfg.backpackPreviewSlotText) LyraSlotText.drawStack(graphics, stack, itemX, itemY);
            count += Math.max(1, stack.getCount());
            total += Math.round(itemValue(stack, Math.max(1, stack.getCount())));
        }
        if (cfg.backpackPreviewShowCount || cfg.backpackPreviewShowValue) {
            String footer = (cfg.backpackPreviewShowCount ? count + " items" : "")
                + (cfg.backpackPreviewShowCount && cfg.backpackPreviewShowValue ? "  " : "")
                + (cfg.backpackPreviewShowValue ? compact(total) + " coins" : "");
            graphics.text(font, footer, 8, panelHeight - 11, 0xFFAAAAAA, false);
        }
        graphics.pose().popMatrix();
        return true;
    }

    private static boolean valueAllowed(AbstractContainerScreen<?> screen) {
        if (!cfg.containerValue || !active()) return false;
        if (screen instanceof InventoryScreen && !cfg.containerValueOwnInventory) return false;
        if (!(screen instanceof InventoryScreen) && (!(screen instanceof ContainerScreen) || !(screen.getMenu() instanceof ChestMenu))) return false;
        if (!cfg.containerValueInDungeons && ConstellationClient.loc().inDungeons()) return false;
        String title = screen.getTitle().getString().strip();
        if (EXCLUDED_TITLES.contains(title)) return false;
        if (!(screen instanceof InventoryScreen) && !STORAGE_MENU.matcher(title).matches()) return false;
        return !EXCLUDED_MENUS.matcher(title).find() && !REWARD_CHESTS.contains(title);
    }

    private static void drawValue(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Rect button = valueButton(screen);
        if (cfg.containerValueButton && !cfg.containerValueAutomatic) {
            boolean hover = button.contains(mouseX, mouseY);
            graphics.fill(button.x, button.y, button.x + button.w, button.y + button.h, manualValue ? 0xE055AA55 : hover ? 0xE070527F : 0xD0302538);
            graphics.text(Minecraft.getInstance().font, "$", button.x + 3, button.y + 2, 0xFFFFFFFF, true);
        }
        if (!cfg.containerValueAutomatic && !manualValue) return;
        if (System.currentTimeMillis() - lastValueAt >= 500) recompute(screen);
        renderValue(screen, graphics, mouseX, mouseY);
    }

    private static boolean click(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        if (!valueAllowed(screen) || !cfg.containerValueButton || cfg.containerValueAutomatic || event.button() != 0) return false;
        if (!valueButton(screen).contains((int) event.x(), (int) event.y())) return false;
        manualValue = !manualValue;
        if (manualValue) recompute(screen);
        else value = ValueResult.EMPTY;
        return true;
    }

    private static Rect valueButton(AbstractContainerScreen<?> screen) {
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        return new Rect(accessor.constellation$left() + accessor.constellation$imageWidth() - 16, accessor.constellation$top() + 4, 12, 12);
    }

    private static void recompute(AbstractContainerScreen<?> screen) {
        // ported from SkyHanni (LGPL-3.0-or-later): features/inventory/ChestValue.kt update
        lastValueAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Map<String, MutableEntry> grouped = new HashMap<>();
        long total = 0;
        boolean incomplete = false;
        List<Slot> sourceSlots = screen instanceof InventoryScreen
            ? screen.getMenu().slots
            : screen.getMenu().slots.subList(0, Math.min(screen.getMenu().slots.size(), ((ChestMenu) screen.getMenu()).getRowCount() * 9));
        for (Slot slot : sourceSlots) {
            boolean playerSlot = slot.container == mc.player.getInventory();
            if (screen instanceof InventoryScreen ? !cfg.containerValueOwnInventory : playerSlot) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || (cfg.containerValueIgnoreSoulbound && soulbound(stack))) continue;
            String id = LyraTooltips.marketId(stack);
            if (id.isBlank()) continue;
            int quantity = Math.max(1, stack.getCount());
            double unit = cfg.containerValueUseSellPrice ? PriceProvider.sellValue(id) : PriceProvider.purchaseValue(id);
            if (unit <= 0) { PriceProvider.warm(id); incomplete = true; continue; }
            long amount = Math.max(0, Math.round(unit * quantity));
            total += amount;
            MutableEntry entry = grouped.computeIfAbsent(id, ignored -> new MutableEntry(stack.copy(), stack.getHoverName().getString()));
            entry.amount += amount;
            entry.quantity += quantity;
            entry.slots.add(slot);
        }
        Comparator<ValueEntry> comparator = Comparator.comparingLong(ValueEntry::amount);
        if (!cfg.containerValueAscending) comparator = comparator.reversed();
        List<ValueEntry> entries = grouped.values().stream().map(MutableEntry::freeze)
            .filter(entry -> entry.amount >= Math.max(0, cfg.containerValueHideBelow))
            .sorted(comparator).limit(Math.clamp(cfg.containerValueMaxItems, 0, 54)).toList();
        value = new ValueResult(total, incomplete, entries);
    }

    private static void renderValue(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int width = 148;
        int wantedRows = cfg.containerValueShowBreakdown ? value.entries.size() : 0;
        int rows = Math.min(wantedRows, Math.max(0, (screen.height - 26) / 18));
        int height = 22 + rows * 18;
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int x = accessor.constellation$left() + accessor.constellation$imageWidth() + 5;
        if (x + width > screen.width) x = Math.max(2, accessor.constellation$left() - width - 5);
        int y = Math.clamp(accessor.constellation$top(), 2, Math.max(2, screen.height - height - 2));
        graphics.fill(x, y, x + width, y + height, 0xE8101018);
        int color = value.incomplete ? cfg.containerValueIncompleteColor : cfg.containerValueCompleteColor;
        graphics.text(font, (value.incomplete ? "Estimated: " : "Value: ") + money(value.total), x + 6, y + 6, color, true);
        for (int i = 0; i < rows; i++) {
            ValueEntry entry = value.entries.get(i);
            int rowY = y + 19 + i * 18;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + 17;
            if (hover) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + 17, 0x604C3860);
                if (cfg.containerValueHighlightSlots) highlightSlots(screen, graphics, entry.slots);
            }
            int textX = x + 5;
            if (cfg.containerValueShowIcons) { graphics.item(entry.icon, x + 3, rowY); textX = x + 22; }
            String amount = money(entry.amount);
            int amountX = x + width - font.width(amount) - 5;
            String name = trim(font, entry.name + (entry.quantity > 1 ? " x" + entry.quantity : ""), Math.max(10, amountX - textX - 4));
            graphics.text(font, name, textX, rowY + 4, 0xFFFFFFFF, false);
            graphics.text(font, amount, amountX, rowY + 4, 0xFFFFD37A, false);
        }
    }

    private static void highlightSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, List<Slot> slots) {
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        for (Slot slot : slots) graphics.fill(accessor.constellation$left() + slot.x, accessor.constellation$top() + slot.y,
            accessor.constellation$left() + slot.x + 16, accessor.constellation$top() + slot.y + 16, 0x8066FF66);
    }

    private static double itemValue(ItemStack stack, int quantity) {
        String id = LyraTooltips.marketId(stack);
        if (id.isBlank()) return 0;
        double unit = cfg.containerValueUseSellPrice ? PriceProvider.sellValue(id) : PriceProvider.purchaseValue(id);
        if (unit <= 0) PriceProvider.warm(id);
        return Math.max(0, unit) * Math.max(1, quantity);
    }

    private static boolean soulbound(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            String text = ChatFormatting.stripFormatting(line.getString());
            if (text != null && (text.contains("Soulbound") || text.contains("Co-op Soulbound"))) return true;
        }
        return false;
    }

    private static void refreshProfile() {
        if (!active()) return;
        String profile = currentProfileKey();
        if (profile.isBlank()) {
            if (!loadedProfile.isBlank()) clearLoadedProfile();
            return;
        }
        if (profile.equals(loadedProfile)) return;
        loadedProfile = profile;
        for (int i = 0; i < STORAGES.length; i++) STORAGES[i] = null;
        if (cfg.backpackPreviewPersist) load();
    }

    private static void clearLoadedProfile() {
        loadedProfile = "";
        for (int i = 0; i < STORAGES.length; i++) STORAGES[i] = null;
    }

    static String currentProfileKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return "";
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display == null) continue;
            String line = ChatFormatting.stripFormatting(display.getString());
            if (line != null && line.startsWith("Profile: ")) {
                String profile = safe(line.substring("Profile: ".length()));
                if (!profile.isBlank()) return mc.getUser().getProfileId() + "/" + profile;
            }
        }
        return "";
    }

    public record CachedItemCount(int amount, int loadedStorages, boolean complete) {}

    public static CachedItemCount cachedItemCount(String itemId) {
        int amount = 0, loaded = 0;
        for (Storage storage : STORAGES) {
            if (storage == null) continue;
            loaded++;
            for (ItemStack stack : storage.items) {
                if (itemId.equals(LyraTooltips.marketId(stack))) amount += stack.getCount();
            }
        }
        return new CachedItemCount(amount, loaded, loaded == STORAGES.length);
    }

    private static Path saveFile() {
        String[] pieces = loadedProfile.split("/", 2);
        String user = pieces.length > 0 ? safe(pieces[0]) : "unknown";
        String profile = pieces.length > 1 ? safe(pieces[1]) : "unknown";
        return FabricLoader.getInstance().getConfigDir().resolve("constellation-storage").resolve(user).resolve(profile).resolve("storages.nbt");
    }

    private static void save() {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/BackpackPreview.java saveStorage
        if (loadedProfile.isBlank()) return;
        try {
            CompoundTag root = new CompoundTag();
            for (int index = 0; index < STORAGES.length; index++) {
                Storage storage = STORAGES[index];
                if (storage == null) continue;
                CompoundTag saved = new CompoundTag();
                saved.putString("name", storage.name);
                ListTag items = new ListTag();
                for (ItemStack stack : storage.items) {
                    Tag tag = ItemStack.OPTIONAL_CODEC.encodeStart(Minecraft.getInstance().player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
                    items.add(tag);
                }
                saved.put("items", items);
                root.put(Integer.toString(index), saved);
            }
            Path file = saveFile();
            STORAGE_WRITE.execute(() -> write(root, file));
        } catch (Exception exception) {
            ConstellationClient.LOGGER.warn("Failed to save storage previews", exception);
        }
    }

    private static void load() {
        // ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/tooltip/BackpackPreview.java loadStorages
        Path file = saveFile();
        if (!Files.isRegularFile(file) || Minecraft.getInstance().player == null) return;
        String expectedProfile = loadedProfile;
        STORAGE_READ.execute(() -> {
            try {
                CompoundTag root = NbtIo.read(file);
                if (root != null) Minecraft.getInstance().execute(() -> decode(root, expectedProfile));
            } catch (Exception exception) {
                ConstellationClient.LOGGER.warn("Failed to load storage previews", exception);
            }
        });
    }

    private static void decode(CompoundTag root, String expectedProfile) {
        if (!expectedProfile.equals(loadedProfile) || Minecraft.getInstance().player == null) return;
        try {
            var ops = Minecraft.getInstance().player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for (int index = 0; index < STORAGES.length; index++) {
                Tag raw = root.get(Integer.toString(index));
                if (!(raw instanceof CompoundTag saved)) continue;
                List<ItemStack> items = new ArrayList<>();
                for (Tag item : saved.getListOrEmpty("items")) {
                    DataResult<ItemStack> decoded = ItemStack.OPTIONAL_CODEC.parse(ops, item);
                    items.add(decoded.result().orElse(ItemStack.EMPTY));
                }
                if (STORAGES[index] == null) STORAGES[index] = new Storage(saved.getStringOr("name", "Storage"), List.copyOf(items));
            }
        } catch (Exception exception) {
            ConstellationClient.LOGGER.warn("Failed to load storage previews", exception);
        }
    }

    private static void write(CompoundTag root, Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.write(root, temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            ConstellationClient.LOGGER.warn("Failed to write storage previews", exception);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("storagepreview")
            .executes(context -> storageStatus())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> storageStatus()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> clearStorage()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scale")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("percent", IntegerArgumentType.integer(50, 200))
                    .executes(context -> scale(IntegerArgumentType.getInteger(context, "percent")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> storageOption(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("containervalue")
            .executes(context -> valueStatus())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> valueStatus()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(context -> toggleValue()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("max")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("items", IntegerArgumentType.integer(0, 54))
                    .executes(context -> setMax(IntegerArgumentType.getInteger(context, "items")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hidebelow")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("coins", IntegerArgumentType.integer(0))
                    .executes(context -> hideBelow(IntegerArgumentType.getInteger(context, "coins")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(context -> valueOption(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "state")))))));
    }

    private static int storageStatus() { local("§eStorage preview " + on(cfg.backpackPreview) + ", no-shift " + on(cfg.backpackPreviewWithoutShift) + ", persistence " + on(cfg.backpackPreviewPersist) + ", cached §f" + cached() + "/27§e, scale §f" + cfg.backpackPreviewScalePercent + "%§e."); return 1; }
    private static int valueStatus() { local("§eContainer value " + on(cfg.containerValue) + ", automatic " + on(cfg.containerValueAutomatic) + ", breakdown " + on(cfg.containerValueShowBreakdown) + ", max §f" + cfg.containerValueMaxItems + "§e, hide below §f" + cfg.containerValueHideBelow + "§e."); return 1; }
    private static int clearStorage() { for (int i = 0; i < STORAGES.length; i++) STORAGES[i] = null; if (!loadedProfile.isBlank()) save(); local("§aStorage preview cache cleared for this profile."); return 1; }
    private static int scale(int percent) { cfg.backpackPreviewScalePercent = percent; saveConfig(); local("§aStorage preview scale updated."); return 1; }
    private static int setMax(int items) { cfg.containerValueMaxItems = items; saveConfig(); local("§aContainer-value item limit updated."); return 1; }
    private static int hideBelow(int coins) { cfg.containerValueHideBelow = coins; saveConfig(); local("§aContainer-value threshold updated."); return 1; }
    private static int toggleValue() { if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen) || !valueAllowed(screen)) { local("§cOpen a supported container first."); return 0; } manualValue = !manualValue; if (manualValue) recompute(screen); local("§eContainer value " + on(manualValue) + "."); return 1; }

    private static int storageOption(String name, String state) {
        Boolean enabled = parseState(state); if (enabled == null) return badState();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled", "preview" -> cfg.backpackPreview = enabled;
            case "noshift", "withoutshift" -> cfg.backpackPreviewWithoutShift = enabled;
            case "persist", "persistence" -> cfg.backpackPreviewPersist = enabled;
            case "value" -> cfg.backpackPreviewShowValue = enabled;
            case "count" -> cfg.backpackPreviewShowCount = enabled;
            case "slottext" -> cfg.backpackPreviewSlotText = enabled;
            default -> { local("§cOption must be enabled, noshift, persist, value, count, or slottext."); return 0; }
        }
        saveConfig(); local("§aStorage-preview option updated."); return 1;
    }

    private static int valueOption(String name, String state) {
        Boolean enabled = parseState(state); if (enabled == null) return badState();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.containerValue = enabled;
            case "button" -> cfg.containerValueButton = enabled;
            case "automatic", "auto" -> cfg.containerValueAutomatic = enabled;
            case "inventory" -> cfg.containerValueOwnInventory = enabled;
            case "dungeons" -> cfg.containerValueInDungeons = enabled;
            case "breakdown" -> cfg.containerValueShowBreakdown = enabled;
            case "icons" -> cfg.containerValueShowIcons = enabled;
            case "highlight" -> cfg.containerValueHighlightSlots = enabled;
            case "compact" -> cfg.containerValueCompact = enabled;
            case "ascending" -> cfg.containerValueAscending = enabled;
            case "soulbound" -> cfg.containerValueIgnoreSoulbound = enabled;
            case "sellprice", "sell" -> cfg.containerValueUseSellPrice = enabled;
            default -> { local("§cOption must be enabled, button, auto, inventory, dungeons, breakdown, icons, highlight, compact, ascending, soulbound, or sellprice."); return 0; }
        }
        saveConfig(); local("§aContainer-value option updated."); return 1;
    }

    private static void saveConfig() { normalize(); ConstellationClient.saveConfig(); }
    private static void normalize() { cfg.backpackPreviewScalePercent = Math.clamp(cfg.backpackPreviewScalePercent, 50, 200); cfg.containerValueMaxItems = Math.clamp(cfg.containerValueMaxItems, 0, 54); cfg.containerValueHideBelow = Math.max(0, cfg.containerValueHideBelow); }
    private static int cached() { int count = 0; for (Storage storage : STORAGES) if (storage != null) count++; return count; }
    private static int badState() { local("§cState must be on or off."); return 0; }
    private static Boolean parseState(String state) { return switch (state.toLowerCase(Locale.ROOT)) { case "on", "true", "yes", "1" -> true; case "off", "false", "no", "0" -> false; default -> null; }; }
    private static String on(boolean enabled) { return enabled ? "§aon" : "§coff"; }
    private static void local(String text) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5Lyra §8> §f" + text)); }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String compact(long value) { if (value < 1_000) return Long.toString(value); if (value < 1_000_000) return String.format(Locale.US, "%.1fk", value / 1_000d); if (value < 1_000_000_000) return String.format(Locale.US, "%.2fM", value / 1_000_000d); return String.format(Locale.US, "%.2fB", value / 1_000_000_000d); }
    private static String money(long amount) { return cfg.containerValueCompact ? compact(amount) : LONG_NUMBER.format(amount); }
    private static String trim(Font font, String text, int width) { if (font.width(text) <= width) return text; return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "..."; }

    private record Storage(String name, List<ItemStack> items) {}
    private record Rect(int x, int y, int w, int h) { boolean contains(int mouseX, int mouseY) { return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h; } }
    private record ValueEntry(ItemStack icon, String name, int quantity, long amount, List<Slot> slots) {}
    private record ValueResult(long total, boolean incomplete, List<ValueEntry> entries) { private static final ValueResult EMPTY = new ValueResult(0, false, List.of()); }
    private static final class MutableEntry {
        private final ItemStack icon; private final String name; private int quantity; private long amount; private final List<Slot> slots = new ArrayList<>();
        private MutableEntry(ItemStack icon, String name) { this.icon = icon; this.name = name; }
        private ValueEntry freeze() { return new ValueEntry(icon, name, quantity, amount, List.copyOf(slots)); }
    }
}
