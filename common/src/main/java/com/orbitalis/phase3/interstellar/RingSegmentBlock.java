package com.orbitalis.phase3.interstellar;

import java.util.Locale;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Un bloc de l'anneau pendant qu'il tourne. Le moteur de l'anneau en met un à la place de chaque bloc de coque, de
 * hublot et de rayon de l'anneau : invisible, c'est le moteur qui dessine l'anneau entier en rotation. Il garde sa
 * collision, et on ne le casse pas : on arrête d'abord l'anneau, qui retrouve alors ses blocs.
 */
public class RingSegmentBlock extends Block {
    public static final MapCodec<RingSegmentBlock> CODEC = simpleCodec(RingSegmentBlock::new);

    /** Le bloc que remplace ce segment. */
    public enum Kind implements StringRepresentable {
        HULL(ModBlocks.STATION_HULL::get),
        WINDOW(ModBlocks.STATION_WINDOW::get),
        TRUSS(ModBlocks.STATION_TRUSS::get);

        private final Supplier<? extends Block> block;

        Kind(Supplier<? extends Block> block) {
            this.block = block;
        }

        public Block block() {
            return block.get();
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Le genre de segment qui remplace ce bloc, ou rien s'il ne fait pas partie d'un anneau. */
        public static Kind of(BlockState state) {
            for (Kind kind : values()) {
                if (state.is(kind.block())) {
                    return kind;
                }
            }
            return null;
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    public RingSegmentBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.HULL));
    }

    @Override
    protected MapCodec<RingSegmentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Le bloc que ce segment remplace, tel qu'il revient quand l'anneau s'arrête. */
    public static BlockState original(BlockState segment) {
        return segment.getValue(KIND).block().defaultBlockState();
    }
}
