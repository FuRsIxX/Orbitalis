package com.orbitalis.phase1.conduit;

import com.orbitalis.Orbitalis;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Prise de données : ce qu'exposent les blocs qu'un câble de données peut relier. Les ordinateurs (pupitre de
 * lancement, station de poursuite) et les réservoirs l'exposent pour être lus ; les écrans de contrôle, pour lire.
 * <p>
 * La prise ne transporte rien par elle-même : elle dit seulement au câble qu'il peut se raccorder. Ce sont les
 * écrans qui parcourent le réseau et relèvent ce qu'ils y trouvent.
 */
public final class DataLink {
    /** Une prise de données. Toutes sont identiques : seul compte le bloc qui la porte. */
    public static final class Port {
        private Port() {
        }
    }

    public static final Port PORT = new Port();
    public static final BlockCapability<Port, Direction> BLOCK = BlockCapability.createSided(Orbitalis.id("data"), Port.class);

    private DataLink() {
    }
}
