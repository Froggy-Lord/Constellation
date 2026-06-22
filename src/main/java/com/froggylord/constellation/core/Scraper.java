package com.froggylord.constellation.core;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.data.DungeonScore;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import com.froggylord.constellation.mixin.MapDataAccessor;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * On-demand data scrapers that dump game state to JSON files. Run one before/after
 * entering a room, opening a GUI, or triggering a chat event, and the captured data
 * shows exactly what the mod sees — sidebar patterns, tab layouts, entity names,
 * GUI slot contents, map pixels etc.
 *
 * Usage: /cn scrape <mode>
 *   sidebar   — current sidebar lines
 *   tab       — current tab list lines
 *   entities  — all entities within 50 blocks (name, type, pos, hp, bb)
 *   actionbar — last parsed health/mana/defense/overflow/skill
 *   gui       — current open container screen slot contents
 *   map       — dungeon map pixel + decoration data
 *   room      — room detection state (name, anchor, rotation, matched)
 *   score     — dungeon score state (score, grade, secrets, crypts, deaths)
 *   chat      — record next 30s of chat messages
 *   all       — run sidebar+tab+entities+actionbar+room+score at once
 *   location  — current location detection state
 */
public final class Scraper {

    private Scraper() {}

    private static final Path DIR = Path.of("config", "constellation-scrapes");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> chatBuffer = new ArrayList<>();
    private static boolean recording = false;
    private static long recordUntil = 0;

    public static void init() {
        try { Files.createDirectories(DIR); } catch (Exception ignored) {}
        // chat recorder — feeds from GAME events while recording is active
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!recording) return;
            chatBuffer.add((overlay ? "[overlay] " : "") + msg.getString());
        });
    }

    // -- dispatcher --

    public static void scrape(String mode) {
        try { Files.createDirectories(DIR); } catch (Exception ignored) {}
        var mc = Minecraft.getInstance();
        if (mc.player == null) { reply("§cScraper: not in game"); return; }

        switch (mode) {
            case "sidebar"   -> save("sidebar",   scrapeSidebar());
            case "tab"       -> save("tab",        scrapeTab());
            case "entities"  -> save("entities",   scrapeEntities(mc));
            case "actionbar" -> save("actionbar",  scrapeActionBar());
            case "gui"       -> save("gui",         scrapeGui(mc));
            case "map"       -> save("map",         scrapeMap(mc));
            case "room"      -> save("room",        scrapeRoom());
            case "score"     -> save("score",       scrapeScore());
            case "location"  -> save("location",    scrapeLocation());
            case "chat"      -> startChatRecord();
            case "all"       -> scrapeAll(mc);
            default -> reply("§cScraper: unknown mode '" + mode + "'. Try: sidebar tab entities actionbar gui map room score location chat all");
        }
    }

    // -- individual scrapers --

    private static JsonObject scrapeSidebar() {
        JsonArray arr = new JsonArray();
        for (String line : ConstellationClient.loc().getSidebarLines()) arr.add(line);
        JsonObject o = new JsonObject();
        o.add("sidebarLines", arr);
        o.addProperty("count", arr.size());
        return o;
    }

    private static JsonObject scrapeTab() {
        JsonArray arr = new JsonArray();
        for (String line : TabList.lines()) arr.add(line);
        JsonObject o = new JsonObject();
        o.add("tabLines", arr);
        o.addProperty("count", arr.size());
        return o;
    }

    private static JsonObject scrapeEntities(Minecraft mc) {
        JsonArray arr = new JsonArray();
        var pp = mc.player.position();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(pp) > 2500) continue; // 50-block radius
            JsonObject ent = new JsonObject();
            ent.addProperty("type", e.getType().getDescriptionId());
            ent.addProperty("className", e.getClass().getSimpleName());
            ent.addProperty("x", Math.round(e.getX() * 100.0) / 100.0);
            ent.addProperty("y", Math.round(e.getY() * 100.0) / 100.0);
            ent.addProperty("z", Math.round(e.getZ() * 100.0) / 100.0);
            ent.addProperty("bbHeight", Math.round(e.getBbHeight() * 100.0) / 100.0);
            ent.addProperty("bbWidth", Math.round(e.getBbWidth() * 100.0) / 100.0);
            Component cn = e.getCustomName();
            if (cn != null) ent.addProperty("customName", cn.getString());
            Component dn = e.getDisplayName();
            if (dn != null && !dn.getString().isEmpty()) ent.addProperty("displayName", dn.getString());
            if (e instanceof LivingEntity le) {
                ent.addProperty("health", Math.round(le.getHealth() * 10.0) / 10.0);
                ent.addProperty("maxHealth", Math.round(le.getMaxHealth() * 10.0) / 10.0);
            }
            arr.add(ent);
        }
        JsonObject o = new JsonObject();
        o.add("entities", arr);
        o.addProperty("count", arr.size());
        o.addProperty("playerX", Math.round(pp.x * 100.0) / 100.0);
        o.addProperty("playerY", Math.round(pp.y * 100.0) / 100.0);
        o.addProperty("playerZ", Math.round(pp.z * 100.0) / 100.0);
        return o;
    }

    private static JsonObject scrapeActionBar() {
        JsonObject o = new JsonObject();
        o.addProperty("health", ActionBar.health());
        o.addProperty("maxHealth", ActionBar.maxHealth());
        o.addProperty("mana", ActionBar.mana());
        o.addProperty("maxMana", ActionBar.maxMana());
        o.addProperty("defense", ActionBar.defense());
        o.addProperty("overflowMana", ActionBar.overflowMana());
        o.addProperty("effectiveHealth", ActionBar.effectiveHealth());
        o.addProperty("hasData", ActionBar.hasData());
        if (ActionBar.hasSkill()) {
            o.addProperty("skillName", ActionBar.skillName());
            o.addProperty("skillPercent", ActionBar.skillPercent());
        }
        return o;
    }

    private static JsonObject scrapeGui(Minecraft mc) {
        JsonObject o = new JsonObject();
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) {
            o.addProperty("error", "no container screen open");
            return o;
        }
        o.addProperty("title", cs.getTitle().getString());
        o.addProperty("containerId", cs.getMenu().containerId);
        o.addProperty("totalSlots", cs.getMenu().slots.size());
        int chest = cs.getMenu().slots.size() - 36;
        o.addProperty("chestSlots", chest);
        JsonArray slots = new JsonArray();
        for (int i = 0; i < chest; i++) {
            Slot slot = cs.getMenu().slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            JsonObject s = new JsonObject();
            s.addProperty("slotIndex", i);
            s.addProperty("slotX", slot.x);
            s.addProperty("slotY", slot.y);
            s.addProperty("itemId", stack.getItem().getDescriptionId());
            s.addProperty("count", stack.getCount());
            s.addProperty("hoverName", stack.getHoverName().getString());
            s.addProperty("hasFoil", stack.hasFoil());
            // NBT ExtraAttributes dump
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
                CompoundTag extra = cd.copyTag().getCompoundOrEmpty("ExtraAttributes");
                if (!extra.isEmpty()) {
                    JsonObject nbt = new JsonObject();
                    for (String key : extra.keySet()) {
                        String val = extra.getStringOr(key, null);
                        if (val == null) val = String.valueOf(extra.getIntOr(key, -999));
                        nbt.addProperty(key, val);
                    }
                    s.add("extraAttributes", nbt);
                }
            }
            // LORE dump
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                JsonArray loreArr = new JsonArray();
                for (var line : lore.lines()) loreArr.add(line.getString());
                s.add("lore", loreArr);
            }
            slots.add(s);
        }
        o.add("slots", slots);
        return o;
    }

    private static JsonObject scrapeMap(Minecraft mc) {
        JsonObject o = new JsonObject();
        // find dungeon map in inventory
        var inv = mc.player.getInventory();
        MapItemSavedData map = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id == null) continue;
            map = mc.level.getMapData(id);
            if (map != null) break;
        }
        if (map == null) { o.addProperty("error", "no map in inventory"); return o; }
        o.addProperty("centerX", map.centerX);
        o.addProperty("centerZ", map.centerZ);
        o.addProperty("dimension", map.dimension.registry().toString());
        o.addProperty("scale", map.scale);
        // dump decorations (player markers, room markers etc.)
        var decos = ((MapDataAccessor) (Object) map).constellation$decorations();
        if (decos != null) {
            JsonArray da = new JsonArray();
            for (var d : decos.values()) {
                JsonObject dec = new JsonObject();
                dec.addProperty("x", d.x());
                dec.addProperty("y", d.y());
                dec.addProperty("type", d.type().getRegisteredName());
                var dname = d.name();
                if (dname.isPresent()) dec.addProperty("name", dname.get().getString());
                da.add(dec);
            }
            o.add("decorations", da);
        }
        // dump first 500 color bytes as base64 (too big for full JSON)
        StringBuilder hex = new StringBuilder();
        int max = Math.min(500, map.colors.length);
        for (int i = 0; i < max; i++) {
            hex.append(String.format("%02x", map.colors[i] & 0xFF));
        }
        o.addProperty("colorSample_first500_hex", hex.toString());
        o.addProperty("totalColors", map.colors.length);
        return o;
    }

    private static JsonObject scrapeRoom() {
        JsonObject o = new JsonObject();
        o.addProperty("currentRoom", RoomMatch.currentRoom());
        o.addProperty("isMatched", RoomMatch.isMatched());
        o.addProperty("anchorX", RoomMatch.anchorX());
        o.addProperty("anchorZ", RoomMatch.anchorZ());
        o.addProperty("direction", String.valueOf(RoomMatch.currentDir()));
        o.addProperty("inDungeon", ConstellationClient.loc().inDungeons());
        return o;
    }

    private static JsonObject scrapeScore() {
        JsonObject o = new JsonObject();
        o.addProperty("score", DungeonScore.score());
        o.addProperty("grade", DungeonScore.grade());
        o.addProperty("secretPercent", DungeonScore.secretPercent());
        o.addProperty("crypts", DungeonScore.crypts());
        o.addProperty("deaths", DungeonScore.deaths());
        o.addProperty("timeSeconds", DungeonScore.timeSeconds());
        o.addProperty("floor", DungeonScore.floor());
        o.addProperty("isMimicFloor", DungeonScore.isMimicFloor());
        o.addProperty("mimicKilled", DungeonScore.mimicKilled());
        o.addProperty("inBoss", DungeonScore.inBoss());
        return o;
    }

    private static JsonObject scrapeLocation() {
        JsonObject o = new JsonObject();
        o.addProperty("area", String.valueOf(ConstellationClient.loc().area()));
        o.addProperty("onHypixel", ConstellationClient.loc().onHypixel());
        o.addProperty("inDungeons", ConstellationClient.loc().inDungeons());
        return o;
    }

    private static void startChatRecord() {
        if (recording) {
            recording = false;
            JsonObject o = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String line : chatBuffer) arr.add(line);
            o.add("chatMessages", arr);
            o.addProperty("count", arr.size());
            o.addProperty("recorded", "manual stop");
            save("chat", o);
            chatBuffer.clear();
            reply("§aScraper: chat recording stopped — " + arr.size() + " messages saved");
            return;
        }
        recording = true;
        recordUntil = System.currentTimeMillis() + 30_000;
        chatBuffer.clear();
        reply("§aScraper: recording chat for 30s. Run again to stop early.");
        // auto-stop after 30s
        ConstellationClient.tick().once(600, "scraper-chat-stop", () -> {
            if (!recording) return;
            recording = false;
            JsonObject o = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String line : chatBuffer) arr.add(line);
            o.add("chatMessages", arr);
            o.addProperty("count", arr.size());
            o.addProperty("recorded", "30s auto-stop");
            save("chat", o);
            chatBuffer.clear();
            reply("§aScraper: chat recording auto-stopped — " + arr.size() + " messages saved");
        });
    }

    private static void scrapeAll(Minecraft mc) {
        save("sidebar",  scrapeSidebar());
        save("tab",      scrapeTab());
        save("entities", scrapeEntities(mc));
        save("actionbar",scrapeActionBar());
        save("room",     scrapeRoom());
        save("score",    scrapeScore());
        save("location", scrapeLocation());
        reply("§aScraper: dumped sidebar+tab+entities+actionbar+room+score+location to scrapes folder");
    }

    // -- helpers --

    private static void save(String mode, JsonObject data) {
        String ts = Instant.now().toString().replace(':', '-').substring(0, 19);
        Path file = DIR.resolve(mode + "-" + ts + ".json");
        data.addProperty("_mode", mode);
        data.addProperty("_timestamp", Instant.now().toString());
        try (Writer w = Files.newBufferedWriter(file)) { GSON.toJson(data, w); }
        catch (Exception e) { ConstellationClient.LOGGER.error("Scraper save failed", e); }
        reply("§aScraper: saved " + file.getFileName().toString());
        // also print a quick preview to chat
        reply("§7  → " + DIR.toAbsolutePath().normalize() + "/" + file.getFileName());
    }

    private static void reply(String msg) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }
}
