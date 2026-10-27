package com.orbitalis.core.research;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * État de la recherche d'une agence spatiale : ses points, les technologies débloquées
 * et les jalons déjà récompensés.
 * <p>
 * Cette classe ne contient que la logique (aucun accès au monde ni au réseau) : les mêmes règles servent
 * sur le serveur, qui fait autorité, et sur le client, pour l'affichage.
 */
public final class AgencyResearch {
    public static final Codec<AgencyResearch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("points").forGetter(AgencyResearch::points),
            Codec.STRING.listOf().fieldOf("unlocked").forGetter(research -> List.copyOf(research.unlocked)),
            Codec.STRING.listOf().optionalFieldOf("milestones", List.of()).forGetter(research -> List.copyOf(research.milestones)),
            Codec.INT.optionalFieldOf("flights", 0).forGetter(AgencyResearch::flights))
            .apply(instance, AgencyResearch::new));

    /** Résultat d'une tentative de recherche. */
    public enum UnlockResult {
        SUCCESS,
        UNKNOWN_NODE,
        ALREADY_UNLOCKED,
        MISSING_PREREQUISITE,
        NOT_ENOUGH_POINTS
    }

    private int points;
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final Set<String> milestones = new LinkedHashSet<>();
    /** Nombre de vols réussis : la fiabilité des lancements augmente avec l'expérience. */
    private int flights;

    public AgencyResearch() {
    }

    public AgencyResearch(int points, Collection<String> unlocked, Collection<String> milestones) {
        this.points = Math.max(0, points);
        this.unlocked.addAll(unlocked);
        this.milestones.addAll(milestones);
    }

    public AgencyResearch(int points, Collection<String> unlocked, Collection<String> milestones, int flights) {
        this(points, unlocked, milestones);
        this.flights = Math.max(0, flights);
    }

    public int points() {
        return points;
    }

    public int flights() {
        return flights;
    }

    public void addFlight() {
        flights++;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(int amount) {
        // Le total ne déborde jamais, même après de très gros ajouts.
        setPoints((int) Math.min(Integer.MAX_VALUE, (long) points + amount));
    }

    /** Identifiants des technologies recherchées (sans celles connues dès le départ). */
    public Set<String> unlocked() {
        return unlocked;
    }

    /** Vrai si la technologie est acquise : recherchée, ou connue dès le départ. */
    public boolean isUnlocked(String id) {
        ResearchNode node = ResearchNodes.get(id);
        return node != null && (node.isStartingKnowledge() || unlocked.contains(id));
    }

    /** Vrai si tous les prérequis de la technologie sont acquis. */
    public boolean hasPrerequisites(ResearchNode node) {
        for (String prerequisite : node.prerequisites()) {
            if (!isUnlocked(prerequisite)) {
                return false;
            }
        }
        return true;
    }

    /** Ce que donnerait une tentative de recherche, sans rien modifier. */
    public UnlockResult canUnlock(String id) {
        ResearchNode node = ResearchNodes.get(id);
        if (node == null) {
            return UnlockResult.UNKNOWN_NODE;
        }
        if (isUnlocked(id)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }
        if (!hasPrerequisites(node)) {
            return UnlockResult.MISSING_PREREQUISITE;
        }
        if (points < node.cost()) {
            return UnlockResult.NOT_ENOUGH_POINTS;
        }
        return UnlockResult.SUCCESS;
    }

    /** Recherche la technologie si c'est possible : les points sont dépensés. */
    public UnlockResult unlock(String id) {
        UnlockResult result = canUnlock(id);
        if (result == UnlockResult.SUCCESS) {
            points -= ResearchNodes.get(id).cost();
            unlocked.add(id);
        }
        return result;
    }

    /** Débloque la technologie sans condition ni dépense (commande d'administration). */
    public boolean forceUnlock(String id) {
        ResearchNode node = ResearchNodes.get(id);
        return node != null && !node.isStartingKnowledge() && unlocked.add(id);
    }

    /** Jalons atteints (primes déjà versées, études de l'établi d'ingénieur). */
    public Set<String> milestones() {
        return java.util.Collections.unmodifiableSet(milestones);
    }

    /** Marque un jalon comme récompensé. Renvoie faux s'il l'était déjà. */
    public boolean completeMilestone(String id) {
        return milestones.add(id);
    }

    public boolean hasMilestone(String id) {
        return milestones.contains(id);
    }

    /** Remet tout à zéro. */
    public void reset() {
        points = 0;
        unlocked.clear();
        milestones.clear();
        flights = 0;
    }
}
