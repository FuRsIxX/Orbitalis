package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : relevé de prospection d'un satellite d'observation, autour d'une station de poursuite.
 * <p>
 * Le terrain est découpé en tronçons de 16 blocs de côté, comme le monde. Le relevé couvre un carré de tronçons
 * centré sur la station : plus l'agence a de satellites d'observation, plus il est grand.
 *
 * @param station  position de la station
 * @param size     côté du carré relevé, en tronçons ; 0 si l'agence n'a aucun satellite d'observation en service
 * @param originX  tronçon du coin nord-ouest du carré (coordonnée de tronçon)
 * @param originZ  idem
 * @param kinds    nombre de minerais distingués (ceux de {@code ProspectOre})
 * @param counts   blocs de chaque minerai dans chaque tronçon, rangés par ligne (du nord au sud), puis par colonne
 *                 (d'ouest en est), puis par minerai. La valeur transmise est le nombre de blocs plus un ; 0 marque
 *                 un tronçon qui n'a pas pu être relevé
 */
public record ProspectPayload(BlockPos station, int size, int originX, int originZ, int kinds, int[] counts) implements CustomPacketPayload {
    public static final Type<ProspectPayload> TYPE = new Type<>(Orbitalis.id("prospect"));

    public static final StreamCodec<FriendlyByteBuf, ProspectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.station);
                buffer.writeVarInt(payload.size);
                buffer.writeInt(payload.originX);
                buffer.writeInt(payload.originZ);
                buffer.writeVarInt(payload.kinds);
                buffer.writeVarIntArray(payload.counts);
            },
            buffer -> new ProspectPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readInt(), buffer.readInt(), buffer.readVarInt(),
                    buffer.readVarIntArray()));

    /** Blocs d'un minerai dans un tronçon du relevé ; -1 s'il n'a pas pu être relevé. */
    public int count(int column, int row, int kind) {
        int index = (row * size + column) * kinds + kind;
        return index >= 0 && index < counts.length ? counts[index] - 1 : -1;
    }

    @Override
    public Type<ProspectPayload> type() {
        return TYPE;
    }
}
