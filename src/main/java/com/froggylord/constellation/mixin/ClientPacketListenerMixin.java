package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.network.PlayerPositionUpdate;
import com.froggylord.constellation.network.BlockStateUpdate;
import com.froggylord.constellation.data.ContainerContentTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Unique private Vec3 constellation$beforePlayerPosition;

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void constellation$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            ConstellationClient.instance().packets().fire(packet);
        }
    }

    // ported from Enhanced Storage (GPL-3.0): mixin/ClientPacketListenerMixin.java
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void constellation$resetContainerContent(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        ContainerContentTracker.reset();
    }

    // ported from Enhanced Storage (GPL-3.0): mixin/ClientPacketListenerMixin.java
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void constellation$markContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        ContainerContentTracker.markReceived(packet.containerId());
    }

    // ported from Skyblocker (LGPL-3.0-or-later):
    // mixins/ClientPacketListenerMixin.java (handleMovePlayer before/return hooks)
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER))
    private void constellation$beforePlayerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        constellation$beforePlayerPosition = player == null ? null : player.position();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void constellation$onPlayerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        Vec3 after = player == null ? null : player.position();
        var packets = ConstellationClient.instance().packets();
        packets.fire(packet);
        if (constellation$beforePlayerPosition != null && after != null)
            packets.fire(new PlayerPositionUpdate(packet, constellation$beforePlayerPosition, after));
        constellation$beforePlayerPosition = null;
    }

    // ported from Devonian (GPL-3.0): mixin/ClientPacketListenerMixin.kt
    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER))
    private void constellation$beforeBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null) ConstellationClient.instance().packets().fire(new BlockStateUpdate(
            packet.getPos().immutable(), level.getBlockState(packet.getPos()), packet.getBlockState()));
    }

    // ported from Devonian (GPL-3.0): mixin/ClientPacketListenerMixin.kt
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void constellation$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        ConstellationClient.instance().packets().fire(packet);
    }

    // ported from Devonian (GPL-3.0): mixin/ClientPacketListenerMixin.kt
    @Inject(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER))
    private void constellation$beforeSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        packet.runUpdates((pos, state) -> ConstellationClient.instance().packets().fire(new BlockStateUpdate(
            pos.immutable(), level.getBlockState(pos), state)));
    }

    // ported from Devonian (GPL-3.0): mixin/ClientPacketListenerMixin.kt
    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void constellation$onSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        ConstellationClient.instance().packets().fire(packet);
    }

    // ported from Odin (BSD-3-Clause): features/impl/skyblock/SpringBoots.kt
    // cancellation point ported from NoFrills (GPL-3.0): mixin/ClientPacketListenerMixin.java
    @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void constellation$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        ConstellationClient.instance().packets().fire(packet);
        com.froggylord.constellation.constellation.AndromedaMirrorverse.onSound(packet);
        // ported from Skyblocker (LGPL-3.0-only): skyblock/hunting/SilencePhantoms.java
        // fusion filter ported from SkyHanni (LGPL-3.0-or-later): features/foraging/MuteFusionMachine.kt
        if (com.froggylord.constellation.constellation.ArtemisGalateaSounds.shouldCancel(packet)
            || com.froggylord.constellation.constellation.VampireSlayerHelper.shouldCancelSound(packet)
            || com.froggylord.constellation.constellation.SlayerSounds.shouldCancel(packet)
            || com.froggylord.constellation.constellation.HerculesHoeLevel.shouldCancel(packet)
            || com.froggylord.constellation.constellation.AquilaMiningAwareness.shouldCancel(packet)
            || com.froggylord.constellation.constellation.AquilaMiningHighlights.shouldCancel(packet)
            || com.froggylord.constellation.constellation.AndromedaDreadfarm.shouldCancelSound(packet)
            || com.froggylord.constellation.constellation.DungeonEndingPresentation.shouldMuteSound()) ci.cancel();
    }

    // ported from SkyHanni (LGPL-2.1): features/dungeon/DungeonSecretTrackerLocator.kt (particle event input)
    // cancellation ported from NoFrills (GPL-3.0): features/general/NoRender.java
    @Inject(method = "handleParticleEvent", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void constellation$filterMageBeam(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        // ported from SkyOcean (MIT): api/HotspotAPI.kt
        boolean hideHotspot = com.froggylord.constellation.constellation.HydraHotspots.onParticle(packet);
        boolean hidePest = com.froggylord.constellation.constellation.HerculesPestWaypoint.onParticle(packet);
        // ported from SkyHanni (LGPL-3.0-or-later): features/rift/everywhere/motes/RiftMotesOrb.kt
        boolean hideMotes = com.froggylord.constellation.constellation.AndromedaMotes.onParticle(packet);
        boolean hideBerberis = com.froggylord.constellation.constellation.AndromedaDreadfarm.onParticle(packet);
        boolean hideLiving = com.froggylord.constellation.constellation.AndromedaLivingCave.onParticle(packet);
        com.froggylord.constellation.constellation.AndromedaStillgore.onParticle(packet);
        if (com.froggylord.constellation.constellation.MageBeamHelper.onParticle(packet) || hideHotspot || hidePest || hideMotes || hideBerberis || hideLiving
            || com.froggylord.constellation.constellation.AshfangHelper.shouldHideParticle(packet)
            || com.froggylord.constellation.constellation.DungeonEndingPresentation.shouldHideParticles()) ci.cancel();
    }

    @Inject(method = "handleParticleEvent", at = @At("RETURN"))
    private void constellation$onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        ConstellationClient.instance().packets().fire(packet);
    }

    // ported from SkyHanni (LGPL-3.0-or-later): features/rift/area/colosseum/TentacleWaypoint.kt
    @Inject(method = "handleDamageEvent", at = @At("RETURN"))
    private void constellation$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        com.froggylord.constellation.constellation.AndromedaColosseum.onDamage(packet);
    }

    // ported from Devonian (GPL-3.0): features/dungeons/f7/TerminalHideCompletion.kt
    @Inject(method = "setTitleText", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void constellation$filterGoldorTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        com.froggylord.constellation.constellation.AndromedaLivingCave.onTitle(packet.text());
        if (com.froggylord.constellation.constellation.AndromedaMirrorverse.shouldHideTitle()) {
            ci.cancel();
            return;
        }
        // ported from Athen (BSD-3-Clause): modules/impl/kuudra/KuudraTitles.kt Supply.Progress cancellation
        if (com.froggylord.constellation.constellation.KuudraTitles.onTitle(packet.text())) {
            ci.cancel();
            return;
        }
        if (com.froggylord.constellation.constellation.DungeonEndingPresentation.shouldHideServerTitle()) {
            ci.cancel();
            return;
        }
        if (com.froggylord.constellation.constellation.TerminalTitleFilter.shouldHide(packet.text(), false)) ci.cancel();
    }

    // ported from Devonian (GPL-3.0): features/dungeons/f7/TerminalHideCompletion.kt
    @Inject(method = "setSubtitleText", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void constellation$filterGoldorSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (com.froggylord.constellation.constellation.DungeonEndingPresentation.shouldHideServerTitle()) {
            ci.cancel();
            return;
        }
        if (com.froggylord.constellation.constellation.TerminalTitleFilter.shouldHide(packet.text(), true)) ci.cancel();
    }
}
