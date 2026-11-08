package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur appuie sur la touche de l'atlas. Si son casque porte la liaison satellite et qu'un
 * satellite relais de l'agence est en service, le serveur lui ouvre l'atlas là où il se trouve.
 */
public record UplinkOpenPayload() implements CustomPacketPayload {
    public static final Type<UplinkOpenPayload> TYPE = new Type<>(Orbitalis.id("uplink_open"));
    public static final StreamCodec<FriendlyByteBuf, UplinkOpenPayload> STREAM_CODEC = StreamCodec.unit(new UplinkOpenPayload());

    @Override
    public Type<UplinkOpenPayload> type() {
        return TYPE;
    }
}
