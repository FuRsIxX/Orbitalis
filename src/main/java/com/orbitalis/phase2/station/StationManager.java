package com.orbitalis.phase2.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Stations spatiales des agences : déploiement du module central à la fin d'un vol, et amarrage des capsules.
 * <p>
 * Un module central lancé en orbite se déploie à la verticale de son pas de tir, dans la dimension d'orbite :
 * une pièce pressurisée de trois blocs de côté, avec trois hublots, un sas d'amarrage, un générateur d'air et
 * son panneau solaire. La station s'agrandit ensuite bloc par bloc. Les capsules de l'agence qui arrivent en
 * orbite au-dessus du même site viennent s'amarrer à son sas.
 */
public final class StationManager {
    /** Demi-côté extérieur du module central, en blocs. */
    public static final int CORE_RADIUS = 2;
    /** Distance maximale entre un bloc et le centre du module central de sa station, sur chaque axe, en blocs. */
    public static final int STATION_REACH = 48;
    /** Distance horizontale maximale entre un pas de tir et la station que ses capsules rejoignent, en blocs. */
    private static final int DOCKING_RANGE = 96;
    /** Prime de recherche de la première station d'une agence. */
    private static final int REWARD_FIRST_STATION = 250;

    /** Sauvegarde : le centre du module central de chaque station, par agence. */
    public static class Data extends SavedData {
        private static final Codec<Data> CODEC = Codec.unboundedMap(Codec.STRING, BlockPos.CODEC.listOf())
                .xmap(Data::new, data -> data.stations);
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Orbitalis.id("stations"), Data::new, CODEC);

        private final Map<String, List<BlockPos>> stations = new HashMap<>();

        public Data() {
        }

        private Data(Map<String, List<BlockPos>> stations) {
            stations.forEach((agency, list) -> this.stations.put(agency, new ArrayList<>(list)));
        }
    }

    private StationManager() {
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
    }

    /**
     * Station où se trouve ce bloc : celle dont le module central est le plus proche, à moins de
     * {@link #STATION_REACH} blocs sur chaque axe, quelle que soit son agence. Les stations sont en orbite : ailleurs,
     * il n'y en a aucune.
     */
    public static BlockPos stationAt(ServerLevel level, BlockPos pos) {
        if (!SpaceRules.isOrbit(level)) {
            return null;
        }
        BlockPos nearest = null;
        long best = Long.MAX_VALUE;
        for (List<BlockPos> list : data(level.getServer()).stations.values()) {
            for (BlockPos station : list) {
                int dx = Math.abs(station.getX() - pos.getX());
                int dy = Math.abs(station.getY() - pos.getY());
                int dz = Math.abs(station.getZ() - pos.getZ());
                if (dx <= STATION_REACH && dy <= STATION_REACH && dz <= STATION_REACH) {
                    long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (distance < best) {
                        best = distance;
                        nearest = station;
                    }
                }
            }
        }
        return nearest;
    }

    /** Agence qui possède cette station, ou {@code null} si ce n'est pas une station connue. */
    public static String agencyOf(MinecraftServer server, BlockPos station) {
        for (Map.Entry<String, List<BlockPos>> entry : data(server).stations.entrySet()) {
            if (entry.getValue().contains(station)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Stations d'une agence, par le centre de leur module central. */
    public static List<BlockPos> stationsOf(MinecraftServer server, String agencyKey) {
        return List.copyOf(data(server).stations.getOrDefault(agencyKey, List.of()));
    }

    /** Enregistre une station déjà bâtie (sert aux tests et aux captures). */
    public static void register(MinecraftServer server, String agencyKey, BlockPos center) {
        Data data = data(server);
        List<BlockPos> list = data.stations.computeIfAbsent(agencyKey, key -> new ArrayList<>());
        if (!list.contains(center)) {
            list.add(center.immutable());
            data.setDirty();
        }
    }

    /** Station de l'agence la plus proche de la verticale d'un point, ou {@code null} s'il n'y en a pas à portée. */
    public static BlockPos nearest(MinecraftServer server, String agencyKey, double x, double z) {
        BlockPos nearest = null;
        double nearestDistance = (double) DOCKING_RANGE * DOCKING_RANGE;
        for (BlockPos station : data(server).stations.getOrDefault(agencyKey, List.of())) {
            double dx = station.getX() + 0.5 - x;
            double dz = station.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= nearestDistance) {
                nearestDistance = dx * dx + dz * dz;
                nearest = station;
            }
        }
        return nearest;
    }

    /** Sas d'amarrage du module central d'une station : au milieu de sa face sud. */
    public static BlockPos dockOf(BlockPos station) {
        return station.offset(0, 0, CORE_RADIUS);
    }

    /** Position d'une capsule amarrée au sas d'une station : contre lui, à l'extérieur. */
    public static Vec3 capsulePositionAt(BlockPos station) {
        BlockPos dock = dockOf(station);
        return new Vec3(dock.getX() + 0.5, dock.getY() - 0.5, dock.getZ() + 2.0);
    }

    /** Le bras robotique : il range le fret livré dans un casier de la station. */
    public static final String ROBOTIC_ARM = "robotic_arm";
    /** Jusqu'où, autour du module central, le bras va chercher un casier de fret, en blocs. */
    private static final int ARM_REACH = 24;

    /**
     * Range ce fret dans le premier casier de fret de la station qui a de la place, le plus près du module central.
     *
     * @return faux s'il n'y a aucun casier, ou qu'ils sont tous pleins
     */
    private static boolean stow(ServerLevel orbit, BlockPos center, ItemStack cargo) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-ARM_REACH, -6, -ARM_REACH), center.offset(ARM_REACH, 6, ARM_REACH))) {
            if (orbit.isLoaded(pos) && orbit.getBlockEntity(pos) instanceof CargoRackBlockEntity rack && hasRoom(rack)) {
                double distance = pos.distSqr(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        if (best == null || !(orbit.getBlockEntity(best) instanceof CargoRackBlockEntity rack)) {
            return false;
        }
        for (int slot = 0; slot < rack.getContainerSize(); slot++) {
            if (rack.getItem(slot).isEmpty()) {
                rack.setItem(slot, cargo);
                rack.setChanged();
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoom(CargoRackBlockEntity rack) {
        for (int slot = 0; slot < rack.getContainerSize(); slot++) {
            if (rack.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Endroit où sort l'équipage d'une capsule amarrée : à l'intérieur, juste derrière le sas. */
    public static Vec3 insideOf(BlockPos station) {
        BlockPos dock = dockOf(station);
        return new Vec3(dock.getX() + 0.5, dock.getY() - 0.4, dock.getZ() - 0.5);
    }

    /**
     * Fin d'un vol réussi qui emportait un module central : il se déploie en orbite si le lanceur pouvait
     * l'atteindre. S'il y a déjà une station au-dessus de ce site, le module lui est livré comme fret.
     *
     * @return ce qu'il faut annoncer aux joueurs
     */
    public static List<Component> onFlight(MinecraftServer server, String agencyKey, RocketStats.Reach reach, BlockPos launchSite) {
        return onFlight(server, agencyKey, reach, launchSite, new ItemStack(ModItems.STATION_CORE.get()));
    }

    /**
     * Fin d'un vol réussi qui emportait un module de station. Le module central se déploie s'il n'y a pas encore de
     * station au-dessus du site ; tout autre module est livré comme fret à la station de l'agence, à l'intérieur,
     * derrière le sas.
     */
    public static List<Component> onFlight(MinecraftServer server, String agencyKey, RocketStats.Reach reach, BlockPos launchSite,
            ItemStack payload) {
        List<Component> messages = new ArrayList<>();
        ServerLevel orbit = server.getLevel(SpaceRules.ORBIT);
        if (orbit == null || reach.ordinal() < RocketStats.Reach.LOW_ORBIT.ordinal()) {
            messages.add(Component.translatable("station.orbitalis.lost").withStyle(ChatFormatting.RED));
            return messages;
        }
        BlockPos existing = nearest(server, agencyKey, launchSite.getX() + 0.5, launchSite.getZ() + 0.5);
        if (existing != null) {
            // Avec le bras robotique, le fret est saisi à l'arrivée et rangé dans un casier de la station.
            if (com.orbitalis.core.research.ResearchManager.forAgency(server, agencyKey).isUnlocked(ROBOTIC_ARM)
                    && stow(orbit, existing, payload.copyWithCount(1))) {
                messages.add(Component.translatable("station.orbitalis.stowed", payload.getHoverName()).withStyle(ChatFormatting.AQUA));
                return messages;
            }
            Vec3 inside = insideOf(existing);
            orbit.addFreshEntity(new ItemEntity(orbit, inside.x, inside.y + 0.5, inside.z, payload.copyWithCount(1), 0, 0, 0));
            messages.add(Component.translatable("station.orbitalis.delivered", payload.getHoverName()).withStyle(ChatFormatting.AQUA));
            return messages;
        }
        if (!payload.is(ModItems.STATION_CORE.get())) {
            // Un module d'extension sans station où le livrer : il dérive, perdu.
            messages.add(Component.translatable("station.orbitalis.no_station", payload.getHoverName()).withStyle(ChatFormatting.RED));
            return messages;
        }

        BlockPos center = new BlockPos(launchSite.getX(), (int) SpaceRules.PARKING_HEIGHT, launchSite.getZ());
        build(orbit, center);
        StationLayout.modules(server, center);
        Data data = data(server);
        data.stations.computeIfAbsent(agencyKey, key -> new ArrayList<>()).add(center);
        data.setDirty();
        messages.add(Component.translatable("station.orbitalis.deployed").withStyle(ChatFormatting.GREEN));

        AgencyResearch research = ResearchManager.forAgency(server, agencyKey);
        if (research.completeMilestone("first_station")) {
            research.addPoints(REWARD_FIRST_STATION);
            ResearchManager.changed(server, agencyKey);
            messages.add(Component.translatable("station.orbitalis.first", REWARD_FIRST_STATION).withStyle(ChatFormatting.AQUA));
        }
        return messages;
    }

    /**
     * Construit le module central : une coque de cinq blocs de côté autour d'une pièce de trois, avec un hublot
     * au milieu des faces nord, est et ouest, le sas au milieu de la face sud, le générateur d'air au milieu
     * du plafond, une aile de cinq panneaux solaires en travers du toit et une antenne. Dedans, la pièce est
     * tapissée de panneaux capitonnés sur un plancher antidérapant ; le terminal de la station est sous le hublot
     * nord, une batterie sous le hublot ouest.
     */
    public static void build(ServerLevel level, BlockPos center) {
        BlockState hull = ModBlocks.STATION_HULL.get().defaultBlockState();
        BlockState window = ModBlocks.STATION_WINDOW.get().defaultBlockState();
        int r = CORE_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int edges = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (edges == 0) {
                        level.removeBlock(pos, false);
                        continue;
                    }
                    boolean faceCenter = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == r;
                    boolean windowSide = faceCenter && dy == 0 && dz != r;
                    BlockState state;
                    if (windowSide) {
                        state = window;
                    } else if (edges > 1) {
                        state = hull;
                    } else if (dy == -r) {
                        state = ModBlocks.STATION_FLOOR.get().defaultBlockState();
                    } else {
                        // Une face de la pièce : le revêtement, tourné vers l'intérieur.
                        Direction inward = dy == r ? Direction.DOWN : dx == r ? Direction.WEST : dx == -r ? Direction.EAST
                                : dz == r ? Direction.NORTH : Direction.SOUTH;
                        state = ModBlocks.STATION_PANEL.get().defaultBlockState().setValue(DirectionalBlock.FACING, inward);
                    }
                    level.setBlockAndUpdate(pos, state);
                }
            }
        }
        level.setBlockAndUpdate(center.offset(0, -1, -r), ModBlocks.STATION_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(center.offset(-r, -1, 0), ModBlocks.STATION_BATTERY.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        // La batterie part chargée à moitié, au sol, avant le lancement : de quoi tenir une première éclipse.
        if (level.getBlockEntity(center.offset(-r, -1, 0)) instanceof StationBatteryBlockEntity battery) {
            battery.getEnergy().generate(StationBatteryBlockEntity.CAPACITY / 2);
        }
        // Le collier du sas est tourné vers le sud, vers l'extérieur.
        level.setBlockAndUpdate(dockOf(center), ModBlocks.DOCKING_PORT.get().defaultBlockState().setValue(DockingPortBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(center.above(r), ModBlocks.LIFE_SUPPORT.get().defaultBlockState());
        // Aile solaire : cinq panneaux raccordés en travers du toit, celui du milieu posé sur le générateur d'air.
        for (int dx = -r; dx <= r; dx++) {
            level.setBlockAndUpdate(center.offset(dx, r + 1, 0), ModBlocks.SOLAR_PANEL.get().defaultBlockState()
                    .setValue(BlockStateProperties.WEST, dx > -r).setValue(BlockStateProperties.EAST, dx < r));
        }
        // Antenne de télécommunications, sur un angle du toit.
        level.setBlockAndUpdate(center.offset(r, r + 1, -r), ModBlocks.LIGHTNING_MAST.get().defaultBlockState());
    }

    /**
     * Accole un module d'extension nu à une station : une nouvelle pièce de trois blocs de côté, bâtie contre le mur
     * visé, avec un hublot sur chacune de ses trois autres faces, et un passage ouvert dans le mur commun.
     * Tant que l'ensemble reste clos, le générateur d'air de la station pressurise aussi la nouvelle pièce.
     *
     * @param wall bloc de paroi visé (coque ou hublot)
     * @param face face visée de ce bloc : la pièce est bâtie de ce côté
     * @return ce qui empêche la construction, ou {@code null} si le module est en place
     */
    public static Component expand(ServerLevel level, BlockPos wall, Direction face) {
        return StationModules.attach(level, wall, face, ModuleType.ROOM);
    }
}
