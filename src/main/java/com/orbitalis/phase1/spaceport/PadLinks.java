package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.conduit.ConduitNetwork;
import com.orbitalis.phase1.conduit.DataLink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Les liaisons entre un pupitre (de lancement ou d'essai) et le pas de tir qu'il commande.
 * <ul>
 * <li>par câble : un câble de données relie le pupitre au port de données d'un pas de tir ; c'est la liaison
 * ordinaire, sans limite de distance ;</li>
 * <li>sans fil : un module sans fil, utilisé sur le pupitre puis sur le pas de tir, les relie à distance (voir
 * {@link WirelessModuleItem}). La liaison est gardée avec le monde.</li>
 * </ul>
 * Sans l'une ni l'autre, un pupitre n'est relié à rien.
 */
public final class PadLinks {
    private PadLinks() {
    }

    /** Les cases d'un poste : la sienne et, pour un pupitre de lancement, ses ailes, où un câble peut aussi se brancher. */
    private static List<BlockPos> cells(Level level, BlockPos device) {
        List<BlockPos> cells = new ArrayList<>();
        cells.add(device);
        BlockState state = level.getBlockState(device);
        if (state.is(ModBlocks.LAUNCH_CONSOLE.get()) && state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            cells.addAll(Workstation.wings(device, state.getValue(HorizontalDirectionalBlock.FACING)));
        }
        return cells;
    }

    /**
     * La table du pas de tir qu'un câble de données relie à ce bloc : par un port de données, ou par un autre pupitre
     * relié lui-même à un pas de tir. {@code null} s'il n'y en a pas.
     */
    public static LaunchTableBlockEntity byCable(Level level, BlockPos device) {
        for (BlockPos cell : cells(level, device)) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = cell.relative(direction);
                BlockState state = level.getBlockState(neighbour);
                if (isDataPort(state)) {
                    LaunchTableBlockEntity table = tableOfPort(level, neighbour);
                    if (table != null) {
                        return table;
                    }
                }
                if (!state.is(ModBlocks.DATA_CABLE.get())) {
                    continue;
                }
                for (ConduitNetwork.Endpoint endpoint : ConduitNetwork.endpoints(level, neighbour, state.getBlock(), DataLink.BLOCK)) {
                    if (isDataPort(level.getBlockState(endpoint.pos()))) {
                        LaunchTableBlockEntity table = tableOfPort(level, endpoint.pos());
                        if (table != null) {
                            return table;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Vrai pour un port de pas de tir réglé sur les données. */
    public static boolean isDataPort(BlockState state) {
        return state.is(ModBlocks.FUEL_PORT.get()) && state.getValue(FuelPortBlock.KIND) == FuelPortBlock.Kind.DATA;
    }

    /** La table du pas de tir dont ce port fait partie. */
    public static LaunchTableBlockEntity tableOfPort(Level level, BlockPos port) {
        return LaunchTableBlockEntity.nearest(level, port, LaunchComplex.REACH + 2);
    }

    /** La table qu'une liaison sans fil donne à ce pupitre, ou {@code null}. */
    public static BlockPos wireless(ServerLevel level, BlockPos device) {
        return Saved.of(level).links.get(device);
    }

    /** Relie sans fil un pupitre à la table d'un pas de tir. */
    public static void linkWireless(ServerLevel level, BlockPos device, BlockPos table) {
        Saved saved = Saved.of(level);
        saved.links.put(device.immutable(), table.immutable());
        saved.setDirty();
    }

    /** Les liaisons sans fil d'un monde : pupitre, puis table. */
    static final class Saved extends SavedData {
        private record Link(BlockPos device, BlockPos table) {
            static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BlockPos.CODEC.fieldOf("device").forGetter(Link::device),
                    BlockPos.CODEC.fieldOf("table").forGetter(Link::table)).apply(i, Link::new));
        }

        private static final Codec<Saved> CODEC = Link.CODEC.listOf().xmap(Saved::new,
                saved -> saved.links.entrySet().stream().map(entry -> new Link(entry.getKey(), entry.getValue())).toList());
        static final SavedDataType<Saved> TYPE = new SavedDataType<>(Orbitalis.id("wireless_links"), Saved::new, CODEC);

        private final Map<BlockPos, BlockPos> links = new HashMap<>();

        Saved() {
        }

        private Saved(List<Link> links) {
            for (Link link : links) {
                this.links.put(link.device(), link.table());
            }
        }

        static Saved of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(TYPE);
        }
    }
}
