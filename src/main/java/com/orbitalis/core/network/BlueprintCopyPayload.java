package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur demande, depuis l'écran du pas de tir, une copie du plan de la fusée qui s'y dresse
 * (une fusée bâtie librement, par exemple, pour la refaire ailleurs). Le serveur prend la table la plus proche.
 */
public record BlueprintCopyPayload() implements CustomPacketPayload {
    public static final Type<BlueprintCopyPayload> TYPE = new Type<>(Orbitalis.id("blueprint_copy"));

    public static final StreamCodec<ByteBuf, BlueprintCopyPayload> STREAM_CODEC = StreamCodec.unit(new BlueprintCopyPayload());

    @Override
    public Type<BlueprintCopyPayload> type() {
        return TYPE;
    }
}
