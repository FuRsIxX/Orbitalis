package com.orbitalis.phase2.station;

import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.core.network.StationStatusPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase2.flight.CapsuleEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Relevé d'une station pour son terminal : on y passe en revue tous les équipements à portée du module central, son
 * réseau électrique, son équipage et ses vaisseaux.
 */
public final class StationStatus {
    /** Distance maximale entre un joueur et le terminal qu'il consulte, en blocs. */
    private static final double TERMINAL_RANGE = 8.0;

    private StationStatus() {
    }

    /** Envoie à un joueur l'état de la station, pour ouvrir l'écran du terminal ou l'actualiser. */
    public static void send(ServerPlayer player, BlockPos terminal, BlockPos station, boolean open) {
        ModNetwork.send(player, collect(player.level(), terminal, station, open));
    }

    /** Demande d'actualisation de l'écran d'un terminal : le joueur doit être devant, et le terminal à sa place. */
    public static void request(ServerPlayer player, BlockPos terminal) {
        ServerLevel level = player.level();
        if (!level.isLoaded(terminal) || !level.getBlockState(terminal).is(ModBlocks.STATION_TERMINAL.get())
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(terminal)) > TERMINAL_RANGE * TERMINAL_RANGE) {
            return;
        }
        BlockPos station = StationManager.stationAt(level, terminal);
        if (station != null) {
            send(player, terminal, station, false);
        }
    }

    public static StationStatusPayload collect(ServerLevel level, BlockPos terminal, BlockPos station, boolean open) {
        int reach = StationManager.STATION_REACH;
        int generators = 0;
        int working = 0;
        int leaks = 0;
        int plants = 0;
        Set<Long> air = new HashSet<>();
        int racks = 0;
        int running = 0;
        int experiments = 0;
        int trays = 0;
        int planted = 0;
        int ripe = 0;
        int cargo = 0;
        int used = 0;
        for (int cx = (station.getX() - reach) >> 4; cx <= (station.getX() + reach) >> 4; cx++) {
            for (int cz = (station.getZ() - reach) >> 4; cz <= (station.getZ() + reach) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (!station.equals(StationManager.stationAt(level, pos))) {
                        continue;
                    }
                    if (entity instanceof LifeSupportBlockEntity unit) {
                        generators++;
                        switch (unit.getStatus()) {
                            case PRESSURIZED -> {
                                working++;
                                air.addAll(StationAir.room(level, pos));
                                plants = Math.max(plants, unit.getPlants());
                            }
                            case LEAK -> leaks++;
                            default -> {
                            }
                        }
                    } else if (entity instanceof LabRackBlockEntity rack) {
                        racks++;
                        running += rack.getStatus() == LabRackBlockEntity.Status.RUNNING ? 1 : 0;
                        experiments += rack.getExperiments();
                    } else if (entity instanceof HydroponicTrayBlockEntity) {
                        BlockState state = entity.getBlockState();
                        trays++;
                        if (HydroponicTrayBlock.growing(state)) {
                            planted++;
                            ripe += state.getValue(HydroponicTrayBlock.AGE) >= HydroponicTrayBlock.MAX_AGE ? 1 : 0;
                        }
                    } else if (entity instanceof CargoRackBlockEntity rack) {
                        cargo++;
                        used += rack.usedSlots();
                    }
                }
            }
        }

        // Le plan, et les couchettes de ses modules.
        List<StationStatusPayload.Module> plan = new ArrayList<>();
        int bunks = 0;
        for (StationLayout.Module module : StationLayout.modules(level.getServer(), station)) {
            plan.add(new StationStatusPayload.Module(module.kind().ordinal(), module.min().getX() - station.getX(), module.min().getZ() - station.getZ(),
                    module.max().getX() - station.getX(), module.max().getZ() - station.getZ()));
            for (BlockPos pos : BlockPos.betweenClosed(module.min(), module.max())) {
                bunks += level.getBlockState(pos).is(ModBlocks.CREW_BUNK.get()) ? 1 : 0;
            }
        }

        AABB area = new AABB(station).inflate(reach);
        List<String> crew = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (area.contains(player.position())) {
                crew.add(player.getGameProfile().name());
            }
        }
        int capsules = level.getEntitiesOfClass(CapsuleEntity.class, area, capsule -> capsule.getPhase() == CapsuleEntity.Phase.ORBIT).size();

        StationPower.Bus bus = StationPower.bus(station);
        StationStatusPayload.Power power = new StationStatusPayload.Power(bus.produced(level), bus.consumed(level), bus.stored(), bus.capacity(),
                bus.batteryCount(), StationPower.sunlit(level), StationPower.ticksToSwitch(level));
        return new StationStatusPayload(terminal, station, open, power,
                new StationStatusPayload.Air(generators, working, leaks, air.size(), plants),
                new StationStatusPayload.Lab(racks, running, experiments, trays, planted, ripe),
                crew, bunks, new int[] {cargo, used}, capsules, plan);
    }
}
