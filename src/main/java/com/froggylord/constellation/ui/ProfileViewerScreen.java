package com.froggylord.constellation.ui;

import com.froggylord.constellation.api.ProfileViewerApi;
import com.froggylord.constellation.api.ProfileItemDecoder;
import com.froggylord.constellation.api.ProfileDungeonCalculator;
import com.froggylord.constellation.api.ProfileSkillCalculator;
import com.froggylord.constellation.api.ProfileSlayerCalculator;
import com.froggylord.constellation.api.ProfilePetCalculator;
import com.froggylord.constellation.api.ProfileBestiaryData;
import com.froggylord.constellation.api.ProfileCollectionData;
import com.froggylord.constellation.api.ProfileMinionCalculator;
import com.froggylord.constellation.api.ProfileMiningCalculator;
import com.froggylord.constellation.api.ProfileMuseumData;
import com.froggylord.constellation.api.ProfileWealthCalculator;
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
import java.util.List;
import java.util.Locale;

// ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/ProfileViewer.java, ProfileViewerScreen.java
// ported from SkyBlockPv (modified MIT): screens/BasePvScreen.kt, screens/PvTab.kt
// Portions of this code are from the SkyBlockPv mod.
public final class ProfileViewerScreen extends Screen {
    private static final String[] TABS = {"Overview", "Skills", "Dungeons", "Slayers", "Pets", "Items", "Wealth", "Bestiary", "Collections", "Minions", "Mining", "Museum"};
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
    private ProfileWealthCalculator.Result wealth;
    private boolean wealthLoading;
    private int wealthProfile = -1;
    private int wealthCategory = -1;
    private int wealthGeneration;
    private ProfileBestiaryData.Catalogue bestiaryData;
    private String bestiaryError = "";
    private boolean bestiaryLoading;
    private ProfileCollectionData.Catalogue collectionData;
    private String collectionError = "";
    private boolean collectionLoading;
    private ProfileMuseumData.Catalogue museumData;
    private ProfileViewerApi.MuseumResult museumResult;
    private String museumError = "";
    private boolean museumLoading;
    private int museumProfile = -1;

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
        if (tab == 6) {
            drawWealth(g, mx, my);
            return;
        }
        if (tab == 7) {
            drawBestiary(g);
            return;
        }
        if (tab == 8) {
            drawCollections(g);
            return;
        }
        if (tab == 9) {
            drawMinions(g);
            return;
        }
        if (tab == 10) {
            drawMining(g);
            return;
        }
        if (tab == 11) {
            drawMuseum(g);
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

    private void drawWealth(GuiGraphicsExtractor g, int mx, int my) {
        if (wealthProfile != profileIndex && !wealthLoading) startWealth();
        if (wealthLoading && wealth == null) {
            g.text(font, itemLoading ? "Decoding profile items..." : "Calculating profile wealth...", 14, 112, ConstellationTheme.TEXT, false);
            return;
        }
        if (wealth == null) {
            g.text(font, "Wealth data is unavailable for this profile.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        String total = "Estimated net worth  " + money(wealth.total());
        g.text(font, total, 14, 109, ConstellationTheme.ACCENT_BRIGHT, false);
        String coverage = wealth.pricedStacks() + "/" + wealth.totalStacks() + " stacks priced"
            + (wealth.pendingIds().isEmpty() ? "" : "  " + wealth.pendingIds().size() + " prices pending");
        g.text(font, coverage, width - 14 - font.width(coverage), 110,
            wealth.pendingIds().isEmpty() ? ConstellationTheme.TEXT_MUTED : 0xFFFFAA55, false);
        List<WealthLine> lines = wealthLines();
        int y = 130 - scroll;
        for (WealthLine line : lines) {
            if (y > 120 && y < height - 20) {
                int color = line.category >= 0 && line.category == wealthCategory ? 0xFF34506A : 0xA0181825;
                g.fill(12, y, width - 12, y + 18, color);
                g.text(font, line.label, 19 + line.indent, y + 6,
                    line.category >= 0 ? ConstellationTheme.TEXT : ConstellationTheme.TEXT_MUTED, false);
                g.text(font, line.value, width - 19 - font.width(line.value), y + 6,
                    line.complete ? ConstellationTheme.TEXT : 0xFFFFAA55, false);
            }
            y += 21;
        }
        if (wealthLoading)
            g.text(font, "Loading market prices at a bounded rate...", 14, height - 13, ConstellationTheme.TEXT_FAINT, false);
    }

    private List<WealthLine> wealthLines() {
        if (wealth == null) return List.of();
        List<WealthLine> lines = new ArrayList<>();
        lines.add(new WealthLine("Liquid currency", money(wealth.currency()), -1, 0, true));
        lines.add(new WealthLine("Priced items", money(wealth.itemValue()), -1, 0,
            wealth.pricedStacks() == wealth.totalStacks() && wealth.completeStacks() == wealth.totalStacks()));
        int maxItems = Math.clamp(ConstellationClient.cfg().lyra.profileWealthItemsPerCategory, 0, 30);
        for (int i = 0; i < wealth.categories().size(); i++) {
            ProfileWealthCalculator.Category category = wealth.categories().get(i);
            boolean complete = category.pricedStacks() == category.totalStacks() && category.completeStacks() == category.totalStacks();
            lines.add(new WealthLine(category.name(), money(category.value()) + "  " + category.pricedStacks() + "/" + category.totalStacks(), i, 0, complete));
            if (i == wealthCategory) {
                category.items().stream().limit(maxItems).forEach(item -> {
                    String count = item.count() > 1 ? " x" + item.count() : "";
                    lines.add(new WealthLine(item.name() + count, money(item.value()), -1, 10, item.complete()));
                });
            }
        }
        return lines;
    }

    private void drawBestiary(GuiGraphicsExtractor g) {
        if (!ConstellationClient.cfg().lyra.profileBestiary) {
            g.text(font, "Bestiary viewer is disabled.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        if (bestiaryData == null && !bestiaryLoading && bestiaryError.isEmpty()) startBestiary(false);
        if (bestiaryLoading && bestiaryData == null) {
            g.text(font, "Loading Bestiary catalogue...", 14, 112, ConstellationTheme.TEXT, false);
            return;
        }
        if (bestiaryData == null) {
            g.text(font, bestiaryError.isEmpty() ? "Bestiary data is unavailable." : bestiaryError,
                14, 112, 0xFFFF7777, false);
            return;
        }
        var cfg = ConstellationClient.cfg().lyra;
        ProfileBestiaryData.Result data = ProfileBestiaryData.calculate(bestiaryData, member(profile()),
            cfg.profileBestiaryCategory, cfg.profileBestiarySearch, cfg.profileBestiarySort,
            cfg.profileBestiaryHideZero, cfg.profileBestiaryHideMaxed);
        int decimals = Math.clamp(cfg.profileBestiaryDecimals, 0, 2);
        List<Row> rows = new ArrayList<>();
        if (cfg.profileBestiaryShowSummary) {
            rows.add(row("Bestiary levels", data.levels() + "/" + data.maxLevels()));
            rows.add(row("Tracked kills", whole(data.totalKills())));
            if (cfg.profileBestiaryShowDeaths) rows.add(row("Tracked deaths", whole(data.totalDeaths())));
            if (data.unknownKills() > 0) rows.add(new Row("Uncatalogued kills", whole(data.unknownKills()), 0xFFFFAA55));
            rows.add(row("Catalogue", bestiaryData.families().size() + " families  "
                + (bestiaryData.cached() ? "cached" : "current")));
        }
        int limit = Math.clamp(cfg.profileBestiaryLimit, 0, 1000);
        int shown = 0;
        for (ProfileBestiaryData.Family family : data.families()) {
            if (limit > 0 && shown >= limit) break;
            shown++;
            String label = cfg.profileBestiaryShowCategory ? family.category() + "  " + family.name() : family.name();
            StringBuilder value = new StringBuilder("Level ").append(family.level()).append("/").append(family.maxLevel());
            if (cfg.profileBestiaryShowKills) value.append("  ").append(compact(family.kills())).append(" kills");
            if (cfg.profileBestiaryShowDeaths && family.deaths() > 0)
                value.append("  ").append(compact(family.deaths())).append(" deaths");
            if (cfg.profileBestiaryShowCompletion)
                value.append("  ").append(fixed(family.completion() * 100, decimals)).append("%");
            if (cfg.profileBestiaryShowNext && family.nextThreshold() > 0)
                value.append("  ").append(compact(family.remaining())).append(" left");
            rows.add(new Row(label, value.toString(),
                family.level() >= family.maxLevel() ? 0xFFFFAA55 : ConstellationTheme.TEXT));
        }
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

    private void drawCollections(GuiGraphicsExtractor g) {
        var cfg = ConstellationClient.cfg().lyra;
        if (!cfg.profileCollections) {
            g.text(font, "Collections viewer is disabled.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        if (collectionData == null && !collectionLoading && collectionError.isEmpty()) startCollections(false);
        if (collectionLoading && collectionData == null) {
            g.text(font, "Loading Collections catalogue...", 14, 112, ConstellationTheme.TEXT, false);
            return;
        }
        if (collectionData == null) {
            g.text(font, collectionError.isEmpty() ? "Collection data is unavailable." : collectionError,
                14, 112, 0xFFFF7777, false);
            return;
        }
        ProfileCollectionData.Result data = collectionRows();
        if (!data.available()) {
            g.text(font, "Collection API data is unavailable for this profile.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        int decimals = Math.clamp(cfg.profileCollectionsDecimals, 0, 2);
        List<Row> rows = new ArrayList<>();
        if (cfg.profileCollectionsShowSummary) {
            rows.add(row("Collection tiers", data.tiers() + "/" + data.maxTiers()));
            rows.add(row("Maxed collections", data.maxed() + "/" + data.known()));
            rows.add(row("Catalogue", data.known() + " collections  " + (collectionData.cached() ? "cached" : "current")));
            if (data.unknown() > 0) rows.add(new Row("Uncatalogued collections", whole(data.unknown()), 0xFFFFAA55));
        }
        int limit = Math.clamp(cfg.profileCollectionsLimit, 0, 1000);
        int shown = 0;
        int unlockLimit = Math.clamp(cfg.profileCollectionsUnlockLimit, 0, 10);
        for (ProfileCollectionData.Collection collection : data.collections()) {
            if (limit > 0 && shown >= limit) break;
            shown++;
            String label = cfg.profileCollectionsShowCategory
                ? collection.category() + "  " + collection.name() : collection.name();
            StringBuilder value = new StringBuilder(collection.unknown() ? "Unknown tiers"
                : "Tier " + collection.tier() + "/" + collection.maxTier());
            if (cfg.profileCollectionsShowTotal) value.append("  ").append(compact(collection.total()));
            if (cfg.profileCollectionsShowPersonal && collection.personal() != collection.total())
                value.append("  ").append(compact(collection.personal())).append(" personal");
            if (cfg.profileCollectionsShowCompletion && !collection.unknown())
                value.append("  ").append(fixed(collection.completion() * 100, decimals)).append("%");
            if (cfg.profileCollectionsShowNext && collection.nextThreshold() > 0)
                value.append("  ").append(compact(collection.remaining())).append(" left");
            rows.add(new Row(label, value.toString(), collection.unknown() ? 0xFFFFAA55
                : collection.tier() >= collection.maxTier() ? 0xFFFFAA55 : ConstellationTheme.TEXT));
            if (cfg.profileCollectionsShowNextUnlocks && unlockLimit > 0 && !collection.nextUnlocks().isEmpty()) {
                String unlocks = String.join(", ", collection.nextUnlocks().stream().limit(unlockLimit).toList());
                rows.add(row("  Next unlock", unlocks));
            }
        }
        int y = 108 - scroll;
        for (Row row : rows) {
            if (y > 98 && y < height - 22) {
                g.fill(12, y, width - 12, y + 18, 0xA0181825);
                g.text(font, row.label, 19, y + 6, ConstellationTheme.TEXT_MUTED, false);
                String value = font.width(row.value) > width / 2
                    ? font.plainSubstrByWidth(row.value, width / 2 - 24) + "..." : row.value;
                g.text(font, value, width - 19 - font.width(value), y + 6, row.color, false);
            }
            y += 21;
        }
        g.text(font, "Esc to close", 12, height - 13, ConstellationTheme.TEXT_FAINT, false);
    }

    private void drawMinions(GuiGraphicsExtractor g) {
        var cfg = ConstellationClient.cfg().lyra;
        if (!cfg.profileMinions) {
            g.text(font, "Minions viewer is disabled.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        ProfileMinionCalculator.Result data = minionRows();
        if (!data.available()) {
            g.text(font, "Minion API data is unavailable for this profile.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        List<Row> rows = new ArrayList<>();
        if (cfg.profileMinionsShowSummary) {
            rows.add(row(cfg.profileMinionsPersonalOnly ? "Personal unique crafts" : "Co-op unique crafts",
                data.unique() + "/" + data.knownMaximum()));
            if (cfg.profileMinionsShowPersonal && !cfg.profileMinionsPersonalOnly)
                rows.add(row("Personal unique crafts", data.personalUnique() + "  co-op " + data.coopUnique()));
            rows.add(row("Slots from crafted minions", Integer.toString(data.slotsFromCrafts())));
            if (cfg.profileMinionsShowSlotProgress)
                rows.add(row("Next crafted-minion slot", data.nextSlotAt() == 0 ? "Maximum reached"
                    : data.nextSlotRemaining() + " crafts  (" + data.unique() + "/" + data.nextSlotAt() + ")"));
            if (data.unknownFamilies() > 0)
                rows.add(new Row("Uncatalogued families", Integer.toString(data.unknownFamilies()), 0xFFFFAA55));
        }
        int limit = Math.clamp(cfg.profileMinionsLimit, 0, 1000);
        int decimals = Math.clamp(cfg.profileMinionsDecimals, 0, 2);
        int shown = 0;
        for (ProfileMinionCalculator.Minion minion : data.minions()) {
            if (limit > 0 && shown >= limit) break;
            shown++;
            String label = cfg.profileMinionsShowCategory
                ? minion.category() + "  " + minion.name() : minion.name();
            StringBuilder value = new StringBuilder(minion.unknown() ? "Unknown cap"
                : "Tier " + minion.highestTier() + "/" + minion.maxTier());
            if (cfg.profileMinionsShowMissing && !minion.unknown())
                value.append("  ").append(minion.missing()).append(" missing");
            if (cfg.profileMinionsShowCompletion && !minion.unknown())
                value.append("  ").append(fixed(minion.completion() * 100, decimals)).append("%");
            rows.add(new Row(label, value.toString(), minion.unknown() ? 0xFFFFAA55
                : minion.crafted() >= minion.maxTier() ? 0xFFFFAA55 : ConstellationTheme.TEXT));
        }
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

    private void drawMining(GuiGraphicsExtractor g) {
        var cfg = ConstellationClient.cfg().lyra;
        if (!cfg.profileMining) {
            g.text(font, "Mining viewer is disabled.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        ProfileMiningCalculator.Result data = miningRows();
        if (!data.available()) {
            g.text(font, "Mining API data is unavailable for this profile.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        List<Row> rows = new ArrayList<>();
        if (cfg.profileMiningShowHotm) {
            String hotm = "Level " + data.hotmLevel() + "  " + whole(data.hotmXp()) + " XP";
            if (data.hotmRequired() > 0)
                hotm += "  " + whole(data.hotmRequired() - data.hotmProgress()) + " left";
            rows.add(row("Heart of the Mountain", hotm));
        }
        if (cfg.profileMiningShowTree) {
            rows.add(row("Selected mining tree", data.selectedTree() + "  " + title(data.selectedAbility())));
            rows.add(row("Tree nodes", data.unlockedNodes() + " unlocked  " + data.nodeLevels() + " total levels"
                + (data.disabledNodes() > 0 ? "  " + data.disabledNodes() + " disabled" : "")));
        }
        if (cfg.profileMiningShowPowder) {
            for (ProfileMiningCalculator.Powder powder : List.of(data.mithril(), data.gemstone(), data.glacite()))
                rows.add(row(powder.name() + " powder", whole(powder.available()) + " available  "
                    + whole(powder.spent()) + " spent  " + whole(powder.total()) + " total"));
        }
        rows.add(row("Crystal Nucleus runs", whole(data.nucleusRuns())));
        if (cfg.profileMiningShowCrystals) {
            int limit = Math.clamp(cfg.profileMiningCrystalLimit, 0, 100);
            int shown = 0;
            for (ProfileMiningCalculator.Crystal crystal : data.crystals()) {
                if (limit > 0 && shown >= limit) break;
                shown++;
                StringBuilder value = new StringBuilder();
                if (cfg.profileMiningShowCrystalState) value.append(title(crystal.state()));
                if (cfg.profileMiningShowCrystalFound)
                    value.append(value.isEmpty() ? "" : "  ").append(crystal.totalFound()).append(" found");
                if (cfg.profileMiningShowCrystalPlaced)
                    value.append(value.isEmpty() ? "" : "  ").append(crystal.totalPlaced()).append(" placed");
                rows.add(new Row((crystal.unknown() ? "Unknown  " : "") + crystal.name() + " Crystal",
                    value.toString(), crystal.unknown() ? 0xFFFFAA55
                        : crystal.active() ? 0xFF55FF55 : ConstellationTheme.TEXT));
            }
        }
        if (cfg.profileMiningShowRock) {
            String value = data.rock().rarity() + "  " + whole(data.oresMined()) + " ores";
            if (data.rock().remaining() > 0) value += "  " + whole(data.rock().remaining()) + " left";
            rows.add(row("Rock Pet milestone", value));
        }
        if (cfg.profileMiningShowGlacite) {
            rows.add(row("Glacite Mineshafts entered", whole(data.mineshaftsEntered())));
            rows.add(row("Fossil Dust", whole(data.fossilDust())));
        }
        if (cfg.profileMiningShowFossils) {
            rows.add(row("Fossils donated", data.fossilsDonated() + "/" + data.fossils().size()));
            for (ProfileMiningCalculator.Fossil fossil : data.fossils())
                rows.add(new Row("  " + fossil.name(), fossil.donated() ? "Donated" : "Missing",
                    fossil.donated() ? 0xFF55FF55 : ConstellationTheme.TEXT_MUTED));
        }
        if (cfg.profileMiningShowCorpses) {
            rows.add(row("Corpses looted", whole(data.corpsesLooted())));
            for (ProfileMiningCalculator.Corpse corpse : data.corpses())
                rows.add(row("  " + corpse.name(), whole(corpse.looted())));
        }
        drawRows(g, rows);
    }

    private void drawMuseum(GuiGraphicsExtractor g) {
        var cfg = ConstellationClient.cfg().lyra;
        if (!cfg.profileMuseum) {
            g.text(font, "Museum viewer is disabled.", 14, 112, ConstellationTheme.TEXT_MUTED, false);
            return;
        }
        if ((museumData == null || museumResult == null || museumProfile != profileIndex)
            && !museumLoading && museumError.isEmpty()) startMuseum(false);
        if (museumLoading && (museumData == null || museumResult == null)) {
            g.text(font, "Loading Museum...", 14, 112, ConstellationTheme.TEXT, false);
            return;
        }
        if (museumData == null || museumResult == null || museumProfile != profileIndex) {
            g.text(font, museumError.isEmpty() ? "Museum data is unavailable." : museumError,
                14, 112, 0xFFFF7777, false);
            return;
        }
        drawRows(g, museumDisplayRows(true));
    }

    private List<Row> museumDisplayRows(boolean values) {
        var cfg = ConstellationClient.cfg().lyra;
        ProfileMuseumData.Result data = ProfileMuseumData.calculate(museumData, museumResult,
            cfg.profileMuseumCategory, cfg.profileMuseumSearch, cfg.profileMuseumFilter,
            cfg.profileMuseumSort, cfg.profileMuseumIncludeBorrowed);
        List<Row> rows = new ArrayList<>();
        if (cfg.profileMuseumShowSummary) {
            rows.add(row("Museum donations", values ? data.donated() + " direct  " + data.throughParent()
                + " through parent  " + data.total() + " total" : ""));
            rows.add(row("Missing donations", values ? Integer.toString(data.missing()) : ""));
            if (data.borrowed() > 0) rows.add(row("Borrowed donations", values ? Integer.toString(data.borrowed()) : ""));
            rows.add(row("Special items", values ? data.specialDonated() + "/" + data.specialTotal() : ""));
            if (data.unknownSpecial() > 0)
                rows.add(new Row("Uncatalogued special items", values ? Long.toString(data.unknownSpecial()) : "", 0xFFFFAA55));
            if (data.specialFailures() > 0)
                rows.add(new Row("Unreadable special entries", values ? Integer.toString(data.specialFailures()) : "", 0xFFFFAA55));
            rows.add(row("Catalogue", values ? data.total() + " donations  "
                + (data.cached() || museumData.cached() ? "cached" : "current") : ""));
        }
        int limit = Math.clamp(cfg.profileMuseumLimit, 0, 2000);
        int pieceLimit = Math.clamp(cfg.profileMuseumPieceLimit, 0, 12);
        int shown = 0;
        for (ProfileMuseumData.Entry entry : data.entries()) {
            if (limit > 0 && shown >= limit) break;
            shown++;
            String label = cfg.profileMuseumShowCategory ? entry.category() + "  " + entry.name() : entry.name();
            StringBuilder value = new StringBuilder();
            if (cfg.profileMuseumShowStatus) value.append(switch (entry.status()) {
                case DONATED -> "Donated";
                case BORROWED -> "Borrowed";
                case PARENT -> "Through parent";
                case MISSING -> "Missing";
            });
            if (cfg.profileMuseumShowType)
                value.append(value.isEmpty() ? "" : "  ").append(entry.armor() ? "Set" : "Item");
            if (cfg.profileMuseumShowParent && entry.parent() != null)
                value.append(value.isEmpty() ? "" : "  ").append("parent ").append(title(entry.parent()));
            if (cfg.profileMuseumShowPieces && entry.armor() && pieceLimit > 0) {
                String pieces = String.join(", ", entry.pieces().stream().limit(pieceLimit).map(ProfileViewerScreen::title).toList());
                value.append(value.isEmpty() ? "" : "  ").append(pieces);
                if (entry.pieces().size() > pieceLimit) value.append(" +").append(entry.pieces().size() - pieceLimit);
            }
            int color = switch (entry.status()) {
                case DONATED -> 0xFF55FF55;
                case BORROWED, PARENT -> 0xFFFFAA55;
                case MISSING -> ConstellationTheme.TEXT;
            };
            rows.add(new Row(label, values ? value.toString() : "", color));
        }
        return rows;
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

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/profileviewer2/pages/SkillsPage.java, widgets/SkillsInfoBoxWidget.java, utils/LevelCalculator.java
    private List<Row> skills(JsonObject m) {
        var cfg = ConstellationClient.cfg().lyra;
        List<ProfileSkillCalculator.Skill> skills = ProfileSkillCalculator.calculate(m);
        List<Row> out = new ArrayList<>();
        int decimals = Math.clamp(cfg.profileSkillsDecimals, 0, 2);
        if (cfg.profileSkillsShowAverage) {
            double average = ProfileSkillCalculator.average(skills, cfg.profileSkillsIncludeCarpentry,
                cfg.profileSkillsIncludeHunting, cfg.profileSkillsIncludeCosmeticInAverage);
            out.add(row("Skill average", fixed(average, decimals)));
        }
        if (cfg.profileSkillsShowTotalXp) {
            double total = skills.stream().filter(ProfileSkillCalculator.Skill::available)
                .mapToDouble(ProfileSkillCalculator.Skill::xp).sum();
            out.add(row("Total skill XP", compact(total)));
        }
        for (ProfileSkillCalculator.Skill skill : skills) {
            if (!skill.available()) {
                out.add(row(title(skill.id()), "API disabled"));
                continue;
            }
            StringBuilder value = new StringBuilder(skill.maxed() ? "Level " + skill.cap()
                : "Level " + fixed(skill.level(), decimals) + " / " + skill.cap());
            if (cfg.profileSkillsShowProgress && !skill.maxed())
                value.append("  ").append(Math.round(skill.progress() * 100)).append("%");
            if (cfg.profileSkillsShowRemaining && !skill.maxed())
                value.append("  ").append(compact(skill.remaining())).append(" left");
            if (cfg.profileSkillsShowOverflow && skill.overflow() > 0)
                value.append("  +").append(compact(skill.overflow())).append(" XP");
            out.add(new Row(title(skill.id()), value.toString(),
                skill.maxed() ? 0xFF55FF55 : ConstellationTheme.TEXT));
        }
        return out;
    }

    private List<Row> dungeons(JsonObject m) {
        var cfg = ConstellationClient.cfg().lyra;
        ProfileDungeonCalculator.Result data = ProfileDungeonCalculator.calculate(m);
        int decimals = Math.clamp(cfg.profileDungeonsDecimals, 0, 2);
        List<Row> out = new ArrayList<>();
        if (!data.available()) {
            out.add(row("Dungeons", "API disabled or no dungeon data"));
            return out;
        }
        out.add(row("Catacombs", dungeonLevel(data.catacombs(), decimals, cfg.profileDungeonsShowOverflow,
            cfg.profileDungeonsShowClassProgress, cfg.profileDungeonsShowClassXp)));
        if (cfg.profileDungeonsShowClassAverage)
            out.add(row("Class average", fixed(ProfileDungeonCalculator.classAverage(data, cfg.profileDungeonsShowOverflow), decimals)));
        for (ProfileDungeonCalculator.ClassLevel entry : data.classes()) {
            String label = (entry.selected() ? "Selected  " : "") + title(entry.id());
            out.add(new Row(label, dungeonLevel(entry.level(), decimals, cfg.profileDungeonsShowOverflow,
                cfg.profileDungeonsShowClassProgress, cfg.profileDungeonsShowClassXp),
                entry.selected() ? 0xFF55FF55 : ConstellationTheme.TEXT));
        }
        out.add(row("Dungeon runs", whole(data.runs())));
        if (cfg.profileDungeonsShowSecrets) out.add(row("Secrets found", whole(data.secrets())));
        if (cfg.profileDungeonsShowSecretsPerRun)
            out.add(row("Secrets per run", data.runs() == 0 ? "No runs" : fixed(data.secrets() / (double) data.runs(), decimals)));
        for (ProfileDungeonCalculator.Floor floor : data.floors()) {
            if (!cfg.profileDungeonsShowEntrance && floor.floor() == 0) continue;
            if (!cfg.profileDungeonsShowEmptyFloors && floor.completions() == 0) continue;
            if (cfg.profileDungeonsShowFloorRuns)
                out.add(row(floor.name() + " completions", whole(floor.completions())));
            if (cfg.profileDungeonsShowFloorTimes) {
                if (floor.fastest() > 0) out.add(row("  Fastest completion", duration(floor.fastest())));
                if (floor.fastestS() > 0) out.add(row("  Fastest S", duration(floor.fastestS())));
                if (floor.fastestSPlus() > 0) out.add(row("  Fastest S+", duration(floor.fastestSPlus())));
            }
            if (cfg.profileDungeonsShowFloorScores && floor.bestScore() > 0)
                out.add(row("  Best score", whole(floor.bestScore())));
        }
        return out;
    }

    private static String dungeonLevel(ProfileDungeonCalculator.Level level, int decimals, boolean overflow,
                                       boolean progress, boolean xp) {
        double shown = overflow ? level.levelWithOverflow() : Math.min(50, level.level());
        StringBuilder value = new StringBuilder("Level ").append(fixed(shown, decimals));
        if (progress && level.level() < 50) value.append("  ").append(Math.round(level.progress() * 100)).append("%");
        if (xp) value.append("  ").append(compact(level.xp())).append(" XP");
        else if (overflow && level.overflowXp() > 0) value.append("  +").append(compact(level.overflowXp())).append(" XP");
        return value.toString();
    }

    private static String duration(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = milliseconds % 1000;
        return millis == 0 ? String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            : String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis);
    }

    private List<Row> slayers(JsonObject m) {
        var cfg = ConstellationClient.cfg().lyra;
        ProfileSlayerCalculator.Result data = ProfileSlayerCalculator.calculate(m);
        List<Row> out = new ArrayList<>();
        if (!data.available()) {
            out.add(row("Slayers", "API disabled or no Slayer data"));
            return out;
        }
        int decimals = Math.clamp(cfg.profileSlayersDecimals, 0, 2);
        if (cfg.profileSlayersShowSummary) {
            out.add(row("Total Slayer XP", compact(data.totalXp())));
            out.add(row("Total bosses", whole(data.totalKills())));
            if (cfg.profileSlayersShowAttempts) out.add(row("Total attempts", whole(data.totalAttempts())));
        }
        for (ProfileSlayerCalculator.Slayer slayer : data.slayers()) {
            if (!cfg.profileSlayersShowUnplayed && !slayer.played()) continue;
            ProfileSlayerCalculator.Level level = slayer.level();
            StringBuilder value = new StringBuilder("Level ").append(fixed(level.level(), decimals));
            if (cfg.profileSlayersShowProgress && level.needed() > 0)
                value.append("  ").append(Math.round(level.progress() * 100)).append("%");
            if (cfg.profileSlayersShowXp) value.append("  ").append(compact(slayer.xp())).append(" XP");
            if (cfg.profileSlayersShowRemaining && level.needed() > 0)
                value.append("  ").append(compact(level.remaining())).append(" left");
            if (cfg.profileSlayersShowOverflow && level.overflow() > 0)
                value.append("  +").append(compact(level.overflow()));
            out.add(new Row(slayer.name(), value.toString(),
                level.needed() == 0 ? 0xFF55FF55 : ConstellationTheme.TEXT));
            if (cfg.profileSlayersShowKills) out.add(row("  Bosses", whole(slayer.kills())));
            if (cfg.profileSlayersShowAttempts) out.add(row("  Attempts", whole(slayer.attempts())));
            if (cfg.profileSlayersShowClaimedRewards) {
                String rewards = slayer.rewardsAvailable()
                    ? slayer.claimedRewards() + "/" + slayer.level().max() : "API disabled";
                if (slayer.unclaimedRewards() > 0) rewards += "  " + slayer.unclaimedRewards() + " unclaimed";
                out.add(new Row("  Rewards claimed", rewards,
                    slayer.unclaimedRewards() > 0 ? 0xFFFFAA55 : ConstellationTheme.TEXT));
            }
            if (cfg.profileSlayersShowTierKills) {
                for (ProfileSlayerCalculator.Tier tier : slayer.tiers()) {
                    if (!cfg.profileSlayersShowUnplayed && tier.kills() == 0 && tier.attempts() == 0) continue;
                    String tierValue = whole(tier.kills()) + " kills";
                    if (cfg.profileSlayersShowAttempts && tier.attempts() > 0)
                        tierValue += " / " + whole(tier.attempts()) + " attempts";
                    out.add(row("    Tier " + tier.tier(), tierValue));
                }
            }
        }
        return out;
    }

    private List<Row> pets(JsonObject m) {
        var cfg = ConstellationClient.cfg().lyra;
        ProfilePetCalculator.Result data = ProfilePetCalculator.calculate(m, cfg.profilePetsSort, cfg.profilePetsActiveFirst);
        List<Row> out = new ArrayList<>();
        if (!data.available()) {
            out.add(row("Pets", "API disabled"));
            return out;
        }
        int decimals = Math.clamp(cfg.profilePetsDecimals, 0, 2);
        if (cfg.profilePetsShowSummary) {
            out.add(row("Pets", whole(data.count())));
            out.add(row("Maxed pets", whole(data.maxed())));
            out.add(row("Total pet XP", compact(data.totalXp())));
        }
        int shown = 0;
        int limit = Math.clamp(cfg.profilePetsLimit, 0, 500);
        String search = cfg.profilePetsSearch == null ? "" : cfg.profilePetsSearch.trim().toLowerCase(Locale.ROOT);
        int minimumRarity = petRarity(cfg.profilePetsMinimumRarity);
        for (ProfilePetCalculator.Pet pet : data.pets()) {
            if (cfg.profilePetsOnlyActive && !pet.active()) continue;
            if (!search.isEmpty() && !pet.type().toLowerCase(Locale.ROOT).contains(search)) continue;
            if (petRarity(pet.effectiveTier()) < minimumRarity) continue;
            if (limit > 0 && shown >= limit) break;
            shown++;
            ProfilePetCalculator.Level level = pet.level();
            String label = (pet.active() ? "Active  " : "") + title(pet.type().replace('_', ' '));
            StringBuilder value = new StringBuilder(pet.effectiveTier()).append("  Level ")
                .append(fixed(level.level(), decimals)).append("/").append(level.cap());
            if (cfg.profilePetsShowNextProgress && !level.maxed())
                value.append("  ").append(Math.round(level.progress() * 100)).append("%");
            if (cfg.profilePetsShowXp) value.append("  ").append(compact(pet.xp())).append(" XP");
            if (cfg.profilePetsShowMaxProgress)
                value.append("  ").append(Math.round(Math.clamp(level.progressToMax(), 0, 1) * 100)).append("% max");
            if (cfg.profilePetsShowRemainingToMax && !level.maxed())
                value.append("  ").append(compact(level.remainingToMax())).append(" left");
            if (cfg.profilePetsShowOverflow && level.overflow() > 0)
                value.append("  +").append(compact(level.overflow()));
            out.add(new Row(label, value.toString(), pet.active() ? 0xFF55FF55
                : level.maxed() ? 0xFFFFAA55 : ConstellationTheme.TEXT));
            if (cfg.profilePetsShowCandy && pet.candyUsed() > 0)
                out.add(row("  Candy used", pet.candyUsed() + "/10"));
            if (cfg.profilePetsShowHeldItem && pet.heldItem() != null)
                out.add(row("  Held item", title(pet.heldItem().replace('_', ' '))));
            if (cfg.profilePetsShowSkin && pet.skin() != null)
                out.add(row("  Skin", title(pet.skin().replace('_', ' '))));
            if (cfg.profilePetsShowUuid && (pet.uniqueId() != null || pet.uuid() != null))
                out.add(row("  Pet UUID", pet.uniqueId() != null ? pet.uniqueId() : pet.uuid()));
        }
        if (data.pets().isEmpty()) out.add(row("Pets", "None"));
        return out;
    }

    private static int petRarity(String rarity) {
        if (rarity == null) return 0;
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        if (inside(mx, my, 150, 12, 48, 18)) { load(false); return true; }
        if (inside(mx, my, 202, 12, 56, 18)) { load(true); return true; }
        if (result != null) {
            int x = 12;
            for (int i = 0; i < result.profiles().size(); i++) {
                int bw = Math.max(52, font.width(string(result.profiles().get(i).getAsJsonObject(), "cute_name", Integer.toString(i + 1))) + 14);
                if (inside(mx, my, x, 58, bw, 16)) { profileIndex = i; scroll = 0; resetItems(); if (tab == 5) startItemDecode(); if (tab == 6) startWealth(); return true; }
                x += bw + 4;
            }
            x = 12;
            for (int i = 0; i < TABS.length; i++) {
                int bw = font.width(TABS[i]) + 16;
                if (inside(mx, my, x, 80, bw, 16)) {
                    tab = i; scroll = 0;
                    if (tab == 5 && itemProfile != profileIndex) startItemDecode();
                    if (tab == 6 && wealthProfile != profileIndex) startWealth();
                    if (tab == 7 && bestiaryData == null) startBestiary(false);
                    if (tab == 8 && collectionData == null) startCollections(false);
                    if (tab == 11 && (museumResult == null || museumProfile != profileIndex)) startMuseum(false);
                    return true;
                }
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
            if (tab == 6 && wealth != null) {
                int y = 130 - scroll;
                for (WealthLine line : wealthLines()) {
                    if (line.category >= 0 && inside(mx, my, 12, y, width - 24, 18)) {
                        wealthCategory = wealthCategory == line.category ? -1 : line.category;
                        scroll = 0;
                        return true;
                    }
                    y += 21;
                }
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
        if (tab == 10) {
            int max = Math.max(0, miningDisplayRows().size() * 21 - (height - 132));
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
            return true;
        }
        if (tab == 11 && museumData != null && museumResult != null && museumProfile == profileIndex) {
            int max = Math.max(0, museumDisplayRows(false).size() * 21 - (height - 132));
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
            return true;
        }
        if (tab == 6) {
            int max = Math.max(0, wealthLines().size() * 21 - (height - 154));
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
            return true;
        }
        if (tab == 7 && bestiaryData != null) {
            var cfg = ConstellationClient.cfg().lyra;
            ProfileBestiaryData.Result data = ProfileBestiaryData.calculate(bestiaryData, member(profile()),
                cfg.profileBestiaryCategory, cfg.profileBestiarySearch, cfg.profileBestiarySort,
                cfg.profileBestiaryHideZero, cfg.profileBestiaryHideMaxed);
            int summary = cfg.profileBestiaryShowSummary ? (data.unknownKills() > 0 ? 5 : 4) : 0;
            int shown = cfg.profileBestiaryLimit <= 0 ? data.families().size()
                : Math.min(data.families().size(), cfg.profileBestiaryLimit);
            int max = Math.max(0, (summary + shown) * 21 - (height - 132));
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
            return true;
        }
        if (tab == 8 && collectionData != null) {
            var cfg = ConstellationClient.cfg().lyra;
            ProfileCollectionData.Result data = collectionRows();
            int summary = cfg.profileCollectionsShowSummary ? (data.unknown() > 0 ? 4 : 3) : 0;
            int shown = cfg.profileCollectionsLimit <= 0 ? data.collections().size()
                : Math.min(data.collections().size(), cfg.profileCollectionsLimit);
            int unlocks = cfg.profileCollectionsShowNextUnlocks && cfg.profileCollectionsUnlockLimit > 0
                ? (int) data.collections().stream().limit(shown).filter(value -> !value.nextUnlocks().isEmpty()).count() : 0;
            int max = Math.max(0, (summary + shown + unlocks) * 21 - (height - 132));
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
            return true;
        }
        if (tab == 9) {
            var cfg = ConstellationClient.cfg().lyra;
            ProfileMinionCalculator.Result data = minionRows();
            int summary = cfg.profileMinionsShowSummary
                ? 2 + (cfg.profileMinionsShowPersonal && !cfg.profileMinionsPersonalOnly ? 1 : 0)
                    + (cfg.profileMinionsShowSlotProgress ? 1 : 0) + (data.unknownFamilies() > 0 ? 1 : 0)
                : 0;
            int shown = cfg.profileMinionsLimit <= 0 ? data.minions().size()
                : Math.min(data.minions().size(), cfg.profileMinionsLimit);
            int max = Math.max(0, (summary + shown) * 21 - (height - 132));
            scroll = Math.clamp(scroll - (int) (sy * 24), 0, max);
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
                if (tab == 7) startBestiary(refresh);
                if (tab == 8) startCollections(refresh);
                if (tab == 11) startMuseum(refresh);
            }
        }));
    }

    private void startItemDecode() {
        if (result == null || itemLoading
            || !ConstellationClient.cfg().lyra.profileViewerInventory && tab != 6) return;
        int requestedProfile = profileIndex;
        itemLoading = true;
        itemResult = null;
        ProfileItemDecoder.decode(member(profile())).whenComplete((decoded, failure) -> Minecraft.getInstance().execute(() -> {
            if (requestedProfile != profileIndex) return;
            itemLoading = false;
            itemProfile = requestedProfile;
            itemResult = failure == null ? decoded : null;
            if (tab == 6 && failure == null) startWealthCalculation();
            else if (tab == 6) wealthLoading = false;
        }));
    }

    private void startWealth() {
        if (result == null || wealthLoading || !ConstellationClient.cfg().lyra.profileWealth) return;
        if (itemProfile != profileIndex || itemResult == null) {
            wealthLoading = true;
            startItemDecode();
            return;
        }
        startWealthCalculation();
    }

    private void startWealthCalculation() {
        if (itemResult == null) { wealthLoading = false; return; }
        int generation = ++wealthGeneration;
        int requestedProfile = profileIndex;
        wealthLoading = true;
        wealthProfile = requestedProfile;
        var cfg = ConstellationClient.cfg().lyra;
        ProfileWealthCalculator.calculate(profile(), member(profile()), itemResult,
            cfg.profileWealthMaxPriceRequests, cfg.profileWealthRequestIntervalMs,
            calculated -> Minecraft.getInstance().execute(() -> {
                if (generation == wealthGeneration && requestedProfile == profileIndex) wealth = calculated;
            }),
            () -> generation != wealthGeneration || requestedProfile != profileIndex
        ).whenComplete((unused, failure) -> Minecraft.getInstance().execute(() -> {
            if (generation == wealthGeneration && requestedProfile == profileIndex) wealthLoading = false;
        }));
    }

    private void startBestiary(boolean refresh) {
        if (bestiaryLoading || !ConstellationClient.cfg().lyra.profileBestiary) return;
        bestiaryLoading = true;
        bestiaryError = "";
        ProfileBestiaryData.load(refresh).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
            bestiaryLoading = false;
            if (failure == null) {
                bestiaryData = loaded;
            } else {
                bestiaryData = null;
                bestiaryError = "Bestiary catalogue is unavailable.";
            }
        }));
    }

    private ProfileCollectionData.Result collectionRows() {
        var cfg = ConstellationClient.cfg().lyra;
        return ProfileCollectionData.calculate(collectionData, profile(),
            result.uuid().toString().replace("-", ""), cfg.profileCollectionsCategory,
            cfg.profileCollectionsSearch, cfg.profileCollectionsSort,
            cfg.profileCollectionsHideZero, cfg.profileCollectionsHideMaxed);
    }

    private ProfileMinionCalculator.Result minionRows() {
        var cfg = ConstellationClient.cfg().lyra;
        return ProfileMinionCalculator.calculate(profile(), result.uuid().toString().replace("-", ""),
            cfg.profileMinionsCategory, cfg.profileMinionsSearch, cfg.profileMinionsSort,
            cfg.profileMinionsPersonalOnly, cfg.profileMinionsHideUncrafted, cfg.profileMinionsHideMaxed);
    }

    private ProfileMiningCalculator.Result miningRows() {
        var cfg = ConstellationClient.cfg().lyra;
        return ProfileMiningCalculator.calculate(member(profile()), cfg.profileMiningCrystalFilter,
            cfg.profileMiningCrystalSort, cfg.profileMiningHideNeverFoundCrystals,
            cfg.profileMiningHideInactiveCrystals);
    }

    private List<Row> miningDisplayRows() {
        var cfg = ConstellationClient.cfg().lyra;
        ProfileMiningCalculator.Result data = miningRows();
        List<Row> rows = new ArrayList<>();
        if (cfg.profileMiningShowHotm) rows.add(row("Heart of the Mountain", ""));
        if (cfg.profileMiningShowTree) { rows.add(row("Selected mining tree", "")); rows.add(row("Tree nodes", "")); }
        if (cfg.profileMiningShowPowder) { rows.add(row("Mithril powder", "")); rows.add(row("Gemstone powder", "")); rows.add(row("Glacite powder", "")); }
        rows.add(row("Crystal Nucleus runs", ""));
        if (cfg.profileMiningShowCrystals) {
            int limit = cfg.profileMiningCrystalLimit <= 0 ? data.crystals().size()
                : Math.min(data.crystals().size(), cfg.profileMiningCrystalLimit);
            for (int i = 0; i < limit; i++) rows.add(row("Crystal", ""));
        }
        if (cfg.profileMiningShowRock) rows.add(row("Rock Pet milestone", ""));
        if (cfg.profileMiningShowGlacite) { rows.add(row("Glacite Mineshafts entered", "")); rows.add(row("Fossil Dust", "")); }
        if (cfg.profileMiningShowFossils) {
            rows.add(row("Fossils donated", ""));
            for (int i = 0; i < data.fossils().size(); i++) rows.add(row("Fossil", ""));
        }
        if (cfg.profileMiningShowCorpses) {
            rows.add(row("Corpses looted", ""));
            for (int i = 0; i < data.corpses().size(); i++) rows.add(row("Corpse", ""));
        }
        return rows;
    }

    private void drawRows(GuiGraphicsExtractor g, List<Row> rows) {
        int y = 108 - scroll;
        for (Row row : rows) {
            if (y > 98 && y < height - 22) {
                g.fill(12, y, width - 12, y + 18, 0xA0181825);
                g.text(font, row.label, 19, y + 6, ConstellationTheme.TEXT_MUTED, false);
                String value = font.width(row.value) > width / 2
                    ? font.plainSubstrByWidth(row.value, width / 2 - 24) + "..." : row.value;
                g.text(font, value, width - 19 - font.width(value), y + 6, row.color, false);
            }
            y += 21;
        }
        g.text(font, "Esc to close", 12, height - 13, ConstellationTheme.TEXT_FAINT, false);
    }

    private void startCollections(boolean refresh) {
        if (collectionLoading || !ConstellationClient.cfg().lyra.profileCollections) return;
        collectionLoading = true;
        collectionError = "";
        ProfileCollectionData.load(refresh).whenComplete((loaded, failure) -> Minecraft.getInstance().execute(() -> {
            collectionLoading = false;
            if (failure == null) {
                collectionData = loaded;
            } else {
                collectionData = null;
                collectionError = "Collection catalogue is unavailable.";
            }
        }));
    }

    private void startMuseum(boolean refresh) {
        if (result == null || museumLoading || !ConstellationClient.cfg().lyra.profileMuseum) return;
        int requestedProfile = profileIndex;
        String profileId = string(profile(), "profile_id", "");
        String memberId = result.uuid().toString().replace("-", "");
        museumLoading = true;
        museumError = "";
        var catalogueFuture = ProfileMuseumData.load(refresh);
        var museumFuture = ProfileViewerApi.loadMuseum(profileId, memberId, refresh);
        java.util.concurrent.CompletableFuture.allOf(catalogueFuture, museumFuture)
            .whenComplete((unused, failure) -> Minecraft.getInstance().execute(() -> {
                if (requestedProfile != profileIndex) {
                    museumLoading = false;
                    return;
                }
                museumLoading = false;
                if (failure == null) {
                    museumData = catalogueFuture.join();
                    museumResult = museumFuture.join();
                    museumProfile = requestedProfile;
                } else {
                    museumResult = null;
                    museumProfile = -1;
                    museumError = "Museum data is unavailable.";
                }
            }));
    }

    private void resetItems() {
        itemResult = null;
        itemLoading = false;
        itemProfile = -1;
        itemContainer = itemContainerScroll = itemPage = 0;
        wealthGeneration++;
        wealth = null;
        wealthLoading = false;
        wealthProfile = -1;
        wealthCategory = -1;
        museumResult = null;
        museumLoading = false;
        museumProfile = -1;
        museumError = "";
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
    private static String fixed(double value,int decimals){return String.format(Locale.ROOT,"%."+decimals+"f",value);}
    private static String compact(double value){if(value>=1_000_000_000)return String.format(Locale.ROOT,"%.2fb",value/1_000_000_000);if(value>=1_000_000)return String.format(Locale.ROOT,"%.2fm",value/1_000_000);if(value>=1_000)return String.format(Locale.ROOT,"%.1fk",value/1_000);return whole(value);}
    private static String whole(double value){return String.format(Locale.ROOT,"%,.0f",value);}
    private static String coins(double value){return value<=0?"API disabled or empty":compact(value)+" coins";}
    private static String date(long value){return value<=0?"Unknown":TIME.format(Instant.ofEpochMilli(value));}
    private static String money(double value){return "$"+compact(value);}
    private record Row(String label,String value,int color){}
    private record WealthLine(String label,String value,int category,int indent,boolean complete){}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
}
