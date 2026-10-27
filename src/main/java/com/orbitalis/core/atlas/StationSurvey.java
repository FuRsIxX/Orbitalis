package com.orbitalis.core.atlas;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.core.network.SurveyPayload;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Le relevé de la planète entière, vu d'une station de poursuite : la planète de son écran est bâtie avec, en cubes,
 * et on la fait tourner sur elle-même.
 * <p>
 * C'est une carte en longitude et latitude ({@link #WIDTH} × {@link #HEIGHT} cases). La station est à la longitude 0
 * et à la latitude {@link #LATITUDE} ; le terrain de chaque case est celui que le générateur du monde met à l'endroit
 * correspondant, à {@link #BLOCKS_PER_RADIAN} blocs par radian autour de la station. Le relevé est fait hors du fil du
 * serveur, sur tous les cœurs, une fois par station, dès qu'on la pose ou qu'on ouvre son écran, et envoyé dès qu'il
 * est prêt. Il est enregistré avec le monde ({@code data/orbitalis_atlas/.../station.X.Y.Z.bin}) : les ouvertures
 * suivantes, même après un redémarrage, sont immédiates.
 */
public final class StationSurvey {
    /** Cases du relevé, en longitude puis en latitude. */
    public static final int WIDTH = 256;
    public static final int HEIGHT = 128;
    /** Rang du relevé dans {@link SurveyPayload} : celui de la planète entière. */
    public static final int LOD = 2;
    /** Latitude de la station, en radians (celle d'un grand centre spatial, à 28,6° nord). */
    public static final double LATITUDE = 0.5;
    /** Échelle de la planète : blocs du monde par radian de longitude ou de latitude. */
    public static final double BLOCKS_PER_RADIAN = 10_430.0;
    /** Nombre de relevés gardés en mémoire : au-delà, le plus ancien consulté est oublié. */
    private static final int KEPT = 16;
    private static final Map<String, CompletableFuture<int[]>> SURVEYS = new LinkedHashMap<>(KEPT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<int[]>> eldest) {
            return size() > KEPT;
        }
    };
    /** Les joueurs qui attendent un relevé en cours. */
    private static final Map<String, Set<UUID>> WAITING = new HashMap<>();

    private StationSurvey() {
    }

    /**
     * Un joueur ouvre l'écran de cette station, ou le redemande : son relevé lui est envoyé s'il est prêt ; sinon il
     * est lancé, et lui sera envoyé dès qu'il le sera.
     */
    public static synchronized void request(ServerPlayer player, ServerLevel level, BlockPos station) {
        String key = key(level, station);
        CompletableFuture<int[]> job = SURVEYS.get(key);
        if (job != null && job.isDone()) {
            int[] cells = job.isCompletedExceptionally() ? null : job.getNow(null);
            if (cells != null) {
                ModNetwork.send(player, new SurveyPayload(station, LOD, cells));
                return;
            }
        }
        WAITING.computeIfAbsent(key, k -> new HashSet<>()).add(player.getUUID());
        start(level, station);
    }

    /** Lance le relevé de cette station s'il n'est ni prêt ni en cours : dès qu'on la pose, il se prépare. */
    public static synchronized void start(ServerLevel level, BlockPos station) {
        String key = key(level, station);
        CompletableFuture<int[]> existing = SURVEYS.get(key);
        if (existing != null && !(existing.isDone() && existing.getNow(null) == null)) {
            return;
        }
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        BlockPos centre = station.immutable();
        MinecraftServer server = level.getServer();
        Path file = file(level, station);
        CompletableFuture<int[]> job = CompletableFuture.supplyAsync(() -> {
            int[] saved = read(file);
            if (saved != null) {
                return saved;
            }
            int[] cells = new int[WIDTH * HEIGHT];
            // Une case couvre des centaines de blocs : on y relève cinq points (le centre et quatre autour), et elle
            // prend le terrain qui l'emporte, terre ou mer. Les côtes se dessinent, sans semis d'îlots. Les lignes se
            // calculent en même temps, sur tous les cœurs.
            double step = 2.0 * Math.PI / WIDTH * BLOCKS_PER_RADIAN / 3.0;
            java.util.stream.IntStream.range(0, HEIGHT).parallel().forEach(row -> {
                int[] samples = new int[5];
                double latitude = (0.5 - (row + 0.5) / HEIGHT) * Math.PI;
                int z = centre.getZ() - (int) Math.round((latitude - LATITUDE) * BLOCKS_PER_RADIAN);
                for (int column = 0; column < WIDTH; column++) {
                    double longitude = ((column + 0.5) / WIDTH - 0.5) * 2.0 * Math.PI;
                    int x = centre.getX() + (int) Math.round(longitude * BLOCKS_PER_RADIAN);
                    int land = 0;
                    for (int k = 0; k < 5; k++) {
                        int ox = k == 0 ? 0 : (k & 1) == 1 ? -1 : 1;
                        int oz = k == 0 ? 0 : k < 3 ? -1 : 1;
                        samples[k] = LaunchTableBlockEntity.farCell(generator, random, level, sea, x + (int) Math.round(ox * step),
                                z + (int) Math.round(oz * step));
                        land += LaunchTableBlockEntity.isFarWater(samples[k] & 0xFFFFFF) ? 0 : 1;
                    }
                    boolean wantLand = land >= 3;
                    int chosen = samples[0];
                    if (LaunchTableBlockEntity.isFarWater(chosen & 0xFFFFFF) == wantLand) {
                        for (int sample : samples) {
                            if (LaunchTableBlockEntity.isFarWater(sample & 0xFFFFFF) != wantLand) {
                                chosen = sample;
                                break;
                            }
                        }
                    }
                    cells[row * WIDTH + column] = chosen;
                }
            });
            write(file, cells);
            return cells;
        });
        SURVEYS.put(key, job);
        // Prêt : envoyé aussitôt à ceux qui l'attendent, sans qu'ils aient à le redemander.
        job.whenComplete((cells, problem) -> server.execute(() -> deliver(server, key, centre, cells)));
    }

    private static String key(ServerLevel level, BlockPos station) {
        return level.dimension().identifier() + "@" + station.asLong();
    }

    /** Le fichier où le relevé de cette station est enregistré. */
    private static Path file(ServerLevel level, BlockPos station) {
        var id = level.dimension().identifier();
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("orbitalis_atlas").resolve(id.getNamespace())
                .resolve(id.getPath().replace('/', '_')).resolve("station." + station.getX() + "." + station.getY() + "." + station.getZ() + ".bin");
    }

    private static int[] read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != WIDTH * HEIGHT) {
                return null;
            }
            int[] cells = new int[WIDTH * HEIGHT];
            for (int k = 0; k < cells.length; k++) {
                cells[k] = in.readInt();
            }
            return cells;
        } catch (IOException problem) {
            return null;
        }
    }

    private static void write(Path file, int[] cells) {
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(cells.length);
                for (int cell : cells) {
                    out.writeInt(cell);
                }
            }
        } catch (IOException problem) {
            com.orbitalis.Orbitalis.LOGGER.warn("Station : relevé de la planète non enregistré ({})", file, problem);
        }
    }

    private static synchronized void deliver(MinecraftServer server, String key, BlockPos station, int[] cells) {
        Set<UUID> waiting = WAITING.remove(key);
        if (waiting == null || cells == null) {
            return;
        }
        for (UUID id : waiting) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                ModNetwork.send(player, new SurveyPayload(station, LOD, cells));
            }
        }
    }
}
