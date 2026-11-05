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
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Les voyages des vaisseaux interstellaires, agence par agence : d'où chaque vaisseau est parti (son berceau au
 * chantier naval), s'il est en ce moment en orbite de Proxima b, et sa place là-bas. Gardé avec le monde principal.
 */
public final class ShipVoyages extends SavedData {
    /** Écart entre les places de deux agences en orbite de Proxima b, en blocs : leurs vaisseaux ne se touchent pas. */
    private static final int SLOT_SPACING = 256;
    /** Hauteur de l'axe des vaisseaux en orbite de Proxima b. */
    private static final int AWAY_Y = 120;

    /**
     * Le voyage d'une agence.
     *
     * @param home  centre de la poupe du vaisseau à son berceau, dans la dimension d'orbite
     * @param out   sens de la proue (le vaisseau garde son orientation d'un bout à l'autre du voyage)
     * @param away  vrai tant que le vaisseau est en orbite de Proxima b
     * @param slot  place de l'agence en orbite de Proxima b
     */
    public record Voyage(BlockPos home, Direction out, boolean away, int slot) {
        static final Codec<Voyage> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("home").forGetter(Voyage::home),
                Direction.CODEC.fieldOf("out").forGetter(Voyage::out),
                Codec.BOOL.fieldOf("away").forGetter(Voyage::away),
                Codec.INT.fieldOf("slot").forGetter(Voyage::slot)).apply(i, Voyage::new));

        /** Centre de la poupe du vaisseau en orbite de Proxima b. */
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
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Le voyage de cette agence, ou {@code null} si son vaisseau n'est jamais parti. */
    public Voyage of(String agency) {
        return voyages.get(agency);
    }

    /** Le vaisseau de cette agence part de son berceau : il prend une place en orbite de Proxima b (la même qu'avant s'il en a déjà une). */
    public Voyage depart(String agency, BlockPos home, Direction out) {
        Voyage known = voyages.get(agency);
        int slot = known != null ? known.slot() : nextSlot();
        Voyage voyage = new Voyage(home.immutable(), out, true, slot);
        voyages.put(agency, voyage);
        setDirty();
        return voyage;
    }

    /** La place qu'aurait en orbite de Proxima b une agence qui n'est jamais partie : la suivante libre. */
    public BlockPos peekAwayOrigin() {
        return new Voyage(BlockPos.ZERO, Direction.NORTH, true, nextSlot()).awayOrigin();
    }

    /** Le vaisseau de cette agence est revenu à son berceau. */
    public void back(String agency) {
        Voyage known = voyages.get(agency);
        if (known != null) {
            voyages.put(agency, new Voyage(known.home(), known.out(), false, known.slot()));
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
