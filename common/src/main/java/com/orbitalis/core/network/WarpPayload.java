package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : le vaisseau de ce joueur passe en distorsion, ou le saut est annulé au dernier moment. Le client
 * met le saut en scène (voir {@code WarpFx}) : les étoiles qui s'étirent, l'éclair, le tunnel, la sortie, le bandeau
 * d'arrivée.
 *
 * @param start    vrai au départ, faux si le saut est annulé
 * @param out      la direction de la proue (valeur 3D d'une {@code Direction}) : le tunnel file dans ce sens
 * @param galactic vrai pour un saut galactique : un tunnel plus violet
 * @param title    la clé du nom du monde d'arrivée, pour le bandeau
 * @param subtitle la clé de la ligne sous le nom
 */
public record WarpPayload(boolean start, int out, boolean galactic, String title, String subtitle) implements CustomPacketPayload {
    public static final Type<WarpPayload> TYPE = new Type<>(Orbitalis.id("warp"));
    public static final StreamCodec<FriendlyByteBuf, WarpPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.start);
                buffer.writeVarInt(payload.out);
                buffer.writeBoolean(payload.galactic);
                buffer.writeUtf(payload.title);
                buffer.writeUtf(payload.subtitle);
            },
            buffer -> new WarpPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(), buffer.readUtf()));

    @Override
    public Type<WarpPayload> type() {
        return TYPE;
    }
}
