package com.orbitalis.phase1.fuel;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import com.orbitalis.phase1.machines.AutomationView;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

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

    /** Vue exposée aux autres blocs : le retrait y est limité aux réservoirs de sortie. */
    public ResourceHandler<FluidResource> automationView() {
        return automationView;
    }
}
