package com.orbitalis.phase1.satellite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Satellites d'une agence spatiale, et les données qu'ils ont accumulées.
 * <p>
 * Cette classe ne contient que la logique (aucun accès au monde ni au réseau) : toutes les méthodes reçoivent
 * la date courante, en ticks de jeu, ce qui permet de les vérifier sans attendre.
 * <p>
 * Un satellite produit des données en continu. Il les garde en mémoire jusqu'à ce qu'elles soient reçues au sol,
 * mais sa mémoire est limitée : au-delà de {@link #BUFFER_DAYS} jours, les plus anciennes sont perdues.
 * En fin de vie il ne produit plus rien ; il reste dans la liste tant qu'il lui reste des données à transmettre.
 */
public final class AgencySatellites {
    public static final Codec<AgencySatellites> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Satellite.CODEC.listOf().fieldOf("satellites").forGetter(satellites -> List.copyOf(satellites.satellites)),
            Codec.INT.optionalFieldOf("launched", 0).forGetter(AgencySatellites::launched),
            Codec.STRING.listOf().optionalFieldOf("discovered", List.of()).forGetter(satellites -> List.copyOf(satellites.discovered)),
            Codec.STRING.listOf().optionalFieldOf("imaged", List.of()).forGetter(satellites -> List.copyOf(satellites.imaged)),
            Codec.STRING.optionalFieldOf("observing", "").forGetter(satellites -> satellites.observing),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(satellites -> Map.copyOf(satellites.progress)))
            .apply(instance, AgencySatellites::new));

    /** Durée d'un jour de jeu, en ticks. */
    public static final long DAY = 24_000;
    /** Mémoire de bord d'un satellite, en jours de données. */
    public static final int BUFFER_DAYS = 3;

    private final List<Satellite> satellites = new ArrayList<>();
    /** Nombre de satellites mis en orbite depuis toujours : le suivant portera le numéro d'après. */
    private int launched;
    /** L'observatoire (voir {@link Observatory}) : les astres découverts, ceux dont l'image est reçue au sol. */
    private final Set<String> discovered = new LinkedHashSet<>();
    private final Set<String> imaged = new LinkedHashSet<>();
    /** L'astre sur lequel les instruments sont pointés, ou "" ; le temps d'observation déjà fait sur chaque astre. */
    private String observing = "";
    private final Map<String, Integer> progress = new HashMap<>();

    public AgencySatellites() {
    }

    private AgencySatellites(List<Satellite> satellites, int launched, List<String> discovered, List<String> imaged, String observing,
            Map<String, Integer> progress) {
        this.satellites.addAll(satellites);
        this.launched = launched;
        this.discovered.addAll(discovered);
        this.imaged.addAll(imaged);
        this.observing = observing;
        this.progress.putAll(progress);
    }

    /** Vrai si cet astre est découvert : ses données sont là, son image peut être reçue. */
    public boolean isDiscovered(CelestialBody body) {
        return discovered.contains(body.id());
    }

    /** Vrai si l'image de cet astre est reçue au sol : il est dans le catalogue. */
    public boolean isImaged(CelestialBody body) {
        return imaged.contains(body.id());
    }

    /** L'astre observé, ou {@code null}. */
    public CelestialBody observing() {
        return CelestialBody.byId(observing);
    }

    public void observe(CelestialBody body) {
        observing = body == null ? "" : body.id();
    }

    /** Temps d'observation déjà fait sur cet astre, en ticks pondérés par la puissance des instruments. */
    public int progress(CelestialBody body) {
        return progress.getOrDefault(body.id(), 0);
    }

    public void addProgress(CelestialBody body, int amount) {
        progress.merge(body.id(), amount, Integer::sum);
    }

    public void discover(CelestialBody body) {
        discovered.add(body.id());
        progress.remove(body.id());
        if (observing.equals(body.id())) {
            observing = "";
        }
    }

    public void image(CelestialBody body) {
        imaged.add(body.id());
    }

    public int launched() {
        return launched;
    }

    /** Satellites suivis, du plus ancien au plus récent. */
    public List<Satellite> all() {
        return Collections.unmodifiableList(satellites);
    }

    public boolean isEmpty() {
        return satellites.isEmpty();
    }

    /** Met un satellite en orbite. */
    public Satellite deploy(SatelliteType type, long now) {
        return deploy(type, now, false);
    }

    /** Met un satellite en orbite ; avec un générateur à radio-isotopes, il durera deux fois plus longtemps. */
    public Satellite deploy(SatelliteType type, long now, boolean rtg) {
        Satellite satellite = new Satellite(++launched, type, now, now, rtg);
        satellites.add(satellite);
        return satellite;
    }

    /**
     * Fin de vie d'un satellite, en ticks de jeu.
     *
     * @param limited faux si la durée de vie est désactivée dans la configuration : il fonctionne alors toujours
     */
    public static long endOfLife(Satellite satellite, boolean limited) {
        return limited ? satellite.launchTime() + (long) satellite.type().lifetimeDays() * DAY * (satellite.rtg() ? 2 : 1) : Long.MAX_VALUE;
    }

    public static boolean isActive(Satellite satellite, long now, boolean limited) {
        return now < endOfLife(satellite, limited);
    }

    /** Date à partir de laquelle les données d'un satellite sont encore dans sa mémoire. */
    private static long dataStart(Satellite satellite, long now, boolean limited) {
        long until = Math.min(now, endOfLife(satellite, limited));
        return Math.max(satellite.dataSince(), until - BUFFER_DAYS * DAY);
    }

    /** Points de recherche que représentent les données en mémoire d'un satellite. */
    public static int pending(Satellite satellite, long now, boolean limited) {
        int perDay = satellite.type().pointsPerDay();
        if (perDay <= 0) {
            return 0;
        }
        long until = Math.min(now, endOfLife(satellite, limited));
        return (int) Math.max(0, (until - dataStart(satellite, now, limited)) * perDay / DAY);
    }

    /** Points de recherche en attente sur tous les satellites. */
    public int pending(long now, boolean limited) {
        int total = 0;
        for (Satellite satellite : satellites) {
            total += pending(satellite, now, limited);
        }
        return total;
    }

    /** Nombre de satellites de ce type en service. */
    public int active(SatelliteType type, long now, boolean limited) {
        int count = 0;
        for (Satellite satellite : satellites) {
            if (satellite.type() == type && isActive(satellite, now, limited)) {
                count++;
            }
        }
        return count;
    }

    /** Vrai si un satellite de télécommunications en service relaie les données vers le sol. */
    public boolean hasRelay(long now, boolean limited) {
        return active(SatelliteType.COMMUNICATION, now, limited) > 0;
    }

    /**
     * Reçoit au sol toutes les données en mémoire, puis oublie les satellites hors service qui n'ont plus rien
     * à transmettre.
     *
     * @return les points de recherche reçus
     */
    public int collect(long now, boolean limited) {
        int total = 0;
        for (int i = 0; i < satellites.size(); i++) {
            Satellite satellite = satellites.get(i);
            int points = pending(satellite, now, limited);
            if (points > 0) {
                // Seul le temps qui correspond à des points entiers est consommé : le reste compte pour la suite.
                long consumed = points * DAY / satellite.type().pointsPerDay();
                satellites.set(i, satellite.withDataSince(dataStart(satellite, now, limited) + consumed));
                total += points;
            }
        }
        satellites.removeIf(satellite -> !isActive(satellite, now, limited) && pending(satellite, now, limited) == 0);
        return total;
    }

    /** Retire tous les satellites (commande d'administration). */
    public void clear() {
        satellites.clear();
    }

    /** Oublie tout ce que l'observatoire a découvert (commande d'administration, essais). */
    public void clearObservatory() {
        discovered.clear();
        imaged.clear();
        progress.clear();
        observing = "";
    }
}
