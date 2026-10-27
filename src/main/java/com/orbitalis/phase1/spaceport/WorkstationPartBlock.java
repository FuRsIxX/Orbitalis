package com.orbitalis.phase1.spaceport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Case occupée par un grand poste de travail, autour de son bloc : invisible (c'est le bloc du poste qui dessine
 * tout le meuble), elle lui donne sa solidité. Un clic droit dessus vaut un clic droit sur le poste ; la casser
 * casse le poste. Elle est posée et retirée par lui, et n'existe pas en objet.
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
    protected boolean propagatesSkylightDown(BlockState state) {
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

    /** Cassée : le poste tout entier tombe, et rend son objet. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos host = Workstation.findHost(level, pos);
        if (host != null) {
            level.destroyBlock(host, true);
        }
    }
}
