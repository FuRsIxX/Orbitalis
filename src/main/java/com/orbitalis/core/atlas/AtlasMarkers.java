package com.orbitalis.core.atlas;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.orbitalis.Orbitalis;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Les repères de l'atlas : des points que les membres d'une agence posent sur la carte, et que tous ses membres
 * voient. Chacun a un nom, l'auteur qui l'a posé et une couleur. Ils sont enregistrés avec le monde
 * ({@code data/orbitalis_atlas/markers.bin}).
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class AtlasMarkers {
    /** Repères qu'une agence peut avoir, au plus. */
    public static final int MAX_PER_AGENCY = 64;
    /** Longueur d'un nom de repère, au plus. */
    public static final int MAX_NAME = 32;

    /**
     * Un repère.
     *
     * @param id        son numéro, propre à l'agence
     * @param dimension le monde où il est posé
     * @param name      son nom ; vide tant qu'on ne l'a pas nommé (l'atlas montre alors « Repère n »)
     */
    public record Marker(int id, String dimension, int x, int y, int z, String name, String author) {
    }

    private static Path file;
    private static final Map<String, List<Marker>> BY_AGENCY = new HashMap<>();
    private static final Map<String, Integer> NEXT_ID = new HashMap<>();
    private static boolean dirty;

    private AtlasMarkers() {
    }

    private static synchronized void open(MinecraftServer server) {
        Path wanted = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("orbitalis_atlas").resolve("markers.bin");
        if (wanted.equals(file)) {
            return;
        }
        file = wanted;
        BY_AGENCY.clear();
        NEXT_ID.clear();
        dirty = false;
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new java.util.zip.GZIPInputStream(Files.newInputStream(file)))) {
            int agencies = in.readInt();
            for (int a = 0; a < agencies; a++) {
                String agency = in.readUTF();
                NEXT_ID.put(agency, in.readInt());
                int count = in.readInt();
                List<Marker> markers = new ArrayList<>();
                for (int k = 0; k < count; k++) {
                    markers.add(new Marker(in.readInt(), in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readUTF(), in.readUTF()));
                }
                BY_AGENCY.put(agency, markers);
            }
        } catch (IOException problem) {
            Orbitalis.LOGGER.warn("Atlas : repères illisibles ({})", file, problem);
        }
    }

    private static synchronized void save() {
        if (!dirty || file == null) {
            return;
        }
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(BY_AGENCY.size());
                for (Map.Entry<String, List<Marker>> entry : BY_AGENCY.entrySet()) {
                    out.writeUTF(entry.getKey());
                    out.writeInt(NEXT_ID.getOrDefault(entry.getKey(), 1));
                    out.writeInt(entry.getValue().size());
                    for (Marker marker : entry.getValue()) {
                        out.writeInt(marker.id());
                        out.writeUTF(marker.dimension());
                        out.writeInt(marker.x());
                        out.writeInt(marker.y());
                        out.writeInt(marker.z());
                        out.writeUTF(marker.name());
                        out.writeUTF(marker.author());
                    }
                }
            }
        } catch (IOException problem) {
            dirty = true;
            Orbitalis.LOGGER.warn("Atlas : repères non enregistrés ({})", file, problem);
        }
    }

    /** Les repères d'une agence dans ce monde. */
    public static synchronized List<Marker> of(MinecraftServer server, String agency, String dimension) {
        open(server);
        List<Marker> found = new ArrayList<>();
        for (Marker marker : BY_AGENCY.getOrDefault(agency, List.of())) {
            if (marker.dimension().equals(dimension)) {
                found.add(marker);
            }
        }
        return found;
    }

    /** Pose un repère ; rien si l'agence en a déjà le plus qu'elle peut. */
    public static synchronized void add(MinecraftServer server, String agency, String dimension, int x, int y, int z, String author) {
        open(server);
        List<Marker> markers = BY_AGENCY.computeIfAbsent(agency, key -> new ArrayList<>());
        if (markers.size() >= MAX_PER_AGENCY) {
            return;
        }
        int id = NEXT_ID.getOrDefault(agency, 1);
        NEXT_ID.put(agency, id + 1);
        markers.add(new Marker(id, dimension, x, y, z, "", author));
        dirty = true;
    }

    /** Renomme un repère de l'agence. */
    public static synchronized void rename(MinecraftServer server, String agency, int id, String name) {
        open(server);
        String clean = name.strip();
        clean = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
        List<Marker> markers = BY_AGENCY.getOrDefault(agency, new ArrayList<>());
        for (int k = 0; k < markers.size(); k++) {
            Marker marker = markers.get(k);
            if (marker.id() == id) {
                markers.set(k, new Marker(id, marker.dimension(), marker.x(), marker.y(), marker.z(), clean, marker.author()));
                dirty = true;
            }
        }
    }

    /** Retire un repère de l'agence. */
    public static synchronized void remove(MinecraftServer server, String agency, int id) {
        open(server);
        if (BY_AGENCY.getOrDefault(agency, new ArrayList<>()).removeIf(marker -> marker.id() == id)) {
            dirty = true;
        }
    }

    @SubscribeEvent
    static void onSave(LevelEvent.Save event) {
        save();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        save();
        synchronized (AtlasMarkers.class) {
            file = null;
            BY_AGENCY.clear();
            NEXT_ID.clear();
        }
    }
}
