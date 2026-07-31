package com.froggylord.constellation.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** World state immediately before an authoritative server block update is applied. */
public record BlockStateUpdate(BlockPos pos, BlockState oldState, BlockState newState) {}
