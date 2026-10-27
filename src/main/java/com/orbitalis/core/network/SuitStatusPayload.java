package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : ce que les capteurs d'une combinaison mesurent autour de son porteur, une fois par seconde, tant
 * qu'il porte un casque de combinaison. Son affichage tête haute le dessine (voir {@code SuitHud}).
 *
 * @param breathable vrai si l'on respire là où il se tient : sur la planète, dans une capsule, dans une pièce
 *                   pressurisée d'une station
 */
public record SuitStatusPayload(boolean breathable) implements CustomPacketPayload {
    public static final Type<SuitStatusPayload> TYPE = new Type<>(Orbitalis.id("suit_status"));

    public static final StreamCodec<ByteBuf, SuitStatusPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(SuitStatusPayload::new,
            SuitStatusPayload::breathable);

    @Override
    public Type<SuitStatusPayload> type() {
        return TYPE;
    }
}
