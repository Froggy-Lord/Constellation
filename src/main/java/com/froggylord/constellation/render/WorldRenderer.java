package com.froggylord.constellation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
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
            boxes.add(new BoxPrim(box, colour, throughWalls, true));
        }

        public void outline(AABB box, int colour, boolean throughWalls) {
            boxes.add(new BoxPrim(box, colour, throughWalls, false));
        }

        public void highlight(AABB box, int colour, boolean throughWalls) {
            int a = colour >>> 24;
            int fill = (Math.min(a, 0x60) << 24) | (colour & 0xFFFFFF);
            boxes.add(new BoxPrim(box, fill, throughWalls, true));
            boxes.add(new BoxPrim(box, 0xFF000000 | (colour & 0xFFFFFF), throughWalls, false));
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

    public record BoxPrim(AABB box, int colour, boolean throughWalls, boolean filled) {}
    public record LinePrim(Vec3 from, Vec3 to, int colour, boolean throughWalls) {}
    public record LabelPrim(Vec3 pos, String text, int colour, boolean throughWalls) {}

    private final List<Consumer<Ctx>> featureRenderers = new ArrayList<>();
    private boolean inited = false;

    public void register(Consumer<Ctx> renderer) {
        featureRenderers.add(renderer);
    }

    public void init() {
        if (inited) return;
        inited = true;
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(this::onRender);
    }

    private void onRender(LevelRenderContext context) {
        if (featureRenderers.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        var camState = context.levelState().cameraRenderState;
        Vec3 cam = camState.pos;
        Ctx ctx = new Ctx(cam);
        for (Consumer<Ctx> r : featureRenderers) {
            try { r.accept(ctx); }
            catch (Exception ignored) {  }
        }
        if (ctx.boxes.isEmpty() && ctx.lines.isEmpty() && ctx.labels.isEmpty()) return;

        PoseStack pose = context.poseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        SubmitNodeCollector collector = context.submitNodeCollector();

        
        for (boolean tw : new boolean[]{false, true}) {
            for (BoxPrim b : ctx.boxes) {
                if (b.throughWalls() != tw) continue;
                if (b.filled()) submitFilled(collector, pose, b.box(), b.colour());
                else submitBoxLines(collector, pose, b.box(), b.colour());
            }
            for (LinePrim l : ctx.lines) {
                if (l.throughWalls() != tw) continue;
                submitLine(collector, pose, l.from(), l.to(), l.colour());
            }
        }

        
        for (LabelPrim l : ctx.labels) {
            Component c = Component.literal(l.text());
            int bg = ((l.colour() >>> 24) << 24); 
            collector.submitNameTag(pose, l.pos(), LightCoordsUtil.FULL_BRIGHT, c, l.throughWalls(), bg, camState);
        }

        pose.popPose();
    }

    // no-depth (through-walls) rende...
    
    
    private static RenderType filledType() { return RenderTypes.debugFilledBox(); }
    private static RenderType lineType() { return RenderTypes.lines(); }

    private void submitFilled(SubmitNodeCollector collector, PoseStack pose, AABB b, int argb) {
        collector.submitCustomGeometry(pose, filledType(), (p, buf) -> {
            float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
            float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
            quad(buf, p, argb, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0); 
            quad(buf, p, argb, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1); 
            quad(buf, p, argb, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0); 
            quad(buf, p, argb, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1); // east
            quad(buf, p, argb, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0); 
            quad(buf, p, argb, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1); 
        });
    }

    private void submitBoxLines(SubmitNodeCollector collector, PoseStack pose, AABB b, int argb) {
        collector.submitCustomGeometry(pose, lineType(), (p, buf) -> {
            float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
            float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
            seg(buf, p, argb, x0,y0,z0, x1,y0,z0); seg(buf, p, argb, x1,y0,z0, x1,y0,z1);
            seg(buf, p, argb, x1,y0,z1, x0,y0,z1); seg(buf, p, argb, x0,y0,z1, x0,y0,z0);
            seg(buf, p, argb, x0,y1,z0, x1,y1,z0); seg(buf, p, argb, x1,y1,z0, x1,y1,z1);
            seg(buf, p, argb, x1,y1,z1, x0,y1,z1); seg(buf, p, argb, x0,y1,z1, x0,y1,z0);
            seg(buf, p, argb, x0,y0,z0, x0,y1,z0); seg(buf, p, argb, x1,y0,z0, x1,y1,z0);
            seg(buf, p, argb, x1,y0,z1, x1,y1,z1); seg(buf, p, argb, x0,y0,z1, x0,y1,z1);
        });
    }

    private void submitLine(SubmitNodeCollector collector, PoseStack pose, Vec3 from, Vec3 to, int argb) {
        collector.submitCustomGeometry(pose, lineType(), (p, buf) ->
            seg(buf, p, argb, (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z));
    }

    private static void quad(VertexConsumer buf, PoseStack.Pose p, int argb,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz) {
        buf.addVertex(p, ax,ay,az).setColor(argb);
        buf.addVertex(p, bx,by,bz).setColor(argb);
        buf.addVertex(p, cx,cy,cz).setColor(argb);
        buf.addVertex(p, dx,dy,dz).setColor(argb);
    }

    private static void seg(VertexConsumer buf, PoseStack.Pose p, int argb,
                            float x0,float y0,float z0, float x1,float y1,float z1) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len < 1e-5f) return;
        nx /= len; ny /= len; nz /= len;
        buf.addVertex(p, x0,y0,z0).setColor(argb).setNormal(nx,ny,nz).setLineWidth(2.0f);
        buf.addVertex(p, x1,y1,z1).setColor(argb).setNormal(nx,ny,nz).setLineWidth(2.0f);
    }
}
