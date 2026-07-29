package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AurigaConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SkyblockAddons (LGPL-3.0-only): mixin/hooks/AbstractContainerScreenHook.java, utils/ItemUtils.java, utils/Utils.java
public final class AurigaReforgeHelper {
    public enum Menu { NONE, BASIC, HEX }
    public record State(Menu menu, String item, String current, boolean matched, long cost,
                        int attempts, long spent, String lastResult) {}

    private static final Pattern COST = Pattern.compile("([\\d,]+) Coins");
    private static final Pattern SUCCESS = Pattern.compile("^You (?:reforged your .+ into an? .+|applied an? .+ to your .+)!$");
    private static final Pattern CANDIDATE = Pattern.compile("(?:Applies?|Apply) (?:the )?([A-Za-z][A-Za-z '\\-]+?)(?: reforge)?(?:\\.|$)", Pattern.CASE_INSENSITIVE);
    private static AurigaConfig cfg;
    private static AbstractContainerScreen<?> screen;
    private static Menu menu = Menu.NONE;
    private static State state = empty();
    private static FilterBox includes;
    private static FilterBox excludes;
    private static String lastModifier = "";
    private static boolean matchLatched;
    private static long pendingCost;
    private static long pendingAt;
    private static int attempts;
    private static long spent;
    private static String lastResult = "";

    private AurigaReforgeHelper() {}

    public static void init(AurigaConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, opened, width, height) -> {
            if (!(opened instanceof AbstractContainerScreen<?> container)) return;
            Menu found = menu(container.getTitle().getString());
            if (found == Menu.NONE || cfg == null || !cfg.enabled || !cfg.reforgeHelper
                || !ConstellationClient.loc().onHypixel()) return;
            screen = container;
            menu = found;
            createFields(container);
            update(container);
            ScreenEvents.afterTick(opened).register(ignored -> update(container));
            ScreenEvents.afterExtract(opened).register((ignored, graphics, mouseX, mouseY, delta) -> drawOverlay(container, graphics));
            ScreenKeyboardEvents.allowKeyPress(opened).register((ignored, event) -> allowKey(event));
            ScreenMouseEvents.allowMouseClick(opened).register((ignored, event) -> allowMouse(event));
            ScreenEvents.remove(opened).register(ignored -> close(container));
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) chat(plain(message.getString()));
            return true;
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reforgehelper")
            .executes(c -> status())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle").executes(c -> {
                cfg.reforgeHelper = !cfg.reforgeHelper;
                save();
                return status();
            }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset").executes(c -> resetSession()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("include")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("reforges", StringArgumentType.greedyString())
                    .executes(c -> setFilter(true, StringArgumentType.getString(c, "reforges")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("exclude")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("reforges", StringArgumentType.greedyString())
                    .executes(c -> setFilter(false, StringArgumentType.getString(c, "reforges")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("include").executes(c -> setFilter(true, "")))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("exclude").executes(c -> setFilter(false, "")))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("all").executes(c -> {
                    cfg.reforgeIncludes = "";
                    cfg.reforgeExcludes = "";
                    refreshFields();
                    save();
                    return status();
                })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("option")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.word())
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("state", StringArgumentType.word())
                        .executes(c -> option(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "state")))))));
    }

    private static void update(AbstractContainerScreen<?> container) {
        if (!active(container)) return;
        int itemSlot = menu == Menu.HEX ? 19 : 13;
        int buttonSlot = menu == Menu.HEX ? 48 : 22;
        if (container.getMenu().slots.size() <= Math.max(itemSlot, buttonSlot)) return;
        ItemStack item = container.getMenu().getSlot(itemSlot).getItem();
        String modifier = reforge(item);
        boolean matched = matches(modifier);
        long cost = cost(container.getMenu().getSlot(buttonSlot).getItem());
        if (!modifier.equals(lastModifier)) {
            lastModifier = modifier;
            lastResult = modifier;
            if (matched && !matchLatched) {
                matchLatched = true;
                if (cfg.reforgeMatchChat) local("Matched " + modifier + ".");
                if (cfg.reforgeMatchSound && Minecraft.getInstance().player != null)
                    Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .8f, 1.3f);
            } else if (!matched) matchLatched = false;
        }
        state = new State(menu, name(item), modifier.isBlank() ? "None" : modifier, matched, cost,
            attempts, spent, lastResult);
    }

    public static boolean shouldBlockClick(AbstractContainerScreen<?> container, Slot slot, int slotId) {
        if (!active(container)) return false;
        int index = slot == null ? slotId : slot.index;
        boolean action = menu == Menu.BASIC ? index == 22 : index == 48 || candidate(slot) != null;
        if (!action) return false;
        if (cfg.reforgeBlockMatched && state.matched() && !bypass()) {
            local("Blocked another reforge because " + state.current() + " matches the filter. Hold Control to bypass.");
            return true;
        }
        pendingCost = state.cost();
        pendingAt = System.currentTimeMillis();
        return false;
    }

    private static void chat(String text) {
        if (!active(screen) || !cfg.reforgeTrackSession || !SUCCESS.matcher(text).matches()) return;
        attempts++;
        if (System.currentTimeMillis() - pendingAt <= 5_000L && pendingCost > 0) spent += pendingCost;
        pendingAt = 0;
        pendingCost = 0;
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> container, Slot slot) {
        if (!active(container) || slot == null || !cfg.reforgeHighlightCandidates || menu != Menu.HEX) return;
        String candidate = candidate(slot);
        if (candidate != null && matches(candidate))
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, cfg.reforgeCandidateColor);
    }

    private static void drawOverlay(AbstractContainerScreen<?> container, GuiGraphicsExtractor graphics) {
        if (!active(container) || !cfg.reforgeCurrentOverlay || state.current().equals("None")) return;
        int itemSlot = menu == Menu.HEX ? 19 : 13;
        Slot slot = container.getMenu().getSlot(itemSlot);
        int color = state.matched() ? cfg.reforgeMatchColor : cfg.reforgeCurrentColor;
        String text = state.current();
        int x = slot.x + ((ContainerScreenAccessor) container).constellation$left() + 8 - container.getFont().width(text) / 2;
        int y = slot.y + ((ContainerScreenAccessor) container).constellation$top() + 20;
        graphics.text(container.getFont(), text, x, y, color, true);
    }

    public static List<Component> appendTooltip(AbstractContainerScreen<?> container, ItemStack stack, List<Component> original) {
        if (!active(container) || !cfg.reforgeShowTooltip) return original;
        Slot hovered = ((ContainerScreenAccessor) container).constellation$hoveredSlot();
        if (hovered == null) return original;
        int itemSlot = menu == Menu.HEX ? 19 : 13;
        int buttonSlot = menu == Menu.HEX ? 48 : 22;
        String candidate = candidate(hovered);
        if (hovered.index != itemSlot && hovered.index != buttonSlot && candidate == null) return original;
        ArrayList<Component> out = new ArrayList<>(original);
        out.add(Component.literal("\u00a78----------------"));
        if (hovered.index == itemSlot)
            out.add(Component.literal("\u00a77Current reforge: " + (state.matched() ? "\u00a7a" : "\u00a7e") + state.current()));
        if (hovered.index == buttonSlot && state.cost() > 0)
            out.add(Component.literal("\u00a77Reforge cost: \u00a76" + coins(state.cost())));
        if (candidate != null)
            out.add(Component.literal("\u00a77Candidate: " + (matches(candidate) ? "\u00a7a" : "\u00a7e") + candidate));
        if (!filters(true).isEmpty()) out.add(Component.literal("\u00a77Wanted: \u00a7b" + String.join(", ", filters(true))));
        if (!filters(false).isEmpty()) out.add(Component.literal("\u00a77Excluded: \u00a7c" + String.join(", ", filters(false))));
        return out;
    }

    private static void createFields(AbstractContainerScreen<?> container) {
        includes = null;
        excludes = null;
        if (!cfg.reforgeFilterEditor) return;
        Font font = container.getFont();
        int left = ((ContainerScreenAccessor) container).constellation$left();
        int width = Math.clamp(left - 30, 90, 180);
        int x = Math.max(10, left - width - 10);
        int y = Math.max(20, container.height / 2 - 30);
        includes = new FilterBox(font, width, "Wanted reforges");
        excludes = new FilterBox(font, width, "Excluded substrings");
        includes.setPosition(x, y);
        excludes.setPosition(x, y + 38);
        includes.setValue(cfg.reforgeIncludes);
        excludes.setValue(cfg.reforgeExcludes);
        includes.setResponder(value -> { cfg.reforgeIncludes = cleanFilter(value); save(); });
        excludes.setResponder(value -> { cfg.reforgeExcludes = cleanFilter(value); save(); });
        Screens.getWidgets(container).addFirst(excludes);
        Screens.getWidgets(container).addFirst(includes);
    }

    private static boolean allowKey(KeyEvent event) {
        if (includes == null) return true;
        if ((includes.isFocused() || excludes.isFocused())
            && event.key() != GLFW.GLFW_KEY_ESCAPE
            && Minecraft.getInstance().options.keyInventory.matches(event)) return false;
        return true;
    }

    private static boolean allowMouse(MouseButtonEvent event) {
        if (includes == null) return true;
        if (includes.isMouseOver(event.x(), event.y())) return !includes.mouseClicked(event, false);
        if (excludes.isMouseOver(event.x(), event.y())) return !excludes.mouseClicked(event, false);
        includes.setFocused(false);
        excludes.setFocused(false);
        return true;
    }

    private static void close(AbstractContainerScreen<?> container) {
        if (screen != container) return;
        screen = null;
        menu = Menu.NONE;
        includes = null;
        excludes = null;
        state = empty();
        lastModifier = "";
        matchLatched = false;
        pendingAt = 0;
        pendingCost = 0;
    }

    private static String candidate(Slot slot) {
        if (slot == null || slot.getItem().isEmpty() || menu != Menu.HEX || slot.index == 19 || slot.index == 48) return null;
        ItemLore lore = slot.getItem().get(DataComponents.LORE);
        if (lore == null) return null;
        boolean applicable = false;
        for (Component line : lore.lines()) {
            String clean = plain(line.getString());
            Matcher matcher = CANDIDATE.matcher(clean);
            if (matcher.find()) return title(matcher.group(1).trim());
            if (clean.contains("Click to apply") || clean.contains("Click to select") || clean.contains("Applies the "))
                applicable = true;
        }
        if (!applicable) return null;
        String name = plain(slot.getItem().getHoverName().getString())
            .replaceAll("(?i)\\s+Reforge(?: Stone)?$", "").trim();
        return name.isBlank() || name.contains("Page") ? null : title(name);
    }

    private static String reforge(ItemStack item) {
        CompoundTag extra = extra(item);
        String modifier = extra.getStringOr("modifier", "");
        if (modifier.isBlank()) return "";
        String value = title(modifier.replace("_sword", "").replace("_bow", ""));
        return value.equalsIgnoreCase("Warped") ? "Hyper" : value;
    }

    public static boolean matches(String reforge) {
        if (reforge == null || reforge.isBlank()) return false;
        String value = normalize(reforge);
        boolean wanted = false;
        for (String include : filters(true)) if (value.contains(normalize(include))) { wanted = true; break; }
        if (!wanted) return false;
        for (String exclude : filters(false)) if (value.contains(normalize(exclude))) return false;
        return true;
    }

    private static List<String> filters(boolean wanted) {
        String raw = wanted ? cfg.reforgeIncludes : cfg.reforgeExcludes;
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static long cost(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            Matcher matcher = COST.matcher(plain(line.getString()));
            if (matcher.find()) try { return Long.parseLong(matcher.group(1).replace(",", "")); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return -1;
    }

    public static State state() { return state; }
    public static AurigaConfig config() { return cfg; }
    public static boolean visible() { return cfg != null && cfg.reforgeHud && active(screen); }

    private static Menu menu(String title) {
        String clean = plain(title);
        if (clean.equals("Reforge Item")) return Menu.BASIC;
        if (clean.equals("The Hex \u279c Reforges") || clean.equals("The Hex -> Reforges")) return Menu.HEX;
        return Menu.NONE;
    }
    private static boolean active(AbstractContainerScreen<?> container) {
        return container != null && container == screen && cfg != null && cfg.enabled && cfg.reforgeHelper
            && ConstellationClient.loc().onHypixel() && menu != Menu.NONE;
    }
    private static boolean bypass() {
        var window = Minecraft.getInstance().getWindow();
        return cfg.reforgeControlBypass && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL));
    }
    private static CompoundTag extra(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return new CompoundTag();
        CompoundTag root = data.copyTag();
        CompoundTag legacy = root.getCompoundOrEmpty("ExtraAttributes");
        return legacy.isEmpty() ? root : legacy;
    }
    private static String name(ItemStack stack) { return stack == null || stack.isEmpty() ? "Empty" : plain(stack.getHoverName().getString()); }
    private static String normalize(String value) { return cfg.reforgeCaseInsensitive ? value.toLowerCase(Locale.ROOT) : value; }
    private static String plain(String value) { String clean = ChatFormatting.stripFormatting(value); return clean == null ? value.trim() : clean.trim(); }
    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
    private static String cleanFilter(String value) { return value == null ? "" : value.replaceAll("[\\r\\n]", "").strip(); }
    private static String coins(long value) { return String.format(Locale.ROOT, "%,d Coins", value); }

    private static int status() {
        local("Helper " + on(cfg.reforgeHelper) + ", menu " + state.menu().name().toLowerCase(Locale.ROOT)
            + ", current " + state.current() + ", matched " + (state.matched() ? "yes" : "no")
            + ", attempts " + attempts + ", spent " + coins(spent) + ".");
        return 1;
    }
    private static int resetSession() {
        attempts = 0;
        spent = 0;
        lastResult = "";
        return status();
    }
    private static int setFilter(boolean wanted, String value) {
        if (wanted) cfg.reforgeIncludes = cleanFilter(value);
        else cfg.reforgeExcludes = cleanFilter(value);
        refreshFields();
        save();
        return status();
    }
    private static void refreshFields() {
        if (includes != null && !includes.getValue().equals(cfg.reforgeIncludes)) includes.setValue(cfg.reforgeIncludes);
        if (excludes != null && !excludes.getValue().equals(cfg.reforgeExcludes)) excludes.setValue(cfg.reforgeExcludes);
    }
    private static int option(String name, String raw) {
        Boolean value = bool(raw);
        if (value == null) { local("State must be on or off."); return 0; }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "enabled" -> cfg.reforgeHelper = value;
            case "hud" -> cfg.reforgeHud = value;
            case "overlay" -> cfg.reforgeCurrentOverlay = value;
            case "editor" -> cfg.reforgeFilterEditor = value;
            case "block" -> cfg.reforgeBlockMatched = value;
            case "bypass" -> cfg.reforgeControlBypass = value;
            case "chat" -> cfg.reforgeMatchChat = value;
            case "sound" -> cfg.reforgeMatchSound = value;
            case "candidates" -> cfg.reforgeHighlightCandidates = value;
            case "tooltip" -> cfg.reforgeShowTooltip = value;
            case "item" -> cfg.reforgeShowItem = value;
            case "cost" -> cfg.reforgeShowCost = value;
            case "attempts" -> cfg.reforgeShowAttempts = value;
            case "spent" -> cfg.reforgeShowSpent = value;
            case "last" -> cfg.reforgeShowLastResult = value;
            case "tracking" -> cfg.reforgeTrackSession = value;
            case "ignorecase" -> cfg.reforgeCaseInsensitive = value;
            default -> { local("Unknown Reforge Helper option."); return 0; }
        }
        save();
        return status();
    }
    private static Boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> null;
        };
    }
    private static String on(boolean value) { return value ? "on" : "off"; }
    private static void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a75[Reforge Helper] \u00a7f" + text));
    }
    private static void save() { ConstellationClient.saveConfig(); }
    private static State empty() { return new State(Menu.NONE, "Empty", "None", false, -1, attempts, spent, lastResult); }

    private static final class FilterBox extends EditBox {
        private final Font font;
        private final String label;
        private FilterBox(Font font, int width, String label) {
            super(font, width, 18, Component.literal(label));
            this.font = font;
            this.label = label;
            setMaxLength(500);
        }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            graphics.text(font, label, getX(), getY() - font.lineHeight - 1, 0xFFAAAAAA, true);
        }
        @Override public boolean keyPressed(KeyEvent event) {
            return super.keyPressed(event) || (isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE);
        }
    }
}
