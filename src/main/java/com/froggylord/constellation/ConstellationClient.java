package com.froggylord.constellation;

import com.froggylord.constellation.config.ConfigManager;
import com.froggylord.constellation.config.ConstellationConfig;
import com.froggylord.constellation.core.*;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.keybind.KeybindRegistry;
import com.froggylord.constellation.network.PacketBus;
import com.froggylord.constellation.render.BatchRenderer;
import com.froggylord.constellation.render.HudRenderer;
import com.froggylord.constellation.render.NebulaTheme;
import com.froggylord.constellation.render.WorldRenderer;
import com.froggylord.constellation.ui.ConfigScreen;
import com.froggylord.constellation.ui.HubScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstellationClient implements ClientModInitializer {

    public static final String MOD_ID = "constellation";
    public static final Logger LOGGER = LoggerFactory.getLogger("Constellation");

    private static ConstellationClient instance;

    private EventBus eventBus;
    private FeatureManager featureManager;
    private TickManager tickManager;
    private LocationManager locationManager;
    private ConfigManager configManager;
    private HudManager hudManager;
    private WorldRenderer worldRenderer;
    private BatchRenderer batchRenderer;
    private KeybindRegistry keybindRegistry;
    private PacketBus packetBus;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Constellation starting up...");

        configManager = new ConfigManager();
        configManager.load();
        NebulaTheme.init();

        eventBus = new EventBus();
        tickManager = new TickManager();
        locationManager = new LocationManager();
        packetBus = new PacketBus();
        keybindRegistry = new KeybindRegistry();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(tickManager::onEndTick);
        locationManager.init();
        com.froggylord.constellation.core.ActionBar.init();
        com.froggylord.constellation.core.StatStore.init();

        // world renderer hooked before features init so they can register draw callbacks
        worldRenderer = new WorldRenderer();
        batchRenderer = new BatchRenderer();
        worldRenderer.init();

        featureManager = new FeatureManager();
        featureManager.discoverAndInit();

        hudManager = new HudManager();
        HudRenderer.init();
        featureManager.registerHudElements();

        // root commands via vanilla brigadier
        // screen opens deferred to next tick so the chat-close doesn't race and insta-close us
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var mc = Minecraft.getInstance();

            // /constellation → opens hub
            dispatcher.register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder
                    .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("constellation")
                    .executes(ctx -> { mc.execute(() -> mc.setScreenAndShow(new HubScreen(null))); return 1; })
            );
            // /cn → opens hub. subcommands: hud, config
            dispatcher.register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder
                    .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("cn")
                    .executes(ctx -> { mc.execute(() -> mc.setScreenAndShow(new HubScreen(null))); return 1; })
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("hud")
                        .executes(ctx -> {
                            mc.execute(() -> mc.setScreenAndShow(new com.froggylord.constellation.hud.HudEditScreen(null)));
                            return 1;
                        }))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>literal("config")
                        .executes(ctx -> {
                            var ids = ConstellationClient.featureManager().getLoadedIds();
                            String first = ids.isEmpty() ? "apollo" : ids.iterator().next();
                            mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(first, null)));
                            return 1;
                        }))
            );
            featureManager.registerCommands(dispatcher);
        });

        keybindRegistry.registerHubKey();

        LOGGER.info("Constellation ready. {} constellations loaded.", featureManager.getLoadedCount());
    }

    // instance accessors
    public static ConstellationClient instance() { return instance; }
    public EventBus events() { return eventBus; }
    public FeatureManager features() { return featureManager; }
    public TickManager ticks() { return tickManager; }
    public LocationManager location() { return locationManager; }
    public ConfigManager configManager() { return configManager; }
    public HudManager hud() { return hudManager; }
    public WorldRenderer worldRender() { return worldRenderer; }
    public BatchRenderer batchRender() { return batchRenderer; }
    public KeybindRegistry keys() { return keybindRegistry; }
    public PacketBus packets() { return packetBus; }

    // static convenience
    public static ConstellationConfig cfg() { return instance.configManager.get(); }
    public static EventBus bus() { return instance.eventBus; }
    public static TickManager tick() { return instance.tickManager; }
    public static LocationManager loc() { return instance.locationManager; }
    public static HudManager hudManager() { return instance.hudManager; }
    public static FeatureManager featureManager() { return instance.featureManager; }
    public static WorldRenderer world() { return instance.worldRenderer; }
    public static void saveConfig() { instance.configManager.save(); }
}
