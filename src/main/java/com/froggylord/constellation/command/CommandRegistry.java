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
import com.froggylord.constellation.ui.ProfileViewerScreen;
import com.froggylord.constellation.ui.LyraRecipeBrowserScreen;
import com.froggylord.constellation.constellation.LyraRecipeRepository;
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
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pv")
                .executes(ctx -> openProfile(null))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> openProfile(StringArgumentType.getString(ctx, "player")))));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recipes")
                .executes(ctx -> openRecipes(""))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> openRecipes(StringArgumentType.getString(ctx, "query")))));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recipesupdate").executes(ctx -> updateRecipes()));
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
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config").executes(ctx -> openConfig(features))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("group", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(features.getAllIds(), builder))
                    .executes(ctx -> openConfig(features, StringArgumentType.getString(ctx, "group")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("profile")
                .executes(ctx -> openProfile(null))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("player", StringArgumentType.word())
                    .executes(ctx -> openProfile(StringArgumentType.getString(ctx, "player")))))
            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("recipes")
                .executes(ctx -> openRecipes(""))
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> openRecipes(StringArgumentType.getString(ctx, "query")))))
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

    private static int openProfile(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (!ConstellationClient.cfg().lyra.profileViewer) {
            message("§cProfile Viewer is disabled in Lyra.");
            return 0;
        }
        String target = name == null ? mc.getUser().getName() : name;
        mc.execute(() -> mc.setScreenAndShow(new ProfileViewerScreen(null, target)));
        return 1;
    }

    private static int openRecipes(String query) {
        if (!ConstellationClient.cfg().lyra.enabled || !ConstellationClient.cfg().lyra.recipeBrowser) { message("§cRecipe Browser is disabled in Lyra."); return 0; }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new LyraRecipeBrowserScreen(null, query)));
        return 1;
    }

    private static int updateRecipes() {
        if (!ConstellationClient.cfg().lyra.enabled || !ConstellationClient.cfg().lyra.recipeBrowser) { message("§cRecipe Browser is disabled in Lyra."); return 0; }
        if (!ConstellationClient.cfg().lyra.recipeBrowserUpdateOnRequest) { message("§cRecipe repository updates are disabled in Lyra."); return 0; }
        LyraRecipeRepository.reload(true);
        message("§bUpdating the SkyBlock item repository in the background.");
        return 1;
    }

    private static int debugBox() {
        if (debugBox != null) {
            ConstellationClient.world().remove(debugBox);
            debugBox = null;
            message("§7World render check disabled");
            return 1;
        }
        debugBox = ConstellationClient.world().register(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var centre = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(4.0));
            ctx.highlight(new AABB(centre.x - 0.5, centre.y - 0.5, centre.z - 0.5,
                centre.x + 0.5, centre.y + 0.5, centre.z + 0.5), 0xA000E5FF, true);
            ctx.label(centre.add(0, 0.9, 0), "through walls", 0xFF00E5FF, true);
            ctx.beam(centre.x, centre.y - 1.0, centre.z, 0xFF00E5FF, 4, true);
            var depth = centre.add(3.0, 0, 0);
            ctx.box(new AABB(depth.x - 0.5, depth.y - 0.5, depth.z - 0.5,
                depth.x + 0.5, depth.y + 0.5, depth.z + 0.5), 0x50FFAA33, false);
            ctx.outline(new AABB(depth.x - 0.52, depth.y - 0.52, depth.z - 0.52,
                depth.x + 0.52, depth.y + 0.52, depth.z + 0.52), 0xFFFFAA33, false);
            ctx.line(centre, depth, 0xFFFF55AA, false, 3);
            ctx.label(depth.add(0, 1.25, 0), "depth tested", 0xFFFFAA33, false);
            var routeOne = centre.add(-1.5, -0.75, 1.5);
            var routeTwo = routeOne.add(1.5, 0.5, 1.5);
            var routeThree = routeTwo.add(-1.0, 0.75, 1.5);
            ctx.line(centre, routeOne, 0xFF5599FF, true, 4);
            ctx.line(routeOne, routeTwo, 0xFF5599FF, true, 4);
            ctx.line(routeTwo, routeThree, 0xFF5599FF, true, 4);
            ctx.label(routeTwo.add(0, 1.15, 0), "route path", 0xFF5599FF, true);
        });
        message("§aWorld render check enabled; run /cn box again to hide it");
        return 1;
    }

    private static int openConfig(FeatureManager features) {
        String first = features.getLoadedIds().stream().findFirst().orElse("apollo");
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new ConfigScreen(first, null)));
        return 1;
    }

    private static int openConfig(FeatureManager features, String group) {
        if (!features.getAllIds().contains(group)) {
            message("§cUnknown constellation: " + group);
            return 0;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new ConfigScreen(group, null)));
        return 1;
    }

    private static void message(String text) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(text));
    }
}
