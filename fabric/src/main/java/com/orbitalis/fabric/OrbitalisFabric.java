package com.orbitalis.fabric;

import com.orbitalis.Orbitalis;

import net.fabricmc.api.ModInitializer;

/** Orbitalis sous Fabric : le démarrage commun. */
public final class OrbitalisFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Orbitalis.init();
    }
}
