package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Construction des modules d'une station, sur ses ports de jonction.
 * <p>
 * Un module ne s'accroche que sur un port libre (voir {@link StationPortBlock}), dans son axe : les couloirs sont
 * toujours droits et centrés. Les embranchements se font par les nœuds, qui ont un port au fond et un sur chaque flanc ;
 * la pièce nue a un port au fond, pour prolonger la station en ligne droite. Les autres modules sont des bouts.
 * <p>
 * Un module habitable a la section du module central : cinq blocs sur cinq, autour d'un couloir de trois sur trois.
 * Il part du mur du port (le mur commun, qu'il ne fait que compléter) et s'étend vers l'extérieur sur sa longueur.
 * Dehors, il est de coque blanche ; dedans, il est tapissé de panneaux capitonnés, sur un plancher antidérapant,
 * éclairé par ses plafonniers. Puis il est aménagé selon son rôle, et le port s'ouvre en passage de deux blocs de haut
 * (le sas de sortie y pose une porte étanche).
 * <p>
 * Les positions s'y comptent en trois coordonnées locales : {@code f} le long du module (0 au mur commun),
 * {@code s} en travers (de -2 à 2, vers la droite quand on regarde vers l'extérieur) et {@code y} en hauteur (de -2
 * au plancher à 2 au plafond, le passage occupant -1 et 0).
 * <p>
 * La poutre d'énergie, qui reste dehors, s'accroche encore sur n'importe quel mur extérieur.
 */
public final class StationModules {
    private StationModules() {
    }

    /** Vrai si ce bloc est une paroi de station sur laquelle un module peut s'appuyer. */
    public static boolean isWall(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.STATION_HULL.get()) || state.is(ModBlocks.STATION_WINDOW.get()) || state.is(ModBlocks.STATION_PANEL.get())
                || state.is(ModBlocks.STATION_LIGHT.get()) || state.is(ModBlocks.STATION_FLOOR.get()) || state.is(ModBlocks.CUPOLA_WINDOW.get())
                || state.is(ModBlocks.STATION_PORT.get());
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

        /** Orientation tournée vers le dehors, pour un port percé dans le flanc du côté {@code s}. */
        Direction outward(int s) {
            return s > 0 ? right : right.getOpposite();
        }

        /** Le repère du module qu'on accrocherait sur ce port. */
        static Frame ofPort(BlockPos port, BlockState state) {
            Direction out = state.getValue(StationPortBlock.FACING);
            return new Frame(StationPortBlock.upper(state, port), out, out.getClockWise());
        }
    }

    /** Un bloc du plan d'un module : sa place, et son état ({@code null} pour une case vide, à l'intérieur). */
    public record Placed(BlockPos pos, @Nullable BlockState state) {
    }

    /** Vrai si ce bloc est un port de jonction. */
    public static boolean isPort(BlockState state) {
        return state.is(ModBlocks.STATION_PORT.get());
    }

    /**
     * Le plan d'un module accroché sur ce port, hors du mur commun : chaque case, avec ce qui s'y posera. Sert à la
     * construction et au fantôme qu'on voit, le kit en main.
     */
    public static List<Placed> plan(BlockPos port, BlockState portState, ModuleType type) {
        Frame frame = Frame.ofPort(port, portState);
        List<Placed> list = new ArrayList<>();
        int length = type.length();
        for (int f = 1; f < length; f++) {
            for (int s = -2; s <= 2; s++) {
                for (int y = -2; y <= 2; y++) {
                    BlockState state = special(type, frame, length, f, s, y);
                    if (state == null) {
                        state = shell(frame, length, f, s, y);
                    }
                    list.add(new Placed(frame.at(f, s, y), state));
                }
            }
        }
        if (type == ModuleType.SHIPYARD) {
            BlockPos origin = shipOrigin(frame);
            BlockState truss = ModBlocks.STATION_TRUSS.get().defaultBlockState();
            for (BlockPos offset : com.orbitalis.phase3.interstellar.ShipPlan.cradle(frame.out())) {
                list.add(new Placed(origin.offset(offset), truss));
            }
            int edge = com.orbitalis.phase3.interstellar.ShipPlan.CRADLE;
            for (int rib : com.orbitalis.phase3.interstellar.ShipPlan.RIBS) {
                list.add(new Placed(origin.offset(com.orbitalis.phase3.interstellar.ShipPlan.local(frame.out(), rib, 0, edge)), light(Direction.DOWN)));
                list.add(new Placed(origin.offset(com.orbitalis.phase3.interstellar.ShipPlan.local(frame.out(), rib, 0, -edge)), light(Direction.UP)));
            }
        }
        return list;
    }

    /** Nombre de cases du module lui-même en tête de son {@link #plan} ; viennent ensuite celles du berceau d'un chantier. */
    public static int moduleCells(ModuleType type) {
        return (type.length() - 1) * 25;
    }

    /** Ce qui empêche d'accrocher ce module sur ce port, ou {@code null} s'il y a la place. */
    public static @Nullable Component blocked(BlockGetter level, BlockPos port, ModuleType type) {
        BlockState state = level.getBlockState(port);
        if (!isPort(state)) {
            return Component.translatable("station.orbitalis.expand.need_port");
        }
        Frame frame = Frame.ofPort(port, state);
        for (BlockPos pos : footprint(frame, type)) {
            if (!level.getBlockState(pos).canBeReplaced()) {
                return Component.translatable("station.orbitalis.expand.no_room");
            }
        }
        return null;
    }

    /**
     * Accroche un module habitable sur un port de jonction.
     *
     * @param port l'une des deux moitiés du port
     * @return ce qui empêche la construction, ou {@code null} si le module est en place
     */
    public static Component attach(ServerLevel level, BlockPos port, ModuleType type) {
        if (!type.pressurized()) {
            return Component.translatable("station.orbitalis.expand.truss_wall");
        }
        Component problem = blocked(level, port, type);
        if (problem != null) {
            return problem;
        }
        BlockState portState = level.getBlockState(port);
        Frame frame = Frame.ofPort(port, portState);
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
        for (Placed placed : plan(port, portState, type)) {
            if (placed.state() == null) {
                level.removeBlock(placed.pos(), false);
            } else if (placed.state().hasProperty(StationPortBlock.HALF)) {
                // Les deux moitiés d'un port se posent sans se prévenir : l'une ne doit pas effacer l'autre en attendant.
                level.setBlock(placed.pos(), placed.state(), Block.UPDATE_CLIENTS);
            } else {
                level.setBlockAndUpdate(placed.pos(), placed.state());
            }
        }
        // Le port s'ouvre : un passage, ou les deux portes du sas, l'une ici, l'autre au fond vers le vide. Ses deux moitiés
        // s'effacent sans prévenir leurs voisines : l'une, privée de l'autre, tomberait sinon en objet.
        BlockPos lower = frame.at(0, 0, -1);
        BlockPos upper = frame.at(0, 0, 0);
        level.setBlock(upper, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(lower, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (type == ModuleType.AIRLOCK) {
            airlockDoor(level, lower, frame.out());
            airlockDoor(level, frame.at(type.length() - 1, 0, -1), frame.out());
        }
        BlockPos station = StationManager.stationAt(level, frame.origin());
        if (station != null) {
            StationLayout.add(level.getServer(), station, type, frame.at(0, -2, -2), frame.at(type.length() - 1, 2, 2));
        }
        return null;
    }

    /** Toutes les cases qui doivent être libres pour ce module : son volume hors du mur commun, et le berceau d'un chantier. */
    private static List<BlockPos> footprint(Frame frame, ModuleType type) {
        List<BlockPos> list = new ArrayList<>();
        for (int f = 1; f < type.length(); f++) {
            for (int s = -2; s <= 2; s++) {
                for (int y = -2; y <= 2; y++) {
                    list.add(frame.at(f, s, y));
                }
            }
        }
        if (type == ModuleType.SHIPYARD) {
            BlockPos origin = shipOrigin(frame);
            for (BlockPos offset : com.orbitalis.phase3.interstellar.ShipPlan.bay(frame.out())) {
                list.add(origin.offset(offset));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Modules habitables
    // ------------------------------------------------------------------

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

    /** Une moitié de port de jonction, si cette case du passage en fait partie ({@code y} vaut -1 ou 0). */
    private static @Nullable BlockState port(Direction out, int y) {
        if (y == -1) {
            return StationPortBlock.state(out, DoubleBlockHalf.LOWER);
        }
        return y == 0 ? StationPortBlock.state(out, DoubleBlockHalf.UPPER) : null;
    }

    /**
     * Ce qui distingue chaque module : ses ports, ses hublots, ses plafonniers et son aménagement. {@code null} : la
     * coque ordinaire (ou le vide, à l'intérieur).
     */
    private static BlockState special(ModuleType type, Frame frame, int length, int f, int s, int y) {
        int as = Math.abs(s);
        int ay = Math.abs(y);
        boolean far = f == length - 1;
        BlockState window = ModBlocks.STATION_WINDOW.get().defaultBlockState();
        BlockState cupola = ModBlocks.CUPOLA_WINDOW.get().defaultBlockState();
        switch (type) {
            case NODE -> {
                // Un carrefour : un port au fond et un au milieu de chaque flanc, des hublots de part et d'autre.
                int middle = length / 2;
                if (far && s == 0 && ay <= 1 && y <= 0) {
                    return port(frame.out(), y);
                }
                if (as == 2 && f == middle && y <= 0 && y >= -1) {
                    return port(frame.outward(s), y);
                }
                if (y == 0 && ((far && as == 1) || (as == 2 && Math.abs(f - middle) == 1))) {
                    return window;
                }
                if (y == 2 && s == 0 && Math.abs(f - middle) == 1) {
                    return light(Direction.DOWN);
                }
                return null;
            }
            case ROOM -> {
                // Une pièce nue qui prolonge la station : un port au fond.
                if (far && s == 0 && (y == -1 || y == 0)) {
                    return port(frame.out(), y);
                }
                if (far && as == 1 && y == 0) {
                    return window;
                }
            }
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
                // La porte du fond, vers le vide, est posée à part : sa place reste vide dans le plan.
                if (far && s == 0 && (y == -1 || y == 0)) {
                    return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
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
                            y == -1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
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

    // ------------------------------------------------------------------
    // Poutre d'énergie
    // ------------------------------------------------------------------

    /** Longueur de la poutre d'énergie, et d'une aile de panneaux solaires, en blocs. */
    private static final int TRUSS = 5;
    private static final int WING = 4;

    /**
     * Plante la poutre d'énergie sur un mur extérieur de la station.
     *
     * @param wall bloc de paroi visé
     * @param face côté de ce bloc où la poutre se bâtit (vers l'extérieur)
     * @return ce qui empêche la construction, ou {@code null} si la poutre est en place
     */
    public static Component attachTruss(ServerLevel level, BlockPos wall, Direction face) {
        if (!isWall(level, wall)) {
            return Component.translatable("station.orbitalis.expand.not_hull");
        }
        if (face.getAxis().isVertical()) {
            return Component.translatable("station.orbitalis.expand.vertical");
        }
        Frame frame = new Frame(wall, face, face.getClockWise());
        for (BlockPos pos : powerFootprint(frame)) {
            if (!level.getBlockState(pos).canBeReplaced()) {
                return Component.translatable("station.orbitalis.expand.no_room");
            }
        }
        buildPower(level, frame);
        BlockPos station = StationManager.stationAt(level, frame.origin());
        if (station != null) {
            StationLayout.add(level.getServer(), station, ModuleType.POWER, frame.at(0, -WING, -1), frame.at(ModuleType.POWER.length() - 1, WING, 1));
        }
        return null;
    }

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
