package com.orbitalis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;

/** Les réglages d'un monde, qu'on remplace pour y permettre les commandes (la carte de test en a besoin). */
@Mixin(PrimaryLevelData.class)
public interface PrimaryLevelDataAccessor {
    @Mutable
    @Accessor("settings")
    void orbitalis$setSettings(LevelSettings settings);
}
