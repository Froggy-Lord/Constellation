package com.froggylord.constellation.constellation;

import com.froggylord.constellation.api.DungeonProfileApi;
import com.froggylord.constellation.config.OrionConfig;
import com.froggylord.constellation.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PartyFinderOverlay {
    private static final Pattern MEMBER = Pattern.compile("^ (\\w{1,16}): (Healer|Tank|Mage|Berserk|Archer) \\((\\d+)\\)$");
    private static final Pattern SELECTED = Pattern.compile("^Currently Selected: (Healer|Tank|Mage|Berserk|Archer)$");
    private static final Pattern FLOOR = Pattern.compile("^Floor: Floor ([IV]+)$");
    private static final Pattern CATA = Pattern.compile("^Requires Catacombs Level (\\d+)!$");
    private static final List<String> ROLES = List.of("Healer", "Tank", "Mage", "Berserk", "Archer");

    private static OrionConfig cfg;
    private static String selectedClass = "";

    private PartyFinderOverlay() {}

    public static void init(OrionConfig config) {
        cfg = config;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            String title = container.getTitle().getString();
            if (!title.equals("Party Finder") && !title.equals("Catacombs Gate")) return;

            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, delta) -> {
                if (cfg == null || !cfg.partyFinderGui) return;
                try {
                    if (title.equals("Catacombs Gate")) readSelectedClass(container);
                    else draw(container, graphics, mouseX, mouseY);
                } catch (Exception ignored) {}
            });
        });
    }

    // ported from devonian (GPL-3.0): api/dungeon/PartyFinderListener.kt
    private static void readSelectedClass(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            for (String line : lore(slot.getItem())) {
                Matcher match = SELECTED.matcher(line);
                if (match.matches()) {
                    selectedClass = match.group(1);
                    return;
                }
            }
        }
    }

    // ported from devonian (GPL-3.0): features/dungeons/PartyFinderHighlight.kt
    // ported from devonian (GPL-3.0): features/dungeons/PartyFinderCount.kt
    // cross-checked with Athen (BSD-3-Clause): modules/impl/dungeon/PartyFinder.kt
    private static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = ((ContainerScreenAccessor) screen).constellation$left();
        int top = ((ContainerScreenAccessor) screen).constellation$top();
        int chestSlots = Math.max(0, screen.getMenu().slots.size() - 36);
        int joinable = 0;
        int blocked = 0;
        int dupes = 0;
        Listing hovered = null;

        for (int i = 0; i < chestSlots; i++) {
            Slot slot = screen.getMenu().slots.get(i);
            Listing listing = parse(slot.getItem());
            if (listing == null) continue;

            int colour;
            String mark;
            if (listing.blocked) {
                blocked++;
                colour = 0x88FF3030;
                mark = "NO";
            } else if (listing.dupe) {
                dupes++;
                colour = 0x88FFCC22;
                mark = "D";
            } else {
                joinable++;
                colour = 0x8855FF55;
                mark = "OK";
            }

            int x = left + slot.x;
            int y = top + slot.y;
            graphics.fill(x, y, x + 16, y + 16, colour);
            graphics.text(Minecraft.getInstance().font, mark, x + 1, y + 1, 0xFFFFFFFF, true);
            String count = Integer.toString(listing.members);
            graphics.text(Minecraft.getInstance().font, count,
                x + 16 - Minecraft.getInstance().font.width(count), y + 8, 0xFFFFFFFF, true);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = listing;
        }

        if (joinable + blocked + dupes == 0) return;
        String role = selectedClass.isEmpty() ? "" : "  Class " + selectedClass;
        String summary = "§aJoinable " + joinable + "  §eDupe " + dupes + "  §cBlocked " + blocked + "§7" + role;
        graphics.text(Minecraft.getInstance().font, summary, left + 8, top - 12, 0xFFFFFFFF, true);
        if (hovered != null) drawDetails(graphics, left + 180, top, hovered);
    }

    // ported from devonian (GPL-3.0): features/dungeons/PartyFinderOverview.kt
    // ported from devonian (GPL-3.0): api/dungeon/DungeonsApi.kt
    private static void drawDetails(GuiGraphicsExtractor graphics, int x, int y, Listing listing) {
        List<String> lines = new ArrayList<>();
        lines.add("§b" + (listing.master ? "Master " : "Normal ") + "Floor " + listing.floor
            + (listing.requiredCata > 0 ? "  §7Cata " + listing.requiredCata : ""));
        lines.add("§7Missing: §f" + (listing.missingRoles.isEmpty() ? "none" : String.join(", ", listing.missingRoles)));
        List<String> names = new ArrayList<>();
        for (Member member : listing.memberList) names.add(member.name);
        if (cfg.partyFinderStats) DungeonProfileApi.request(names);
        for (Member member : listing.memberList) {
            String line = "§f" + member.name + " §7" + member.role + " " + member.level;
            DungeonProfileApi.Profile profile = cfg.partyFinderStats ? DungeonProfileApi.get(member.name) : null;
            if (profile != null) {
                String pb = DungeonProfileApi.personalBest(profile, listing.master, listing.floor);
                line += " §6C" + Math.round(profile.cata()) + " §b" + compact(profile.secrets()) + "s §dPB " + pb;
            } else if (cfg.partyFinderStats) line += " §8loading";
            lines.add(line);
        }
        var font = Minecraft.getInstance().font;
        int width = 120;
        for (String line : lines) width = Math.max(width, font.width(line) + 8);
        graphics.fill(x, y, x + width, y + 7 + lines.size() * 10, 0xCC101018);
        for (int i = 0; i < lines.size(); i++) graphics.text(font, lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFFFF, true);
    }

    // ported from devonian (GPL-3.0): api/dungeon/PartyFinderListener.kt
    private static Listing parse(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) return null;
        boolean dungeon = false;
        boolean blocked = false;
        boolean dupe = false;
        int members = 0;
        boolean master = false;
        int floor = -1;
        int requiredCata = 0;
        List<Member> memberList = new ArrayList<>();
        Set<String> currentRoles = new HashSet<>();

        for (String line : lore(stack)) {
            if (line.equals("Dungeon: The Catacombs") || line.equals("Dungeon: Master Mode The Catacombs")) {
                dungeon = true;
                master = line.contains("Master Mode");
            } else if (line.matches("Requires Catacombs Level \\d+!")
                || line.matches("Requires a Class at Level \\d+!")
                || line.equals("Complete previous floor first!")) {
                blocked = true;
            }

            Matcher floorMatch = FLOOR.matcher(line);
            if (floorMatch.matches()) floor = roman(floorMatch.group(1));
            Matcher cataMatch = CATA.matcher(line);
            if (cataMatch.matches()) requiredCata = Integer.parseInt(cataMatch.group(1));

            Matcher member = MEMBER.matcher(line);
            if (!member.matches()) continue;
            members++;
            memberList.add(new Member(member.group(1), member.group(2), Integer.parseInt(member.group(3))));
            currentRoles.add(member.group(2));
            if (!selectedClass.isEmpty() && selectedClass.equals(member.group(2))) dupe = true;
        }

        List<String> missing = new ArrayList<>(ROLES);
        missing.removeAll(currentRoles);
        return dungeon && members > 0 ? new Listing(members, blocked, dupe, master, floor,
            requiredCata, memberList, missing) : null;
    }

    private static String[] lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return new String[0];
        String[] lines = new String[lore.lines().size()];
        for (int i = 0; i < lines.length; i++) lines[i] = lore.lines().get(i).getString();
        return lines;
    }

    private static int roman(String value) {
        return switch (value) { case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4;
            case "V" -> 5; case "VI" -> 6; case "VII" -> 7; default -> -1; };
    }

    private static String compact(int value) {
        if (value >= 1_000_000) return String.format("%.1fm", value / 1_000_000.0);
        if (value >= 1_000) return String.format("%.1fk", value / 1_000.0);
        return Integer.toString(value);
    }

    private record Member(String name, String role, int level) {}
    private record Listing(int members, boolean blocked, boolean dupe, boolean master, int floor,
                           int requiredCata, List<Member> memberList, List<String> missingRoles) {}
}
