package com.orbitalis.phase1.spaceport;

import java.util.Locale;

import com.orbitalis.core.registry.ModFluids;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bloc de ravitaillement : la prise par laquelle un ergol entre dans le pas de tir. Il se pose au pied de la tour, à
 * la place d'un bloc de charpente, et ne laisse passer qu'un seul ergol : il en faut donc un par ergol que la
 * fusée embarque (kérosène, oxygène liquide, hydrogène liquide).
 * <p>
 * Un clic droit à main vide change son ergol. Les réservoirs et les tuyaux se branchent contre lui ; ce qu'il reçoit
 * est chargé dans la fusée de la table de lancement voisine (voir {@link UmbilicalFluidHandler}).
 */
public class FuelPortBlock extends Block {
    /** Les ports d'ergols sont ceux de la ferme de réservoirs : il faut en maîtriser la technologie pour les poser. */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return com.orbitalis.core.research.ResearchManager.mayPlace(context, "fuel_farm") ? super.getStateForPlacement(context) : null;
    }

    /** Ergol que le bloc laisse passer. */
    public enum Kind implements StringRepresentable {
        RP1,
        LOX,
        LH2,
        /** Méthane liquide. */
        CH4,
        /** Hélium-3, le combustible des moteurs à fusion. */
        HE3,
        /** L'eau du déluge : elle va dans la réserve du pas de tir, pas dans la fusée. */
        WATER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Vrai si ce fluide est l'ergol du bloc. */
        public boolean accepts(Fluid fluid) {
            return switch (this) {
                case RP1 -> fluid == ModFluids.RP1.get();
                case LOX -> fluid == ModFluids.LIQUID_OXYGEN.get();
                case LH2 -> fluid == ModFluids.LIQUID_HYDROGEN.get();
                case CH4 -> fluid == ModFluids.LIQUID_METHANE.get();
                case HE3 -> fluid == ModFluids.HELIUM_3.get();
                case WATER -> fluid == net.minecraft.world.level.material.Fluids.WATER;
            };
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    public FuelPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.RP1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    /**
     * Avec un seau d'eau, sur un bloc réglé sur l'eau : le seau est versé dans la réserve du déluge. C'est le moyen
     * le plus simple de la remplir ; un tuyau d'eau fait la même chose sans y penser.
     */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(KIND) != Kind.WATER || !stack.is(net.minecraft.world.item.Items.WATER_BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, pos, LaunchComplex.REACH + 2);
            if (table == null || table.fill(net.minecraft.world.level.material.Fluids.WATER, 1000, true) < 1000) {
                serverPlayer.sendSystemMessage(Component.translatable("sequence.orbitalis.water_full").withStyle(ChatFormatting.GOLD), true);
            } else {
                table.fill(net.minecraft.world.level.material.Fluids.WATER, 1000, false);
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                serverPlayer.sendSystemMessage(Component.translatable("sequence.orbitalis.water_level", table.getWater(), table.waterCapacity())
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Clic droit à main vide : passe au réglage suivant. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            Kind next = Kind.values()[(state.getValue(KIND).ordinal() + 1) % Kind.values().length];
            level.setBlock(pos, state.setValue(KIND, next), Block.UPDATE_ALL);
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.fuel_port.kind",
                    Component.translatable("spaceport.orbitalis.fuel_port." + next.getSerializedName())).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LaunchTableBlockEntity.notifyStructureChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        LaunchTableBlockEntity.notifyStructureChanged(level, pos);
    }
}
