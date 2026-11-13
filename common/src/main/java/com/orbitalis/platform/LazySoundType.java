package com.orbitalis.platform;

import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;

/**
 * Un jeu de sons de bloc dont les sons ne sont lus qu'au moment de servir : il peut ainsi utiliser des sons du mod, inscrits
 * après les blocs.
 */
public class LazySoundType extends SoundType {
    private final Supplier<SoundEvent> breakSound;
    private final Supplier<SoundEvent> stepSound;
    private final Supplier<SoundEvent> placeSound;
    private final Supplier<SoundEvent> hitSound;
    private final Supplier<SoundEvent> fallSound;

    public LazySoundType(float volume, float pitch, Supplier<SoundEvent> breakSound, Supplier<SoundEvent> stepSound, Supplier<SoundEvent> placeSound,
            Supplier<SoundEvent> hitSound, Supplier<SoundEvent> fallSound) {
        super(volume, pitch, SoundEvents.METAL_BREAK, SoundEvents.METAL_STEP, SoundEvents.METAL_PLACE, SoundEvents.METAL_HIT, SoundEvents.METAL_FALL);
        this.breakSound = breakSound;
        this.stepSound = stepSound;
        this.placeSound = placeSound;
        this.hitSound = hitSound;
        this.fallSound = fallSound;
    }

    @Override
    public SoundEvent getBreakSound() {
        return breakSound.get();
    }

    @Override
    public SoundEvent getStepSound() {
        return stepSound.get();
    }

    @Override
    public SoundEvent getPlaceSound() {
        return placeSound.get();
    }

    @Override
    public SoundEvent getHitSound() {
        return hitSound.get();
    }

    @Override
    public SoundEvent getFallSound() {
        return fallSound.get();
    }
}
