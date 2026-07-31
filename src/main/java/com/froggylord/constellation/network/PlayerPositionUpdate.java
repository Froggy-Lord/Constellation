package com.froggylord.constellation.network;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;

public record PlayerPositionUpdate(ClientboundPlayerPositionPacket packet, Vec3 before, Vec3 after) {}
