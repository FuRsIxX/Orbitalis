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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * La mémoire de l'atlas : la surface réelle du monde, telle qu'on l'a vue, bloc par bloc.
 * <p>
 * Chaque fois qu'un morceau de monde est chargé, puis quand il est déchargé, sa surface est relevée : pour chacune de
 * ses 256 colonnes, la hauteur du sol et sa couleur. Ce relevé est gardé, et enregistré avec le monde. L'atlas montre
 * ainsi le monde tel qu'il est vraiment partout où quelqu'un est déjà passé, constructions et arbres compris, même
 * quand l'endroit n'est plus chargé ; il ne s'en remet au générateur que là où personne n'est encore allé.
 * <p>
 * Les relevés sont rangés par régions de 32 morceaux de côté, un fichier par région, dans le dossier du monde
 * ({@code data/orbitalis_atlas}). Une région n'est lue que quand on en a besoin. Chaque morceau a aussi un résumé
 * (sa couleur la plus fréquente, à sa hauteur moyenne), pour les échelles où une case de la carte en couvre plusieurs.
 * <p>
 * Une colonne est codée comme une case de l'atlas (voir {@link AtlasTiles}).
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class AtlasStore {
    private static final Map<ResourceKey<Level>, AtlasStore> STORES = new ConcurrentHashMap<>();
    /** Régions gardées en mémoire : au-delà, celles qui n'ont pas servi depuis longtemps sont oubliées (après enregistrement). */
    private static final int REGIONS_KEPT = 96;
    /** Un morceau chargé n'est pas relevé de nouveau avant ce délai, en ticks. */
    private static final int FRESH_TICKS = 200;

    private static final class Region {
        final int[][] chunks = new int[1024][];
        final int[] summary = new int[1024];
        final long[] seen = new long[1024];
        volatile boolean dirty;
        volatile long used;
    }

    private final Path folder;
    private final Map<Long, Region> regions = new ConcurrentHashMap<>();
    /** Régions dont on sait qu'elles n'ont pas de fichier : inutile de le chercher à chaque fois. */
    private final Set<Long> absent = ConcurrentHashMap.newKeySet();
    private volatile long clock;

    private AtlasStore(Path folder) {
        this.folder = folder;
    }

    /** La mémoire de l'atlas pour ce monde. */
    public static AtlasStore of(ServerLevel level) {
        return STORES.computeIfAbsent(level.dimension(), dimension -> {
            var id = dimension.identifier();
            return new AtlasStore(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("orbitalis_atlas")
                    .resolve(id.getNamespace()).resolve(id.getPath().replace('/', '_')));
        });
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private Path file(int rx, int rz) {
        return folder.resolve("r." + rx + "." + rz + ".bin");
    }

    /** La région, lue sur le disque s'il le faut ; {@code null} si elle n'existe pas et qu'on ne la crée pas. */
    private Region region(int rx, int rz, boolean create) {
        long key = key(rx, rz);
        Region region = regions.get(key);
        if (region == null) {
            if (!create && absent.contains(key)) {
                return null;
            }
            synchronized (this) {
                region = regions.get(key);
                if (region == null) {
                    region = read(rx, rz);
                    if (region == null) {
                        if (!create) {
                            absent.add(key);
                            return null;
                        }
                        region = new Region();
                    }
                    absent.remove(key);
                    regions.put(key, region);
                }
            }
        }
        region.used = clock;
        return region;
    }

    private Region read(int rx, int rz) {
        Path file = file(rx, rz);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Region region = new Region();
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            int count = in.readInt();
            for (int k = 0; k < count; k++) {
                int index = in.readShort() & 0x3FF;
                region.summary[index] = in.readInt();
                int[] columns = new int[256];
                for (int c = 0; c < 256; c++) {
                    columns[c] = in.readInt();
                }
                region.chunks[index] = columns;
            }
        } catch (IOException problem) {
            // Un fichier abîmé : la région sera relevée de nouveau au fil des passages.
            Orbitalis.LOGGER.warn("Atlas : région illisible, elle sera refaite ({})", file, problem);
            return new Region();
        }
        return region;
    }

    /** Surface relevée de ce morceau de monde : 256 colonnes, rangées par ligne (z) puis par colonne (x) ; ou {@code null}. */
    public int[] chunk(int chunkX, int chunkZ) {
        Region region = region(chunkX >> 5, chunkZ >> 5, false);
        return region == null ? null : region.chunks[(chunkZ & 31) * 32 + (chunkX & 31)];
    }

    /** Résumé de ce morceau de monde : sa couleur la plus fréquente, à sa hauteur moyenne ; 0 s'il n'a pas été relevé. */
    public int summary(int chunkX, int chunkZ) {
        Region region = region(chunkX >> 5, chunkZ >> 5, false);
        return region == null ? 0 : region.summary[(chunkZ & 31) * 32 + (chunkX & 31)];
    }

    /** Relève la surface d'un morceau de monde chargé. */
    public void record(ServerLevel level, LevelChunk chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        clock = level.getGameTime();
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        int sea = level.getSeaLevel();
        int[] columns = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int index = 0; index < 256; index++) {
            columns[index] = sample(level, chunk, pos, (chunkX << 4) + (index & 15), (chunkZ << 4) + (index >> 4), sea);
        }
        Region region = region(chunkX >> 5, chunkZ >> 5, true);
        int at = (chunkZ & 31) * 32 + (chunkX & 31);
        region.chunks[at] = columns;
        region.summary[at] = dominant(columns, 0, 0, 16);
        region.seen[at] = clock;
        region.dirty = true;
    }

    /** Relève de nouveau ce morceau de monde s'il est chargé et que son relevé date. */
    public void refresh(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        Region region = region(chunkX >> 5, chunkZ >> 5, true);
        int at = (chunkZ & 31) * 32 + (chunkX & 31);
        if (region.chunks[at] == null || level.getGameTime() - region.seen[at] > FRESH_TICKS) {
            record(level, chunk);
        }
    }

    /** Une colonne du monde : le bloc de surface, sa hauteur et sa couleur. */
    private static int sample(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z, int sea) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockState state = chunk.getBlockState(pos.set(x, y, z));
        // Une herbe haute, une fleur, une torche : c'est le bloc dessous qui fait le terrain.
        if (!state.isAir() && state.getFluidState().isEmpty() && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                && y > level.getMinY()) {
            BlockState below = chunk.getBlockState(pos.set(x, y - 1, z));
            if (!below.isAir()) {
                state = below;
                y--;
            }
        }
        if (state.isAir()) {
            return 0;
        }
        int rgb;
        if (!state.getFluidState().isEmpty()) {
            // L'eau est d'autant plus sombre qu'elle est profonde.
            int depth = y - chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            rgb = depth > 14 ? LaunchTableBlockEntity.FAR_DEEP : depth > 4 ? LaunchTableBlockEntity.FAR_SEA : LaunchTableBlockEntity.FAR_SHALLOW;
        } else if (state.getBlock() instanceof com.orbitalis.phase1.spaceport.LaunchPadBlock) {
            // Une dalle de pas de tir : la carte la dessinera à son image, pourtour rouge et carneau compris.
            rgb = switch (state.getValue(com.orbitalis.phase1.spaceport.LaunchPadBlock.LOOK)) {
                case BORDER -> AtlasTiles.PAD_BORDER;
                case TRENCH -> AtlasTiles.PAD_TRENCH;
                default -> AtlasTiles.PAD_INNER;
            };
        } else if (state.is(BlockTags.LEAVES)) {
            rgb = AtlasTiles.LEAVES;
        } else {
            MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            rgb = color == MapColor.GRASS ? LaunchTableBlockEntity.FAR_GRASS
                    : color == MapColor.NONE ? 0x7A8088 : color.calculateARGBColor(MapColor.Brightness.NORMAL) & 0xFFFFFF;
        }
        return Math.clamp(y - sea + LaunchTableBlockEntity.MAP_HEIGHT_BIAS, 1, 255) << 24 | rgb;
    }

    /**
     * Ce qui domine un carré de colonnes d'un morceau de monde : sa couleur la plus fréquente, à la hauteur moyenne
     * des colonnes qui la portent. C'est ce qu'une case de la carte montre quand elle couvre plusieurs blocs.
     *
     * @param fromX première colonne du carré, de 0 à 15
     * @param size  côté du carré, en blocs
     */
    public static int dominant(int[] columns, int fromX, int fromZ, int size) {
        int[] colors = new int[12];
        int[] counts = new int[12];
        int[] heights = new int[12];
        int kinds = 0;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int packed = columns[(fromZ + dz) * 16 + fromX + dx];
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

    /** Enregistre les régions qui ont changé, et oublie celles qui n'ont pas servi depuis longtemps. */
    public void save() {
        List<Long> idle = new ArrayList<>();
        for (Map.Entry<Long, Region> entry : regions.entrySet()) {
            Region region = entry.getValue();
            if (region.dirty) {
                region.dirty = false;
                int rx = (int) (entry.getKey() >> 32);
                int rz = (int) (long) entry.getKey();
                try {
                    Files.createDirectories(folder);
                    try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file(rx, rz))))) {
                        int count = 0;
                        for (int[] columns : region.chunks) {
                            count += columns == null ? 0 : 1;
                        }
                        out.writeInt(count);
                        for (int index = 0; index < 1024; index++) {
                            int[] columns = region.chunks[index];
                            if (columns == null) {
                                continue;
                            }
                            out.writeShort(index);
                            out.writeInt(region.summary[index]);
                            for (int column : columns) {
                                out.writeInt(column);
                            }
                        }
                    }
                } catch (IOException problem) {
                    region.dirty = true;
                    Orbitalis.LOGGER.warn("Atlas : région non enregistrée ({}, {})", rx, rz, problem);
                }
            } else if (clock - region.used > 6000) {
                idle.add(entry.getKey());
            }
        }
        if (regions.size() > REGIONS_KEPT) {
            for (Long key : idle) {
                regions.remove(key);
            }
        }
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            of(level).record(level, event.getChunk());
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        // Au moment où il est déchargé, le morceau porte ce que les joueurs y ont bâti : c'est ce relevé-là qui reste.
        if (event.getLevel() instanceof ServerLevel level) {
            of(level).record(level, event.getChunk());
        }
    }

    @SubscribeEvent
    static void onSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AtlasStore store = STORES.get(level.dimension());
            if (store != null) {
                store.save();
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        for (AtlasStore store : new HashMap<>(STORES).values()) {
            store.save();
        }
        STORES.clear();
    }
}
