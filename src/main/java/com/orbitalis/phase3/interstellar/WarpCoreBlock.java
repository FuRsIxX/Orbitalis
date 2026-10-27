package com.orbitalis.phase3.interstellar;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le cœur de distorsion, dressé au milieu de la salle des machines : une colonne de verre cerclée d'anneaux, où la
 * matière exotique tiendra ouverte la bulle de distorsion. Il se charge à la main, une matière exotique après l'autre,
 * jusqu'à {@link #MAX_CHARGE} ; plus il est chargé, plus il luit. Il faut maîtriser le moteur à distorsion pour le poser.
 */
public class WarpCoreBlock extends TwoHighBlock {
    public static final MapCodec<WarpCoreBlock> CODEC = simpleCodec(WarpCoreBlock::new);
    public static final String RESEARCH = "warp_drive";
    /** Matière exotique que tient le cœur, au plus : de quoi ouvrir la bulle pour un saut. */
    public static final int MAX_CHARGE = 8;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);

    public WarpCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<WarpCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!ResearchManager.mayPlace(context, RESEARCH)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!stack.is(ModItems.EXOTIC_MATTER.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int charge = state.getValue(CHARGE);
        if (charge >= MAX_CHARGE) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("ship.orbitalis.core.full", MAX_CHARGE).withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            setCharge(level, pos, state, charge + 1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 0.6F + 0.1F * charge);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("ship.orbitalis.core.charged", charge + 1, MAX_CHARGE)
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            int charge = state.getValue(CHARGE);
            serverPlayer.sendSystemMessage(Component.translatable(charge >= MAX_CHARGE ? "ship.orbitalis.core.ready" : "ship.orbitalis.core.status",
                    charge, MAX_CHARGE).withStyle(charge >= MAX_CHARGE ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE));
        }
        return InteractionResult.SUCCESS;
    }

    /** Change la charge du cœur, sur ses deux moitiés. */
    public static void setCharge(Level level, BlockPos pos, BlockState state, int charge) {
        BlockPos lower = lower(state, pos);
        for (BlockPos half : new BlockPos[] {lower, lower.above()}) {
            BlockState there = level.getBlockState(half);
            if (there.getBlock() instanceof WarpCoreBlock) {
                level.setBlock(half, there.setValue(CHARGE, charge), Block.UPDATE_ALL);
            }
        }
    }
}
