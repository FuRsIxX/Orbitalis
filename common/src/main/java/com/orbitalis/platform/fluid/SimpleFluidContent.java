package com.orbitalis.platform.fluid;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Le contenu en fluide gardé sur un objet (un réservoir cassé, une machine) : une pile figée, qu'on recopie à la lecture.
 */
public final class SimpleFluidContent {
    public static final SimpleFluidContent EMPTY = new SimpleFluidContent(FluidStack.EMPTY);
    public static final Codec<SimpleFluidContent> CODEC = FluidStack.OPTIONAL_CODEC.xmap(SimpleFluidContent::new, content -> content.stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, SimpleFluidContent> STREAM_CODEC = FluidStack.STREAM_CODEC
            .map(SimpleFluidContent::new, content -> content.stack);

    private final FluidStack stack;

    private SimpleFluidContent(FluidStack stack) {
        this.stack = stack;
    }

    public static SimpleFluidContent copyOf(FluidStack stack) {
        return stack.isEmpty() ? EMPTY : new SimpleFluidContent(stack.copy());
    }

    public FluidStack copy() {
        return stack.copy();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int getAmount() {
        return stack.getAmount();
    }

    public net.minecraft.world.level.material.Fluid getFluid() {
        return stack.getFluid();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SimpleFluidContent content && content.stack.equals(stack);
    }

    @Override
    public int hashCode() {
        return stack.hashCode();
    }
}
