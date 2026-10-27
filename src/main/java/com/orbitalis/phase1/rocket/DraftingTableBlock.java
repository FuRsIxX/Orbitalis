package com.orbitalis.phase1.rocket;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Table à dessin : la planche inclinée d'un bureau d'études, où l'on étale un plan de fusée. Un clic droit avec un
 * plan en main l'y pose ; la main vide, on le reprend. Dans les centres spatiaux, on y trouve parfois un plan
 * d'archive, oublié là.
 */
public class DraftingTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DraftingTableBlock> CODEC = simpleCodec(DraftingTableBlock::new);
    /** Vrai quand un plan est étalé sur la planche. */
    public static final BooleanProperty HAS_PLAN = BooleanProperty.create("has_plan");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public DraftingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_PLAN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_PLAN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DraftingTableBlockEntity(pos, state);
    }

    /** Avec un plan en main : on l'étale sur la planche, si elle est libre. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!stack.is(ModItems.ROCKET_BLUEPRINT.get()) || state.getValue(HAS_PLAN)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DraftingTableBlockEntity table) {
            table.setPlan(stack.copyWithCount(1));
            stack.consume(1, player);
            level.setBlock(pos, state.setValue(HAS_PLAN, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /** La main vide : on reprend le plan étalé. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(HAS_PLAN)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("block.orbitalis.drafting_table.empty"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DraftingTableBlockEntity table) {
            ItemStack plan = table.takePlan();
            if (!plan.isEmpty() && !player.getInventory().add(plan)) {
                player.drop(plan, false);
            }
            level.setBlock(pos, state.setValue(HAS_PLAN, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }
}
