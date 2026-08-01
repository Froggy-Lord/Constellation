package com.froggylord.constellation.keybind;

import com.froggylord.constellation.ui.HubScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KeybindRegistry {

    private static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("constellation", "constellation");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CATEGORY_ID);

    private final List<KeyMapping> binds = new ArrayList<>();
    private KeyMapping hubKey;

    public void registerHubKey() {
        hubKey = new KeyMapping(
            "key.constellation.hub",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            CATEGORY
        );

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (hubKey.consumeClick()) {
                if (client.player != null) {
                    Minecraft.getInstance().setScreenAndShow(new HubScreen(null));
                }
            }
        });

        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(hubKey);
        binds.add(hubKey);
    }

    public KeyMapping register(String id, int defaultKey) {
        KeyMapping kb = new KeyMapping(
            "key.constellation." + id,
            InputConstants.Type.KEYSYM,
            defaultKey,
            CATEGORY
        );
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(kb);
        binds.add(kb);
        return kb;
    }

    public List<KeyMapping> all() { return Collections.unmodifiableList(binds); }
}
