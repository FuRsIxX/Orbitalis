package com.orbitalis.platform.fluid;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Une quantité de fluide, en millibuckets : le fluide, la quantité, et ses composants éventuels. La même forme que le
 * {@code FluidStack} de NeoForge, pour Fabric comme pour NeoForge.
 */
public final class FluidStack {
    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY, 0, DataComponentPatch.EMPTY);

    /** Une pile non vide : {"id": "…", "amount": …, "components": {…}}. */
    public static final Codec<FluidStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidStack::getFluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidStack::getComponentsPatch))
            .apply(instance, FluidStack::new));

    /** Une pile qui peut être vide. */
    public static final Codec<FluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
            .xmap(optional -> optional.orElse(EMPTY), stack -> stack.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(stack));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FluidStack decode(RegistryFriendlyByteBuf buffer) {
            int amount = buffer.readVarInt();
            if (amount <= 0) {
                return EMPTY;
            }
            Holder<Fluid> fluid = ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.FLUID).decode(buffer);
            DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buffer);
            return new FluidStack(fluid.value(), amount, patch);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FluidStack stack) {
            if (stack.isEmpty()) {
                buffer.writeVarInt(0);
                return;
            }
            buffer.writeVarInt(stack.amount);
            ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.FLUID).encode(buffer, stack.fluid.builtInRegistryHolder());
            DataComponentPatch.STREAM_CODEC.encode(buffer, stack.components);
        }
    };

    private final Fluid fluid;
    private int amount;
    private final DataComponentPatch components;

    public FluidStack(Fluid fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
    }

    public FluidStack(Holder<Fluid> fluid, int amount) {
        this(fluid.value(), amount, DataComponentPatch.EMPTY);
    }

    public FluidStack(Fluid fluid, int amount, DataComponentPatch components) {
        this.fluid = fluid;
        this.amount = amount;
        this.components = components;
    }

    public Fluid getFluid() {
        return isEmpty() ? Fluids.EMPTY : fluid;
    }

    public Holder<Fluid> getFluidHolder() {
        return getFluid().builtInRegistryHolder();
    }

    public int getAmount() {
        return isEmpty() ? 0 : amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void grow(int by) {
        amount += by;
    }

    public void shrink(int by) {
        amount -= by;
    }

    public boolean isEmpty() {
        return this == EMPTY || fluid == Fluids.EMPTY || amount <= 0;
    }

    public DataComponentPatch getComponentsPatch() {
        return components;
    }

    public boolean is(Fluid other) {
        return getFluid().isSame(other);
    }

    public FluidStack copy() {
        return isEmpty() ? EMPTY : new FluidStack(fluid, amount, components);
    }

    public FluidStack copyWithAmount(int newAmount) {
        return isEmpty() || newAmount <= 0 ? EMPTY : new FluidStack(fluid, newAmount, components);
    }

    public static boolean isSameFluid(FluidStack a, FluidStack b) {
        return a.getFluid() == b.getFluid();
    }

    public static boolean isSameFluidSameComponents(FluidStack a, FluidStack b) {
        return isSameFluid(a, b) && a.components.equals(b.components);
    }

    /** Le nom du fluide, tel qu'on l'affiche (celui de son bloc liquide). */
    public Component getHoverName() {
        if (isEmpty()) {
            return Component.empty();
        }
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        if ("minecraft".equals(id.getNamespace())) {
            // L'eau et la lave du jeu n'ont pas de nom de fluide : celui de leur bloc.
            return Component.translatable(fluid.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId());
        }
        return Component.translatable("fluid." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidStack stack && stack.getAmount() == getAmount() && isSameFluidSameComponents(this, stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFluid(), getAmount(), components);
    }

    @Override
    public String toString() {
        return getAmount() + " mB " + BuiltInRegistries.FLUID.getKey(getFluid());
    }

    /** Pour les codecs réseau qui n'ont pas besoin des registres. */
    static StreamCodec<ByteBuf, Fluid> fluidById() {
        return ByteBufCodecs.idMapper(BuiltInRegistries.FLUID);
    }
}
