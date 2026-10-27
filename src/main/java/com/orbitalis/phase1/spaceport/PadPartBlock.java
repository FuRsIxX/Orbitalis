package com.orbitalis.phase1.spaceport;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pièce de pas de tir à forme propre : escalier, palier, dalle, appui, rampe de déluge. Chacune a un modèle 3D
 * et une collision qui épouse ce modèle : on marche sur un palier, on gravit un escalier.
 * <p>
 * La pièce s'oriente à la pose, et prévient la table de lancement voisine quand on la pose ou qu'on la casse.
 * Sa forme est donnée tournée vers l'est (l'axe x du modèle) ; elle tourne avec le bloc.
 */
public class PadPartBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Escalier : quatre marches minces qui montent vers l'est, d'un quart de bloc chacune. Elles sont minces parce
     * que les volées se superposent tous les deux étages : il faut laisser passer la tête.
     */
    public static final double[][] STAIRS = {{0, 3, 0, 4, 4, 16}, {4, 7, 0, 8, 8, 16}, {8, 11, 0, 12, 12, 16}, {12, 15, 0, 16, 16, 16}};
    /** Palier : un platelage au pied de la case. */
    public static final double[][] LANDING = {{0, 0, 0, 16, 1, 16}};
    /** Dalle de tour : une vraie demi-dalle, pour le sommet. */
    public static final double[][] DECK = {{0, 0, 0, 16, 8, 16}};
    /** Appui du tablier : une semelle, un fût, un chapiteau. */
    public static final double[][] SUPPORT = {{2, 0, 2, 14, 2.5, 14}, {4.5, 2.5, 4.5, 11.5, 13, 11.5}, {3, 13, 3, 13, 16, 13}};
    /** Rampe de déluge : la conduite couchée, et sa buse au milieu. */
    public static final double[][] DELUGE = {{0, 0, 6, 16, 5, 10}, {6.5, 5, 6.5, 9.5, 9, 9.5}};

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    /**
     * Vrai pour l'escalier : on le vise et on le casse comme un bloc plein (on ne casse pas au travers, comme pour le
     * bâti de la tour), mais on marche toujours sur ses marches.
     */
    private final boolean fullOutline;

    public PadPartBlock(BlockBehaviour.Properties properties, double[][] boxes) {
        super(properties);
        this.fullOutline = boxes == STAIRS;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxes) {
                shape = Shapes.or(shape, rotated(box, facing));
            }
            shapes.put(facing, shape.optimize());
        }
    }

    /** Un pavé donné tourné vers l'est, ramené à l'orientation voulue (quart de tour par quart de tour). */
    static VoxelShape rotated(double[] box, Direction facing) {
        double x0 = box[0], z0 = box[2], x1 = box[3], z1 = box[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - z1, box[1], x0, 16 - z0, box[4], x1);
            case WEST -> Block.box(16 - x1, box[1], 16 - z1, 16 - x0, box[4], 16 - z0);
            case NORTH -> Block.box(z0, box[1], 16 - x1, z1, box[4], 16 - x0);
            default -> Block.box(x0, box[1], z0, x1, box[4], z1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return fullOutline ? Shapes.block() : shapes.get(state.getValue(FACING));
    }

    /** La collision suit toujours la vraie forme de la pièce : les marches de l'escalier, la tôle du palier. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** Posée, la pièce regarde dans la direction du joueur : un escalier monte donc en s'éloignant de lui. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LaunchTableBlockEntity.notifyStructureChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        LaunchTableBlockEntity.notifyStructureChanged(level, pos);
    }
}
