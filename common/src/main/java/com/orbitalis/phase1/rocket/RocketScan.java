package com.orbitalis.phase1.rocket;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Lecture d'une fusée bâtie librement, sans plan, au-dessus d'une table de lancement.
 * <p>
 * Le joueur empile ce qu'il veut dans l'axe de la table : un moteur, ses réservoirs, un interétage, un autre moteur,
 * d'autres réservoirs... puis ce qui coiffe la fusée : une baie de charge utile et sa pointe, ou une cabine et sa
 * tour de sauvetage. La table lit cette colonne de bas en haut et en déduit la fusée : chaque moteur fait un étage,
 * les réservoirs posés au-dessus de lui sont les siens (plus il y en a, plus l'étage emporte d'ergols), un interétage
 * commence l'étage suivant.
 * <p>
 * Rien n'est laissé au hasard : la même colonne donne toujours la même fusée, et la lecture dit à chaque bloc posé ce
 * que vaut la fusée en l'état, et ce qui lui manque encore.
 * <p>
 * Les règles, peu nombreuses :
 * <ul>
 * <li>un moteur à poudre porte des corps de moteur ; un moteur cryogénique, des réservoirs cryogéniques ; les autres,
 * des réservoirs d'une seule sorte par étage ;</li>
 * <li>un bloc de large jusqu'à 60 tonnes ; au-delà, la fusée doit faire trois blocs de large : il suffit alors de
 * poser l'axe, la table montre la couronne de panneaux à ajouter, et les moteurs posés autour du premier comptent ;</li>
 * <li>trois étages au plus ;</li>
 * <li>la charge utile (satellite, capsule, vaisseau) se confie à la table, d'un clic droit.</li>
 * </ul>
 */
public final class RocketScan {
    /** Masse au décollage à partir de laquelle une fusée doit faire trois blocs de large, en kg (voir {@link RocketShape}). */
    private static final double WIDE_MASS = 60_000;
    /** Cases autour de l'axe, pour une fusée large. */
    private static final int[][] RING = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    /**
     * Ce que la table a lu.
     *
     * @param slots   pièces de la fusée reconnue, emplacement par emplacement ; {@code null} tant qu'elle n'est pas
     *                complète et cohérente
     * @param stats   ce que vaut la fusée en l'état, même inachevée ; {@code null} si rien ne se lit encore
     * @param message ce qu'il faut dire au joueur : ce qui manque, ce qui ne va pas, ou que la fusée est reconnue
     * @param height  nombre de blocs lus dans l'axe
     */
    public record Reading(NonNullList<ItemStack> slots, RocketStats stats, Component message, int height) {
    }

    /** Étage lu : son moteur, le nombre de moteurs, la sorte de ses réservoirs et le nombre de blocs qu'ils occupent. */
    private record Stage(Item engine, int engines, Item tank, int tankBlocks) {
    }

    private RocketScan() {
    }

    /** Vrai si ce bloc est un bloc de construction de fusée. */
    public static boolean isPart(Block block) {
        return block instanceof RocketPartBlock;
    }

    /** Moteur que représente un bloc moteur, ou {@code null}. */
    private static Item engineOf(Block block) {
        Item item = RocketPlan.pieceOf(block);
        return item != null && RocketParts.is(new ItemStack(item), RocketParts.Kind.ENGINE) ? item : null;
    }

    /** Propulsion de ce moteur. */
    private static Propellant propellantOf(Item engine) {
        return RocketParts.get(new ItemStack(engine)).engine().propellant();
    }

    /**
     * Ce que représente un bloc posé au-dessus de ce moteur, dans le corps de son étage, ou {@code null} s'il ne lui
     * convient pas : un réservoir pour un moteur à ergols liquides, le corps prévu par le plan pour un moteur qui
     * embarque les siens (corps de moteur à poudre, ou panneau de coque).
     */
    private static Item tankOf(Block block, Item engine) {
        if (propellantOf(engine).isPreloaded()) {
            return block == RocketPlan.bodyOf(engine) ? block.asItem() : null;
        }
        Item item = RocketPlan.pieceOf(block);
        return item != null && RocketParts.is(new ItemStack(item), RocketParts.Kind.TANK) ? item : null;
    }

    private static Component say(String key, Object... args) {
        return Component.translatable("free.orbitalis." + key, args);
    }

    /**
     * Lit la colonne posée au-dessus d'une table.
     *
     * @param trench sens du carneau du pas de tir (il oriente la couronne d'une fusée large)
     * @param cargo  charge utile confiée à la table, ou une pile vide
     * @return la lecture, ou {@code null} si rien n'est posé dans l'axe de la table
     */
    public static Reading read(Level level, BlockPos table, Direction trench, ItemStack cargo) {
        List<Block> column = new ArrayList<>();
        for (int y = 1; y <= 96; y++) {
            Block block = level.getBlockState(table.above(y)).getBlock();
            if (!isPart(block)) {
                break;
            }
            column.add(block);
        }
        if (column.isEmpty()) {
            return null;
        }
        // Fusée large : quelque chose est posé autour du premier bloc.
        boolean wide = false;
        for (int[] cell : RING) {
            wide |= isPart(level.getBlockState(table.offset(cell[0], 1, cell[1])).getBlock());
        }

        // --- Les étages, de bas en haut ---------------------------------------------------------
        List<Stage> stages = new ArrayList<>();
        int at = 0;
        while (true) {
            Item engine = engineOf(column.get(at));
            if (engine == null) {
                return new Reading(null, null, say("need_engine", at + 1), column.size());
            }
            // Les moteurs posés autour du premier, sur une fusée large, s'ajoutent à lui.
            int engines = 1;
            if (wide) {
                for (int[] cell : RING) {
                    engines += level.getBlockState(table.offset(cell[0], at + 1, cell[1])).is(column.get(at)) ? 1 : 0;
                }
            }
            at++;
            Propellant propellant = propellantOf(engine);
            Item tank = null;
            int tankBlocks = 0;
            // Une voile n'a pas de corps ; un propulseur ionique, un seul (son réservoir de xénon).
            int maxBlocks = propellant == Propellant.SUNLIGHT ? 0 : propellant == Propellant.XENON ? 1 : Integer.MAX_VALUE;
            while (at < column.size() && tankBlocks < maxBlocks) {
                Item here = tankOf(column.get(at), engine);
                if (here == null || (tank != null && here != tank)) {
                    break;
                }
                tank = here;
                tankBlocks++;
                at++;
            }
            if (tankBlocks == 0 && propellant != Propellant.SUNLIGHT) {
                Block body = RocketPlan.bodyOf(engine);
                Component wanted = !propellant.isPreloaded() ? say("need_tank", at + 1)
                        : say("need_body", at + 1, body.getName());
                return new Reading(null, stats(stages, cargo, wide), wanted, column.size());
            }
            stages.add(new Stage(engine, engines, tank, tankBlocks));
            if (at < column.size() && column.get(at) == ModBlocks.ROCKET_INTERSTAGE.get()) {
                if (stages.size() >= RocketDesign.MAX_STAGES) {
                    return new Reading(null, stats(stages, cargo, wide), say("too_many_stages", RocketDesign.MAX_STAGES), column.size());
                }
                at++;
                if (at >= column.size()) {
                    return new Reading(null, stats(stages, cargo, wide), say("next_engine"), column.size());
                }
                continue;
            }
            break;
        }

        // --- Ce qui coiffe la fusée -------------------------------------------------------------
        List<Block> top = column.subList(at, column.size());
        boolean crewed = top.contains(ModBlocks.CREW_CABIN.get());
        boolean bay = top.contains(ModBlocks.ROCKET_PAYLOAD_BAY.get());
        boolean closed = !top.isEmpty() && (top.get(top.size() - 1) == ModBlocks.ROCKET_NOSE.get() || top.get(top.size() - 1) == ModBlocks.ESCAPE_TOWER.get());
        RocketStats soFar = stats(stages, cargo, wide);
        if (!closed) {
            return new Reading(null, soFar, readout(soFar, say(top.isEmpty() ? "need_top" : crewed ? "need_tower" : "need_nose")), column.size());
        }
        if ((crewed || bay) && cargo.isEmpty()) {
            return new Reading(null, soFar, readout(soFar, say(crewed ? "need_crew_cargo" : "need_cargo")), column.size());
        }
        if (crewed != RocketParts.isCrewCabin(cargo) && !cargo.isEmpty()) {
            return new Reading(null, soFar, say(crewed ? "cargo_not_crew" : "cargo_needs_cabin"), column.size());
        }

        // --- La fusée que cela fait, et la vérification : son plan doit redonner exactement cette colonne -----
        RocketBlock.Size[] candidates = wide ? new RocketBlock.Size[] {RocketBlock.Size.LARGE}
                : new RocketBlock.Size[] {RocketBlock.Size.MEDIUM, RocketBlock.Size.SLIM};
        Component problem = null;
        for (RocketBlock.Size size : candidates) {
            NonNullList<ItemStack> slots = slots(stages, crewed || bay ? cargo : ItemStack.EMPTY, size);
            if (slots == null) {
                continue;
            }
            RocketDesign design = RocketDesign.fromSlots(slots::get);
            RocketStats stats = RocketStats.of(design);
            RocketShape shape = RocketShape.of(design);
            if (shape.size() != size) {
                if (problem == null) {
                    problem = say(wide ? "too_light" : stats.liftoffMass() >= WIDE_MASS ? "too_heavy" : "mismatch_size", (int) (stats.liftoffMass() / 1000));
                }
                continue;
            }
            List<Block> expected = new ArrayList<>();
            for (RocketPlan.Piece piece : RocketPlan.pieces(design, trench)) {
                if (piece.offset().getX() == 0 && piece.offset().getZ() == 0) {
                    expected.add(piece.block());
                }
            }
            if (expected.equals(column)) {
                if (!stats.isFlightReady()) {
                    return new Reading(null, stats, readout(stats, say("not_ready")), column.size());
                }
                return new Reading(slots, stats, readout(stats, say("recognized")), column.size());
            }
            // Le premier bloc qui diffère dit quoi corriger.
            for (int k = 0; k < Math.max(expected.size(), column.size()); k++) {
                Block wanted = k < expected.size() ? expected.get(k) : null;
                if (k >= column.size() || wanted != column.get(k)) {
                    problem = wanted == null ? say("extra_block", k + 1) : say("wrong_block", k + 1, wanted.getName());
                    break;
                }
            }
        }
        return new Reading(null, soFar, problem == null ? say("mismatch_size", 0) : problem, column.size());
    }

    /** Pièces de la fusée, pour un diamètre donné ; {@code null} si la colonne ne tombe pas juste à ce diamètre. */
    private static NonNullList<ItemStack> slots(List<Stage> stages, ItemStack cargo, RocketBlock.Size size) {
        NonNullList<ItemStack> slots = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        slots.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        if (!cargo.isEmpty()) {
            slots.set(RocketDesign.SLOT_PAYLOAD, cargo.copyWithCount(1));
        }
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            Propellant propellant = propellantOf(stage.engine());
            // Une fusée fine loge un réservoir sur deux blocs, une moyenne un par bloc, une large deux par bloc.
            int count = 0;
            if (propellant.isLowThrust()) {
                // Propulseur ionique ou voile : leur corps ne dit rien du nombre.
            } else if (size == RocketBlock.Size.SLIM) {
                if (stage.tankBlocks() % 2 != 0) {
                    return null;
                }
                count = stage.tankBlocks() / 2;
            } else {
                count = stage.tankBlocks() * (size == RocketBlock.Size.LARGE ? 2 : 1);
            }
            if (propellant.isLowThrust()) {
                // Propulseur ionique ou voile : ni réservoir, ni nombre qui dépende du corps.
                slots.set(RocketDesign.engineSlot(i), new ItemStack(stage.engine(), stage.engines()));
            } else if (propellant.isPreloaded()) {
                // Moteurs qui embarquent leurs ergols : le corps de l'étage dit combien il y en a.
                slots.set(RocketDesign.engineSlot(i), new ItemStack(stage.engine(), count));
            } else {
                slots.set(RocketDesign.engineSlot(i), new ItemStack(stage.engine(), stage.engines()));
                slots.set(RocketDesign.tankSlot(i), new ItemStack(stage.tank(), count));
            }
            if (i > 0) {
                slots.set(RocketDesign.interstageSlot(i), new ItemStack(ModItems.INTERSTAGE.get()));
            }
        }
        return slots;
    }

    /** Ce que vaut la fusée en l'état, même inachevée ; {@code null} si aucun étage n'est encore lisible. */
    private static RocketStats stats(List<Stage> stages, ItemStack cargo, boolean wide) {
        if (stages.isEmpty()) {
            return null;
        }
        NonNullList<ItemStack> slots = slots(stages, cargo, wide ? RocketBlock.Size.LARGE : RocketBlock.Size.MEDIUM);
        return slots == null ? null : RocketStats.of(RocketDesign.fromSlots(slots::get));
    }

    /** Le relevé affiché au joueur : ce que vaut la fusée, puis ce qu'il y a à dire. */
    private static Component readout(RocketStats stats, Component note) {
        if (stats == null) {
            return note;
        }
        return Component.translatable("free.orbitalis.readout", (int) stats.totalDeltaV(), Component.translatable(stats.reach().nameKey()),
                String.format(java.util.Locale.ROOT, "%.1f", stats.liftoffMass() / 1000.0), note);
    }
}
