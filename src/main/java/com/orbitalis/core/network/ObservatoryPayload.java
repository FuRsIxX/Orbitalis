package com.orbitalis.core.network;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : l'observatoire de l'agence, vu d'une station de poursuite (voir {@code Observatory}).
 *
 * @param station    la station
 * @param telescopes télescopes spatiaux en service
 * @param observers  satellites d'observation en service
 * @param link       vrai si l'on peut recevoir une image : l'antenne voit le ciel, ou un relais transmet
 * @param entries    les astres que l'agence connaît
 */
public record ObservatoryPayload(BlockPos station, int telescopes, int observers, boolean link, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ObservatoryPayload> TYPE = new Type<>(Orbitalis.id("observatory"));
    /** États d'un astre : connu, observé en ce moment, découvert (image à recevoir), au catalogue. */
    public static final int LISTED = 0;
    public static final int OBSERVING = 1;
    public static final int DISCOVERED = 2;
    public static final int IMAGED = 3;

    /**
     * Un astre.
     *
     * @param body    son rang dans {@code CelestialBody}
     * @param state   où en est son observation
     * @param permille avancement de l'observation, en millièmes
     * @param power   puissance que l'agence peut pointer sur lui : 0 si aucun instrument ne le voit
     * @param visible vrai s'il est dans sa fenêtre de visibilité
     * @param change  ticks avant que sa fenêtre ne se ferme (s'il est visible) ou ne s'ouvre
     */
    public record Entry(int body, int state, int permille, int power, boolean visible, int change) {
    }

    public static final StreamCodec<FriendlyByteBuf, ObservatoryPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.station);
                buffer.writeVarInt(payload.telescopes);
                buffer.writeVarInt(payload.observers);
                buffer.writeBoolean(payload.link);
                buffer.writeVarInt(payload.entries.size());
                for (Entry entry : payload.entries) {
                    buffer.writeVarInt(entry.body);
                    buffer.writeByte(entry.state);
                    buffer.writeVarInt(entry.permille);
                    buffer.writeVarInt(entry.power);
                    buffer.writeBoolean(entry.visible);
                    buffer.writeVarInt(entry.change);
                }
            },
            buffer -> {
                BlockPos station = buffer.readBlockPos();
                int telescopes = buffer.readVarInt();
                int observers = buffer.readVarInt();
                boolean link = buffer.readBoolean();
                int count = Math.min(buffer.readVarInt(), 256);
                List<Entry> entries = new ArrayList<>(count);
                for (int k = 0; k < count; k++) {
                    entries.add(new Entry(buffer.readVarInt(), buffer.readByte(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                            buffer.readVarInt()));
                }
                return new ObservatoryPayload(station, telescopes, observers, link, entries);
            });

    @Override
    public Type<ObservatoryPayload> type() {
        return TYPE;
    }
}
