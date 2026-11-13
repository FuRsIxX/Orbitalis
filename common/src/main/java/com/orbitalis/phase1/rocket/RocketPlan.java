package com.orbitalis.phase1.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Plan de construction d'une fusée : la liste de tous les blocs à poser, un à un, au-dessus de la table de lancement.
 * <p>
 * Il suit la silhouette de la fusée ({@link RocketShape}) tronçon par tronçon. Chaque pièce du plan (moteur, réservoir,
 * interétage, pointe) a son bloc, et se pose elle-même sur le fantôme de ce bloc : un moteur à turbopompes pose sa
 * baie, un réservoir en aluminium son tronçon de réservoir. Chaque bloc dit ce qu'il coûte : la baie d'un étage à
 * trois moteurs en prend trois, un tronçon de réservoir d'une fusée large en prend deux, un tronçon d'une fusée fine
 * (où un réservoir s'étend sur deux blocs) un sur deux. Le reste (coque, chemin de câbles, ordinateur de bord,
 * coiffe, baie de charge utile, cabine) est fait de blocs que l'on fabrique et pose comme d'autres.
 * <p>
 * Un lanceur lourd fait trois blocs de large : autour de chaque bloc central vient alors une couronne de huit blocs,
 * faite de panneaux de coque, d'un chemin de câbles du côté de la tour, des moteurs en plus du premier dans une baie
 * de propulsion, et de l'ordinateur de bord au sommet du dernier étage. La cabine, plus étroite, n'a pas de couronne.
 */
public final class RocketPlan {
    /**
     * Un bloc du plan.
     *
     * @param offset position par rapport à la table de lancement
     * @param block  le bloc attendu
     * @param source ce qui le pose : la pièce de fusée qu'il représente, ou son propre objet
     * @param count  combien il en coûte ; 0 pour le second bloc d'un réservoir qui s'étend sur deux (il faut alors
     *               avoir la pièce en main, sans qu'elle soit prise)
     */
    public record Piece(BlockPos offset, Block block, Item source, int count) {
    }

    /** Cases de la couronne, autour du bloc central : la première est du côté de la tour pour un carneau vers l'est. */
    private static final int[][] RING = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private static Map<Item, Block> blocks;
    private static Map<Block, Item> pieces;

    private RocketPlan() {
    }

    /** Les pièces de fusée qui ont leur bloc, et ce bloc. Les objets n'existent pas au chargement de la classe. */
    private static Map<Item, Block> table() {
        if (blocks == null) {
            Map<Item, Block> map = new LinkedHashMap<>();
            map.put(ModItems.SOLID_MOTOR.get(), ModBlocks.ROCKET_ENGINE_SOLID.get());
            map.put(ModItems.SOLID_BOOSTER.get(), ModBlocks.ROCKET_ENGINE_BOOSTER.get());
            map.put(ModItems.PRESSURE_FED_ENGINE.get(), ModBlocks.ROCKET_ENGINE_PRESSURE.get());
            map.put(ModItems.HYPERGOLIC_ENGINE.get(), ModBlocks.ROCKET_ENGINE_HYPERGOLIC.get());
            map.put(ModItems.KEROLOX_ENGINE.get(), ModBlocks.ROCKET_ENGINE_KEROLOX.get());
            map.put(ModItems.STAGED_COMBUSTION_ENGINE.get(), ModBlocks.ROCKET_ENGINE_STAGED.get());
            map.put(ModItems.METHALOX_ENGINE.get(), ModBlocks.ROCKET_ENGINE_METHALOX.get());
            map.put(ModItems.HYDROLOX_ENGINE.get(), ModBlocks.ROCKET_ENGINE_HYDROLOX.get());
            map.put(ModItems.VACUUM_ENGINE.get(), ModBlocks.ROCKET_ENGINE_VACUUM.get());
            map.put(ModItems.NUCLEAR_THERMAL_ENGINE.get(), ModBlocks.ROCKET_ENGINE_NUCLEAR.get());
            map.put(ModItems.FUSION_ENGINE.get(), ModBlocks.ROCKET_ENGINE_FUSION.get());
            map.put(ModItems.ION_THRUSTER.get(), ModBlocks.ROCKET_ION_THRUSTER.get());
            map.put(ModItems.SOLAR_SAIL.get(), ModBlocks.ROCKET_SOLAR_SAIL.get());
            map.put(ModItems.STEEL_TANK.get(), ModBlocks.ROCKET_TANK_STEEL.get());
            map.put(ModItems.ALUMINUM_TANK.get(), ModBlocks.ROCKET_TANK_ALUMINUM.get());
            map.put(ModItems.ALUMINUM_LITHIUM_TANK.get(), ModBlocks.ROCKET_TANK_ALLOY.get());
            map.put(ModItems.COMPOSITE_TANK.get(), ModBlocks.ROCKET_TANK_COMPOSITE.get());
            map.put(ModItems.TITANIUM_TANK.get(), ModBlocks.ROCKET_TANK_TITANIUM.get());
            map.put(ModItems.STAINLESS_TANK.get(), ModBlocks.ROCKET_TANK_STAINLESS.get());
            map.put(ModItems.HYDROGEN_TANK.get(), ModBlocks.ROCKET_TANK_CRYO.get());
            map.put(ModItems.INTERSTAGE.get(), ModBlocks.ROCKET_INTERSTAGE.get());
            map.put(ModItems.NOSE_CONE.get(), ModBlocks.ROCKET_NOSE.get());
            Map<Block, Item> reverse = new HashMap<>();
            map.forEach((item, block) -> reverse.put(block, item));
            pieces = reverse;
            blocks = map;
        }
        return blocks;
    }

    /** Bloc que pose cette pièce de fusée, ou {@code null} si elle n'en a pas (charge utile, composant...). */
    public static @Nullable Block blockOf(Item item) {
        return table().get(item);
    }

    /** Pièce de fusée que représente ce bloc, ou {@code null} si c'est un bloc qui se fabrique et se pose lui-même. */
    public static @Nullable Item pieceOf(Block block) {
        table();
        return pieces.get(block);
    }

    /**
     * Corps d'un étage dont les moteurs embarquent leurs ergols : le corps de moteur à poudre pour la poudre, un
     * panneau de coque autour des sphères d'un moteur hypergolique ou du réservoir de xénon d'un propulseur ionique.
     */
    public static Block bodyOf(Item engine) {
        return engine == ModItems.SOLID_MOTOR.get() || engine == ModItems.SOLID_BOOSTER.get() ? ModBlocks.ROCKET_MOTOR_CASING.get()
                : ModBlocks.ROCKET_FUSELAGE.get();
    }

    /** Bloc d'un tronçon qui coiffe la fusée. */
    private static Block top(RocketBlock.Part part) {
        return switch (part) {
            case PAYLOAD -> ModBlocks.ROCKET_PAYLOAD_BAY.get();
            case NOSE_LOWER -> ModBlocks.ROCKET_FAIRING.get();
            case NOSE -> ModBlocks.ROCKET_NOSE.get();
            case CAPSULE, CAPSULE_MK2, CAPSULE_MK3 -> ModBlocks.CREW_CABIN.get();
            case CAPSULE_UPPER -> ModBlocks.CABIN_ROOF.get();
            case ESCAPE_TOWER -> ModBlocks.ESCAPE_TOWER.get();
            default -> ModBlocks.ROCKET_FUSELAGE.get();
        };
    }

    /** Un bloc qui se pose avec son propre objet, un à la fois. */
    private static Piece plain(BlockPos offset, Block block) {
        Item piece = pieceOf(block);
        return new Piece(offset, block, piece != null ? piece : block.asItem(), 1);
    }

    /**
     * Tous les blocs à poser pour bâtir une fusée, du bas vers le haut.
     *
     * @param design le plan de la fusée
     * @param trench sens du carneau du pas de tir : la tour, et donc le chemin de câbles, sont du côté opposé
     */
    public static List<Piece> pieces(RocketDesign design, Direction trench) {
        RocketShape shape = RocketShape.of(design);
        List<RocketBlock.Part> parts = shape.parts();
        List<RocketShape.Segment> segments = shape.segments();
        int stageCount = design.stages().size();
        // Tronçons de réservoirs de chaque étage : le coût de ses réservoirs s'y répartit.
        int[] bodies = new int[stageCount];
        int avionics = -1;
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).role() == RocketShape.Role.BODY) {
                bodies[segments.get(i).stage()]++;
                avionics = i;
            }
        }
        int[] seen = new int[stageCount];
        boolean wide = shape.isWide();
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            RocketBlock.Part part = parts.get(i);
            RocketShape.Segment segment = segments.get(i);
            RocketDesign.Stage stage = segment.stage() >= 0 ? design.stages().get(segment.stage()) : null;
            BlockPos axis = new BlockPos(0, i + 1, 0);
            Block centre;
            int extraEngines = 0;
            switch (segment.role()) {
                case BAY -> {
                    Item engine = stage.engines().getItem();
                    int engines = stage.engines().getCount();
                    centre = blockOf(engine);
                    // Sur une fusée large, les moteurs en plus du premier prennent place autour de lui.
                    extraEngines = wide ? Math.min(RING.length, engines - 1) : 0;
                    pieces.add(new Piece(axis, centre, engine, engines - extraEngines));
                }
                case BODY -> {
                    Item engine = stage.engines().getItem();
                    RocketParts.Part engineSpec = RocketParts.get(stage.engines());
                    if (engineSpec != null && engineSpec.engine().propellant().isPreloaded()) {
                        centre = bodyOf(engine);
                        pieces.add(plain(axis, centre));
                    } else {
                        // Les réservoirs de l'étage, répartis sur ses tronçons.
                        Item tank = stage.tanks().getItem();
                        int total = stage.tanks().getCount();
                        int k = seen[segment.stage()]++;
                        int n = bodies[segment.stage()];
                        centre = blockOf(tank);
                        pieces.add(new Piece(axis, centre, tank, (k + 1) * total / n - k * total / n));
                    }
                }
                case INTERSTAGE -> {
                    centre = ModBlocks.ROCKET_INTERSTAGE.get();
                    pieces.add(plain(axis, centre));
                }
                default -> {
                    centre = top(part);
                    pieces.add(plain(axis, centre));
                }
            }
            if (!wide || part.isCrewModule()) {
                continue;
            }
            for (int k = 0; k < RING.length; k++) {
                int forward = RING[k][0];
                int right = RING[k][1];
                BlockPos offset = new BlockPos(trench.getStepX() * forward - trench.getStepZ() * right, i + 1,
                        trench.getStepZ() * forward + trench.getStepX() * right);
                Block block;
                if (segment.role() == RocketShape.Role.BAY) {
                    // Les moteurs se placent face à face, deux par deux : les premières cases de la couronne sont en croix.
                    block = k >= 1 && k <= extraEngines ? centre : k == 0 ? ModBlocks.ROCKET_RACEWAY.get() : ModBlocks.ROCKET_FUSELAGE.get();
                } else if (part == RocketBlock.Part.NOSE_LOWER || part == RocketBlock.Part.NOSE) {
                    block = ModBlocks.ROCKET_FAIRING.get();
                } else if (k == 0 && part != RocketBlock.Part.PAYLOAD) {
                    block = ModBlocks.ROCKET_RACEWAY.get();
                } else if (k == 1 && i == avionics) {
                    block = ModBlocks.FLIGHT_COMPUTER.get();
                } else {
                    block = ModBlocks.ROCKET_FUSELAGE.get();
                }
                pieces.add(plain(offset, block));
            }
        }
        // Les boosters latéraux : de bas en haut, leur tuyère (la pièce elle-même), leur corps de poudre et leur pointe.
        List<BlockPos> boosters = shape.boosterOffsets(trench);
        for (int k = 0; k < boosters.size(); k++) {
            int height = shape.boosterHeight();
            int level = k % height;
            Block block = level == 0 ? ModBlocks.ROCKET_ENGINE_BOOSTER.get() : level == height - 1 ? ModBlocks.ROCKET_NOSE.get()
                    : ModBlocks.ROCKET_MOTOR_CASING.get();
            pieces.add(plain(boosters.get(k), block));
        }
        return List.copyOf(pieces);
    }

    /** Ce qu'il faut réunir pour bâtir la fusée : chaque pièce et chaque bloc, avec leur nombre, du bas vers le haut. */
    public static Map<Item, Integer> materials(RocketDesign design) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Piece piece : pieces(design, Direction.EAST)) {
            if (piece.count() > 0) {
                counts.merge(piece.source(), piece.count(), Integer::sum);
            }
        }
        return counts;
    }
}
