package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HerculesConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.core.LocationManager.SkyblockArea;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HerculesFarming extends BaseConstellation {

    @Override public String id() { return "hercules"; }
    @Override public String displayName() { return "Hercules"; }
    @Override public String description() { return "farming/garden hud"; }

    private static final Pattern CONTEST = Pattern.compile("(?<crop>[A-Za-z ]+):?\\s*(?<pct>\\d+(?:\\.\\d+)?%|DONE)");
    private static final Pattern VISITORS = Pattern.compile("Visitors:?\\s*(\\d+)");
    private static final Pattern PESTS = Pattern.compile("Pests:?\\s*(\\d+)");
    private static final Pattern MILESTONE = Pattern.compile("(\\w+) (?:Crop )?Milestone:?\\s*(\\d+)");
    private static final Pattern COMPOST = Pattern.compile("(?:Organic Matter|Compost):?\\s*([\\d,]+)");
    private static final Pattern SPEED = Pattern.compile("(?:Speed|✦):?\\s*(\\d+)");

    private HerculesConfig cfg;

    private static long lastContestAlert = 0;
    private static int lastPestCount = 0;
    private static long lastPestAlert = 0;

    @Override
    public void init(InitContext ctx) {
        cfg = (HerculesConfig) getConfig();
        if (cfg == null) return;
        // contest start notification — c...
        ConstellationClient.tick().every(20, "hercules-contest-alert", () -> {
            if (!inGarden()) return;
            for (String line : ConstellationClient.loc().getSidebarLines()) {
                if (line.contains("Starts in") || line.contains("Soon")) {
                    long now = System.currentTimeMillis();
                    if (now - lastContestAlert > 60_000) {
                        lastContestAlert = now;
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.gui.hud.resetTitleTimes();
                            mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§a🌾 Contest starting!"));
                            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.0f);
                        }
                    }
                    break;
                }
            }
        });
        
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.glowingMushrooms || !inGarden()) return;
            var mc3 = net.minecraft.client.Minecraft.getInstance();
            if (mc3.level == null || mc3.player == null) return;
            var pp = mc3.player.blockPosition();
            for (int dx = -15; dx <= 15; dx++)
                for (int dz = -15; dz <= 15; dz++)
                    for (int dy = -3; dy <= 5; dy++) {
                        var bp = pp.offset(dx, dy, dz);
                        var bs = mc3.level.getBlockState(bp);
                        if (bs.getBlock().getDescriptionId().contains("mushroom") && !bs.getBlock().getDescriptionId().contains("stem")) {
                            wctx.highlight(new net.minecraft.world.phys.AABB(bp), 0x40FF66FF, true);
                        }
                    }
        });

        
        ConstellationClient.world().register(wctx -> {
            if (cfg == null || !cfg.sweepOverlay || !inGarden()) return;
            var mc2 = net.minecraft.client.Minecraft.getInstance();
            if (mc2.player == null) return;
            var stack = mc2.player.getMainHandItem();
            if (stack.isEmpty()) return;
            String id = stack.getItem().getDescriptionId();
            if (!id.contains("hoe") && !id.contains("axe") && !id.contains("shears")) return;
            var look = mc2.player.getViewVector(1f);
            var eye = mc2.player.getEyePosition(1f);
            
            for (int d = 1; d <= 5; d++) {
                var center = eye.add(look.scale(d));
                for (int x = -2; x <= 2; x++)
                    for (int z = -2; z <= 2; z++) {
                        var bp = net.minecraft.core.BlockPos.containing(center.x + x, center.y, center.z + z);
                        wctx.highlight(new net.minecraft.world.phys.AABB(bp), 0x40AAFF00, false);
                    }
            }
        });

        
        if (cfg.spaceFarmer) {
            ConstellationClient.tick().every(2, "hercules-space", () -> {
                if (!inGarden()) return;
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null && mc.options.keyJump != null)
                    mc.options.keyJump.setDown(true);
            });
        }

        
        if (cfg.dicerFilter) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return true;
                String s = msg.getString();
                if (s.contains("Dicer") || s.contains("dicer")) return false;
                return true;
            });
        }

        
        ConstellationClient.tick().every(20, "hercules-pests", () -> {
            if (cfg == null || !cfg.pestAlert || !inGarden()) return;
            int pests = readPests();
            if (pests > lastPestCount && System.currentTimeMillis() - lastPestAlert > 20_000) {
                lastPestAlert = System.currentTimeMillis();
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.hud.resetTitleTimes();
                    mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§c🐛 " + pests + " Pest" + (pests > 1 ? "s" : "") + "!"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
                }
            }
            lastPestCount = pests;
        });
    }

    private static int readPests() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = PESTS.matcher(line);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (HerculesConfig) getConfig();
        if (cfg == null) return;

        if (cfg.contestHud) {
            hud.register(new HudWidget("hercules-contest", "Contest",
                () -> inGarden() ? contestLine() : null,
                HudPosition.of(2, 110), cfg.contestHud));
        }
        if (cfg.visitorsHud) {
            hud.register(new HudWidget("hercules-visitors", "Visitors",
                () -> inGarden() ? visitorsLine() : null,
                HudPosition.of(2, 120), cfg.visitorsHud));
        }
        if (cfg.pestHud) {
            hud.register(new HudWidget("hercules-pests", "Pests",
                () -> {
                    if (!inGarden()) return null;
                    int p = readPests();
                    return p > 0 ? "§c🐛 " + p : null;
                },
                HudPosition.of(2, 130), cfg.pestHud));
        }
        if (cfg.cropMilestones) {
            hud.register(new HudWidget("hercules-milestone", "Milestone",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = MILESTONE.matcher(line);
                        if (m.find()) return "§e" + m.group(1) + " §f" + m.group(2);
                    }
                    return null;
                },
                HudPosition.of(2, 140), cfg.cropMilestones));
        }
        if (cfg.visitorRequirements) {
            hud.register(new HudWidget("hercules-visitor-req", "VisitorReq",
                () -> {
                    if (!inGarden()) return null;
                    var tab = com.froggylord.constellation.data.TabList.lines();
                    boolean inSection = false;
                    for (String line : tab) {
                        String clean = net.minecraft.ChatFormatting.stripFormatting(line);
                        if (clean.contains("Visitor") && clean.contains("wants")) { inSection = true; continue; }
                        if (!inSection) continue;
                        if (clean.length() < 3) break;
                        return "§e👤 " + clean.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 150), cfg.visitorRequirements));
        }
        if (cfg.composterHud) {
            hud.register(new HudWidget("hercules-compost", "Compost",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = COMPOST.matcher(line);
                        if (m.find()) return "§2🌱 " + m.group(1) + " matter";
                    }
                    return null;
                },
                HudPosition.of(2, 160), cfg.composterHud));
        }
        if (cfg.speedHud) {
            hud.register(new HudWidget("hercules-speed", "Speed",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = SPEED.matcher(line);
                        if (m.find()) return "§f✦ " + m.group(1) + " speed";
                    }
                    return null;
                },
                HudPosition.of(2, 168), cfg.speedHud));
        }
        if (cfg.moongladeBeacon) {
            hud.register(new HudWidget("hercules-beacon", "Beacon",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Beacon") || line.contains("Moonglade")) return "§d🌙 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 176), cfg.moongladeBeacon));
        }
        if (cfg.greenhouseHelper) {
            hud.register(new HudWidget("hercules-greenhouse", "Greenhouse",
                () -> inGarden() ? "§a🌿 Greenhouse active" : null,
                HudPosition.of(2, 184), cfg.greenhouseHelper));
        }
        if (cfg.cropGrowthDisplay) {
            hud.register(new HudWidget("hercules-growth", "Growth",
                () -> {
                    if (!inGarden()) return null;
                    // crop growth is in tab, not sidebar — SkyHanni reads it from the crop menu
                    for (String line : com.froggylord.constellation.data.TabList.lines()) {
                        if (line.contains("Growth") && line.contains("%")) return "§a🌱 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 192), cfg.cropGrowthDisplay));
        }
        if (cfg.farmingContestTimer) {
            hud.register(new HudWidget("hercules-contest-timer", "ContestTimer",
                () -> {
                    if (!inGarden()) return null;
                    // garden contest status is in the tab list, not sidebar
                    for (String line : com.froggylord.constellation.data.TabList.lines()) {
                        if (line.contains("Contest") || line.contains("Jacob")) return "§e🏆 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 200), cfg.farmingContestTimer));
        }
        if (cfg.farmingXpDisplay) {
            hud.register(new HudWidget("hercules-farmxp", "FarmingXP",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Farming") && (line.contains("XP") || line.contains("Level"))) return "§e🌾 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 208), cfg.farmingXpDisplay));
        if (cfg.cropProfitTracker) {
            hud.register(new HudWidget("hercules-profit", "CropProfit",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Coins") || line.contains("Profit")) return "§6💰 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 216), cfg.cropProfitTracker));
        if (cfg.pestRepellentTimer) {
            hud.register(new HudWidget("hercules-repellent", "Repellent",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Repellent") || line.contains("Spray")) return "§a🪲 " + line.trim();
                    return null;
                },
                HudPosition.of(2, 224), cfg.pestRepellentTimer));
        }
        if (cfg.cropUpgradeHelper) {
            hud.register(new HudWidget("hercules-upgrade", "CropUpgrade",
                () -> {
                    if (!inGarden()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines())
                        if (line.contains("Upgrade") || line.contains("Crop")) return "§e⬆ " + line.trim();
                    return null;
                },
                HudPosition.of(2, 232), cfg.cropUpgradeHelper));
        }
        }
        }
    }

    private static boolean inGarden() {
        return ConstellationClient.loc().area() == SkyblockArea.GARDEN;
    }

    private static String contestLine() {
        var tab = com.froggylord.constellation.data.TabList.lines();
        boolean section = false;
        StringBuilder sb = new StringBuilder();
        for (String line : tab) {
            if (line.startsWith("Jacob") || line.contains("Contest")) { section = true; continue; }
            if (!section) continue;
            Matcher m = CONTEST.matcher(line);
            if (!m.find()) break;
            sb.append("§a").append(m.group("crop").trim()).append(" §f").append(m.group("pct"));
            break; 
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String visitorsLine() {
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = VISITORS.matcher(line);
            if (m.find()) return "§e" + m.group(1) + " visiting";
        }
        return null;
    }
}
