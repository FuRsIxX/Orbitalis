package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : ce que le pilote d'une fusée en montée doit avoir sous les yeux. Envoyé dix fois par seconde au
 * seul pilote ; son affichage tête haute le dessine (voir {@code CockpitHud}).
 *
 * @param altitudeKm   altitude, en km
 * @param speed        vitesse, en m/s
 * @param stress       fatigue de la structure, de 0 à 1
 * @param flightBits   étapes du vol (voir {@code LaunchTableBlockEntity.flightBits})
 * @param flightWindow secondes avant que ne se referme la fenêtre ouverte la plus pressée
 * @param assist       niveau d'aide : 0 manuel, 1 assisté, 2 automatique
 * @param ticks        ticks de vol
 * @param qte          le QTE en cours (voir {@code LaunchTableBlockEntity.qteHud})
 */
public record FlightHudPayload(float altitudeKm, float speed, float stress, int flightBits, int flightWindow, int assist, int ticks,
        int qte) implements CustomPacketPayload {
    public static final Type<FlightHudPayload> TYPE = new Type<>(Orbitalis.id("flight_hud"));

    public static final StreamCodec<ByteBuf, FlightHudPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeFloat(payload.altitudeKm);
                buffer.writeFloat(payload.speed);
                buffer.writeFloat(payload.stress);
                buffer.writeInt(payload.flightBits);
                buffer.writeInt(payload.flightWindow);
                buffer.writeInt(payload.assist);
                buffer.writeInt(payload.ticks);
                buffer.writeInt(payload.qte);
            },
            buffer -> new FlightHudPayload(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt()));

    /** Secondes de vol. */
    public int seconds() {
        return ticks / 20;
    }

    @Override
    public Type<FlightHudPayload> type() {
        return TYPE;
    }
}
