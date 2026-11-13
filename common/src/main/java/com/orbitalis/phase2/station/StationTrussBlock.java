package com.orbitalis.phase2.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Poutre treillis : la charpente extérieure d'une station, qui porte les ailes de panneaux solaires. C'est un bloc
 * plein, sur lequel on s'appuie et qu'on ne traverse pas, mais l'air passe à travers ses croisillons.
 */
public class StationTrussBlock extends Block implements AirPermeable {
    public static final MapCodec<StationTrussBlock> CODEC = simpleCodec(StationTrussBlock::new);

    public StationTrussBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<StationTrussBlock> codec() {
        return CODEC;
    }
}
