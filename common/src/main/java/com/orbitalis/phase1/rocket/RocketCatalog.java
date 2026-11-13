package com.orbitalis.phase1.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.core.registry.ModItems;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Catalogue des lanceurs proposés par l'ordinateur de conception.
 * <p>
 * Chaque modèle est un lanceur type, éprouvé : son nombre d'étages, ses moteurs et la famille de ses réservoirs sont
 * fixés. On y règle ce qui dépend de la mission : la charge utile, parmi celles que le modèle sait emporter, et le
 * nombre de réservoirs de chaque étage (ou de moteurs, pour un étage à poudre), dans une plage raisonnable. Les
 * caractéristiques du lanceur se recalculent à chaque réglage ; le plan imprimé correspond exactement à ce choix.
 */
public final class RocketCatalog {
    /**
     * Étage d'un modèle.
     *
     * @param engine  moteur
     * @param engines nombre de moteurs
     * @param tank    réservoir, ou {@code null} pour un étage à poudre (c'est alors le nombre de moteurs qui se règle)
     * @param min     plus petit réglage du nombre de réservoirs
     * @param max     plus grand réglage
     * @param step    pas du réglage
     * @param preset  réglage proposé d'origine
     */
    public record StageSpec(Supplier<? extends Item> engine, int engines, Supplier<? extends Item> tank, int min, int max, int step, int preset) {
    }

    /**
     * Modèle de lanceur.
     *
     * @param id       identifiant : il donne son nom et sa description dans les fichiers de langue
     * @param payloads charges utiles possibles ; {@code null} pour « aucune »
     * @param stages   étages, du premier au dernier
     */
    public record Template(String id, List<Supplier<? extends Item>> payloads, List<StageSpec> stages) {
        public String nameKey() {
            return "design.orbitalis.rocket." + id;
        }

        public String descriptionKey() {
            return nameKey() + ".desc";
        }

        /** Réglages d'origine : le nombre de réservoirs de chaque étage. */
        public int[] presets() {
            return stages.stream().mapToInt(StageSpec::preset).toArray();
        }
    }

    private static List<Template> templates;

    private RocketCatalog() {
    }

    /** Les modèles du catalogue, du plus modeste au plus puissant. */
    public static List<Template> all() {
        if (templates == null) {
            List<Template> list = new ArrayList<>();
            list.add(new Template("sounding", List.of(ModItems.SOUNDING_PAYLOAD),
                    List.of(new StageSpec(ModItems.SOLID_MOTOR, 1, null, 1, 3, 1, 1))));
            List<Supplier<? extends Item>> optional = new ArrayList<>();
            optional.add(null);
            optional.add(ModItems.SOUNDING_PAYLOAD);
            list.add(new Template("liquid_test", optional,
                    List.of(new StageSpec(ModItems.PRESSURE_FED_ENGINE, 2, ModItems.STEEL_TANK, 1, 3, 1, 1))));
            list.add(new Template("crewed_suborbital", List.of(ModItems.CREW_CAPSULE),
                    List.of(new StageSpec(ModItems.KEROLOX_ENGINE, 1, ModItems.STEEL_TANK, 2, 4, 1, 2))));
            list.add(new Template("orbital",
                    List.of(ModItems.SOUNDING_PAYLOAD, ModItems.TEST_SATELLITE, ModItems.WEATHER_SATELLITE, ModItems.OBSERVATION_SATELLITE,
                            ModItems.STATION_CORE),
                    List.of(new StageSpec(ModItems.KEROLOX_ENGINE, 2, ModItems.ALUMINUM_TANK, 16, 32, 4, 24),
                            new StageSpec(ModItems.KEROLOX_ENGINE, 1, ModItems.ALUMINUM_TANK, 2, 8, 2, 4))));
            list.add(new Template("crewed_orbital", List.of(ModItems.CREW_CAPSULE, ModItems.STATION_CORE),
                    List.of(new StageSpec(ModItems.KEROLOX_ENGINE, 2, ModItems.ALUMINUM_LITHIUM_TANK, 16, 32, 4, 24),
                            new StageSpec(ModItems.KEROLOX_ENGINE, 1, ModItems.ALUMINUM_LITHIUM_TANK, 4, 8, 2, 6))));
            list.add(new Template("heavy",
                    List.of(ModItems.LUNAR_MODULE, ModItems.NAVIGATION_SATELLITE, ModItems.COMMUNICATION_SATELLITE, ModItems.SPACE_TELESCOPE),
                    List.of(new StageSpec(ModItems.STAGED_COMBUSTION_ENGINE, 3, ModItems.COMPOSITE_TANK, 8, 16, 2, 12),
                            new StageSpec(ModItems.HYDROLOX_ENGINE, 1, ModItems.COMPOSITE_TANK, 12, 24, 2, 18),
                            new StageSpec(ModItems.HYDROLOX_ENGINE, 1, ModItems.COMPOSITE_TANK, 2, 6, 2, 4))));
            // Le vaisseau martien pèse le double du module lunaire et va plus loin : il lui faut le lanceur lourd, avec
            // des réservoirs plus grands à chaque étage.
            list.add(new Template("mars", List.of(ModItems.MARS_LANDER),
                    List.of(new StageSpec(ModItems.STAGED_COMBUSTION_ENGINE, 3, ModItems.COMPOSITE_TANK, 14, 20, 2, 16),
                            new StageSpec(ModItems.HYDROLOX_ENGINE, 1, ModItems.COMPOSITE_TANK, 20, 28, 2, 24),
                            new StageSpec(ModItems.HYDROLOX_ENGINE, 1, ModItems.COMPOSITE_TANK, 6, 10, 2, 8))));
            templates = List.copyOf(list);
        }
        return templates;
    }

    /** Ramène un réglage dans la plage de son étage, sur un pas valide. */
    public static int clamp(StageSpec stage, int value) {
        int steps = Math.round((Math.clamp(value, stage.min(), stage.max()) - stage.min()) / (float) stage.step());
        return Math.min(stage.max(), stage.min() + steps * stage.step());
    }

    /**
     * Compose la fusée d'un modèle réglé : ses pièces, emplacement par emplacement (voir {@link RocketDesign}).
     *
     * @param payload rang de la charge utile choisie parmi celles du modèle
     * @param tanks   réglage de chaque étage
     */
    public static NonNullList<ItemStack> build(Template template, int payload, int[] tanks) {
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        Supplier<? extends Item> cargo = template.payloads().get(Math.floorMod(payload, template.payloads().size()));
        if (cargo != null) {
            parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(cargo.get()));
        }
        for (int i = 0; i < template.stages().size(); i++) {
            StageSpec stage = template.stages().get(i);
            int setting = clamp(stage, i < tanks.length ? tanks[i] : stage.preset());
            if (stage.tank() == null) {
                parts.set(RocketDesign.engineSlot(i), new ItemStack(stage.engine().get(), setting));
            } else {
                parts.set(RocketDesign.engineSlot(i), new ItemStack(stage.engine().get(), stage.engines()));
                parts.set(RocketDesign.tankSlot(i), new ItemStack(stage.tank().get(), setting));
            }
            if (i > 0) {
                parts.set(RocketDesign.interstageSlot(i), new ItemStack(ModItems.INTERSTAGE.get()));
            }
        }
        return parts;
    }
}
