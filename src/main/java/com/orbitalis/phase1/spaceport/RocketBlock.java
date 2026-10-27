package com.orbitalis.phase1.spaceport;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tronçon de la fusée dressée sur la table de lancement. La fusée est une colonne de ces blocs, dont la hauteur,
 * le diamètre et l'aspect dépendent de son plan (voir {@link RocketShape}).
 * <p>
 * Ces blocs ne sont que la partie visible de la fusée : ils sont posés et retirés par la table de lancement,
 * qui conserve la fusée elle-même. Ils n'existent pas en objet et ne lâchent rien s'ils sont cassés
 * (la table les remet en place tant qu'elle porte une fusée). Un clic droit ouvre les informations du pas de tir.
 */
public class RocketBlock extends Block {
    /** Nature du tronçon. */
    public enum Part implements StringRepresentable {
        /** Baie de propulsion : structure de poussée et tuyères. */
        ENGINE,
        /** Réservoirs d'un étage RP-1 / oxygène : peinture blanche. */
        TANK_WHITE,
        /** Réservoirs d'un étage hydrogène / oxygène : mousse isolante orange. */
        TANK_ORANGE,
        /** Corps d'un moteur à poudre. */
        TANK_SOLID,
        /** Interétage, entre deux étages. */
        INTERSTAGE,
        /** Compartiment de la charge utile, sous la coiffe. */
        PAYLOAD,
        /** Bas de l'ogive d'une grosse fusée : la coiffe commence à se resserrer. */
        NOSE_LOWER,
        /** Pointe de la coiffe. */
        NOSE,
        /** Baie d'un moteur à poudre : le corps du moteur, sa tuyère et quatre ailerons. */
        ENGINE_SOLID,
        /** Baie d'un moteur alimenté par pression : jupe à damier et ailerons. */
        ENGINE_PRESSURE,
        /** Baie d'un moteur cryogénique : une seule grande tuyère de cuivre. */
        ENGINE_HYDROLOX,
        /** Réservoirs en acier inoxydable, laissés nus. */
        TANK_STEEL,
        /** Réservoirs en aluminium-lithium : blancs à bande bleu nuit. */
        TANK_ALLOY,
        /** Capsule habitée : la cabine vitrée, haute de deux blocs, dessinée par ce tronçon. */
        CAPSULE,
        /** Haut de la capsule : occupe le bloc du dessus, sans rien dessiner. */
        CAPSULE_UPPER,
        /** Tour de sauvetage, au sommet d'une fusée habitée. */
        ESCAPE_TOWER,
        /** Grands réservoirs en composite : carbone noir. */
        TANK_COMPOSITE,
        /** Capsule habitée Mk2 « Horizon » (quatre places, tout en écrans), haute de deux blocs. */
        CAPSULE_MK2,
        /** Capsule habitée Mk3 « Odyssée » (quatre places, voyages lointains), haute de deux blocs. */
        CAPSULE_MK3;

        /** Vrai si le tronçon fait partie de la capsule habitée et de sa tour : ils quittent le lanceur en vol. */
        public boolean isCrewModule() {
            return isCabin() || this == CAPSULE_UPPER || this == ESCAPE_TOWER;
        }

        /** Vrai pour le tronçon de la cabine elle-même, quel que soit son modèle. */
        public boolean isCabin() {
            return this == CAPSULE || this == CAPSULE_MK2 || this == CAPSULE_MK3;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Diamètre de la fusée. */
    public enum Size implements StringRepresentable {
        /** Fusée-sonde : un demi-bloc de large. */
        SLIM(4),
        /** Petit lanceur : un bloc de large. */
        MEDIUM(8),
        /** Lanceur lourd : trois blocs de large. Le modèle déborde sur les huit cases voisines, occupées par des flancs invisibles. */
        LARGE(24);

        private final int halfWidth;

        Size(int halfWidth) {
            this.halfWidth = halfWidth;
        }

        /** Demi-largeur du corps, en pixels. */
        public int halfWidth() {
            return halfWidth;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Size> SIZE = EnumProperty.create("size", Size.class);

    public RocketBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.TANK_WHITE).setValue(SIZE, Size.SLIM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, SIZE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // La forme ne dépasse jamais le bloc : pour une fusée large, ce sont les flancs voisins qui font le reste.
        int half = Math.min(8, state.getValue(SIZE).halfWidth());
        return Block.box(8 - half, 0, 8 - half, 8 + half, 16, 8 + half);
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
