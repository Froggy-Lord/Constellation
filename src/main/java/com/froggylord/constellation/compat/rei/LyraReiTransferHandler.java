package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.ConstellationClient;
import io.github.moulberry.repo.data.NEUCraftingRecipe;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/SkyblockTransferHandler.java
public final class LyraReiTransferHandler implements TransferHandler {
    private static final long COOLDOWN_MS = 1_000;
    private static final long FAILED_MESSAGE_MS = 5_000;
    private static volatile long lastSentAt;
    private static volatile String failedId = "";
    private static volatile long failedUntil;
    @Override public ApplicabilityResult checkApplicable(Context context) {
        if (!ConstellationClient.cfg().lyra.recipeBrowserSafeViewRecipe || !(context.getDisplay() instanceof LyraReiDisplay display))
            return ApplicabilityResult.createNotApplicable();
        if (!(display.recipe() instanceof NEUCraftingRecipe) || display.view().outputs().isEmpty())
            return ApplicabilityResult.createApplicableWithError(Component.literal("This entry has no Craft Item recipe."));
        String id = display.view().outputs().getFirst().id();
        long now = System.currentTimeMillis();
        if (now >= failedUntil) failedId = "";
        if (id.equals(failedId)) return ApplicabilityResult.createApplicableWithError(Component.literal("Craft Item did not open. Try again in a moment."));
        if (now - lastSentAt < COOLDOWN_MS)
            return ApplicabilityResult.createApplicableWithError(Component.literal("Wait a moment before trying again."));
        return ApplicabilityResult.createApplicable();
    }

    @Override public Result handle(Context context) {
        if (!(context.getDisplay() instanceof LyraReiDisplay display) || display.view().outputs().isEmpty()) return Result.createNotApplicable();
        if (!context.isActuallyCrafting()) return Result.createSuccessful();
        var minecraft = context.getMinecraft();
        if (minecraft.player == null || minecraft.player.connection == null) return Result.createFailed(Component.literal("Not connected."));
        String id = display.view().outputs().getFirst().id();
        long now = System.currentTimeMillis();
        if (now - lastSentAt < COOLDOWN_MS) return Result.createFailed(Component.literal("Wait a moment before trying again."));
        if (id.equals(failedId)) { failedId = ""; failedUntil = 0; }
        lastSentAt = now;
        Screen before = minecraft.gui.screen();
        minecraft.player.connection.sendCommand("viewrecipe " + id);
        CompletableFuture.delayedExecutor(1250, TimeUnit.MILLISECONDS).execute(() -> minecraft.execute(() -> {
            if (minecraft.gui.screen() == before) {
                failedId = id;
                failedUntil = System.currentTimeMillis() + FAILED_MESSAGE_MS;
            }
        }));
        return Result.createSuccessful();
    }
}
