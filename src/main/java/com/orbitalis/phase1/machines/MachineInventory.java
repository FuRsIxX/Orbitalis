package com.orbitalis.phase1.machines;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Inventaire d'une machine.
 * <ul>
 * <li>Dépôt : seuls les objets acceptés par {@code canInsert} entrent, pour les joueurs comme pour
 * l'automatisation (entonnoirs, tuyaux d'autres mods).</li>
 * <li>Retrait : un joueur peut toujours reprendre le contenu d'un emplacement depuis l'interface ;
 * l'automatisation, elle, ne retire que des emplacements autorisés par {@code canExtract}
 * (voir {@link #automationView()}).</li>
 * </ul>
 * La machine modifie directement son contenu avec {@link #setStack(int, ItemStack)}.
 */
public class MachineInventory extends ItemStacksResourceHandler {
    private final BiPredicate<Integer, ItemStack> canInsert;
    private final Runnable onChanged;
    private final ResourceHandler<ItemResource> automationView;

    /**
     * @param size       nombre d'emplacements
     * @param canInsert  indique si un objet peut être déposé dans un emplacement
     * @param canExtract indique si l'automatisation peut retirer le contenu d'un emplacement
     * @param onChanged  appelé quand le contenu change
     */
    public MachineInventory(int size, BiPredicate<Integer, ItemStack> canInsert, IntPredicate canExtract, Runnable onChanged) {
        super(size);
        this.canInsert = canInsert;
        this.onChanged = onChanged;
        this.automationView = new AutomationView<>(this, canExtract);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return canInsert.test(index, resource.toStack());
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }

    /** Copie du contenu d'un emplacement. */
    public ItemStack getStack(int index) {
        return getResource(index).toStack(getAmountAsInt(index));
    }

    /** Remplace le contenu d'un emplacement, sans vérifier les règles de dépôt. */
    public void setStack(int index, ItemStack stack) {
        set(index, ItemResource.of(stack), stack.getCount());
    }

    /** Vue de l'inventaire exposée aux autres blocs : le retrait y est limité aux emplacements de sortie. */
    public ResourceHandler<ItemResource> automationView() {
        return automationView;
    }
}
