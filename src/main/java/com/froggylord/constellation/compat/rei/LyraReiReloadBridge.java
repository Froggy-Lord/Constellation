package com.froggylord.constellation.compat.rei;

import me.shedaniel.rei.api.common.plugins.PluginManager;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LyraReiReloadBridge {
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private LyraReiReloadBridge() {}
    public static void repositoryReady() {
        if (!QUEUED.compareAndSet(false, true)) return;
        attempt(0);
    }
    private static void attempt(int idlePasses) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().level == null || PluginManager.areAnyReloading()) {
                CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> attempt(0));
                return;
            }
            if (idlePasses < 20) {
                CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> attempt(idlePasses + 1));
                return;
            }
            try { PluginManager.getClientInstance().startReload(); }
            finally { QUEUED.set(false); }
        });
    }
}
