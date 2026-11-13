package com.orbitalis.platform.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;

/** Un fluide et une quantité, tels qu'une recette les décrit : {"id": "…", "amount": …}. */
public record FluidStackTemplate(Fluid fluid, int amount) {
    public static final Codec<FluidStackTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidStackTemplate::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStackTemplate::amount))
            .apply(instance, FluidStackTemplate::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStackTemplate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), FluidStackTemplate::fluid,
            ByteBufCodecs.VAR_INT, FluidStackTemplate::amount,
            FluidStackTemplate::new);

    public FluidStack create() {
        return new FluidStack(fluid, amount);
    }

    public Fluid getFluid() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }
}
