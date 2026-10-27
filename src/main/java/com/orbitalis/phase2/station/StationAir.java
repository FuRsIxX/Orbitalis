package com.orbitalis.phase2.station;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Air respirable des stations. Chaque générateur d'air en service déclare ici le volume qu'il pressurise :
 * l'ensemble des cases libres qu'on peut atteindre depuis lui sans traverser de paroi. Tant que ce volume
 * est clos, on y respire ; s'il fuit vers le vide (une paroi manquante, une porte ouverte), il n'y a plus d'air.
 */
public final class StationAir {
    /** Volume maximal qu'un générateur peut pressuriser, en cases. Au-delà, on considère que la pièce fuit. */
    public static final int MAX_VOLUME = 4000;
    /** Distance maximale entre le générateur et une case de son volume, en blocs : une station de plusieurs modules. */
    private static final int MAX_REACH = 64;

    /** Volume pressurisé par chaque générateur en service, repéré par sa position. */
    private static final Map<GlobalPos, Set<Long>> ROOMS = new ConcurrentHashMap<>();

    private StationAir() {
    }

    /** Vrai si la case contient de l'air respirable. */
    public static boolean isBreathable(Level level, BlockPos pos) {
        long key = pos.asLong();
        for (Map.Entry<GlobalPos, Set<Long>> room : ROOMS.entrySet()) {
            if (room.getKey().dimension() == level.dimension() && room.getValue().contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Volume que pressurise ce générateur, vide s'il ne pressurise rien. */
    public static Set<Long> room(Level level, BlockPos generator) {
        return ROOMS.getOrDefault(GlobalPos.of(level.dimension(), generator), Set.of());
    }

    /** Déclare le volume pressurisé par un générateur ; un volume vide le retire. */
    public static void setRoom(Level level, BlockPos generator, Set<Long> cells) {
        GlobalPos key = GlobalPos.of(level.dimension(), generator.immutable());
        if (cells.isEmpty()) {
            ROOMS.remove(key);
        } else {
            ROOMS.put(key, cells);
        }
    }

    /**
     * Relève le volume clos autour d'un générateur : toutes les cases sans obstacle qu'on atteint de proche en
     * proche depuis lui.
     *
     * @return les cases du volume, ou un ensemble vide s'il n'est pas clos (ou s'il n'y a aucune case libre)
     */
    public static Set<Long> scan(Level level, BlockPos generator) {
        Set<Long> cells = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos start = generator.relative(direction);
            if (isOpen(level, start) && cells.add(start.asLong())) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            // Trop loin ou trop grand : l'air s'échappe dans le vide.
            if (cells.size() > MAX_VOLUME || pos.distManhattan(generator) > MAX_REACH) {
                return Set.of();
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (isOpen(level, next) && cells.add(next.asLong())) {
                    queue.add(next);
                }
            }
        }
        return cells;
    }

    /**
     * Vrai si l'air peut occuper cette case : rien n'y fait obstacle (un bloc plein, une porte fermée...), ou c'est un
     * aménagement que l'air traverse (un bac de culture, une poutre treillis), ou une porte de sas ouverte.
     */
    private static boolean isOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AirPermeable || StationHatch.isOpen(state) || AirlockDoorBlock.isOpen(state)
                || state.getCollisionShape(level, pos).isEmpty();
    }

    /** Nombre de bacs de culture plantés dans ce volume : leurs plantes recyclent l'air. */
    public static int plants(Level level, Set<Long> cells) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long cell : cells) {
            pos.set(cell);
            if (HydroponicTrayBlock.growing(level.getBlockState(pos))) {
                count++;
            }
        }
        return count;
    }
}
