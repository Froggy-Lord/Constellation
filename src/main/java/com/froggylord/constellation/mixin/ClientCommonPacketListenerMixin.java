package com.froggylord.constellation.mixin;

import com.froggylord.constellation.constellation.BloodTimer;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from devonian (GPL-3.0): mixin/ClientCommonPacketListenerImplMixin.java
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerMixin {
    @Inject(method = "handlePing", at = @At("RETURN"))
    private void constellation$onServerTick(ClientboundPingPacket packet, CallbackInfo ci) {
        if (packet.getId() < 0) {
            BloodTimer.onServerTick();
            com.froggylord.constellation.constellation.TerracottaTimer.onServerTick();
            com.froggylord.constellation.constellation.KuudraSplits.onServerTick();
            com.froggylord.constellation.constellation.SlayerState.onServerTick();
        }
    }
}
