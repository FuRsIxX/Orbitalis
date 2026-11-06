package com.orbitalis.core.network;

import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase3.interstellar.ShipSystems;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : ouvre l'écran du poste de pilotage d'un vaisseau interstellaire, avec le bilan de ses systèmes,
 * l'endroit où il se trouve et ce qui l'empêche de sauter.
 *
 * @param helm   le poste de pilotage
 * @param values dans l'ordre : coque posée et en tout, cœur présent (1), charge du cœur, moteur de l'anneau (1),
 *               caissons posés et en tout, bacs posés et en tout, poste de pilotage (1), place (ShipJump.Place),
 *               empêchement (ShipJump.Hold) pour la destination montrée, ticks du compte à rebours (-1 sans saut en
 *               cours), anneau en rotation (1), destination montrée (ShipDestination), puis l'empêchement pour chaque
 *               destination, dans l'ordre
 */
public record ShipHelmPayload(BlockPos helm, List<Integer> values) implements CustomPacketPayload {
    public static final Type<ShipHelmPayload> TYPE = new Type<>(Orbitalis.id("ship_helm"));

    public static final StreamCodec<ByteBuf, ShipHelmPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShipHelmPayload::helm,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ShipHelmPayload::values,
            ShipHelmPayload::new);

    public static ShipHelmPayload of(BlockPos helm, ShipSystems systems, int place, int hold, int countdown, boolean ringSpinning,
            int destination, List<Integer> holds) {
        List<Integer> values = new java.util.ArrayList<>(List.of(systems.hull()[0], systems.hull()[1], systems.core() ? 1 : 0,
                systems.charge(), systems.ringDrive() ? 1 : 0, systems.pods()[0], systems.pods()[1], systems.trays()[0],
                systems.trays()[1], systems.helm() ? 1 : 0, place, hold, countdown + 1, ringSpinning ? 1 : 0, destination));
        values.addAll(holds);
        return new ShipHelmPayload(helm, List.copyOf(values));
    }

    private int at(int index) {
        return index < values.size() ? values.get(index) : 0;
    }

    public int hullPlaced() {
        return at(0);
    }

    public int hullTotal() {
        return at(1);
    }

    public boolean core() {
        return at(2) != 0;
    }

    public int charge() {
        return at(3);
    }

    public boolean ringDrive() {
        return at(4) != 0;
    }

    public int podsPlaced() {
        return at(5);
    }

    public int podsTotal() {
        return at(6);
    }

    public int traysPlaced() {
        return at(7);
    }

    public int traysTotal() {
        return at(8);
    }

    public boolean helmPlaced() {
        return at(9) != 0;
    }

    /** Rang de ShipJump.Place : 0 au chantier, 1 en orbite de Proxima b, 2 ailleurs. */
    public int place() {
        return at(10);
    }

    /** Rang de ShipJump.Hold : 0 si rien n'empêche le saut. */
    public int hold() {
        return at(11);
    }

    /** Ticks avant le saut, ou -1 sans saut en cours (le décalage d'un garde les valeurs positives pour le codec). */
    public int countdown() {
        return at(12) - 1;
    }

    public boolean ringSpinning() {
        return at(13) != 0;
    }

    /** Rang de la destination à montrer : celle du voyage en cours, sinon la première. */
    public int destination() {
        return at(14);
    }

    /** Rang de ShipJump.Hold pour un départ vers la destination de ce rang. */
    public int holdFor(int destination) {
        return at(15 + destination);
    }

    @Override
    public Type<ShipHelmPayload> type() {
        return TYPE;
    }
}
