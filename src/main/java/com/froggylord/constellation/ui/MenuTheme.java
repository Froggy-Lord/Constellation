package com.froggylord.constellation.ui;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.VisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.CreateBuffetWorldScreen;
import net.minecraft.client.gui.screens.CreateFlatWorldScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.PresetFlatWorldScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.client.gui.screens.options.InWorldGameRulesScreen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen;

import java.util.Set;

public final class MenuTheme {
    private static Screen renderingScreen;
    private static final Set<Class<? extends Screen>> MENU_SCREENS = Set.of(
        OptionsScreen.class, VideoSettingsScreen.class, SoundOptionsScreen.class,
        ControlsScreen.class, KeyBindsScreen.class, MouseSettingsScreen.class,
        ChatOptionsScreen.class, SkinCustomizationScreen.class, FontOptionsScreen.class,
        OnlineOptionsScreen.class, MultiplayerOptionsScreen.class,
        JoinMultiplayerScreen.class, DirectJoinServerScreen.class, ManageServerScreen.class,
        SelectWorldScreen.class, CreateWorldScreen.class, EditWorldScreen.class,
        CreateFlatWorldScreen.class, PresetFlatWorldScreen.class, CreateBuffetWorldScreen.class,
        WorldCreationGameRulesScreen.class, InWorldGameRulesScreen.class,
        PackSelectionScreen.class, CreditsAndAttributionScreen.class,
        TelemetryInfoScreen.class, StatsScreen.class
    );
    private MenuTheme() {}

    public static VisualConfig config() {
        try {
            return ConstellationClient.cfg().visual;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean titleBackdrop(Screen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.titleBackdrop
            && Minecraft.getInstance().level == null && screen instanceof TitleScreen;
    }

    public static boolean titleButtons() {
        VisualConfig config = config();
        return config != null && config.enabled && config.titleButtons
            && Minecraft.getInstance().level == null && currentScreen() instanceof TitleScreen;
    }

    public static boolean menuBackdrop(Screen screen) {
        VisualConfig config = config();
        return config != null && config.enabled && config.menuBackdrops
            && Minecraft.getInstance().level == null && allowedMenu(screen);
    }

    public static boolean menuButtons() {
        VisualConfig config = config();
        return config != null && config.enabled && config.menuButtons
            && Minecraft.getInstance().level == null && allowedMenu(currentScreen());
    }

    public static boolean menuSliders() {
        VisualConfig config = config();
        return config != null && config.enabled && config.menuSliders
            && Minecraft.getInstance().level == null && allowedMenu(currentScreen());
    }

    public static boolean buttons() { return titleButtons() || menuButtons(); }

    public static void setRenderingScreen(Screen screen) { renderingScreen = screen; }

    private static boolean allowedMenu(Screen screen) {
        return screen != null && MENU_SCREENS.contains(screen.getClass());
    }

    private static Screen currentScreen() { return Minecraft.getInstance().gui.screen(); }
}
