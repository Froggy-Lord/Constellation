package com.froggylord.constellation.compat.rei;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.constellation.LyraRecipeRepository;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.CollapsibleEntryRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

// ported from Skyblocker (LGPL-3.0-or-later): compatibility/rei/SkyblockerREIClientPlugin.java
public final class LyraReiClientPlugin implements REIClientPlugin {
    private static boolean enabled() {
        boolean enabled = ConstellationClient.cfg().lyra.enabled && ConstellationClient.cfg().lyra.recipeBrowser
                && ConstellationClient.cfg().lyra.recipeBrowserReiIntegration && !FabricLoader.getInstance().isModLoaded("skyblocker");
        if (enabled) LyraRecipeRepository.init();
        return enabled;
    }

    @Override public void registerCategories(CategoryRegistry registry) {
        if (!enabled()) return;
        registry.add(new LyraReiCategory(LyraReiDisplay.CRAFTING, "SkyBlock Crafting", Items.CRAFTING_TABLE));
        registry.add(new LyraReiCategory(LyraReiDisplay.FORGE, "SkyBlock Forge", Items.ANVIL));
        registry.add(new LyraReiCategory(LyraReiDisplay.NPC_SHOP, "SkyBlock NPC Shop", Items.GOLD_NUGGET));
        registry.add(new LyraReiCategory(LyraReiDisplay.KAT, "SkyBlock Kat Upgrade", Items.BONE));
        registry.add(new LyraReiInfoCategory());
        registry.addWorkstations(LyraReiDisplay.CRAFTING, EntryStacks.of(Items.CRAFTING_TABLE));
        registry.addWorkstations(LyraReiDisplay.FORGE, EntryStacks.of(Items.ANVIL));
        registry.addWorkstations(LyraReiDisplay.NPC_SHOP, EntryStacks.of(Items.GOLD_NUGGET));
        registry.addWorkstations(LyraReiDisplay.KAT, EntryStacks.of(Items.BONE));
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        if (enabled()) {
            registry.registerGlobalDisplayGenerator(new LyraReiDisplayGenerator());
            registry.registerGlobalDisplayGenerator(new LyraReiInfoGenerator());
        }
    }

    @Override public void registerEntries(EntryRegistry registry) {
        if (!enabled() || !ConstellationClient.cfg().lyra.recipeBrowserReiEntries || LyraRecipeRepository.snapshot().state() != LyraRecipeRepository.State.READY) return;
        registry.addEntries(LyraRecipeRepository.snapshot().items().stream().map(item -> EntryStacks.of(LyraRecipeRepository.stack(item.id(), 1))).toList());
    }

    @Override public void registerCollapsibleEntries(CollapsibleEntryRegistry registry) {
        if (!enabled() || !ConstellationClient.cfg().lyra.recipeBrowserReiCollapsible || LyraRecipeRepository.snapshot().state() != LyraRecipeRepository.State.READY) return;
        LyraRecipeRepository.snapshot().parents().forEach((parent, children) -> {
            var parentItem = LyraRecipeRepository.item(parent);
            if (parentItem == null) return;
            var entries = Stream.concat(Stream.of(parent), children.stream()).distinct().filter(id -> LyraRecipeRepository.item(id) != null)
                    .map(id -> EntryStacks.of(LyraRecipeRepository.stack(id, 1))).toList();
            if (entries.size() < 2) return;
            String path = parent.toLowerCase(Locale.ROOT).replace(';', '_').replaceAll("[^a-z0-9_./-]", "_");
            registry.group(Identifier.fromNamespaceAndPath("constellation", "rei_family/" + path), Component.literal(parentItem.name()), entries);
        });
    }

    @Override public void registerTransferHandlers(TransferHandlerRegistry registry) {
        if (enabled() && ConstellationClient.cfg().lyra.recipeBrowserSafeViewRecipe) registry.register(new LyraReiTransferHandler());
    }

    @Override public double getPriority() { return -50; }
}
