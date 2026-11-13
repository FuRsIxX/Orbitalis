package com.orbitalis.phase3.exploration;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Une machine de production sur place (ISRU), autonome, alimentée par son petit générateur à radio-isotopes :
 * <ul>
 * <li>la foreuse à glace, sur la Lune ou sur Mars, tire de l'eau de la glace enfouie sous elle ;</li>
 * <li>l'extracteur d'oxygène, sur Mars, tire de l'oxygène du gaz carbonique de l'atmosphère.</li>
 * </ul>
 * Ce qu'elle produit va dans les réservoirs et les tuyaux qui la touchent. Un clic droit donne son état. Il faut
 * maîtriser la technologie de l'ISRU pour la poser.
 */
public class IsruBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final String RESEARCH = "isru";

    /** Les deux sortes de machines. */
    public enum Kind {
        ICE_DRILL,
        OXYGEN_EXTRACTOR
    }

    private static final VoxelShape SHAPE = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 1.0, 0.9375);
    private final Kind kind;

    public IsruBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new IsruBlock(properties, kind));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!ResearchManager.mayPlace(context, RESEARCH)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof IsruBlockEntity isru) {
            serverPlayer.sendSystemMessage(isru.status((ServerLevel) level));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IsruBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof IsruBlockEntity isru) {
                isru.serverTick((ServerLevel) tickLevel, pos);
            }
        };
    }
}
