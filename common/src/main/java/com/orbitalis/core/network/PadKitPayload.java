package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur a choisi, dans l'écran du kit de pas de tir, le plan à poser.
 * Le serveur vérifie qu'il tient bien un kit et qu'il est à portée avant d'agir.
 *
 * @param table    emplacement de la table de lancement (à poser, ou déjà posée si l'on change son plan)
 * @param radius   taille du tablier : 1 pour 3×3 ... 5 pour 11×11
 * @param elevator vrai pour une tour à ascenseur, faux pour une tour à escalier
 * @param facing   sens du carneau
 */
public record PadKitPayload(BlockPos table, int radius, boolean elevator, Direction facing) implements CustomPacketPayload {
    public static final Type<PadKitPayload> TYPE = new Type<>(Orbitalis.id("pad_kit"));

    public static final StreamCodec<ByteBuf, PadKitPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PadKitPayload::table,
            ByteBufCodecs.VAR_INT, PadKitPayload::radius,
            ByteBufCodecs.BOOL, PadKitPayload::elevator,
            Direction.STREAM_CODEC, PadKitPayload::facing,
            PadKitPayload::new);

    @Override
    public Type<PadKitPayload> type() {
        return TYPE;
    }
}
