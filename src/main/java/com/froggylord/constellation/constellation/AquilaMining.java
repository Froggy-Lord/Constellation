package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.AquilaConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.data.TabList;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aquila — mining. For now: powder totals off the sidebar and commission progress off the tab
 * list, shown only in the mining worlds. Patterns are best-effort English literals; a live line
 * may need a tweak, but the structure (sidebar for powder, tab for commissions) matches how the
 * other mods read them.
 */
public class AquilaMining extends BaseConstellation {

    @Override public String id() { return "aquila"; }
    @Override public String displayName() { return "Aquila"; }
    @Override public String description() { return "Mining — powder, commissions, Crystal Hollows"; }

    private static final Pattern POWDER = Pattern.compile("(Mithril|Gemstone|Glacite) Powder:?\\s*([\\d,]+)");
    private static final Pattern COMMISSION = Pattern.compile("(?<name>[A-Za-z ]+?): (?<val>\\d+(?:\\.\\d+)?%|DONE)");
    private static final Pattern FORGE = Pattern.compile("(?<slot>\\d+)\\. (?<item>.+): (?<time>\\d+h|\\d+m|\\d+s|Ready!)");
    private static final Pattern COMPASS = Pattern.compile("Wishing Compass:?\\s*(\\d+)\\s*(\\d+)\\s*(\\d+)");
    private static final Pattern FUEL = Pattern.compile("Fuel:?\\s*(\\d+\\.?\\d*)/(\\d+\\.?\\d*)k?");
    private static final Pattern COLD = Pattern.compile("Cold:?\\s*-?(\\d+)");
    private static final Pattern HOTM = Pattern.compile("HOTM:?\\s*(\\d+)");
    private static final Pattern DRILL_FUEL = Pattern.compile("(?:⛏\\s*)?(?:Drill\\s*)?Fuel:?\\s*([\\d,\\.]+[kKmM]?)\\s*/?\\s*([\\d,\\.]+[kKmM]?)?");
    private static final Pattern PICKONIMBUS = Pattern.compile("Pickonimbus:?\\s*([\\d,]+)\\s*/?\\s*([\\d,]+)");

    // Fetchur item hints → correct item name (Skyblocker FetchurSolver mapping)
    private static final java.util.Map<String, String> FETCHUR = new java.util.HashMap<>();
    static {
        FETCHUR.put("hot stuff", "Lava Bucket");
        FETCHUR.put("yellow rock", "Gold Ore");
        FETCHUR.put("white stone", "Diorite");
        FETCHUR.put("red stone", "Redstone");
        FETCHUR.put("shiny rock", "Mithril");
        FETCHUR.put("blue gem", "Lapis Lazuli");
        FETCHUR.put("black rock", "Coal");
        FETCHUR.put("green rock", "Emerald");
        FETCHUR.put("clear rock", "Diamond");
        FETCHUR.put("expensive rock", "Diamond Block");
        FETCHUR.put("iron rock", "Iron Ore");
        FETCHUR.put("hard rock", "Obsidian");
    }
    private static final int[] COLD_STEPS = {25, 50, 75, 90, 95, 99};

    private AquilaConfig cfg;

    private static int lastColdStep = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (AquilaConfig) getConfig();
        // cold thresholds — a clean title ping instead of the screen-filling vignette
        ConstellationClient.tick().every(10, "aquila-cold", () -> {
            if (cfg == null || !cfg.coldWarning || !inMining()) return;
            int cold = readCold();
            int step = 0;
            for (int s : COLD_STEPS) if (cold >= s) step = s;
            if (step > lastColdStep) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§b❄ Cold " + cold + "!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, step >= 90 ? 0.4f : 0.8f);
                }
            }
            lastColdStep = step;
        });
        // Fetchur / Puzzler chat hints
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !ConstellationClient.loc().onHypixel()) return;
            String s = msg.getString();
            // Fetchur: "[NPC] Fetchur: hot stuff" → match to item
            if (cfg.fetchurSolver && s.contains("Fetchur")) {
                String low = s.toLowerCase(java.util.Locale.ROOT);
                String hint = low.substring(low.indexOf("fetchur") + 7).trim().replace(":", "").trim();
                for (var e : FETCHUR.entrySet()) {
                    if (hint.contains(e.getKey())) {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player != null)
                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[Fetchur] §fWants: §6" + e.getValue()));
                        break;
                    }
                }
            }
            // Puzzler: "[NPC] Puzzler: <question>" → known answers
            if (cfg.puzzlerSolver && s.contains("Puzzler")) {
                String low = s.toLowerCase(java.util.Locale.ROOT);
                String block = puzzlerAnswer(low);
                if (block != null) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player != null)
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[Puzzler] §fAnswer: §6" + block));
                }
            }
        });

        // Treasure chest ESP — highlight chests in Crystal Hollows
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.treasureChestEsp || !inMining()) return;
            var mc2 = net.minecraft.client.Minecraft.getInstance();
            if (mc2.level == null || mc2.player == null) return;
            var pp = mc2.player.position();
            for (var e : mc2.level.entitiesForRendering()) {
                if (e.distanceToSqr(pp) > 1600) continue; // 40-block range
                var name = e.getCustomName();
                if (name == null) continue;
                String nm = name.getString().toLowerCase(java.util.Locale.ROOT);
                if (nm.contains("chest") || nm.contains("treasure") || nm.contains("loot")) {
                    wctx.highlight(e.getBoundingBox().inflate(0.3), 0x80FFAA00, true);
                    wctx.label(e.position().add(0, e.getBbHeight() + 0.4, 0), "Treasure", 0xFFFFAA00, true);
                }
            }
        });

        // Pickobulus prediction — highlight the break zone of the targeted block
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.pickobulusPreview || !inMining()) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.hitResult == null) return;
            if (mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
            var stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) return;
            String name = stack.getItem().getDescriptionId();
            if (!name.contains("pickaxe") && !name.contains("drill") && !name.contains("gauntlet")) return;
            var hit = (net.minecraft.world.phys.BlockHitResult) mc.hitResult;
            var center = hit.getBlockPos();
            // Skyblocker uses a radius based on Pickobulus level (2-4 blocks).
            // we default to 2 for visibility; the user can spot-check from the highlight.
            int r = 2;
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx*dx + dy*dy + dz*dz > r*r) continue;
                        var bp = center.offset(dx, dy, dz);
                        var bs = mc.level.getBlockState(bp);
                        if (bs.isAir()) continue;
                        wctx.highlight(new net.minecraft.world.phys.AABB(bp), 0x40AAFF00, false);
                    }
        });

        // mineshaft entry + scatha — rare events worth a title ping
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay || cfg == null || !ConstellationClient.loc().onHypixel()) return;
            if (!cfg.mineshaftAlert && !cfg.scathaAlert) return;
            String s = msg.getString();
            if (cfg.mineshaftAlert && (s.contains("You have entered a Glacite Mineshaft") || s.contains("found a Glacite Mineshaft"))) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§b⛏ Mineshaft!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.0f);
                }
            }
            if (cfg.scathaAlert && s.contains("Scatha") && (s.contains("spawned") || s.contains("found") || s.contains("worm"))) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§6🐛 SCATHA!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.9f, 1.1f);
                }
            }
        });
    }

    private static int readCold() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = COLD.matcher(line);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (AquilaConfig) getConfig();
        if (cfg == null) return;

        if (cfg.powderHud) {
            hud.register(new HudWidget("aquila-powder", "Powder",
                () -> inMining() ? powderLine() : null,
                HudPosition.of(2, 60), cfg.powderHud));
        }
        if (cfg.commissionHud) {
            hud.register(new HudWidget("aquila-commissions", "Commissions",
                () -> inMining() ? commissionLine() : null,
                HudPosition.of(2, 70), cfg.commissionHud));
            hud.register(new HudWidget("aquila-forge", "Forge",
                () -> inMining() ? forgeLine() : null,
                HudPosition.of(2, 80), cfg.commissionHud));
            hud.register(new HudWidget("aquila-compass", "Compass",
                () -> inMining() ? compassLine() : null,
                HudPosition.of(2, 90), cfg.commissionHud));
            hud.register(new HudWidget("aquila-fuel", "Fuel",
                () -> inMining() ? fuelLine() : null,
                HudPosition.of(2, 98), cfg.commissionHud));
        }
        if (cfg.coldHud) {
            hud.register(new HudWidget("aquila-cold", "Cold",
                () -> {
                    if (!inMining()) return null;
                    int c = readCold();
                    if (c <= 0) return null;
                    String col = c >= 90 ? "§c" : c >= 50 ? "§b" : "§7";
                    return col + "❄ " + c + "/100";
                },
                HudPosition.of(2, 106), cfg.coldHud));
        }
        if (cfg.hotmHud) {
            hud.register(new HudWidget("aquila-hotm", "HOTM",
                () -> {
                    if (!inMining()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = HOTM.matcher(line);
                        if (m.find()) return "§6HOTM " + m.group(1);
                    }
                    return null;
                },
                HudPosition.of(2, 114), cfg.hotmHud));
        }
        if (cfg.drillFuelHud) {
            hud.register(new HudWidget("aquila-drillfuel", "DrillFuel",
                () -> {
                    if (!inMining()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = DRILL_FUEL.matcher(line);
                        if (m.find()) {
                            String cur = m.group(1), max = m.group(2);
                            return "§2⛏ Fuel §f" + cur + (max != null ? "/" + max : "");
                        }
                    }
                    return null;
                },
                HudPosition.of(2, 122), cfg.drillFuelHud));
        }
        if (cfg.pickonimbusHud) {
            hud.register(new HudWidget("aquila-pickonimbus", "Pickonimbus",
                () -> {
                    if (!inMining()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = PICKONIMBUS.matcher(line);
                        if (m.find()) return "§6⛏ Pickonimbus §f" + m.group(1);
                    }
                    return null;
                },
                HudPosition.of(2, 130), cfg.pickonimbusHud));
        }
    }

    private static boolean inMining() {
        SkyblockArea a = ConstellationClient.loc().area();
        return a == SkyblockArea.DWARVEN_MINES || a == SkyblockArea.CRYSTAL_HOLLOWS
            || a == SkyblockArea.GLACITE_TUNNELS || a == SkyblockArea.GLACITE_MINESHAFT;
    }

    /** All powder types from the sidebar. */
    private static String powderLine() {
        String mithril = null, gemstone = null, glacite = null;
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = POWDER.matcher(line);
            if (!m.find()) continue;
            switch (m.group(1)) {
                case "Mithril" -> mithril = m.group(2);
                case "Gemstone" -> gemstone = m.group(2);
                case "Glacite" -> glacite = m.group(2);
                default -> { }
            }
        }
        if (mithril == null && gemstone == null && glacite == null) return null;
        StringBuilder sb = new StringBuilder();
        if (mithril != null) sb.append("§2").append(mithril).append("m");
        if (gemstone != null) sb.append(sb.length() > 0 ? "  " : "").append("§d").append(gemstone).append("g");
        if (glacite != null) sb.append(sb.length() > 0 ? "  " : "").append("§b").append(glacite).append("g");
        return sb.toString();
    }

    /** Active commissions + progress from the tab list. */
    private static String commissionLine() {
        List<String> tab = TabList.lines();
        boolean inSection = false;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String line : tab) {
            if (line.startsWith("Commissions")) { inSection = true; continue; }
            if (!inSection) continue;
            Matcher m = COMMISSION.matcher(line);
            if (!m.matches()) break; // section ended
            if (shown++ > 0) sb.append("  §7| ");
            String val = m.group("val");
            sb.append("§f").append(m.group("name").trim()).append(" §b").append(val);
            if (shown >= 2) break;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Forge slots with remaining time. */
    private static String forgeLine() {
        var tab = TabList.lines();
        boolean section = false;
        StringBuilder sb = new StringBuilder();
        for (String line : tab) {
            if (line.contains("Forge")) { section = true; continue; }
            if (!section) continue;
            Matcher m = FORGE.matcher(line);
            if (!m.find()) break;
            if (sb.length() > 0) sb.append(" §7|");
            String time = m.group("time");
            sb.append("§f").append(m.group("item").trim()).append(" §7").append(time);
        }
        return sb.length() == 0 ? null : "§6" + sb.toString();
    }

    private static String compassLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = COMPASS.matcher(line);
            if (m.find()) return "§6🧭 " + m.group(1) + " " + m.group(2) + " " + m.group(3);
        }
        return null;
    }

    private static String puzzlerAnswer(String q) {
        if (q.contains("light") && q.contains("blue")) return "Lapis Block";
        if (q.contains("gold") || q.contains("yellow")) return "Gold Block";
        if (q.contains("diamond")) return "Diamond Block";
        if (q.contains("emerald") || q.contains("green")) return "Emerald Block";
        if (q.contains("redstone") || q.contains("red")) return "Redstone Block";
        if (q.contains("coal") || q.contains("black")) return "Coal Block";
        if (q.contains("iron") || q.contains("white") && q.contains("grey")) return "Iron Block";
        if (q.contains("obsidian") || q.contains("dark")) return "Obsidian";
        if (q.contains("snow") || q.contains("cold")) return "Snow Block";
        if (q.contains("pumpkin") || q.contains("orange")) return "Pumpkin";
        return null;
    }

    private static String fuelLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = FUEL.matcher(line);
            if (m.find()) return "§2⛏ Fuel §f" + m.group(1) + "/" + m.group(2) + "k";
        }
        return null;
    }
}
