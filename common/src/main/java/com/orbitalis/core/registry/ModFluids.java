package com.orbitalis.core.registry;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.platform.registry.DeferredHolder;
import com.orbitalis.platform.registry.DeferredRegister;

import dev.architectury.core.fluid.ArchitecturyFlowingFluid;
import dev.architectury.core.fluid.SimpleArchitecturyFluidAttributes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Fluides du mod : gaz et ergols.
 * <p>
 * Ce sont de vrais fluides Minecraft (compatibles avec les tuyaux et réservoirs d'autres mods), posés sur les fluides
 * d'Architectury pour marcher sous Fabric comme sous NeoForge. Ils n'existent que dans les machines et les réservoirs, sauf le
 * méthane liquide, qui remplit les lacs de Titan.
 */
public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Orbitalis.MODID);

    /** Température, en kelvins, sous laquelle un fluide est considéré comme cryogénique. */
    private static final int CRYOGENIC_TEMPERATURE = 150;
    private static final int WATER_COLOR = 0xFF3F76E4;
    private static final int UNKNOWN_COLOR = 0xFF9A9A9A;
    /** Les fluides du mod se dessinent avec la texture de l'eau, teintée à leur couleur. */
    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    /**
     * Un fluide du mod.
     *
     * @param name        identifiant, aussi utilisé pour la traduction (fluid.orbitalis.nom)
     * @param color       couleur d'affichage dans les jauges (format 0xAARRGGBB)
     * @param temperature température du fluide, en kelvins
     */
    public record Entry(String name, int color, int temperature, DeferredHolder<Fluid, ArchitecturyFlowingFluid.Source> source,
            DeferredHolder<Fluid, ArchitecturyFlowingFluid.Flowing> flowing) {
        public Fluid get() {
            return source.get();
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    // Couleurs : la famille de l'hydrogène est rouge, celle de l'oxygène bleue, pour les distinguer d'un coup d'œil.
    // Gaz produits par l'électrolyse de l'eau (température ambiante).
    public static final Entry HYDROGEN = register("hydrogen", 0xFFF0A6A6, 293, false);
    public static final Entry OXYGEN = register("oxygen", 0xFFBFE4FF, 293, false);

    // Ergols cryogéniques : ils s'évaporent s'ils ne sont pas maintenus au froid.
    /** Hydrogène liquide (LH2), 20 K : carburant des étages supérieurs. */
    public static final Entry LIQUID_HYDROGEN = register("liquid_hydrogen", 0xFFD96A6A, 20, false);
    /** Oxygène liquide (LOX), 90 K : le comburant de presque tous les moteurs. */
    public static final Entry LIQUID_OXYGEN = register("liquid_oxygen", 0xFF5FB0F5, 90, false);

    /** Kérosène RP-1 : carburant stockable à température ambiante. */
    public static final Entry RP1 = register("rp1", 0xFFC98A3A, 293, false);

    /**
     * Hélium-3 : un isotope rare de l'hélium, que le vent solaire a déposé dans le régolithe de la Lune. C'est le
     * combustible de la fusion : il ne rend presque pas de neutrons.
     */
    public static final Entry HELIUM_3 = register("helium_3", 0xFFF1E7A8, 293, false);

    /** Méthane gazeux, que fait le réacteur de Sabatier ; le liquéfacteur en fait du méthane liquide. */
    public static final Entry METHANE = register("methane", 0xFFB6E3C6, 293, false);

    /**
     * Méthane liquide, 111 K : il remplit les lacs et les mers de Titan. C'est le seul fluide du mod qu'on peut verser
     * dans le monde, et puiser au seau ; hors de Titan, il se vaporise aussitôt versé (voir {@code MethaneBucketItem}). On y
     * nage comme dans l'eau : il est dans l'étiquette de fluide {@code minecraft:water}.
     */
    public static final Entry LIQUID_METHANE = register("liquid_methane", 0xFF4E3219, 111, true);

    private ModFluids() {
    }

    public static void register() {
        FLUIDS.register();
    }

    private static Entry register(String name, int color, int temperature, boolean placeable) {
        // La forme « source » et la forme « coulante » se référencent mutuellement : on passe par un relais.
        Entry[] self = new Entry[1];
        SimpleArchitecturyFluidAttributes attributes = SimpleArchitecturyFluidAttributes.ofSupplier(
                        () -> self[0].flowing(), () -> self[0].source())
                .sourceTexture(STILL).flowingTexture(FLOW).color(color).temperature(temperature).convertToSource(false);
        if (placeable) {
            attributes.density(422).viscosity(200)
                    .blockSupplier(() -> ModBlocks.LIQUID_METHANE.registrySupplier())
                    .bucketItemSupplier(() -> ModItems.LIQUID_METHANE_BUCKET.registrySupplier());
        }
        DeferredHolder<Fluid, ArchitecturyFlowingFluid.Source> source =
                FLUIDS.register(name, () -> new ArchitecturyFlowingFluid.Source(attributes));
        DeferredHolder<Fluid, ArchitecturyFlowingFluid.Flowing> flowing =
                FLUIDS.register("flowing_" + name, () -> new ArchitecturyFlowingFluid.Flowing(attributes));
        self[0] = new Entry(name, color, temperature, source, flowing);
        ENTRIES.add(self[0]);
        return self[0];
    }

    /** Tous les fluides du mod, dans leur ordre de déclaration. */
    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    /** L'entrée de ce fluide (source ou coulant), ou {@code null} s'il n'est pas du mod. */
    public static Entry entry(Fluid fluid) {
        for (Entry entry : ENTRIES) {
            if (entry.source().get() == fluid || entry.flowing().get() == fluid) {
                return entry;
            }
        }
        return null;
    }

    /** Couleur d'affichage d'un fluide dans les jauges. */
    public static int color(Fluid fluid) {
        if (fluid == Fluids.WATER) {
            return WATER_COLOR;
        }
        Entry entry = entry(fluid);
        return entry == null ? UNKNOWN_COLOR : entry.color();
    }

    /** Température d'un fluide, en kelvins : celle d'un fluide du mod, l'ambiante (293 K) pour les autres, 1300 K pour la lave. */
    public static int temperature(Fluid fluid) {
        if (fluid.isSame(Fluids.LAVA)) {
            return 1300;
        }
        Entry entry = entry(fluid);
        return entry == null ? 293 : entry.temperature();
    }

    /** Température de la surface de Titan, en kelvins : un fluide qui reste liquide plus chaud ne s'y évapore pas. */
    private static final int TITAN_SURFACE = 94;

    /**
     * Vrai si ce fluide, laissé sans refroidissement dans ce monde, s'évapore : un ergol cryogénique, sauf sur Titan
     * s'il y reste liquide (le méthane, à 111 K, dans un air à 94 K).
     */
    public static boolean boilsOff(Fluid fluid, net.minecraft.world.level.Level level) {
        return isCryogenic(fluid) && !(level != null && com.orbitalis.phase2.survival.SpaceRules.isTitan(level)
                && temperature(fluid) >= TITAN_SURFACE);
    }

    /** Vrai si le fluide doit être maintenu au froid pour ne pas s'évaporer. */
    public static boolean isCryogenic(Fluid fluid) {
        return fluid != Fluids.EMPTY && temperature(fluid) < CRYOGENIC_TEMPERATURE;
    }
}
