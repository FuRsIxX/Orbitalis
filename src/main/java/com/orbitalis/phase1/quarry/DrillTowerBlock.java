package com.orbitalis.phase1.quarry;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Bloc de la tour d'une foreuse : socle ou mât. Posé seul, il a son aspect de bloc à bâtir. Quand la tour est complète,
 * la foreuse note la place de chaque bloc dans la tour (angle, côté, cœur, et l'étage du haut) et son orientation : la
 * tour prend alors son aspect assemblé, d'un seul tenant, comme le tablier d'un pas de tir.
 */
public class DrillTowerBlock extends Block {
    /** Place du bloc dans une tour assemblée. */
    public enum Part implements StringRepresentable {
        /** Bloc isolé, ou tour pas encore complète. */
        LOOSE,
        /** Angle : deux faces vers l'extérieur, la sienne et celle de sa droite. */
        CORNER,
        /** Côté : une face vers l'extérieur. */
        EDGE,
        /** Cœur : aucune face vers l'extérieur, sauf le dessus de l'étage du haut. */
        CENTER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Vrai pour les blocs de l'étage du haut : la couronne de la tour. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public DrillTowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.LOOSE).setValue(TOP, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, TOP, BlockStateProperties.HORIZONTAL_FACING);
    }

    /** Posé : la foreuse voisine relève aussitôt sa tour. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            DerrickBlockEntity.notifyTowerChanged(serverLevel, pos);
        }
    }

    /** Cassé : la tour se défait à l'instant, sans attendre le relevé périodique de la foreuse. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            DerrickBlockEntity.notifyTowerChanged(level, pos);
        }
    }
}
