package com.orbitalis.phase3.interstellar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.orbitalis.platform.SavedDataType;

/**
 * Les voyages des vaisseaux interstellaires, agence par agence : d'où chaque vaisseau est parti (son berceau au
 * chantier naval), vers quel monde, s'il y est en ce moment, et sa place en orbite. Gardé avec le monde principal.
 */
public final class ShipVoyages extends com.orbitalis.platform.SavedDataType.CodecSavedData {
    /** Écart entre les places de deux agences en orbite d'un autre monde, en blocs : leurs vaisseaux ne se touchent pas. */
    private static final int SLOT_SPACING = 256;
    /** Hauteur de l'axe des vaisseaux en orbite d'un autre monde. */
    private static final int AWAY_Y = 120;

    /**
     * Le voyage d'une agence.
     *
     * @param home  centre de la poupe du vaisseau à son berceau, dans la dimension d'orbite
     * @param out   sens de la proue (le vaisseau garde son orientation d'un bout à l'autre du voyage)
     * @param away  vrai tant que le vaisseau est en orbite de sa destination
     * @param slot  place de l'agence en orbite (la même pour chaque destination)
     * @param to    nom de la destination du dernier départ (voir {@link ShipDestination})
     */
    public record Voyage(BlockPos home, Direction out, boolean away, int slot, String to) {
        static final Codec<Voyage> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("home").forGetter(Voyage::home),
                Direction.CODEC.fieldOf("out").forGetter(Voyage::out),
                Codec.BOOL.fieldOf("away").forGetter(Voyage::away),
                Codec.INT.fieldOf("slot").forGetter(Voyage::slot),
                Codec.STRING.optionalFieldOf("to", "proxima").forGetter(Voyage::to)).apply(i, Voyage::new));

        public ShipDestination destination() {
            return ShipDestination.byId(to);
        }

        /** Centre de la poupe du vaisseau en orbite de sa destination. */
        public BlockPos awayOrigin() {
            return new BlockPos(slot * SLOT_SPACING, AWAY_Y, 0);
        }
    }

    private static final Codec<ShipVoyages> CODEC = Codec.unboundedMap(Codec.STRING, Voyage.CODEC)
            .xmap(ShipVoyages::new, voyages -> voyages.voyages);
    static final SavedDataType<ShipVoyages> TYPE = new SavedDataType<>(Orbitalis.id("ship_voyages"), ShipVoyages::new, CODEC);

    private final Map<String, Voyage> voyages = new LinkedHashMap<>();

    private ShipVoyages() {
    }

    private ShipVoyages(Map<String, Voyage> voyages) {
        this.voyages.putAll(voyages);
    }

    public static ShipVoyages of(MinecraftServer server) {
        return com.orbitalis.platform.SavedDataType.computeIfAbsent(server.overworld().getDataStorage(), TYPE);
    }

    /** Un vaisseau parti : son agence, le centre de sa poupe et le sens de sa proue. */
    public record Located(String agency, BlockPos origin, Direction out) {
    }

    /**
     * Le vaisseau en orbite d'une destination, dans ce monde, dont la boîte contient ce point ; ou {@code null}. Sert au
     * réseau électrique et au laboratoire de bord, quand le vaisseau est loin de sa station.
     */
    public Located awayAt(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        for (Map.Entry<String, Voyage> entry : voyages.entrySet()) {
            Voyage voyage = entry.getValue();
            if (voyage.away() && voyage.destination().orbit() == dimension
                    && ShipJump.box(voyage.awayOrigin(), voyage.out()).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))) {
                return new Located(entry.getKey(), voyage.awayOrigin(), voyage.out());
            }
        }
        return null;
    }

    /** L'agence du vaisseau parti dont la poupe est là, ou {@code null}. */
    public String agencyAway(BlockPos origin) {
        for (Map.Entry<String, Voyage> entry : voyages.entrySet()) {
            if (entry.getValue().away() && entry.getValue().awayOrigin().equals(origin)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Le voyage de cette agence, ou {@code null} si son vaisseau n'est jamais parti. */
    public Voyage of(String agency) {
        return voyages.get(agency);
    }

    /** Le vaisseau de cette agence part de son berceau : il prend une place en orbite de sa destination (la même qu'avant s'il en a déjà une). */
    public Voyage depart(String agency, BlockPos home, Direction out, ShipDestination destination) {
        Voyage known = voyages.get(agency);
        int slot = known != null ? known.slot() : nextSlot();
        Voyage voyage = new Voyage(home.immutable(), out, true, slot, destination.id());
        voyages.put(agency, voyage);
        setDirty();
        return voyage;
    }

    /** La place qu'aurait en orbite une agence qui n'est jamais partie : la suivante libre. */
    public BlockPos peekAwayOrigin() {
        return new Voyage(BlockPos.ZERO, Direction.NORTH, true, nextSlot(), ShipDestination.PROXIMA.id()).awayOrigin();
    }

    /** Le vaisseau de cette agence est revenu à son berceau. */
    public void back(String agency) {
        Voyage known = voyages.get(agency);
        if (known != null) {
            voyages.put(agency, new Voyage(known.home(), known.out(), false, known.slot(), known.to()));
            setDirty();
        }
    }

    private int nextSlot() {
        List<Integer> taken = new ArrayList<>();
        for (Voyage voyage : voyages.values()) {
            taken.add(voyage.slot());
        }
        int slot = 0;
        while (taken.contains(slot)) {
            slot++;
        }
        return slot;
    }
}
