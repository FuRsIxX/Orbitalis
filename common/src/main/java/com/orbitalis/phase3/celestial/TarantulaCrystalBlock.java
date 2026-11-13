package com.orbitalis.phase3.celestial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le cristal de la Tarentule : un bouquet de prismes roses qui luisent, sorti de la poussière. Il n'est pas un cube : sa
 * forme est celle du bouquet, un peu moins large et moins haut qu'un bloc. Le jeu le tourne au hasard d'un quart de
 * tour à l'autre, pour que deux bouquets voisins ne se ressemblent pas.
 */
public class TarantulaCrystalBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);

    public TarantulaCrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
