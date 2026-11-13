package com.orbitalis.core.creative;

import java.util.List;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.rocket.RocketDesign;
import com.orbitalis.phase1.rocket.RocketItem;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Fusées prêtes à l'emploi, proposées dans l'onglet créatif pour tester les lancements sans passer par l'atelier.
 */
public final class CreativeRockets {
    private CreativeRockets() {
    }

    /** Les fusées de test, de la plus simple à la plus grosse. */
    public static List<ItemStack> all() {
        return List.of(sounding(), liquidTest(), orbital(), satelliteLauncher(), crewedSuborbital(), crewedOrbital(), stationLauncher(), lunarLauncher(), marsLauncher(), titanLauncher(), europaLauncher(), fusionLauncher());
    }

    /** Fusée-sonde à poudre : aucun ravitaillement, pas de tir 3×3. */
    private static ItemStack sounding() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.SOLID_MOTOR.get()));
        return named(parts, "creative.orbitalis.rocket.sounding");
    }

    /** Petite fusée à ergols liquides : pour essayer le ravitaillement par le bras ombilical, pas de tir 3×3. */
    private static ItemStack liquidTest() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.PRESSURE_FED_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get()));
        return named(parts, "creative.orbitalis.rocket.liquid_test");
    }

    /** Lanceur à deux étages qui atteint l'orbite basse : 125 t, il lui faut un pas de tir 5×5. */
    private static ItemStack orbital() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.ALUMINUM_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.KEROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.ALUMINUM_TANK.get(), 4));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.orbital");
    }

    /** Le même lanceur, avec un satellite d'observation sous la coiffe : il le dépose en orbite polaire. */
    public static ItemStack satelliteLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.OBSERVATION_SATELLITE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.ALUMINUM_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.KEROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.ALUMINUM_TANK.get(), 4));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.satellite");
    }

    /** Petite fusée habitée : un saut au-dessus de l'atmosphère et retour, depuis un pas de tir 3×3. */
    public static ItemStack crewedSuborbital() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.CREW_CAPSULE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.KEROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get(), 2));
        return named(parts, "creative.orbitalis.rocket.crewed_suborbital");
    }

    /** Lanceur habité à deux étages : il met la capsule en orbite basse, depuis un pas de tir 5×5. */
    public static ItemStack crewedOrbital() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.CREW_CAPSULE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.ALUMINUM_LITHIUM_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.KEROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.ALUMINUM_LITHIUM_TANK.get(), 6));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.crewed_orbital");
    }

    /** Lanceur du module central de station : il le dépose en orbite basse, depuis un pas de tir 5×5. */
    public static ItemStack stationLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.STATION_CORE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.ALUMINUM_LITHIUM_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.KEROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.ALUMINUM_LITHIUM_TANK.get(), 6));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.station");
    }

    /**
     * Lanceur lunaire à trois étages, 356 t : trois moteurs à combustion étagée, puis deux étages cryogéniques,
     * tous en grands réservoirs composites. Il emporte le vaisseau lunaire. Il lui faut un pas de tir 7×7.
     */
    public static ItemStack lunarLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.LUNAR_MODULE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.STAGED_COMBUSTION_ENGINE.get(), 3));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.COMPOSITE_TANK.get(), 12));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.COMPOSITE_TANK.get(), 18));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        parts.set(RocketDesign.engineSlot(2), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(2), new ItemStack(ModItems.COMPOSITE_TANK.get(), 4));
        parts.set(RocketDesign.interstageSlot(2), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.lunar");
    }

    /**
     * Lanceur martien à trois étages : le lanceur lunaire, avec des réservoirs plus grands pour emporter le vaisseau
     * martien, plus lourd, un peu plus loin. Il lui faut un pas de tir 7×7.
     */
    public static ItemStack marsLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.MARS_LANDER.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.STAGED_COMBUSTION_ENGINE.get(), 3));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.COMPOSITE_TANK.get(), 16));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.COMPOSITE_TANK.get(), 24));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        parts.set(RocketDesign.engineSlot(2), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(2), new ItemStack(ModItems.COMPOSITE_TANK.get(), 8));
        parts.set(RocketDesign.interstageSlot(2), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.mars");
    }

    /**
     * Lanceur titanien à trois étages : un premier étage à combustion étagée, un étage cryogénique, et un étage nucléaire
     * thermique, avec ses réservoirs d'hydrogène, qui pousse le vaisseau titanien jusqu'à Saturne.
     */
    public static ItemStack titanLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.TITAN_LANDER.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.STAGED_COMBUSTION_ENGINE.get(), 3));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.COMPOSITE_TANK.get(), 16));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.COMPOSITE_TANK.get(), 12));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        parts.set(RocketDesign.engineSlot(2), new ItemStack(ModItems.NUCLEAR_THERMAL_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(2), new ItemStack(ModItems.HYDROGEN_TANK.get(), 24));
        parts.set(RocketDesign.interstageSlot(2), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.titan");
    }

    /**
     * Lanceur d'Europe : le lanceur titanien, agrandi à chaque étage (quatre moteurs en bas, plus de réservoirs
     * partout) pour emporter le vaisseau d'Europe un peu plus loin, jusqu'à Jupiter.
     */
    public static ItemStack europaLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.EUROPA_LANDER.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.STAGED_COMBUSTION_ENGINE.get(), 4));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.COMPOSITE_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.COMPOSITE_TANK.get(), 16));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        parts.set(RocketDesign.engineSlot(2), new ItemStack(ModItems.NUCLEAR_THERMAL_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(2), new ItemStack(ModItems.HYDROGEN_TANK.get(), 32));
        parts.set(RocketDesign.interstageSlot(2), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.europa");
    }

    /**
     * Lanceur à fusion : le premier étage du lanceur d'Europe, un étage cryogénique plus court, et un étage à fusion
     * qui emporte le vaisseau d'Europe plus loin que Mercure, pour moins de masse au décollage.
     */
    public static ItemStack fusionLauncher() {
        NonNullList<ItemStack> parts = emptyParts();
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.EUROPA_LANDER.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.STAGED_COMBUSTION_ENGINE.get(), 4));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.COMPOSITE_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.HYDROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.COMPOSITE_TANK.get(), 12));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        parts.set(RocketDesign.engineSlot(2), new ItemStack(ModItems.FUSION_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(2), new ItemStack(ModItems.HYDROGEN_TANK.get(), 16));
        parts.set(RocketDesign.interstageSlot(2), new ItemStack(ModItems.INTERSTAGE.get()));
        return named(parts, "creative.orbitalis.rocket.fusion");
    }

    /** Toutes les fusées de test portent une coiffe. */
    private static NonNullList<ItemStack> emptyParts() {
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        return parts;
    }

    private static ItemStack named(NonNullList<ItemStack> parts, String nameKey) {
        ItemStack rocket = RocketItem.create(parts);
        rocket.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
        return rocket;
    }
}
