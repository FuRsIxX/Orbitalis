package com.orbitalis.neoforge;

import com.orbitalis.Orbitalis;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Orbitalis sous NeoForge : le démarrage commun, puis celui du client s'il y en a un. */
@Mod(Orbitalis.MODID)
public final class OrbitalisNeoForge {
    public OrbitalisNeoForge(IEventBus modBus) {
        Orbitalis.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Orbitalis.initClient();
        }
    }
}
