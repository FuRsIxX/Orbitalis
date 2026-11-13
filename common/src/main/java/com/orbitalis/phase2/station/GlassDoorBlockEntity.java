package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Porte vitrée, côté moitié basse : sur le client, elle fait coulisser ses deux battants en douceur vers leur
 * position, ouverte ou fermée.
 */
public class GlassDoorBlockEntity extends BlockEntity {
    /** Durée du coulissement des battants, en ticks. */
    public static final int SLIDE_TICKS = 10;

    private float open;
    private float openBefore;
    private boolean placed;

    public GlassDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GLASS_DOOR.get(), pos, state);
    }

    public void clientTick(BlockState state) {
        float target = state.getValue(GlassDoorBlock.OPEN) ? 1.0F : 0.0F;
        if (!placed) {
            // Une porte qui entre dans le champ de vue est déjà à sa place : elle ne rejoue pas son mouvement.
            placed = true;
            open = openBefore = target;
            return;
        }
        openBefore = open;
        open = Mth.approach(open, target, 1.0F / SLIDE_TICKS);
    }

    /** Ouverture des battants, de 0 (fermés) à 1 (glissés dans les parois), adoucie au départ et à l'arrivée. */
    public float openness(float partialTick) {
        float t = Mth.lerp(partialTick, openBefore, open);
        return t * t * (3.0F - 2.0F * t);
    }
}
