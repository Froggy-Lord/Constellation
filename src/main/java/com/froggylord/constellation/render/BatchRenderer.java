package com.froggylord.constellation.render;

import com.froggylord.constellation.render.WorldRenderer.LabelPrim;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * text labels are rendered through MC's {@code SubmitNodeCollector.submitNameTag()}
 * which handles batching internally — no additional per-frame flush needed.
 * this class exists as a hook point if we ever need custom text rendering
 * (e.g. font-atlas batching for hud text outside of world space).
 */
public class BatchRenderer {

    // mc 26.2's SubmitNodeCollector batches nametag submissions — no-op needed
    public void queueLabels(List<LabelPrim> labels, Vec3 camera) {}
    public void flush(Object worldRenderer) {}
}
