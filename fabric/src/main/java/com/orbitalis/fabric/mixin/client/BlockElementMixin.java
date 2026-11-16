package com.orbitalis.fabric.mixin.client;

import com.orbitalis.fabric.client.EmissiveElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.block.model.BlockElement;

/** Un élément de modèle garde sa lumière propre (voir {@link BlockElementDeserializerMixin}). */
@Mixin(BlockElement.class)
public abstract class BlockElementMixin implements EmissiveElement {
    @Unique
    private int orbitalis$light;

    @Override
    public int orbitalis$light() {
        return orbitalis$light;
    }

    @Override
    public void orbitalis$setLight(int light) {
        orbitalis$light = light;
    }
}
