package com.orbitalis.core.energy;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Réserve d'énergie d'une machine, exprimée en FE (Forge Energy).
 * <p>
 * Elle s'appuie sur l'API d'énergie de NeoForge : les machines d'Orbitalis sont donc compatibles
 * avec les câbles, générateurs et batteries des autres mods.
 * <ul>
 * <li>{@code maxInsert} et {@code maxExtract} limitent les échanges avec l'extérieur (par tick).</li>
 * <li>{@link #generate(int)} et {@link #consume(int)} servent à la machine elle-même et ignorent ces limites.</li>
 * </ul>
 */
public class MachineEnergyStorage extends SimpleEnergyHandler {
    private final Runnable onChanged;

    /**
     * @param capacity   capacité en FE
     * @param maxInsert  FE par tick acceptés de l'extérieur (0 = la machine ne reçoit pas d'énergie)
     * @param maxExtract FE par tick fournis à l'extérieur (0 = la machine ne donne pas d'énergie)
     * @param onChanged  appelé quand la quantité d'énergie change (pour marquer le bloc à sauvegarder)
     */
    public MachineEnergyStorage(int capacity, int maxInsert, int maxExtract, Runnable onChanged) {
        super(capacity, maxInsert, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }

    public int getEnergy() {
        return energy;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isFull() {
        return energy >= capacity;
    }

    /** Produit de l'énergie dans la réserve. Renvoie la quantité réellement ajoutée. */
    public int generate(int amount) {
        int added = Math.min(amount, capacity - energy);
        if (added > 0) {
            set(energy + added);
        }
        return added;
    }

    /** Consomme de l'énergie si la réserve en contient assez. Renvoie vrai si la consommation a eu lieu. */
    public boolean consume(int amount) {
        if (amount > energy) {
            return false;
        }
        if (amount > 0) {
            set(energy - amount);
        }
        return true;
    }
}
