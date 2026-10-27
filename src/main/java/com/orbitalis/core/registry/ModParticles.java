package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particules propres au mod. Leur apparence et leur comportement sont définis côté client
 * (voir {@code OrbitalisParticle}) ; ici ne figure que leur déclaration, commune au client et au serveur.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Orbitalis.MODID);

    /** Fumée des moteurs-fusées : de grosses volutes qui gonflent, durent longtemps et se dissipent lentement. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXHAUST_SMOKE = register("exhaust_smoke", true);
    /** Flamme des moteurs-fusées : un jet blanc à la sortie de la tuyère, qui vire au jaune puis à l'orange. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENGINE_FLAME = register("engine_flame", true);
    /** Étincelle : un éclat incandescent qui retombe (arc électrique, métal en fusion, foyer). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = register("spark", false);
    /** Brume cryogénique : un voile bleuté qui coule vers le sol autour des ergols très froids. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYO_MIST = register("cryo_mist", false);
    /** Vapeur : de petites bouffées blanches qui montent et s'effacent. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VAPOR = register("vapor", false);
    /** Suie : la fumée sombre d'une cheminée de machine. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOOT = register("soot", false);

    // Pas de tir : voir LaunchEffects pour leur emploi.
    /** Fumée de lancement : d'énormes volutes dorées par les flammes, qui roulent au sol puis grisonnent. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_SMOKE = register("launch_smoke", true);
    /** Vapeur du déluge : blanche, elle gonfle vite, monte et se dissipe bien avant la fumée. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_STEAM = register("launch_steam", true);
    /** Eau du déluge : des paquets de gouttes qui retombent. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DELUGE_WATER = register("deluge_water", false);
    /** Feu du carneau : des boules de feu qui filent vers sa bouche. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRENCH_FIRE = register("trench_fire", true);
    /** Escarbille : un éclat incandescent arraché au carneau, qui rebondit au sol. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_EMBER = register("launch_ember", false);
    /** Poussière soulevée autour du pas de tir par le souffle du décollage. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_DUST = register("launch_dust", true);
    /** Voile de chaleur : l'air qui tremble au-dessus du carneau. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEAT_HAZE = register("heat_haze", false);
    /** Éclair d'allumage : une grande lueur brève au pied de la fusée. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_FLASH = register("launch_flash", true);

    // Accidents : voir BreakupFx. Les mêmes images que le feu du carneau, la fumée et la vapeur de lancement, mais
    // en bien plus gros : ce sont les boules de feu et le champignon d'une fusée qui éclate.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_FIRE = register("blast_fire", true);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_SMOKE = register("blast_smoke", true);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_CRYO = register("blast_cryo", true);

    /** La planète vue depuis l'orbite : un seul immense disque sous le joueur (voir {@code PlanetParticle}). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PLANET = register("planet", true);

    private ModParticles() {
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name, boolean alwaysVisible) {
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(alwaysVisible));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
