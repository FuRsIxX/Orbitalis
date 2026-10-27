package com.orbitalis.core.network;

import java.util.List;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : satellites de l'agence du joueur, vus depuis une station de poursuite.
 *
 * @param station       position de la station
 * @param open          vrai si le client doit ouvrir l'écran, faux pour une simple actualisation
 * @param skyClear      vrai si l'antenne de la station voit le ciel
 * @param relay         vrai si un satellite de télécommunications relaie les données en continu
 * @param navigation    nombre de satellites de navigation en service
 * @param forecast      prévision météo : {@link #FORECAST_NONE} sans satellite météo, sinon le temps qu'il fait
 * @param forecastTicks durée pendant laquelle ce temps doit encore durer, en ticks
 * @param satellites    satellites suivis, du plus ancien au plus récent
 */
public record SatelliteStatusPayload(BlockPos station, boolean open, boolean skyClear, boolean relay, int navigation,
        int forecast, int forecastTicks, List<Entry> satellites) implements CustomPacketPayload {
    public static final int FORECAST_NONE = -1;
    public static final int FORECAST_CLEAR = 0;
    public static final int FORECAST_RAIN = 1;
    public static final int FORECAST_THUNDER = 2;

    /**
     * Un satellite.
     *
     * @param number    numéro d'ordre dans l'agence
     * @param type      rang dans {@code SatelliteType}
     * @param age       temps passé en orbite, en ticks
     * @param remaining durée de vie restante, en ticks ; 0 s'il est hors service, -1 si elle est illimitée
     * @param pending   points de recherche en mémoire, pas encore reçus au sol
     */
    public record Entry(int number, int type, long age, long remaining, int pending) {
        public boolean active() {
            return remaining != 0;
        }
    }

    public static final Type<SatelliteStatusPayload> TYPE = new Type<>(Orbitalis.id("satellite_status"));

    public static final StreamCodec<FriendlyByteBuf, SatelliteStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.station);
                buffer.writeBoolean(payload.open);
                buffer.writeBoolean(payload.skyClear);
                buffer.writeBoolean(payload.relay);
                buffer.writeVarInt(payload.navigation);
                buffer.writeInt(payload.forecast);
                buffer.writeVarInt(payload.forecastTicks);
                buffer.writeCollection(payload.satellites, (output, entry) -> {
                    output.writeVarInt(entry.number());
                    output.writeVarInt(entry.type());
                    output.writeVarLong(entry.age());
                    output.writeLong(entry.remaining());
                    output.writeVarInt(entry.pending());
                });
            },
            buffer -> new SatelliteStatusPayload(buffer.readBlockPos(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readInt(), buffer.readVarInt(),
                    buffer.readList(input -> new Entry(input.readVarInt(), input.readVarInt(), input.readVarLong(), input.readLong(),
                            input.readVarInt()))));

    /** Points de recherche en attente sur tous les satellites. */
    public int pending() {
        int total = 0;
        for (Entry entry : satellites) {
            total += entry.pending();
        }
        return total;
    }

    @Override
    public Type<SatelliteStatusPayload> type() {
        return TYPE;
    }
}
