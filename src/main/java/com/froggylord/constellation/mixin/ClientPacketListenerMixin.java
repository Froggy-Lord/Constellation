package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void constellation$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            ConstellationClient.instance().packets().fire(packet);
        }
    }
}
