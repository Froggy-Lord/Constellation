package com.froggylord.constellation.ui;

import com.froggylord.constellation.api.ProfileViewerApi;
import com.froggylord.constellation.api.ProfileItemDecoder;
import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.render.ConstellationTheme;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/ProfileViewer.java, ProfileViewerScreen.java
// ported from SkyBlockPv (modified MIT): screens/BasePvScreen.kt, screens/PvTab.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileViewerScreen extends Screen {
    private static final String[] TABS = {"Overview", "Skills", "Dungeons", "Slayers", "Pets", "Items"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private EditBox player;
    private ProfileViewerApi.Result result;
    private String error = "";
    private boolean loading;
    private int profileIndex;
    private int tab;
    private int scroll;
    private ProfileItemDecoder.Result itemResult;
    private boolean itemLoading;
    private int itemProfile = -1;
    private int itemContainer;
    private int itemContainerScroll;
    private int itemPage;

    public ProfileViewerScreen(Screen parent, String name) {
        super(Component.literal("Profile Viewer"));
        this.parent = parent;
        this.error = name == null ? "" : name;
    }

    @Override protected void init() {
        String initial = error;
        error = "";
        player = new EditBox(font, 12, 12, 132, 18, Component.literal("Player"));
        player.setHint(Component.literal("player name"));
        player.setMaxLength(16);
        player.setValue(initial);
        addRenderableWidget(player);
        if (!initial.isBlank()) load(false);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE0080810);
        button(g, 150, 12, 48, "Open", mx, my);
        button(g, 202, 12, 56, "Refresh", mx, my);
        if (loading) {
            g.text(font, "Loading profile...", 12, 42, ConstellationTheme.TEXT, false);
            return;
        }
        if (!error.isBlank()) g.text(font, error, 12, 42, 0xFFFF7777, false);
        if (result == null) {
            g.text(font, "Search for a player to view their public SkyBlock profiles.", 12, 58, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        JsonObject profile = profile();
        JsonObject member = member(profile);
        String profileName = string(profile, "cute_name", "Profile " + (profileIndex + 1));
        String mode = string(profile, "game_mode", "normal");
        String fetched = TIME.format(Instant.ofEpochMilli(result.fetchedAt()));
        g.text(font, result.name() + "  " + profileName + "  " + mode, 12, 42, ConstellationTheme.ACCENT_BRIGHT, false);
        g.text(font, (result.cached() ? "cached " : "updated ") + fetched, width - 12 - font.width((result.cached() ? "cached " : "updated ") + fetched), 43, ConstellationTheme.TEXT_MUTED, false);
        int px = 12;
        for (int i = 0; i < result.profiles().size(); i++) {
            JsonObject p = result.profiles().get(i).getAsJsonObject();
            int bw = Math.max(52, font.width(string(p, "cute_name", Integer.toString(i + 1))) + 14);
            chip(g, px, 58, bw, string(p, "cute_name", Integer.toString(i + 1)), i == profileIndex, mx, my);
            px += bw + 4;
        }
        int tx = 12;
        for (int i = 0; i < TABS.length; i++) {
            int bw = font.width(TABS[i]) + 16;
            chip(g, tx, 80, bw, TABS[i], i == tab, mx, my);
            tx += bw + 4;
        }
        if (tab == 5) {
            drawItems(g, mx, my);
            return;
        }
        List<Row> rows = rows(profile, member);
        int y = 108 - scroll;
        for (Row row : rows) {
            if (y > 98 && y < height - 22) {
                g.fill(12, y, width - 12, y + 18, 0xA0181825);
                g.text(font, row.label, 19, y + 6, ConstellationTheme.TEXT_MUTED, false);
                g.text(font, row.value, width - 19 - font.width(row.value), y + 6, row.color, false);
            }
            y += 21;
        }
        g.text(font, "Esc to close", 12, height - 13, ConstellationTheme.TEXT_FAINT, false);
    }

    private List<Row> rows(JsonObject profile, JsonObject member) {
        return switch (tab) {
            case 1 -> skills(member);
            case 2 -> dungeons(member);
            case 3 -> slayers(member);
            case 4 -> pets(member);
            default -> overview(profile, member);
        };
    }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/widgets/InventoryWidget.java
    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/pages/InventoryPage.java
    private void drawItems(GuiGraphicsExtractor g, int mx, int my) {
        if (itemProfile != profileIndex && !itemLoading) startItemDecode();
        if (itemLoading) {
            g.text(font, "Decoding profile items...", 14, 112, ConstellationTheme.TEXT, false);
            return;
        }
        if (itemResult == null || itemResult.containers().isEmpty()) {
            g.text(font, "Inventory API disabled or no item data was returned.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        var cfg = ConstellationClient.cfg().lyra;
        List<ProfileItemDecoder.Container> containers = itemResult.containers();
        itemContainer = Math.clamp(itemContainer, 0, containers.size() - 1);
        int visibleMenus = Math.max(2, (height - 136) / 18);
        itemContainerScroll = Math.clamp(itemContainerScroll, 0, Math.max(0, containers.size() - visibleMenus));
        int menuY = 108;
        for (int i = itemContainerScroll; i < Math.min(containers.size(), itemContainerScroll + visibleMenus); i++) {
            String name = containers.get(i).name();
            boolean active = i == itemContainer;
            g.fill(12, menuY, 118, menuY + 16, active ? 0xFF34506A : inside(mx, my, 12, menuY, 106, 16) ? 0xFF303044 : 0xFF20202C);
            String shown = font.width(name) > 94 ? font.plainSubstrByWidth(name, 88) + "..." : name;
            g.text(font, shown, 18, menuY + 5, active ? 0xFFFFFFFF : ConstellationTheme.TEXT_MUTED, false);
            menuY += 18;
        }
        if (!itemResult.failures().isEmpty())
            g.text(font, itemResult.failures().size() + " unavailable", 14, height - 13, 0xFFFFAA55, false);

        ProfileItemDecoder.Container container = containers.get(itemContainer);
        int rows = Math.clamp(cfg.profileViewerInventoryRows, 1, 6);
        rows = Math.min(rows, Math.max(1, (height - 154) / 18));
        int pageSize = rows * 9;
        int pages = Math.max(1, (container.items().size() + pageSize - 1) / pageSize);
        itemPage = Math.clamp(itemPage, 0, pages - 1);
        int gridX = Math.max(130, (width + 118 - 162) / 2);
        int gridY = 126;
        String heading = container.name() + "  " + (itemPage + 1) + "/" + pages;
        g.text(font, heading, gridX, 109, ConstellationTheme.TEXT, false);
        int from = itemPage * pageSize;
        int to = Math.min(container.items().size(), from + pageSize);
        ItemStack hovered = null;
        for (int local = 0; local < pageSize; local++) {
            int slotX = gridX + (local % 9) * 18;
            int row = local / 9;
            int hotbarShift = container.hotbar() && rows >= 4 && row == rows - 1 ? 3 : 0;
            int slotY = gridY + row * 18 + hotbarShift;
            boolean over = inside(mx, my, slotX, slotY, 18, 18);
            g.fill(slotX, slotY, slotX + 18, slotY + 18, over ? cfg.profileViewerInventoryHoverColor : cfg.profileViewerInventorySlotColor);
            int index = from + local;
            if (index >= to) continue;
            ItemStack stack = container.items().get(index);
            if (!stack.isEmpty()) {
                g.fakeItem(stack, slotX + 1, slotY + 1);
                if (cfg.profileViewerInventoryDecorations) g.itemDecorations(font, stack, slotX + 1, slotY + 1);
                if (over) hovered = stack;
            }
        }
        if (pages > 1) {
            button(g, gridX, gridY + rows * 18 + 8, 56, "Previous", mx, my);
            button(g, gridX + 106, gridY + rows * 18 + 8, 56, "Next", mx, my);
        }
        if (hovered != null && cfg.profileViewerInventoryTooltips)
            g.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Minecraft.getInstance(), hovered), mx, my);
    }

    private List<Row> overview(JsonObject profile, JsonObject m) {
        List<Row> out = new ArrayList<>();
        out.add(row("SkyBlock level", compact(number(path(m, "leveling.experience")) / 100.0)));
        out.add(row("Purse", coins(number(number(path(m, "currencies.coin_purse")), number(path(m, "coin_purse"))))));
        out.add(row("Bank", coins(number(path(profile, "banking.balance")))));
        out.add(row("Fairy souls", whole(number(number(path(m, "fairy_soul.total_collected")), number(path(m, "fairy_souls_collected"))))));
        out.add(row("First joined", date((long) number(number(path(m, "profile.first_join")), number(path(m, "first_join"))))));
        out.add(row("Profile type", string(profile, "game_mode", "normal")));
        out.add(row("Co-op members", Integer.toString(object(profile, "members").size())));
        return out;
    }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/pages/SkillsPage.java, utils/LevelCalculator.java
    private List<Row> skills(JsonObject m) {
        List<Row> out = new ArrayList<>();
        for (String skill : List.of("farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry", "runecrafting", "social")) {
            double xp = number(number(path(m, "player_data.experience.SKILL_" + skill.toUpperCase(Locale.ROOT))),
                number(path(m, "experience_skill_" + skill)));
            out.add(row(title(skill), xp <= 0 ? "API disabled or no XP" : compact(xp) + " XP"));
        }
        return out;
    }

    private List<Row> dungeons(JsonObject m) {
        List<Row> out = new ArrayList<>();
        JsonObject d = object(m, "dungeons");
        out.add(row("Catacombs XP", compact(number(path(d, "dungeon_types.catacombs.experience")))));
        out.add(row("Secrets found", whole(number(number(path(m, "player_stats.secrets")), number(path(m, "secrets"))))));
        JsonObject classes = object(d, "player_classes");
        for (String name : List.of("healer", "mage", "berserk", "archer", "tank"))
            out.add(row(title(name) + " XP", compact(number(path(classes, name + ".experience")))));
        JsonObject normal = object(path(d, "dungeon_types"), "catacombs");
        JsonObject master = object(path(d, "dungeon_types"), "master_catacombs");
        for (int floor = 1; floor <= 7; floor++) {
            out.add(row("F" + floor + " completions", whole(number(path(normal, "tier_completions." + floor)))));
            out.add(row("M" + floor + " completions", whole(number(path(master, "tier_completions." + floor)))));
        }
        return out;
    }

    private List<Row> slayers(JsonObject m) {
        List<Row> out = new ArrayList<>();
        JsonObject bosses = object(path(m, "slayer"), "slayer_bosses");
        if (bosses.isEmpty()) bosses = object(m, "slayer_bosses");
        for (String id : List.of("zombie", "spider", "wolf", "enderman", "blaze", "vampire")) {
            JsonObject boss = object(bosses, id);
            out.add(row(title(id), compact(number(path(boss, "xp"))) + " XP"));
            for (int tier = 0; tier <= 4; tier++) {
                double kills = number(path(boss, "boss_kills_tier_" + tier));
                if (kills > 0) out.add(row("  Tier " + (tier + 1), whole(kills) + " kills"));
            }
        }
        return out;
    }

    private List<Row> pets(JsonObject m) {
        List<Row> out = new ArrayList<>();
        JsonArray pets = array(path(m, "pets_data"), "pets");
        if (pets.isEmpty()) pets = array(m, "pets");
        pets.asList().stream().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
            .sorted(Comparator.comparing((JsonObject p) -> bool(p, "active")).reversed().thenComparing(p -> string(p, "type", "")))
            .forEach(p -> out.add(new Row((bool(p, "active") ? "Active  " : "") + title(string(p, "type", "Unknown").replace('_', ' ')),
                string(p, "tier", "Unknown") + "  " + compact(number(p.get("exp"))) + " XP", bool(p, "active") ? 0xFF55FF55 : ConstellationTheme.TEXT)));
        if (out.isEmpty()) out.add(row("Pets", "API disabled or none"));
        return out;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        if (inside(mx, my, 150, 12, 48, 18)) { load(false); return true; }
        if (inside(mx, my, 202, 12, 56, 18)) { load(true); return true; }
        if (result != null) {
            int x = 12;
            for (int i = 0; i < result.profiles().size(); i++) {
                int bw = Math.max(52, font.width(string(result.profiles().get(i).getAsJsonObject(), "cute_name", Integer.toString(i + 1))) + 14);
                if (inside(mx, my, x, 58, bw, 16)) { profileIndex = i; scroll = 0; resetItems(); if (tab == 5) startItemDecode(); return true; }
                x += bw + 4;
            }
            x = 12;
            for (int i = 0; i < TABS.length; i++) {
                int bw = font.width(TABS[i]) + 16;
                if (inside(mx, my, x, 80, bw, 16)) { tab = i; scroll = 0; if (tab == 5 && itemProfile != profileIndex) startItemDecode(); return true; }
                x += bw + 4;
            }
            if (tab == 5 && itemResult != null && !itemResult.containers().isEmpty()) {
                int visibleMenus = Math.max(2, (height - 136) / 18);
                int y = 108;
                for (int i = itemContainerScroll; i < Math.min(itemResult.containers().size(), itemContainerScroll + visibleMenus); i++) {
                    if (inside(mx, my, 12, y, 106, 16)) { itemContainer = i; itemPage = 0; return true; }
                    y += 18;
                }
                int rows = Math.clamp(ConstellationClient.cfg().lyra.profileViewerInventoryRows, 1, 6);
                rows = Math.min(rows, Math.max(1, (height - 154) / 18));
                int gridX = Math.max(130, (width + 118 - 162) / 2);
                int gridY = 126;
                if (inside(mx, my, gridX, gridY + rows * 18 + 8, 56, 18)) { itemPage = Math.max(0, itemPage - 1); return true; }
                if (inside(mx, my, gridX + 106, gridY + rows * 18 + 8, 56, 18)) { itemPage++; return true; }
            }
        }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (result == null) return true;
        if (tab == 5) {
            if (mx < 124 && itemResult != null)
                itemContainerScroll = Math.clamp(itemContainerScroll - (int) sy, 0,
                    Math.max(0, itemResult.containers().size() - Math.max(2, (height - 136) / 18)));
            else itemPage = Math.max(0, itemPage - (int) sy);
            return true;
        }
        int max = Math.max(0, rows(profile(), member(profile())).size() * 21 - (height - 132));
        scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER) { load(false); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    private void load(boolean refresh) {
        String name = player.getValue().trim();
        if (name.isBlank()) name = Minecraft.getInstance().getUser().getName();
        loading = true; error = ""; scroll = 0;
        ProfileViewerApi.load(name, refresh).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (failure != null) {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                error = cause.getMessage() == null ? "Profile request failed." : cause.getMessage();
            } else {
                result = loaded;
                player.setValue(loaded.name());
                profileIndex = selectedIndex(loaded.profiles());
                resetItems();
            }
        }));
    }

    private void startItemDecode() {
        if (result == null || itemLoading || !ConstellationClient.cfg().lyra.profileViewerInventory) return;
        int requestedProfile = profileIndex;
        itemLoading = true;
        itemResult = null;
        ProfileItemDecoder.decode(member(profile())).whenComplete((decoded, failure) -> Minecraft.getInstance().execute(() -> {
            if (requestedProfile != profileIndex) return;
            itemLoading = false;
            itemProfile = requestedProfile;
            itemResult = failure == null ? decoded : null;
        }));
    }

    private void resetItems() {
        itemResult = null;
        itemLoading = false;
        itemProfile = -1;
        itemContainer = itemContainerScroll = itemPage = 0;
    }

    private JsonObject profile() { return result.profiles().get(Math.clamp(profileIndex, 0, result.profiles().size() - 1)).getAsJsonObject(); }
    private JsonObject member(JsonObject profile) { return object(object(profile, "members"), result.uuid().toString().replace("-", "")); }
    private static int selectedIndex(JsonArray profiles) { for (int i = 0; i < profiles.size(); i++) if (bool(profiles.get(i).getAsJsonObject(), "selected")) return i; return 0; }
    private static Row row(String label, String value) { return new Row(label, value, ConstellationTheme.TEXT); }
    private void chip(GuiGraphicsExtractor g, int x, int y, int w, String text, boolean selected, int mx, int my) { g.fill(x,y,x+w,y+16,selected?0xFF34506A:inside(mx,my,x,y,w,16)?0xFF303044:0xFF20202C);g.text(font,text,x+(w-font.width(text))/2,y+5,selected?0xFFFFFFFF:ConstellationTheme.TEXT_MUTED,false); }
    private void button(GuiGraphicsExtractor g,int x,int y,int w,String text,int mx,int my){g.fill(x,y,x+w,y+18,inside(mx,my,x,y,w,18)?0xFF3C3C55:0xFF252538);g.text(font,text,x+(w-font.width(text))/2,y+6,ConstellationTheme.TEXT,false);}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static JsonElement path(JsonObject root, String path) { JsonElement e=root; for(String part:path.split("\\.")){if(e==null||!e.isJsonObject()||!e.getAsJsonObject().has(part))return null;e=e.getAsJsonObject().get(part);}return e; }
    private static JsonObject object(JsonObject root,String key){return root!=null&&root.has(key)&&root.get(key).isJsonObject()?root.getAsJsonObject(key):new JsonObject();}
    private static JsonObject object(JsonElement root,String key){return root!=null&&root.isJsonObject()?object(root.getAsJsonObject(),key):new JsonObject();}
    private static JsonArray array(JsonObject root,String key){return root!=null&&root.has(key)&&root.get(key).isJsonArray()?root.getAsJsonArray(key):new JsonArray();}
    private static JsonArray array(JsonElement root,String key){return root!=null&&root.isJsonObject()?array(root.getAsJsonObject(),key):new JsonArray();}
    private static double number(JsonElement e){try{return e!=null&&!e.isJsonNull()?e.getAsDouble():0;}catch(Exception ignored){return 0;}}
    private static double number(double... values){for(double value:values)if(value!=0)return value;return 0;}
    private static String string(JsonObject o,String key,String fallback){try{return o.has(key)?o.get(key).getAsString():fallback;}catch(Exception ignored){return fallback;}}
    private static boolean bool(JsonObject o,String key){try{return o.has(key)&&o.get(key).getAsBoolean();}catch(Exception ignored){return false;}}
    private static String title(String value){if(value.isBlank())return value;return Character.toUpperCase(value.charAt(0))+value.substring(1).toLowerCase(Locale.ROOT);}
    private static String compact(double value){if(value>=1_000_000_000)return String.format(Locale.ROOT,"%.2fb",value/1_000_000_000);if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fm",value/1_000_000);if(value>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1_000);return whole(value);}
    private static String whole(double value){return String.format(Locale.ROOT,"%,.0f",value);}
    private static String coins(double value){return value<=0?"API disabled or empty":compact(value)+" coins";}
    private static String date(long value){return value<=0?"Unknown":TIME.format(Instant.ofEpochMilli(value));}
    private record Row(String label,String value,int color){}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
}
