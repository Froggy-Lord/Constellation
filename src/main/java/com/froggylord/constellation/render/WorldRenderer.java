package com.froggylord.constellation.render;

import com.froggylord.constellation.ConstellationClient;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

public class WorldRenderer {

    public static class Ctx {
        public final Vec3 camera;
        final List<BoxPrim> boxes = new ArrayList<>();
        final List<LinePrim> lines = new ArrayList<>();
        final List<LabelPrim> labels = new ArrayList<>();

        public Ctx(Vec3 camera) { this.camera = camera; }

        public void box(AABB box, int colour, boolean throughWalls) {
            boxes.add(new BoxPrim(box, colour, throughWalls, true, 2.0f));
        }

        public void outline(AABB box, int colour, boolean throughWalls) {
            outline(box, colour, throughWalls, 2.0f);
        }

        public void outline(AABB box, int colour, boolean throughWalls, float lineWidth) {
            boxes.add(new BoxPrim(box, colour, throughWalls, false, Math.clamp(lineWidth, 0.1f, 10.0f)));
        }

        public void highlight(AABB box, int colour, boolean throughWalls) {
            int a = colour >>> 24;
            int fill = (Math.min(0x60, Math.max(a, 0x30)) << 24) | (colour & 0xFFFFFF);
            boxes.add(new BoxPrim(box, fill, throughWalls, true, 2.0f));
            boxes.add(new BoxPrim(box, 0xFF000000 | (colour & 0xFFFFFF), throughWalls, false, 2.0f));
        }

        public void line(Vec3 from, Vec3 to, int colour, boolean throughWalls) {
            lines.add(new LinePrim(from, to, colour, throughWalls));
        }

        public void beam(double x, double y, double z, int colour, int height, boolean throughWalls) {
            lines.add(new LinePrim(new Vec3(x, y, z), new Vec3(x, y + height, z), colour, throughWalls));
        }

        public void label(Vec3 pos, String text, int colour, boolean throughWalls) {
            labels.add(new LabelPrim(pos, text, colour, throughWalls));
        }
    }

    public record BoxPrim(AABB box, int colour, boolean throughWalls, boolean filled, float lineWidth) {}
    public record LinePrim(Vec3 from, Vec3 to, int colour, boolean throughWalls) {}
    public record LabelPrim(Vec3 pos, String text, int colour, boolean throughWalls) {}

    public record Handle(UUID id) {}

    private record Registered(Handle handle, Consumer<Ctx> renderer) {}
    private final List<Registered> featureRenderers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean inited = false;
    private RenderType thruLines;
    private RenderType thruFilled;
    private boolean warnedDrawFailure;

    public Handle register(Consumer<Ctx> renderer) {
        Handle handle = new Handle(UUID.randomUUID());
        featureRenderers.add(new Registered(handle, renderer));
        return handle;
    }

    public void remove(Handle handle) {
        if (handle != null) featureRenderers.removeIf(r -> r.handle().equals(handle));
    }

    public void init() {
        if (inited) return;
        inited = true;
        buildThru();
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::onRender);
    }

    // ported from cryptkit (GPL-3.0): render/WorldRender.java
    private void buildThru() {
        try {
            RenderPipeline lines = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ConstellationClient.MOD_ID, "pipeline/lines_through_walls"))
                .withDepthStencilState(Optional.empty())
                .build());
            thruLines = RenderType.create("constellation_lines_tw",
                RenderSetup.builder(lines).createRenderSetup());

            RenderPipeline filled = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ConstellationClient.MOD_ID, "pipeline/filled_through_walls"))
                .withDepthStencilState(Optional.empty())
                .build());
            thruFilled = RenderType.create("constellation_filled_tw",
                RenderSetup.builder(filled).createRenderSetup());
        } catch (Throwable t) {
            thruLines = RenderTypes.lines();
            thruFilled = RenderTypes.debugFilledBox();
            ConstellationClient.LOGGER.warn("see-through render types unavailable, drawing depth-tested", t);
        }
    }

    private void onRender(LevelRenderContext context) {
        if (featureRenderers.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var camState = context.levelState().cameraRenderState;
        Vec3 cam = camState.pos;
        Ctx ctx = new Ctx(cam);
        for (Registered r : featureRenderers) {
            try { r.renderer().accept(ctx); }
            catch (Exception ignored) {  }
        }
        if (ctx.boxes.isEmpty() && ctx.lines.isEmpty() && ctx.labels.isEmpty()) return;

        PoseStack pose = context.poseStack();
        pose.pushPose();
        SubmitNodeCollector collector = context.submitNodeCollector();

        
        for (boolean tw : new boolean[]{false, true}) {
            for (BoxPrim b : ctx.boxes) {
                if (b.throughWalls() != tw) continue;
                try {
                    AABB relative = b.box().move(-cam.x, -cam.y, -cam.z);
                    if (b.filled()) submitFilled(collector, pose, relative, b.colour(), tw);
                    else submitBoxLines(collector, pose, relative, b.colour(), tw, b.lineWidth());
                } catch (Exception e) { warnDrawFailure(e); }
            }
            for (LinePrim l : ctx.lines) {
                if (l.throughWalls() != tw) continue;
                try { submitLine(collector, pose, l.from().subtract(cam), l.to().subtract(cam), l.colour(), tw); }
                catch (Exception e) { warnDrawFailure(e); }
            }
        }

        
        for (LabelPrim l : ctx.labels) {
            Component c = Component.literal(l.text());
            int bg = ((l.colour() >>> 24) << 24); 
            collector.submitNameTag(pose, l.pos().subtract(cam), LightCoordsUtil.FULL_BRIGHT, c, l.throughWalls(), bg, camState);
        }

        pose.popPose();
    }

    private RenderType filledType(boolean throughWalls) { return throughWalls ? thruFilled : RenderTypes.debugFilledBox(); }
    private RenderType lineType(boolean throughWalls) { return throughWalls ? thruLines : RenderTypes.lines(); }

    private void submitFilled(SubmitNodeCollector collector, PoseStack pose, AABB b, int argb, boolean throughWalls) {
        collector.submitCustomGeometry(pose, filledType(throughWalls), (p, buf) -> {
            try {
                float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
                float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
                quad(buf, p, argb, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
                quad(buf, p, argb, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
                quad(buf, p, argb, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0);
                quad(buf, p, argb, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
                quad(buf, p, argb, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
                quad(buf, p, argb, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
            } catch (Exception e) { warnDrawFailure(e); }
        });
    }

    private void submitBoxLines(SubmitNodeCollector collector, PoseStack pose, AABB b, int argb,
                                boolean throughWalls, float lineWidth) {
        collector.submitCustomGeometry(pose, lineType(throughWalls), (p, buf) -> {
            try {
                float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
                float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
                seg(buf, p, argb, lineWidth, x0,y0,z0, x1,y0,z0); seg(buf, p, argb, lineWidth, x1,y0,z0, x1,y0,z1);
                seg(buf, p, argb, lineWidth, x1,y0,z1, x0,y0,z1); seg(buf, p, argb, lineWidth, x0,y0,z1, x0,y0,z0);
                seg(buf, p, argb, lineWidth, x0,y1,z0, x1,y1,z0); seg(buf, p, argb, lineWidth, x1,y1,z0, x1,y1,z1);
                seg(buf, p, argb, lineWidth, x1,y1,z1, x0,y1,z1); seg(buf, p, argb, lineWidth, x0,y1,z1, x0,y1,z0);
                seg(buf, p, argb, lineWidth, x0,y0,z0, x0,y1,z0); seg(buf, p, argb, lineWidth, x1,y0,z0, x1,y1,z0);
                seg(buf, p, argb, lineWidth, x1,y0,z1, x1,y1,z1); seg(buf, p, argb, lineWidth, x0,y0,z1, x0,y1,z1);
            } catch (Exception e) { warnDrawFailure(e); }
        });
    }

    private void submitLine(SubmitNodeCollector collector, PoseStack pose, Vec3 from, Vec3 to, int argb, boolean throughWalls) {
        collector.submitCustomGeometry(pose, lineType(throughWalls), (p, buf) -> {
            try { seg(buf, p, argb, 2.0f, (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z); }
            catch (Exception e) { warnDrawFailure(e); }
        });
    }

    private void warnDrawFailure(Exception e) {
        if (warnedDrawFailure) return;
        warnedDrawFailure = true;
        ConstellationClient.LOGGER.warn("world primitive draw failed", e);
    }

    private static void quad(VertexConsumer buf, PoseStack.Pose p, int argb,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz) {
        buf.addVertex(p, ax,ay,az).setColor(argb);
        buf.addVertex(p, bx,by,bz).setColor(argb);
        buf.addVertex(p, cx,cy,cz).setColor(argb);
        buf.addVertex(p, dx,dy,dz).setColor(argb);
    }

    private static void seg(VertexConsumer buf, PoseStack.Pose p, int argb, float lineWidth,
                            float x0,float y0,float z0, float x1,float y1,float z1) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len < 1e-5f) return;
        nx /= len; ny /= len; nz /= len;
        buf.addVertex(p, x0,y0,z0).setColor(argb).setNormal(nx,ny,nz).setLineWidth(lineWidth);
        buf.addVertex(p, x1,y1,z1).setColor(argb).setNormal(nx,ny,nz).setLineWidth(lineWidth);
    }
}
