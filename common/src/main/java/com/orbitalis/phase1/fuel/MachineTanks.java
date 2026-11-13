package com.orbitalis.phase1.fuel;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import com.orbitalis.phase1.machines.AutomationView;

import net.minecraft.world.level.material.Fluid;
import com.orbitalis.platform.fluid.FluidStack;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.fluid.FluidResource;
import com.orbitalis.platform.transfer.fluid.FluidStacksResourceHandler;
import com.orbitalis.platform.transfer.fluid.FluidUtil;

/**
 * Réservoirs de fluides d'une machine, en millibuckets (mB) : 1000 mB = 1 seau.
 * <p>
 * Même principe que l'inventaire des machines : {@code canInsert} filtre ce qui peut entrer, et
 * l'automatisation ne retire que des réservoirs autorisés par {@code canExtract}.
 */
public class MachineTanks extends FluidStacksResourceHandler {
    private final BiPredicate<Integer, Fluid> canInsert;
    private final Runnable onChanged;
    private final ResourceHandler<FluidResource> automationView;

    /**
     * @param size       nombre de réservoirs
     * @param capacity   capacité de chaque réservoir, en mB
     * @param canInsert  indique si un fluide peut entrer dans un réservoir
     * @param canExtract indique si l'automatisation peut vider un réservoir
     * @param onChanged  appelé quand le contenu change
     */
    public MachineTanks(int size, int capacity, BiPredicate<Integer, Fluid> canInsert, IntPredicate canExtract, Runnable onChanged) {
        super(size, capacity);
        this.canInsert = canInsert;
        this.onChanged = onChanged;
        this.automationView = new AutomationView<>(this, canExtract);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return canInsert.test(index, resource.getFluid());
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged.run();
    }

    public int getCapacity() {
        return capacity;
    }

    /** Copie du contenu d'un réservoir. */
    public FluidStack getFluid(int index) {
        return FluidUtil.getStack(this, index);
    }

    /** Remplace le contenu d'un réservoir, sans vérifier les règles d'entrée. */
    public void setFluid(int index, FluidStack stack) {
        set(index, FluidResource.of(stack), stack.getAmount());
    }

    /** Garde les fluides sur l'objet de la machine cassée, réservoir par réservoir ; rien s'ils sont tous vides. */
    public void storeIn(net.minecraft.core.component.DataComponentMap.Builder components) {
        java.util.List<com.orbitalis.platform.fluid.SimpleFluidContent> contents = new java.util.ArrayList<>();
        boolean any = false;
        for (int i = 0; i < size(); i++) {
            FluidStack stack = getFluid(i);
            any |= !stack.isEmpty();
            contents.add(com.orbitalis.platform.fluid.SimpleFluidContent.copyOf(stack));
        }
        if (any) {
            components.set(com.orbitalis.core.registry.ModDataComponents.MACHINE_FLUIDS.get(), contents);
        }
    }

    /** Rend les fluides que l'objet avait gardés, une fois la machine reposée (jamais plus que la capacité). */
    public void restoreFrom(com.orbitalis.platform.ComponentGetter components) {
        var contents = components.get(com.orbitalis.core.registry.ModDataComponents.MACHINE_FLUIDS.get());
        if (contents == null) {
            return;
        }
        for (int i = 0; i < Math.min(size(), contents.size()); i++) {
            FluidStack stack = contents.get(i).copy();
            if (stack.getAmount() > capacity) {
                stack.setAmount(capacity);
            }
            setFluid(i, stack);
        }
    }

    /** Vue exposée aux autres blocs : le retrait y est limité aux réservoirs de sortie. */
    public ResourceHandler<FluidResource> automationView() {
        return automationView;
    }
}
