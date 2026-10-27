package com.orbitalis.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.Orbitalis;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Fluides du mod : gaz et ergols.
 * <p>
 * Ce sont de vrais fluides Minecraft (compatibles avec les tuyaux et réservoirs d'autres mods). Ils n'existent que
 * dans les machines et les réservoirs, sauf le méthane liquide, qui remplit les lacs de Titan.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Orbitalis.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Orbitalis.MODID);

    /** Température, en kelvins, sous laquelle un fluide est considéré comme cryogénique. */
    private static final int CRYOGENIC_TEMPERATURE = 150;
    private static final int WATER_COLOR = 0xFF3F76E4;
    private static final int UNKNOWN_COLOR = 0xFF9A9A9A;

    /**
     * Un fluide du mod.
     *
     * @param name  identifiant, aussi utilisé pour la traduction (fluid.orbitalis.nom)
     * @param color couleur d'affichage dans les jauges (format 0xAARRGGBB)
     */
    public record Entry(String name, int color, DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, BaseFlowingFluid.Source> source, DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {
        public Fluid get() {
            return source.get();
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    // Couleurs : la famille de l'hydrogène est rouge, celle de l'oxygène bleue, pour les distinguer d'un coup d'œil.
    // Gaz produits par l'électrolyse de l'eau (température ambiante).
    public static final Entry HYDROGEN = register("hydrogen", 0xFFF0A6A6, 293);
    public static final Entry OXYGEN = register("oxygen", 0xFFBFE4FF, 293);

    // Ergols cryogéniques : ils s'évaporent s'ils ne sont pas maintenus au froid.
    /** Hydrogène liquide (LH2), 20 K : carburant des étages supérieurs. */
    public static final Entry LIQUID_HYDROGEN = register("liquid_hydrogen", 0xFFD96A6A, 20);
    /** Oxygène liquide (LOX), 90 K : le comburant de presque tous les moteurs. */
    public static final Entry LIQUID_OXYGEN = register("liquid_oxygen", 0xFF5FB0F5, 90);

    /** Kérosène RP-1 : carburant stockable à température ambiante. */
    public static final Entry RP1 = register("rp1", 0xFFC98A3A, 293);

    /**
     * Hélium-3 : un isotope rare de l'hélium, que le vent solaire a déposé dans le régolithe de la Lune. C'est le
     * combustible de la fusion : il ne rend presque pas de neutrons.
     */
    public static final Entry HELIUM_3 = register("helium_3", 0xFFF1E7A8, 293);

    /** Méthane gazeux, que fait le réacteur de Sabatier ; le liquéfacteur en fait du méthane liquide. */
    public static final Entry METHANE = register("methane", 0xFFB6E3C6, 293);

    /**
     * Méthane liquide, 111 K : il remplit les lacs et les mers de Titan. C'est le seul fluide du mod qu'on peut verser
     * dans le monde, et puiser au seau ; hors de Titan, il se vaporise aussitôt versé.
     */
    public static final Entry LIQUID_METHANE = registerPlaceable("liquid_methane", 0xFF4E3219, 111);

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private static Entry register(String name, int color, int temperature) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(
                FluidType.Properties.create().descriptionId("fluid.orbitalis." + name).temperature(temperature)));

        // La forme « source » et la forme « coulante » se référencent mutuellement : on passe par un relais.
        Entry[] self = new Entry[1];
        Supplier<BaseFlowingFluid.Properties> properties =
                () -> new BaseFlowingFluid.Properties(type, self[0].source(), self[0].flowing());
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source =
                FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties.get()));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing =
                FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(properties.get()));

        self[0] = new Entry(name, color, type, source, flowing);
        ENTRIES.add(self[0]);
        return self[0];
    }

    /** Un fluide qu'on peut verser dans le monde : on s'y baigne, on s'y noie, on y navigue, comme dans l'eau. */
    private static Entry registerPlaceable(String name, int color, int temperature) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid.orbitalis." + name).temperature(temperature).density(422).viscosity(200)
                .canSwim(true).canDrown(true).canExtinguish(true).supportsBoating(true).fallDistanceModifier(0.0F)
                .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)) {
            /** Versé ailleurs que sur Titan, le méthane bout : il ne reste liquide que dans le froid de cette lune. */
            @Override
            public boolean isVaporizedOnPlacement(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                    net.neoforged.neoforge.fluids.FluidStack stack) {
                return !com.orbitalis.phase2.survival.SpaceRules.isTitan(level);
            }
        });
        Entry[] self = new Entry[1];
        Supplier<BaseFlowingFluid.Properties> properties = () -> new BaseFlowingFluid.Properties(type, self[0].source(), self[0].flowing())
                .block(ModBlocks.LIQUID_METHANE).bucket(ModItems.LIQUID_METHANE_BUCKET);
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source =
                FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties.get()));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing =
                FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(properties.get()));
        self[0] = new Entry(name, color, type, source, flowing);
        ENTRIES.add(self[0]);
        return self[0];
    }

    /** Tous les fluides du mod, dans leur ordre de déclaration. */
    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    /** Couleur d'affichage d'un fluide dans les jauges. */
    public static int color(Fluid fluid) {
        if (fluid == Fluids.WATER) {
            return WATER_COLOR;
        }
        for (Entry entry : ENTRIES) {
            if (entry.get() == fluid) {
                return entry.color();
            }
        }
        return UNKNOWN_COLOR;
    }

    /** Température de la surface de Titan, en kelvins : un fluide qui reste liquide plus chaud ne s'y évapore pas. */
    private static final int TITAN_SURFACE = 94;

    /**
     * Vrai si ce fluide, laissé sans refroidissement dans ce monde, s'évapore : un ergol cryogénique, sauf sur Titan
     * s'il y reste liquide (le méthane, à 111 K, dans un air à 94 K).
     */
    public static boolean boilsOff(Fluid fluid, net.minecraft.world.level.Level level) {
        return isCryogenic(fluid) && !(level != null && com.orbitalis.phase2.survival.SpaceRules.isTitan(level)
                && fluid.getFluidType().getTemperature() >= TITAN_SURFACE);
    }

    /** Vrai si le fluide doit être maintenu au froid pour ne pas s'évaporer. */
    public static boolean isCryogenic(Fluid fluid) {
        return fluid != Fluids.EMPTY && fluid.getFluidType().getTemperature() < CRYOGENIC_TEMPERATURE;
    }
}
