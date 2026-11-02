package com.orbitalis.client.guide;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import com.orbitalis.phase1.spaceport.PadPlan;
import static com.orbitalis.client.guide.GuideScene.at;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.item.Items;

/**
 * Démonstrations animées du manuel, une par chapitre qui s'y prête.
 * <p>
 * Repères : x vers l'est, y vers le haut, z vers le sud, comme dans le monde. Les machines regardent le sud
 * (vers la caméra). Les câbles et tuyaux se raccordent d'eux-mêmes aux blocs voisins, comme en jeu.
 */
public final class GuideScenes {
    private static final int ENERGY = 0xFFFFC23D;
    private static final int RP1 = ModFluids.RP1.color();
    private static final int HYDROGEN = ModFluids.LIQUID_HYDROGEN.color();
    private static final int OXYGEN = ModFluids.LIQUID_OXYGEN.color();
    private static final int VAPOR = 0xFFE8F4FF;
    private static final int WATER = 0xFF3F76E4;
    private static final int DATA = 0xFF3FD8F2;

    private static final Map<String, GuideScene> SCENES = new HashMap<>();

    static {
        register(energy());
        register(arcFoundry());
        register(rollingMill());
        register(conduits());
        register(fuels());
        register(cryogenics());
        register(storage());
        register(quarry());
        register(testPad());
        // Un pas de tir par taille : le 5×5 dans le chapitre du pas de tir, puis un chapitre pour chacune des autres.
        register(launchPad("launch_complex", 2));
        register(launchPad("pad_9x9", 4));
        register(launchPad("pad_11x11", 5));
    }

    private GuideScenes() {
    }

    private static void register(GuideScene scene) {
        SCENES.put(scene.chapterId(), scene);
    }

    /** Démonstration d'un chapitre, ou {@code null} s'il n'en a pas. */
    public static GuideScene get(String chapterId) {
        return SCENES.get(chapterId);
    }

    /** Énergie : un générateur, son combustible, puis des machines à son contact. */
    private static GuideScene energy() {
        return GuideScene.builder("energy")
                .step().block(ModBlocks.COMBUSTION_GENERATOR, 0, 0, 0)
                .step().marker(Items.COAL, 0, 1.2F, 0).lit(0, 0, 0)
                .step().block(ModBlocks.ARC_FOUNDRY, 1, 0, 0)
                        .flow(ENERGY, at(0, 0, 0), at(1, 0, 0))
                .step().block(ModBlocks.ROLLING_MILL, -1, 0, 0)
                        .flow(ENERGY, at(0, 0, 0), at(-1, 0, 0))
                .build();
    }

    /** Fonderie à arc : deux ingrédients entrent, un alliage sort. */
    private static GuideScene arcFoundry() {
        return GuideScene.builder("arc_foundry")
                .step().block(ModBlocks.COMBUSTION_GENERATOR, 0, 0, 0).lit(0, 0, 0).block(ModBlocks.ARC_FOUNDRY, 1, 0, 0)
                        .flow(ENERGY, at(0, 0, 0), at(1, 0, 0))
                .step().marker(Items.IRON_INGOT, 0.75F, 1.2F, 0).marker(Items.COAL, 1.25F, 1.2F, 0).lit(1, 0, 0)
                .step().marker(ModItems.STEEL_INGOT, 1, 1.2F, 0)
                .build();
    }

    /** Laminoir : un lingot entre, une tôle sort. */
    private static GuideScene rollingMill() {
        return GuideScene.builder("rolling_mill")
                .step().block(ModBlocks.COMBUSTION_GENERATOR, 0, 0, 0).lit(0, 0, 0).block(ModBlocks.ROLLING_MILL, 1, 0, 0)
                        .flow(ENERGY, at(0, 0, 0), at(1, 0, 0))
                .step().marker(ModItems.STEEL_INGOT, 1, 1.2F, 0).lit(1, 0, 0)
                .step().marker(ModItems.STEEL_PLATE, 1, 1.2F, 0)
                .build();
    }

    /** Conduits : une ligne de câbles vers deux machines, puis une ligne de tuyaux vers un réservoir. */
    private static GuideScene conduits() {
        return GuideScene.builder("conduits")
                .step().block(ModBlocks.COMBUSTION_GENERATOR, 0, 0, 0).lit(0, 0, 0)
                .step().block(ModBlocks.ENERGY_CABLE, 1, 0, 0).block(ModBlocks.ENERGY_CABLE, 2, 0, 0)
                .step().block(ModBlocks.REFINERY, 3, 0, 0).lit(3, 0, 0).block(ModBlocks.ARC_FOUNDRY, 2, 0, 1)
                        .flow(ENERGY, at(0, 0, 0), at(3, 0, 0))
                        .flow(ENERGY, at(2, 0, 0), at(2, 0, 1))
                .step().block(ModBlocks.FLUID_PIPE, 4, 0, 0).block(ModBlocks.FLUID_PIPE, 5, 0, 0)
                        .block(ModBlocks.CRYOGENIC_TANK, 6, 0, 0)
                        .flow(RP1, at(3, 0, 0), at(6, 0, 0))
                .build();
    }

    /** Carburants : la raffinerie transforme le charbon en RP-1 et remplit un réservoir. */
    private static GuideScene fuels() {
        return GuideScene.builder("fuels")
                .step().block(ModBlocks.COMBUSTION_GENERATOR, 0, 0, 0).lit(0, 0, 0).block(ModBlocks.REFINERY, 1, 0, 0)
                        .flow(ENERGY, at(0, 0, 0), at(1, 0, 0))
                .step().marker(Items.COAL, 1, 1.2F, 0).lit(1, 0, 0)
                .step().block(ModBlocks.CRYOGENIC_TANK, 2, 0, 0)
                        .flow(RP1, at(1, 0, 0), at(2, 0, 0))
                .build();
    }

    /**
     * Oxygène et hydrogène : l'électrolyseur regarde le sud, donc sa gauche (hydrogène) est à l'ouest et
     * sa droite (oxygène) à l'est. Un liquéfacteur de chaque côté, puis un réservoir au-dessus de chacun.
     */
    private static GuideScene cryogenics() {
        return GuideScene.builder("cryogenics")
                .step().block(ModBlocks.ELECTROLYZER, 0, 0, 0).marker(Items.WATER_BUCKET, 0, 1.2F, 0)
                .step().lit(0, 0, 0)
                        .block(ModBlocks.CRYOGENIC_LIQUEFIER, -1, 0, 0).lit(-1, 0, 0)
                        .block(ModBlocks.CRYOGENIC_LIQUEFIER, 1, 0, 0).lit(1, 0, 0)
                        .flow(HYDROGEN, at(0, 0, 0), at(-1, 0, 0))
                        .flow(OXYGEN, at(0, 0, 0), at(1, 0, 0))
                .step().block(ModBlocks.CRYOGENIC_TANK, -1, 1, 0).block(ModBlocks.CRYOGENIC_TANK, 1, 1, 0)
                        .flow(HYDROGEN, at(-1, 0, 0), at(-1, 1, 0))
                        .flow(OXYGEN, at(1, 0, 0), at(1, 1, 0))
                .build();
    }

    /**
     * Montage complet d'un pas de tir, pièce par pièce, dans l'ordre où on le bâtit, d'après le plan du kit : la
     * table, le déflecteur et les appuis, le tablier, le carneau, les mâts et les rampes de déluge, la colonne
     * technique, le reste de la tour, le bras ombilical, la validation, puis le ravitaillement et le pupitre.
     *
     * @param chapterId chapitre du manuel auquel appartient la démonstration
     * @param radius    taille du tablier : 2 pour 5×5, 4 pour 9×9, 5 pour 11×11
     */
    private static GuideScene launchPad(String chapterId, int radius) {
        // Tour à escalier pour le plus petit, à ascenseur pour les autres : le manuel montre les deux.
        PadPlan plan = new PadPlan(radius, radius >= 2, Direction.EAST);
        GuideScene.Builder builder = GuideScene.builder(chapterId).step().block(ModBlocks.LAUNCH_TABLE, 0, 1, 0);
        List<java.util.function.Predicate<PadPlan.Piece>> steps = List.of(
                piece -> piece.role() == PadPlan.Role.DEFLECTOR || piece.role() == PadPlan.Role.SUPPORT,
                piece -> piece.role() == PadPlan.Role.DECK || piece.role() == PadPlan.Role.ACCESS,
                piece -> piece.role() == PadPlan.Role.TRENCH,
                piece -> piece.role() == PadPlan.Role.MAST || piece.role() == PadPlan.Role.DELUGE,
                piece -> piece.role() == PadPlan.Role.TOWER && piece.block() == ModBlocks.SERVICE_TOWER.get(),
                piece -> (piece.role() == PadPlan.Role.TOWER && piece.block() != ModBlocks.SERVICE_TOWER.get()) || piece.role() == PadPlan.Role.ROOF);
        for (java.util.function.Predicate<PadPlan.Piece> step : steps) {
            builder.step();
            for (PadPlan.Piece piece : plan.pieces()) {
                if (step.test(piece)) {
                    Block block = piece.block();
                    BlockPos at = piece.offset();
                    if (piece.facing() != null) {
                        builder.block(() -> block, at.getX(), at.getY() + 1, at.getZ(), piece.facing());
                    } else {
                        builder.block(() -> block, at.getX(), at.getY() + 1, at.getZ());
                    }
                }
            }
        }
        return builder
                .step().block(ModBlocks.UMBILICAL_ARM, -radius, 4, 0)
                .step().lit(0, 1, 0)
                // Un bloc de ravitaillement à la place d'un bloc de charpente, au pied de la tour, et son réservoir.
                .step().block(ModBlocks.FUEL_PORT, -radius - 3, 0, 2)
                        .block(ModBlocks.CRYOGENIC_TANK, -radius - 3, 0, 3)
                        // Le pupitre est rapproché pour tenir dans l'image : en jeu, il se pose à 8 blocs au moins.
                        .block(ModBlocks.LAUNCH_CONSOLE, 0, 0, radius + 3)
                .build();
    }

    /** Pose les blocs du plan dont le rôle est l'un de ceux-là, un bloc plus haut que le plan (le sol est en y = 0). */
    private static void padPieces(GuideScene.Builder builder, PadPlan plan, PadPlan.Role... roles) {
        List<PadPlan.Role> wanted = List.of(roles);
        for (PadPlan.Piece piece : plan.pieces()) {
            if (wanted.contains(piece.role())) {
                Block block = piece.block();
                BlockPos at = piece.offset();
                builder.block(() -> block, at.getX(), at.getY() + 1, at.getZ(), piece.facing() != null ? piece.facing() : Direction.SOUTH);
            }
        }
    }

    /**
     * Pas de tir d'essai : la table, le déflecteur, les huit dalles, le carneau, la mise en service, puis l'électricité
     * et le pupitre d'essai relié par câble de données à un port de données.
     */
    private static GuideScene testPad() {
        PadPlan plan = PadPlan.test(Direction.EAST);
        GuideScene.Builder builder = GuideScene.builder("test_pad").step().block(ModBlocks.LAUNCH_TABLE, 0, 1, 0);
        builder.step();
        padPieces(builder, plan, PadPlan.Role.DEFLECTOR, PadPlan.Role.SUPPORT);
        builder.step();
        padPieces(builder, plan, PadPlan.Role.DECK, PadPlan.Role.ACCESS);
        builder.step();
        padPieces(builder, plan, PadPlan.Role.TRENCH);
        return builder
                .step().lit(0, 1, 0)
                .step().block(ModBlocks.COMBUSTION_GENERATOR, 1, 1, 2).lit(1, 1, 2)
                        .flow(ENERGY, at(1, 1, 2), at(1, 1, 1))
                        .block(ModBlocks.FUEL_PORT, -1, 1, 2)
                        .block(ModBlocks.DATA_CABLE, -1, 1, 3).block(ModBlocks.DATA_CABLE, -1, 1, 4)
                        .block(ModBlocks.TEST_CONSOLE, -1, 1, 5, Direction.NORTH)
                        .flow(DATA, at(-1, 1, 5), at(-1, 1, 2))
                .build();
    }

    /**
     * Foreuse : elle regarde le sud (la zone forée est devant elle, vers la caméra). Le socle, deux étages de mâts
     * pour la plus petite tour, les raccords, puis deux étages de plus.
     */
    private static GuideScene quarry() {
        GuideScene.Builder builder = GuideScene.builder("quarry").step().block(ModBlocks.DRILL_DERRICK, 0, 0, 0, Direction.SOUTH);
        List<com.orbitalis.phase1.quarry.DerrickPlan.Piece> pieces = com.orbitalis.phase1.quarry.DerrickPlan.pieces(Direction.SOUTH, 4);
        // Étape par hauteur : le socle (y = 0), puis chaque étage de mâts ; les deux derniers vont ensemble.
        for (int y = 0; y <= 4; y++) {
            if (y != 4) {
                builder.step();
            }
            for (com.orbitalis.phase1.quarry.DerrickPlan.Piece piece : pieces) {
                if (piece.offset().getY() == y) {
                    Block block = piece.block();
                    builder.block(() -> block, piece.offset().getX(), y, piece.offset().getZ());
                }
            }
            if (y == 2) {
                // La plus petite tour est complète : elle fore. Puis ses raccords, avant les étages de plus.
                builder.lit(0, 0, 0)
                        .step().block(ModBlocks.COMBUSTION_GENERATOR, 2, 0, -1).lit(2, 0, -1)
                        .flow(ENERGY, at(2, 0, -1), at(1, 0, -1))
                        .block(ModBlocks.WATER_PUMP, -2, 0, -1)
                        .flow(WATER, at(-2, 0, -1), at(-1, 0, -1))
                        .block(() -> net.minecraft.world.level.block.Blocks.BARREL, 0, 0, -3);
            }
        }
        return builder.build();
    }

    /** Stockage : alimenté, le réservoir conserve son contenu ; sans énergie, l'ergol s'évapore. */
    private static GuideScene storage() {
        return GuideScene.builder("storage")
                .step().block(ModBlocks.CRYOGENIC_TANK, 0, 0, 0)
                .step().block(ModBlocks.COMBUSTION_GENERATOR, -1, 0, 0).lit(-1, 0, 0).lit(0, 0, 0)
                        .flow(ENERGY, at(-1, 0, 0), at(0, 0, 0))
                .step().stopFlows().remove(-1, 0, 0)
                        .flow(VAPOR, at(0, 0.5F, 0), at(0, 1.8F, 0))
                .build();
    }
}
