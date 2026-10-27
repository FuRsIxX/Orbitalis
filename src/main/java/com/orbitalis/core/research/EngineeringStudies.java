package com.orbitalis.core.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.rocket.RocketCatalog;
import com.orbitalis.phase1.rocket.RocketParts;
import com.orbitalis.phase1.satellite.SatelliteType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Études de l'établi d'ingénieur.
 * <p>
 * Le terminal de recherche donne à l'agence des technologies : un savoir. L'établi d'ingénieur en fait du matériel.
 * Chaque pièce de fusée (moteur, réservoir, charge utile) et chaque modèle de lanceur du catalogue a son étude :
 * elle demande la technologie correspondante, et les matériaux d'un prototype, pris dans l'inventaire du joueur.
 * Une fois l'étude faite, l'agence sait employer la pièce (sur un plan imprimé comme sur une fusée bâtie librement),
 * ou imprimer le modèle à l'ordinateur de conception.
 * <p>
 * Un modèle de lanceur demande en plus que les moteurs et les réservoirs qu'il emploie aient été étudiés. On peut
 * donc inventer ses propres fusées avec les seules pièces, et n'étudier un modèle que pour l'avoir tout prêt.
 * <p>
 * Rien n'est tiré au sort : une étude réussit toujours, dès que tout ce qu'elle demande est réuni.
 */
public final class EngineeringStudies {
    /** Rubriques de l'établi. */
    public enum Category {
        ENGINES,
        TANKS,
        PAYLOADS,
        ROCKETS,
        /** Les plans d'archive trouvés dans les centres spatiaux, à valider (rétro-ingénierie). */
        ARCHIVES;

        public String nameKey() {
            return "engineering.orbitalis.category." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Un matériau du prototype : l'objet, et combien il en faut. */
    public record Cost(Supplier<? extends Item> item, int count) {
    }

    /**
     * Une étude.
     *
     * @param id       identifiant : celui de l'objet pour une pièce, {@code rocket_<modèle>} pour un modèle de lanceur
     * @param category rubrique
     * @param icon     objet qui la représente
     * @param research technologie à maîtriser, ou {@code null}
     * @param requires études à avoir faites d'abord (les pièces d'un modèle de lanceur)
     * @param costs    matériaux du prototype
     * @param technologies pour un plan d'archive : les technologies des moteurs et des réservoirs du modèle
     */
    public record Study(String id, Category category, Supplier<? extends Item> icon, String research, List<String> requires, List<Cost> costs,
            List<String> technologies) {
        /** Nom affiché : celui de la pièce, celui du modèle de lanceur, ou celui du plan d'archive. */
        public Component name() {
            if (id.startsWith(ARCHIVE_PREFIX)) {
                return Component.translatable("engineering.orbitalis.archive.name",
                        Component.translatable("design.orbitalis.rocket." + id.substring(ARCHIVE_PREFIX.length())));
            }
            return id.startsWith(ROCKET_PREFIX) ? Component.translatable("design.orbitalis.rocket." + id.substring(ROCKET_PREFIX.length()))
                    : new ItemStack(icon.get()).getHoverName();
        }
    }

    /** Pourquoi une étude ne peut pas se faire. */
    public enum Problem {
        NONE,
        UNKNOWN,
        ALREADY_DONE,
        MISSING_RESEARCH,
        MISSING_STUDY,
        MISSING_MATERIALS,
        MISSING_ARCHIVE
    }

    public static final String ROCKET_PREFIX = "rocket_";
    /** Préfixe des études d'archive : le plan d'archive d'un modèle du catalogue. */
    public static final String ARCHIVE_PREFIX = "archive_";
    /** Préfixe sous lequel les études faites sont enregistrées avec la recherche de l'agence. */
    public static final String SAVED_PREFIX = "study/";

    private static List<Study> studies;

    private EngineeringStudies() {
    }

    private static Cost cost(Supplier<? extends Item> item, int count) {
        return new Cost(item, count);
    }

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    /** Étude d'une pièce : sa technologie est celle que la pièce déclare elle-même. */
    private static void part(List<Study> list, Category category, Supplier<? extends Item> item, Cost... costs) {
        RocketParts.Part part = RocketParts.get(new ItemStack(item.get()));
        list.add(new Study(idOf(item.get()), category, item, part == null ? null : part.research(), List.of(), List.of(costs), List.of()));
    }

    /** Étude d'un modèle de lanceur : elle demande les études des moteurs et des réservoirs qu'il emploie. */
    private static void rocket(List<Study> list, String template, Supplier<? extends Item> icon, Cost... costs) {
        Set<String> requires = new LinkedHashSet<>();
        for (RocketCatalog.Template candidate : RocketCatalog.all()) {
            if (candidate.id().equals(template)) {
                for (RocketCatalog.StageSpec stage : candidate.stages()) {
                    requires.add(idOf(stage.engine().get()));
                    if (stage.tank() != null) {
                        requires.add(idOf(stage.tank().get()));
                    }
                }
            }
        }
        list.add(new Study(ROCKET_PREFIX + template, Category.ROCKETS, icon, null, List.copyOf(requires), List.of(costs), List.of()));
    }

    /** Toutes les études, rubrique par rubrique, de la plus simple à la plus avancée. */
    public static List<Study> all() {
        if (studies == null) {
            List<Study> list = new ArrayList<>();
            Supplier<Item> copper = () -> Items.COPPER_INGOT;
            Supplier<Item> redstone = () -> Items.REDSTONE;
            Supplier<Item> paper = () -> Items.PAPER;
            Supplier<Item> glass = () -> Items.GLASS;
            Supplier<Item> gold = () -> Items.GOLD_INGOT;

            part(list, Category.ENGINES, ModItems.SOLID_MOTOR, cost(ModItems.STEEL_PLATE, 4), cost(() -> Items.GUNPOWDER, 8));
            part(list, Category.ENGINES, ModItems.PRESSURE_FED_ENGINE, cost(ModItems.STEEL_PLATE, 6), cost(copper, 4));
            part(list, Category.ENGINES, ModItems.KEROLOX_ENGINE, cost(ModItems.STEEL_PLATE, 8), cost(ModItems.ALUMINUM_PLATE, 4), cost(copper, 8));
            part(list, Category.ENGINES, ModItems.STAGED_COMBUSTION_ENGINE, cost(ModItems.TITANIUM_PLATE, 8), cost(ModItems.STEEL_PLATE, 8), cost(copper, 12));
            part(list, Category.ENGINES, ModItems.METHALOX_ENGINE, cost(ModItems.TITANIUM_PLATE, 10), cost(ModItems.STEEL_PLATE, 12), cost(copper, 12));
            part(list, Category.ENGINES, ModItems.HYDROLOX_ENGINE, cost(ModItems.TITANIUM_PLATE, 6), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 6), cost(copper, 8));
            part(list, Category.ENGINES, ModItems.SOLID_BOOSTER, cost(ModItems.STEEL_PLATE, 12), cost(() -> Items.GUNPOWDER, 16));
            part(list, Category.ENGINES, ModItems.HYPERGOLIC_ENGINE, cost(ModItems.TITANIUM_PLATE, 4), cost(copper, 6));
            part(list, Category.ENGINES, ModItems.VACUUM_ENGINE, cost(ModItems.TITANIUM_PLATE, 8), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 4), cost(gold, 4));
            part(list, Category.ENGINES, ModItems.NUCLEAR_THERMAL_ENGINE, cost(ModItems.TITANIUM_PLATE, 12), cost(ModItems.REACTOR_CORE, 1),
                    cost(ModItems.INSULATION_BLANKET, 6));
            part(list, Category.ENGINES, ModItems.FUSION_ENGINE, cost(ModItems.TITANIUM_PLATE, 20), cost(ModItems.REACTOR_CORE, 2),
                    cost(ModItems.CIRCUIT_BOARD, 8));
            part(list, Category.ENGINES, ModItems.ION_THRUSTER, cost(ModItems.TITANIUM_PLATE, 6), cost(ModItems.CIRCUIT_BOARD, 4), cost(gold, 4));
            part(list, Category.ENGINES, ModItems.SOLAR_SAIL, cost(ModItems.ALUMINUM_LITHIUM_PLATE, 6), cost(ModItems.INSULATION_BLANKET, 8));

            part(list, Category.TANKS, ModItems.STEEL_TANK, cost(ModItems.STEEL_PLATE, 8));
            part(list, Category.TANKS, ModItems.ALUMINUM_TANK, cost(ModItems.ALUMINUM_PLATE, 8));
            part(list, Category.TANKS, ModItems.ALUMINUM_LITHIUM_TANK, cost(ModItems.ALUMINUM_LITHIUM_PLATE, 8));
            part(list, Category.TANKS, ModItems.COMPOSITE_TANK, cost(ModItems.TITANIUM_PLATE, 6), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 4));
            part(list, Category.TANKS, ModItems.TITANIUM_TANK, cost(ModItems.TITANIUM_PLATE, 8));
            part(list, Category.TANKS, ModItems.STAINLESS_TANK, cost(ModItems.STEEL_PLATE, 16));
            part(list, Category.TANKS, ModItems.HYDROGEN_TANK, cost(ModItems.ALUMINUM_LITHIUM_PLATE, 8), cost(ModItems.INSULATION_BLANKET, 4));

            part(list, Category.PAYLOADS, ModItems.SOUNDING_PAYLOAD, cost(ModItems.STEEL_PLATE, 2), cost(redstone, 4));
            for (SatelliteType satellite : SatelliteType.values()) {
                Supplier<Item> item = satellite::item;
                switch (satellite) {
                    case EXPERIMENTAL -> part(list, Category.PAYLOADS, item, cost(ModItems.ALUMINUM_PLATE, 2), cost(redstone, 4));
                    case WEATHER -> part(list, Category.PAYLOADS, item, cost(ModItems.ALUMINUM_PLATE, 4), cost(redstone, 8), cost(glass, 2));
                    case COMMUNICATION -> part(list, Category.PAYLOADS, item, cost(ModItems.ALUMINUM_PLATE, 6), cost(redstone, 12), cost(gold, 4));
                    case OBSERVATION -> part(list, Category.PAYLOADS, item, cost(ModItems.ALUMINUM_PLATE, 6), cost(redstone, 8), cost(glass, 6));
                    case NAVIGATION -> part(list, Category.PAYLOADS, item, cost(ModItems.ALUMINUM_PLATE, 6), cost(redstone, 12), cost(gold, 2));
                    case TELESCOPE -> part(list, Category.PAYLOADS, item, cost(ModItems.TITANIUM_PLATE, 8), cost(glass, 12), cost(gold, 4));
                    case LUNAR_GATEWAY -> part(list, Category.PAYLOADS, item, cost(ModItems.TITANIUM_PLATE, 12), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 8),
                            cost(gold, 4));
                }
            }
            part(list, Category.PAYLOADS, ModItems.CREW_CAPSULE, cost(ModItems.ALUMINUM_PLATE, 12), cost(glass, 4));
            part(list, Category.PAYLOADS, ModItems.CREW_CAPSULE_MK2, cost(ModItems.ALUMINUM_LITHIUM_PLATE, 12), cost(glass, 8));
            part(list, Category.PAYLOADS, ModItems.CREW_CAPSULE_MK3, cost(ModItems.TITANIUM_PLATE, 16), cost(glass, 8));
            part(list, Category.PAYLOADS, ModItems.STATION_CORE, cost(ModItems.ALUMINUM_PLATE, 16), cost(ModItems.TITANIUM_PLATE, 4));
            part(list, Category.PAYLOADS, ModItems.LUNAR_MODULE, cost(ModItems.ALUMINUM_LITHIUM_PLATE, 12), cost(ModItems.TITANIUM_PLATE, 6));
            part(list, Category.PAYLOADS, ModItems.MARS_LANDER, cost(ModItems.TITANIUM_PLATE, 16), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 8));
            part(list, Category.PAYLOADS, ModItems.TITAN_LANDER, cost(ModItems.TITANIUM_PLATE, 20), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 10));
            part(list, Category.PAYLOADS, ModItems.EUROPA_LANDER, cost(ModItems.TITANIUM_PLATE, 22), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 10));
            part(list, Category.PAYLOADS, ModItems.LUNAR_PROBE, cost(ModItems.ALUMINUM_PLATE, 6), cost(gold, 2));
            part(list, Category.PAYLOADS, ModItems.MARS_PROBE, cost(ModItems.ALUMINUM_LITHIUM_PLATE, 6), cost(gold, 4));
            part(list, Category.PAYLOADS, ModItems.VENUS_ORBITER, cost(ModItems.TITANIUM_PLATE, 4), cost(gold, 2));
            part(list, Category.PAYLOADS, ModItems.VENUS_LANDER, cost(ModItems.TITANIUM_PLATE, 5), cost(gold, 2));
            part(list, Category.PAYLOADS, ModItems.MERCURY_ORBITER, cost(ModItems.TITANIUM_PLATE, 6), cost(gold, 3));
            part(list, Category.PAYLOADS, ModItems.JUPITER_ORBITER, cost(ModItems.TITANIUM_PLATE, 7), cost(gold, 3));
            part(list, Category.PAYLOADS, ModItems.JUPITER_ENTRY_PROBE, cost(ModItems.TITANIUM_PLATE, 8), cost(gold, 4));
            part(list, Category.PAYLOADS, ModItems.SATURN_ORBITER, cost(ModItems.TITANIUM_PLATE, 9), cost(gold, 4));
            part(list, Category.PAYLOADS, ModItems.TITAN_ENTRY_PROBE, cost(ModItems.TITANIUM_PLATE, 10), cost(gold, 5));
            part(list, Category.PAYLOADS, ModItems.EUROPA_ORBITER, cost(ModItems.TITANIUM_PLATE, 11), cost(gold, 5));
            part(list, Category.PAYLOADS, ModItems.LUNAR_ROBOTIC_LANDER, cost(ModItems.TITANIUM_PLATE, 6), cost(ModItems.ALUMINUM_PLATE, 6));
            part(list, Category.PAYLOADS, ModItems.MARS_ROBOTIC_LANDER, cost(ModItems.TITANIUM_PLATE, 10), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 6));
            part(list, Category.PAYLOADS, ModItems.LUNAR_SAMPLE_RETURN, cost(ModItems.TITANIUM_PLATE, 8), cost(gold, 2));
            part(list, Category.PAYLOADS, ModItems.MARS_SAMPLE_RETURN, cost(ModItems.TITANIUM_PLATE, 12), cost(gold, 4));

            rocket(list, "sounding", ModItems.SOLID_MOTOR, cost(paper, 2));
            rocket(list, "liquid_test", ModItems.PRESSURE_FED_ENGINE, cost(paper, 2), cost(ModItems.STEEL_PLATE, 4));
            rocket(list, "crewed_suborbital", ModItems.CREW_CAPSULE, cost(paper, 3), cost(ModItems.STEEL_PLATE, 6));
            rocket(list, "orbital", ModItems.KEROLOX_ENGINE, cost(paper, 4), cost(ModItems.ALUMINUM_PLATE, 8));
            rocket(list, "crewed_orbital", ModItems.CREW_CAPSULE, cost(paper, 4), cost(ModItems.ALUMINUM_LITHIUM_PLATE, 8));
            rocket(list, "heavy", ModItems.STAGED_COMBUSTION_ENGINE, cost(paper, 6), cost(ModItems.TITANIUM_PLATE, 8));
            rocket(list, "mars", ModItems.MARS_LANDER, cost(paper, 8), cost(ModItems.TITANIUM_PLATE, 12));
            // La rétro-ingénierie d'un plan d'archive : la moitié du prototype du modèle, et les technologies de ses pièces.
            for (RocketCatalog.Template template : RocketCatalog.all()) {
                Study model = null;
                for (Study study : list) {
                    if (study.id().equals(ROCKET_PREFIX + template.id())) {
                        model = study;
                    }
                }
                List<Cost> half = new ArrayList<>();
                if (model != null) {
                    for (Cost cost : model.costs()) {
                        half.add(cost(cost.item(), (cost.count() + 1) / 2));
                    }
                }
                Set<String> technologies = new LinkedHashSet<>();
                for (RocketCatalog.StageSpec stage : template.stages()) {
                    for (Supplier<? extends Item> item : stage.tank() == null ? List.<Supplier<? extends Item>>of(stage.engine())
                            : List.of(stage.engine(), stage.tank())) {
                        RocketParts.Part part = RocketParts.get(new ItemStack(item.get()));
                        if (part != null && part.research() != null && !part.research().isEmpty()) {
                            technologies.add(part.research());
                        }
                    }
                }
                list.add(new Study(ARCHIVE_PREFIX + template.id(), Category.ARCHIVES, ModItems.ROCKET_BLUEPRINT, null, List.of(), List.copyOf(half),
                        List.copyOf(technologies)));
            }
            studies = List.copyOf(list);
        }
        return studies;
    }

    /** L'étude de cet identifiant, ou {@code null}. */
    public static Study get(String id) {
        for (Study study : all()) {
            if (study.id().equals(id)) {
                return study;
            }
        }
        return null;
    }

    /**
     * Vrai si l'agence du joueur a fait cette étude. En mode créatif, tout est acquis. Côté serveur, la réponse vient
     * de la sauvegarde ; côté client, de la copie envoyée par le serveur.
     */
    public static boolean isDone(Player player, String id) {
        return player.getAbilities().instabuild || isRecorded(player, id);
    }

    /** Vrai si l'étude est réellement enregistrée pour l'agence du joueur, mode créatif ou non. */
    public static boolean isRecorded(Player player, String id) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ResearchManager.of(serverPlayer).hasMilestone(SAVED_PREFIX + id);
        }
        // Les études arrivent mêlées aux technologies : ce ne sont pas des technologies, on les cherche telles quelles.
        return ResearchManager.clientView.get().unlocked().contains(SAVED_PREFIX + id);
    }

    /** Première pièce, parmi celles-ci, que l'agence du joueur n'a pas étudiée ; {@code null} si toutes le sont. */
    public static Study firstUnstudied(Player player, List<ItemStack> parts) {
        for (ItemStack stack : parts) {
            Study study = stack.isEmpty() ? null : get(idOf(stack.getItem()));
            if (study != null && !isDone(player, study.id())) {
                return study;
            }
        }
        return null;
    }

    /** L'étude d'un modèle de lanceur du catalogue. */
    public static Study ofTemplate(RocketCatalog.Template template) {
        return get(ROCKET_PREFIX + template.id());
    }

    /** Nombre d'exemplaires d'un objet dans l'inventaire d'un joueur. */
    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            total += stack.is(item) ? stack.getCount() : 0;
        }
        return total;
    }

    /** Vrai pour la validation d'un plan d'archive. */
    public static boolean isArchive(Study study) {
        return study != null && study.category() == Category.ARCHIVES;
    }

    /** Le premier plan d'archive non validé de ce modèle que le joueur porte, ou un objet vide. */
    public static ItemStack heldArchive(Player player, String template) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.ROCKET_BLUEPRINT.get()) && template.equals(stack.get(com.orbitalis.core.registry.ModDataComponents.ARCHIVE_PLAN.get()))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Nombre de plans d'archive non validés que porte le joueur, tous modèles confondus. */
    public static int heldArchives(Player player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (com.orbitalis.phase1.rocket.RocketBlueprintItem.isArchive(inventory.getItem(slot))) {
                count++;
            }
        }
        return count;
    }

    /** Points de recherche que rapporte la validation d'un plan d'archive : d'autant plus que le modèle est avancé. */
    public static int archivePoints(Study study) {
        String template = study.id().substring(ARCHIVE_PREFIX.length());
        List<RocketCatalog.Template> catalog = RocketCatalog.all();
        for (int k = 0; k < catalog.size(); k++) {
            if (catalog.get(k).id().equals(template)) {
                return 20 + 20 * k;
            }
        }
        return 20;
    }

    /** Ce qui empêche la validation d'un plan d'archive. */
    private static Problem checkArchive(Player player, Study study) {
        ItemStack plan = heldArchive(player, study.id().substring(ARCHIVE_PREFIX.length()));
        if (plan.isEmpty()) {
            return Problem.MISSING_ARCHIVE;
        }
        if (player.getAbilities().instabuild) {
            return Problem.NONE;
        }
        for (String technology : study.technologies()) {
            if (!ResearchManager.isUnlocked(player, technology)) {
                return Problem.MISSING_RESEARCH;
            }
        }
        if (com.orbitalis.phase1.rocket.DesignComputerBlock.lockedResearch(player, com.orbitalis.phase1.rocket.RocketBlueprintItem.parts(plan)) != null) {
            return Problem.MISSING_RESEARCH;
        }
        for (Cost cost : study.costs()) {
            if (count(player.getInventory(), cost.item().get()) < cost.count()) {
                return Problem.MISSING_MATERIALS;
            }
        }
        return Problem.NONE;
    }

    private static void take(ServerPlayer player, List<Cost> costs) {
        if (player.getAbilities().instabuild) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (Cost cost : costs) {
            int left = cost.count();
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(cost.item().get())) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
    }

    /** Ce qui empêche le joueur de faire cette étude maintenant ; {@link Problem#NONE} si rien. */
    public static Problem check(Player player, Study study) {
        if (study == null) {
            return Problem.UNKNOWN;
        }
        if (isArchive(study)) {
            return checkArchive(player, study);
        }
        if (isRecorded(player, study.id())) {
            return Problem.ALREADY_DONE;
        }
        if (study.research() != null && !study.research().isEmpty() && !player.getAbilities().instabuild
                && !ResearchManager.isUnlocked(player, study.research())) {
            return Problem.MISSING_RESEARCH;
        }
        for (String required : study.requires()) {
            if (!isDone(player, required)) {
                return Problem.MISSING_STUDY;
            }
        }
        if (!player.getAbilities().instabuild) {
            for (Cost cost : study.costs()) {
                if (count(player.getInventory(), cost.item().get()) < cost.count()) {
                    return Problem.MISSING_MATERIALS;
                }
            }
        }
        return Problem.NONE;
    }

    /**
     * Fait une étude : les matériaux du prototype sont pris au joueur, et l'étude est acquise à son agence.
     *
     * @return ce qui s'y oppose, ou {@link Problem#NONE} si elle est faite
     */
    public static Problem complete(ServerPlayer player, String id) {
        Study study = get(id);
        if (isArchive(study)) {
            // La rétro-ingénierie : le plan est validé, le modèle est étudié, la recherche y gagne.
            Problem problem = checkArchive(player, study);
            if (problem != Problem.NONE) {
                return problem;
            }
            String template = id.substring(ARCHIVE_PREFIX.length());
            ItemStack plan = heldArchive(player, template);
            take(player, study.costs());
            plan.remove(com.orbitalis.core.registry.ModDataComponents.ARCHIVE_PLAN.get());
            plan.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("item.orbitalis.rocket_blueprint.named",
                    Component.translatable("design.orbitalis.rocket." + template)));
            var research = ResearchManager.of(player);
            research.completeMilestone(SAVED_PREFIX + ROCKET_PREFIX + template);
            research.addPoints(archivePoints(study));
            ResearchManager.changed(player);
            return Problem.NONE;
        }
        if (study != null && ResearchManager.of(player).hasMilestone(SAVED_PREFIX + id)) {
            return Problem.ALREADY_DONE;
        }
        Problem problem = check(player, study);
        if (problem != Problem.NONE) {
            return problem;
        }
        if (!player.getAbilities().instabuild) {
            Inventory inventory = player.getInventory();
            for (Cost cost : study.costs()) {
                int left = cost.count();
                for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.is(cost.item().get())) {
                        int taken = Math.min(left, stack.getCount());
                        stack.shrink(taken);
                        left -= taken;
                    }
                }
            }
        }
        ResearchManager.of(player).completeMilestone(SAVED_PREFIX + id);
        // Bancs d'essai : le prototype d'un moteur y est allumé, et l'essai rapporte des points de recherche.
        if (study.category() == Category.ENGINES && ResearchManager.isUnlocked(player, TEST_STANDS)) {
            ResearchManager.of(player).addPoints(TEST_POINTS);
            player.sendSystemMessage(Component.translatable("engineer.orbitalis.test_fired", TEST_POINTS).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        ResearchManager.changed(player);
        return Problem.NONE;
    }

    /** La technologie des bancs d'essai, et ce que rapporte l'essai d'un moteur. */
    public static final String TEST_STANDS = "test_stands";
    public static final int TEST_POINTS = 15;
}
