package com.orbitalis.core.atlas;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.AtlasSitesPayload;
import com.orbitalis.core.network.AtlasSitesPayload.Site;
import com.orbitalis.core.network.AtlasSitesRequestPayload;
import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.satellite.AgencySatellites;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.satellite.TrackingStationBlock;
import com.orbitalis.phase1.spaceport.FlightProfile;
import com.orbitalis.phase1.spaceport.LaunchConsoleBlock;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.RocketBreakup;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Les installations que montre l'atlas : les pas de tir, les pupitres des centres de contrôle et les stations de
 * poursuite, partout où il y en a, chargés ou non.
 * <p>
 * Chaque monde en tient la liste, enregistrée avec lui ({@code data/orbitalis_atlas/.../sites.bin}). Une installation
 * y entre quand on la pose, ou quand son morceau de monde est chargé ; elle en sort quand on la casse, ou quand son
 * morceau, rechargé, ne la porte plus. L'état d'un pas de tir (phase, fusée, ergols, vol) est lu sur la table quand
 * elle est chargée ; sinon, l'atlas montre celui qu'elle avait la dernière fois qu'on l'a vue.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class AtlasSites {
    private static final Map<ResourceKey<Level>, AtlasSites> ALL = new ConcurrentHashMap<>();

    private final Path file;
    /** Les installations : position (codée) et sorte. */
    private final Map<Long, Integer> kinds = new ConcurrentHashMap<>();
    /** Dernier état connu de chaque pas de tir. */
    private final Map<Long, Site> seen = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    private AtlasSites(Path file) {
        this.file = file;
        read();
    }

    /** La liste des installations de ce monde. */
    public static AtlasSites of(ServerLevel level) {
        return ALL.computeIfAbsent(level.dimension(), dimension -> {
            var id = dimension.identifier();
            return new AtlasSites(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("orbitalis_atlas")
                    .resolve(id.getNamespace()).resolve(id.getPath().replace('/', '_')).resolve("sites.bin"));
        });
    }

    /** Une installation vient d'être posée, ou d'être trouvée. */
    public static void note(Level level, BlockPos pos, int kind) {
        if (level instanceof ServerLevel server) {
            Integer before = of(server).kinds.put(pos.asLong(), kind);
            if (before == null || before != kind) {
                of(server).dirty = true;
            }
        }
    }

    /** Une installation vient d'être cassée. */
    public static void forget(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            AtlasSites sites = of(server);
            if (sites.kinds.remove(pos.asLong()) != null) {
                sites.dirty = true;
            }
            sites.seen.remove(pos.asLong());
        }
    }

    /** Sorte de l'installation connue à cette place, ou -1. */
    public int kindAt(BlockPos pos) {
        return kinds.getOrDefault(pos.asLong(), -1);
    }

    /** Sorte d'installation de ce bloc, ou -1. */
    private static int kindOf(Block block) {
        return block instanceof LaunchConsoleBlock ? AtlasSitesPayload.CONSOLE : block instanceof TrackingStationBlock ? AtlasSitesPayload.STATION : -1;
    }

    // ------------------------------------------------------------------
    // Enregistrement
    // ------------------------------------------------------------------

    private void read() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            int count = in.readInt();
            for (int k = 0; k < count; k++) {
                long pos = in.readLong();
                kinds.put(pos, (int) in.readByte());
            }
        } catch (IOException problem) {
            // Une liste abîmée : elle se refera au fil des chargements.
            Orbitalis.LOGGER.warn("Atlas : liste des installations illisible ({})", file, problem);
        }
    }

    private void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
                Map<Long, Integer> copy = new HashMap<>(kinds);
                out.writeInt(copy.size());
                for (Map.Entry<Long, Integer> entry : copy.entrySet()) {
                    out.writeLong(entry.getKey());
                    out.writeByte(entry.getValue());
                }
            }
        } catch (IOException problem) {
            dirty = true;
            Orbitalis.LOGGER.warn("Atlas : liste des installations non enregistrée ({})", file, problem);
        }
    }

    // ------------------------------------------------------------------
    // État
    // ------------------------------------------------------------------

    /** L'état d'un pas de tir chargé, à l'instant. */
    private static Site pad(LaunchTableBlockEntity table) {
        int phase = switch (table.getPhase()) {
            case IDLE -> AtlasSitesPayload.IDLE;
            case COUNTDOWN -> AtlasSitesPayload.COUNTDOWN;
            case FLIGHT -> AtlasSitesPayload.FLIGHT;
        };
        boolean rocket = table.hasRocket();
        RocketStats stats = rocket ? table.rocketStats() : null;
        int fuel = rocket ? table.fuelPercent() : 0;
        float blast = 0.0F;
        if (stats != null) {
            boolean solid = !stats.stages().isEmpty() && stats.stages().get(0).propellant().isSolid();
            blast = (float) RocketBreakup.blastRadius(stats.liftoffMass(), solid ? 1.0 : fuel / 100.0, solid);
        }
        float downrange = 0.0F;
        float altitude = 0.0F;
        float driftX = 0.0F;
        float driftZ = 0.0F;
        if (stats != null && phase == AtlasSitesPayload.FLIGHT) {
            double progress = Math.clamp(table.phaseTicks() / (double) table.flightTicks(), 0.0, 1.0);
            downrange = (float) FlightProfile.downrangeKm(stats, progress);
            altitude = (float) FlightProfile.altitudeKm(stats, progress);
            driftX = (float) table.flightOffset().x;
            driftZ = (float) table.flightOffset().z;
        }
        return new Site(AtlasSitesPayload.PAD, table.getBlockPos(), true, phase, rocket, table.getLastResult().ordinal(),
                stats == null ? -1 : stats.reach().ordinal(), fuel, 2 * table.getComplex().radius() + 1, table.usableTowerHeight(),
                table.getTrenchDirection().get2DDataValue(), (int) Math.min(table.phaseTicks(), Integer.MAX_VALUE), blast, driftX, driftZ, downrange,
                altitude, table.flightTicks());
    }

    /**
     * Un joueur consulte l'atlas d'un pupitre : les installations que ce pupitre connaît lui sont envoyées, avec leur
     * état, de la plus proche à la plus lointaine.
     */
    public static void request(ServerPlayer player, AtlasSitesRequestPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos source = payload.source();
        if (!AtlasAccess.allowed(player, source)) {
            return;
        }
        AtlasSites sites = of(level);
        // Consulté depuis un pupitre : il est lui-même une installation. Depuis une tablette, il n'y a rien à noter.
        if (AtlasAccess.atConsole(player, source)) {
            note(level, source, AtlasSitesPayload.CONSOLE);
        }
        for (BlockPos table : LaunchTableBlockEntity.loadedTables(level)) {
            note(level, table, AtlasSitesPayload.PAD);
        }
        String agency = ResearchManager.agencyKey(player);
        long now = SatelliteManager.now(level.getServer());
        boolean limited = SatelliteManager.limited();
        AgencySatellites fleet = SatelliteManager.of(level.getServer(), agency);
        int satellites = 0;
        List<Integer> active = new ArrayList<>();
        for (var satellite : fleet.all()) {
            if (AgencySatellites.isActive(satellite, now, limited)) {
                satellites++;
                active.add(satellite.number());
                active.add(satellite.type().ordinal());
            }
        }
        // Le ciel de l'agence : ce que ses satellites lui donnent, pour les calques météo et satellites de l'atlas.
        int[] forecast = SatelliteManager.forecast(level.getServer(), agency);
        AtlasSitesPayload.Sky sky = new AtlasSitesPayload.Sky(forecast[0], forecast[1], fleet.hasRelay(now, limited),
                fleet.active(com.orbitalis.phase1.satellite.SatelliteType.NAVIGATION, now, limited),
                fleet.active(com.orbitalis.phase1.satellite.SatelliteType.OBSERVATION, now, limited),
                active.stream().mapToInt(Integer::intValue).toArray(),
                AtlasTiles.knownRadius(fleet.active(com.orbitalis.phase1.satellite.SatelliteType.OBSERVATION, now, limited)));
        long reach = (long) sky.known() * sky.known();
        List<Site> found = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : new HashMap<>(sites.kinds).entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            long dx = pos.getX() - source.getX();
            long dz = pos.getZ() - source.getZ();
            if (dx * dx + dz * dz > reach) {
                continue;
            }
            int kind = entry.getValue();
            if (level.isLoaded(pos)) {
                // Chargée : l'installation est lue telle qu'elle est ; si elle n'y est plus, on l'oublie.
                if (kind == AtlasSitesPayload.PAD) {
                    BlockEntity at = level.getBlockEntity(pos);
                    if (!(at instanceof LaunchTableBlockEntity table)) {
                        forget(level, pos);
                        continue;
                    }
                    Site site = pad(table);
                    sites.seen.put(entry.getKey(), site);
                    found.add(site);
                } else if (kindOf(level.getBlockState(pos).getBlock()) != kind) {
                    forget(level, pos);
                } else {
                    int extra = kind == AtlasSitesPayload.STATION ? satellites
                            : LaunchTableBlockEntity.nearest(level, pos, LaunchConsoleBlock.RELAY_RANGE) != null ? 1 : 0;
                    found.add(new Site(kind, pos, true, AtlasSitesPayload.IDLE, false, 0, -1, 0, 0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, extra));
                }
            } else {
                Site last = sites.seen.get(entry.getKey());
                found.add(last != null ? last.remembered() : Site.bare(kind, pos));
            }
        }
        found.sort(Comparator.comparingDouble(site -> site.pos().distSqr(source)));
        // Les repères de l'agence dans ce monde, dans ce qu'elle connaît.
        List<AtlasSitesPayload.MarkerInfo> markers = new ArrayList<>();
        for (AtlasMarkers.Marker marker : AtlasMarkers.of(level.getServer(), agency, level.dimension().identifier().toString())) {
            long dx = marker.x() - source.getX();
            long dz = marker.z() - source.getZ();
            if (dx * dx + dz * dz <= reach) {
                markers.add(new AtlasSitesPayload.MarkerInfo(marker.id(), marker.x(), marker.y(), marker.z(), marker.name(), marker.author()));
            }
        }
        ModNetwork.send(player, new AtlasSitesPayload(source, found, sky, markers));
    }

    /**
     * Un joueur pose, renomme ou retire un repère de son agence depuis l'atlas d'un pupitre ; il reçoit aussitôt la
     * carte à jour.
     */
    public static void marker(ServerPlayer player, com.orbitalis.core.network.AtlasMarkerPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos source = payload.source();
        if (!AtlasAccess.allowed(player, source)) {
            return;
        }
        String agency = ResearchManager.agencyKey(player);
        switch (payload.action()) {
            case com.orbitalis.core.network.AtlasMarkerPayload.ADD -> {
                long dx = payload.x() - source.getX();
                long dz = payload.z() - source.getZ();
                long radius = AtlasTiles.knownRadius(player);
                if (dx * dx + dz * dz <= radius * radius) {
                    int y = Math.clamp(payload.y(), level.getMinY(), level.getMaxY());
                    AtlasMarkers.add(level.getServer(), agency, level.dimension().identifier().toString(), payload.x(), y, payload.z(),
                            player.getName().getString());
                }
            }
            case com.orbitalis.core.network.AtlasMarkerPayload.RENAME -> AtlasMarkers.rename(level.getServer(), agency, payload.id(), payload.name());
            case com.orbitalis.core.network.AtlasMarkerPayload.REMOVE -> AtlasMarkers.remove(level.getServer(), agency, payload.id());
            default -> {
            }
        }
        request(player, new AtlasSitesRequestPayload(source));
    }

    // ------------------------------------------------------------------
    // Événements
    // ------------------------------------------------------------------

    /**
     * Un morceau de monde est chargé : les installations qu'il porte entrent dans la liste, et celles qu'on y croyait
     * et qui n'y sont plus en sortent. Seules les tranches du morceau qui peuvent contenir un pupitre ou une station
     * sont parcourues.
     */
    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        AtlasSites sites = of(level);
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        for (Map.Entry<Long, Integer> entry : new HashMap<>(sites.kinds).entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (pos.getX() >> 4 != chunkX || pos.getZ() >> 4 != chunkZ) {
                continue;
            }
            boolean there = entry.getValue() == AtlasSitesPayload.PAD ? chunk.getBlockEntity(pos) instanceof LaunchTableBlockEntity
                    : kindOf(chunk.getBlockState(pos).getBlock()) == entry.getValue();
            if (!there) {
                forget(level, pos);
            }
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof LaunchTableBlockEntity) {
                note(level, blockEntity.getBlockPos(), AtlasSitesPayload.PAD);
            }
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> kindOf(state.getBlock()) >= 0)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int kind = kindOf(section.getBlockState(x, y, z).getBlock());
                        if (kind >= 0) {
                            note(level, new BlockPos((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z), kind);
                        }
                    }
                }
            }
        }
    }

    /** Un morceau de monde est déchargé : l'état de ses pas de tir est gardé, pour l'atlas. */
    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof LaunchTableBlockEntity table) {
                of(level).seen.put(table.getBlockPos().asLong(), pad(table));
            }
        }
    }

    @SubscribeEvent
    static void onSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AtlasSites sites = ALL.get(level.dimension());
            if (sites != null) {
                sites.save();
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        for (AtlasSites sites : new HashMap<>(ALL).values()) {
            sites.save();
        }
        ALL.clear();
    }
}
