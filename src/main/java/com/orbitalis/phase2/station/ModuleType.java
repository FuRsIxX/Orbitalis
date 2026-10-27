package com.orbitalis.phase2.station;

/**
 * Les modules d'une station. Chacun a son rôle, sa longueur (en blocs, mur commun compris), la technologie qu'il
 * demande et la couleur qui le représente sur le plan du terminal.
 */
public enum ModuleType {
    /** Le module central : la première pièce, avec le sas d'amarrage et le générateur d'air. */
    CORE("core", 5, "station_modules", 0x7FD0FF, true),
    /** Une pièce nue, à aménager soi-même. */
    ROOM("room", 5, "station_modules", 0xAEB6BF, true),
    /** Quartiers d'équipage : quatre couchettes, des casiers personnels et le terminal. */
    HABITATION("habitation", 7, "station_modules", 0x3F76E4, true),
    /** Laboratoire : huit baies d'expériences, qui font avancer la recherche en apesanteur. */
    LABORATORY("laboratory", 7, "station_modules", 0x39B5C9, true),
    /** Serre : huit bacs de culture sous leurs rampes de diodes. Ses plantes recyclent aussi l'air. */
    GREENHOUSE("greenhouse", 7, "closed_loop_life_support", 0x5BC27A, true),
    /** Module logistique : huit casiers de fret. */
    CARGO("cargo", 7, "station_modules", 0xE8872A, true),
    /** Coupole : une pièce courte vitrée de toutes parts, d'où l'on voit la planète et les étoiles. */
    CUPOLA("cupola", 4, "station_modules", 0xC9A0FF, true),
    /** Sas de sortie : deux portes étanches, pour sortir dans le vide sans dépressuriser la station. */
    AIRLOCK("airlock", 4, "station_modules", 0xE8C02A, true),
    /** Poutre d'énergie : une poutre treillis, deux ailes de panneaux solaires et une batterie. Elle reste dehors. */
    POWER("power", 6, "solar_arrays", 0x1F3F8F, false),
    /**
     * Chantier naval : une salle de contrôle à verrière, son pupitre, et derrière, le berceau ouvert sur le vide où se bâtit
     * le vaisseau interstellaire.
     */
    SHIPYARD("shipyard", 5, "orbital_shipyard", 0xF0B95A, true);

    private final String id;
    private final int length;
    private final String research;
    private final int color;
    private final boolean pressurized;

    ModuleType(String id, int length, String research, int color, boolean pressurized) {
        this.id = id;
        this.length = length;
        this.research = research;
        this.color = color;
        this.pressurized = pressurized;
    }

    public String id() {
        return id;
    }

    /** Longueur du module, mur commun compris, en blocs. */
    public int length() {
        return length;
    }

    /** Technologie à maîtriser pour bâtir ce module. */
    public String research() {
        return research;
    }

    /** Couleur du module sur le plan du terminal. */
    public int color() {
        return color;
    }

    /** Vrai si c'est une pièce habitable (un passage s'ouvre dans le mur commun), faux pour la poutre d'énergie. */
    public boolean pressurized() {
        return pressurized;
    }

    public String nameKey() {
        return "station.orbitalis.module." + id;
    }

    public static ModuleType byId(String id) {
        for (ModuleType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return ROOM;
    }
}
