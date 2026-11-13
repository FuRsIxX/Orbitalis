package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * Sas étanche : une porte de station à hublot, qu'on ouvre à la main. Fermée, elle sépare l'air des deux pièces ;
 * ouverte, elle les réunit. Deux sas de suite font une chambre de sortie : on sort dans le vide sans vider la station.
 */
public final class StationHatch {
    /** La famille de la porte : métallique, mais elle s'ouvre à la main, avec le bruit sourd d'une écoutille. */
    public static final BlockSetType TYPE = BlockSetType.register(new BlockSetType("orbitalis_station", true, false, false,
            BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    private StationHatch() {
    }

    /** Vrai si ce bloc est un sas ouvert : l'air passe. */
    public static boolean isOpen(BlockState state) {
        return state.is(ModBlocks.STATION_HATCH.get()) && state.getValue(DoorBlock.OPEN);
    }
}
