package com.orbitalis.phase1.spaceport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Case occupée par un grand poste de travail, autour de son bloc : invisible (c'est le bloc du poste qui dessine
 * tout le meuble), elle lui donne sa solidité. Un clic droit dessus vaut un clic droit sur le poste ; la casser
 * casse le poste, aussi vite et avec le même outil que le poste lui-même. Elle est posée et retirée par lui, et
 * n'existe pas en objet.
 */
public class WorkstationPartBlock extends Block {
    public WorkstationPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** La case laisse passer toute la lumière : sans cela, le meuble serait assombri. */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level$, BlockPos pos$) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos host = Workstation.findHost(level, pos);
        if (host == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(host).useWithoutItem(level, player, hitResult.withPosition(host));
    }

    /** On la casse au même rythme que le poste : sa dureté et son outil sont les siens. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level instanceof Level world) {
            BlockPos host = Workstation.findHost(world, pos);
            if (host != null) {
                return world.getBlockState(host).getDestroyProgress(player, level, host);
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Cassée par un joueur : le poste tombe comme s'il l'avait cassé lui-même. Il rend son objet si l'outil convient,
     * jamais en mode créatif.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos host = Workstation.findHost(level, pos);
            if (host != null) {
                BlockState hostState = level.getBlockState(host);
                boolean drop = !player.isCreative() && player.hasCorrectToolForDrops(hostState);
                if (drop) {
                    Block.dropResources(hostState, level, host, level.getBlockEntity(host), player, player.getMainHandItem());
                }
                level.levelEvent(2001, host, Block.getId(hostState));
                level.setBlock(host, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Cassée autrement (explosion, commande...) : le poste tout entier tombe, et rend son objet. */
    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level$, BlockPos pos, BlockState newState$, boolean movedByPiston) {
        super.onRemove(state, level$, pos, newState$, movedByPiston);
        if (state.is(newState$.getBlock()) || !(level$ instanceof ServerLevel level)) {
            return;
        }
        BlockPos host = Workstation.findHost(level, pos);
        if (host != null) {
            level.destroyBlock(host, true);
        }
    }
}
