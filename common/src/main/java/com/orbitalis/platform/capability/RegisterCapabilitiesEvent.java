package com.orbitalis.platform.capability;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * L'inscription de ce qu'offrent les blocs du mod (même forme que l'événement de NeoForge) : faite une fois, quand les
 * registres sont prêts. Chaque chargeur expose ensuite aux autres mods l'énergie, les fluides et les objets inscrits ici.
 */
public final class RegisterCapabilitiesEvent {
    public static final RegisterCapabilitiesEvent INSTANCE = new RegisterCapabilitiesEvent();

    private RegisterCapabilitiesEvent() {
    }

    public <T, C, E extends BlockEntity> void registerBlockEntity(BlockCapability<T, C> capability, BlockEntityType<E> type,
            BlockCapability.EntityProvider<T, C, ? super E> provider) {
        capability.byEntity.put(type, provider);
    }

    public <T, C> void registerBlock(BlockCapability<T, C> capability, BlockCapability.BlockProvider<T, C> provider, Block... blocks) {
        for (Block block : blocks) {
            capability.byBlock.put(block, provider);
        }
    }
}
