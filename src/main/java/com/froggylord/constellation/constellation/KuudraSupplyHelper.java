package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.DracoConfig;
import com.froggylord.constellation.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class KuudraSupplyHelper {
    // ported from Athen (BSD-3-Clause): api/kuudra/enums/KuudraSupply.kt
    private enum Spot {
        SHOP("Shop", new BlockPos(-81, 76, -143), new BlockPos(-98, 78, -113)),
        EQUALS("Equals", new BlockPos(-65, 76, -87), new BlockPos(-99, 78, -100)),
        CANNON("Cannon", new BlockPos(-143, 76, -125), new BlockPos(-110, 78, -107)),
        X("X", new BlockPos(-142, 77, -151), new BlockPos(-106, 78, -113)),
        TRIANGLE("Triangle", new BlockPos(-67, 77, -122), new BlockPos(-94, 78, -106)),
        SLASH("Slash", new BlockPos(-113, 77, -68), new BlockPos(-107, 78, -100));

        final String label;
        final BlockPos pickup;
        final BlockPos dropOff;
        boolean complete;

        Spot(String label, BlockPos pickup, BlockPos dropOff) {
            this.label = label;
            this.pickup = pickup;
            this.dropOff = dropOff;
        }
    }

    private record Pickup(AABB box, Vec3 center, boolean nearby) {}
    private static List<Pickup> pickups = List.of();

    private KuudraSupplyHelper() {}

    // ported from Odin (BSD-3-Clause): features/impl/nether/SupplyHelper.kt
    // and utils/skyblock/KuudraUtils.kt
    public static void tick() {
        if (!KuudraState.inRun() || (KuudraState.phase() != KuudraState.Phase.SUPPLY
            && KuudraState.phase() != KuudraState.Phase.FUEL)) {
            pickups = List.of();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<Pickup> found = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Giant giant && giant.isAlive()
                && giant.getMainHandItem().getHoverName().getString().endsWith("Head")) {
                double radians = Math.toRadians(giant.getYRot() + 130.0);
                double x = giant.getX() + 3.7 * Math.cos(radians);
                double z = giant.getZ() + 3.7 * Math.sin(radians);
                AABB box = new AABB(x - 1.0, 74.5, z, x + 2.0, 76.5, z + 3.0);
                boolean nearby = mc.level.players().stream().anyMatch(player -> box.inflate(4.0, 5.0, 4.0).contains(player.position()));
                found.add(new Pickup(box, box.getCenter(), nearby));
            }
            if (KuudraState.phase() == KuudraState.Phase.SUPPLY && entity instanceof ArmorStand stand
                && stand.getName().getString().equals("\u2713 SUPPLIES RECEIVED \u2713")) {
                BlockPos pos = stand.blockPosition();
                for (Spot spot : Spot.values()) {
                    double dx = pos.getX() - spot.dropOff.getX();
                    double dz = pos.getZ() - spot.dropOff.getZ();
                    if (dx * dx + dz * dz <= 4.0) spot.complete = true;
                }
            }
        }
        pickups = List.copyOf(found);
    }

    // ported from Athen (BSD-3-Clause): modules/impl/kuudra/SupplyWaypoints.kt
    public static void draw(WorldRenderer.Ctx ctx) {
        DracoConfig cfg = ConstellationClient.cfg().draco;
        if (!cfg.kuudraSupplyHelper || !KuudraState.inRun()) return;
        KuudraState.Phase phase = KuudraState.phase();
        if (phase != KuudraState.Phase.SUPPLY && phase != KuudraState.Phase.FUEL) return;

        boolean walls = cfg.kuudraSupplyThroughWalls;
        if (phase == KuudraState.Phase.SUPPLY && cfg.kuudraSupplyDropOffWaypoints) {
            for (Spot spot : Spot.values()) {
                if (spot.complete) continue;
                int colour = cfg.kuudraSupplyDropOffColour;
                AABB box = new AABB(spot.dropOff);
                if (cfg.kuudraSupplyBoxes) ctx.highlight(box, colour, walls);
                if (cfg.kuudraSupplyBeams) ctx.beam(spot.dropOff.getX() + .5, spot.dropOff.getY() + 1,
                    spot.dropOff.getZ() + .5, colour, 10, walls);
                if (cfg.kuudraSupplyLabels) ctx.label(Vec3.atCenterOf(spot.dropOff).add(0, 1.2, 0),
                    KuudraState.carrying() ? "Place Here: " + spot.label : spot.label + " Drop-off", colour, walls);
            }
        }

        boolean showPickups = phase == KuudraState.Phase.SUPPLY && cfg.kuudraSupplyPickupWaypoints
            || phase == KuudraState.Phase.FUEL && cfg.kuudraFuelWaypoints;
        if (showPickups) {
            for (Pickup pickup : pickups) {
                int colour = phase == KuudraState.Phase.FUEL ? cfg.kuudraSupplyFuelColour
                    : cfg.kuudraSupplyDetectNearbyPlayers && pickup.nearby()
                        ? cfg.kuudraSupplyNearbyColour : cfg.kuudraSupplyPickupColour;
                if (cfg.kuudraSupplyBoxes) ctx.outline(pickup.box(), colour, walls);
                if (cfg.kuudraSupplyBeams) ctx.beam(pickup.center().x, pickup.box().maxY,
                    pickup.center().z, colour, 10, walls);
                if (cfg.kuudraSupplyLabels) ctx.label(pickup.center().add(0, 1.8, 0),
                    phase == KuudraState.Phase.FUEL ? "Fuel" : pickup.nearby() ? "Supply: player nearby" : "Supply",
                    colour, walls);
            }
        }

        // ported from Odin (BSD-3-Clause): features/impl/nether/SupplyHelper.kt renderArea
        if (phase == KuudraState.Phase.SUPPLY && cfg.kuudraSupplyPickupAreas && cfg.kuudraSupplyLabels) {
            for (Spot spot : Spot.values()) {
                ctx.label(Vec3.atCenterOf(spot.pickup).add(0, 1.2, 0), spot.label + " Pickup",
                    cfg.kuudraSupplyPickupColour, walls);
            }
        }
    }

    public static String phaseHudText() {
        if (!KuudraState.inRun()) return null;
        String phase = switch (KuudraState.phase()) {
            case SUPPLY -> "Supplies";
            case BUILD -> "Build";
            case FUEL -> "Fuel";
            case STUN -> "Stun";
            case DPS -> "DPS";
            case SKIP -> "Skip";
            case KILL -> "Kill";
            default -> null;
        };
        if (phase == null) return null;
        String tier = KuudraState.tier() > 0 ? " T" + KuudraState.tier() : "";
        return phase + tier + " " + KuudraState.formatSeconds(KuudraState.phaseElapsedMillis());
    }

    public static String supplyHudText() {
        if (!KuudraState.inRun() || KuudraState.phase() != KuudraState.Phase.SUPPLY) return null;
        return KuudraState.recovered() + "/" + KuudraState.total()
            + (KuudraState.carrying() ? " Carrying" : " Supplies");
    }

    public static void reset() {
        pickups = List.of();
        for (Spot spot : Spot.values()) spot.complete = false;
    }
}
