package com.orbitalis.core.creative;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Source créative : un bloc de test qui fournit sans limite de l'énergie ou un fluide à tout ce qui le touche
 * (machines, câbles, tuyaux, bras ombilicaux). Il n'a pas de recette : on le prend dans l'onglet créatif.
 * <p>
 * La citerne change de fluide à chaque clic droit. Les réservoirs créatifs, eux, sont un bloc par fluide : on voit
 * d'un coup d'œil ce que chacun fournit.
 */
public class CreativeSourceBlock extends BaseEntityBlock {
    /** Ce que fournit la source. */
    public enum Kind {
        ENERGY,
        FLUID
    }

    /** Fluide fourni par une source de fluide : rang dans {@link #fluids()}. Sans effet sur une source d'énergie. */
    public static final IntegerProperty FLUID = IntegerProperty.create("fluid", 0, 5);

    private final Kind kind;
    /** Le fluide d'un réservoir créatif, qui n'en fournit qu'un ; {@code null} pour la citerne, qui les fait défiler. */
    private final Supplier<Fluid> fixed;
    private final MapCodec<CreativeSourceBlock> codec;

    public CreativeSourceBlock(BlockBehaviour.Properties properties, Kind kind) {
        this(properties, kind, null);
    }

    /** Réservoir créatif : une source de fluide qui fournit toujours le même. */
    public CreativeSourceBlock(BlockBehaviour.Properties properties, Kind kind, Supplier<Fluid> fixed) {
        super(properties);
        this.kind = kind;
        this.fixed = fixed;
        this.codec = simpleCodec(p -> new CreativeSourceBlock(p, kind, fixed));
        registerDefaultState(stateDefinition.any().setValue(FLUID, 0));
    }

    /** Fluides proposés, dans l'ordre où les clics les font défiler : les ergols d'abord. */
    public static List<Supplier<Fluid>> fluids() {
        return List.of(ModFluids.RP1::get, ModFluids.LIQUID_OXYGEN::get, ModFluids.LIQUID_HYDROGEN::get,
                () -> Fluids.WATER, ModFluids.HYDROGEN::get, ModFluids.OXYGEN::get);
    }

    /** Fluide fourni par une source dans cet état. */
    public static Fluid fluidOf(BlockState state) {
        if (state.getBlock() instanceof CreativeSourceBlock source && source.fixed != null) {
            return source.fixed.get();
        }
        return fluids().get(state.getValue(FLUID)).get();
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<CreativeSourceBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLUID);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeSourceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CreativeSourceBlockEntity source && tickLevel instanceof ServerLevel serverLevel) {
                source.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** Clic droit sur une source de fluide : passe au fluide suivant et l'annonce. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (kind != Kind.FLUID || fixed != null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockState next = state.setValue(FLUID, (state.getValue(FLUID) + 1) % fluids().size());
            level.setBlock(pos, next, Block.UPDATE_ALL);
            serverPlayer.sendSystemMessage(Component.translatable("creative.orbitalis.fluid_source.selected",
                    fluidOf(next).getFluidType().getDescription()));
        }
        return InteractionResult.SUCCESS;
    }
}
