package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.LyraConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyraEconomy extends BaseConstellation {

    @Override public String id() { return "lyra"; }
    @Override public String displayName() { return "Lyra"; }
    @Override public String description() { return "coin/purse stuff"; }

    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)");
    private static final Pattern BITS = Pattern.compile("Bits:\\s*([\\d,]+)");

    private static long sessionStart = Long.MIN_VALUE;
    private static long currentPurse = 0;
    private static long lastPurse = 0;
    private static long changeAt = 0;
    private static long changeAmount = 0;

    private LyraConfig cfg;

    @Override
    public void init(InitContext ctx) {
        cfg = (LyraConfig) getConfig();
        // refresh the parsed purse each second
        ConstellationClient.tick().every(20, "lyra-purse", LyraEconomy::readPurse);
        
        if (cfg.bazaarUndercutAlert) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                String s = msg.getString();
                if ((s.contains("undercut") || s.contains("undercut")) && (s.contains("Bazaar") || s.contains("order"))) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§6⚠ Bazaar undercut! §7" + s));
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.6f);
                    }
                }
            });
        }

        
        if (cfg.auctionOutbidAlert || cfg.auctionSoldAlert) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
                if (overlay || !ConstellationClient.loc().onHypixel()) return;
                String s = msg.getString();
                var mc = Minecraft.getInstance();
                if (mc.player == null) return;
                if (cfg.auctionOutbidAlert && s.contains("outbid") && s.contains("auction")) {
                    mc.player.sendSystemMessage(Component.literal("§6⚠ Outbid! §7" + s));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.7f);
                }
                if (cfg.auctionSoldAlert && (s.contains("sold") || s.contains("expired") || s.contains("ended")) && s.contains("auction")) {
                    mc.player.sendSystemMessage(Component.literal(s.contains("sold") ? "§a💰 Sold! §7" + s : "§c⏰ Expired! §7" + s));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.2f);
                }
            });
        }

        
        LyraTooltips.init(cfg);
        
        LyraSlotText.init(cfg);
    }

    private static void readPurse() {
        long prev = currentPurse;
        for (String line : ConstellationClient.loc().getSidebarLines()) {
            Matcher m = PURSE.matcher(line);
            if (!m.find()) continue;
            currentPurse = parse(m.group(1));
            if (sessionStart == Long.MIN_VALUE) sessionStart = currentPurse;
            if (prev > 0 && currentPurse != prev) {
                changeAmount = currentPurse - prev;
                changeAt = System.currentTimeMillis();
            }
            lastPurse = prev;
            return;
        }
    }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (LyraConfig) getConfig();
        if (cfg == null) return;

        if (cfg.purseHud) {
            hud.register(new HudWidget("lyra-purse", "Purse",
                () -> {
                    if (!ConstellationClient.loc().onHypixel() || sessionStart == Long.MIN_VALUE) return null;
                    String s = "§6" + compact(currentPurse);
                    if (cfg.coinSession) {
                        long delta = currentPurse - sessionStart;
                        if (delta != 0) s += (delta > 0 ? " §a(+" : " §c(-") + compact(Math.abs(delta)) + ")";
                    }
                    return s;
                },
                HudPosition.of(2, 80), cfg.purseHud));
        }
        
        hud.register(new HudWidget("lyra-change", "ΔCoins",
            () -> {
                long ms = System.currentTimeMillis() - changeAt;
                if (ms > 3000 || changeAmount == 0) return null;
                String s = (changeAmount > 0 ? "§a+" : "§c") + compact(Math.abs(changeAmount));
                return s;
            },
            HudPosition.of(50, 86), true));

        if (cfg.quiverHud) {
            hud.register(new HudWidget("lyra-quiver", "Quiver",
                () -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player == null) return null;
                    
                    var inv = mc.player.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        var stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;
                        String id = stack.getItem().getDescriptionId();
                        if (id.contains("arrow") || id.contains("arrow")) {
                            return "§f🏹 " + stack.getCount() + " " + stack.getHoverName().getString();
                        }
                    }
                    return null;
                },
                HudPosition.of(50, 94), cfg.quiverHud));
        }
        if (cfg.bitsHud) {
            hud.register(new HudWidget("lyra-bits", "Bits",
                () -> {
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        Matcher m = BITS.matcher(line);
                        if (m.find()) return "§b" + compact(parse(m.group(1)));
                    }
                    return null;
                },
                HudPosition.of(2, 90), cfg.bitsHud));
        }
        if (cfg.accessoryDisplay) {
            hud.register(new HudWidget("lyra-accessories", "Accessories",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Accessor")) return "§d💍 " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 98), cfg.accessoryDisplay));
        }
        if (cfg.inventoryValueHud) {
            hud.register(new HudWidget("lyra-invvalue", "InvValue",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    var mc = Minecraft.getInstance();
                    if (mc.player == null) return null;
                    com.froggylord.constellation.api.BazaarApi.ensureFresh();
                    double total = 0;
                    int count = 0;
                    var inv = mc.player.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        var stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;
                        var cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                        if (cd == null) continue;
                        var extra = cd.copyTag().getCompoundOrEmpty("ExtraAttributes");
                        if (extra.isEmpty()) continue;
                        String id = extra.getStringOr("id", "");
                        if (id.isEmpty()) continue;
                        double[] bz = com.froggylord.constellation.api.BazaarApi.get(id);
                        if (bz != null && bz[1] > 0) { total += bz[1] * stack.getCount(); count++; }
                    }
                    return count > 0 ? "§6💰 " + compact((long) total) + " §7(" + count + " items)" : null;
                },
                HudPosition.of(2, 106), cfg.inventoryValueHud));
        }
        if (cfg.essenceShopHelper) {
            hud.register(new HudWidget("lyra-essence", "Essence",
                () -> {
                    if (!ConstellationClient.loc().onHypixel()) return null;
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Essence") || line.contains("Wither Essence") || line.contains("Undead Essence"))
                            return "§d✦ " + line.trim();
                    }
                    return null;
                },
                HudPosition.of(2, 114), cfg.essenceShopHelper));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("buy")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    String item = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item");
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("bz " + item);
                    return 1;
                })));
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sell")
            .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    String item = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item");
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.connection.sendCommand("bz " + item);
                    return 1;
                })));
        if (cfg != null && cfg.profileCommand) {
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("profile")
                .executes(ctx -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null || !ConstellationClient.loc().onHypixel()) return 0;
                    mc.player.sendSystemMessage(Component.literal("§6=== Profile Summary ==="));
                    
                    long purse = currentPurse;
                    mc.player.sendSystemMessage(Component.literal("§6Purse: §f" + compact(purse)));
                    
                    for (String line : ConstellationClient.loc().getSidebarLines()) {
                        if (line.contains("Bits")) mc.player.sendSystemMessage(Component.literal("§bBits: §f" + line.substring(line.indexOf(":") + 1).trim()));
                    }
                    
                    if (com.froggylord.constellation.core.ActionBar.hasData()) {
                        mc.player.sendSystemMessage(Component.literal("§cHP: §f" + compact(com.froggylord.constellation.core.ActionBar.health()) + " §7/ " + compact(com.froggylord.constellation.core.ActionBar.maxHealth())));
                        mc.player.sendSystemMessage(Component.literal("§bMana: §f" + compact(com.froggylord.constellation.core.ActionBar.mana()) + " §7/ " + compact(com.froggylord.constellation.core.ActionBar.maxMana())));
                        mc.player.sendSystemMessage(Component.literal("§aDefense: §f" + compact(com.froggylord.constellation.core.ActionBar.defense())));
                    }
                    return 1;
                }));
        }
        
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("coinsreset")
            .executes(ctx -> {
                sessionStart = currentPurse;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[Lyra]§r coin session reset"));
                return 1;
            }));
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.2fM", n / 1_000_000.0);
        return String.format("%.2fB", n / 1_000_000_000.0);
    }
}
