package com.orbitalis.phase2.station;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.survival.SpaceRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Réseau électrique des stations, et course de la station autour de la planète.
 * <p>
 * Tout ce qui est bâti dans une station partage le même réseau, comme le câblage qui court sous les panneaux d'une
 * vraie station : les panneaux solaires y versent ce que leurs voisins directs n'ont pas pris, les batteries y
 * stockent, et les équipements y puisent quand leur propre réserve est vide. Un bloc appartient à la station dont le
 * module central est le plus proche, à moins de {@link StationManager#STATION_REACH} blocs.
 * <p>
 * La station fait le tour de la planète en {@link #ORBIT_PERIOD} ticks, dont un peu plus d'un tiers dans son ombre :
 * pendant l'éclipse, les panneaux ne produisent plus rien, et ce sont les batteries qui tiennent la station.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class StationPower {
    /** Durée d'une orbite, en ticks : cinq minutes, comme une orbite basse en accéléré. */
    public static final int ORBIT_PERIOD = 6000;
    /** Part de l'orbite passée dans l'ombre de la planète. */
    public static final double ECLIPSE_SHARE = 0.36;
    /** Ce que le câblage d'une station garde en réserve sans aucune batterie, en FE. */
    public static final int WIRING_BUFFER = 4000;
    /** Intervalle de mesure de la production et de la consommation, en ticks. */
    private static final int WINDOW = 20;

    private static final Map<BlockPos, Bus> BUSES = new ConcurrentHashMap<>();

    private StationPower() {
    }

    /** Ticks d'orbite passés au soleil, avant l'éclipse. */
    private static int sunlitTicks() {
        return (int) Math.round(ORBIT_PERIOD * (1.0 - ECLIPSE_SHARE));
    }

    /** Vrai si les panneaux voient le Soleil : toujours sur une planète, hors de l'éclipse en orbite. */
    public static boolean sunlit(Level level) {
        return !SpaceRules.isOrbit(level) || level.getGameTime() % ORBIT_PERIOD < sunlitTicks();
    }

    /** Ticks avant le prochain changement : l'entrée dans l'ombre, ou le retour au soleil. */
    public static int ticksToSwitch(Level level) {
        int phase = (int) (level.getGameTime() % ORBIT_PERIOD);
        return phase < sunlitTicks() ? sunlitTicks() - phase : ORBIT_PERIOD - phase;
    }

    /** Le réseau d'une station, repéré par le centre de son module central. */
    public static Bus bus(BlockPos station) {
        return BUSES.computeIfAbsent(station.immutable(), Bus::new);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BUSES.clear();
    }

    /**
     * Le réseau électrique d'une station : une petite réserve propre au câblage, et les batteries qui s'y sont
     * raccordées. On y verse et on y puise ; il mesure ce qui y passe, seconde après seconde.
     */
    public static final class Bus {
        private final BlockPos station;
        private final Set<StationBatteryBlockEntity> batteries = Collections.newSetFromMap(new IdentityHashMap<>());
        private int buffer;
        /** Début de la mesure en cours ; négatif tant qu'aucune n'a commencé. */
        private long windowStart = -1;
        private long producedNow;
        private long consumedNow;
        private int produced;
        private int consumed;

        private Bus(BlockPos station) {
            this.station = station;
        }

        public BlockPos station() {
            return station;
        }

        /** Raccorde une batterie (elle se raccorde elle-même à chaque tick, tant qu'elle est en place). */
        public synchronized void attach(StationBatteryBlockEntity battery) {
            batteries.add(battery);
        }

        public synchronized void detach(StationBatteryBlockEntity battery) {
            batteries.remove(battery);
        }

        private List<StationBatteryBlockEntity> live() {
            List<StationBatteryBlockEntity> list = new ArrayList<>();
            batteries.removeIf(StationBatteryBlockEntity::isRemoved);
            list.addAll(batteries);
            return list;
        }

        private void measure(Level level) {
            long now = level.getGameTime();
            if (windowStart < 0 || now - windowStart >= WINDOW || now < windowStart) {
                long span = windowStart < 0 ? 0 : Math.max(1, now - windowStart);
                produced = span == 0 ? 0 : (int) (producedNow / span);
                consumed = span == 0 ? 0 : (int) (consumedNow / span);
                producedNow = consumedNow = 0;
                windowStart = now;
            }
        }

        /**
         * Verse de l'énergie dans le réseau : elle charge les batteries, puis la réserve du câblage.
         *
         * @return ce que le réseau a accepté
         */
        public synchronized int offer(Level level, int amount) {
            measure(level);
            int left = amount;
            for (StationBatteryBlockEntity battery : live()) {
                if (left <= 0) {
                    break;
                }
                left -= battery.getEnergy().generate(left);
            }
            int kept = Math.min(left, WIRING_BUFFER - buffer);
            buffer += kept;
            left -= kept;
            producedNow += amount - left;
            return amount - left;
        }

        /**
         * Puise exactement cette quantité d'énergie dans le réseau, si elle y est : d'abord dans la réserve du
         * câblage, puis dans les batteries. Sinon, rien n'est pris.
         */
        public synchronized boolean draw(Level level, int amount) {
            measure(level);
            if (amount <= 0) {
                return true;
            }
            List<StationBatteryBlockEntity> live = live();
            long available = buffer;
            for (StationBatteryBlockEntity battery : live) {
                available += battery.getEnergy().getEnergy();
            }
            if (available < amount) {
                return false;
            }
            int left = amount;
            int fromBuffer = Math.min(buffer, left);
            buffer -= fromBuffer;
            left -= fromBuffer;
            for (StationBatteryBlockEntity battery : live) {
                if (left <= 0) {
                    break;
                }
                int take = Math.min(left, battery.getEnergy().getEnergy());
                battery.getEnergy().consume(take);
                left -= take;
            }
            consumedNow += amount;
            return true;
        }

        /** Énergie en réserve : le câblage et toutes les batteries, en FE. */
        public synchronized long stored() {
            long total = buffer;
            for (StationBatteryBlockEntity battery : live()) {
                total += battery.getEnergy().getEnergy();
            }
            return total;
        }

        /** Capacité du réseau : le câblage et toutes les batteries, en FE. */
        public synchronized long capacity() {
            long total = WIRING_BUFFER;
            for (StationBatteryBlockEntity battery : live()) {
                total += battery.getEnergy().getCapacity();
            }
            return total;
        }

        public synchronized int batteryCount() {
            return live().size();
        }

        /** Production moyenne de la dernière seconde, en FE par tick. */
        public synchronized int produced(Level level) {
            measure(level);
            return produced;
        }

        /** Consommation moyenne de la dernière seconde, en FE par tick. */
        public synchronized int consumed(Level level) {
            measure(level);
            return consumed;
        }
    }

    /**
     * Ce qu'un équipement retient de la station où il se trouve : il ne la recherche que de temps en temps.
     */
    public static final class Link {
        private static final int RECHECK = 100;
        private BlockPos station;
        /** Moment de la dernière recherche ; négatif tant qu'il n'y en a pas eu. */
        private long checkedAt = -1;

        /** Le réseau de la station de cet équipement, ou {@code null} s'il n'est dans aucune station. */
        public Bus bus(ServerLevel level, BlockPos pos) {
            long now = level.getGameTime();
            if (checkedAt < 0 || now - checkedAt >= RECHECK || now < checkedAt) {
                checkedAt = now;
                station = StationManager.stationAt(level, pos);
            }
            return station == null ? null : StationPower.bus(station);
        }

        /** La station de cet équipement, ou {@code null}. */
        public BlockPos station(ServerLevel level, BlockPos pos) {
            bus(level, pos);
            return station;
        }
    }
}
