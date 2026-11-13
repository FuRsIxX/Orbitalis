package com.orbitalis.phase1.quarry;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.satellite.ProspectOre;

import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.Tags;

/**
 * Ce que le derrick sait reconnaître et extraire : les minerais que distinguent les satellites d'observation
 * ({@link ProspectOre}), puis tous les autres minerais réunis, puis le régolithe de la Lune, de Mars et de Titan, dont
 * l'extracteur d'hélium-3 a besoin. Chaque sorte a son rang, qui sert aux filtres et aux relevés.
 */
public final class QuarryKinds {
    /** Les autres minerais (émeraude, lapis-lazuli, minerais des autres mondes et des autres mods). */
    public static final int OTHER = ProspectOre.values().length;
    /** Le régolithe et le sable de tholines. */
    public static final int REGOLITH = OTHER + 1;
    public static final int COUNT = REGOLITH + 1;
    /** Le filtre de départ : tous les minerais, sans le régolithe. */
    public static final int ALL_ORES = (1 << REGOLITH) - 1;

    private QuarryKinds() {
    }

    /** Rang de la sorte à laquelle appartient ce bloc, ou -1 s'il n'en est pas une. */
    public static int of(BlockState state) {
        ProspectOre ore = ProspectOre.of(state);
        if (ore != null) {
            return ore.ordinal();
        }
        if (state.is(Tags.Blocks.ORES)) {
            return OTHER;
        }
        if (state.is(ModBlocks.MOON_REGOLITH.get()) || state.is(ModBlocks.MARS_REGOLITH.get()) || state.is(ModBlocks.THOLIN_SAND.get())) {
            return REGOLITH;
        }
        return -1;
    }

    /** Couleur de la sorte, sur la carte et dans les listes. */
    public static int color(int kind) {
        if (kind < OTHER) {
            return ProspectOre.values()[kind].color();
        }
        return kind == OTHER ? 0xFF9AA3AD : 0xFFC9B48A;
    }

    /** Clé du nom de la sorte. */
    public static String nameKey(int kind) {
        if (kind < OTHER) {
            return ProspectOre.values()[kind].nameKey();
        }
        return kind == OTHER ? "derrick.orbitalis.kind.other" : "derrick.orbitalis.kind.regolith";
    }

    /** Vrai pour les minerais rares : leur premier carottage rapporte deux fois plus. */
    public static boolean rare(int kind) {
        return kind == ProspectOre.DIAMOND.ordinal() || kind == ProspectOre.TITANIUM.ordinal() || kind == ProspectOre.LITHIUM.ordinal()
                || kind == ProspectOre.GOLD.ordinal();
    }
}
