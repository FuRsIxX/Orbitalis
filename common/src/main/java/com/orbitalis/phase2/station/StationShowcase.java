package com.orbitalis.phase2.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * La station de démonstration de la carte de test et des captures, bâtie en ligne comme on le ferait en jeu : le module
 * central, puis deux nœuds vers l'est, le chantier naval au bout.
 * <pre>
 *                 [Logistique]          [Habitation]     [Laboratoire]
 *                      │                     │                 │
 *   [Sas] ── [Module central] ──────── [Nœud A] ──────── [Nœud B] ── [Chantier] ══ berceau ══►
 *                      ║ amarrage            │                 │
 *                                         [Serre]          [Coupole]
 * </pre>
 * La poutre d'énergie part du fond du module logistique, vers le nord. Les positions sont comptées depuis le centre du
 * module central.
 */
public final class StationShowcase {
    private StationShowcase() {
    }

    /**
     * Accroche les modules autour d'un module central déjà bâti.
     *
     * @param shipyard vrai pour poser aussi le chantier naval
     * @return les refus, s'il y en a eu (vide si tout est en place)
     */
    public static List<Component> build(ServerLevel level, BlockPos core, boolean shipyard) {
        List<Component> problems = new ArrayList<>();
        attach(problems, level, core.offset(-2, 0, 0), ModuleType.AIRLOCK);
        attach(problems, level, core.offset(0, 0, -2), ModuleType.CARGO);
        attach(problems, level, core.offset(2, 0, 0), ModuleType.NODE);
        attach(problems, level, core.offset(5, 0, -2), ModuleType.HABITATION);
        attach(problems, level, core.offset(5, 0, 2), ModuleType.GREENHOUSE);
        attach(problems, level, core.offset(8, 0, 0), ModuleType.NODE);
        attach(problems, level, core.offset(11, 0, -2), ModuleType.LABORATORY);
        attach(problems, level, core.offset(11, 0, 2), ModuleType.CUPOLA);
        Component truss = StationModules.attachTruss(level, core.offset(0, 0, -8), Direction.NORTH);
        if (truss != null) {
            problems.add(truss);
        }
        if (shipyard) {
            attach(problems, level, core.offset(14, 0, 0), ModuleType.SHIPYARD);
        }
        return problems;
    }

    private static void attach(List<Component> problems, ServerLevel level, BlockPos port, ModuleType type) {
        Component problem = StationModules.attach(level, port, type);
        if (problem != null) {
            problems.add(Component.literal(type.id() + " : ").append(problem));
        }
    }

    /** Le pupitre du chantier naval, face à sa verrière. */
    public static BlockPos console(BlockPos core) {
        return core.offset(17, -1, 0);
    }

    /** Les portes du sas : l'intérieure, dans le mur du module central, et l'extérieure, vers le vide (moitiés basses). */
    public static BlockPos innerAirlockDoor(BlockPos core) {
        return core.offset(-2, -1, 0);
    }

    public static BlockPos outerAirlockDoor(BlockPos core) {
        return core.offset(-5, -1, 0);
    }
}
