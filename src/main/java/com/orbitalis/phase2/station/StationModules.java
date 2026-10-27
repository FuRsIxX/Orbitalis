package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Construction des modules d'une station, contre une paroi existante.
 * <p>
 * Un module habitable a la section du module central : cinq blocs sur cinq, autour d'un couloir de trois sur trois.
 * Il part du mur visé (le mur commun, qu'il ne fait que compléter) et s'étend vers l'extérieur sur sa longueur.
 * Dehors, il est de coque blanche ; dedans, il est tapissé de panneaux capitonnés, sur un plancher antidérapant,
 * éclairé par ses plafonniers. Puis il est aménagé selon son rôle, et un passage de deux blocs de haut s'ouvre dans
 * le mur commun (le sas de sortie y pose une porte étanche).
 * <p>
 * Les positions s'y comptent en trois coordonnées locales : {@code f} le long du module (0 au mur commun),
 * {@code s} en travers (de -2 à 2, vers la droite quand on regarde vers l'extérieur) et {@code y} en hauteur (de -2
 * au plancher à 2 au plafond, le passage occupant -1 et 0).
 */
public final class StationModules {
    private StationModules() {
    }

    /** Vrai si ce bloc est une paroi de station sur laquelle un module peut s'appuyer. */
    public static boolean isWall(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.STATION_HULL.get()) || state.is(ModBlocks.STATION_WINDOW.get()) || state.is(ModBlocks.STATION_PANEL.get())
                || state.is(ModBlocks.STATION_LIGHT.get()) || state.is(ModBlocks.STATION_FLOOR.get()) || state.is(ModBlocks.CUPOLA_WINDOW.get());
    }

    /** Repère d'un module : son origine (au mur commun, au milieu du passage), sa direction et sa droite. */
    private record Frame(BlockPos origin, Direction out, Direction right) {
        BlockPos at(int f, int s, int y) {
            return origin.relative(out, f).relative(right, s).above(y);
        }

        /** Orientation tournée vers l'axe du couloir, pour un meuble posé du côté {@code s}. */
        Direction inward(int s) {
            return s < 0 ? right : right.getOpposite();
        }
    }

    /**
     * Accole un module à une station.
     *
     * @param wall bloc de paroi visé
     * @param face côté de ce bloc où le module se bâtit (vers l'extérieur)
     * @return ce qui empêche la construction, ou {@code null} si le module est en place
     */
    public static Component attach(ServerLevel level, BlockPos wall, Direction face, ModuleType type) {
        if (!isWall(level, wall)) {
            return Component.translatable("station.orbitalis.expand.not_hull");
        }
        if (face.getAxis().isVertical()) {
            return Component.translatable("station.orbitalis.expand.vertical");
        }
        // Le passage fait deux blocs de haut : le bloc visé et son voisin du dessous, ou du dessus s'il est au ras du sol.
        boolean lower = isWall(level, wall.below()) && isWall(level, wall.below(2));
        BlockPos doorBottom = lower ? wall.below() : wall;
        if (!isWall(level, doorBottom.above())) {
            return Component.translatable("station.orbitalis.expand.not_hull");
        }
        Frame frame = new Frame(doorBottom.above(), face, face.getClockWise());
        int length = type.length();
        List<BlockPos> footprint = type.pressurized() ? box(frame, length) : powerFootprint(frame);
        if (type == ModuleType.SHIPYARD) {
            footprint = new ArrayList<>(footprint);
            footprint.addAll(bay(frame));
        }
        for (BlockPos pos : footprint) {
            if (!level.getBlockState(pos).canBeReplaced()) {
                return Component.translatable("station.orbitalis.expand.no_room");
            }
        }
        if (type.pressurized()) {
            buildRoom(level, frame, type);
            if (type == ModuleType.SHIPYARD) {
                buildCradle(level, frame);
            }
        } else {
            buildPower(level, frame);
        }
        BlockPos station = StationManager.stationAt(level, frame.origin());
        if (station != null) {
            int extent = type.pressurized() ? 2 : 4;
            StationLayout.add(level.getServer(), station, type, frame.at(0, -extent, -2), frame.at(length - 1, extent, 2));
        }
        return null;
    }

    /** Toutes les cases d'un module habitable, hors du mur commun. */
    private static List<BlockPos> box(Frame frame, int length) {
        List<BlockPos> list = new ArrayList<>();
        for (int f = 1; f < length; f++) {
            for (int s = -2; s <= 2; s++) {
                for (int y = -2; y <= 2; y++) {
                    list.add(frame.at(f, s, y));
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Modules habitables
    // ------------------------------------------------------------------

    private static void buildRoom(ServerLevel level, Frame frame, ModuleType type) {
        int length = type.length();
        BlockState hull = ModBlocks.STATION_HULL.get().defaultBlockState();
        // Le mur commun : on comble ce qui y manque, sans toucher au reste.
        for (int s = -2; s <= 2; s++) {
            for (int y = -2; y <= 2; y++) {
                BlockPos pos = frame.at(0, s, y);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlockAndUpdate(pos, hull);
                }
            }
        }
        for (int f = 1; f < length; f++) {
            for (int s = -2; s <= 2; s++) {
                for (int y = -2; y <= 2; y++) {
                    BlockState state = special(type, frame, length, f, s, y);
                    if (state == null) {
                        state = shell(frame, length, f, s, y);
                    }
                    BlockPos pos = frame.at(f, s, y);
                    if (state == null) {
                        level.removeBlock(pos, false);
                    } else {
                        level.setBlockAndUpdate(pos, state);
                    }
                }
            }
        }
        if (type == ModuleType.AIRLOCK) {
            // Les deux portes du sas : l'une dans le mur commun, l'autre au fond, vers le vide. Verrouillées entre elles.
            airlockDoor(level, frame.at(0, 0, -1), frame.out());
            airlockDoor(level, frame.at(length - 1, 0, -1), frame.out());
        } else {
            level.removeBlock(frame.at(0, 0, -1), false);
            level.removeBlock(frame.at(0, 0, 0), false);
        }
    }

    /** Coque d'un module habitable, à cette position : {@code null} pour l'intérieur, qui reste vide. */
    private static BlockState shell(Frame frame, int length, int f, int s, int y) {
        int as = Math.abs(s);
        int ay = Math.abs(y);
        boolean far = f == length - 1;
        if ((as == 2 && ay == 2) || (far && (as == 2 || ay == 2))) {
            return ModBlocks.STATION_HULL.get().defaultBlockState();
        }
        if (far) {
            return panel(frame.out().getOpposite());
        }
        if (as == 2) {
            return panel(frame.inward(s));
        }
        if (y == -2) {
            return ModBlocks.STATION_FLOOR.get().defaultBlockState();
        }
        if (y == 2) {
            return panel(Direction.DOWN);
        }
        return null;
    }

    private static BlockState panel(Direction facing) {
        return ModBlocks.STATION_PANEL.get().defaultBlockState().setValue(DirectionalBlock.FACING, facing);
    }

    private static BlockState light(Direction facing) {
        return ModBlocks.STATION_LIGHT.get().defaultBlockState().setValue(DirectionalBlock.FACING, facing);
    }

    private static BlockState facing(Block block, Direction facing) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /**
     * Ce qui distingue chaque module : ses hublots, ses plafonniers et son aménagement. {@code null} : la coque
     * ordinaire (ou le vide, à l'intérieur).
     */
    private static BlockState special(ModuleType type, Frame frame, int length, int f, int s, int y) {
        int as = Math.abs(s);
        int ay = Math.abs(y);
        boolean far = f == length - 1;
        BlockState window = ModBlocks.STATION_WINDOW.get().defaultBlockState();
        BlockState cupola = ModBlocks.CUPOLA_WINDOW.get().defaultBlockState();
        switch (type) {
            case SHIPYARD -> {
                // La verrière, au fond, donne sur le berceau ; le pupitre lui fait face, le dos à la vitre.
                if (far && as <= 1 && ay <= 1) {
                    return cupola;
                }
                if (f == length - 2 && s == 0 && y == -1) {
                    return facing(ModBlocks.SHIPYARD_CONSOLE.get(), frame.out().getOpposite());
                }
                if (f == 1 && y == 2 && s == 0) {
                    return light(Direction.DOWN);
                }
                if (as == 2 && y == 0 && f == 2) {
                    return window;
                }
                return null;
            }
            case CUPOLA -> {
                // Le fond, le plafond et les flancs du bout du module : une verrière.
                if (far && as <= 1 && ay <= 1) {
                    return cupola;
                }
                if (f == length - 2 && ((y == 2 && as <= 1) || (as == 2 && ay <= 1))) {
                    return cupola;
                }
                if (f == 1 && y == 2 && s == 0) {
                    return light(Direction.DOWN);
                }
                return null;
            }
            case AIRLOCK -> {
                if (f == 1 && y == 2 && s == 0) {
                    return light(Direction.DOWN);
                }
                // Un casier pour ranger les combinaisons, près de la porte intérieure.
                if (f == 1 && s == -1 && y == -1) {
                    return facing(ModBlocks.CARGO_RACK.get(), frame.inward(s));
                }
                return null;
            }
            default -> {
            }
        }
        // Les autres modules : des hublots au milieu des flancs et du fond, des plafonniers dans l'axe.
        int middle = length / 2;
        if (as == 2 && y == 0 && f == middle) {
            return window;
        }
        if (far && s == 0 && y == 0) {
            return window;
        }
        if (y == 2 && s == 0 && f < length - 1 && (f - 1) % 2 == 0) {
            return light(Direction.DOWN);
        }
        boolean side = as == 1 && (y == -1 || y == 0) && !far;
        switch (type) {
            case HABITATION -> {
                if (side && f == 2) {
                    return facing(ModBlocks.CREW_BUNK.get(), frame.inward(s)).setValue(CrewBunkBlock.HALF,
                            y == -1 ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                                    : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                }
                if (side && f == 4) {
                    return facing(ModBlocks.CARGO_RACK.get(), frame.inward(s));
                }
                if (far && s == 0 && y == -1) {
                    return facing(ModBlocks.STATION_TERMINAL.get(), frame.out().getOpposite());
                }
            }
            case LABORATORY -> {
                if (side && (f == 2 || f == 4)) {
                    return facing(ModBlocks.LAB_RACK.get(), frame.inward(s));
                }
                if (far && s == 0 && y == -1) {
                    return facing(ModBlocks.STATION_TERMINAL.get(), frame.out().getOpposite());
                }
            }
            case GREENHOUSE -> {
                if (as == 1 && y == -1 && f >= 1 && f <= 4) {
                    return ModBlocks.HYDROPONIC_TRAY.get().defaultBlockState();
                }
                // Les rampes de culture, au plafond, au-dessus des bacs.
                if (as == 1 && y == 2 && f >= 1 && f <= 4) {
                    return light(Direction.DOWN);
                }
                if (f == 5 && s == -1 && y == -1) {
                    return facing(ModBlocks.CARGO_RACK.get(), frame.inward(s));
                }
            }
            case CARGO -> {
                if (side && (f == 2 || f == 4)) {
                    return facing(ModBlocks.CARGO_RACK.get(), frame.inward(s));
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** Pose une porte de sas, fermée, dans un passage de deux blocs de haut. */
    private static void airlockDoor(ServerLevel level, BlockPos bottom, Direction facing) {
        BlockState door = ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(AirlockDoorBlock.FACING, facing)
                .setValue(AirlockDoorBlock.PHASE, AirlockDoorBlock.Phase.CLOSED);
        level.setBlock(bottom, door.setValue(AirlockDoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(bottom.above(), door.setValue(AirlockDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------
    // Chantier naval
    // ------------------------------------------------------------------

    /** L'origine du vaisseau : le centre de sa poupe, un bloc derrière le premier cadre du berceau. */
    private static BlockPos shipOrigin(Frame frame) {
        return frame.at(ModuleType.SHIPYARD.length() - 1 - com.orbitalis.phase3.interstellar.ShipPlan.RIBS[0] + 1, 0, 0);
    }

    /** Toutes les cases du berceau, qui doivent être libres. */
    private static List<BlockPos> bay(Frame frame) {
        List<BlockPos> list = new ArrayList<>();
        BlockPos origin = shipOrigin(frame);
        for (BlockPos offset : com.orbitalis.phase3.interstellar.ShipPlan.bay(frame.out())) {
            list.add(origin.offset(offset));
        }
        return list;
    }

    /** Le berceau : ses longerons et ses cadres de poutre treillis, et un plafonnier au milieu de chaque traverse. */
    private static void buildCradle(ServerLevel level, Frame frame) {
        BlockPos origin = shipOrigin(frame);
        BlockState truss = ModBlocks.STATION_TRUSS.get().defaultBlockState();
        for (BlockPos offset : com.orbitalis.phase3.interstellar.ShipPlan.cradle(frame.out())) {
            level.setBlockAndUpdate(origin.offset(offset), truss);
        }
        int edge = com.orbitalis.phase3.interstellar.ShipPlan.CRADLE;
        for (int rib : com.orbitalis.phase3.interstellar.ShipPlan.RIBS) {
            level.setBlockAndUpdate(origin.offset(com.orbitalis.phase3.interstellar.ShipPlan.local(frame.out(), rib, 0, edge)), light(Direction.DOWN));
            level.setBlockAndUpdate(origin.offset(com.orbitalis.phase3.interstellar.ShipPlan.local(frame.out(), rib, 0, -edge)), light(Direction.UP));
        }
    }

    // ------------------------------------------------------------------
    // Poutre d'énergie
    // ------------------------------------------------------------------

    /** Longueur de la poutre d'énergie, et d'une aile de panneaux solaires, en blocs. */
    private static final int TRUSS = 5;
    private static final int WING = 4;

    private static List<BlockPos> powerFootprint(Frame frame) {
        List<BlockPos> list = new ArrayList<>();
        for (int f = 1; f <= TRUSS; f++) {
            list.add(frame.at(f, 0, 0));
        }
        list.add(frame.at(1, 0, 1));
        for (int k = 1; k <= WING; k++) {
            list.add(frame.at(TRUSS, k, 0));
            list.add(frame.at(TRUSS, -k, 0));
        }
        return list;
    }

    /**
     * La poutre d'énergie : une poutre treillis qui part du mur, une batterie à son pied, et au bout deux ailes de
     * panneaux solaires, en travers.
     */
    private static void buildPower(ServerLevel level, Frame frame) {
        for (int f = 1; f <= TRUSS; f++) {
            level.setBlockAndUpdate(frame.at(f, 0, 0), ModBlocks.STATION_TRUSS.get().defaultBlockState());
        }
        level.setBlockAndUpdate(frame.at(1, 0, 1), facing(ModBlocks.STATION_BATTERY.get(), frame.right()));
        BooleanProperty toRight = connection(frame.right());
        BooleanProperty toLeft = connection(frame.right().getOpposite());
        for (int k = 1; k <= WING; k++) {
            for (int sign : new int[] {1, -1}) {
                int s = k * sign;
                // Chaque élément se raccorde à ses voisins de la même aile ; l'aile ne touche pas la poutre.
                boolean right = sign > 0 ? k < WING : k > 1;
                boolean left = sign > 0 ? k > 1 : k < WING;
                level.setBlockAndUpdate(frame.at(TRUSS, s, 0), ModBlocks.SOLAR_PANEL.get().defaultBlockState()
                        .setValue(toRight, right).setValue(toLeft, left));
            }
        }
    }

    private static BooleanProperty connection(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST;
            default -> BlockStateProperties.WEST;
        };
    }
}
