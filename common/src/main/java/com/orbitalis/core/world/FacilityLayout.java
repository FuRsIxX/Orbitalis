package com.orbitalis.core.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * Le plan de masse d'un centre : où vont ses bâtiments autour de l'esplanade, et de quel côté ils regardent. Le plan
 * se dessine dans le repère du site (u vers l'est, v vers le sud, l'esplanade à l'origine, l'entrée au sud), puis
 * tourne d'un quart de tour au hasard, et parfois se retourne comme dans un miroir.
 */
final class FacilityLayout {
    /** Marge entre les bâtiments et l'enceinte. */
    private static final int MARGIN = 4;

    /**
     * Un bâtiment du plan.
     *
     * @param u      position de son centre, vers l'est
     * @param v      position de son centre, vers le sud
     * @param facing côté de sa façade
     */
    record Placement(FacilityBuilding building, int u, int v, Direction facing) {
        Placement mirrored() {
            return new Placement(building, -u, v, facing.getAxis() == Direction.Axis.X ? facing.getOpposite() : facing);
        }
    }

    private FacilityLayout() {
    }

    static List<Placement> plan(FacilityKind kind, boolean large, RandomSource random) {
        List<Placement> plan = new ArrayList<>();
        switch (kind) {
            case WEATHER_STATION -> {
                plan.add(new Placement(FacilityBuilding.WEATHER_HUT, 0, -7, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.MET_MAST, 9, -6, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.WEATHER_GARDEN, -9, 3, Direction.EAST));
                if (large) {
                    plan.add(new Placement(FacilityBuilding.RADAR_TOWER, 11, 6, Direction.WEST));
                    plan.add(new Placement(FacilityBuilding.BALLOON_SHED, -11, -8, Direction.EAST));
                }
            }
            case RESEARCH_CENTER -> {
                plan.add(new Placement(FacilityBuilding.RESEARCH_LAB, 0, -8, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.STORAGE_SHED, 12, 1, Direction.WEST));
                if (large) {
                    plan.add(new Placement(FacilityBuilding.TEST_STAND, -15, 6, Direction.EAST));
                    plan.add(new Placement(FacilityBuilding.HANGAR, 14, 14, Direction.WEST));
                }
            }
            case OBSERVATION_CENTER -> {
                plan.add(new Placement(FacilityBuilding.OBSERVATORY, -8, -7, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.CONTROL_HUT, 6, -7, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.SMALL_DISH, 0, 8, Direction.NORTH));
                if (large) {
                    plan.add(new Placement(FacilityBuilding.RADIO_DISH, 17, 7, Direction.WEST));
                }
            }
            case CONTROL_CENTER -> {
                plan.add(new Placement(FacilityBuilding.LAUNCH_CONTROL, 0, -10, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.ASSEMBLY_BUILDING, -24, -7, Direction.EAST));
                plan.add(new Placement(FacilityBuilding.WATER_TOWER, 20, -9, Direction.SOUTH));
                plan.add(new Placement(FacilityBuilding.LAUNCH_PAD, 0, 30, Direction.NORTH));
            }
        }
        addExtras(plan, kind, large, random);
        if (random.nextBoolean()) {
            plan.replaceAll(Placement::mirrored);
        }
        return plan;
    }

    /** Les annexes possibles de chaque sorte de centre. */
    private static FacilityBuilding[] extras(FacilityKind kind) {
        return switch (kind) {
            case WEATHER_STATION -> new FacilityBuilding[] {FacilityBuilding.SOLAR_FARM, FacilityBuilding.COMMS_MAST, FacilityBuilding.STORAGE_YARD,
                    FacilityBuilding.GUARD_HOUSE, FacilityBuilding.OFFICE_ANNEX};
            case RESEARCH_CENTER -> new FacilityBuilding[] {FacilityBuilding.FUEL_FARM, FacilityBuilding.STORAGE_YARD, FacilityBuilding.OFFICE_ANNEX,
                    FacilityBuilding.COMMS_MAST, FacilityBuilding.HELIPAD, FacilityBuilding.SOLAR_FARM};
            case OBSERVATION_CENTER -> new FacilityBuilding[] {FacilityBuilding.DISH_ARRAY, FacilityBuilding.COMMS_MAST, FacilityBuilding.SOLAR_FARM,
                    FacilityBuilding.OFFICE_ANNEX, FacilityBuilding.GUARD_HOUSE};
            case CONTROL_CENTER -> new FacilityBuilding[] {FacilityBuilding.FUEL_FARM, FacilityBuilding.HELIPAD, FacilityBuilding.COMMS_MAST,
                    FacilityBuilding.OFFICE_ANNEX, FacilityBuilding.DISH_ARRAY, FacilityBuilding.GUARD_HOUSE};
        };
    }

    /**
     * Ajoute au plan quelques annexes tirées au hasard, chacune une seule fois, là où il reste de la place autour de
     * l'esplanade, leur façade tournée vers elle.
     */
    private static void addExtras(List<Placement> plan, FacilityKind kind, boolean large, RandomSource random) {
        List<FacilityBuilding> pool = new ArrayList<>(List.of(extras(kind)));
        int wanted = kind == FacilityKind.CONTROL_CENTER ? 3 : large ? 2 + random.nextInt(2) : 1 + random.nextInt(2);
        // Le centre de contrôle garde libre la piste vers le pas de tir.
        List<int[]> taken = new ArrayList<>();
        for (Placement placement : plan) {
            taken.add(siteRect(placement));
        }
        if (kind == FacilityKind.CONTROL_CENTER) {
            taken.add(new int[] {-6, -2, 6, 20});
        }
        taken.add(new int[] {-4, -4, 4, 4});
        int minRadius = kind == FacilityKind.CONTROL_CENTER ? 22 : 15;
        for (int n = 0; n < wanted && !pool.isEmpty(); n++) {
            FacilityBuilding building = pool.remove(random.nextInt(pool.size()));
            double start = random.nextDouble() * Math.PI * 2.0;
            search:
            for (int radius = minRadius; radius <= minRadius + 18; radius += 3) {
                for (int k = 0; k < 12; k++) {
                    double angle = start + k * Math.PI / 6.0;
                    int u = (int) Math.round(Math.cos(angle) * radius);
                    int v = (int) Math.round(Math.sin(angle) * radius);
                    Direction facing = Math.abs(u) > Math.abs(v) ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH);
                    Placement candidate = new Placement(building, u, v, facing);
                    int[] rect = siteRect(candidate);
                    boolean free = true;
                    for (int[] other : taken) {
                        if (rect[0] - 3 <= other[2] && rect[2] + 3 >= other[0] && rect[1] - 3 <= other[3] && rect[3] + 3 >= other[1]) {
                            free = false;
                            break;
                        }
                    }
                    if (free) {
                        plan.add(candidate);
                        taken.add(rect);
                        break search;
                    }
                }
            }
        }
    }

    /** L'emprise d'un bâtiment dans le repère du site : u et v minimaux, puis maximaux. */
    private static int[] siteRect(Placement placement) {
        boolean alongU = placement.facing().getAxis() == Direction.Axis.Z;
        int su = alongU ? placement.building().width() : placement.building().depth();
        int sv = alongU ? placement.building().depth() : placement.building().width();
        int minU = placement.u() - su / 2;
        int minV = placement.v() - sv / 2;
        return new int[] {minU, minV, minU + su - 1, minV + sv - 1};
    }

    static int[] rotate(int u, int v, int turns) {
        for (int k = 0; k < turns; k++) {
            int t = u;
            u = -v;
            v = t;
        }
        return new int[] {u, v};
    }

    static Direction rotate(Direction direction, int turns) {
        for (int k = 0; k < turns; k++) {
            direction = direction.getClockWise();
        }
        return direction;
    }

    /** L'emprise d'un bâtiment dans le monde : x et z minimaux, puis maximaux. */
    static int[] footprint(Placement placement, int cx, int cz, int turns) {
        Direction facing = rotate(placement.facing(), turns);
        int[] c = rotate(placement.u(), placement.v(), turns);
        boolean alongX = facing.getAxis() == Direction.Axis.Z;
        int sx = alongX ? placement.building().width() : placement.building().depth();
        int sz = alongX ? placement.building().depth() : placement.building().width();
        int minX = cx + c[0] - sx / 2;
        int minZ = cz + c[1] - sz / 2;
        return new int[] {minX, minZ, minX + sx - 1, minZ + sz - 1};
    }

    /** Où l'on mesure le sol : au centre et aux quatre coins de chaque bâtiment. */
    static List<int[]> samplePoints(List<Placement> plan, int cx, int cz, int turns) {
        List<int[]> points = new ArrayList<>();
        points.add(new int[] {cx, cz});
        for (Placement placement : plan) {
            int[] f = footprint(placement, cx, cz, turns);
            points.add(new int[] {(f[0] + f[2]) / 2, (f[1] + f[3]) / 2});
            points.add(new int[] {f[0], f[1]});
            points.add(new int[] {f[2], f[1]});
            points.add(new int[] {f[0], f[3]});
            points.add(new int[] {f[2], f[3]});
        }
        return points;
    }

    /**
     * Pose le centre : d'abord le terrain commun (esplanade, allées, éclairage, enceinte et son entrée), puis chaque
     * bâtiment, sa façade tournée comme le plan le veut.
     */
    static void build(StructurePiecesBuilder builder, FacilityKind kind, List<Placement> plan, int cx, int ground, int cz, int turns,
            boolean abandoned, long seed) {
        List<int[]> rects = new ArrayList<>();
        List<int[]> paths = new ArrayList<>();
        int minX = cx - 4;
        int minZ = cz - 4;
        int maxX = cx + 4;
        int maxZ = cz + 4;
        for (Placement placement : plan) {
            int[] f = footprint(placement, cx, cz, turns);
            rects.add(f);
            minX = Math.min(minX, f[0]);
            minZ = Math.min(minZ, f[1]);
            maxX = Math.max(maxX, f[2]);
            maxZ = Math.max(maxZ, f[3]);
            // L'allée part de l'esplanade jusqu'au parvis : d'abord d'est en ouest, puis du nord au sud.
            Direction facing = rotate(placement.facing(), turns);
            int reach = (facing.getAxis() == Direction.Axis.Z ? f[3] - f[1] : f[2] - f[0]) / 2 + 2;
            int fx = (f[0] + f[2]) / 2 + facing.getStepX() * reach;
            int fz = (f[1] + f[3]) / 2 + facing.getStepZ() * reach;
            int width = placement.building() == FacilityBuilding.LAUNCH_PAD ? 7 : 3;
            paths.add(new int[] {cx, cz, fx, cz, width});
            paths.add(new int[] {fx, cz, fx, fz, width});
        }
        minX -= MARGIN;
        minZ -= MARGIN;
        maxX += MARGIN;
        maxZ += MARGIN;
        // L'entrée de l'enceinte, en face de l'esplanade ; celle du centre de contrôle sur le côté, le pas de tir étant devant.
        Direction gate = rotate(kind == FacilityKind.CONTROL_CENTER ? Direction.EAST : Direction.SOUTH, turns);
        int gateX = gate.getAxis() == Direction.Axis.X ? (gate == Direction.EAST ? maxX : minX) : cx;
        int gateZ = gate.getAxis() == Direction.Axis.Z ? (gate == Direction.SOUTH ? maxZ : minZ) : cz;
        paths.add(new int[] {cx, cz, gateX, gateZ, 3});
        BoundingBox site = new BoundingBox(minX, ground, minZ, maxX, ground + 4, maxZ);
        int style = Math.floorMod((int) (seed >>> 17), FacilityStyle.COUNT);
        builder.addPiece(new FacilityGroundPiece(kind, site, paths, rects, gateX, gateZ, gate, abandoned, seed));
        for (int k = 0; k < plan.size(); k++) {
            Placement placement = plan.get(k);
            int[] f = rects.get(k);
            Direction facing = rotate(placement.facing(), turns);
            builder.addPiece(new FacilityPiece(placement.building(), kind, f[0], ground, f[1], facing.getOpposite(), abandoned,
                    seed + (k + 1) * 0x9E3779B97F4A7C15L, style));
        }
    }
}
