package com.orbitalis.phase2.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.Orbitalis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Plan des stations : les modules de chacune, avec leur emprise. Le terminal en dessine le plan ; la coupole et
 * les autres modules y sont comptés.
 */
public final class StationLayout {
    /**
     * Un module bâti.
     *
     * @param type rôle du module (voir {@link ModuleType})
     * @param min  angle de son emprise, coque comprise
     * @param max  angle opposé
     */
    public record Module(String type, BlockPos min, BlockPos max) {
        public static final Codec<Module> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Module::type),
                BlockPos.CODEC.fieldOf("min").forGetter(Module::min),
                BlockPos.CODEC.fieldOf("max").forGetter(Module::max)).apply(instance, Module::new));

        public ModuleType kind() {
            return ModuleType.byId(type);
        }
    }

    /** Sauvegarde : les modules de chaque station, repérée par le centre de son module central. */
    public static class Data extends SavedData {
        private static final Codec<Data> CODEC = Codec.unboundedMap(Codec.STRING, Module.CODEC.listOf())
                .xmap(Data::new, data -> data.modules);
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Orbitalis.id("station_layout"), Data::new, CODEC);

        private final Map<String, List<Module>> modules = new HashMap<>();

        public Data() {
        }

        private Data(Map<String, List<Module>> modules) {
            modules.forEach((station, list) -> this.modules.put(station, new ArrayList<>(list)));
        }
    }

    private StationLayout() {
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    private static String key(BlockPos station) {
        return Long.toString(station.asLong());
    }

    /** Ajoute un module au plan d'une station. */
    public static void add(MinecraftServer server, BlockPos station, ModuleType type, BlockPos a, BlockPos b) {
        Data data = data(server);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        data.modules.computeIfAbsent(key(station), k -> new ArrayList<>()).add(new Module(type.id(), min, max));
        data.setDirty();
    }

    /**
     * Modules d'une station. Une station bâtie avant que le plan existe y gagne son module central à la première
     * lecture.
     */
    public static List<Module> modules(MinecraftServer server, BlockPos station) {
        Data data = data(server);
        List<Module> list = data.modules.get(key(station));
        if (list == null || list.isEmpty()) {
            int r = StationManager.CORE_RADIUS;
            list = new ArrayList<>(List.of(new Module(ModuleType.CORE.id(), station.offset(-r, -r, -r), station.offset(r, r, r))));
            data.modules.put(key(station), list);
            data.setDirty();
        }
        return List.copyOf(list);
    }

    /** Efface le plan d'une station (sert aux captures, qui la rebâtissent). */
    public static void clear(MinecraftServer server, BlockPos station) {
        Data data = data(server);
        if (data.modules.remove(key(station)) != null) {
            data.setDirty();
        }
    }

    /** Nombre de modules de ce type dans toutes les stations de cette liste. */
    public static int count(MinecraftServer server, List<BlockPos> stations, ModuleType type) {
        int count = 0;
        for (BlockPos station : stations) {
            for (Module module : modules(server, station)) {
                count += module.kind() == type ? 1 : 0;
            }
        }
        return count;
    }
}
