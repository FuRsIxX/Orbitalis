package com.orbitalis.phase1.spaceport;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Dalle de pas de tir. Posée seule, c'est une simple dalle de béton. Quand le pas de tir est assemblé et mis
 * en service, la table de lancement note le rôle de chaque dalle dans la structure et les dalles s'effacent :
 * le tablier est alors dessiné d'un seul tenant, avec une seule grande image.
 * <p>
 * Une fois la structure formée, un clic droit sur n'importe quelle dalle ouvre les informations du pas de tir.
 */
public class LaunchPadBlock extends Block {
    /** Aspect de la dalle. */
    public enum Look implements StringRepresentable {
        /** Dalle isolée, ou complexe pas encore en service. */
        PLAIN,
        /** Intérieur du pas de tir. */
        INNER,
        /** Pourtour du pas de tir. */
        BORDER,
        /** Carneau, au contact de la table de lancement. */
        TRENCH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);

    public LaunchPadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOOK, Look.PLAIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOOK);
    }

    /**
     * Dans un pas de tir assemblé, la dalle s'efface : c'est la table de lancement qui dessine le tablier
     * d'un seul tenant. Elle reste en place, avec sa collision.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(LOOK) == Look.PLAIN ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    /** Posé : la table de lancement voisine relève aussitôt sa structure. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LaunchTableBlockEntity.notifyStructureChanged(level, pos);
        }
    }

    /** Cassé : la structure se défait à l'instant, sans attendre le relevé périodique de la table. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        LaunchTableBlockEntity.notifyStructureChanged(level, pos);
    }

    /** Rôle d'une dalle située à {@code ring} blocs de la table, dans un pas de tir de rayon {@code radius}. */
    public static Look lookFor(int ring, int radius) {
        if (radius <= 0 || ring > radius) {
            return Look.PLAIN;
        }
        if (ring == radius) {
            return Look.BORDER;
        }
        return ring == 1 ? Look.TRENCH : Look.INNER;
    }

    /**
     * Un clic droit sur une dalle vaut un clic droit sur la table de lancement de son pas de tir : la table est vite
     * cachée sous la fusée qu'on y bâtit. Avec un bloc en main, on le pose comme d'habitude.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem) {
            return InteractionResult.PASS;
        }
        LaunchTableBlockEntity table = LaunchTableBlockEntity.ownerOfDeck(level, pos);
        if (table == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(table.getBlockPos()).useWithoutItem(level, player, hitResult.withPosition(table.getBlockPos()));
    }

}
