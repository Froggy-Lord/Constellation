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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashSet;
import java.util.Set;

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

    // ported from SkyBlockPv (modified MIT): data/museum/MuseumData.kt
    // Portions of this code are from the SkyBlockPv mod.
    public static SpecialIds decodeMuseumSpecial(JsonElement encodedSpecial) {
        Set<String> ids = new LinkedHashSet<>();
        int failures = 0;
        if (encodedSpecial == null || !encodedSpecial.isJsonArray()) return new SpecialIds(Set.of(), 0);
        for (JsonElement element : encodedSpecial.getAsJsonArray()) {
            try {
                JsonObject wrapper = element.getAsJsonObject();
                JsonObject items = object(wrapper, "items");
                for (ItemStack stack : decodeData(items)) {
                    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                    if (data == null) continue;
                    CompoundTag root = data.copyTag();
                    CompoundTag extra = root.getCompoundOrEmpty("ExtraAttributes");
                    String id = (extra.isEmpty() ? root : extra).getStringOr("id", "");
                    if (!id.isBlank()) ids.add(id.toUpperCase(Locale.ROOT));
                }
            } catch (Exception ignored) {
                failures++;
            }
        }
        return new SpecialIds(Set.copyOf(ids), failures);
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
        Loadouts loadouts = decodeLoadouts(member, loadout, inventory, failures);
        addLoadouts(containers, failures, "Wardrobe", object(loadout, "armor"),
            List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
        addLoadouts(containers, failures, "Equipment Sets", object(loadout, "equipment"),
            List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4"));

        containers.removeIf(container -> container.items.isEmpty());
        return new Result(List.copyOf(containers), List.copyOf(failures), loadouts);
    }

    // ported from SkyBlockPv (modified MIT): api/data/InventoryData.kt, screens/windowed/tabs/loadout/LoadoutTab.kt
    // Portions of this code are from the SkyBlockPv mod.
    private static Loadouts decodeLoadouts(JsonObject member, JsonObject loadout, JsonObject inventory,
                                           List<String> failures) {
        if (loadout.isEmpty()) return Loadouts.EMPTY;
        JsonObject rawArmor = object(loadout, "armor");
        JsonObject rawEquipment = object(loadout, "equipment");
        int equippedArmorSet = integer(rawArmor.get("equipped_set"));
        int equippedEquipmentSet = integer(rawEquipment.get("equipped_set"));
        Map<Integer, List<ItemStack>> armorSets = decodeSets(rawArmor,
            List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"), "Loadout armor", failures);
        Map<Integer, List<ItemStack>> equipmentSets = decodeSets(rawEquipment,
            List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4"),
            "Loadout equipment", failures);
        List<ItemStack> equippedArmor = decodeContainer(object(inventory, "inv_armor"), true,
            "Equipped armor", failures);
        List<ItemStack> equippedEquipment = decodeContainer(object(inventory, "equipment_contents"), false,
            "Equipped equipment", failures);

        List<SavedLoadout> saved = new ArrayList<>();
        JsonObject rawSaved = object(loadout, "loadouts");
        rawSaved.entrySet().stream().filter(entry -> entry.getValue().isJsonObject())
            .sorted(Comparator.comparingInt(entry -> integer(entry.getKey()))).forEach(entry -> {
                JsonObject value = entry.getValue().getAsJsonObject();
                int id = value.has("id") ? integer(value.get("id")) : integer(entry.getKey());
                saved.add(new SavedLoadout(id, string(value.get("name"), "Template " + id),
                    optionalInt(value.get("armor_set_id")), optionalInt(value.get("equipment_set_id")),
                    optionalInt(value.get("mining_core_selected_slot")),
                    optionalInt(value.get("foraging_core_selected_slot")),
                    string(value.get("power_stone"), null), optionalInt(value.get("tuning_points_slot")),
                    string(value.get("pet"), null), true));
            });
        return new Loadouts(true, equippedArmorSet, equippedEquipmentSet, Map.copyOf(armorSets),
            Map.copyOf(equipmentSets), List.copyOf(equippedArmor), List.copyOf(equippedEquipment),
            List.copyOf(saved));
    }

    private static Map<Integer, List<ItemStack>> decodeSets(JsonObject sets, List<String> slots, String name,
                                                            List<String> failures) {
        Map<Integer, List<ItemStack>> decoded = new LinkedHashMap<>();
        sets.entrySet().stream().filter(entry -> !entry.getKey().equals("equipped_set")
            && entry.getValue().isJsonObject()).sorted(Comparator.comparingInt(entry -> integer(entry.getKey())))
            .forEach(entry -> {
                JsonObject set = entry.getValue().getAsJsonObject();
                int id = set.has("id") ? integer(set.get("id")) : integer(entry.getKey());
                List<ItemStack> items = new ArrayList<>();
                for (String slot : slots) items.add(decodeSlot(set.get(slot), name, failures));
                decoded.put(id, List.copyOf(items));
            });
        return decoded;
    }

    private static ItemStack decodeSlot(JsonElement encoded, String name, List<String> failures) {
        if (encoded == null || !encoded.isJsonObject()) return ItemStack.EMPTY;
        try {
            List<ItemStack> items = decodeData(encoded.getAsJsonObject());
            return items.isEmpty() ? ItemStack.EMPTY : items.getFirst();
        } catch (Exception ignored) {
            if (!failures.contains(name)) failures.add(name);
            return ItemStack.EMPTY;
        }
    }

    private static List<ItemStack> decodeContainer(JsonObject encoded, boolean reverse, String name,
                                                   List<String> failures) {
        if (encoded.isEmpty()) return List.of();
        try {
            List<ItemStack> items = decodeData(encoded);
            return reverse ? items.reversed() : items;
        } catch (Exception ignored) {
            if (!failures.contains(name)) failures.add(name);
            return List.of();
        }
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
            stacks.add(fixLegacyStack(legacy));
        }
        return List.copyOf(stacks);
    }

    @SuppressWarnings("unchecked")
    public static ItemStack fixLegacyStack(CompoundTag legacy) {
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
            // Constellation's tooltip/value pipeline reads the original ExtraAttributes wrapper.
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
    private static int integer(JsonElement value) { try { return value == null ? 0 : value.getAsInt(); } catch (Exception ignored) { return 0; } }
    private static Integer optionalInt(JsonElement value) {
        try { return value == null || value.isJsonNull() ? null : value.getAsInt(); }
        catch (Exception ignored) { return null; }
    }
    private static String string(JsonElement value, String fallback) {
        try { return value == null || value.isJsonNull() ? fallback : value.getAsString(); }
        catch (Exception ignored) { return fallback; }
    }

    public record Container(String name, int rows, List<ItemStack> items, boolean hotbar) {}
    public record SavedLoadout(int id, String name, Integer armorSetId, Integer equipmentSetId,
                               Integer miningPreset, Integer foragingPreset, String powerStone,
                               Integer tuningSlot, String petUuid, boolean saved) {
        public boolean empty() {
            return armorSetId == null && equipmentSetId == null && miningPreset == null
                && foragingPreset == null && powerStone == null && tuningSlot == null && petUuid == null;
        }
        public static SavedLoadout locked(int id) {
            return new SavedLoadout(id, "Template " + id, null, null, null, null,
                null, null, null, false);
        }
    }
    public record Loadouts(boolean available, int equippedArmorSet, int equippedEquipmentSet,
                           Map<Integer, List<ItemStack>> armorSets,
                           Map<Integer, List<ItemStack>> equipmentSets,
                           List<ItemStack> equippedArmor, List<ItemStack> equippedEquipment,
                           List<SavedLoadout> saved) {
        public static final Loadouts EMPTY = new Loadouts(false, 0, 0, Map.of(), Map.of(),
            List.of(), List.of(), List.of());
        public List<ItemStack> armor(SavedLoadout loadout) {
            if (loadout == null || loadout.armorSetId() == null) return List.of();
            return loadout.armorSetId() == equippedArmorSet ? equippedArmor
                : armorSets.getOrDefault(loadout.armorSetId(), List.of());
        }
        public List<ItemStack> equipment(SavedLoadout loadout) {
            if (loadout == null || loadout.equipmentSetId() == null) return List.of();
            return loadout.equipmentSetId() == equippedEquipmentSet ? equippedEquipment
                : equipmentSets.getOrDefault(loadout.equipmentSetId(), List.of());
        }
    }
    public record Result(List<Container> containers, List<String> failures, Loadouts loadouts) {}
    public record SpecialIds(Set<String> ids, int failures) {}
}
