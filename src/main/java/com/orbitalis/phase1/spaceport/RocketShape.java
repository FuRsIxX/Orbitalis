package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.rocket.Propellant;
import com.orbitalis.phase1.rocket.RocketDesign;
import com.orbitalis.phase1.rocket.RocketParts;
import com.orbitalis.phase1.rocket.RocketStats;

/**
 * Silhouette d'une fusée sur le pas de tir : la liste de ses tronçons, de bas en haut, et son diamètre.
 * <p>
 * Elle se déduit du plan : chaque étage donne une baie de propulsion surmontée de ses réservoirs (d'autant plus
 * hauts qu'ils sont nombreux), les étages sont séparés par un interétage, et le tout est coiffé du compartiment
 * de la charge utile et de la pointe. Le diamètre dépend de la masse au décollage : une fusée-sonde est un fin
 * crayon de cinq blocs, un petit lanceur fait un bloc de large, et un lanceur lourd trois blocs de large pour
 * une vingtaine à plusieurs dizaines de blocs de haut.
 *
 * @param size     diamètre de la fusée
 * @param parts    tronçons, du bas vers le haut
 * @param segments ce qu'est chaque tronçon dans le plan : son étage et son rôle (même rang que dans {@code parts})
 * @param boosters nombre de boosters latéraux accrochés au premier étage (0, ou une paire)
 */
public record RocketShape(RocketBlock.Size size, List<RocketBlock.Part> parts, List<Segment> segments, int boosters) {
    /** Plus petite hauteur d'un booster latéral, en blocs : sa tuyère, un corps de poudre, sa pointe. */
    public static final int MIN_BOOSTER_HEIGHT = 3;

    /** Hauteur des boosters latéraux, en blocs : les sept dixièmes du premier étage, trois au moins. */
    public int boosterHeight() {
        int first = 0;
        for (Segment segment : segments) {
            if (segment.stage() == 0 && (segment.role() == Role.BAY || segment.role() == Role.BODY)) {
                first++;
            }
        }
        return Math.max(MIN_BOOSTER_HEIGHT, Math.round(first * 0.7F));
    }

    /** Diamètre des boosters : un bloc à côté d'un lanceur large, fins sinon. */
    public RocketBlock.Size boosterSize() {
        return isWide() ? RocketBlock.Size.MEDIUM : RocketBlock.Size.SLIM;
    }

    /** Tronçon d'un booster à cette hauteur : sa tuyère en bas, sa pointe en haut, son corps de poudre entre les deux. */
    public static RocketBlock.Part boosterPart(int level, int height) {
        return level == 0 ? RocketBlock.Part.ENGINE_SOLID : level == height - 1 ? RocketBlock.Part.NOSE : RocketBlock.Part.TANK_SOLID;
    }

    /**
     * Places des blocs des boosters, par rapport à la table : de part et d'autre de la fusée, perpendiculairement à
     * l'axe de la tour (ils ne gênent ainsi ni les bras ni les passerelles), collés au corps. Chaque booster, de bas en
     * haut ({@link #boosterHeight()} blocs), le premier puis le second ; aucune place sans boosters.
     *
     * @param trench sens du carneau du pas de tir
     */
    public List<net.minecraft.core.BlockPos> boosterOffsets(net.minecraft.core.Direction trench) {
        List<net.minecraft.core.BlockPos> cells = new ArrayList<>();
        if (boosters <= 0) {
            return cells;
        }
        net.minecraft.core.Direction side = trench.getClockWise();
        int distance = isWide() ? 2 : 1;
        int height = boosterHeight();
        for (int sign = 1; sign >= -1; sign -= 2) {
            for (int k = 0; k < height; k++) {
                cells.add(new net.minecraft.core.BlockPos(side.getStepX() * distance * sign, k + 1, side.getStepZ() * distance * sign));
            }
        }
        return cells;
    }

    /** Rôle d'un tronçon dans le plan de la fusée. */
    public enum Role {
        /** Baie de propulsion d'un étage : ses moteurs (ou la voile d'un étage à voile). */
        BAY,
        /** Corps d'un étage : ses réservoirs, ou le corps de ses moteurs à poudre. */
        BODY,
        /** Interétage, sous l'étage qu'il porte. */
        INTERSTAGE,
        /** Ce qui coiffe la fusée : baie de charge utile, coiffe, pointe, cabine et tour de sauvetage. */
        TOP
    }

    /**
     * Un tronçon du plan.
     *
     * @param stage rang de son étage (0 pour le premier), ou -1 pour ce qui coiffe la fusée
     * @param role  son rôle
     */
    public record Segment(int stage, Role role) {
    }

    /** Masse au décollage à partir de laquelle la fusée passe au diamètre moyen, puis au grand diamètre, en kg. */
    private static final double MEDIUM_MASS = 5_000;
    private static final double LARGE_MASS = 60_000;
    /** Hauteur maximale des réservoirs d'un étage, en blocs. */
    private static final int MAX_TANK_BLOCKS = 24;

    /** Hauteur de la fusée, en blocs. */
    public int height() {
        return parts.size();
    }

    /** Vrai si la fusée fait trois blocs de large : chaque tronçon occupe alors aussi les huit cases qui l'entourent. */
    public boolean isWide() {
        return size == RocketBlock.Size.LARGE;
    }

    /** Rang du tronçon de la cabine habitée dans cette colonne, quel que soit son modèle ; -1 s'il n'y en a pas. */
    public static int cabinIndex(List<RocketBlock.Part> parts) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).isCabin()) {
                return i;
            }
        }
        return -1;
    }

    public static RocketShape of(RocketDesign design) {
        RocketStats stats = RocketStats.of(design);
        int boosters = design.boosters().is(ModItems.SOLID_BOOSTER.get()) && design.boosters().getCount() == RocketDesign.BOOSTER_PAIR
                ? RocketDesign.BOOSTER_PAIR : 0;
        RocketBlock.Size size = stats.liftoffMass() >= LARGE_MASS ? RocketBlock.Size.LARGE
                : stats.liftoffMass() >= MEDIUM_MASS ? RocketBlock.Size.MEDIUM : RocketBlock.Size.SLIM;
        // Une capsule habitée ne tient pas sur une fusée d'un demi-bloc de large : il lui faut au moins un bloc.
        boolean crewed = RocketParts.isCrewCabin(design.payload());
        if (crewed && size == RocketBlock.Size.SLIM) {
            size = RocketBlock.Size.MEDIUM;
        }
        // Hauteur de réservoir par pièce, en demi-blocs : une fusée fine est tout en longueur, une fusée large
        // loge deux réservoirs par bloc de hauteur.
        int halfBlocksPerTank = switch (size) {
            case SLIM -> 4;
            case MEDIUM -> 2;
            case LARGE -> 1;
        };

        List<RocketBlock.Part> parts = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();
        for (int s = 0; s < design.stages().size(); s++) {
            RocketDesign.Stage stage = design.stages().get(s);
            RocketParts.Part engine = RocketParts.get(stage.engines());
            if (engine == null) {
                continue;
            }
            if (!parts.isEmpty()) {
                add(parts, segments, RocketBlock.Part.INTERSTAGE, s, Role.INTERSTAGE);
            }
            Propellant propellant = engine.engine().propellant();
            if (propellant == Propellant.SUNLIGHT) {
                // La voile repliée dans son étui : un seul tronçon blanc, qui tient lieu de baie.
                add(parts, segments, RocketBlock.Part.TANK_WHITE, s, Role.BAY);
                continue;
            }
            if (propellant == Propellant.XENON) {
                // Propulseur ionique : sa baie, et un petit corps blanc où loge son réservoir de xénon.
                add(parts, segments, RocketBlock.Part.ENGINE_PRESSURE, s, Role.BAY);
                add(parts, segments, RocketBlock.Part.TANK_WHITE, s, Role.BODY);
                continue;
            }
            add(parts, segments, enginePart(stage.engines(), propellant), s, Role.BAY);

            // Moteurs à poudre : le corps du moteur tient lieu de réservoir.
            int count = propellant.isSolid() ? stage.engines().getCount() : stage.tanks().getCount();
            int blocks = Math.clamp((count * halfBlocksPerTank + 1) / 2, 1, MAX_TANK_BLOCKS);
            // Les ergols stockables du moteur hypergolique sont dans des réservoirs ordinaires, pas dans un corps de booster.
            RocketBlock.Part body = propellant.isSolid()
                    ? (stage.engines().is(ModItems.HYPERGOLIC_ENGINE.get()) ? RocketBlock.Part.TANK_WHITE : RocketBlock.Part.TANK_SOLID)
                    : propellant == Propellant.HYDROLOX || propellant == Propellant.HYDROGEN || propellant == Propellant.FUSION
                            ? RocketBlock.Part.TANK_ORANGE : tankPart(stage.tanks());
            for (int k = 0; k < blocks; k++) {
                add(parts, segments, body, s, Role.BODY);
            }
        }

        // Fusée habitée : pas de coiffe, mais la capsule elle-même (deux blocs) coiffée de sa tour de sauvetage.
        // Sur un gros lanceur, un adaptateur fait la jonction entre le corps et la capsule, plus étroite.
        if (crewed) {
            if (size == RocketBlock.Size.LARGE) {
                add(parts, segments, RocketBlock.Part.NOSE_LOWER, -1, Role.TOP);
            }
            int variant = RocketParts.cabinVariant(design.payload());
            add(parts, segments, variant == 2 ? RocketBlock.Part.CAPSULE_MK3 : variant == 1 ? RocketBlock.Part.CAPSULE_MK2 : RocketBlock.Part.CAPSULE,
                    -1, Role.TOP);
            add(parts, segments, RocketBlock.Part.CAPSULE_UPPER, -1, Role.TOP);
            add(parts, segments, RocketBlock.Part.ESCAPE_TOWER, -1, Role.TOP);
            return new RocketShape(size, List.copyOf(parts), List.copyOf(segments), boosters);
        }
        // Coiffe : le compartiment de la charge utile, plus haut sur une grosse fusée, puis la pointe.
        if (!design.payload().isEmpty()) {
            for (int k = 0; k <= size.ordinal(); k++) {
                add(parts, segments, RocketBlock.Part.PAYLOAD, -1, Role.TOP);
            }
        }
        if (size == RocketBlock.Size.LARGE) {
            add(parts, segments, RocketBlock.Part.NOSE_LOWER, -1, Role.TOP);
        }
        add(parts, segments, RocketBlock.Part.NOSE, -1, Role.TOP);
        return new RocketShape(size, List.copyOf(parts), List.copyOf(segments), boosters);
    }

    private static void add(List<RocketBlock.Part> parts, List<Segment> segments, RocketBlock.Part part, int stage, Role role) {
        parts.add(part);
        segments.add(new Segment(stage, role));
    }

    /** Aspect de la baie de propulsion : chaque famille de moteurs a la sienne. */
    private static RocketBlock.Part enginePart(ItemStack engines, Propellant propellant) {
        if (propellant.isSolid()) {
            return engines.is(ModItems.HYPERGOLIC_ENGINE.get()) ? RocketBlock.Part.ENGINE_PRESSURE : RocketBlock.Part.ENGINE_SOLID;
        }
        if (propellant == Propellant.HYDROLOX || propellant == Propellant.HYDROGEN || propellant == Propellant.FUSION) {
            return RocketBlock.Part.ENGINE_HYDROLOX;
        }
        return engines.is(ModItems.PRESSURE_FED_ENGINE.get()) ? RocketBlock.Part.ENGINE_PRESSURE : RocketBlock.Part.ENGINE;
    }

    /** Aspect des réservoirs d'ergols stockables : acier nu, aluminium peint en blanc, ou aluminium-lithium à bande bleue. */
    private static RocketBlock.Part tankPart(ItemStack tanks) {
        if (tanks.is(ModItems.COMPOSITE_TANK.get())) {
            return RocketBlock.Part.TANK_COMPOSITE;
        }
        if (tanks.is(ModItems.STEEL_TANK.get()) || tanks.is(ModItems.STAINLESS_TANK.get())) {
            return RocketBlock.Part.TANK_STEEL;
        }
        return tanks.is(ModItems.ALUMINUM_LITHIUM_TANK.get()) || tanks.is(ModItems.TITANIUM_TANK.get()) ? RocketBlock.Part.TANK_ALLOY
                : RocketBlock.Part.TANK_WHITE;
    }

}
