package com.orbitalis.phase1.spaceport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
 * Flanc d'une fusée de trois blocs de large. Le tronçon central ({@link RocketBlock}) dessine toute la largeur
 * de la fusée ; ces blocs invisibles occupent les huit cases qui l'entourent, pour que la fusée soit solide
 * sur tout son diamètre et qu'on ne puisse rien construire dedans.
 * <p>
 * Comme les tronçons, ils sont posés et retirés par la table de lancement, n'existent pas en objet
 * et ouvrent les informations du pas de tir d'un clic droit.
 */
public class RocketHullBlock extends Block {
    public RocketHullBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Le flanc laisse passer toute la lumière et n'assombrit pas ses voisins : sans cela, la fusée serait noire. */
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
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            LaunchTableBlock.useRocket(serverPlayer, level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Cassé par un joueur : si la fusée a été bâtie d'après un plan, elle se défait. Ses blocs de construction
     * reviennent, sauf celui-ci, qui tombe au sol, et le plan réapparaît pour montrer ce qui manque.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, pos, 72.0);
            if (table != null) {
                table.unformRocket(pos);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * La caméra de la vue extérieure ne bute pas sur la fusée : assis dans la cabine, on recule à travers la coque
     * et on voit le pas de tir. Sans cela, elle resterait coincée dans la cabine.
     */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getVisualShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }
}
