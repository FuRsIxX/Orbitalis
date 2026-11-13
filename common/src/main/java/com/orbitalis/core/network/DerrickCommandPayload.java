package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : une commande de l'écran d'un derrick (actualiser, lancer ou arrêter, changer le carré, le
 * remblai, un filtre, carotter). Voir les {@code ACTION_*} de {@code DerrickBlockEntity}.
 *
 * @param pos      le derrick
 * @param action   la commande
 * @param argument son argument : le sens du changement de carré, ou la sorte du filtre
 */
public record DerrickCommandPayload(BlockPos pos, int action, int argument) implements CustomPacketPayload {
    public static final Type<DerrickCommandPayload> TYPE = new Type<>(Orbitalis.id("derrick_command"));

    public static final StreamCodec<ByteBuf, DerrickCommandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DerrickCommandPayload::pos,
            ByteBufCodecs.VAR_INT, DerrickCommandPayload::action,
            ByteBufCodecs.INT, DerrickCommandPayload::argument,
            DerrickCommandPayload::new);

    @Override
    public Type<DerrickCommandPayload> type() {
        return TYPE;
    }
}
