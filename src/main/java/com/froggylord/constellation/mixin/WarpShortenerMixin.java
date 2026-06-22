package com.froggylord.constellation.mixin;

import com.froggylord.constellation.ConstellationClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class WarpShortenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void constellation$shortenWarp(String command, CallbackInfo ci) {
        var cfg = ConstellationClient.cfg().cassiopeia;
        if (cfg == null || !cfg.warpShortcuts) return;

        String t = command.trim();
        if (t.isEmpty() || t.indexOf(' ') >= 0) return; 

        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;

        
        if (conn.getCommands().getRoot().getChild(t) != null) return;

        
        conn.sendCommand("warp " + t);
        ci.cancel();
    }
}
