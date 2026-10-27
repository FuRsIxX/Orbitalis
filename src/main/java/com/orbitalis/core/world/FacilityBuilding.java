package com.orbitalis.core.world;

import java.util.function.Consumer;

/**
 * Les bâtiments des centres spatiaux, chacun avec son emprise (largeur de façade, hauteur, profondeur, parvis
 * compris) et son plan (voir {@link FacilityBuilders}).
 */
public enum FacilityBuilding {
    // Station météorologique
    WEATHER_HUT(9, 10, 8, FacilityBuilders::weatherHut),
    MET_MAST(3, 13, 3, FacilityBuilders::metMast),
    WEATHER_GARDEN(7, 4, 7, FacilityBuilders::weatherGarden),
    RADAR_TOWER(9, 24, 9, FacilityBuilders::radarTower),
    BALLOON_SHED(7, 7, 8, FacilityBuilders::balloonShed),
    // Centre de recherche
    RESEARCH_LAB(17, 12, 12, FacilityBuilders::researchLab),
    STORAGE_SHED(7, 6, 7, FacilityBuilders::storageShed),
    TEST_STAND(11, 24, 11, FacilityBuilders::testStand),
    HANGAR(15, 11, 13, FacilityBuilders::hangar),
    // Centre d'observation
    OBSERVATORY(11, 12, 12, FacilityBuilders::observatory),
    CONTROL_HUT(9, 10, 10, FacilityBuilders::controlHut),
    SMALL_DISH(7, 9, 7, FacilityBuilders::smallDish),
    RADIO_DISH(15, 18, 15, FacilityBuilders::radioDish),
    // Centre de contrôle et son pas de tir
    LAUNCH_CONTROL(27, 18, 14, FacilityBuilders::launchControl),
    ASSEMBLY_BUILDING(15, 26, 14, FacilityBuilders::assemblyBuilding),
    WATER_TOWER(7, 16, 7, FacilityBuilders::waterTower),
    LAUNCH_PAD(25, 31, 25, FacilityBuilders::launchPad),
    // Annexes, tirées au hasard
    COMMS_MAST(5, 24, 5, FacilityBuilders::commsMast),
    FUEL_FARM(11, 7, 9, FacilityBuilders::fuelFarm),
    SOLAR_FARM(9, 3, 9, FacilityBuilders::solarFarm),
    STORAGE_YARD(9, 4, 9, FacilityBuilders::storageYard),
    DISH_ARRAY(13, 7, 9, FacilityBuilders::dishArray),
    HELIPAD(9, 2, 9, FacilityBuilders::helipad),
    GUARD_HOUSE(5, 5, 6, FacilityBuilders::guardHouse),
    OFFICE_ANNEX(11, 6, 9, FacilityBuilders::officeAnnex);

    private final int width;
    private final int height;
    private final int depth;
    private final Consumer<FacilityPiece> plan;

    FacilityBuilding(int width, int height, int depth, Consumer<FacilityPiece> plan) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.plan = plan;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    void build(FacilityPiece piece) {
        plan.accept(piece);
    }

    static FacilityBuilding byName(String name) {
        for (FacilityBuilding building : values()) {
            if (building.name().equals(name)) {
                return building;
            }
        }
        return WEATHER_HUT;
    }
}
