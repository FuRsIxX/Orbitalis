package com.orbitalis.phase1.fuel;

/**
 * Disposition d'une machine à fluides : ce qu'elle reçoit et combien de fluides elle produit.
 * La même entité de bloc et la même interface servent à toutes ces machines.
 */
public enum FluidMachineLayout {
    /** Électrolyseur : eau -> hydrogène + oxygène. Il pompe l'eau d'une source voisine. */
    ELECTROLYZER("electrolyzer", false, true, 2, true),
    /** Liquéfacteur cryogénique : gaz -> liquide. */
    CRYOGENIC_LIQUEFIER("cryogenic_liquefier", false, true, 1, false),
    /** Raffinerie : charbon -> kérosène RP-1. */
    REFINERY("refinery", true, false, 1, false),
    /**
     * Réacteur de Sabatier : du carbone (du charbon, que le réacteur brûle en gaz carbonique) et de l'hydrogène donnent
     * du méthane et de l'eau. Le méthane sort par le côté gauche, l'eau par le côté droit.
     */
    SABATIER_REACTOR("sabatier_reactor", true, true, 2, false),
    /**
     * Extracteur d'hélium-3 : un four rotatif cuit le régolithe de la Lune vers 700 °C, et en libère les gaz que le vent
     * solaire y a déposés. L'hélium-3 sort par le côté gauche, l'hydrogène par le côté droit.
     */
    HELIUM3_EXTRACTOR("helium3_extractor", true, false, 2, false);

    private final String name;
    private final boolean itemInput;
    private final boolean fluidInput;
    private final int outputCount;
    private final boolean waterIntake;

    FluidMachineLayout(String name, boolean itemInput, boolean fluidInput, int outputCount, boolean waterIntake) {
        this.name = name;
        this.itemInput = itemInput;
        this.fluidInput = fluidInput;
        this.outputCount = outputCount;
        this.waterIntake = waterIntake;
    }

    /** Nom de la machine, utilisé pour l'image de son interface. */
    public String id() {
        return name;
    }

    public boolean hasItemInput() {
        return itemInput;
    }

    public boolean hasFluidInput() {
        return fluidInput;
    }

    public int outputCount() {
        return outputCount;
    }

    /** Vrai si la machine se remplit d'eau toute seule au contact d'une source d'eau. */
    public boolean hasWaterIntake() {
        return waterIntake;
    }
}
