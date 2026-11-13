package com.orbitalis.fabric;

import com.orbitalis.Orbitalis;

import net.fabricmc.api.ClientModInitializer;

/** Orbitalis sous Fabric : le démarrage du client. */
public final class OrbitalisFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Orbitalis.initClient();
    }
}
