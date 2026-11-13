package com.orbitalis.platform.transfer.fluid;

import java.util.Objects;

import com.orbitalis.platform.fluid.FluidStack;
import com.orbitalis.platform.fluid.FluidStackTemplate;
import com.orbitalis.platform.transfer.resource.Resource;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Un fluide, sans quantité : ce qu'on échange entre réserves de fluides. */
public final class FluidResource implements Resource {
    public static final FluidResource EMPTY = new FluidResource(Fluids.EMPTY, DataComponentPatch.EMPTY);

    private final Fluid fluid;
    private final DataComponentPatch patch;

    private FluidResource(Fluid fluid, DataComponentPatch patch) {
        this.fluid = fluid;
        this.patch = patch;
    }

    public static FluidResource of(Fluid fluid) {
        return fluid == Fluids.EMPTY ? EMPTY : new FluidResource(fluid, DataComponentPatch.EMPTY);
    }

    public static FluidResource of(Fluid fluid, DataComponentPatch patch) {
        return fluid == Fluids.EMPTY ? EMPTY : new FluidResource(fluid, patch);
    }

    public static FluidResource of(Holder<Fluid> fluid) {
        return of(fluid.value());
    }

    public static FluidResource of(FluidStack stack) {
        return stack.isEmpty() ? EMPTY : new FluidResource(stack.getFluid(), stack.getComponentsPatch());
    }

    public static FluidResource of(@Nullable FluidStackTemplate template) {
        return template == null ? EMPTY : of(template.fluid());
    }

    public Fluid getFluid() {
        return fluid;
    }

    public Fluid value() {
        return fluid;
    }

    @Override
    public boolean isEmpty() {
        return fluid == Fluids.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return patch;
    }

    public FluidStack toStack(int amount) {
        return isEmpty() || amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount, patch);
    }

    public boolean matches(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid() == fluid && stack.getComponentsPatch().equals(patch);
    }

    public boolean is(Fluid other) {
        return fluid.isSame(other);
    }

    public Component getHoverName() {
        return toStack(1).getHoverName();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidResource resource && resource.fluid == fluid && resource.patch.equals(patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluid, patch);
    }

    @Override
    public String toString() {
        return "FluidResource[" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid) + "]";
    }
}
