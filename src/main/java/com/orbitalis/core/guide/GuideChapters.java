package com.orbitalis.core.guide;

import java.util.List;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.item.Items;

/**
 * Sommaire du manuel, dans l'ordre de la progression du joueur et regroupé par rubriques.
 * Chaque nouvelle étape du mod ajoute ses chapitres à la suite.
 */
public final class GuideChapters {
    private static final String BASICS = "basics";
    private static final String MACHINES = "machines";
    private static final String FUELS = "fuels";
    private static final String PROGRESS = "progress";
    private static final String ROCKETS = "rockets";
    private static final String SPACEPORT = "spaceport";
    private static final String ORBIT = "orbit";
    private static final String CREW = "crew";
    private static final String EXPLORATION = "exploration";

    public static final List<GuideChapter> ALL = shown(List.of(
            new GuideChapter("welcome", BASICS, ModItems.GUIDE_BOOK),
            new GuideChapter("ores", BASICS, ModItems.RAW_BAUXITE),
            new GuideChapter("facilities", BASICS, ModBlocks.RADOME_PANEL),
            new GuideChapter("energy", MACHINES, ModBlocks.COMBUSTION_GENERATOR),
            new GuideChapter("arc_foundry", MACHINES, ModBlocks.ARC_FOUNDRY),
            new GuideChapter("rolling_mill", MACHINES, ModBlocks.ROLLING_MILL),
            new GuideChapter("conduits", MACHINES, ModBlocks.ENERGY_CABLE),
            new GuideChapter("automation", MACHINES, () -> Items.HOPPER),
            new GuideChapter("fuels", FUELS, ModBlocks.REFINERY),
            new GuideChapter("cryogenics", FUELS, ModBlocks.CRYOGENIC_LIQUEFIER),
            new GuideChapter("methane", FUELS, ModBlocks.SABATIER_REACTOR),
            new GuideChapter("storage", FUELS, ModBlocks.CRYOGENIC_TANK),
            new GuideChapter("research", PROGRESS, () -> Items.KNOWLEDGE_BOOK),
            new GuideChapter("engineering", ROCKETS, ModBlocks.ENGINEER_WORKBENCH),
            new GuideChapter("rocket_assembly", ROCKETS, ModBlocks.DESIGN_COMPUTER),
            new GuideChapter("rocket_stats", ROCKETS, ModItems.KEROLOX_ENGINE),
            new GuideChapter("launch_complex", SPACEPORT, ModBlocks.LAUNCH_TABLE),
            new GuideChapter("test_pad", SPACEPORT, ModBlocks.TEST_CONSOLE),
            new GuideChapter("launch", SPACEPORT, ModItems.ROCKET),
            new GuideChapter("pad_sizes", SPACEPORT, ModBlocks.LAUNCH_PAD),
            new GuideChapter("pad_9x9", SPACEPORT, ModBlocks.SERVICE_TOWER),
            new GuideChapter("pad_11x11", SPACEPORT, ModBlocks.SERVICE_TOWER),
            new GuideChapter("missions", SPACEPORT, ModBlocks.LAUNCH_CONSOLE),
            new GuideChapter("satellites", ORBIT, ModItems.TEST_SATELLITE),
            new GuideChapter("tracking_station", ORBIT, ModBlocks.TRACKING_STATION),
            new GuideChapter("crewed_flight", CREW, ModItems.CREW_CAPSULE),
            new GuideChapter("space", CREW, ModItems.SPACESUIT),
            new GuideChapter("stations", CREW, ModItems.STATION_CORE),
            new GuideChapter("moon", EXPLORATION, ModItems.LUNAR_MODULE),
            new GuideChapter("mars", EXPLORATION, ModItems.MARS_LANDER),
            new GuideChapter("titan", EXPLORATION, ModItems.TITAN_LANDER),
            new GuideChapter("europa", EXPLORATION, ModItems.EUROPA_LANDER),
            new GuideChapter("helium3", EXPLORATION, ModBlocks.HELIUM3_EXTRACTOR),
            new GuideChapter("fusion", EXPLORATION, ModItems.FUSION_ENGINE),
            new GuideChapter("fusion_power", EXPLORATION, ModBlocks.FUSION_REACTOR),
            new GuideChapter("shipyard", EXPLORATION, ModItems.SHIPYARD_MODULE)));

    /** Les chapitres du contenu en réserve pour une prochaine version n'apparaissent pas. */
    private static List<GuideChapter> shown(List<GuideChapter> chapters) {
        return chapters.stream().filter(chapter -> com.orbitalis.core.Features.interstellar() || !chapter.id().equals("shipyard")).toList();
    }

    private GuideChapters() {
    }
}
