package com.froggylord.constellation.render;

import com.froggylord.constellation.render.WorldRenderer.LabelPrim;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * GPU-batched text renderer for world-space labels. Queues labels from WorldRenderer
 * and flushes them at END_MAIN. Stubbed for Phase 1 — proper GPU batcher comes in
 * Phase 3 with the dungeon room detection.
 */
public class BatchRenderer {

    public void queueLabels(List<LabelPrim> labels, Vec3 camera) {
        // stubbed — actual batched text rendering in Phase 3
    }

    public void flush(Object worldRenderer) {
        // stubbed
    }
}
