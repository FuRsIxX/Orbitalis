package com.orbitalis.phase1.spaceport;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pupitre d'essai : le petit frère du pupitre de lancement, d'un seul bloc. Il se relie de même à la table de
 * lancement la plus proche, mais n'affiche que la séquence de lancement, sans carte ni fiche de la fusée. C'est le poste
 * du pas de tir d'essai, d'où l'on tire ses premières fusées-sondes.
 */
public class TestConsoleBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TestConsoleBlock> CODEC = simpleCodec(TestConsoleBlock::new);
    /** Le caisson et son plan de travail ; l'écran, à l'arrière, dépasse à peine. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);

    public TestConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<TestConsoleBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** L'écran fait face au joueur qui le pose. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, LaunchTableBlock.RESEARCH_PAD)) {
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.research_required",
                    Component.translatable("research.orbitalis.node." + LaunchTableBlock.RESEARCH_PAD)).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        // Même état que le grand pupitre : le client voit le bloc, et ouvre la seule séquence de lancement.
        PacketDistributor.sendToPlayer(serverPlayer, LaunchConsoleBlock.status(level, pos, true, ResearchManager.agencyKey(serverPlayer)));
        return InteractionResult.SUCCESS;
    }
}
