package com.orbitalis.phase1.conduit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Réseau de conduits : trouve tous les blocs raccordés à un ensemble de conduits qui se touchent.
 * <p>
 * Un conduit ne stocke rien. Quand une machine lui envoie de l'énergie ou un fluide, il parcourt le réseau
 * auquel il appartient et livre directement aux machines raccordées. Le résultat du parcours est mémorisé
 * jusqu'au prochain changement du réseau (conduit ou machine posé ou retiré).
 * <p>
 * Le réseau gère aussi la lueur des conduits : tant que quelque chose y circule, tous ses conduits
 * s'illuminent progressivement ; quand le transfert s'arrête, ils s'éteignent de la même façon.
 */
public final class ConduitNetwork {
    /** Taille maximale d'un réseau, en conduits : au-delà, les machines les plus lointaines ne sont plus desservies. */
    public static final int MAX_SIZE = 1024;

    /** Intervalle entre deux paliers de lueur, en ticks : l'allumage complet prend trois paliers. */
    private static final int GLOW_STEP_TICKS = 4;
    /** Durée pendant laquelle un réseau reste allumé après le dernier transfert, pour ne pas clignoter. */
    private static final int ACTIVE_TICKS = 40;

    /**
     * Bloc raccordé au réseau.
     *
     * @param pos     position du bloc
     * @param side    face du bloc touchée par le conduit
     * @param conduit le conduit qui le touche
     */
    public record Endpoint(BlockPos pos, Direction side, BlockPos conduit) {
    }

    /** Un réseau : ses conduits, les blocs qu'il dessert, et le moment du dernier transfert. */
    private static final class Network {
        private final List<BlockPos> conduits;
        private final List<Endpoint> endpoints;
        private long lastActive = Long.MIN_VALUE / 2;
        /** Numéro de contenu du dernier fluide passé (voir ConduitBlock.contentOf) ; 0 pour de l'énergie. */
        private int content;
        /** Vrai quand la forme du réseau a changé depuis le parcours : il doit être recherché à nouveau. */
        private boolean stale;

        private Network(List<BlockPos> conduits, List<Endpoint> endpoints) {
            this.conduits = conduits;
            this.endpoints = endpoints;
        }
    }

    /** Réseaux mémorisés, par monde puis par conduit : tous les conduits d'un réseau partagent le même objet. */
    private static final Map<Level, Map<BlockPos, Network>> CACHE = new WeakHashMap<>();
    /** Réseaux dont la lueur est en cours d'évolution (allumés, ou en train de s'éteindre). */
    private static final Map<Level, Set<Network>> GLOWING = new WeakHashMap<>();

    private ConduitNetwork() {
    }

    /** Oublie les parcours mémorisés d'un monde. À appeler dès qu'un réseau change de forme. */
    public static synchronized void invalidate(Level level) {
        CACHE.remove(level);
        Set<Network> glowing = GLOWING.get(level);
        if (glowing != null) {
            for (Network network : glowing) {
                network.stale = true;
            }
        }
    }

    /**
     * Blocs raccordés au réseau dont fait partie le conduit situé en {@code start}.
     *
     * @param conduit    type de conduit qui forme le réseau
     * @param capability capacité transportée (énergie ou fluide)
     */
    public static synchronized List<Endpoint> endpoints(Level level, BlockPos start, Block conduit,
            BlockCapability<?, Direction> capability) {
        return network(level, start, conduit, capability).endpoints;
    }

    /** Signale qu'un transfert vient de passer par ce conduit : son réseau s'illumine. */
    public static synchronized void markActive(Level level, BlockPos start, Block conduit, BlockCapability<?, Direction> capability) {
        markActive(level, start, conduit, capability, 0);
    }

    /**
     * Signale qu'un transfert vient de passer par ce conduit, et ce qui a circulé.
     *
     * @param content numéro de contenu du fluide (voir ConduitBlock.contentOf), ou 0 pour de l'énergie
     */
    public static synchronized void markActive(Level level, BlockPos start, Block conduit, BlockCapability<?, Direction> capability,
            int content) {
        if (level instanceof ServerLevel) {
            Network network = network(level, start, conduit, capability);
            network.lastActive = level.getGameTime();
            network.content = content;
            glowing(level).add(network);
        }
    }

    /**
     * Fait surveiller le réseau de ce conduit sans le déclarer actif : s'il est resté allumé alors que plus
     * rien n'y circule (monde rechargé, par exemple), il s'éteindra.
     */
    public static synchronized void watch(ServerLevel level, BlockPos start, ConduitBlock conduit) {
        glowing(level).add(network(level, start, conduit, conduit.getCapability()));
    }

    private static Set<Network> glowing(Level level) {
        return GLOWING.computeIfAbsent(level, key -> Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Network network(Level level, BlockPos start, Block conduit, BlockCapability<?, Direction> capability) {
        Map<BlockPos, Network> networks = CACHE.computeIfAbsent(level, key -> new HashMap<>());
        Network network = networks.get(start);
        if (network == null) {
            network = search(level, start.immutable(), conduit, capability);
            for (BlockPos pos : network.conduits) {
                networks.put(pos, network);
            }
        }
        return network;
    }

    /** Parcours en largeur des conduits qui se touchent, à partir de {@code start}. */
    private static Network search(Level level, BlockPos start, Block conduit, BlockCapability<?, Direction> capability) {
        List<Endpoint> endpoints = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && visited.size() <= MAX_SIZE) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction);
                if (level.getBlockState(neighbour).is(conduit)) {
                    if (visited.add(neighbour)) {
                        queue.add(neighbour);
                    }
                } else if (!(level.getBlockState(neighbour).getBlock() instanceof ConduitBlock)
                        && level.getCapability(capability, neighbour, direction.getOpposite()) != null) {
                    endpoints.add(new Endpoint(neighbour, direction.getOpposite(), current));
                }
            }
        }
        return new Network(List.copyOf(visited), List.copyOf(endpoints));
    }

    // ------------------------------------------------------------------
    // Lueur des conduits
    // ------------------------------------------------------------------

    /** À chaque palier, les conduits des réseaux surveillés se rapprochent d'un cran de leur lueur cible. */
    public static synchronized void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % GLOW_STEP_TICKS != 0) {
            return;
        }
        Set<Network> glowing = GLOWING.get(level);
        if (glowing == null || glowing.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        for (Network network : List.copyOf(glowing)) {
            if (network.stale) {
                // Le réseau a changé de forme : on surveille à sa place ceux qui portent maintenant ses conduits.
                glowing.remove(network);
                for (BlockPos pos : network.conduits) {
                    if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof ConduitBlock conduit) {
                        Network current = network(level, pos, conduit, conduit.getCapability());
                        // Le réseau qui prend la suite hérite du dernier transfert, et de ce qui circulait.
                        if (network.lastActive > current.lastActive) {
                            current.lastActive = network.lastActive;
                            current.content = network.content;
                        }
                        glowing.add(current);
                    }
                }
                continue;
            }

            int target = now - network.lastActive <= ACTIVE_TICKS ? ConduitBlock.MAX_GLOW : 0;
            boolean anyLit = false;
            for (BlockPos pos : network.conduits) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof ConduitBlock)) {
                    continue;
                }
                BlockState updated = state;
                // Un câble s'allume et s'éteint par paliers.
                if (state.hasProperty(ConduitBlock.GLOW)) {
                    int glow = state.getValue(ConduitBlock.GLOW);
                    int next = glow + Integer.signum(target - glow);
                    updated = updated.setValue(ConduitBlock.GLOW, next);
                    anyLit |= next > 0;
                }
                // Tant qu'un tuyau est en service, il montre ce qui y circule ; au repos, il redevient vide.
                if (state.hasProperty(ConduitBlock.CONTENT)) {
                    int content = target > 0 ? Math.min(network.content, ConduitBlock.MAX_CONTENT) : 0;
                    updated = updated.setValue(ConduitBlock.CONTENT, content);
                    anyLit |= content > 0;
                }
                if (updated != state) {
                    // Envoyé aux joueurs, sans prévenir les voisins : la forme du réseau ne change pas.
                    level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            if (target == 0 && !anyLit) {
                glowing.remove(network);
            }
        }
    }
}
