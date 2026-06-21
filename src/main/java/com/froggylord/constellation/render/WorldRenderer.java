package com.froggylord.constellation.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * World-space 3D overlay renderer. Features queue primitives via Ctx during the render
 * phase, and they get flushed to the GPU in batches. Stubbed for Phase 1 — actual
 * LevelRenderEvents hookup comes in Phase 3 (Orion dungeons).
 */
public class WorldRenderer {

    public static class Ctx {
        public final Vec3 camera;

        final List<BoxPrim> boxes = new ArrayList<>();
        final List<LinePrim> lines = new ArrayList<>();
        final List<LabelPrim> labels = new ArrayList<>();

        public Ctx(Vec3 camera) { this.camera = camera; }

        public void box(AABB box, int colour, boolean throughWalls) {
            boxes.add(new BoxPrim(box, colour, throughWalls, true));
        }

        public void outline(AABB box, int colour, boolean throughWalls) {
            boxes.add(new BoxPrim(box, colour, throughWalls, false));
        }

        public void line(Vec3 from, Vec3 to, int colour, boolean throughWalls) {
            lines.add(new LinePrim(from, to, colour, throughWalls));
        }

        public void label(Vec3 pos, String text, int colour, boolean throughWalls) {
            labels.add(new LabelPrim(pos, text, colour, throughWalls));
        }
    }

    public record BoxPrim(AABB box, int colour, boolean throughWalls, boolean filled) {}
    public record LinePrim(Vec3 from, Vec3 to, int colour, boolean throughWalls) {}
    public record LabelPrim(Vec3 pos, String text, int colour, boolean throughWalls) {}

    private final List<Consumer<Ctx>> featureRenderers = new ArrayList<>();

    public void register(Consumer<Ctx> renderer) {
        featureRenderers.add(renderer);
    }

    // actual LevelRenderEvents wiring comes in Phase 3
}
