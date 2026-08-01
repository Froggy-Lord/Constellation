package com.froggylord.constellation.command;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.Routes;
import com.froggylord.constellation.constellation.ItemProtection;
import com.froggylord.constellation.core.FeatureManager;
import com.froggylord.constellation.core.Scraper;
import com.froggylord.constellation.data.RoomMatch;
import com.froggylord.constellation.hud.HudEditScreen;
import com.froggylord.constellation.render.WorldRenderer;
import com.froggylord.constellation.ui.ConfigScreen;
import com.froggylord.constellation.ui.HubScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public final class CommandRegistry {

    private static WorldRenderer.Handle debugBox;

    private CommandRegistry() {}

    public static void register(FeatureManager features) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(root("cn", features));
            dispatcher.register(root("constellation", features));
            features.registerCommands(dispatcher);
            ItemProtection.registerCommands(dispatcher);
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root(String name, FeatureManager features) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
            .executes(ctx -> openHub())
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("group", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(features.getAllIds(), builder))
                    .executes(ctx -> toggle(features, StringArgumentType.getString(ctx, "group"), null))
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("onoff", StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"on", "off"}, builder))
                        .executes(ctx -> toggle(features, StringArgumentType.getString(ctx, "group"), StringArgumentType.getString(ctx, "onoff"))))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("room")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reset")
                    .executes(ctx -> { RoomMatch.resetCache(); message("§aRoom cache reset"); return 1; })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("box").executes(ctx -> debugBox()))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("verify")
                .executes(ctx -> verify(null))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("onoff", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"on", "off"}, builder))
                    .executes(ctx -> verify(StringArgumentType.getString(ctx, "onoff")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hud")
                .executes(ctx -> { Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new HudEditScreen(null))); return 1; }))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config").executes(ctx -> openConfig(features)))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("scrape")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("mode", StringArgumentType.word())
                    .executes(ctx -> { Scraper.scrape(StringArgumentType.getString(ctx, "mode")); return 1; })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("autoscrape")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("onoff", StringArgumentType.word())
                    .executes(ctx -> { Scraper.setAutoScrape(StringArgumentType.getString(ctx, "onoff").equalsIgnoreCase("on")); return 1; })))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("route")
                .executes(ctx -> { message(Routes.routeStatus()); return 1; })
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("next")
                    .executes(ctx -> { message(Routes.nextStep()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("previous")
                    .executes(ctx -> { message(Routes.previousStep()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("restart")
                    .executes(ctx -> { message(Routes.restartPlayback()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status")
                    .executes(ctx -> { message(Routes.isRecording() ? Routes.recordingStatus() : Routes.routeStatus()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("steps")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> { message(Routes.visibleSteps(IntegerArgumentType.getInteger(ctx, "count"))); return 1; })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("linecolor")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(ctx -> { message(Routes.lineColour(StringArgumentType.getString(ctx, "argb"), false)); return 1; })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pearlcolor")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("argb", StringArgumentType.word())
                        .executes(ctx -> { message(Routes.lineColour(StringArgumentType.getString(ctx, "argb"), true)); return 1; })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("record")
                    .executes(ctx -> { message(Routes.record()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
                    .executes(ctx -> { message(Routes.stop()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("undo")
                    .executes(ctx -> { message(Routes.undoRecordedStep()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("play")
                    .executes(ctx -> { message(Routes.play()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tag")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            new String[]{"etherwarp", "interact", "mine", "tnt", "pearl", "secret", "item", "bat", "exit", "exitroute"}, builder))
                        .executes(ctx -> { message(Routes.tag(StringArgumentType.getString(ctx, "type"))); return 1; })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("save")
                    .executes(ctx -> { message(Routes.save()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("load")
                    .executes(ctx -> { message(Routes.load()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear")
                    .executes(ctx -> { message(Routes.clearRoute()); return 1; }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
                    .executes(ctx -> { message(Routes.listRoutes()); return 1; })));
    }

    private static int toggle(FeatureManager features, String group, String value) {
        var found = features.get(group.toLowerCase(java.util.Locale.ROOT));
        if (found.isEmpty()) { message("§cUnknown constellation: " + group); return 0; }
        boolean enabled = value == null ? !found.get().isEnabled() : value.equalsIgnoreCase("on");
        if (value != null && !value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) {
            message("§cExpected on or off"); return 0;
        }
        features.setEnabled(found.get().id(), enabled);
        message((enabled ? "§a" : "§7") + found.get().displayName() + (enabled ? " enabled" : " disabled"));
        return 1;
    }

    private static int verify(String value) {
        boolean enabled = value == null ? !ConstellationClient.verify() : value.equalsIgnoreCase("on");
        if (value != null && !value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) {
            message("§cExpected on or off"); return 0;
        }
        ConstellationClient.setVerify(enabled);
        message(enabled ? "§a/cn verify ON" : "§7/cn verify OFF");
        return 1;
    }

    private static int openHub() {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new HubScreen(null)));
        return 1;
    }

    private static int debugBox() {
        if (debugBox != null) ConstellationClient.world().remove(debugBox);
        debugBox = ConstellationClient.world().register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var centre = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(4.0));
            ctx.highlight(new AABB(centre.x - 0.5, centre.y - 0.5, centre.z - 0.5,
                centre.x + 0.5, centre.y + 0.5, centre.z + 0.5), 0xA000E5FF, true);
            ctx.label(centre.add(0, 0.8, 0), "constellation render check", 0xFFFFFFFF, true);
        });
        message("§aSee-through render box enabled");
        return 1;
    }

    private static int openConfig(FeatureManager features) {
        String first = features.getLoadedIds().stream().findFirst().orElse("apollo");
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new ConfigScreen(first, null)));
        return 1;
    }

    private static void message(String text) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(text));
    }
}
