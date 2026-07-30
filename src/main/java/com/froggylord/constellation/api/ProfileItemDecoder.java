package com.froggylord.constellation.api;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import net.azureaaron.legacyitemdfu.TypeReferences;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.azureaaron.legacyitemdfu.LegacyItemStackFixer.getFirstVersion;
import static net.azureaaron.legacyitemdfu.LegacyItemStackFixer.getFixer;
import static net.azureaaron.legacyitemdfu.LegacyItemStackFixer.getLatestVersion;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/utils/ItemLoader.java, ProfileItemStorage.java
// ported from Skyblocker (LGPL-3.0-or-later): utils/datafixer/LegacyItemStackFixer.java, utils/TextTransformer.java
// uses legacy-item-dfu (Apache-2.0): net.azureaaron:legacy-item-dfu
public final class ProfileItemDecoder {
    private ProfileItemDecoder() {}

    public static CompletableFuture<Result> decode(JsonObject member) {
        return CompletableFuture.supplyAsync(() -> decodeNow(member));
    }

    private static Result decodeNow(JsonObject member) {
        List<Container> containers = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        JsonObject inventory = object(member, "inventory");
        add(containers, failures, "Inventory", inventory, "inv_contents", 4, true, true);
        add(containers, failures, "Armor", inventory, "inv_armor", 1, false, true);
        add(containers, failures, "Equipment", inventory, "equipment_contents", 1, false, false);
        addPages(containers, failures, "Ender Chest", inventory, "ender_chest_contents", 5);
        addPages(containers, failures, "Accessory Bag", object(inventory, "bag_contents"), "talisman_bag", 5);
        add(containers, failures, "Potion Bag", object(inventory, "bag_contents"), "potion_bag", 5, false, false);
        add(containers, failures, "Fishing Bag", object(inventory, "bag_contents"), "fishing_bag", 5, false, false);
        add(containers, failures, "Quiver", object(inventory, "bag_contents"), "quiver", 5, false, false);
        add(containers, failures, "Personal Vault", inventory, "personal_vault_contents", 5, false, false);

        JsonObject backpacks = object(inventory, "backpack_contents");
        backpacks.entrySet().stream().sorted(Comparator.comparingInt(entry -> integer(entry.getKey())))
            .forEach(entry -> add(containers, failures, "Backpack " + entry.getKey(), entry.getValue(), 5, false, false));

        JsonObject loadout = object(member, "loadout");
        addLoadouts(containers, failures, "Wardrobe", object(loadout, "armor"),
            List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
        addLoadouts(containers, failures, "Equipment Sets", object(loadout, "equipment"),
            List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4"));

        containers.removeIf(container -> container.items.isEmpty());
        return new Result(List.copyOf(containers), List.copyOf(failures));
    }

    private static void add(List<Container> out, List<String> failures, String name, JsonObject parent, String key,
                            int rows, boolean hotbar, boolean reverse) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) return;
        try {
            List<ItemStack> items = decodeData(value.getAsJsonObject());
            if (reverse) items = items.reversed();
            if (hotbar && items.size() >= 36) {
                List<ItemStack> fixed = new ArrayList<>(items.subList(9, 36));
                fixed.addAll(items.subList(0, 9));
                items = List.copyOf(fixed);
            }
            out.add(new Container(name, Math.max(1, rows), items, hotbar));
        } catch (Exception e) {
            failures.add(name);
            ConstellationClient.LOGGER.warn("Could not decode Profile Viewer container {}", name);
        }
    }

    private static void add(List<Container> out, List<String> failures, String name, JsonElement value,
                            int rows, boolean hotbar, boolean reverse) {
        if (value == null || !value.isJsonObject()) return;
        try {
            List<ItemStack> items = decodeData(value.getAsJsonObject());
            if (reverse) items = items.reversed();
            out.add(new Container(name, Math.max(1, rows), items, hotbar));
        } catch (Exception e) {
            failures.add(name);
        }
    }

    private static void addPages(List<Container> out, List<String> failures, String name, JsonObject parent, String key, int rows) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) return;
        try {
            List<ItemStack> items = decodeData(value.getAsJsonObject());
            int pageSize = rows * 9;
            for (int from = 0, page = 1; from < items.size(); from += pageSize, page++) {
                int to = Math.min(from + pageSize, items.size());
                out.add(new Container(name + (items.size() > pageSize ? " " + page : ""), rows, List.copyOf(items.subList(from, to)), false));
            }
        } catch (Exception e) {
            failures.add(name);
        }
    }

    private static void addLoadouts(List<Container> out, List<String> failures, String name, JsonObject sets, List<String> slots) {
        List<JsonObject> ordered = sets.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("equipped_set") && entry.getValue().isJsonObject())
            .sorted(Comparator.comparingInt(entry -> integer(entry.getKey())))
            .map(entry -> entry.getValue().getAsJsonObject()).toList();
        if (ordered.isEmpty()) return;
        List<ItemStack> items = new ArrayList<>();
        for (JsonObject set : ordered) {
            for (String slot : slots) {
                JsonElement encoded = set.get(slot);
                try {
                    List<ItemStack> decoded = encoded != null && encoded.isJsonObject() ? decodeData(encoded.getAsJsonObject()) : List.of();
                    items.add(decoded.isEmpty() ? ItemStack.EMPTY : decoded.getFirst());
                } catch (Exception e) {
                    items.add(ItemStack.EMPTY);
                    if (!failures.contains(name)) failures.add(name);
                }
            }
        }
        // the API groups pieces per set; the in-game wardrobe groups the same piece across each row
        List<ItemStack> arranged = new ArrayList<>();
        int pageSize = 9 * slots.size();
        for (int from = 0; from < items.size(); from += pageSize) {
            int to = Math.min(items.size(), from + pageSize);
            List<ItemStack> page = new ArrayList<>(items.subList(from, to));
            while (page.size() < pageSize) page.add(ItemStack.EMPTY);
            for (int offset = 0; offset < slots.size(); offset++)
                for (int i = offset; i < page.size(); i += slots.size()) arranged.add(page.get(i));
        }
        int rows = slots.size();
        for (int from = 0, page = 1; from < arranged.size(); from += pageSize, page++)
            out.add(new Container(name + (arranged.size() > pageSize ? " " + page : ""), rows,
                List.copyOf(arranged.subList(from, Math.min(from + pageSize, arranged.size()))), false));
    }

    private static List<ItemStack> decodeData(JsonObject encoded) throws Exception {
        if (!encoded.has("data") || encoded.get("data").isJsonNull()) return List.of();
        byte[] bytes = Base64.getDecoder().decode(encoded.get("data").getAsString());
        CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        ListTag list = root.getListOrEmpty("i");
        List<ItemStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag legacy = list.getCompoundOrEmpty(i);
            if (legacy.getIntOr("id", 0) == 0) {
                stacks.add(ItemStack.EMPTY);
                continue;
            }
            stacks.add(fix(legacy));
        }
        return List.copyOf(stacks);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack fix(CompoundTag legacy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        RegistryOps<Tag> ops = mc.player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> fixed = getFixer().update(TypeReferences.LEGACY_ITEM_STACK, new Dynamic<>(ops, legacy), getFirstVersion(), getLatestVersion());
        ItemStack stack = ItemStack.CODEC.parse(fixed).setPartial(ItemStack.EMPTY)
            .resultOrPartial(error -> ConstellationClient.LOGGER.debug("Profile item decode: {}", error)).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.has(DataComponents.CUSTOM_NAME))
            stack.set(DataComponents.CUSTOM_NAME, fromLegacy(stack.get(DataComponents.CUSTOM_NAME).getString()));
        if (stack.has(DataComponents.LORE))
            stack.set(DataComponents.LORE, new ItemLore(stack.get(DataComponents.LORE).lines().stream()
                .map(Component::getString).map(ProfileItemDecoder::fromLegacy).map(Component.class::cast).toList()));
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            CompoundTag extra = tag.getCompoundOrEmpty("ExtraAttributes");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(extra.isEmpty() ? tag : extra));
        }
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
            .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true).withHidden(DataComponents.ENCHANTMENTS, true);
        stack.set(DataComponents.TOOLTIP_DISPLAY, display);
        return stack;
    }

    private static MutableComponent fromLegacy(String legacy) {
        MutableComponent result = Component.empty();
        StringBuilder text = new StringBuilder();
        ChatFormatting color = null;
        boolean bold = false, italic = false, underlined = false, strike = false, obfuscated = false;
        for (int i = 0; i <= legacy.length(); i++) {
            boolean code = i + 1 < legacy.length() && legacy.charAt(i) == '§';
            if (code || i == legacy.length()) {
                if (!text.isEmpty()) {
                    result.append(Component.literal(text.toString()).setStyle(Style.EMPTY.withColor(color).withBold(bold)
                        .withItalic(italic).withUnderlined(underlined).withStrikethrough(strike).withObfuscated(obfuscated)));
                    text.setLength(0);
                }
                if (code) {
                    ChatFormatting format = ChatFormatting.getByCode(legacy.charAt(++i));
                    if (format == ChatFormatting.BOLD) bold = true;
                    else if (format == ChatFormatting.ITALIC) italic = true;
                    else if (format == ChatFormatting.UNDERLINE) underlined = true;
                    else if (format == ChatFormatting.STRIKETHROUGH) strike = true;
                    else if (format == ChatFormatting.OBFUSCATED) obfuscated = true;
                    else {
                        color = format == ChatFormatting.RESET ? null : format;
                        bold = italic = underlined = strike = obfuscated = false;
                    }
                }
            } else text.append(legacy.charAt(i));
        }
        return result;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }
    private static int integer(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return Integer.MAX_VALUE; } }

    public record Container(String name, int rows, List<ItemStack> items, boolean hotbar) {}
    public record Result(List<Container> containers, List<String> failures) {}
}
