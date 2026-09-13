package com.froggylord.constellation;

import com.froggylord.constellation.config.ConfigManager;
import com.froggylord.constellation.config.ConstellationConfig;
import com.froggylord.constellation.command.CommandRegistry;
import com.froggylord.constellation.core.*;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.data.DungeonState;
import com.froggylord.constellation.keybind.KeybindRegistry;
import com.froggylord.constellation.network.PacketBus;
import com.froggylord.constellation.render.HudRenderer;
import com.froggylord.constellation.render.WorldRenderer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstellationClient implements ClientModInitializer {

    public static final String MOD_ID = "constellation";
    public static final Logger LOGGER = LoggerFactory.getLogger("Constellation");

    private static ConstellationClient instance;
    private static boolean verifyMode = false;
    public static boolean verify() { return verifyMode; }

    private EventBus eventBus;
    private FeatureManager featureManager;
    private TickManager tickManager;
    private LocationManager locationManager;
    private ConfigManager configManager;
    private HudManager hudManager;
    private WorldRenderer worldRenderer;
    private KeybindRegistry keybindRegistry;
    private PacketBus packetBus;
    private DungeonState dungeonState;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Constellation starting up...");

        configManager = new ConfigManager();
        configManager.load();
        verifyMode = configManager.get().verifyMode;

        eventBus = new EventBus();
        tickManager = new TickManager();
        locationManager = new LocationManager();
        packetBus = new PacketBus();
        keybindRegistry = new KeybindRegistry();
        com.froggylord.constellation.constellation.ItemProtection.init();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(tickManager::onEndTick);
        locationManager.init();
        dungeonState = new DungeonState();
        dungeonState.init();
        com.froggylord.constellation.data.RoomMatch.init();
        com.froggylord.constellation.core.ActionBar.init();
        com.froggylord.constellation.core.StatStore.init();
        com.froggylord.constellation.core.Scraper.init();

        
        worldRenderer = new WorldRenderer();
        worldRenderer.init();

        featureManager = new FeatureManager();
        featureManager.discoverAndInit();

        hudManager = new HudManager();
        HudRenderer.init();
        featureManager.registerHudElements();

        
        
        CommandRegistry.register(featureManager);

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
    public KeybindRegistry keys() { return keybindRegistry; }
    public PacketBus packets() { return packetBus; }
    public DungeonState dungeonState() { return dungeonState; }

    
    public static ConstellationConfig cfg() { return instance.configManager.get(); }
    public static EventBus bus() { return instance.eventBus; }
    public static TickManager tick() { return instance.tickManager; }
    public static LocationManager loc() { return instance.locationManager; }
    public static HudManager hudManager() { return instance.hudManager; }
    public static FeatureManager featureManager() { return instance.featureManager; }
    public static WorldRenderer world() { return instance.worldRenderer; }
    public static DungeonState dungeon() { return instance.dungeonState; }
    public static void saveConfig() { instance.configManager.save(); }

    public static void setVerify(boolean enabled) {
        verifyMode = enabled;
        cfg().verifyMode = enabled;
        saveConfig();
    }

    public static void verifyNoMatch(String context) {
        if (verifyMode) LOGGER.info("[verify] NO-MATCH: {}", context);
    }

    /** log whether a feature matched its data source. call from sidebar/GUI-reading widgets when verify mode is on */
    public static void verifyLog(String feature, boolean matched, String source) {
        if (!verifyMode) return;
        LOGGER.info("[verify] {} {} — {}", matched ? "MATCHED " : "NO-MATCH", feature, source);
    }
}
