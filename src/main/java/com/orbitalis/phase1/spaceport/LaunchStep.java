package com.orbitalis.phase1.spaceport;

/**
 * Étapes de la séquence de lancement, dans l'ordre où une équipe les fait. Chacune se commande depuis le pupitre de
 * lancement ou depuis la cabine (voir {@link LaunchTableBlockEntity#command}).
 * <ol>
 * <li>{@link #VALVES} : ouvrir les vannes, le temps de faire le plein, puis les refermer ;</li>
 * <li>{@link #PRESSURIZE} : mettre les réservoirs sous pression (elle ne tient que deux minutes) ;</li>
 * <li>{@link #RETRACT} : retirer le bras ombilical et la passerelle ;</li>
 * <li>{@link #POLL} : le tour de table, où chaque poste dit s'il est prêt (facultatif, mais il évite les oublis) ;</li>
 * <li>{@link #DELUGE} : ouvrir le déluge d'eau, sur les pas de tir qui en ont un ;</li>
 * <li>{@link #IGNITE} : allumer les moteurs, qui montent en poussée en trois secondes ;</li>
 * <li>{@link #RELEASE} : lâcher les attaches, à pleine poussée.</li>
 * </ol>
 * Toutes les fusées ne demandent pas tout : une fusée à poudre n'a ni plein ni pression ; un moteur à turbopompes
 * demande en plus sa mise en froid ({@link #CHILL}) ; un lanceur à combustion étagée ou à hydrogène doit encore passer
 * sur son alimentation interne ({@link #INTERNAL}). Plus la technique avance, plus la séquence est longue.
 * <p>
 * {@link #ABORT} coupe tout tant que la fusée est retenue. Les quatre dernières étapes se font en vol.
 */
public enum LaunchStep {
    VALVES,
    PRESSURIZE,
    RETRACT,
    DELUGE,
    IGNITE,
    RELEASE,
    ABORT,
    // En vol : chacune a sa fenêtre, pendant laquelle il faut la commander.
    /** Réduire les gaz au passage de la pression maximale (fusées à ergols liquides). */
    THROTTLE,
    /** Séparer l'étage vide, à chaque changement d'étage. */
    STAGE,
    /** Larguer la coiffe, une fois dans l'espace (fusées sans équipage). */
    FAIRING,
    /** Couper les moteurs pour l'injection en orbite (vols orbitaux). */
    CUTOFF,
    /** Changer le niveau d'aide de l'ordinateur de bord : manuel, assisté, automatique. */
    ASSIST,
    /** Tour de table avant l'allumage : chaque poste répond « prêt » ou « pas prêt ». Facultatif : sans lui, on allume à ses risques. */
    POLL,
    /** Mettre les moteurs en froid (moteurs à turbopompes) : le froid ne tient que deux minutes. */
    CHILL,
    /** Passer le lanceur sur son alimentation interne (lanceurs à combustion étagée ou à hydrogène). */
    INTERNAL,
    // Essais du pas de tir : ils se font au repos, avec ou sans fusée, et ne comptent pas dans la séquence.
    /** Essai du déluge : les rampes crachent huit secondes, pour un quart de l'eau d'un vrai déluge. */
    TEST_DELUGE,
    /** Essai des bras : le bras ombilical et la passerelle s'écartent, puis reviennent d'eux-mêmes. */
    TEST_ARMS,
    /** Essai de l'alarme : la sirène du pas de tir sonne trois fois. */
    TEST_ALARM;

    /** Vrai pour un essai du pas de tir. */
    public boolean isTest() {
        return this == TEST_DELUGE || this == TEST_ARMS || this == TEST_ALARM;
    }
}
