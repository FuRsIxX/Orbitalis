package com.orbitalis.core.atlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.AtlasRequestPayload;
import com.orbitalis.core.network.AtlasTilePayload;
import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * L'atlas, côté serveur : le terrain du monde, découpé en tuiles, pour la carte que consultent les joueurs.
 * <ul>
 * <li><b>Des tuiles.</b> Le monde est découpé en carrés de {@link #TILE} cases de côté, à neuf échelles : une case
 * vaut de 1 à 256 blocs ({@link #CELL}). La carte ne demande que les tuiles qu'elle montre, à l'échelle de son
 * zoom.</li>
 * <li><b>Le vrai monde.</b> Partout où quelqu'un est déjà passé, une tuile montre la surface réellement relevée
 * (voir {@link AtlasStore}) : constructions, arbres, lacs, tels qu'ils sont. Quand une case couvre plusieurs blocs,
 * elle montre ce qui y domine. Ce qui est chargé au moment de la demande est relevé à l'instant.</li>
 * <li><b>Là où personne n'est allé</b>, le monde n'existe pas encore : la tuile montre ce que le générateur y
 * mettra, hauteur et nature du sol, sans le créer. Le calcul se fait hors du fil du serveur.</li>
 * <li><b>Ce que l'on connaît.</b> Un centre de contrôle ne connaît que ses environs : au-delà de
 * {@link #KNOWN_RADIUS} blocs, la tuile est rendue vide, « non relevée ». D'autres moyens (satellites
 * d'observation) étendront ce rayon.</li>
 * </ul>
 * Une case est codée comme dans les relevés lointains : sa hauteur dans l'octet du haut (comptée depuis le niveau de
 * la mer, décalée de {@code MAP_HEIGHT_BIAS}), puis sa couleur, qui dit aussi sa nature.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class AtlasTiles {
    /** Côté d'une tuile, en cases. */
    public static final int TILE = 32;
    /**
     * Marge envoyée autour de chaque tuile, en cases : la carte y lit les voisines de ses cases du bord, pour que les
     * ombres et les arêtes se raccordent d'une tuile à l'autre. Une tuile envoyée fait donc {@link #SIDE} cases de côté.
     */
    public static final int MARGIN = 2;
    public static final int SIDE = TILE + 2 * MARGIN;
    /** Taille d'une case, en blocs, pour chaque échelle. */
    public static final int[] CELL = {1, 2, 4, 8, 16, 32, 64, 128, 256};
    /** Distance jusqu'à laquelle un centre de contrôle connaît le terrain, en blocs. */
    public static final int KNOWN_RADIUS = 4096;
    /** Couleur d'un feuillage réellement présent dans le monde. */
    public static final int LEAVES = 0x2F7B22;
    /** Distance à laquelle la carte de navigation d'une table de lancement peut être suivie, en blocs. */
    private static final double TABLE_REACH = 512.0;
    /**
     * Dalles d'un pas de tir : l'intérieur, le pourtour (bordé de rouge) et le carneau. Leur couleur les désigne à la
     * carte, qui les dessine à leur image.
     */
    public static final int PAD_INNER = 0xB9BDC4;
    public static final int PAD_BORDER = 0xB9BDC5;
    public static final int PAD_TRENCH = 0x454A53;
    /** Tuiles en cours de calcul pour un même joueur : au-delà, ses demandes attendent. */
    private static final int JOBS_PER_PLAYER = 16;
    private static final int CACHED_TILES = 600;
    /** Durée pendant laquelle une tuile calculée est resservie telle quelle, en ticks : le monde change, elle périme. */
    private static final int CACHE_TICKS = 400;

    private record Key(ResourceKey<Level> dimension, int lod, int tx, int tz) {
    }

    private record Job(Key key, UUID player, CompletableFuture<int[]> cells) {
    }

    private record Cached(int[] cells, long at) {
    }

    private static final Map<Key, Cached> CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Cached> eldest) {
            return size() > CACHED_TILES;
        }
    };
    private static final List<Job> JOBS = new ArrayList<>();

    private AtlasTiles() {
    }

    /** Vrai si cette tuile touche la zone que connaît le centre de contrôle placé là. */
    /**
     * Ce que connaît un centre de contrôle seul, autour de son pupitre, en blocs : ce que portent ses propres moyens.
     * Chaque satellite d'observation de l'agence l'étend, jusqu'à {@link #KNOWN_RADIUS}.
     */
    public static final int BASE_RADIUS = 1536;
    /** Ce qu'ajoute chaque satellite d'observation au rayon connu, en blocs. */
    public static final int OBSERVER_REACH = 1280;

    /** Rayon connu d'une agence qui a tant de satellites d'observation en service. */
    public static int knownRadius(int observers) {
        return Math.min(KNOWN_RADIUS, BASE_RADIUS + OBSERVER_REACH * Math.max(0, observers));
    }

    /** Rayon connu de l'agence de ce joueur. */
    public static int knownRadius(ServerPlayer player) {
        var server = player.level().getServer();
        String agency = com.orbitalis.core.research.ResearchManager.agencyKey(player);
        return knownRadius(com.orbitalis.phase1.satellite.SatelliteManager.of(server, agency).active(
                com.orbitalis.phase1.satellite.SatelliteType.OBSERVATION, com.orbitalis.phase1.satellite.SatelliteManager.now(server),
                com.orbitalis.phase1.satellite.SatelliteManager.limited()));
    }

    /** Vrai si cette tuile est dans ce que connaît un centre de contrôle, dans le plus grand rayon possible. */
    public static boolean isKnown(BlockPos source, int lod, int tx, int tz) {
        return isKnown(source, KNOWN_RADIUS, lod, tx, tz);
    }

    /** Vrai si cette tuile est dans ce que connaît un centre de contrôle, dans ce rayon. */
    public static boolean isKnown(BlockPos source, int radius, int lod, int tx, int tz) {
        double span = TILE * CELL[lod];
        double dx = (tx + 0.5) * span - source.getX();
        double dz = (tz + 0.5) * span - source.getZ();
        double reach = radius + span * 0.5;
        return dx * dx + dz * dz <= reach * reach;
    }

    /** Un joueur demande une tuile, depuis le pupitre d'un centre de contrôle ou pour la carte de navigation d'un pas de tir. */
    public static void request(ServerPlayer player, AtlasRequestPayload payload) {
        int lod = payload.lod();
        if (lod < 0 || lod >= CELL.length || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos source = payload.source();
        // L'atlas se consulte depuis un pupitre ; la carte de navigation, elle, suit une table de lancement, que
        // montrent le pupitre et les écrans de contrôle des environs.
        boolean console = AtlasAccess.allowed(player, source);
        if (!console && !level.isLoaded(source)) {
            return;
        }
        boolean pad = !console && level.getBlockEntity(source) instanceof LaunchTableBlockEntity && player.blockPosition().closerThan(source, TABLE_REACH);
        if (!console && !pad) {
            return;
        }
        // Un pupitre ne connaît que ses environs. La carte de navigation, elle, suit la fusée jusque dans l'espace :
        // elle voit le sol aussi loin que porte le regard, d'autant plus grossièrement qu'il est loin.
        if (!pad && !isKnown(source, knownRadius(player), lod, payload.tx(), payload.tz())) {
            ModNetwork.send(player, new AtlasTilePayload(lod, payload.tx(), payload.tz(), new int[0]));
            return;
        }
        Key key = new Key(level.dimension(), lod, payload.tx(), payload.tz());
        Cached cached = CACHE.get(key);
        if (cached != null && level.getGameTime() - cached.at() < CACHE_TICKS) {
            ModNetwork.send(player, new AtlasTilePayload(lod, payload.tx(), payload.tz(), cached.cells()));
            return;
        }
        int running = 0;
        for (Job job : JOBS) {
            if (job.player().equals(player.getUUID())) {
                if (job.key().equals(key)) {
                    return;
                }
                running++;
            }
        }
        // Trop de tuiles en cours pour ce joueur : sa carte redemandera celle-ci un peu plus tard.
        if (running >= JOBS_PER_PLAYER) {
            return;
        }
        JOBS.add(new Job(key, player.getUUID(), compute(level, lod, payload.tx(), payload.tz())));
    }

    /**
     * Fait calculer une tuile, hors du fil du serveur : la surface relevée là où il y en a une, le générateur ailleurs.
     * Ce qui est chargé à cet instant est d'abord relevé de nouveau, pour que la tuile soit à jour.
     *
     * @return les cases de la tuile et de sa marge, rangées par ligne (du nord au sud) puis par colonne (d'ouest en est)
     */
    public static CompletableFuture<int[]> compute(ServerLevel level, int lod, int tx, int tz) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        int cell = CELL[lod];
        AtlasStore store = AtlasStore.of(level);
        int fromX = (tx * TILE - MARGIN) * cell;
        int fromZ = (tz * TILE - MARGIN) * cell;
        // Aux échelles fines, la tuile ne couvre que quelques morceaux de monde : on les met à jour un par un. Plus
        // loin, ils sont trop nombreux ; leur dernier relevé (à leur chargement) suffit.
        if (cell <= 8) {
            for (int chunkZ = fromZ >> 4; chunkZ <= fromZ + SIDE * cell - 1 >> 4; chunkZ++) {
                for (int chunkX = fromX >> 4; chunkX <= fromX + SIDE * cell - 1 >> 4; chunkX++) {
                    store.refresh(level, chunkX, chunkZ);
                }
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            int[] cells = new int[SIDE * SIDE];
            for (int index = 0; index < cells.length; index++) {
                int x = fromX + index % SIDE * cell;
                int z = fromZ + index / SIDE * cell;
                int packed;
                if (cell <= 16) {
                    // La case tient dans un seul morceau de monde.
                    int[] columns = store.chunk(x >> 4, z >> 4);
                    packed = columns == null ? 0 : cell == 1 ? columns[(z & 15) * 16 + (x & 15)] : AtlasStore.dominant(columns, x & 15, z & 15, cell);
                } else {
                    packed = dominant(store, x >> 4, z >> 4, cell >> 4);
                }
                // Personne n'est encore passé là : ce que le générateur y mettra.
                cells[index] = packed != 0 ? packed : LaunchTableBlockEntity.farCell(generator, random, level, sea, x + cell / 2, z + cell / 2);
            }
            return cells;
        });
    }

    /** Ce qui domine un carré de morceaux de monde, d'après leurs résumés ; 0 si aucun n'a été relevé. */
    private static int dominant(AtlasStore store, int chunkX, int chunkZ, int size) {
        int[] colors = new int[16];
        int[] counts = new int[16];
        int[] heights = new int[16];
        int kinds = 0;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int packed = store.summary(chunkX + dx, chunkZ + dz);
                if (packed == 0) {
                    continue;
                }
                int rgb = packed & 0xFFFFFF;
                int slot = 0;
                while (slot < kinds && colors[slot] != rgb) {
                    slot++;
                }
                if (slot == kinds) {
                    if (kinds == colors.length) {
                        continue;
                    }
                    colors[kinds++] = rgb;
                }
                counts[slot]++;
                heights[slot] += packed >>> 24;
            }
        }
        int best = -1;
        for (int slot = 0; slot < kinds; slot++) {
            if (best < 0 || counts[slot] > counts[best]) {
                best = slot;
            }
        }
        return best < 0 ? 0 : Math.round(heights[best] / (float) counts[best]) << 24 | colors[best];
    }

    /** Les tuiles dont le calcul est fini partent vers les joueurs qui les attendaient. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        for (int k = JOBS.size() - 1; k >= 0; k--) {
            Job job = JOBS.get(k);
            if (!job.cells().isDone()) {
                continue;
            }
            JOBS.remove(k);
            // Un générateur qui ne s'y prête pas : pas de tuile, la carte montrera une zone non relevée.
            int[] cells = job.cells().isCompletedExceptionally() ? null : job.cells().getNow(null);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.player());
            if (cells != null) {
                ServerLevel level = event.getServer().getLevel(job.key().dimension());
                CACHE.put(job.key(), new Cached(cells, level == null ? 0L : level.getGameTime()));
            }
            if (player != null && player.level().dimension().equals(job.key().dimension())) {
                ModNetwork.send(player, new AtlasTilePayload(job.key().lod(), job.key().tx(), job.key().tz(), cells == null ? new int[0] : cells));
            }
        }
    }
}
